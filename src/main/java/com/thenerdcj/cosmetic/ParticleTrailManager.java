package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages personal particle trails / auras for players.
 * Folia-safe: uses per-player scheduler (EntityScheduler) for spawning.
 *
 * Unlock via: Prestige levels (some free at thresholds) + Slayer Tokens (GUI + SlayerShop).
 * Rich visual variety: layered particles, rainbow cycling, dust transitions, shaped auras.
 */
public class ParticleTrailManager {

    private final FoliaSkyblock plugin;

    // Player's currently active trail (NONE if disabled)
    private final Map<UUID, ParticleTrail> activeTrails = new ConcurrentHashMap<>();

    // Player's unlocked trails
    private final Map<UUID, Set<ParticleTrail>> unlockedTrails = new ConcurrentHashMap<>();

    // Running per-player tasks (Folia/Paper ScheduledTask)
    private final Map<UUID, ScheduledTask> activeTasks = new ConcurrentHashMap<>();

    // Simple per-player tick counters for rainbow / animation effects (lightweight)
    private final Map<UUID, Integer> playerTickCounters = new ConcurrentHashMap<>();

    public ParticleTrailManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public void loadPlayerTrails(UUID uuid) {
        Set<String> unlockedIds = plugin.getDatabaseManager().loadUnlockedParticleTrails(uuid);
        Set<ParticleTrail> unlocked = new HashSet<>();
        for (String id : unlockedIds) {
            try {
                unlocked.add(ParticleTrail.valueOf(id));
            } catch (IllegalArgumentException ignored) {}
        }
        unlockedTrails.put(uuid, unlocked);

        String activeId = plugin.getDatabaseManager().loadActiveParticleTrail(uuid);
        ParticleTrail active = ParticleTrail.NONE;
        if (activeId != null) {
            try {
                active = ParticleTrail.valueOf(activeId);
            } catch (IllegalArgumentException ignored) {}
        }

        if (active != ParticleTrail.NONE && unlocked.contains(active)) {
            activeTrails.put(uuid, active);
            // Will be started on join when player object is available
        }
    }

    public void savePlayerTrails(UUID uuid) {
        Set<ParticleTrail> unlocked = unlockedTrails.getOrDefault(uuid, Collections.emptySet());
        Set<String> unlockedIds = new HashSet<>();
        for (ParticleTrail t : unlocked) {
            unlockedIds.add(t.name());
        }

        ParticleTrail active = activeTrails.getOrDefault(uuid, ParticleTrail.NONE);
        String activeId = active == ParticleTrail.NONE ? "NONE" : active.name();

        plugin.getDatabaseManager().saveUnlockedParticleTrails(uuid, unlockedIds);
        plugin.getDatabaseManager().saveActiveParticleTrail(uuid, activeId);
    }

    public boolean unlockTrail(Player player, ParticleTrail trail) {
        if (trail == ParticleTrail.NONE) return false;

        Set<ParticleTrail> unlocked = unlockedTrails.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (unlocked.contains(trail)) return false;

        unlocked.add(trail);
        player.sendMessage("§a§lUnlocked Trail! §7» " + trail.getDisplayName());
        savePlayerTrails(player.getUniqueId());
        return true;
    }

    /**
     * Grant all trails that the given prestige level qualifies for (used on prestige-up for rewards).
     * Only grants those with tokenCost == 0 or that are now accessible.
     */
    public void grantPrestigeUnlocks(Player player, int newPrestigeLevel) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        Set<ParticleTrail> unlocked = unlockedTrails.computeIfAbsent(uuid, k -> new HashSet<>());
        boolean grantedAny = false;

        for (ParticleTrail trail : ParticleTrail.values()) {
            if (trail == ParticleTrail.NONE) continue;
            if (unlocked.contains(trail)) continue;
            if (trail.getMinPrestige() <= newPrestigeLevel) {
                // Prestige reward path: grant even if it had a token cost (prestige overrides for the reward ones)
                // Or only grant the intended free ones (tokenCost==0). We grant qualifying for nice feel.
                unlocked.add(trail);
                grantedAny = true;
                player.sendMessage("§d§lPrestige Reward! §7Unlocked " + trail.getDisplayName());
            }
        }
        if (grantedAny) {
            savePlayerTrails(uuid);
        }
    }

    public boolean setActiveTrail(Player player, ParticleTrail trail) {
        UUID uuid = player.getUniqueId();
        Set<ParticleTrail> unlocked = unlockedTrails.getOrDefault(uuid, Collections.emptySet());

        if (trail != ParticleTrail.NONE && !unlocked.contains(trail)) {
            player.sendMessage("§cYou haven't unlocked this trail yet.");
            return false;
        }

        stopTrailTask(uuid);

        if (trail == ParticleTrail.NONE) {
            activeTrails.remove(uuid);
            playerTickCounters.remove(uuid);
            player.sendMessage("§7Particle trail disabled.");
        } else {
            activeTrails.put(uuid, trail);
            playerTickCounters.putIfAbsent(uuid, 0);
            startTrailTask(player, trail);
            player.sendMessage("§a§lTrail Activated: §e" + trail.getDisplayName());
        }

        savePlayerTrails(uuid);
        return true;
    }

    public ParticleTrail getActiveTrail(UUID uuid) {
        return activeTrails.getOrDefault(uuid, ParticleTrail.NONE);
    }

    public Set<ParticleTrail> getUnlockedTrails(UUID uuid) {
        return Collections.unmodifiableSet(unlockedTrails.getOrDefault(uuid, Collections.emptySet()));
    }

    private void startTrailTask(Player player, ParticleTrail trail) {
        if (player == null || trail == ParticleTrail.NONE) return;

        stopTrailTask(player.getUniqueId());

        // Folia-safe: per-player scheduler (runs on the player's entity region thread)
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline()) {
                stopTrailTask(player.getUniqueId());
                return;
            }
            // Advance animation tick for rainbow / pulsing effects
            int tick = playerTickCounters.merge(player.getUniqueId(), 1, Integer::sum);

            spawnTrailParticles(player, trail, tick);
        }, null, 5L, 5L);  // initialDelay 5, period 5 ticks

        activeTasks.put(player.getUniqueId(), task);
    }

    private void stopTrailTask(UUID uuid) {
        ScheduledTask task = activeTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        playerTickCounters.remove(uuid);
    }

    /**
     * Rich visual variety implementation.
     * Different behaviors per trail type: layers, rainbow hue cycling via DustTransition + time, shaped rings, mixed particles.
     */
    private void spawnTrailParticles(Player player, ParticleTrail trail, int tick) {
        if (trail.getParticle() == null) return;

        Location loc = player.getLocation().add(0, 0.9, 0);  // chest height
        org.bukkit.World world = player.getWorld();

        try {
            Particle particle = trail.getParticle();
            Color baseColor = trail.getColor();

            switch (trail) {
                case RAINBOW_DUST -> {
                    // Smooth rainbow cycling using HSL -> RGB
                    float hue = (tick % 360) / 360.0f;
                    Color rgb = hsbToColor(hue, 0.9f, 1.0f);
                    Particle.DustTransition dt = new Particle.DustTransition(rgb, Color.WHITE, 1.1f);
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 6, 0.45, 0.65, 0.45, 0.0, dt);

                    // Extra sparkle layer
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.4, 0), 2, 0.2, 0.3, 0.2, 0.01);
                }
                case DUST_TRAIL -> {
                    Color c = (baseColor != null) ? baseColor : Color.fromRGB(255, 200, 50);
                    Particle.DustOptions dust = new Particle.DustOptions(c, 1.15f);
                    world.spawnParticle(particle, loc, 5, 0.35, 0.55, 0.35, 0.0, dust);
                    // subtle secondary
                    world.spawnParticle(Particle.SMALL_FLAME, loc.clone().add(0, 0.3, 0), 1, 0.2, 0.2, 0.2, 0.0);
                }
                case HEART_AURA -> {
                    // Layered heart + happy
                    world.spawnParticle(Particle.HEART, loc, 3, 0.35, 0.5, 0.35, 0.0);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.35, 0), 2, 0.25, 0.25, 0.25, 0.0);
                }
                case DRAGON_BREATH -> {
                    // Dramatic high-prestige aura - thick + lingering
                    world.spawnParticle(Particle.DRAGON_BREATH, loc, 8, 0.5, 0.7, 0.5, 0.015);
                    world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 0.6, 0), 4, 0.3, 0.4, 0.3, 0.02);
                }
                case SOUL_TRAIL -> {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 4, 0.3, 0.55, 0.3, 0.01);
                    world.spawnParticle(Particle.SOUL, loc.clone().add(0, 0.25, 0), 3, 0.25, 0.3, 0.25, 0.0);
                }
                case VOID_AURA, PORTAL_AURA -> {
                    world.spawnParticle(Particle.REVERSE_PORTAL, loc, 7, 0.4, 0.6, 0.4, 0.02);
                    world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 0.3, 0), 3, 0.25, 0.35, 0.25, 0.01);
                }
                case ELECTRIC_TRAIL -> {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 5, 0.4, 0.6, 0.4, 0.02);
                    if (tick % 3 == 0) {
                        world.spawnParticle(Particle.CRIT, loc.clone().add(0, 0.4, 0), 2, 0.15, 0.2, 0.15, 0.3);
                    }
                }
                case CRIT_SPARK -> {
                    world.spawnParticle(Particle.CRIT, loc, 4, 0.35, 0.5, 0.35, 0.25);
                    world.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0, 0.2, 0), 2, 0.2, 0.3, 0.2, 0.1);
                }
                case NOTE_TRAIL -> {
                    // Musical notes with slight color variation
                    world.spawnParticle(Particle.NOTE, loc, 3, 0.3, 0.5, 0.3, 0.0);
                    if (tick % 4 == 0) {
                        world.spawnParticle(Particle.NOTE, loc.clone().add(0.2, 0.1, -0.15), 1, 0.1, 0.1, 0.1, 0.5);
                    }
                }
                case FLAME_TRAIL, LAVA_TRAIL -> {
                    world.spawnParticle(particle, loc, 4, 0.3, 0.55, 0.3, 0.01);
                    // Extra embers / lava pop layer
                    world.spawnParticle(Particle.LAVA, loc.clone().add(0, 0.15, 0), 1, 0.15, 0.2, 0.15, 0.0);
                }
                default -> {
                    // Generic good looking default with slight offset variety
                    if (baseColor != null && particle == Particle.DUST) {
                        Particle.DustOptions d = new Particle.DustOptions(baseColor, 1.0f);
                        world.spawnParticle(particle, loc, 4, 0.32, 0.5, 0.32, 0.0, d);
                    } else {
                        world.spawnParticle(particle, loc, 5, 0.38, 0.55, 0.38, 0.008);
                    }
                }
            }
        } catch (Exception e) {
            // Robust fallback
            try {
                world.spawnParticle(Particle.END_ROD, loc, 2, 0.25, 0.35, 0.25, 0.01);
            } catch (Exception ignored) {}
        }
    }

    /** Simple HSL to Color conversion for rainbow effects (no external deps) */
    private Color hsbToColor(float hue, float saturation, float brightness) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        stopTrailTask(uuid);
        savePlayerTrails(uuid);
        playerTickCounters.remove(uuid);
    }

    public void onPlayerJoin(Player player) {
        loadPlayerTrails(player.getUniqueId());
        UUID uuid = player.getUniqueId();
        ParticleTrail active = activeTrails.get(uuid);
        if (active != ParticleTrail.NONE && unlockedTrails.getOrDefault(uuid, Collections.emptySet()).contains(active)) {
            startTrailTask(player, active);
        }
    }

    /** For admin/debug or future weekly resets if desired */
    public void clearAllForPlayer(UUID uuid) {
        stopTrailTask(uuid);
        unlockedTrails.remove(uuid);
        activeTrails.remove(uuid);
        playerTickCounters.remove(uuid);
        plugin.getDatabaseManager().saveUnlockedParticleTrails(uuid, Collections.emptySet());
        plugin.getDatabaseManager().saveActiveParticleTrail(uuid, "NONE");
    }
}