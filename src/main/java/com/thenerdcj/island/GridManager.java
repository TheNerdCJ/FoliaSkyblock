package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class GridManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final int regionDistance;

    // Cache last known free position per dimension for performance
    private final Map<Environment, GridPosition> lastFreePositionCache = new ConcurrentHashMap<>();

    public GridManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.regionDistance = plugin.getConfig().getInt("grid.region-distance", 10);

        plugin.getLogger().info("§aGridManager initialized with region-distance = " + regionDistance);
    }

    public CompletableFuture<GridPosition> findNextFreePosition(Environment dimension) {
        return databaseManager.getAllOccupiedPositionsInDimension(dimension)
                .thenApply(occupied -> {
                    GridPosition cached = lastFreePositionCache.get(dimension);
                    int startX = (cached != null) ? cached.x() : 0;
                    int startZ = (cached != null) ? cached.z() : 0;

                    int x = startX, z = startZ;
                    int dx = 0, dz = -1;
                    int steps = 0;
                    int sideLength = 1;

                    while (true) {
                        GridPosition pos = new GridPosition(x, z);
                        if (!(dimension == Environment.NORMAL && pos.isSpawn()) && !occupied.contains(pos)) {
                            lastFreePositionCache.put(dimension, pos);
                            return pos;
                        }

                        steps++;
                        if (steps == sideLength) {
                            steps = 0;
                            int temp = dx;
                            dx = -dz;
                            dz = temp;
                            if (dx == 0) sideLength++;
                        }
                        x += dx;
                        z += dz;
                    }
                });
    }

    public CompletableFuture<GridPosition> createPlayerIsland(UUID uuid, Environment dimension) {
        return databaseManager.hasIslandInDimension(uuid, dimension)
                .thenCompose(hasIsland -> {
                    if (hasIsland) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return findNextFreePosition(dimension)
                            .thenCompose(pos -> {
                                if (pos == null) return CompletableFuture.completedFuture(null);
                                return databaseManager.createIsland(uuid, pos.x(), pos.z(), dimension)
                                        .thenApply(success -> success ? pos : null);
                            });
                });
    }

    public CompletableFuture<Boolean> deletePlayerIsland(UUID uuid, Environment dimension) {
        lastFreePositionCache.remove(dimension);
        return databaseManager.deleteIsland(uuid, dimension);
    }

    public CompletableFuture<GridPosition> getPlayerIslandPosition(UUID uuid, Environment dimension) {
        return databaseManager.getIslandPosition(uuid, dimension);
    }

    public Location getCenterLocation(GridPosition pos, World world) {
        int blockX = pos.x() * regionDistance * 512;
        int blockZ = pos.z() * regionDistance * 512;
        return new Location(world, blockX + 0.5, 100.0, blockZ + 0.5);
    }

    public int getRegionDistance() {
        return regionDistance;
    }

    // Backward compatibility
    public CompletableFuture<GridPosition> findNextFreePosition() {
        return findNextFreePosition(Environment.NORMAL);
    }

    public CompletableFuture<GridPosition> createPlayerIsland(UUID uuid) {
        return createPlayerIsland(uuid, Environment.NORMAL);
    }
}