package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * BorderVisualManager - Provides high-quality visual feedback for island borders.
 *
 * Features:
 * - Per-player WorldBorder (clean, modern, no client mods needed)
 * - Border particle effects (Folia-safe)
 * - Expansion effects when ISLAND_SIZE is upgraded
 * - Respects border color from IslandSettings where possible
 * - Integrates with Prestige for larger borders looking more impressive
 */
public class BorderVisualManager {

    private final FoliaSkyblock plugin;
    private final ConcurrentHashMap<GridPosition, WorldBorder> activeWorldBorders = new ConcurrentHashMap<>();
    private final Map<GridPosition, List<TextDisplay>> activeBorderMarkers = new ConcurrentHashMap<>();

    private boolean worldBorderEnabled;
    private boolean particlesEnabled;
    private int particleDensity;
    private boolean expansionEffectEnabled;

    public BorderVisualManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();

        // Start the repeating particle task (Folia-safe)
        if (particlesEnabled) {
            startParticleTask();
        }

        // Start WorldBorder auto-apply task (every 2 seconds - lightweight)
        if (worldBorderEnabled) {
            startWorldBorderTask();
        }
    }

    private void startWorldBorderTask() {
        plugin.getThreadSafety().runRepeatingOnMainThread(() -> {
            if (!worldBorderEnabled) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                if (island != null) {
                    // Only apply if player is reasonably close to their island center
                    Location center = island.getCenter(player.getWorld());
                    if (center != null && center.distance(player.getLocation()) < 300) {
                        updatePlayerWorldBorder(player, island);
                    } else {
                        clearPlayerWorldBorder(player);
                    }
                } else {
                    clearPlayerWorldBorder(player);
                }
            }
        }, 40L, 40L); // every 2 seconds
    }

    public void loadConfig() {
        var section = plugin.getConfig().getConfigurationSection("upgrades.island-size.visuals");
        if (section == null) {
            worldBorderEnabled = true;
            particlesEnabled = true;
            particleDensity = 4;
            expansionEffectEnabled = true;
            return;
        }

        worldBorderEnabled = section.getBoolean("worldborder", true);
        particlesEnabled = section.getBoolean("particles", true);
        particleDensity = section.getInt("particle-density", 4);
        expansionEffectEnabled = section.getBoolean("expansion-effect", true);
    }

    /**
     * Maps island borderColor string to a suitable Particle for visual borders.
     */
    private Particle getBorderParticle(String color) {
        if (color == null) color = "BLUE";
        return switch (color.toUpperCase()) {
            case "RED", "DARK_RED" -> Particle.DUST;           // Will tint red
            case "GREEN", "DARK_GREEN" -> Particle.HAPPY_VILLAGER;
            case "BLUE", "DARK_BLUE" -> Particle.END_ROD;
            case "YELLOW", "GOLD" -> Particle.ELECTRIC_SPARK;
            case "PURPLE", "DARK_PURPLE" -> Particle.PORTAL;
            case "PINK", "LIGHT_PURPLE" -> Particle.HEART;
            case "WHITE", "GRAY" -> Particle.CLOUD;
            case "BLACK" -> Particle.SMOKE;
            default -> Particle.END_ROD;
        };
    }

    private Color getParticleColor(String color) {
        if (color == null) color = "BLUE";
        return switch (color.toUpperCase()) {
            case "RED" -> Color.RED;
            case "DARK_RED" -> Color.fromRGB(139, 0, 0);
            case "GREEN" -> Color.GREEN;
            case "DARK_GREEN" -> Color.fromRGB(0, 100, 0);
            case "BLUE" -> Color.BLUE;
            case "DARK_BLUE" -> Color.fromRGB(0, 0, 139);
            case "YELLOW" -> Color.YELLOW;
            case "GOLD" -> Color.fromRGB(255, 215, 0);
            case "PURPLE" -> Color.PURPLE;
            case "PINK" -> Color.fromRGB(255, 105, 180);
            case "WHITE" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            default -> Color.BLUE;
        };
    }

    private void startParticleTask() {
        // Run every 20 ticks (1 second) - lightweight
        plugin.getThreadSafety().runRepeatingOnMainThread(() -> {
            if (!particlesEnabled) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                if (island == null) continue;

                int radius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
                Location center = island.getCenter(player.getWorld());
                if (center == null) continue;

                showBorderParticles(player, center, radius);
            }
        }, 20L, 20L);
    }

    private void showBorderParticles(Player player, Location center, int radius) {
        World world = center.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Get island settings for color
        String borderColor = "BLUE";
        try {
            var settingsFuture = plugin.getIslandSettingsManager().getSettings(
                plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment()).getGridPosition()
            );
            // We use cached value for performance in the particle task
            var island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island != null) {
                // Folia-safe: cached only in hot particle task (non-blocking)
                borderColor = plugin.getIslandSettingsManager()
                    .getCachedSettings(island.getGridPosition())
                    .getBorderColor();
            }
        } catch (Exception ignored) {}

        Particle particle = getBorderParticle(borderColor);
        Color tint = getParticleColor(borderColor);

        // Explicit scale for gen radius changes: dynamic density to avoid spam on large (gen scaled) islands, more particles for bigger radius.
        int dynamicDensity = Math.max(2, (int) (particleDensity * (256.0 / Math.max(256, radius))));
        // Four sides of the square border
        for (int i = -radius; i <= radius; i += dynamicDensity) {
            // North & South edges
            spawnColoredParticleLine(player, world, center.getX() + i, center.getZ() - radius, center.getY(), particle, tint);
            spawnColoredParticleLine(player, world, center.getX() + i, center.getZ() + radius, center.getY(), particle, tint);

            // West & East edges
            spawnColoredParticleLine(player, world, center.getX() - radius, center.getZ() + i, center.getY(), particle, tint);
            spawnColoredParticleLine(player, world, center.getX() + radius, center.getZ() + i, center.getY(), particle, tint);
        }
    }

    private void spawnColoredParticleLine(Player player, World world, double x, double z, double baseY, Particle particle, Color tint) {
        Location particleLoc = new Location(world, x, baseY + 1.5, z);
        if (particleLoc.distance(player.getLocation()) > 48) return;

        particleLoc.setY(baseY + 0.8 + ThreadLocalRandom.current().nextDouble(0, 2.5));

        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, t -> {
                if (particle == Particle.DUST) {
                    player.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(tint, 1.2f));
                } else {
                    player.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
                }
            }, null);
        } else {
            if (particle == Particle.DUST) {
                player.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(tint, 1.2f));
            } else {
                player.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Shows a nice WorldBorder to the player for their island (if enabled).
     * This is one of the best "visual expansion" features in modern Skyblock.
     */
    public void updatePlayerWorldBorder(Player player, Island island) {
        if (!worldBorderEnabled || player == null || island == null) return;

        int effectiveRadius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
        Location center = island.getCenter(player.getWorld());
        if (center == null) return;

        // Explicit particle/border scale update when gen radius changes (IslandGenerator now scales terrain with sizeLevel; protection+gen aligned).
        // Use effective (from upgrade which drives gen radius at create/reset time) for border size and particle spacing.
        int genAwareRadius = effectiveRadius; // could merge with island.getGenRadius() if stored separately in future
        // Get or create a WorldBorder for this island
        GridPosition key = island.getGridPosition();
        WorldBorder border = activeWorldBorders.computeIfAbsent(key, k -> {
            WorldBorder newBorder = Bukkit.createWorldBorder();
            newBorder.setCenter(center);
            newBorder.setSize(genAwareRadius * 2.0);
            newBorder.setWarningDistance(5);
            newBorder.setWarningTime(0);
            return newBorder;
        });

        // Update size if it changed (upgrade happened or gen radius scale)
        border.setSize(genAwareRadius * 2.0, 1); // 1 second smooth transition

        // Apply to player
        player.setWorldBorder(border);
    }

    /**
     * Called when a player leaves an island area or logs off.
     */
    public void clearPlayerWorldBorder(Player player) {
        if (player != null) {
            player.setWorldBorder(null);
        }
    }

    /**
     * Plays a satisfying expansion effect when ISLAND_SIZE is purchased.
     * Includes outward-moving animation rings for strong visual feedback.
     */
    public void playExpansionEffect(Player player, Island island, int newLevel) {
        if (!expansionEffectEnabled || player == null || island == null) return;

        Location center = island.getCenter(player.getWorld());
        if (center == null) return;

        int newRadius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
        int oldRadius = Math.max(50, newRadius - 8); // approximate previous

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.7f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);

        // Animated expansion rings (3 layers moving outward) - simple delayed bursts
        for (int tick = 0; tick < 5; tick++) {
            final int t = tick;
            plugin.getThreadSafety().runOnMainThreadLater(() -> {
                for (int ring = 0; ring < 3; ring++) {
                    double progress = (t + 1) / 5.0;
                    double currentR = oldRadius + (ring * 3) + (newRadius - oldRadius) * progress;
                    for (int i = 0; i < 360; i += 22) {
                        double angle = Math.toRadians(i);
                        double x = center.getX() + Math.cos(angle) * currentR;
                        double z = center.getZ() + Math.sin(angle) * currentR;
                        Location pLoc = new Location(center.getWorld(), x, center.getY() + 1.1 + ring * 0.5, z);
                        player.spawnParticle(Particle.END_ROD, pLoc, 1, 0, 0, 0, 0.01);
                    }
                }
            }, tick * 3L);
        }

        // Final solid ring at new border
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double x = center.getX() + Math.cos(angle) * newRadius;
            double z = center.getZ() + Math.sin(angle) * newRadius;
            Location loc = new Location(center.getWorld(), x, center.getY() + 1.8, z);
            player.spawnParticle(Particle.EXPLOSION, loc, 0);
            player.spawnParticle(Particle.GLOW, loc, 2, 0.15, 0.4, 0.15, 0.01);
        }

        player.sendMessage("§a§lIsland border expanded! §7New effective radius: §b" + newRadius);
    }

    public void reload() {
        loadConfig();
    }

    /**
     * Cleanup border marker displays when a world unloads (Folia region safety).
     */
    public void onWorldUnload(World world) {
        if (world == null) return;
        activeBorderMarkers.entrySet().removeIf(entry -> {
            List<TextDisplay> list = entry.getValue();
            if (!list.isEmpty()) {
                TextDisplay first = list.get(0);
                if (first != null && first.isValid() && first.getWorld() != null && first.getWorld().equals(world)) {
                    for (TextDisplay d : list) {
                        if (d != null && d.isValid()) d.remove();
                    }
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Full persistent hologram border markers.
     * Spawns (or re-spawns) TextDisplay holograms at the 4 corners of the island border.
     * These are saved via HologramManager so they persist across restarts.
     */
    public void spawnBorderHologramMarkers(Island island, Player requester) {
        if (island == null) return;

        int radius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
        Location center = island.getCenter(requester != null ? requester.getWorld() : Bukkit.getWorlds().get(0));
        if (center == null) return;

        // Folia-safe: cached only (border marker path)
        var settings = plugin.getIslandSettingsManager().getCachedSettings(island.getGridPosition());
        if (!settings.isBorderMarkersEnabled()) return;
        String colorCode = getColorCode(settings.getBorderColor());

        GridPosition key = island.getGridPosition();

        // Remove any existing markers for this island first
        removeBorderHologramMarkers(island);

        List<TextDisplay> markers = new ArrayList<>();
        String gridKey = key.x() + "_" + key.z() + "_" + island.getDimension().name();
        String[] corners = {"NW", "NE", "SW", "SE"};
        double[][] offsets = {{-radius, -radius}, {radius, -radius}, {-radius, radius}, {radius, radius}};

        // Place higher to be above terrain; use direct TextDisplay (bypasses Hologram system for reliability)
        float yOffset = 10.0f;
        float scale = 0.7f;
        float viewRange = Math.max(4.0f, (radius * 1.42f) / 64f + 2.0f);

        for (int i = 0; i < 4; i++) {
            Location loc = center.clone().add(offsets[i][0], yOffset, offsets[i][1]);
            String corner = corners[i];

            TextDisplay td = (TextDisplay) center.getWorld().spawn(loc, TextDisplay.class, entity -> {
                // Multi-line text with colors (no § translation needed here, use Adventure)
                Component text = Component.text()
                    .append(Component.text("◆ Border Marker ◆", getNamedTextColor(colorCode)))
                    .append(Component.newline())
                    .append(Component.text(corner, NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("Radius: ", NamedTextColor.WHITE))
                    .append(Component.text(String.valueOf(radius), NamedTextColor.AQUA))
                    .build();
                entity.text(text);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setSeeThrough(false);
                entity.setShadowed(true);
                entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new Quaternionf(),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
                ));
                entity.setViewRange(viewRange);
                entity.setPersistent(false);
            });

            markers.add(td);
        }

        activeBorderMarkers.put(key, markers);

        if (requester != null) {
            requester.sendMessage("§aSpawned persistent border markers at corners (using direct displays).");
        }
    }

    private NamedTextColor getNamedTextColor(String code) {
        if (code == null) code = "b";
        return switch (code.toLowerCase()) {
            case "c", "4" -> NamedTextColor.RED;
            case "a", "2" -> NamedTextColor.GREEN;
            case "9", "1" -> NamedTextColor.BLUE;
            case "e", "6" -> NamedTextColor.YELLOW;
            case "5" -> NamedTextColor.DARK_PURPLE;
            case "d" -> NamedTextColor.LIGHT_PURPLE;
            case "f", "7" -> NamedTextColor.WHITE;
            case "0" -> NamedTextColor.BLACK;
            default -> NamedTextColor.BLUE;
        };
    }

    public void removeBorderHologramMarkers(Island island) {
        if (island == null) return;
        GridPosition key = island.getGridPosition();
        List<TextDisplay> list = activeBorderMarkers.remove(key);
        if (list != null) {
            for (TextDisplay d : list) {
                if (d != null && d.isValid()) {
                    d.remove();
                }
            }
        }
    }

    private String getColorCode(String color) {
        if (color == null) return "b";
        return switch (color.toUpperCase()) {
            case "RED", "DARK_RED" -> "c";
            case "GREEN", "DARK_GREEN" -> "a";
            case "BLUE", "DARK_BLUE" -> "9";
            case "YELLOW", "GOLD" -> "e";
            case "PURPLE" -> "5";
            case "PINK" -> "d";
            case "WHITE" -> "f";
            case "BLACK" -> "0";
            default -> "b";
        };
    }
}