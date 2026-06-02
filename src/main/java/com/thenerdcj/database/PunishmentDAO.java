package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Data Access Object for player punishments (bans, mutes, etc.).
 * Extracted from DatabaseManager as part of ongoing modularization.
 * 
 * Uses the Punishment model (same package).
 * Follows BaseDAO + bridge pattern.
 */
public class PunishmentDAO extends BaseDAO {

    public PunishmentDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // punishments table created centrally.
    }

    public CompletableFuture<Boolean> logPunishment(UUID target, UUID staff, Punishment.Type type, String reason, long duration) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO punishments (target_uuid, staff_uuid, type, reason, duration, timestamp, active) VALUES (?, ?, ?, ?, ?, ?, 1)")) {
                        ps.setString(1, target.toString());
                        ps.setString(2, staff != null ? staff.toString() : null);
                        ps.setString(3, type.name());
                        ps.setString(4, reason);
                        ps.setLong(5, duration);
                        ps.setLong(6, System.currentTimeMillis());
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[PunishmentDAO] logPunishment failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<List<Punishment>> getActivePunishments(UUID uuid) {
        return supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT * FROM punishments WHERE target_uuid = ? AND active = 1 ORDER BY timestamp DESC")) {
                        ps.setString(1, uuid.toString());
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            Punishment p = new Punishment(
                                    rs.getInt("id"),
                                    UUID.fromString(rs.getString("target_uuid")),
                                    rs.getString("staff_uuid") != null ? UUID.fromString(rs.getString("staff_uuid")) : null,
                                    Punishment.Type.valueOf(rs.getString("type")),
                                    rs.getString("reason"),
                                    rs.getLong("timestamp"),
                                    rs.getLong("duration"),
                                    rs.getBoolean("active")
                            );
                            list.add(p);
                        }
                        return list;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[PunishmentDAO] getActivePunishments failed: " + e.getMessage());
                return list;
            }
        });
    }

    public CompletableFuture<List<Punishment>> getPunishmentsForPlayer(UUID uuid) {
        return supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT * FROM punishments WHERE target_uuid = ? ORDER BY timestamp DESC")) {
                        ps.setString(1, uuid.toString());
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            Punishment p = new Punishment(
                                    rs.getInt("id"),
                                    UUID.fromString(rs.getString("target_uuid")),
                                    rs.getString("staff_uuid") != null ? UUID.fromString(rs.getString("staff_uuid")) : null,
                                    Punishment.Type.valueOf(rs.getString("type")),
                                    rs.getString("reason"),
                                    rs.getLong("timestamp"),
                                    rs.getLong("duration"),
                                    rs.getBoolean("active")
                            );
                            list.add(p);
                        }
                        return list;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[PunishmentDAO] getPunishmentsForPlayer failed: " + e.getMessage());
                return list;
            }
        });
    }

    public CompletableFuture<Boolean> unbanPlayer(UUID uuid) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE punishments SET active = 0 WHERE target_uuid = ? AND type IN ('BAN', 'TEMPBAN') AND active = 1")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[PunishmentDAO] unbanPlayer failed: " + e.getMessage());
                return false;
            }
        });
    }
}