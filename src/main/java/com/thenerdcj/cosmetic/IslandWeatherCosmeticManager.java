package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IslandWeatherCosmeticManager - Play-to-Win cosmetic island weather/ambient particle effects.
 *
 * Active weather is per-island (cosmetic only - particles, no actual weather or gameplay changes).
 * Visible to players on the island. Purely visual flair.
 * Folia-safe: particle spawning scheduled via player/entity schedulers + ThreadSafety.
 * Hooks for island enter/leave (integrated in IslandManager).
 */
public class IslandWeatherCosmeticManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<IslandWeatherCosmetic>> ownedWeather = new ConcurrentHashMap<>();
    private final Map<String, IslandWeatherCosmetic> activeWeatherByIsland = new ConcurrentHashMap<>();

    // Track per-player repeating particle tasks (for cleanup)
    private final Map<UUID, Object> activeWeatherTasks = new ConcurrentHashMap<>(); // use Object to hold scheduler task if needed

    private static final int[] COLLECTION_MILESTONES = {3, 5, 8, 12};

    public IslandWeatherCosmeticManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<IslandWeatherCosmetic> getOwnedWeather(UUID uuid) {
        return ownedWeather.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public int getCollectionCount(UUID uuid) {
        return getOwnedWeather(uuid).size();
    }

    public void unlockWeather(UUID uuid, IslandWeatherCosmetic weather) {
        if (weather == null || weather.isNone()) return;
        Set<IslandWeatherCosmetic> owned = getOwnedWeather(uuid);
        boolean wasNew = owned.add(weather);
        if (wasNew) {
            int xp = Math.max(10, weather.getRarity().getXpReward() / 3);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && plugin.getConfig().getBoolean("island.weather.collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(player, xp);
                player.sendMessage("§a§lIsland Weather Collection §7» Unlocked " + weather.getRarity().getColorCode() + weather.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = owned.size();
                for (int m : COLLECTION_MILESTONES) {
                    if (count == m) {
                        player.sendMessage("§6§l★ Island Meteorologist Milestone! ★ §e" + count + " unique weather effects unlocked!");
                        break;
                    }
                }
            }
            plugin.getDatabaseManager().savePlayerIslandWeather(uuid, getWeatherIdSet(uuid));
        }
    }

    public boolean hasWeather(UUID uuid, IslandWeatherCosmetic weather) {
        if (weather == null || weather.isNone()) return true;
        return getOwnedWeather(uuid).contains(weather);
    }

    public IslandWeatherCosmetic getActiveWeather(String islandKey) {
        return activeWeatherByIsland.getOrDefault(islandKey, IslandWeatherCosmetic.NONE);
    }

    public void setActiveWeather(Island island, IslandWeatherCosmetic weather) {
        if (island == null) return;
        String islandKey = island.getId();

        // Stop current effects for players on island
        stopWeatherForIsland(island);

        if (weather == null || weather.isNone()) {
            activeWeatherByIsland.remove(islandKey);
            plugin.getDatabaseManager().saveIslandActiveWeather(islandKey, "NONE");
            return;
        }

        activeWeatherByIsland.put(islandKey, weather);
        plugin.getDatabaseManager().saveIslandActiveWeather(islandKey, weather.name());

        // Start for current players on this island
        playWeatherForIsland(island, weather);
    }

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerIslandWeather(uuid);
        Set<IslandWeatherCosmetic> set = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                IslandWeatherCosmetic w = IslandWeatherCosmetic.valueOf(id);
                if (!w.isNone()) set.add(w);
            } catch (Exception ignored) {}
        }
        ownedWeather.put(uuid, set);
    }

    public void savePlayer(UUID uuid) {
        plugin.getDatabaseManager().savePlayerIslandWeather(uuid, getWeatherIdSet(uuid));
    }

    private Set<String> getWeatherIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (IslandWeatherCosmetic w : getOwnedWeather(uuid)) ids.add(w.name());
        return ids;
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        ownedWeather.remove(player.getUniqueId());
        stopWeatherForPlayer(player);
    }

    // ==================== WEATHER EFFECTS (Folia-safe particles) ====================

    private void playWeatherForIsland(Island island, IslandWeatherCosmetic weather) {
        if (island == null || weather == null || weather.isNone()) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            Island playerIsland = plugin.getIslandManager().getIsland(p.getUniqueId(), p.getWorld().getEnvironment());
            if (playerIsland != null && playerIsland.getId().equals(island.getId())) {
                startParticleLoopForPlayer(p, weather, island);
            }
        }
    }

    private void stopWeatherForIsland(Island island) {
        if (island == null) return;
        String islandKey = island.getId();

        for (Player p : Bukkit.getOnlinePlayers()) {
            Island playerIsland = plugin.getIslandManager().getIsland(p.getUniqueId(), p.getWorld().getEnvironment());
            if (playerIsland != null && playerIsland.getId().equals(islandKey)) {
                stopWeatherForPlayer(p);
            }
        }
    }

    private void startParticleLoopForPlayer(Player player, IslandWeatherCosmetic weather, Island island) {
        stopWeatherForPlayer(player);
        if (player == null || !player.isOnline()) return;

        String key = weather.getWeatherKey();
        Location center = island.getCenter(null);
        if (center == null) center = player.getLocation();

        final Location baseLoc = center.clone();

        Runnable particleTask = () -> {
            if (!player.isOnline()) return;

            Location loc = player.getLocation().add(0, 2.5, 0); // above head area, or use base + random
            // Spawn themed particles in a small radius around player / island feel
            switch (key) {
                case "rain":
                    player.getWorld().spawnParticle(Particle.SPLASH, loc, 12, 2.5, 1.5, 2.5, 0.01);
                    player.getWorld().spawnParticle(Particle.SPLASH, loc.add(0, 1, 0), 4, 1.5, 0.8, 1.5, 0.01);
                    break;
                case "snow":
                    player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 15, 2.5, 1.8, 2.5, 0.01);
                    break;
                case "pollen":
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 2.0, 1.2, 2.0, 0.01);
                    player.getWorld().spawnParticle(Particle.DUST, loc, 6, 2.0, 1.0, 2.0, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(144, 238, 144), 1f));
                    break;
                case "ember":
                    player.getWorld().spawnParticle(Particle.FLAME, loc, 6, 2.0, 1.2, 2.0, 0.01);
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 4, 1.8, 0.8, 1.8, 0.01);
                    break;
                case "fog":
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 10, 2.5, 1.5, 2.5, 0.01);
                    player.getWorld().spawnParticle(Particle.PORTAL, loc, 5, 2.0, 1.0, 2.0, 0.01);
                    break;
                case "starfall":
                    player.getWorld().spawnParticle(Particle.FIREWORK, loc, 4, 2.5, 1.5, 2.5, 0.01);
                    player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 8, 2.0, 1.2, 2.0, 0.01);
                    break;
                case "aurora":
                    // Color shifting feel with dust + end rod
                    player.getWorld().spawnParticle(Particle.END_ROD, loc, 6, 2.5, 1.5, 2.5, 0.01);
                    player.getWorld().spawnParticle(Particle.DUST, loc, 5, 2.0, 1.0, 2.0, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(50, 205, 50), 1.2f));
                    player.getWorld().spawnParticle(Particle.DUST, loc, 5, 2.0, 1.0, 2.0, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(138, 43, 226), 1.2f));
                    break;
                case "tempest":
                    player.getWorld().spawnParticle(Particle.FLAME, loc, 8, 2.2, 1.3, 2.2, 0.01);
                    player.getWorld().spawnParticle(Particle.CRIT, loc, 6, 2.0, 1.0, 2.0, 0.01);
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 1.8, 0.8, 1.8, 0.01);
                    break;
                case "celestial":
                    player.getWorld().spawnParticle(Particle.END_ROD, loc, 8, 2.5, 1.5, 2.5, 0.01);
                    player.getWorld().spawnParticle(Particle.FIREWORK, loc, 3, 2.0, 1.2, 2.0, 0.01);
                    break;
                case "void":
                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 6, 2.2, 1.2, 2.2, 0.01);
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 8, 2.5, 1.3, 2.5, 0.01);
                    break;
                case "rainbow":
                    player.getWorld().spawnParticle(Particle.DUST, loc, 4, 2.0, 1.0, 2.0, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 0, 0), 1f));
                    player.getWorld().spawnParticle(Particle.DUST, loc, 4, 2.0, 1.0, 2.0, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 255, 0), 1f));
                    player.getWorld().spawnParticle(Particle.DUST, loc, 4, 2.0, 1.0, 2.0, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 0, 255), 1f));
                    player.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 2.0, 1.0, 2.0, 0.01);
                    break;
                case "bloom":
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 2.2, 1.2, 2.2, 0.01);
                    player.getWorld().spawnParticle(Particle.DUST, loc, 5, 2.0, 1.0, 2.0, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 182, 193), 1f));
                    break;
                case "lava":
                    player.getWorld().spawnParticle(Particle.FLAME, loc, 7, 2.2, 1.2, 2.2, 0.01);
                    player.getWorld().spawnParticle(Particle.LAVA, loc, 3, 2.0, 1.0, 2.0, 0.01);
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 2.0, 1.0, 2.0, 0.01);
                    break;
                case "crystal":
                    player.getWorld().spawnParticle(Particle.END_ROD, loc, 6, 2.2, 1.2, 2.2, 0.01);
                    player.getWorld().spawnParticle(Particle.SPLASH, loc, 5, 2.0, 1.0, 2.0, 0.01);
                    player.getWorld().spawnParticle(Particle.DUST, loc, 4, 2.0, 1.0, 2.0, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(173, 216, 230), 1f));
                    break;
                case "sand":
                    player.getWorld().spawnParticle(Particle.DUST, loc, 10, 2.5, 1.5, 2.5, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(210, 180, 140), 1.2f));
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 6, 2.0, 1.0, 2.0, 0.01);
                    break;
                case "firefly":
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 2.2, 1.2, 2.2, 0.01);
                    player.getWorld().spawnParticle(Particle.END_ROD, loc, 4, 2.0, 1.0, 2.0, 0.01);
                    break;
                default:
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 5, 1.5, 0.8, 1.5, 0.01);
            }
        };

        // Schedule repeating particle effect (visible to this player)
        if (threadSafety.isFolia() && player.isValid()) {
            var task = player.getScheduler().runAtFixedRate(plugin, (scheduledTask) -> {
                if (player.isOnline()) {
                    particleTask.run();
                } else {
                    scheduledTask.cancel();
                }
            }, null, 10L, 15L); // fairly frequent for nice effect, low cost
            activeWeatherTasks.put(player.getUniqueId(), task);
        } else {
            // Bukkit fallback (rare)
            var task = Bukkit.getScheduler().runTaskTimer(plugin, particleTask, 10L, 15L);
            activeWeatherTasks.put(player.getUniqueId(), task);
        }

        // Immediate burst
        particleTask.run();
    }

    private void stopWeatherForPlayer(Player player) {
        Object task = activeWeatherTasks.remove(player.getUniqueId());
        if (task != null) {
            try {
                if (task instanceof org.bukkit.scheduler.BukkitTask) {
                    ((org.bukkit.scheduler.BukkitTask) task).cancel();
                } else if (task instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask) {
                    ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).cancel();
                }
            } catch (Exception ignored) {}
        }
        // Particles are short-lived so no need to "remove" entities
    }

    public void onPlayerEnterIsland(Player player, Island island) {
        if (island == null || player == null || !player.isOnline()) return;
        IslandWeatherCosmetic weather = getActiveWeather(island.getId());
        if (weather != null && !weather.isNone()) {
            startParticleLoopForPlayer(player, weather, island);
        }
    }

    public void onPlayerLeaveIsland(Player player, Island island) {
        if (player != null) {
            stopWeatherForPlayer(player);
        }
    }

    public void loadActiveForIsland(String islandKey) {
        String weatherId = plugin.getDatabaseManager().loadIslandActiveWeather(islandKey);
        if (weatherId != null && !"NONE".equals(weatherId)) {
            try {
                IslandWeatherCosmetic w = IslandWeatherCosmetic.valueOf(weatherId);
                activeWeatherByIsland.put(islandKey, w);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Called from IslandManager on load to respawn effects if needed.
     */
    public void respawnWeatherForIsland(String islandKey, org.bukkit.World world) {
        // Particles are player-driven on enter; this can be no-op or future area effect.
        // For now, active is started on enter.
    }
}
