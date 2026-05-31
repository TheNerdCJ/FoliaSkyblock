package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.mission.Mission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Data Access Object for Island Missions.
 * Extracted as part of full DatabaseManager modularization.
 */
public class MissionDAO extends BaseDAO {

    public MissionDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Schema managed centrally for now
    }

    public CompletableFuture<Boolean> saveMission(Mission mission) {
        return supplyAsync(() -> {
            try (Connection conn = dbOps.getPlugin().getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_missions (id, island_key, owner_uuid, type, objective, target_material, target, progress, reward_money, reward_xp, reward_item_base64, reward_booster_type, reward_booster_duration, completed, claimed, created_at, expires_at, title, description) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                ps.setString(1, mission.getId());
                ps.setString(2, mission.getIslandKey());
                ps.setString(3, mission.getIslandOwner() != null ? mission.getIslandOwner().toString() : null);
                ps.setString(4, mission.getType().name());
                ps.setString(5, mission.getObjective().name());
                ps.setString(6, mission.getTargetMaterial());
                ps.setInt(7, mission.getTarget());
                ps.setInt(8, mission.getProgress());
                ps.setInt(9, mission.getRewardMoney());
                ps.setInt(10, mission.getRewardIslandXp());
                ps.setString(11, mission.getRewardItemBase64());
                ps.setString(12, mission.getRewardBoosterType() != null ? mission.getRewardBoosterType().name() : null);
                ps.setInt(13, mission.getRewardBoosterDurationMinutes());
                ps.setBoolean(14, mission.isCompleted());
                ps.setBoolean(15, mission.isClaimed());
                ps.setLong(16, mission.getCreatedAt());
                ps.setLong(17, mission.getExpiresAt());
                ps.setString(18, mission.getTitle());
                ps.setString(19, mission.getDescription());

                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[MissionDAO] saveMission failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<List<Mission>> loadMissionsForIsland(String islandKey) {
        return supplyAsync(() -> {
            List<Mission> missions = new ArrayList<>();
            try (Connection conn = dbOps.getPlugin().getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM island_missions WHERE island_key = ?")) {

                ps.setString(1, islandKey);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    // Simplified reconstruction - full deserialization logic should be in Mission class or a mapper
                    // For now we return a stub; real implementation would use full fields
                    Mission m = new Mission(
                            rs.getString("id"),
                            rs.getString("island_key"),
                            rs.getString("owner_uuid") != null ? UUID.fromString(rs.getString("owner_uuid")) : null,
                            com.thenerdcj.mission.Mission.MissionType.valueOf(rs.getString("type")),
                            com.thenerdcj.mission.Mission.ObjectiveType.valueOf(rs.getString("objective")),
                            rs.getString("target_material"),
                            rs.getInt("target"),
                            rs.getInt("progress"),
                            rs.getInt("reward_money"),
                            rs.getInt("reward_xp"),
                            rs.getString("reward_item_base64"),
                            rs.getString("reward_booster_type") != null ? com.thenerdcj.booster.BoosterType.valueOf(rs.getString("reward_booster_type")) : null,
                            rs.getInt("reward_booster_duration"),
                            rs.getBoolean("completed"),
                            rs.getBoolean("claimed"),
                            rs.getLong("created_at"),
                            rs.getLong("expires_at"),
                            rs.getString("title"),
                            rs.getString("description")
                    );
                    missions.add(m);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[MissionDAO] loadMissionsForIsland failed: " + e.getMessage());
            }
            return missions;
        });
    }
}