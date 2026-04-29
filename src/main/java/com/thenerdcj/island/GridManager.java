package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class GridManager {

    private final FoliaSkyblock plugin;

    private int currentX = 0;
    private int currentZ = 0;
    private int step = 1;
    private int direction = 0; // 0=right, 1=up, 2=left, 3=down
    // Grid spacing (in blocks) - 512 is a good default for Skyblock
    private final int islandSize = 512;
    private final int islandBuffer = 64; // Space between islands

    // Used islands (persistent)
    private final Set<GridPosition> usedPositions = ConcurrentHashMap.newKeySet();

    // Simple spiral iterator for finding next free spot
    private final Queue<GridPosition> positionQueue = new LinkedList<>();

    public GridManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadUsedPositions(plugin.getDatabaseManager());
        generateInitialSpiral();
    }

    public void loadUsedPositions(DatabaseManager databaseManager) {
        // Query the database for the highest grid position currently in use
        // For now, we'll start at a safe offset
        // TODO: Implement actual database query to find max(grid_x, grid_z)

        this.currentX = 100;  // Start at a safe offset
        this.currentZ = 100;
        this.step = 20;       // Larger step to avoid collisions

        System.out.println("[GridManager] Loaded last position: (" + currentX + ", " + currentZ + ")");
    }

    private void generateInitialSpiral() {
        // Pre-generate some positions in a spiral starting from (0,0)
        positionQueue.add(new GridPosition(0, 0));
        int layer = 1;
        while (positionQueue.size() < 200) { // Preload ~200 positions
            addSpiralLayer(layer++);
        }
    }

    private void addSpiralLayer(int layer) {
        // Right
        for (int i = -layer; i <= layer; i++) positionQueue.add(new GridPosition(layer, i));
        // Up
        for (int i = layer - 1; i >= -layer; i--) positionQueue.add(new GridPosition(i, layer));
        // Left
        for (int i = layer - 1; i >= -layer; i--) positionQueue.add(new GridPosition(-layer, i));
        // Down
        for (int i = -layer + 1; i <= layer - 1; i++) positionQueue.add(new GridPosition(i, -layer));
    }

    /**
     * Creates a new island position for a player (async-friendly)
     */
    public CompletableFuture<GridPosition> createPlayerIsland(UUID playerUuid, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            GridPosition pos = findNextFreePosition();

            if (pos != null) {
                usedPositions.add(pos);
                // TODO: Save to database via DatabaseManager
            }

            return pos;
        });
    }

    private GridPosition findNextFreePosition() {
        while (!positionQueue.isEmpty()) {
            GridPosition pos = positionQueue.poll();

            if (!usedPositions.contains(pos)) {
                // Add more positions to queue if running low
                if (positionQueue.size() < 50) {
                    addSpiralLayer(Math.max(usedPositions.size() / 8, 5));
                }
                return pos;
            }
        }
        return null; // No free spots (very unlikely)
    }

    /**
     * Returns the center Location of an island
     */
    public Location getCenterLocation(GridPosition pos, World world) {
        double x = pos.x() * islandSize + (islandSize / 2.0);
        double z = pos.z() * islandSize + (islandSize / 2.0);
        return new Location(world, x, plugin.getConfig().getInt("island.base-y", 80), z);
    }

    public boolean isIslandLocation(Location loc) {
        if (loc == null) return false;
        GridPosition pos = getGridPosition(loc);
        return usedPositions.contains(pos);
    }

    public GridPosition getGridPosition(Location loc) {
        if (loc == null) return new GridPosition(0, 0);
        int gridX = (int) Math.floor(loc.getBlockX() / (double) islandSize);
        int gridZ = (int) Math.floor(loc.getBlockZ() / (double) islandSize);
        return new GridPosition(gridX, gridZ);
    }

    /**
     * For island deletion
     */
    public CompletableFuture<Boolean> deletePlayerIsland(UUID playerUuid, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Remove from database + usedPositions
            // For now we just mark as free in memory
            plugin.getLogger().info("§eMarked island for player " + playerUuid + " as deleted.");
            return true;
        });
    }

    // ====================== UTILITY ======================
    public int getIslandSize() {
        return islandSize;
    }

    public Set<GridPosition> getUsedPositions() {
        return Collections.unmodifiableSet(usedPositions);
    }

    /**
     * Saves used positions (call on shutdown or periodic save)
     */
    public void saveUsedPositions() {
        // TODO: Save to database
    }
    public int getCurrentX() { return currentX; }
    public int getCurrentZ() { return currentZ; }
}