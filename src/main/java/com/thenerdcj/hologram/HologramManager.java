package com.thenerdcj.hologram;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.TopIslandEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.Component;
import com.thenerdcj.util.MessageUtil;
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
    private final Map<Integer, Hologram> activeHolograms = new ConcurrentHashMap<>();
    private final Map<Integer, ScheduledTask> dynamicTasks = new ConcurrentHashMap<>();

    // Bounded for large scale (many dynamic/top holograms on large servers)
    private static final int MAX_HOLOGRAMS = 1000;

    public HologramManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        // Periodic bound for compression
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
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
            MessageUtil.info(plugin.getLogger(), "§a[HologramManager] Loaded and spawned " + holoList.size() + " persistent holograms.");
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to load holograms", ex);
            return null;
        });
    }

    public void spawnHologram(HologramData data) {
        if (data == null) return;

        // Clean any previous runtime hologram for this id (handles line edits, moves, refreshes, and re-spawns).
        // This prevents duplicate entity stacks and ensures the active map always points to a current wrapper
        // with up-to-date visuals. We remove the entry first, then schedule cleanup of its (possibly already
        // cleared) displays using the persisted data location as the authoritative spot.
        Hologram previous = activeHolograms.remove(data.getId());
        ScheduledTask oldTask = dynamicTasks.remove(data.getId());
        if (oldTask != null) oldTask.cancel();

        if (previous != null) {
            // Prefer the previous wrapper's actual display locations for cleanup (important for /movehere
            // where the passed data has already been mutated to the *new* position).
            if (!previous.getDisplays().isEmpty()) {
                Location loc = previous.getDisplays().get(0).getLocation();
                plugin.getServer().getRegionScheduler().execute(plugin, loc, previous::removeAll);
            } else {
                // Fallback to the (current) data location
                World prevWorld = Bukkit.getWorld(data.getWorldName());
                if (prevWorld != null) {
                    Location cleanLoc = new Location(prevWorld, data.getX(), data.getY(), data.getZ());
                    plugin.getServer().getRegionScheduler().execute(plugin, cleanLoc, previous::removeAll);
                } else {
                    // Last resort
                    previous.removeAll();
                }
            }
        }

        // Always ensure a wrapper exists in activeHolograms (even for 0-line "empty" holograms created via /holo create).
        // This allows /holo addline etc. to find it by name immediately after creation (before any lines are added).
        // Entities are only spawned below if there are lines.
        Hologram wrapper = activeHolograms.get(data.getId());
        if (wrapper == null) {
            wrapper = new Hologram(data, new ArrayList<>());
            activeHolograms.put(data.getId(), wrapper);
        } else {
            // Update the data reference in case it was reloaded
            // (lines will be set below if we spawn)
        }

        if (data.getLines().isEmpty()) {
            return;
        }

        World world = Bukkit.getWorld(data.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Hologram world not found: " + data.getWorldName());
            return;
        }

        Location baseLoc = new Location(world, data.getX(), data.getY(), data.getZ());

        plugin.getServer().getRegionScheduler().execute(plugin, baseLoc, () -> {
            List<TextDisplay> displays = new ArrayList<>();
            double yOffset = 1.0; // start a bit above feet level so the text is visibly floating even when base is at player.getLocation()
            double scale = data.getScale();   // Local scale variable for consistent use in spacing and entity scaling

            for (String rawLine : data.getLines()) {
                Location lineLoc = baseLoc.clone().add(0, yOffset, 0);

                TextDisplay td = world.spawn(lineLoc, TextDisplay.class, entity -> {
                    String processedLine = rawLine;

                    // Basic exposure of Island Worth system in holograms
                    if (rawLine.contains("%island_worth%") || rawLine.contains("%island_worth_level%")) {
                        // This is simplistic; real impl would resolve per-hologram island
                        // For owner-linked holograms we can enhance later
                        processedLine = processedLine
                            .replace("%island_worth%", "N/A")
                            .replace("%island_worth_level%", "N/A");
                    }

                    // Use MessageUtil.legacy so both & and § color codes work reliably for holograms.
                    entity.text(MessageUtil.legacy(processedLine));
                    entity.setBillboard(org.bukkit.entity.Display.Billboard.valueOf(data.getBillboard().toUpperCase()));
                    entity.setSeeThrough(data.isSeeThrough());
                    entity.setShadowed(data.isShadow());
                    entity.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15)); // full bright so text is always visible even at feet level / low light
                    entity.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); // transparent background for clean floating text
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

            // Update the pre-created wrapper (from empty-hologram support above) with the live displays list.
            // We avoid overwriting the map entry so that empty-created holograms remain findable by name.
            Hologram holo = activeHolograms.get(data.getId());
            if (holo != null) {
                holo.getDisplays().clear();
                holo.getDisplays().addAll(displays);
            } else {
                // Fallback safety (shouldn't happen)
                holo = new Hologram(data, displays);
                activeHolograms.put(data.getId(), holo);
            }

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
            // Visual spawn/respawn is now driven by the caller (e.g. HologramCommand) AFTER the DB update succeeds.
            // This avoids race conditions between the sync spawn and the async DB future, ensuring the text reliably appears.
        } else {
            plugin.getLogger().fine("[HologramManager] updateLines for id=" + id + " had no active wrapper; only DB updated.");
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

    /**
     * Moves an existing hologram to a new location (persists + respawns visuals).
     * Used by command /holo movehere and the GUI "move to player" action.
     * Cleans up old visuals using the previous wrapper if present.
     */
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

            // Schedule removal of the old visuals immediately (using their current positions)
            if (!holo.getDisplays().isEmpty()) {
                Location oldLoc = holo.getDisplays().get(0).getLocation();
                plugin.getServer().getRegionScheduler().execute(plugin, oldLoc, holo::removeAll);
            }
        }

        return databaseManager.updateHologramPosition(id, worldName, x, y, z).thenApply(success -> {
            if (success && holo != null) {
                // Respawn at the (now updated) data location.
                // spawnHologram will clean any remaining map entry + schedule any leftover cleanup.
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

        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
        final long profileStart = start;

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
            if (profileStart != 0) {
                long ns = System.nanoTime() - profileStart;
                if (ns > 500_000L) plugin.getLogger().info("[HologramManager] PROFILE: refreshDynamicContent took " + (ns / 1_000_000.0) + " ms (hot path for dynamic holograms on large servers)");
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

    /**
     * Folia edge-case hook: called on WorldUnloadEvent to drop tracking of holograms in the unloaded world.
     * Prevents stale entity references and scheduler tasks attached to displays in unloaded regions.
     */
    public void onWorldUnload(World world) {
        if (world == null) return;
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Hologram> entry : activeHolograms.entrySet()) {
            List<TextDisplay> displays = entry.getValue().getDisplays();
            if (!displays.isEmpty()) {
                TextDisplay first = displays.get(0);
                if (first != null && first.isValid() && first.getWorld() != null && first.getWorld().equals(world)) {
                    // Remove the visual entities too
                    for (TextDisplay d : displays) {
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

    /**
     * Bounded eviction for activeHolograms/dynamicTasks (large scale compression, avoid unbounded globals).
     * Ties to "Minion/Hologram: ensure all ticking uses EntityScheduler ... avoid any global lists iteration without bounds".
     */
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
}
