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

                // Preload names for any top-islands dynamic holograms
                if (data.isDynamic() && data.getDynamicType() != null &&
                    data.getDynamicType().toUpperCase().contains("TOP_ISLANDS")) {
                    List<TopIslandEntry> top = databaseManager.getTopIslandsByLevel(10);
                    for (TopIslandEntry entry : top) {
                        plugin.getNameCache().ensureCachedAsync(entry.getOwnerUuid());
                    }
                }
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
                TextDisplay primary = holo.getDisplays().get(0);
                Runnable removalTask = () -> {
                    holo.removeAll();
                    spawnHologram(holo.getData());
                };

                if (primary.isValid() && primary.getScheduler() != null) {
                    primary.getScheduler().run(plugin, t -> removalTask.run(), null);
                } else {
                    plugin.getServer().getRegionScheduler().execute(plugin, primary.getLocation(), removalTask);
                }
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

        List<TextDisplay> displays = holo.getDisplays();
        if (displays.isEmpty()) return;

        // Deeper Folia optimization: Attach the repeating refresh task to one of the TextDisplay entities
        // using EntityScheduler. This is more precise than GlobalRegionScheduler for per-hologram ticking.
        TextDisplay primaryDisplay = displays.get(0);

        if (plugin.getThreadSafety().isFolia() && primaryDisplay.isValid()) {
            ScheduledTask task = primaryDisplay.getScheduler().runAtFixedRate(
                    plugin,
                    t -> refreshDynamicContent(holo),
                    null,                    // retired runnable
                    20L,                     // initial delay
                    intervalTicks
            );
            dynamicTasks.put(data.getId(), task);
        } else {
            // Fallback for non-Folia
            ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    t -> refreshDynamicContent(holo),
                    20L,
                    intervalTicks
            );
            dynamicTasks.put(data.getId(), task);
        }
    }

    private void cancelDynamicTask(int id) {
        ScheduledTask task = dynamicTasks.remove(id);
        if (task != null) task.cancel();
    }

    public void refreshDynamicContent(Hologram holo) {
        HologramData data = holo.getData();
        if (!data.isDynamic() || holo.getDisplays().isEmpty()) return;

        List<TextDisplay> displays = holo.getDisplays();
        Location loc = displays.get(0).getLocation();

        getDynamicLines(data).thenAccept(newLines -> {
            // Prefer entity scheduler on the primary display for the actual update when possible
            TextDisplay primary = displays.get(0);

            Runnable updateTask = () -> {
                holo.removeAll();
                data.setLines(newLines);
                spawnHologram(data);
            };

            if (plugin.getThreadSafety().isFolia() && primary.isValid()) {
                primary.getScheduler().run(plugin, t -> updateTask.run(), null);
            } else {
                plugin.getServer().getRegionScheduler().execute(plugin, loc, updateTask);
            }
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

                // Use centralized NameCache for proper caching (much better than per-entry lazy lookup)
                for (TopIslandEntry e : top) {
                    plugin.getNameCache().ensureCachedAsync(e.getOwnerUuid());
                }

                int rank = 1;
                for (TopIslandEntry e : top) {
                    String name = plugin.getNameCache().getName(e.getOwnerUuid());
                    lines.add(getMedal(rank) + " " + name + " &7- Lvl " + e.getLevel());
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
