package com.thenerdcj.hologram;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.TopIslandEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class HologramManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final Map<Integer, Hologram> activeHolograms = new HashMap<>();
    private final Map<Integer, ScheduledTask> dynamicTasks = new ConcurrentHashMap<>();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public HologramManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void loadAndSpawnAll() {
        databaseManager.loadAllHolograms().thenAccept(holoList -> {
            for (HologramData data : holoList) {
                spawnHologram(data);
            }
            plugin.getLogger().info("§a[HologramManager] Loaded and spawned " + holoList.size() + " persistent holograms.");
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to load holograms", ex);
            return null;
        });
    }

    public void spawnHologram(HologramData data) {
        if (data == null || data.getLines().isEmpty()) return;

        World world = Bukkit.getWorld(data.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Hologram world not found: " + data.getWorldName());
            return;
        }

        Location baseLoc = new Location(world, data.getX(), data.getY(), data.getZ());

        plugin.getServer().getRegionScheduler().execute(plugin, baseLoc, () -> {
            List<TextDisplay> displays = new ArrayList<>();
            double yOffset = 0.0;
            double scale = data.getScale();   // Local scale variable for consistent use in spacing and entity scaling

            for (String rawLine : data.getLines()) {
                Location lineLoc = baseLoc.clone().add(0, yOffset, 0);

                TextDisplay td = world.spawn(lineLoc, TextDisplay.class, entity -> {
                    entity.text(legacySerializer.deserialize(rawLine));
                    entity.setBillboard(org.bukkit.entity.Display.Billboard.valueOf(data.getBillboard().toUpperCase()));
                    entity.setSeeThrough(data.isSeeThrough());
                    entity.setShadowed(data.isShadow());
                    entity.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),                    // translation
                            new Quaternionf(),                        // left rotation
                            new Vector3f((float) scale, (float) scale, (float) scale), // scale
                            new Quaternionf()                         // right rotation
                    ));

                    if (data.getBackgroundColor() != null && !data.getBackgroundColor().isEmpty()) {
                        try {
                            int argb = Integer.parseUnsignedInt(data.getBackgroundColor().replace("#", ""), 16);
                            entity.setBackgroundColor(org.bukkit.Color.fromARGB(argb));
                        } catch (Exception ignored) {}
                    }
                    entity.setPersistent(false);
                });

                displays.add(td);
                yOffset -= 0.28 * scale;   // Spacing now correctly scales with the hologram size
            }

            Hologram holo = new Hologram(data, displays);
            activeHolograms.put(data.getId(), holo);

            if (data.isDynamic()) {
                startDynamicRefresh(holo);
            }
        });
    }

    public CompletableFuture<Boolean> deleteHologram(int id) {
        Hologram holo = activeHolograms.remove(id);
        if (holo != null && !holo.getDisplays().isEmpty()) {
            Location loc = holo.getDisplays().get(0).getLocation();
            plugin.getServer().getRegionScheduler().execute(plugin, loc, holo::removeAll);
        }
        return databaseManager.deleteHologram(id);
    }

    public CompletableFuture<Boolean> updateLines(int id, List<String> newLines) {
        Hologram holo = activeHolograms.get(id);
        if (holo != null) {
            holo.getData().setLines(newLines);
            if (!holo.getDisplays().isEmpty()) {
                Location loc = holo.getDisplays().get(0).getLocation();
                plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                    holo.removeAll();
                    spawnHologram(holo.getData());
                });
            }
        }
        return databaseManager.updateHologramLines(id, newLines);
    }

    public Map<Integer, Hologram> getActiveHolograms() {
        return Collections.unmodifiableMap(activeHolograms);
    }

    public Hologram getHologramByName(String name) {
        return activeHolograms.values().stream()
                .filter(h -> h.getData().getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public CompletableFuture<Boolean> setUpdateInterval(int id, int seconds) {
        return databaseManager.updateHologramInterval(id, seconds).thenApply(success -> {
            if (success) {
                Hologram holo = activeHolograms.get(id);
                if (holo != null && holo.getData().isDynamic()) {
                    cancelDynamicTask(id);
                    startDynamicRefresh(holo);
                }
            }
            return success;
        });
    }

    private void startDynamicRefresh(Hologram holo) {
        HologramData data = holo.getData();
        if (!data.isDynamic()) return;

        cancelDynamicTask(data.getId());
        int intervalTicks = Math.max(600, data.getUpdateInterval() * 20);

        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            refreshDynamicContent(holo);
        }, 20L, intervalTicks);

        dynamicTasks.put(data.getId(), task);
    }

    private void cancelDynamicTask(int id) {
        ScheduledTask task = dynamicTasks.remove(id);
        if (task != null) task.cancel();
    }

    public void refreshDynamicContent(Hologram holo) {
        HologramData data = holo.getData();
        if (!data.isDynamic() || holo.getDisplays().isEmpty()) return;

        Location loc = holo.getDisplays().get(0).getLocation();

        getDynamicLines(data).thenAccept(newLines -> {
            plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                holo.removeAll();
                data.setLines(newLines);
                spawnHologram(data);
            });
        });
    }

    private CompletableFuture<List<String>> getDynamicLines(HologramData data) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> lines = new ArrayList<>();
            String type = data.getDynamicType() == null ? "" : data.getDynamicType().toUpperCase();
            int limit = 8;

            if (type.contains("TOP_ISLANDS_LEVEL")) {
                lines.add("&6&l★ Top Islands by Level ★");
                List<TopIslandEntry> top = databaseManager.getTopIslandsByLevel(10);
                int rank = 1;
                for (TopIslandEntry e : top) {
                    lines.add(getMedal(rank) + " " + (e.getOwnerName() != null ? e.getOwnerName() : "Unknown") + " &7- Lvl " + e.getLevel());
                    rank++;
                }
            } else if (type.contains("TOP_ISLANDS_XP")) {
                lines.add("&6&l★ Top Islands by XP ★");
            } else {
                lines.add("&cUnknown type: " + type);
            }

            lines.add("&8Updates every " + (data.getUpdateInterval() / 60) + " min");
            return lines;
        });
    }

    private String getMedal(int rank) {
        return switch (rank) {
            case 1 -> "§6🥇";
            case 2 -> "§7🥈";
            case 3 -> "§c🥉";
            default -> "§f#" + rank;
        };
    }

    public void cleanup() {
        dynamicTasks.values().forEach(ScheduledTask::cancel);
        dynamicTasks.clear();

        for (Hologram holo : activeHolograms.values()) {
            if (!holo.getDisplays().isEmpty()) {
                Location loc = holo.getDisplays().get(0).getLocation();
                plugin.getServer().getRegionScheduler().execute(plugin, loc, holo::removeAll);
            }
        }
        activeHolograms.clear();
    }
}
