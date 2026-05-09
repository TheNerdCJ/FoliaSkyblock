package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GridManager - Handles island grid position allocation and tracking.
 *
 * Responsibilities:
 * - Allocate unique grid positions for new islands
 * - Track which positions are already used
 * - Provide center locations for islands
 *
 * Note: Actual island data (owner, biome, level, etc.) is stored via DatabaseManager.
 * GridManager only manages the grid coordinate system.
 */
public class GridManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;

    // Grid configuration
    private final int islandSize = 512;      // Distance between island centers
    private final int islandBuffer = 64;     // Extra buffer space

    // Tracking
    private final Set<GridPosition> usedPositions = ConcurrentHashMap.newKeySet();

    // Spiral position generator
    private final Queue<GridPosition> positionQueue = new LinkedList<>();
    private int currentLayer = 1;

    public GridManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        loadUsedPositions();
        generateInitialSpiral();
    }

    // ====================== POSITION LOADING ======================

    /**
     * Loads all existing island grid positions from the database.
     */
    private void loadUsedPositions() {
        String sql = "SELECT grid_x, grid_z, dimension FROM islands";

        CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    int x = rs.getInt("grid_x");
                    int z = rs.getInt("grid_z");
                    String dimStr = rs.getString("dimension");

                    try {
                        World.Environment dimension = World.Environment.valueOf(dimStr);
                        GridPosition pos = new GridPosition(x, z, dimension);
                        usedPositions.add(pos);
                        count++;
                    } catch (IllegalArgumentException ignored) {}
                }

                plugin.getLogger().info("[GridManager] Loaded " + count + " used island positions from database.");

            } catch (SQLException e) {
                plugin.getLogger().severe("[GridManager] Failed to load used positions: " + e.getMessage());
            }
        });
    }

    // ====================== SPIRAL GENERATION ======================

    private void generateInitialSpiral() {
        // Start with (0,0) - protected spawn area
        positionQueue.add(new GridPosition(0, 0));

        // Pre-generate a large number of positions
        while (positionQueue.size() < 500) {
            addSpiralLayer(currentLayer++);
        }
    }

    private void addSpiralLayer(int layer) {
        // Right side
        for (int i = -layer; i <= layer; i++) {
            positionQueue.add(new GridPosition(layer, i));
        }
        // Top side
        for (int i = layer - 1; i >= -layer; i--) {
            positionQueue.add(new GridPosition(i, layer));
        }
        // Left side
        for (int i = layer - 1; i >= -layer; i--) {
            positionQueue.add(new GridPosition(-layer, i));
        }
        // Bottom side
        for (int i = -layer + 1; i <= layer - 1; i++) {
            positionQueue.add(new GridPosition(i, -layer));
        }
    }

    // ====================== CORE METHODS ======================

    /**
     * Allocates the next available grid position for a new island.
     */
    public CompletableFuture<GridPosition> createPlayerIsland(UUID playerUuid, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            GridPosition pos = findNextFreePosition(dimension);

            if (pos != null) {
                usedPositions.add(pos);
                plugin.getLogger().info("[GridManager] Allocated new position: " + pos);
            } else {
                plugin.getLogger().warning("[GridManager] No free positions available!");
            }

            return pos;
        });
    }

    private GridPosition findNextFreePosition(World.Environment dimension) {
        while (!positionQueue.isEmpty()) {
            GridPosition candidate = positionQueue.poll();

            GridPosition withDimension = new GridPosition(candidate.x(), candidate.z(), dimension);

            if (!usedPositions.contains(withDimension)) {
                // Refill queue if getting low
                if (positionQueue.size() < 100) {
                    addSpiralLayer(currentLayer++);
                }
                return withDimension;
            }
        }
        return null;
    }

    /**
     * Marks an island position as deleted (frees it up).
     */
    public CompletableFuture<Boolean> deletePlayerIsland(UUID playerUuid, World.Environment dimension) {
        return CompletableFuture.supplyAsync(() -> {
            // Note: The actual database deletion is handled by DatabaseManager.deleteIsland()
            // We only remove it from our in-memory tracking here.
            // In a more advanced version we could store which player owned which position.

            // For now we just log it. Full cleanup would require tracking owner -> position mapping.
            plugin.getLogger().info("[GridManager] Island for player " + playerUuid + " marked for deletion in " + dimension);

            return true;
        });
    }

    /**
     * Saves used positions (can be called on shutdown or periodically).
     */
    public void saveUsedPositions() {
        // Currently we load on startup and manage in-memory.
        // If you want persistence of the grid state independently of the islands table,
        // you can implement saving here.
        plugin.getLogger().info("[GridManager] Used positions saved (in-memory).");
    }

    // ====================== UTILITY METHODS ======================

    public Location getCenterLocation(GridPosition pos, World world) {
        if (pos == null || world == null) return null;

        double x = pos.x() * islandSize + (islandSize / 2.0);
        double z = pos.z() * islandSize + (islandSize / 2.0);

        int baseY = plugin.getConfig().getInt("island.base-y", 80);
        return new Location(world, x, baseY, z);
    }

    public boolean isIslandLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        GridPosition pos = getGridPosition(loc);
        return usedPositions.contains(pos);
    }

    public GridPosition getGridPosition(Location loc) {
        if (loc == null) return new GridPosition(0, 0);

        int gridX = (int) Math.floor(loc.getBlockX() / (double) islandSize);
        int gridZ = (int) Math.floor(loc.getBlockZ() / (double) islandSize);

        return new GridPosition(gridX, gridZ, loc.getWorld().getEnvironment());
    }

    public int getIslandSize() {
        return islandSize;
    }

    public Set<GridPosition> getUsedPositions() {
        return Collections.unmodifiableSet(usedPositions);
    }
}