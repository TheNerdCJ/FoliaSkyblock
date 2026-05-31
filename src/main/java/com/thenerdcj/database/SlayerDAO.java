package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.BossManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Extracted DAO for Slayer persistence (tokens, stats, etc.).
 * Second DAO after AuctionDAO in the compression effort.
 */
public class SlayerDAO {

    private final FoliaSkyblock plugin;
    private final DBOperations dbOps;

    public SlayerDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        this.plugin = plugin;
        this.dbOps = dbOps;
    }

    public CompletableFuture<List<Object[]>> getTopSlayerTokenEarners(int limit) {
        return dbOps.supplyAsync(() -> {
            List<Object[]> results = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT uuid, tokens FROM slayer_tokens ORDER BY tokens DESC LIMIT ?")) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int tokens = rs.getInt("tokens");
                    results.add(new Object[]{uuid, tokens});
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[SlayerDAO] getTopSlayerTokenEarners failed: " + e.getMessage());
            }
            return results;
        });
    }

    // Additional slayer methods can be migrated here in follow-up steps (stats, kills, etc.)
}