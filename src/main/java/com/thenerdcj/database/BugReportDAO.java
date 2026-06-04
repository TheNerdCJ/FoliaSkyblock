package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * DAO for bug_reports table.
 * Supports player submissions and staff triage (list open, update status + notes).
 * All operations are async via supplyAsync / runAsync.
 */
public class BugReportDAO extends BaseDAO {

    public BugReportDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Table is created centrally in DatabaseManager.initDatabase() + migrations.
    }

    /** Submit a new report. Returns the generated ID on success, or -1 on failure. */
    public CompletableFuture<Integer> submitReport(BugReport report) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    String sql = "INSERT INTO bug_reports (reporter_uuid, reporter_name, category, description, status, created_at, resolved_at, resolved_by_uuid, staff_notes) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, report.getReporterUuid().toString());
                        ps.setString(2, report.getReporterName());
                        ps.setString(3, report.getCategory().name());
                        ps.setString(4, report.getDescription());
                        ps.setString(5, report.getStatus().name());
                        ps.setLong(6, report.getCreatedAt());
                        if (report.getResolvedAt() != null) {
                            ps.setLong(7, report.getResolvedAt());
                        } else {
                            ps.setNull(7, java.sql.Types.BIGINT);
                        }
                        ps.setString(8, report.getResolvedByUuid() != null ? report.getResolvedByUuid().toString() : null);
                        ps.setString(9, report.getStaffNotes());
                        ps.executeUpdate();

                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) {
                                return keys.getInt(1);
                            }
                        }
                        return -1;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BugReportDAO] submitReport failed: " + e.getMessage());
                return -1;
            }
        });
    }

    /** Get most recent open/investigating reports (newest first), limited. */
    public CompletableFuture<List<BugReport>> getOpenReports(int limit) {
        return supplyAsync(() -> {
            List<BugReport> list = new ArrayList<>();
            try {
                return withConnection(conn -> {
                    String sql = "SELECT * FROM bug_reports WHERE status IN ('OPEN','INVESTIGATING') ORDER BY created_at DESC LIMIT ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, Math.max(1, limit));
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            list.add(mapRow(rs));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return list;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BugReportDAO] getOpenReports failed: " + e.getMessage());
                return list;
            }
        });
    }

    /** Paginated open reports. */
    public CompletableFuture<List<BugReport>> getOpenReports(int limit, int offset) {
        return supplyAsync(() -> {
            List<BugReport> list = new ArrayList<>();
            try {
                return withConnection(conn -> {
                    String sql = "SELECT * FROM bug_reports WHERE status IN ('OPEN','INVESTIGATING') ORDER BY created_at DESC LIMIT ? OFFSET ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, Math.max(1, limit));
                        ps.setInt(2, Math.max(0, offset));
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            list.add(mapRow(rs));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return list;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BugReportDAO] getOpenReports(paged) failed: " + e.getMessage());
                return list;
            }
        });
    }

    public CompletableFuture<BugReport> getReportById(int id) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM bug_reports WHERE id = ?")) {
                        ps.setInt(1, id);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            return mapRow(rs);
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BugReportDAO] getReportById failed: " + e.getMessage());
                return null;
            }
        });
    }

    /** Update status + optional resolver/note. Returns true on success. */
    public CompletableFuture<Boolean> updateStatus(int reportId, BugReport.Status newStatus, UUID resolvedBy, String notes) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    String sql = "UPDATE bug_reports SET status = ?, resolved_at = ?, resolved_by_uuid = ?, staff_notes = ? WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, newStatus.name());
                        if (newStatus == BugReport.Status.OPEN || newStatus == BugReport.Status.INVESTIGATING) {
                            ps.setNull(2, java.sql.Types.BIGINT);
                            ps.setString(3, null);
                        } else {
                            ps.setLong(2, System.currentTimeMillis());
                            ps.setString(3, resolvedBy != null ? resolvedBy.toString() : null);
                        }
                        ps.setString(4, notes);
                        ps.setInt(5, reportId);
                        return ps.executeUpdate() > 0;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BugReportDAO] updateStatus failed: " + e.getMessage());
                return false;
            }
        });
    }

    /** Get all reports submitted by a specific player (newest first). */
    public CompletableFuture<List<BugReport>> getReportsForPlayer(UUID playerUuid, int limit) {
        return supplyAsync(() -> {
            List<BugReport> list = new ArrayList<>();
            try {
                return withConnection(conn -> {
                    String sql = "SELECT * FROM bug_reports WHERE reporter_uuid = ? ORDER BY created_at DESC LIMIT ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setInt(2, Math.max(1, limit));
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            list.add(mapRow(rs));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return list;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BugReportDAO] getReportsForPlayer failed: " + e.getMessage());
                return list;
            }
        });
    }

    public CompletableFuture<Integer> countOpenReports() {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM bug_reports WHERE status IN ('OPEN','INVESTIGATING')")) {
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) return rs.getInt(1);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return 0;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[BugReportDAO] countOpenReports failed: " + e.getMessage());
                return 0;
            }
        });
    }

    private BugReport mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID reporter = UUID.fromString(rs.getString("reporter_uuid"));
        String reporterName = rs.getString("reporter_name");
        BugReport.Category cat = BugReport.Category.valueOf(rs.getString("category"));
        String desc = rs.getString("description");
        BugReport.Status status = BugReport.Status.fromString(rs.getString("status"));
        long created = rs.getLong("created_at");

        Long resolvedAt = null;
        if (rs.getObject("resolved_at") != null) {
            resolvedAt = rs.getLong("resolved_at");
        }
        UUID resolvedBy = null;
        String resolvedByStr = rs.getString("resolved_by_uuid");
        if (resolvedByStr != null) {
            try { resolvedBy = UUID.fromString(resolvedByStr); } catch (Exception ignored) {}
        }
        String notes = rs.getString("staff_notes");

        return new BugReport(id, reporter, reporterName, cat, desc, status, created, resolvedAt, resolvedBy, notes);
    }
}
