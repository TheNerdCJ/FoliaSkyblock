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
 * Folia-safe: uses per-player EntityScheduler for spawning.
 *
 * Unlock via: Prestige levels (many free at thresholds + high-prestige rewards) + Slayer Tokens.
 *
 * Now features 30+ trails including advanced shaped/animated effects:
 * Angel Wings, Helix, Double Helix, Rotating Rings, Galaxy Swirl, Orbiting Orbs, Footsteps, etc.
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
     * Supports many shaped and animated effects for the expanded trail list.
     */
    private void spawnTrailParticles(Player player, ParticleTrail trail, int tick) {
        if (trail.getParticle() == null) return;

        Location loc = player.getLocation().add(0, 0.85, 0);
        org.bukkit.World world = player.getWorld();

        try {
            Particle particle = trail.getParticle();
            Color baseColor = trail.getColor();
            float yaw = player.getLocation().getYaw();

            switch (trail) {

                // === EXISTING SPECIAL CASES (kept & slightly improved) ===
                case RAINBOW_DUST -> {
                    float hue = (tick % 360) / 360.0f;
                    Color rgb = hsbToColor(hue, 0.9f, 1.0f);
                    Particle.DustTransition dt = new Particle.DustTransition(rgb, Color.WHITE, 1.15f);
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 7, 0.5, 0.7, 0.5, 0.0, dt);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.45, 0), 3, 0.25, 0.35, 0.25, 0.01);
                }
                case DUST_TRAIL -> {
                    Color c = (baseColor != null) ? baseColor : Color.fromRGB(255, 200, 50);
                    Particle.DustOptions dust = new Particle.DustOptions(c, 1.2f);
                    world.spawnParticle(particle, loc, 6, 0.4, 0.6, 0.4, 0.0, dust);
                    world.spawnParticle(Particle.SMALL_FLAME, loc.clone().add(0, 0.35, 0), 2, 0.25, 0.25, 0.25, 0.0);
                }
                case HEART_AURA -> {
                    world.spawnParticle(Particle.HEART, loc, 4, 0.4, 0.55, 0.4, 0.0);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.4, 0), 3, 0.3, 0.3, 0.3, 0.0);
                }
                case DRAGON_BREATH -> {
                    world.spawnParticle(Particle.DRAGON_BREATH, loc, 9, 0.55, 0.75, 0.55, 0.02);
                    world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 0.65, 0), 5, 0.35, 0.45, 0.35, 0.02);
                }
                case SOUL_TRAIL -> {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 5, 0.35, 0.6, 0.35, 0.015);
                    world.spawnParticle(Particle.SOUL, loc.clone().add(0, 0.3, 0), 4, 0.3, 0.35, 0.3, 0.0);
                }
                case VOID_AURA, PORTAL_AURA -> {
                    world.spawnParticle(Particle.REVERSE_PORTAL, loc, 8, 0.45, 0.65, 0.45, 0.025);
                    world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 0.35, 0), 4, 0.3, 0.4, 0.3, 0.015);
                }
                case ELECTRIC_TRAIL -> {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 6, 0.45, 0.65, 0.45, 0.025);
                    if (tick % 3 == 0) {
                        world.spawnParticle(Particle.CRIT, loc.clone().add(0, 0.45, 0), 3, 0.2, 0.25, 0.2, 0.4);
                    }
                }
                case CRIT_SPARK -> {
                    world.spawnParticle(Particle.CRIT, loc, 5, 0.4, 0.55, 0.4, 0.3);
                    world.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0, 0.25, 0), 3, 0.25, 0.35, 0.25, 0.15);
                }
                case NOTE_TRAIL -> {
                    world.spawnParticle(Particle.NOTE, loc, 4, 0.35, 0.55, 0.35, 0.0);
                    if (tick % 3 == 0) {
                        world.spawnParticle(Particle.NOTE, loc.clone().add(0.25, 0.15, -0.2), 1, 0.1, 0.1, 0.1, 0.6);
                    }
                }
                case FLAME_TRAIL, LAVA_TRAIL -> {
                    world.spawnParticle(particle, loc, 5, 0.35, 0.6, 0.35, 0.015);
                    world.spawnParticle(Particle.LAVA, loc.clone().add(0, 0.2, 0), 2, 0.2, 0.25, 0.2, 0.0);
                }

                // === NEW EXPANDED EFFECTS ===

                case LEAF_TRAIL -> {
                    world.spawnParticle(Particle.CHERRY_LEAVES, loc, 5, 0.4, 0.7, 0.4, 0.02);
                    if (tick % 2 == 0) {
                        world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.3, 0), 2, 0.3, 0.3, 0.3, 0.0);
                    }
                }
                case SPARKLE_AURA -> {
                    world.spawnParticle(Particle.WAX_ON, loc, 6, 0.45, 0.6, 0.45, 0.0);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0), 2, 0.2, 0.3, 0.2, 0.01);
                }
                case HOLY_AURA -> {
                    world.spawnParticle(Particle.END_ROD, loc, 4, 0.3, 0.8, 0.3, 0.0);
                    if (tick % 4 == 0) {
                        world.spawnParticle(Particle.FIREWORK, loc.clone().add(0, 0.6, 0), 1, 0.1, 0.1, 0.1, 0.05);
                    }
                }
                case FIREWORK_TRAIL -> {
                    world.spawnParticle(Particle.FIREWORK, loc, 3, 0.3, 0.5, 0.3, 0.0);
                    world.spawnParticle(Particle.POOF, loc.clone().add(0, 0.4, 0), 1, 0.15, 0.15, 0.15, 0.0);
                }
                case PULSE_AURA -> {
                    double pulse = 1.0 + Math.sin(tick * 0.15) * 0.3;
                    world.spawnParticle(Particle.CRIT, loc, (int)(5 * pulse), 0.4, 0.6, 0.4, 0.2);
                }
                case METEOR_TRAIL -> {
                    world.spawnParticle(Particle.LAVA, loc, 4, 0.3, 0.5, 0.3, 0.02);
                    world.spawnParticle(Particle.SMALL_FLAME, loc.clone().add(0, -0.3, 0), 3, 0.25, 0.4, 0.25, 0.0);
                }
                case BUBBLE_AURA -> {
                    world.spawnParticle(Particle.BUBBLE_POP, loc, 6, 0.4, 0.6, 0.4, 0.01);
                    world.spawnParticle(Particle.SPLASH, loc.clone().add(0, 0.3, 0), 2, 0.2, 0.2, 0.2, 0.0);
                }
                case FROST_AURA -> {
                    world.spawnParticle(Particle.SNOWFLAKE, loc, 5, 0.4, 0.65, 0.4, 0.015);
                    world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.4, 0), 2, 0.25, 0.2, 0.25, 0.0);
                }

                // Shaped / Animated effects
                case WING_AURA -> spawnWingEffect(world, loc, yaw, tick);
                case ORBITING_ORBS -> spawnOrbitingOrbs(world, loc, tick);
                case FOOTSTEP_TRAIL -> spawnFootstepTrail(player, world, loc, tick);
                case HELIX_TRAIL -> spawnHelixEffect(world, loc, tick, false);
                case DOUBLE_HELIX -> spawnHelixEffect(world, loc, tick, true);
                case RING_AURA -> spawnRotatingRings(world, loc, tick);
                case GALAXY_AURA -> spawnGalaxySwirl(world, loc, tick);
                case SHADOW_AURA -> {
                    world.spawnParticle(Particle.ASH, loc, 7, 0.45, 0.7, 0.45, 0.01);
                    world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.3, 0), 3, 0.3, 0.4, 0.3, 0.0);
                }
                case ASCENDING_SPIRAL -> spawnAscendingSpiral(world, loc, tick);

                case COSMIC_WINGS -> {
                    spawnWingEffect(world, loc, player.getLocation().getYaw(), tick);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.9, 0), 2, 0.15, 0.1, 0.15, 0.0);
                }
                case ETERNAL_FLAME -> {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 5, 0.35, 0.6, 0.35, 0.015);
                    world.spawnParticle(Particle.SOUL, loc.clone().add(0, 0.5, 0), 3, 0.2, 0.3, 0.2, 0.0);
                }

                // New expanded trails
                case VINE_TRAIL -> {
                    world.spawnParticle(Particle.CHERRY_LEAVES, loc, 5, 0.4, 0.6, 0.4, 0.01);
                    if (tick % 4 == 0) {
                        world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.2, 0), 2, 0.25, 0.25, 0.25, 0.0);
                    }
                }
                case FLOWER_AURA -> {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 6, 0.45, 0.65, 0.45, 0.0);
                    world.spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 0.4, 0), 3, 0.3, 0.3, 0.3, 0.02);
                }
                case MUSHROOM_TRAIL -> {
                    world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc, 4, 0.35, 0.55, 0.35, 0.01);
                    if (tick % 5 == 0) {
                        world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.1, 0), 1, 0.1, 0.1, 0.1, 0.0);
                    }
                }
                case ARCANE_AURA -> {
                    world.spawnParticle(Particle.ENCHANT, loc, 5, 0.4, 0.6, 0.4, 0.0);
                    world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 0.3, 0), 3, 0.25, 0.35, 0.25, 0.01);
                }
                case STAR_TRAIL -> {
                    world.spawnParticle(Particle.FIREWORK, loc, 3, 0.3, 0.5, 0.3, 0.0);
                    if (tick % 2 == 0) {
                        world.spawnParticle(Particle.END_ROD, loc.clone().add(0.2, 0.4, -0.15), 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
                case RUNE_CIRCLE -> spawnRuneCircle(world, loc, tick);
                case WIND_TRAIL -> {
                    world.spawnParticle(Particle.CLOUD, loc, 4, 0.4, 0.6, 0.4, 0.01);
                    world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.2, 0), 2, 0.2, 0.3, 0.2, 0.0);
                }
                case EARTH_AURA -> {
                    Color earth = Color.fromRGB(139, 90, 43);
                    Particle.DustOptions dust = new Particle.DustOptions(earth, 1.1f);
                    world.spawnParticle(Particle.DUST, loc, 5, 0.4, 0.55, 0.4, 0.0, dust);
                    if (tick % 3 == 0) {
                        world.spawnParticle(Particle.DUST, loc.clone().add(0, -0.2, 0), 2, 0.2, 0.1, 0.2, 0.0, new Particle.DustOptions(Color.fromRGB(139, 90, 43), 0.8f));
                    }
                }
                case THUNDER_AURA -> {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 4, 0.35, 0.55, 0.35, 0.02);
                    if (tick % 4 == 0) {
                        world.spawnParticle(Particle.FLASH, loc.clone().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0.0);
                    }
                }
                case CONFETTI_BURST -> {
                    world.spawnParticle(Particle.FIREWORK, loc, 5, 0.45, 0.6, 0.45, 0.0);
                    // Use multiple colored dusts for confetti feel
                    Particle.DustOptions pink = new Particle.DustOptions(Color.fromRGB(255, 105, 180), 0.9f);
                    Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(0, 255, 255), 0.9f);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 0.3, 0), 2, 0.25, 0.25, 0.25, 0.0, pink);
                    world.spawnParticle(Particle.DUST, loc.clone().add(0.1, 0.35, -0.1), 2, 0.25, 0.25, 0.25, 0.0, cyan);
                }
                case HEART_RING -> spawnHeartRing(world, loc, tick);
                case STAR_BURST -> spawnStarBurst(world, loc, tick);
                case NEBULA_AURA -> {
                    world.spawnParticle(Particle.PORTAL, loc, 6, 0.45, 0.65, 0.45, 0.02);
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.4, 0), 4, 0.3, 0.4, 0.3, 0.01);
                }

                // Footprints (leave ground marks when moving - rlTrails inspired)
                case FOOTPRINT_STONE, FOOTPRINT_LILY, FOOTPRINT_CREEPER -> spawnFootprintTrail(player, world, loc, trail, tick);
                case FOOTPRINT_RAINBOW -> spawnRainbowFootprint(player, world, loc, tick);

                // Spheres (surrounding body - PlayerParticles/rlTrails spheres)
                case GUARDIAN_SPHERE, GALAXY_SPHERE, CRYSTAL_SPHERE -> spawnSphereAura(world, loc, trail, tick);

                // Expanded Wings (more animated variants)
                case DRAGON_WINGS, BAT_WINGS, FAIRY_WINGS -> spawnAdvancedWing(world, loc, player.getLocation().getYaw(), trail, tick);
                case RAINBOW_WINGS -> spawnRainbowWing(world, loc, player.getLocation().getYaw(), tick);

                // Halos (above head)
                case HALO_GOLD, HALO_ANGEL, HALO_DEMON -> spawnHaloEffect(world, loc, trail, tick);

                // Vortexes
                case VOID_VORTEX, WATER_VORTEX -> spawnVortexEffect(world, loc, trail, tick);

                // Waves / expanding
                case GROUND_WAVE, SHOCKWAVE -> spawnWaveEffect(world, loc, trail, tick);

                // Arches / themed
                case CRYSTAL_ARCH, MAGIC_ARCH -> spawnArchEffect(world, loc, trail, tick);
                case MAGIC_CIRCLE -> spawnMagicCircle(world, loc, tick);
                case GUARDIAN_AURA -> {
                    world.spawnParticle(Particle.BUBBLE_POP, loc, 6, 0.45, 0.65, 0.45, 0.015);
                    world.spawnParticle(Particle.GLOW, loc.clone().add(0, 0.5, 0), 3, 0.3, 0.4, 0.3, 0.0);
                }
                case MYTHIC_AURA -> {
                    world.spawnParticle(Particle.DRAGON_BREATH, loc, 5, 0.4, 0.6, 0.4, 0.02);
                    if (tick % 3 == 0) world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 0.7, 0), 2, 0.2, 0.3, 0.2, 0.01);
                }
                case CUTE_HEARTS -> world.spawnParticle(Particle.HEART, loc, 5, 0.4, 0.6, 0.4, 0.0);
                case ANGRY_SPARKS -> {
                    world.spawnParticle(Particle.CRIT, loc, 4, 0.35, 0.55, 0.35, 0.25);
                    if (tick % 2 == 0) world.spawnParticle(Particle.ANGRY_VILLAGER, loc.clone().add(0, 0.4, 0), 1, 0.1, 0.1, 0.1, 0.0);
                }
                case LAVA_POP -> {
                    world.spawnParticle(Particle.LAVA, loc, 4, 0.3, 0.5, 0.3, 0.01);
                    if (tick % 4 == 0) world.spawnParticle(Particle.LAVA, loc.clone().add(0, 0.2, 0), 2, 0.15, 0.2, 0.15, 0.0);
                }
                case SLINKY_TRAIL -> {
                    double bounce = Math.sin(tick * 0.2) * 0.3;
                    world.spawnParticle(Particle.NOTE, loc.clone().add(0, bounce, 0), 4, 0.3, 0.5, 0.3, 0.0);
                }
                case SHOOTER_TRAIL -> {
                    world.spawnParticle(Particle.CRIT, loc, 4, 0.35, 0.5, 0.35, 0.3);
                    if (tick % 3 == 0) world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0.0);
                }
                case MAGICIAN_CIRCLE -> {
                    double r = 0.8;
                    for (int i = 0; i < 8; i++) {
                        double a = Math.toRadians((tick * 6 + i * 45) % 360);
                        world.spawnParticle(Particle.WITCH, loc.clone().add(Math.cos(a) * r, 0.4, Math.sin(a) * r), 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }

                default -> {
                    // Generic fallback with slight improvements
                    if (baseColor != null && particle == Particle.DUST) {
                        Particle.DustOptions d = new Particle.DustOptions(baseColor, 1.05f);
                        world.spawnParticle(particle, loc, 5, 0.38, 0.55, 0.38, 0.0, d);
                    } else {
                        world.spawnParticle(particle, loc, 6, 0.42, 0.6, 0.42, 0.01);
                    }
                }
            }
        } catch (Exception e) {
            // Safe fallback
            try {
                world.spawnParticle(Particle.END_ROD, loc, 2, 0.25, 0.35, 0.25, 0.01);
            } catch (Exception ignored) {}
        }
    }

    // ==================== NEW SHAPED EFFECT HELPERS ====================

    private void spawnWingEffect(org.bukkit.World world, Location loc, float yaw, int tick) {
        double angle = Math.toRadians(yaw);
        double leftX = Math.cos(angle + Math.PI / 2) * 0.7;
        double leftZ = Math.sin(angle + Math.PI / 2) * 0.7;
        double rightX = Math.cos(angle - Math.PI / 2) * 0.7;
        double rightZ = Math.sin(angle - Math.PI / 2) * 0.7;

        for (int i = -2; i <= 2; i++) {
            double yOffset = i * 0.12;
            Location left = loc.clone().add(leftX * (1 + Math.abs(i) * 0.1), 0.3 + yOffset, leftZ * (1 + Math.abs(i) * 0.1));
            Location right = loc.clone().add(rightX * (1 + Math.abs(i) * 0.1), 0.3 + yOffset, rightZ * (1 + Math.abs(i) * 0.1));
            world.spawnParticle(Particle.END_ROD, left, 1, 0.02, 0.02, 0.02, 0.0);
            world.spawnParticle(Particle.END_ROD, right, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void spawnOrbitingOrbs(org.bukkit.World world, Location center, int tick) {
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians((tick * 8 + i * 120) % 360);
            double x = Math.cos(angle) * 0.7;
            double z = Math.sin(angle) * 0.7;
            Location orbLoc = center.clone().add(x, 0.3 + Math.sin(tick * 0.1 + i) * 0.2, z);
            world.spawnParticle(Particle.ENCHANT, orbLoc, 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private void spawnFootstepTrail(Player player, org.bukkit.World world, Location loc, int tick) {
        if (player.isOnGround() && tick % 2 == 0) {
            Location foot = player.getLocation().add(0, 0.15, 0);
            Color c = Color.fromRGB(180, 220, 255);
            Particle.DustOptions dust = new Particle.DustOptions(c, 0.9f);
            world.spawnParticle(Particle.DUST, foot, 3, 0.15, 0.05, 0.15, 0.0, dust);
        }
    }

    private void spawnHelixEffect(org.bukkit.World world, Location center, int tick, boolean doubleHelix) {
        for (int i = 0; i < 2; i++) {
            double angle = Math.toRadians((tick * 12 + i * 180) % 360);
            double x = Math.cos(angle) * 0.55;
            double z = Math.sin(angle) * 0.55;
            double y = ((tick % 20) / 20.0) * 1.6;
            Location p = center.clone().add(x, y - 0.3, z);
            world.spawnParticle(Particle.PORTAL, p, 1, 0.0, 0.0, 0.0, 0.0);

            if (doubleHelix) {
                double angle2 = angle + Math.PI;
                double x2 = Math.cos(angle2) * 0.55;
                double z2 = Math.sin(angle2) * 0.55;
                Location p2 = center.clone().add(x2, y - 0.3, z2);
                world.spawnParticle(Particle.REVERSE_PORTAL, p2, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void spawnRotatingRings(org.bukkit.World world, Location center, int tick) {
        double radius = 0.8;
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians((tick * 6 + i * 45) % 360);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Color c = Color.fromRGB(255, 100 + (int)(Math.sin(tick * 0.05) * 80), 200);
            Particle.DustOptions dust = new Particle.DustOptions(c, 0.8f);
            world.spawnParticle(Particle.DUST, center.clone().add(x, 0.6, z), 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private void spawnGalaxySwirl(org.bukkit.World world, Location center, int tick) {
        for (int layer = 0; layer < 2; layer++) {
            double r = 0.5 + layer * 0.35;
            for (int i = 0; i < 6; i++) {
                double angle = Math.toRadians((tick * (8 + layer * 3) + i * 60) % 360);
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;
                double y = 0.4 + layer * 0.25 + Math.sin(tick * 0.08 + layer) * 0.1;
                world.spawnParticle(Particle.END_ROD, center.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void spawnAscendingSpiral(org.bukkit.World world, Location center, int tick) {
        double progress = (tick % 25) / 25.0;
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians((tick * 15 + i * 72) % 360);
            double x = Math.cos(angle) * 0.6;
            double z = Math.sin(angle) * 0.6;
            double y = progress * 1.4 + i * 0.08;
            world.spawnParticle(Particle.ENCHANTED_HIT, center.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void spawnRuneCircle(org.bukkit.World world, Location center, int tick) {
        double radius = 0.75;
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians((tick * 4 + i * 30) % 360);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            world.spawnParticle(Particle.ENCHANTED_HIT, center.clone().add(x, 0.5, z), 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.ENCHANT, center.clone().add(x * 0.6, 0.7, z * 0.6), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void spawnHeartRing(org.bukkit.World world, Location center, int tick) {
        double radius = 0.7;
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians((tick * 5 + i * 36) % 360);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            world.spawnParticle(Particle.HEART, center.clone().add(x, 0.55 + Math.sin(tick * 0.1) * 0.1, z), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void spawnStarBurst(org.bukkit.World world, Location center, int tick) {
        for (int arms = 0; arms < 5; arms++) {
            double baseAngle = Math.toRadians(arms * 72 + (tick * 3));
            for (int dist = 1; dist <= 3; dist++) {
                double x = Math.cos(baseAngle) * (0.3 * dist);
                double z = Math.sin(baseAngle) * (0.3 * dist);
                world.spawnParticle(Particle.FIREWORK, center.clone().add(x, 0.5, z), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Simple HSL to Color conversion for rainbow effects (no external deps) */
    private Color hsbToColor(float hue, float saturation, float brightness) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    // === MASSIVE EXPANSION HELPERS (rlTrails/PlayerParticles/SuperTrails inspired) ===
    private void spawnFootprintTrail(Player player, org.bukkit.World world, Location loc, ParticleTrail trail, int tick) {
        if (!player.isOnGround() || tick % 2 != 0) return;
        Location foot = player.getLocation().add(0, 0.1, 0);
        world.spawnParticle(Particle.DUST, foot, 3, 0.2, 0.05, 0.2, 0.0, new Particle.DustOptions(Color.fromRGB(100,100,100), 0.7f));
    }

    private void spawnRainbowFootprint(Player player, org.bukkit.World world, Location loc, int tick) {
        if (!player.isOnGround() || tick % 2 != 0) return;
        Location foot = player.getLocation().add(0, 0.1, 0);
        float hue = (tick % 360) / 360.0f;
        Color c = hsbToColor(hue, 0.8f, 1.0f);
        world.spawnParticle(Particle.DUST, foot, 4, 0.2, 0.05, 0.2, 0.0, new Particle.DustOptions(c, 0.8f));
    }

    private void spawnSphereAura(org.bukkit.World world, Location center, ParticleTrail trail, int tick) {
        double r = 1.0;
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians((tick * 3 + i * 30) % 360);
            double x = Math.cos(a) * r;
            double z = Math.sin(a) * r;
            double y = Math.sin(tick * 0.1 + i) * 0.3;
            Particle p = (trail == ParticleTrail.GUARDIAN_SPHERE) ? Particle.BUBBLE_POP : (trail == ParticleTrail.GALAXY_SPHERE) ? Particle.END_ROD : Particle.ENCHANT;
            world.spawnParticle(p, center.clone().add(x, 0.8 + y, z), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void spawnAdvancedWing(org.bukkit.World world, Location loc, float yaw, ParticleTrail trail, int tick) {
        double angle = Math.toRadians(yaw);
        for (int side = -1; side <= 1; side += 2) {
            double sx = Math.cos(angle + (side * Math.PI / 2)) * 0.7;
            double sz = Math.sin(angle + (side * Math.PI / 2)) * 0.7;
            for (int i = -2; i <= 2; i++) {
                double y = 0.3 + i * 0.12;
                Particle p = (trail == ParticleTrail.DRAGON_WINGS) ? Particle.DRAGON_BREATH : (trail == ParticleTrail.BAT_WINGS) ? Particle.ASH : Particle.HAPPY_VILLAGER;
                world.spawnParticle(p, loc.clone().add(sx * (1 + Math.abs(i)*0.1), y, sz * (1 + Math.abs(i)*0.1)), 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private void spawnRainbowWing(org.bukkit.World world, Location loc, float yaw, int tick) {
        double angle = Math.toRadians(yaw);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = -2; i <= 2; i++) {
                float hue = ((tick * 2 + i * 20 + side * 100) % 360) / 360.0f;
                Color c = hsbToColor(hue, 0.9f, 1.0f);
                double sx = Math.cos(angle + (side * Math.PI / 2)) * 0.7;
                double sz = Math.sin(angle + (side * Math.PI / 2)) * 0.7;
                double y = 0.3 + i * 0.12;
                world.spawnParticle(Particle.DUST, loc.clone().add(sx * (1+Math.abs(i)*0.1), y, sz*(1+Math.abs(i)*0.1)), 1, 0.02,0.02,0.02,0.0, new Particle.DustOptions(c, 0.9f));
            }
        }
    }

    private void spawnHaloEffect(org.bukkit.World world, Location loc, ParticleTrail trail, int tick) {
        double r = 0.4;
        Particle p = (trail == ParticleTrail.HALO_GOLD) ? Particle.END_ROD : (trail == ParticleTrail.HALO_ANGEL) ? Particle.FIREWORK : Particle.LAVA;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians((tick * 2 + i * 45) % 360);
            world.spawnParticle(p, loc.clone().add(Math.cos(a)*r, 1.6, Math.sin(a)*r), 1, 0.0,0.0,0.0,0.0);
        }
    }

    private void spawnVortexEffect(org.bukkit.World world, Location center, ParticleTrail trail, int tick) {
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians((tick * 8 + i * 60) % 360);
            double x = Math.cos(a) * 0.6;
            double z = Math.sin(a) * 0.6;
            double y = 1.2 - ((tick + i) % 20) / 20.0 * 1.5;
            Particle p = (trail == ParticleTrail.VOID_VORTEX) ? Particle.REVERSE_PORTAL : Particle.BUBBLE_POP;
            world.spawnParticle(p, center.clone().add(x, y, z), 1, 0.0,0.0,0.0,0.0);
        }
    }

    private void spawnWaveEffect(org.bukkit.World world, Location center, ParticleTrail trail, int tick) {
        double r = 0.3 + ((tick % 10) / 10.0) * 0.8;
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            Particle p = (trail == ParticleTrail.GROUND_WAVE) ? Particle.DUST : Particle.POOF;
            world.spawnParticle(p, center.clone().add(Math.cos(a)*r, 0.2, Math.sin(a)*r), 1, 0.0,0.0,0.0,0.0);
        }
    }

    private void spawnArchEffect(org.bukkit.World world, Location center, ParticleTrail trail, int tick) {
        for (int i = -4; i <= 4; i++) {
            double y = 0.5 + Math.abs(i) * 0.15;
            double x = i * 0.15;
            Particle p = (trail == ParticleTrail.CRYSTAL_ARCH) ? Particle.ENCHANT : Particle.WITCH;
            world.spawnParticle(p, center.clone().add(x, y, 0), 1, 0.0,0.0,0.0,0.0);
        }
    }

    private void spawnMagicCircle(org.bukkit.World world, Location center, int tick) {
        double r = 0.9;
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians((tick * 3 + i * 22.5) % 360);
            world.spawnParticle(Particle.ENCHANT, center.clone().add(Math.cos(a)*r, 0.3, Math.sin(a)*r), 1, 0.0,0.0,0.0,0.0);
            if (i % 4 == 0) world.spawnParticle(Particle.WITCH, center.clone().add(Math.cos(a)*r*0.5, 0.8, Math.sin(a)*r*0.5), 1, 0.0,0.0,0.0,0.0);
        }
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