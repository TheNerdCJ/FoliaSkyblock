package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.hologram.Hologram;
import com.thenerdcj.hologram.HologramData;
import com.thenerdcj.island.Island;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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
        startParticleTask();
        startWorldBorderTask();
    }

    private void startWorldBorderTask() {
        plugin.getThreadSafety().runRepeatingOnMainThread(() -> {
            if (!worldBorderEnabled) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                if (island != null) {
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
        }, 40L, 40L);
    }

    public void loadConfig() {
        var section = plugin.getConfig().getConfigurationSection("upgrades.island-size.visuals");
        if (section == null) {
            worldBorderEnabled = false; particlesEnabled = false; particleDensity = 4; expansionEffectEnabled = false;
            return;
        }

        worldBorderEnabled = section.getBoolean("worldborder", false);
        particlesEnabled = section.getBoolean("particles", false);
        particleDensity = section.getInt("particle-density", 4);
        expansionEffectEnabled = section.getBoolean("expansion-effect", false);
    }

    private Particle getBorderParticle(String color) { return Particle.DUST; }
    private Color parseBorderColor(String color) {
        if (color == null || color.isBlank()) {
            return Color.BLUE;
        }
        String c = color.trim();
        if (c.startsWith("#")) {
            String hex = c.substring(1);
            if (hex.length() == 3) { hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2); }
            if (hex.length() == 6) { try { int rgb = Integer.parseInt(hex, 16); return Color.fromRGB(rgb); } catch (NumberFormatException ignored) {} }
        }
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

        String borderColor = "BLUE";
        try {
            var island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island != null) { borderColor = plugin.getIslandSettingsManager().getCachedSettings(island.getGridPosition()).getBorderColor(); }
        } catch (Exception ignored) {}

        Particle particle = getBorderParticle(borderColor);
        Color tint = parseBorderColor(borderColor);

        int dynamicDensity = Math.max(2, (int) (particleDensity * (256.0 / Math.max(256, radius))));
        double time = System.currentTimeMillis() / 280.0;
        double pulse = 1.0 + Math.sin(time) * 0.25;
        double heightWave = Math.sin(time * 0.7) * 0.6;
        for (int thick = -1; thick <= 1; thick++) {
            double thickOffset = thick * 0.45;
            for (int i = -radius; i <= radius; i += dynamicDensity) {
                double progress = (i + radius) / (double) (radius * 2);
                double edgeWave = Math.sin(progress * Math.PI * 2 + time * 1.3) * 0.35;

                spawnColoredParticleLine(player, world, center.getX() + i + thickOffset, center.getZ() - radius, center.getY(), particle, tint, pulse, heightWave + edgeWave);
                spawnColoredParticleLine(player, world, center.getX() + i + thickOffset, center.getZ() + radius, center.getY(), particle, tint, pulse, heightWave - edgeWave);
                spawnColoredParticleLine(player, world, center.getX() - radius, center.getZ() + i + thickOffset, center.getY(), particle, tint, pulse, heightWave + edgeWave);
                spawnColoredParticleLine(player, world, center.getX() + radius, center.getZ() + i + thickOffset, center.getY(), particle, tint, pulse, heightWave - edgeWave);
            }
        }

        double cornerYBase = center.getY() + 1.2;
        for (int cx = -1; cx <= 1; cx += 2) {
            for (int cz = -1; cz <= 1; cz += 2) {
                double cxPos = center.getX() + cx * radius;
                double czPos = center.getZ() + cz * radius;
                for (int h = 0; h < 5; h++) {
                    double y = cornerYBase + h * 0.7 + Math.sin(time * 2 + h) * 0.2;
                    Location pLoc = new Location(world, cxPos, y, czPos);
                    if (pLoc.distance(player.getLocation()) > 48) continue;
                    final double fy = y; player.getScheduler().run(plugin, t -> { player.spawnParticle(Particle.DUST, pLoc.getX(), fy, pLoc.getZ(), 1, 0, 0, 0, 0, new Particle.DustOptions(tint, 1.0f + (float)(pulse * 0.3))); }, null);
                }
            }
        }
    }

    private void spawnColoredParticleLine(Player player, World world, double x, double z, double baseY,
                                          Particle particle, Color tint, double pulse, double waveOffset) {
        Location particleLoc = new Location(world, x, baseY + 1.5, z);
        if (particleLoc.distance(player.getLocation()) > 48) return;

        double y = baseY + 1.0 + waveOffset + ThreadLocalRandom.current().nextDouble(-0.4, 0.6);
        particleLoc.setY(y);

        float size = (float) (1.15 + pulse * 0.25);

        player.getScheduler().run(plugin, t -> { player.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0, new Particle.DustOptions(tint, size)); }, null);
    }

    private static final double LARGE_WORLD_BORDER_SIZE = 100000.0;

    public void updatePlayerWorldBorder(Player player, Island island) {
        if (!worldBorderEnabled || player == null) return;

        if (island == null) {
            clearPlayerWorldBorder(player);
            return;
        }

        Location center = island.getCenter(player.getWorld());
        if (center == null) return;

        GridPosition key = island.getGridPosition();
        WorldBorder border = activeWorldBorders.computeIfAbsent(key, k -> {
            WorldBorder newBorder = Bukkit.createWorldBorder();
            newBorder.setCenter(center);
            newBorder.setSize(LARGE_WORLD_BORDER_SIZE);
            newBorder.setWarningDistance(0);
            newBorder.setWarningTime(0);
            return newBorder;
        });

        border.setCenter(center);
        border.setSize(LARGE_WORLD_BORDER_SIZE);
        player.setWorldBorder(border);
    }

    public void clearPlayerWorldBorder(Player player) {
        if (player != null && player.isOnline()) {
            player.getScheduler().run(plugin, t -> player.setWorldBorder(null), null);
        }
    }

    public void forceBorderRefresh(Player player, Island island) {
        if (player == null || island == null || !player.isOnline()) return;

        if (worldBorderEnabled) {
            updatePlayerWorldBorder(player, island);
        }

        if (particlesEnabled) {
            int radius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
            Location center = island.getCenter(player.getWorld());
            if (center != null) { showBorderParticles(player, center, radius); }
        }
    }

    public void playExpansionEffect(Player player, Island island, int newLevel) {
        if (!expansionEffectEnabled || player == null || island == null) return;

        Location center = island.getCenter(player.getWorld());
        if (center == null) return;

        int newRadius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
        int oldRadius = Math.max(50, newRadius - 8); // approximate previous

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.7f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);

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

    public void spawnBorderHologramMarkers(Island island, Player requester) {
        if (island == null || plugin.getHologramManager() == null) return;

        int radius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
        Location center = island.getCenter(requester != null ? requester.getWorld() : Bukkit.getWorlds().get(0));
        if (center == null) return;

        var settings = plugin.getIslandSettingsManager().getCachedSettings(island.getGridPosition());
        if (!settings.isBorderMarkersEnabled()) return;

        String colorCode = getColorCode(settings.getBorderColor());

        String gridKey = island.getGridPosition().x() + "_" + island.getGridPosition().z() + "_" + island.getDimension().name();
        String[] corners = {"NW", "NE", "SW", "SE"};
        double[][] offsets = {{-radius, -radius}, {radius, -radius}, {-radius, radius}, {radius, radius}};

        for (int i = 0; i < 4; i++) {
            Location loc = center.clone().add(offsets[i][0], 3.2, offsets[i][1]);
            String name = "border_" + gridKey + "_" + corners[i];

            HologramData data = new HologramData(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
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
        if (island == null || plugin.getHologramManager() == null) return;

        String gridKey = island.getGridPosition().x() + "_" + island.getGridPosition().z() + "_" + island.getDimension().name();
        for (String c : new String[]{"NW", "NE", "SW", "SE"}) {
            String name = "border_" + gridKey + "_" + c;
            Hologram h = plugin.getHologramManager().getHologramByName(name);
            if (h != null) {
                plugin.getHologramManager().deleteHologram(h.getData().getId());
            }
        }
    }

    private String getColorCode(String color) {
        if (color == null) return "b";
        String c = color.toUpperCase();
        if (c.startsWith("#")) {
            return "b"; // hex fallback
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