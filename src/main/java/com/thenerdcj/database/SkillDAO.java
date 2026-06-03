package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * DAO for player skills (MCMMO-style per-player, contributes to island XP).
 * Extracted for task 3 DB modularization (remaining from inline in DatabaseManager).
 * 
 * Tables: player_skills (uuid, skill, xp, level)
 * Folia-safe async via DBOperations.
 * PtW: skills only from play actions (listeners enforce AC).
 * Used by: PlayerSkillManager, Island (load/save progression), SkillListener.
 */
public class SkillDAO extends BaseDAO {

    public SkillDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Table created in DatabaseManager.createTables() for now (migration bridge).
    }

    public CompletableFuture<Map<Island.Skill, Double>> loadPlayerSkillXp(UUID uuid) {
        return supplyAsync(() -> {
            Map<Island.Skill, Double> xpMap = new EnumMap<>(Island.Skill.class);
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT skill, xp FROM player_skills WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            try {
                                Island.Skill sk = Island.Skill.valueOf(rs.getString("skill").toUpperCase());
                                xpMap.put(sk, rs.getDouble("xp"));
                            } catch (IllegalArgumentException ignored) {}
                        }
                        return xpMap;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[SkillDAO] loadPlayerSkillXp failed: " + e.getMessage());
                return xpMap;
            }
        });
    }

    public CompletableFuture<Map<Island.Skill, Integer>> loadPlayerSkillLevels(UUID uuid) {
        return supplyAsync(() -> {
            Map<Island.Skill, Integer> lvlMap = new EnumMap<>(Island.Skill.class);
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT skill, level FROM player_skills WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            try {
                                Island.Skill sk = Island.Skill.valueOf(rs.getString("skill").toUpperCase());
                                lvlMap.put(sk, rs.getInt("level"));
                            } catch (IllegalArgumentException ignored) {}
                        }
                        return lvlMap;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[SkillDAO] loadPlayerSkillLevels failed: " + e.getMessage());
                return lvlMap;
            }
        });
    }

    public CompletableFuture<Boolean> savePlayerSkill(UUID uuid, Island.Skill skill, double xp, int level) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO player_skills (uuid, skill, xp, level) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, skill.name());
                        ps.setDouble(3, xp);
                        ps.setInt(4, level);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[SkillDAO] savePlayerSkill failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deletePlayerSkills(UUID uuid) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_skills WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[SkillDAO] deletePlayerSkills failed: " + e.getMessage());
                return false;
            }
        });
    }
}