package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for Advanced Overhead Cosmetics (TextDisplay + particles above player head).
 * Follows the established Play-to-Win + Folia-safe pattern.
 */
public class OverheadCosmeticManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<OverheadCosmetic>> owned = new ConcurrentHashMap<>();
    private final Map<UUID, OverheadCosmetic> active = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.entity.Entity> activeDisplay = new ConcurrentHashMap<>();

    private static final int[] COLLECTION_MILESTONES = {3, 5, 8, 12};

    // Bounded for large scale CHM compression (100s-1000+ players)
    private static final int MAX_PLAYERS_OVERHEAD = 5000;

    public OverheadCosmeticManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
        // Periodic bound + eviction
        threadSafety.runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
    }

    public Set<OverheadCosmetic> getOwned(UUID uuid) {
        return owned.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public OverheadCosmetic getActive(UUID uuid) {
        return active.getOrDefault(uuid, OverheadCosmetic.NONE);
    }

    public void unlock(UUID uuid, OverheadCosmetic cosmetic) {
        if (cosmetic == null || cosmetic.isNone()) return;
        Set<OverheadCosmetic> set = getOwned(uuid);
        boolean wasNew = set.add(cosmetic);
        if (wasNew) {
            int xp = Math.max(15, cosmetic.getRarity().getXpReward() / 3);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && plugin.getConfig().getBoolean("overhead.collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(p, xp);
                p.sendMessage("§a§lOverhead Cosmetic Collection §7» Unlocked " + cosmetic.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = set.size();
                for (int m : COLLECTION_MILESTONES) {
                    if (count == m) {
                        p.sendMessage("§6§l★ Overhead Collector Milestone! ★ §e" + count + " unique overhead effects unlocked!");
                        break;
                    }
                }
            }
            // Persistence will be wired once DB methods exist
        }
    }

    public boolean has(UUID uuid, OverheadCosmetic c) {
        if (c == null || c.isNone()) return true;
        return getOwned(uuid).contains(c);
    }

    public void setActive(Player player, OverheadCosmetic cosmetic) {
        if (cosmetic == null) cosmetic = OverheadCosmetic.NONE;
        if (!cosmetic.isNone() && !has(player.getUniqueId(), cosmetic)) {
            player.sendMessage("§cYou have not unlocked this overhead cosmetic.");
            return;
        }

        removeActive(player);
        active.put(player.getUniqueId(), cosmetic);

        if (!cosmetic.isNone()) {
            spawnOverhead(player, cosmetic);
        }
    }

    private void spawnOverhead(Player player, OverheadCosmetic cosmetic) {
        Runnable task = () -> {
            if (!player.isOnline() || !active.containsKey(player.getUniqueId())) return;
            updateDisplay(player, cosmetic);
        };

        if (threadSafety.isFolia() && player.isValid()) {
            player.getScheduler().runAtFixedRate(plugin, (scheduledTask) -> {
                if (!player.isOnline() || !active.containsKey(player.getUniqueId())) {
                    scheduledTask.cancel();
                    return;
                }
                updateDisplay(player, cosmetic);
            }, null, 1L, 20L);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, 20L);
        }
    }

    private void updateDisplay(Player player, OverheadCosmetic cosmetic) {
        Location loc = player.getLocation().add(0, 2.3, 0);

        org.bukkit.entity.Entity old = activeDisplay.remove(player.getUniqueId());
        if (old != null && old.isValid()) old.remove();

        TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class);
        String type = cosmetic.getEffectType();

        if ("title".equals(type)) {
            // Titles expansion UI polish: distinctive readable floating titles (not just halo text)
            // Decorative framing + player context feel; larger/emphatic for titles vs other effects
            // Deeper polish: varied framing/particles per title for runic/void/celestial etc.
            String frame = "§f✦ ";
            String endFrame = " §f✦";
            if (cosmetic.name().contains("RUNIC") || cosmetic.name().contains("SIGIL")) {
                frame = "§6ᚱ ";
                endFrame = " §6ᚱ";
            } else if (cosmetic.name().contains("VOID")) {
                frame = "§5◌ ";
                endFrame = " §5◌";
            } else if (cosmetic.name().contains("CELESTIAL")) {
                frame = "§b✧ ";
                endFrame = " §b✧";
            } else if (cosmetic.name().contains("ETHEREAL")) {
                frame = "§d❖ ";
                endFrame = " §d❖";
            }
            display.setText(frame + cosmetic.getDisplayName() + endFrame);
            display.setBillboard(TextDisplay.Billboard.CENTER);
            display.setSeeThrough(false); // solid for readability
            display.setShadowed(true);
            float titleScale = 0.9f;
            if (cosmetic.name().contains("CROWN") || cosmetic.name().contains("SIGIL")) titleScale = 1.05f;
            display.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0, 0.05f, 0),
                new org.joml.AxisAngle4f(),
                new org.joml.Vector3f(titleScale, titleScale, titleScale),
                new org.joml.AxisAngle4f()
            ));
            // subtle title flair particles (kept light) + variety for deeper titles
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 3, 0.3, 0.2, 0.3, 0.01);
        } else if ("crown".equals(type)) {
            display.setText("§6👑 " + cosmetic.getDisplayName());
            display.setBillboard(TextDisplay.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0, 0.1f, 0),
                new org.joml.AxisAngle4f(),
                new org.joml.Vector3f(1.2f, 1.2f, 1.2f),
                new org.joml.AxisAngle4f()
            ));
        } else {
            display.setText(cosmetic.getDisplayName());
            display.setBillboard(TextDisplay.Billboard.CENTER);
            display.setSeeThrough(true);
        }
        display.setShadowed(true);

        // Enhanced particle variety per type
        if ("halo".equals(type) || "aura".equals(type)) {
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 4, 0.5, 0.3, 0.5, 0.01);
            player.getWorld().spawnParticle(Particle.FIREWORK, loc, 2, 0.3, 0.2, 0.3, 0.01);
        } else if ("glow".equals(type)) {
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 6, 0.4, 0.3, 0.4, 0.02);
        } else if ("heart".equals(type)) {
            player.getWorld().spawnParticle(Particle.HEART, loc, 1, 0.2, 0.1, 0.2, 0.0);
        } else if ("legendary".equals(type)) {
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 3, 0.4, 0.2, 0.4, 0.01);
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.4, 0.2, 0.4, 0.01);
        } else if ("crown".equals(type)) {
            player.getWorld().spawnParticle(Particle.CRIT, loc, 5, 0.4, 0.2, 0.4, 0.01);
        } else if ("rainbow".equals(type)) {
            player.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.5, 0.3, 0.5, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(255,0,0), 1f));
            player.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.5, 0.3, 0.5, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(0,255,0), 1f));
        } else if ("dragon".equals(type)) {
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 5, 0.5, 0.3, 0.5, 0.01);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 3, 0.4, 0.2, 0.4, 0.01);
        } else if ("book".equals(type)) {
            display.setText("§a📖 " + cosmetic.getDisplayName());
            display.setBillboard(TextDisplay.Billboard.CENTER);
            display.setSeeThrough(true);
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 6, 0.4, 0.3, 0.4, 0.01);
        } else if ("eyes".equals(type)) {
            display.setText("§c👀");
            display.setBillboard(TextDisplay.Billboard.CENTER);
            display.setSeeThrough(true);
            player.getWorld().spawnParticle(Particle.FLAME, loc, 4, 0.3, 0.2, 0.3, 0.01);
        } else if ("ice".equals(type)) {
            display.setText("§b❄ " + cosmetic.getDisplayName());
            display.setBillboard(TextDisplay.Billboard.CENTER);
            display.setSeeThrough(true);
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 8, 0.5, 0.3, 0.5, 0.01);
            player.getWorld().spawnParticle(Particle.DUST, loc, 4, 0.4, 0.2, 0.4, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(200,230,255), 1f));
        }

        // Title-specific extra particles for deeper polish (runes get magic, void dark, celestial star etc.)
        if ("title".equals(type)) {
            String tname = cosmetic.name();
            if (tname.contains("RUNIC") || tname.contains("SIGIL")) {
                player.getWorld().spawnParticle(Particle.CRIT, loc, 4, 0.4, 0.2, 0.4, 0.01);
                player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 2, 0.3, 0.1, 0.3, 0.01);
            } else if (tname.contains("VOID")) {
                player.getWorld().spawnParticle(Particle.PORTAL, loc, 5, 0.3, 0.2, 0.3, 0.01);
                player.getWorld().spawnParticle(Particle.SMOKE, loc, 2, 0.2, 0.1, 0.2, 0.01);
            } else if (tname.contains("CELESTIAL")) {
                player.getWorld().spawnParticle(Particle.END_ROD, loc, 4, 0.4, 0.2, 0.4, 0.01);
            } else if (tname.contains("ETHEREAL")) {
                player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc, 3, 0.3, 0.2, 0.3, 0.01);
            }
        }

        activeDisplay.put(player.getUniqueId(), display);
    }

    public void removeActive(Player player) {
        active.remove(player.getUniqueId());
        org.bukkit.entity.Entity e = activeDisplay.remove(player.getUniqueId());
        if (e != null && e.isValid()) e.remove();
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        removeActive(player);
        owned.remove(player.getUniqueId());
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
        OverheadCosmetic current = getActive(player.getUniqueId());
        if (!current.isNone()) {
            spawnOverhead(player, current);
        }
    }

    // Persistence
    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerOverheadCosmetics(uuid);
        Set<OverheadCosmetic> set = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                OverheadCosmetic c = OverheadCosmetic.valueOf(id);
                if (!c.isNone()) set.add(c);
            } catch (Exception ignored) {}
        }
        owned.put(uuid, set);
    }

    public void savePlayer(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (OverheadCosmetic c : getOwned(uuid)) ids.add(c.name());
        plugin.getDatabaseManager().savePlayerOverheadCosmetics(uuid, ids);
    }

    /**
     * Bounded CHM eviction for owned/active/activeDisplay (large scale memory compression).
     * Per "more CHM bounds in all managers (e.g. other cosmetics like trails/pets if unbounded)".
     */
    private void cleanupCaches() {
        if (owned.size() > MAX_PLAYERS_OVERHEAD) {
            java.util.Iterator<UUID> it = owned.keySet().iterator();
            int toRemove = owned.size() - (MAX_PLAYERS_OVERHEAD - 500);
            while (it.hasNext() && toRemove > 0) {
                UUID u = it.next(); it.remove();
                active.remove(u);
                org.bukkit.entity.Entity e = activeDisplay.remove(u);
                if (e != null && e.isValid()) e.remove();
                toRemove--;
            }
        }
        if (active.size() > MAX_PLAYERS_OVERHEAD) {
            java.util.Iterator<UUID> it = active.keySet().iterator();
            int toRemove = active.size() - (MAX_PLAYERS_OVERHEAD - 500);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
        if (activeDisplay.size() > MAX_PLAYERS_OVERHEAD) {
            java.util.Iterator<UUID> it = activeDisplay.keySet().iterator();
            int toRemove = activeDisplay.size() - (MAX_PLAYERS_OVERHEAD - 500);
            while (it.hasNext() && toRemove > 0) {
                UUID u = it.next();
                org.bukkit.entity.Entity e = activeDisplay.remove(u);
                if (e != null && e.isValid()) e.remove();
                toRemove--;
            }
        }
    }
}
