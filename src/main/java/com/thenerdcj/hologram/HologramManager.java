package com.thenerdcj.hologram;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.TopIslandEntry;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import com.thenerdcj.util.ThreadSafety;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Modern hologram manager for FoliaSkyblock.
 * Fully utilizes Folia schedulers via ThreadSafety:
 * - RegionScheduler for spawn/move at location.
 * - EntityScheduler for per-display updates.
 * - Async for DB.
 * Supports text and item lines using modern Display entities (TextDisplay/ItemDisplay).
 * Inspired by DecentHolograms features (lines, properties, dynamic) but custom implementation.
 */
public class HologramManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final Map<Integer, Hologram> activeHolograms = new ConcurrentHashMap<>();
    private final Map<Integer, ScheduledTask> dynamicTasks = new ConcurrentHashMap<>();
    // All known (persistent) holograms loaded from DB. Used to re-spawn on chunk load/return.
    // This ensures holograms always re-appear when their chunk is loaded, unless they are temp with timer.
    private final Map<Integer, HologramData> allKnownHolograms = new ConcurrentHashMap<>();

    // Bounded for large scale
    private static final int MAX_HOLOGRAMS = 1000;

    public HologramManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
    }

    public void loadAndSpawnAll() {
        plugin.getLogger().info("[HOLO-DEBUG] loadAndSpawnAll() called - starting async DB load for all holograms");
        databaseManager.loadAllHolograms().thenAccept(holoList -> {
            allKnownHolograms.clear();
            plugin.getLogger().info("[HOLO-DEBUG] DB load complete - found " + holoList.size() + " holograms to spawn");
            for (HologramData data : holoList) {
                allKnownHolograms.put(data.getId(), data);
                plugin.getLogger().info("[HOLO-DEBUG] loadAndSpawnAll processing: id=" + data.getId() + " name='" + data.getName() + "' world=" + data.getWorldName() + " lines=" + data.getLines().size() + " dynamic=" + data.isDynamic());
                spawnHologram(data);

                if (data.isDynamic() && data.getDynamicType() != null &&
                    data.getDynamicType().toUpperCase().contains("TOP_ISLANDS")) {
                    List<TopIslandEntry> top = databaseManager.getTopIslandsByLevel(10);
                    for (TopIslandEntry entry : top) {
                        plugin.getNameCache().ensureCachedAsync(entry.getOwnerUuid());
                    }
                }
            }
            MessageUtil.info(plugin.getLogger(), "§a[HologramManager] Loaded and spawned " + holoList.size() + " persistent holograms.");
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "[HOLO-DEBUG] Failed to load holograms", ex);
            return null;
        });
    }

    public void spawnHologram(HologramData data) {
        if (data == null) {
            plugin.getLogger().warning("[HOLO-DEBUG] spawnHologram called with null data!");
            return;
        }

        plugin.getLogger().info("[HOLO-DEBUG] === SPAWN START === id=" + data.getId() + " name='" + data.getName() + "' world='" + data.getWorldName() + 
            "' pos=(" + String.format("%.2f,%.2f,%.2f", data.getX(), data.getY(), data.getZ()) + ") lines=" + data.getLines().size() + 
            " scale=" + data.getScale() + " billboard=" + data.getBillboard() + " dynamic=" + data.isDynamic());

        if (data.getLines().isEmpty()) {
            plugin.getLogger().warning("[HOLO-DEBUG] Aborting spawn - 0 lines for id=" + data.getId());
        }

        Hologram previous = activeHolograms.remove(data.getId());
        ScheduledTask oldTask = dynamicTasks.remove(data.getId());
        if (oldTask != null) oldTask.cancel();

        if (previous != null) {
            plugin.getLogger().info("[HOLO-DEBUG] Cleaning previous wrapper for id=" + data.getId() + " (had " + previous.getDisplays().size() + " displays)");
            if (!previous.getDisplays().isEmpty()) {
                Location loc = previous.getDisplays().get(0).getLocation();
                plugin.getServer().getRegionScheduler().execute(plugin, loc, previous::removeAll);
            } else {
                World prevWorld = Bukkit.getWorld(data.getWorldName());
                if (prevWorld != null) {
                    Location cleanLoc = new Location(prevWorld, data.getX(), data.getY(), data.getZ());
                    plugin.getServer().getRegionScheduler().execute(plugin, cleanLoc, previous::removeAll);
                } else {
                    previous.removeAll();
                }
            }
        }

        Hologram wrapper = activeHolograms.get(data.getId());
        if (wrapper == null) {
            wrapper = new Hologram(data, new ArrayList<>());
            activeHolograms.put(data.getId(), wrapper);
            plugin.getLogger().info("[HOLO-DEBUG] Created new empty wrapper and put in activeHolograms for id=" + data.getId());
        }

        if (data.getLines().isEmpty()) {
            plugin.getLogger().fine("[HologramManager] spawnHologram id=" + data.getId() + " name=" + data.getName() + " skipped (0 lines)");
            return;
        }

        World world = Bukkit.getWorld(data.getWorldName());
        if (world == null) {
            plugin.getLogger().severe("[HOLO-DEBUG] CRITICAL: World '" + data.getWorldName() + "' is NULL for hologram id=" + data.getId() + " name='" + data.getName() + "'. Cannot spawn. Check world name in DB vs actual loaded world.");
            return;
        }
        plugin.getLogger().info("[HOLO-DEBUG] World resolved: " + world.getName() + " (env=" + world.getEnvironment() + ") for id=" + data.getId());

        Location baseLoc = new Location(world, data.getX(), data.getY(), data.getZ());
        boolean isSpawnArea = (plugin.getWorldManager() != null && plugin.getWorldManager().getHubSpawnLocation(world) != null 
            && baseLoc.distance(plugin.getWorldManager().getHubSpawnLocation(world)) < 150);
        plugin.getLogger().info("[HOLO-DEBUG] isSpawnArea=" + isSpawnArea + " for this hologram");

        plugin.getLogger().info("[HologramManager] Scheduling spawn for hologram id=" + data.getId() + " '" + data.getName() + "' with " + data.getLines().size() + " line(s) at " +
                String.format("%.1f,%.1f,%.1f", data.getX(), data.getY(), data.getZ()) + " in world " + data.getWorldName());

        // Reduced delay for faster hologram spawn. Spawn area gets small extra time.
        final int delayTicks = isSpawnArea ? 5 : 1;
        plugin.getLogger().info("[HOLO-DEBUG] Computed delayTicks=" + delayTicks + " (spawnArea=" + isSpawnArea + ")");

        // Use RegionScheduler with delay for reliability on Folia (region activation + chunk load)
        plugin.getServer().getRegionScheduler().runDelayed(plugin, baseLoc, scheduledTask -> {
            try {
                plugin.getLogger().info("[HOLO-DEBUG] >>> REGION TASK START <<< for id=" + data.getId() + " name='" + data.getName() + "' delayWas=" + delayTicks + " thread=" + Thread.currentThread().getName());

                Location chunkLoc = baseLoc.clone();
                boolean wasLoaded = chunkLoc.getChunk().isLoaded();
                chunkLoc.getChunk().load();
                boolean nowLoaded = chunkLoc.getChunk().isLoaded();
                plugin.getLogger().info("[HOLO-DEBUG] Chunk at " + (baseLoc.getBlockX()>>4) + "," + (baseLoc.getBlockZ()>>4) + " wasLoaded=" + wasLoaded + " nowLoaded=" + nowLoaded + " after .load()");

                List<Display> displays = new ArrayList<>();
                double yOffset = 1.0;
                double scale = data.getScale();
                int lineIndex = 0;

                for (String rawLine : data.getLines()) {
                    lineIndex++;
                    Location lineLoc = baseLoc.clone().add(0, yOffset, 0);
                    try { lineLoc.getChunk().load(); } catch (Exception ignored) {}

                    plugin.getLogger().info("[HOLO-DEBUG] Line " + lineIndex + "/" + data.getLines().size() + ": '" + (rawLine.length() > 50 ? rawLine.substring(0,50)+"..." : rawLine) + "' at " + String.format("%.1f,%.1f,%.1f", lineLoc.getX(), lineLoc.getY(), lineLoc.getZ()));

                    Display display = null;
                    try {
                        if (rawLine.startsWith("ITEM:")) {
                            String[] parts = rawLine.substring(5).split(":");
                            Material mat = Material.matchMaterial(parts[0]);
                            if (mat == null) mat = Material.STONE;
                            int amount = 1;
                            if (parts.length > 1) {
                                try { amount = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
                            }
                            ItemStack item = new ItemStack(mat, amount);
                            display = world.spawn(lineLoc, ItemDisplay.class, entity -> {
                                entity.setItemStack(item);
                                entity.setBillboard(Display.Billboard.valueOf(data.getBillboard().toUpperCase()));
                                entity.setTransformation(new org.bukkit.util.Transformation(
                                        new org.joml.Vector3f(0,0,0),
                                        new org.joml.Quaternionf(),
                                        new org.joml.Vector3f((float) scale, (float) scale, (float) scale),
                                        new org.joml.Quaternionf()
                                ));
                                entity.setPersistent(false);
                            });
                        } else {
                            display = world.spawn(lineLoc, TextDisplay.class, entity -> {
                                String processed = rawLine;
                                if (rawLine.contains("%island_worth%") || rawLine.contains("%island_worth_level%")) {
                                    processed = processed.replace("%island_worth%", "N/A").replace("%island_worth_level%", "N/A");
                                }
                                entity.text(MessageUtil.legacy(processed));
                                entity.setBillboard(Display.Billboard.valueOf(data.getBillboard().toUpperCase()));
                                entity.setAlignment(TextDisplay.TextAlignment.CENTER);
                                entity.setLineWidth(1000); // High width so long lines don't auto-wrap inside a single TextDisplay (prevents "extra line" effect)
                                entity.setSeeThrough(data.isSeeThrough());
                                entity.setShadowed(data.isShadow());
                                entity.setBrightness(new Display.Brightness(15, 15));
                                entity.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                                if (data.getBackgroundColor() != null && !data.getBackgroundColor().isEmpty()) {
                                    try {
                                        int argb = Integer.parseUnsignedInt(data.getBackgroundColor().replace("#", ""), 16);
                                        entity.setBackgroundColor(org.bukkit.Color.fromARGB(argb));
                                    } catch (Exception ignored) {}
                                }
                                entity.setTransformation(new org.bukkit.util.Transformation(
                                        new org.joml.Vector3f(0,0,0),
                                        new org.joml.Quaternionf(),
                                        new org.joml.Vector3f((float) scale, (float) scale, (float) scale),
                                        new org.joml.Quaternionf()
                                ));
                                entity.setPersistent(false);
                            });
                        }
                    } catch (Throwable spawnEx) {
                        plugin.getLogger().severe("[HOLO-DEBUG] FAILED to spawn display for line " + lineIndex + " id=" + data.getId() + ": " + spawnEx.getMessage());
                        spawnEx.printStackTrace();
                    }

                    if (display != null) {
                        boolean valid = display.isValid();
                        displays.add(display);
                        plugin.getLogger().info("[HOLO-DEBUG] Spawned " + (rawLine.startsWith("ITEM:") ? "ItemDisplay" : "TextDisplay") + " for line " + lineIndex + " valid=" + valid + " at y=" + String.format("%.2f", display.getLocation().getY()));
                    } else {
                        plugin.getLogger().warning("[HOLO-DEBUG] display was null after spawn attempt for line " + lineIndex);
                    }

                    yOffset -= 0.28 * scale;
                }

                Hologram holo = activeHolograms.get(data.getId());
                if (holo != null) {
                    holo.getDisplays().clear();
                    for (Display d : displays) holo.addDisplay(d);
                    plugin.getLogger().info("[HOLO-DEBUG] Assigned " + displays.size() + " displays to EXISTING wrapper for id=" + data.getId());
                } else {
                    Hologram newH = new Hologram(data, displays);
                    activeHolograms.put(data.getId(), newH);
                    plugin.getLogger().info("[HOLO-DEBUG] Created NEW wrapper and assigned " + displays.size() + " displays for id=" + data.getId());
                }

                plugin.getLogger().info("[HologramManager] Successfully spawned hologram '" + data.getName() + "' (id=" + data.getId() + ") with " + displays.size() + " Display(s). Active count now: " + activeHolograms.size());

                if (data.isDynamic()) {
                    plugin.getLogger().info("[HOLO-DEBUG] Starting dynamic refresh for id=" + data.getId());
                    startDynamicRefresh(activeHolograms.get(data.getId()));
                }
            } catch (Throwable t) {
                plugin.getLogger().severe("[HOLO-DEBUG] EXCEPTION in region task for id=" + data.getId() + " name='" + data.getName() + "': " + t.getMessage());
                t.printStackTrace();
            }
        }, delayTicks);
    }

    public CompletableFuture<Boolean> deleteHologram(int id) {
        allKnownHolograms.remove(id);
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

    public CompletableFuture<Boolean> moveHologram(int id, Location newLocation) {
        if (newLocation == null || newLocation.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }
        String worldName = newLocation.getWorld().getName();
        double x = newLocation.getX();
        double y = newLocation.getY();
        double z = newLocation.getZ();

        Hologram holo = activeHolograms.get(id);
        if (holo != null) {
            HologramData data = holo.getData();
            data.setWorldName(worldName);
            data.setX(x);
            data.setY(y);
            data.setZ(z);

            if (!holo.getDisplays().isEmpty()) {
                Location oldLoc = holo.getDisplays().get(0).getLocation();
                plugin.getServer().getRegionScheduler().execute(plugin, oldLoc, holo::removeAll);
            }
        }

        return databaseManager.updateHologramPosition(id, worldName, x, y, z).thenApply(success -> {
            if (success && holo != null) {
                spawnHologram(holo.getData());
            }
            return success;
        });
    }

    private void startDynamicRefresh(Hologram holo) {
        HologramData data = holo.getData();
        if (!data.isDynamic()) return;

        cancelDynamicTask(data.getId());
        int intervalTicks = Math.max(600, data.getUpdateInterval() * 20);

        List<Display> displays = holo.getDisplays();
        if (displays.isEmpty()) return;

        Display primary = displays.get(0);
        ScheduledTask task = primary.getScheduler().runAtFixedRate(
                plugin,
                t -> refreshDynamicContent(holo),
                null,
                20L,
                intervalTicks
        );
        dynamicTasks.put(data.getId(), task);
    }

    private void cancelDynamicTask(int id) {
        ScheduledTask task = dynamicTasks.remove(id);
        if (task != null) task.cancel();
    }

    public void refreshDynamicContent(Hologram holo) {
        HologramData data = holo.getData();
        if (!data.isDynamic() || holo.getDisplays().isEmpty()) return;

        List<Display> displays = holo.getDisplays();
        getDynamicLines(data).thenAccept(newLines -> {
            Display primary = displays.get(0);
            Runnable updateTask = () -> {
                holo.removeAll();
                data.setLines(newLines);
                spawnHologram(data);
            };
            if (primary.isValid()) {
                primary.getScheduler().run(plugin, t -> updateTask.run(), null);
            } else {
                plugin.getServer().getRegionScheduler().execute(plugin, primary.getLocation(), updateTask);
            }
        });
    }

    private CompletableFuture<List<String>> getDynamicLines(HologramData data) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> lines = new ArrayList<>();
            String type = data.getDynamicType() == null ? "" : data.getDynamicType().toUpperCase();
            if (type.contains("TOP_ISLANDS_LEVEL")) {
                lines.add("&6&l★ Top Islands by Level ★");
                List<TopIslandEntry> top = databaseManager.getTopIslandsByLevel(10);
                for (TopIslandEntry e : top) {
                    plugin.getNameCache().ensureCachedAsync(e.getOwnerUuid());
                }
                int rank = 1;
                for (TopIslandEntry e : top) {
                    String name = plugin.getNameCache().getName(e.getOwnerUuid());
                    lines.add(getMedal(rank) + " " + name + " &7- Lvl " + e.getLevel());
                    rank++;
                }
            } else {
                lines.add("&cUnknown dynamic type: " + type);
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

    public void onWorldUnload(World world) {
        if (world == null) return;
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Hologram> entry : activeHolograms.entrySet()) {
            List<Display> displays = entry.getValue().getDisplays();
            if (!displays.isEmpty()) {
                Display first = displays.get(0);
                if (first != null && first.isValid() && first.getWorld() != null && first.getWorld().equals(world)) {
                    for (Display d : displays) {
                        if (d != null && d.isValid()) d.remove();
                    }
                    toRemove.add(entry.getKey());
                }
            }
        }
        for (int id : toRemove) {
            activeHolograms.remove(id);
            ScheduledTask t = dynamicTasks.remove(id);
            if (t != null) t.cancel();
        }
        if (!toRemove.isEmpty()) {
            plugin.getLogger().info("[HologramManager] Cleaned " + toRemove.size() + " holograms for unloaded world " + world.getName());
        }
    }

    private void cleanupCaches() {
        if (activeHolograms.size() > MAX_HOLOGRAMS) {
            java.util.Iterator<Integer> it = activeHolograms.keySet().iterator();
            int toRemove = activeHolograms.size() - (MAX_HOLOGRAMS - 100);
            while (it.hasNext() && toRemove > 0) {
                int id = it.next(); it.remove();
                ScheduledTask t = dynamicTasks.remove(id);
                if (t != null) t.cancel();
                toRemove--;
            }
        }
        if (dynamicTasks.size() > MAX_HOLOGRAMS) {
            java.util.Iterator<Integer> it = dynamicTasks.keySet().iterator();
            int toRemove = dynamicTasks.size() - (MAX_HOLOGRAMS - 100);
            while (it.hasNext() && toRemove > 0) {
                int id = it.next();
                ScheduledTask t = dynamicTasks.remove(id);
                if (t != null) t.cancel();
                toRemove--;
            }
        }
    }

    /**
     * Called on ChunkLoadEvent to re-spawn any known holograms whose location falls in this chunk.
     * This ensures holograms always re-appear when their chunk is loaded/returned to (fixes disappearing on chunk leave/return).
     * All holograms are persistent by default (re-spawned).
     * For temporary ones with timer, they are deleted on timer, so won't be in known after.
     */
    public void onChunkLoad(Chunk chunk) {
        if (chunk == null || allKnownHolograms.isEmpty()) return;
        String wName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        for (HologramData data : allKnownHolograms.values()) {
            if (!data.getWorldName().equals(wName)) continue;
            int hx = (int) Math.floor(data.getX()) >> 4;
            int hz = (int) Math.floor(data.getZ()) >> 4;
            if (hx == cx && hz == cz) {
                Hologram active = activeHolograms.get(data.getId());
                boolean needsRespawn = (active == null);
                if (active != null) {
                    // Remove any stale/invalid Display refs that were auto-removed by server (due to persistent=false + chunk unload)
                    // This was causing holograms to "despawn eventually" and never reappear on subsequent chunk loads.
                    active.getDisplays().removeIf(d -> d == null || !d.isValid());
                    if (active.getDisplays().isEmpty()) {
                        needsRespawn = true;
                    }
                }
                if (needsRespawn) {
                    plugin.getLogger().info("[HOLO-DEBUG] Re-spawning hologram id=" + data.getId() + " name='" + data.getName() + "' on chunk load " + cx + "," + cz);
                    spawnHologram(data);
                }
            }
        }
    }
}