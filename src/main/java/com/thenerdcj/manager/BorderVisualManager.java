package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import org.bukkit.*;
import org.bukkit.entity.Player;

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
     * Maps island borderColor string to a Particle.
     *
     * For true custom/"our own" border colors without ProtocolLib we primarily rely on
     * Particle.DUST + DustOptions (which accepts any Color / RGB).
     * The named colors below are mapped for backward compat with the current Settings GUI.
     *
     * If you ever want fully arbitrary colors (e.g. hex from a future GUI), just store
     * the color as a hex string in IslandSettings.borderColor and parse it here into
     * a Color, then always return Particle.DUST.
     */
    private Particle getBorderParticle(String color) {
        // We always return DUST for island borders.
        // This lets us use DustOptions with a parsed Color (supporting both named presets
        // and full hex #RRGGBB / #RGB from island_settings.borderColor).
        // No ProtocolLib required — pure particle visuals.
        return Particle.DUST;
    }

    /**
     * Parses border color supporting both named colors (BLUE, RED, etc.) and hex (#RRGGBB or #RGB).
     * Returns a Bukkit Color for use with DustOptions.
     */
    private Color parseBorderColor(String color) {
        if (color == null || color.isBlank()) {
            return Color.BLUE;
        }
        String c = color.trim();
        if (c.startsWith("#")) {
            String hex = c.substring(1);
            // Expand #RGB to #RRGGBB
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                    + hex.charAt(1) + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2);
            }
            if (hex.length() == 6) {
                try {
                    int rgb = Integer.parseInt(hex, 16);
                    return Color.fromRGB(rgb);
                } catch (NumberFormatException ignored) {}
            }
            // invalid hex, fall through to named
        }
        // Named colors (case insensitive)
        return switch (c.toUpperCase()) {
            case "RED", "DARK_RED" -> Color.RED;
            case "GREEN", "DARK_GREEN" -> Color.GREEN;
            case "BLUE", "DARK_BLUE" -> Color.BLUE;
            case "YELLOW", "GOLD" -> Color.YELLOW;
            case "PURPLE", "DARK_PURPLE" -> Color.PURPLE;
            case "PINK", "LIGHT_PURPLE" -> Color.fromRGB(255, 105, 180);
            case "WHITE", "GRAY" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            default -> Color.BLUE;
        };
    }

    private Color getParticleColor(String color) {
        return parseBorderColor(color);
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

        // Get island settings for color (supports hex now)
        String borderColor = "BLUE";
        try {
            var island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island != null) {
                // Folia-safe: cached only in hot particle task (non-blocking)
                borderColor = plugin.getIslandSettingsManager()
                    .getCachedSettings(island.getGridPosition())
                    .getBorderColor();
            }
        } catch (Exception ignored) {}

        Particle particle = getBorderParticle(borderColor);
        Color tint = parseBorderColor(borderColor);

        // Dynamic density for performance on large islands
        int dynamicDensity = Math.max(2, (int) (particleDensity * (256.0 / Math.max(256, radius))));

        // Time-based pulse for animation (independent of server tick, smooth)
        double time = System.currentTimeMillis() / 280.0;
        double pulse = 1.0 + Math.sin(time) * 0.25;           // gentle size pulse
        double heightWave = Math.sin(time * 0.7) * 0.6;       // slow vertical wave

        // Draw thicker border by rendering 3 parallel lines per edge with small offsets
        for (int thick = -1; thick <= 1; thick++) {
            double thickOffset = thick * 0.45; // small perpendicular thickness

            // Four sides of the square border
            for (int i = -radius; i <= radius; i += dynamicDensity) {
                double progress = (i + radius) / (double) (radius * 2); // 0..1 along edge for wave
                double edgeWave = Math.sin(progress * Math.PI * 2 + time * 1.3) * 0.35;

                // North edge (Z-)
                spawnColoredParticleLine(player, world,
                    center.getX() + i + thickOffset, center.getZ() - radius,
                    center.getY(), particle, tint, pulse, heightWave + edgeWave);

                // South edge (Z+)
                spawnColoredParticleLine(player, world,
                    center.getX() + i + thickOffset, center.getZ() + radius,
                    center.getY(), particle, tint, pulse, heightWave - edgeWave);

                // West edge (X-)
                spawnColoredParticleLine(player, world,
                    center.getX() - radius, center.getZ() + i + thickOffset,
                    center.getY(), particle, tint, pulse, heightWave + edgeWave);

                // East edge (X+)
                spawnColoredParticleLine(player, world,
                    center.getX() + radius, center.getZ() + i + thickOffset,
                    center.getY(), particle, tint, pulse, heightWave - edgeWave);
            }
        }

        // Corner "posts" for extra visual weight (animated upward)
        double cornerYBase = center.getY() + 1.2;
        for (int cx = -1; cx <= 1; cx += 2) {
            for (int cz = -1; cz <= 1; cz += 2) {
                double cxPos = center.getX() + cx * radius;
                double czPos = center.getZ() + cz * radius;
                for (int h = 0; h < 5; h++) {
                    double y = cornerYBase + h * 0.7 + Math.sin(time * 2 + h) * 0.2;
                    Location pLoc = new Location(world, cxPos, y, czPos);
                    if (pLoc.distance(player.getLocation()) > 48) continue;
                    if (plugin.isFolia()) {
                        final double fy = y;
                        player.getScheduler().run(plugin, t -> {
                            player.spawnParticle(Particle.DUST, pLoc.getX(), fy, pLoc.getZ(), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(tint, 1.0f + (float)(pulse * 0.3)));
                        }, null);
                    } else {
                        player.spawnParticle(Particle.DUST, pLoc.getX(), y, pLoc.getZ(), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(tint, 1.0f + (float)(pulse * 0.3)));
                    }
                }
            }
        }
    }

    private void spawnColoredParticleLine(Player player, World world, double x, double z, double baseY,
                                          Particle particle, Color tint, double pulse, double waveOffset) {
        Location particleLoc = new Location(world, x, baseY + 1.5, z);
        if (particleLoc.distance(player.getLocation()) > 48) return;

        // Height with wave + variation + pulse influence
        double y = baseY + 1.0 + waveOffset + ThreadLocalRandom.current().nextDouble(-0.4, 0.6);
        particleLoc.setY(y);

        float size = (float) (1.15 + pulse * 0.25);

        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, t -> {
                player.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(tint, size));
            }, null);
        } else {
            player.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0,
                new Particle.DustOptions(tint, size));
        }
    }

    /**
     * Very large WorldBorder size used to suppress the vanilla red "outside border" hue.
     *
     * When a player is on/near an island we apply a *huge* per-player WorldBorder.
     * This keeps the player "inside" the border from the client's perspective, completely
     * eliminating the red tint that normally appears when you walk off your island or
     * use /spawn while an island-sized border is still applied.
     *
     * The actual visible **colored border** at the real (small) island radius is provided
     * entirely by the particle system (see showBorderParticles + getBorderParticle /
     * getParticleColor). This is how you get custom border colors (BLUE, RED, GREEN, etc.
     * from the Island Settings GUI) **without ProtocolLib or any packets**.
     */
    private static final double LARGE_WORLD_BORDER_SIZE = 100000.0;

    /**
     * Shows a nice WorldBorder to the player for their island (if enabled).
     * This is one of the best "visual expansion" features in modern Skyblock.
     */
    public void updatePlayerWorldBorder(Player player, Island island) {
        if (!worldBorderEnabled || player == null) return;

        if (island == null) {
            // Global spawn or non-island location: clear any lingering island border
            clearPlayerWorldBorder(player);
            return;
        }

        Location center = island.getCenter(player.getWorld());
        if (center == null) return;

        // Get or create a WorldBorder for this island.
        // We use a VERY LARGE size (see constant above) instead of the actual island radius.
        // This is the key trick for "own border color without ProtocolLib":
        // - The vanilla WorldBorder line will be extremely far away (practically invisible).
        // - No red hue when the player steps slightly outside the claim or teleports to /spawn.
        // - The pretty colored border at the correct radius comes from particles (which read
        //   the live island_settings.border_color and support custom tints via DustOptions).
        GridPosition key = island.getGridPosition();
        WorldBorder border = activeWorldBorders.computeIfAbsent(key, k -> {
            WorldBorder newBorder = Bukkit.createWorldBorder();
            newBorder.setCenter(center);
            newBorder.setSize(LARGE_WORLD_BORDER_SIZE);
            newBorder.setWarningDistance(0);
            newBorder.setWarningTime(0);
            return newBorder;
        });

        // Keep center and huge size fresh (no need to transition the giant size).
        border.setCenter(center);
        border.setSize(LARGE_WORLD_BORDER_SIZE);

        // Apply the (now color-neutral) WorldBorder to this player only.
        player.setWorldBorder(border);
    }

    /**
     * Called when a player leaves an island area or logs off.
     */
    public void clearPlayerWorldBorder(Player player) {
        if (player != null && player.isOnline()) {
            if (plugin.isFolia()) {
                player.getScheduler().run(plugin, t -> player.setWorldBorder(null), null);
            } else {
                player.setWorldBorder(null);
            }
        }
    }

    /**
     * Immediately refreshes both the WorldBorder and particle border for a player on their island.
     * Called after settings changes like border color to give instant feedback.
     */
    public void forceBorderRefresh(Player player, Island island) {
        if (player == null || island == null || !player.isOnline()) return;

        if (worldBorderEnabled) {
            updatePlayerWorldBorder(player, island);
        }

        if (particlesEnabled) {
            int radius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
            Location center = island.getCenter(player.getWorld());
            if (center != null) {
                // One-shot particle update with latest color from cache
                showBorderParticles(player, center, radius);
            }
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
     * Full persistent hologram border markers.
     * Spawns (or re-spawns) TextDisplay holograms at the 4 corners of the island border.
     * These are saved via HologramManager so they persist across restarts.
     */
    public void spawnBorderHologramMarkers(Island island, Player requester) {
        if (island == null) return;

        int radius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
        Location center = island.getCenter(requester != null ? requester.getWorld() : Bukkit.getWorlds().get(0));
        if (center == null) return;

        // Folia-safe: cached only (hologram spawn path)
        var settings = plugin.getIslandSettingsManager().getCachedSettings(island.getGridPosition());
        if (!settings.isBorderMarkersEnabled()) return;
        String colorCode = getColorCode(settings.getBorderColor());

        String gridKey = island.getGridPosition().x() + "_" + island.getGridPosition().z() + "_" + island.getDimension().name();
        String[] corners = {"NW", "NE", "SW", "SE"};
        double[][] offsets = {{-radius, -radius}, {radius, -radius}, {-radius, radius}, {radius, radius}};

        for (int i = 0; i < 4; i++) {
            Location loc = center.clone().add(offsets[i][0], 3.2, offsets[i][1]);
            String name = "border_" + gridKey + "_" + corners[i];

            // Remove old one if exists (best effort)
            // For simplicity in this pass we rely on unique names and let admin clean if needed

            com.thenerdcj.hologram.HologramData data = new com.thenerdcj.hologram.HologramData(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
            data.setScale(0.7);
            data.setBillboard("CENTER");
            data.addLine("§" + colorCode + "◆ Border Marker ◆");
            data.addLine("§7" + corners[i]);
            data.addLine("§fRadius: §b" + radius);

            plugin.getHologramManager().spawnHologram(data);
        }

        if (requester != null) {
            requester.sendMessage("§aSpawned persistent border hologram markers at corners.");
        }
    }

    public void removeBorderHologramMarkers(Island island) {
        // In a production version we would query and delete by name prefix.
        // For this implementation, we document that admins can use /hologram delete for "border_*" names.
    }

    private String getColorCode(String color) {
        if (color == null) return "b";
        String c = color.toUpperCase();
        if (c.startsWith("#")) {
            return "b"; // hex not directly mappable to chat color codes, fallback
        }
        return switch (c) {
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