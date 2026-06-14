package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.hologram.HologramData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Data Access Object for persistent holograms (admin/info/leaderboard displays).
 * Modern implementation using DBOperations for Folia-safe async DB access.
 * Supports text lines and basic properties for TextDisplay entities.
 */
public class HologramDAO extends BaseDAO {

    public HologramDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Schema handled in DatabaseManager for now.
    }

    public CompletableFuture<List<HologramData>> loadAllHolograms() {
        return supplyAsync(() -> {
            List<HologramData> list = new ArrayList<>();
            try {
                withConnection(conn -> {
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT * FROM holograms")) {

                        while (rs.next()) {
                            int id = rs.getInt("id");
                            HologramData data = new HologramData(
                                    rs.getString("name"),
                                    rs.getString("world"),
                                    rs.getDouble("x"),
                                    rs.getDouble("y"),
                                    rs.getDouble("z")
                            );
                            data.setId(id);

                            // Load lines (separate table)
                            try (PreparedStatement linePs = conn.prepareStatement(
                                    "SELECT text FROM hologram_lines WHERE holo_id = ? ORDER BY line_index")) {
                                linePs.setInt(1, id);
                                ResultSet lineRs = linePs.executeQuery();
                                List<String> lines = new ArrayList<>();
                                while (lineRs.next()) lines.add(lineRs.getString("text"));
                                data.setLines(lines);
                            } catch (SQLException ex) {
                                throw new RuntimeException(ex);
                            }

                            // Optional fields
                            try { data.setBillboard(rs.getString("billboard")); } catch (Exception ignored) {}
                            try { data.setScale(rs.getDouble("scale")); } catch (Exception ignored) {}
                            try { data.setSeeThrough(rs.getBoolean("see_through")); } catch (Exception ignored) {}
                            try { data.setShadow(rs.getBoolean("shadow")); } catch (Exception ignored) {}
                            try { data.setBackgroundColor(rs.getString("background_color")); } catch (Exception ignored) {}
                            try { data.setDynamic(rs.getBoolean("is_dynamic")); } catch (Exception ignored) {}
                            try { data.setDynamicType(rs.getString("dynamic_type")); } catch (Exception ignored) {}
                            try { data.setUpdateInterval(rs.getInt("update_interval")); } catch (Exception ignored) {}

                            list.add(data);
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[HologramDAO] loadAllHolograms failed: " + e.getMessage());
            }
            return list;
        });
    }

    public CompletableFuture<Boolean> saveHologram(HologramData data) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    int id;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO holograms (name, world, x, y, z, billboard, background_color, scale, see_through, shadow, permission, is_dynamic, dynamic_type, update_interval) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {

                        ps.setString(1, data.getName());
                        ps.setString(2, data.getWorldName());
                        ps.setDouble(3, data.getX());
                        ps.setDouble(4, data.getY());
                        ps.setDouble(5, data.getZ());
                        ps.setString(6, data.getBillboard() != null ? data.getBillboard() : "CENTER");
                        ps.setString(7, data.getBackgroundColor());
                        ps.setDouble(8, data.getScale());
                        ps.setBoolean(9, data.isSeeThrough());
                        ps.setBoolean(10, data.isShadow());
                        ps.setString(11, data.getPermission());
                        ps.setBoolean(12, data.isDynamic());
                        ps.setString(13, data.getDynamicType());
                        ps.setInt(14, data.getUpdateInterval());
                        ps.executeUpdate();

                        ResultSet keys = ps.getGeneratedKeys();
                        id = keys.next() ? keys.getInt(1) : -1;
                        data.setId(id);
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }

                    // Save lines (replace)
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM hologram_lines WHERE holo_id = ?");
                         PreparedStatement ins = conn.prepareStatement(
                                 "INSERT INTO hologram_lines (holo_id, line_index, text) VALUES (?, ?, ?)")) {
                        del.setInt(1, id);
                        del.executeUpdate();

                        for (int i = 0; i < data.getLines().size(); i++) {
                            ins.setInt(1, id);
                            ins.setInt(2, i);
                            ins.setString(3, data.getLines().get(i));
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return true;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[HologramDAO] saveHologram failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deleteHologram(int id) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM holograms WHERE id = ?")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM hologram_lines WHERE holo_id = ?")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return true;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[HologramDAO] deleteHologram failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> updateHologramLines(int id, List<String> lines) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM hologram_lines WHERE holo_id = ?")) {
                        del.setInt(1, id);
                        del.executeUpdate();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO hologram_lines (holo_id, line_index, text) VALUES (?, ?, ?)")) {
                        for (int i = 0; i < lines.size(); i++) {
                            ins.setInt(1, id);
                            ins.setInt(2, i);
                            ins.setString(3, lines.get(i));
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return true;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[HologramDAO] updateHologramLines failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> updateHologramInterval(int id, int interval) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE holograms SET update_interval = ? WHERE id = ?")) {
                        ps.setInt(1, interval);
                        ps.setInt(2, id);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[HologramDAO] updateHologramInterval failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> updateHologramPosition(int id, String world, double x, double y, double z) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE holograms SET world = ?, x = ?, y = ?, z = ? WHERE id = ?")) {
                        ps.setString(1, world);
                        ps.setDouble(2, x);
                        ps.setDouble(3, y);
                        ps.setDouble(4, z);
                        ps.setInt(5, id);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[HologramDAO] updateHologramPosition failed: " + e.getMessage());
                return false;
            }
        });
    }
}