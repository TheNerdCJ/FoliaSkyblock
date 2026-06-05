package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.World;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Grid allocation persistence (extracted from {@link com.thenerdcj.manager.GridManager}).
 */
public class GridDAO extends BaseDAO {

    public GridDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
    }

    public CompletableFuture<Set<GridPosition>> loadUsedGridPositions() {
        return supplyAsync(() -> {
            Set<GridPosition> used = new HashSet<>();
            withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT grid_x, grid_z, dimension FROM islands");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int x = rs.getInt("grid_x");
                        int z = rs.getInt("grid_z");
                        String dimStr = rs.getString("dimension");
                        try {
                            World.Environment dimension = World.Environment.valueOf(dimStr);
                            used.add(new GridPosition(x, z, dimension));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            return used;
        });
    }
}