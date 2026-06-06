package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.BugReport;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
/**
 * Manager for the bug reporting system.
 * - Rate limiting / cooldowns per player (configurable).
 * - Submission wrapper with validation.
 * - Staff notifications on new reports (actionbar + optional chat to online staff).
 * - Helpers to resolve reports.
 *
 * Play-to-Win friendly: no power from reporting; purely for quality/support.
 */
public class BugReportManager {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Long> lastReportTime = new ConcurrentHashMap<>();

    public BugReportManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public int getCooldownMinutes() {
        return plugin.getConfig().getInt("reports.cooldown-minutes", 5);
    }

    public int getMaxDescriptionLength() {
        return plugin.getConfig().getInt("reports.max-description-length", 500);
    }

    public boolean isStaffNotifyEnabled() {
        return plugin.getConfig().getBoolean("reports.staff-notify", true);
    }

    public boolean canSubmit(Player player) {
        if (player.hasPermission("foliasb.admin") || player.hasPermission("foliasb.staff")) {
            return true; // staff bypass cooldown for testing/important reports
        }
        long cooldownMs = getCooldownMinutes() * 60L * 1000L;
        Long last = lastReportTime.get(player.getUniqueId());
        if (last == null) return true;
        return (System.currentTimeMillis() - last) >= cooldownMs;
    }

    public long getRemainingCooldownMillis(Player player) {
        long cooldownMs = getCooldownMinutes() * 60L * 1000L;
        Long last = lastReportTime.get(player.getUniqueId());
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, cooldownMs - elapsed);
    }

    /** Main entry for submitting a report from command/GUI. */
    public CompletableFuture<Integer> submitReport(Player reporter, BugReport.Category category, String description) {
        if (reporter == null || description == null || description.trim().isEmpty()) {
            return CompletableFuture.completedFuture(-1);
        }

        String desc = description.trim();
        int maxLen = getMaxDescriptionLength();
        if (desc.length() > maxLen) {
            desc = desc.substring(0, maxLen);
        }
        final String finalDesc = desc;
        if (finalDesc.length() < 10) {
            MessageUtil.sendMessage(reporter, "§cReport description is too short (min 10 chars).");
            return CompletableFuture.completedFuture(-1);
        }

        if (!canSubmit(reporter)) {
            long remain = getRemainingCooldownMillis(reporter);
            long mins = remain / (60 * 1000);
            MessageUtil.sendMessage(reporter, "§cYou must wait " + Math.max(1, mins) + " minute(s) before submitting another bug report.");
            return CompletableFuture.completedFuture(-1);
        }

        String name = reporter.getName();
        BugReport report = BugReport.createNew(reporter.getUniqueId(), name, category, finalDesc);

        lastReportTime.put(reporter.getUniqueId(), System.currentTimeMillis());

        // Capture effectively final copies for lambdas
        final Player reporterRef = reporter;
        final BugReport.Category catRef = category;

        return plugin.getDatabaseManager().submitBugReport(report).thenApply(id -> {
            if (id != null && id > 0) {
                // Success feedback to reporter (main thread safe via caller or chain)
                MessageUtil.sendMessage(reporterRef, "§aBug report #" + id + " submitted. Thank you! Staff have been notified.");
                SoundUtil.success(reporterRef);

                // Append to the single readable bug_reports.md file (for Grok Build analysis / plugin repairs)
                // This happens only after cooldown + validation passed.
                appendToBugReportsFile(id, reporterRef.getName(), reporterRef.getUniqueId(), catRef, finalDesc, null);

                // Notify staff (async result -> main for player iteration)
                final int reportId = id;
                plugin.getThreadSafety().runOnMainThread(() -> {
                    notifyStaffOfNewReport(reportId, reporterRef, catRef, finalDesc);
                });
            } else {
                MessageUtil.sendMessage(reporterRef, "§cFailed to submit report (database error). Please try again later or contact staff.");
            }
            return id;
        });
    }

    private void notifyStaffOfNewReport(int reportId, Player reporter, BugReport.Category category, String shortDesc) {
        if (!isStaffNotifyEnabled()) return;

        String preview = shortDesc.length() > 60 ? shortDesc.substring(0, 57) + "..." : shortDesc;
        String msg = "§6[BugReport #" + reportId + "] §e" + reporter.getName() + " §7(" + category.getColor() + category.getDisplayName() + "§7): §f" + preview +
                " §7Use §6/isadmin reports §7to view.";

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("foliasb.staff") || staff.hasPermission("foliasb.admin")) {
                MessageUtil.sendMessage(staff, msg);
                // Subtle sound for staff awareness (non-intrusive)
                SoundUtil.click(staff);
                // Action bar for less spam
                staff.sendActionBar(MessageUtil.legacy("§6New bug report #" + reportId + " from " + reporter.getName()));
            }
        }
        plugin.getLogger().info("[BugReport] #" + reportId + " submitted by " + reporter.getName() + " (" + category + "): " + preview);
    }

    /** Resolve / update a report (called from GUI or command). */
    public CompletableFuture<Boolean> resolveReport(int reportId, BugReport.Status newStatus, Player staff, String notes) {
        UUID staffUuid = (staff != null) ? staff.getUniqueId() : null;
        String noteText = (notes == null ? "" : notes.trim());

        return plugin.getDatabaseManager().updateBugReportStatus(reportId, newStatus, staffUuid, noteText)
            .thenApply(success -> {
                if (success && staff != null) {
                    plugin.getThreadSafety().runOnMainThread(() -> {
                        MessageUtil.sendMessage(staff, "§aUpdated report #" + reportId + " to " + newStatus.getColor() + newStatus.getDisplayName());
                        SoundUtil.success(staff);
                    });

                    // Append status update to the bug_reports.md file (keeps full history readable for Grok)
                    String update = "Status changed to " + newStatus.name() + " by " + staff.getName() +
                            (noteText.isEmpty() ? "" : " | " + noteText);
                    // Use a lightweight call (null category means update entry)
                    appendToBugReportsFile(reportId, staff.getName(), staff.getUniqueId(), null, "", update);
                }
                return success;
            });
    }

    public CompletableFuture<BugReport> getReport(int id) {
        return plugin.getDatabaseManager().getBugReportById(id);
    }

    public CompletableFuture<java.util.List<BugReport>> getOpenReports(int limit) {
        return plugin.getDatabaseManager().getOpenBugReports(limit);
    }

    public CompletableFuture<Integer> getOpenCount() {
        return plugin.getDatabaseManager().countOpenBugReports();
    }

    /** For admin inspect integration (future). */
    public CompletableFuture<java.util.List<BugReport>> getReportsForPlayer(UUID uuid, int limit) {
        return plugin.getDatabaseManager().getBugReportsForPlayer(uuid, limit);
    }

    /**
     * Appends a human-readable Markdown entry to plugins/FoliaSkyblock/bug_reports.md .
     * This single file is designed to be easily readable/pastable into Grok Build (or other LLMs)
     * for further analysis and plugin repairs. Each submission and status change adds an entry.
     * Spam protection is enforced upstream in canSubmit (cooldown) before reaching here.
     */
    private void appendToBugReportsFile(int reportId, String reporterName, UUID reporterUuid,
                                        BugReport.Category category, String description, String statusUpdate) {
        if (reportId <= 0) return;
        try {
            Path dataDir = plugin.getDataFolder().toPath();
            Path logFile = dataDir.resolve("bug_reports.md");

            if (!Files.exists(logFile)) {
                Files.createDirectories(dataDir);
                String header = "# FoliaSkyblock Bug Reports Log\n\n" +
                    "This file is automatically appended to whenever players submit bug reports (via /bug or aliases).\n" +
                    "It is intentionally in a clean, single-file Markdown format that is easy for Grok Build / AI tools to read and analyze for diagnosing and repairing the plugin.\n\n" +
                    "**Spam Protection:** Per-player cooldown (configurable in config.yml under `reports.cooldown-minutes`, default 5 minutes). " +
                    "Staff with foliasb.admin or foliasb.staff bypass the cooldown. Short descriptions (<10 chars) and length caps are also enforced.\n\n" +
                    "Reports are also stored in the database for in-game GUI triage (/isadmin reports).\n\n";
                Files.writeString(logFile, header, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }

            String time = Instant.now().toString();
            StringBuilder sb = new StringBuilder();
            sb.append("\n---\n\n");
            sb.append("## Report #").append(reportId).append("\n\n");
            sb.append("- **Time:** ").append(time).append("\n");
            sb.append("- **Reporter:** ").append(reporterName).append(" (").append(reporterUuid != null ? reporterUuid.toString() : "system").append(")\n");
            if (category != null) {
                sb.append("- **Category:** ").append(category.name()).append("\n");
            }
            if (statusUpdate != null && !statusUpdate.isEmpty()) {
                sb.append("- **Update:** ").append(statusUpdate).append("\n");
            }
            if (description != null && !description.trim().isEmpty()) {
                sb.append("\n**Description:**\n\n");
                sb.append(description).append("\n\n");
            }
            sb.append("---\n");

            Files.writeString(logFile, sb.toString(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);

        } catch (Exception e) {
            plugin.getLogger().warning("[BugReportManager] Failed to append to bug_reports.md (non-fatal): " + e.getMessage());
        }
    }

    // ==================== PENDING STAFF NOTES (for GUI "Add Note" + /bug note flow) ====================

    private final java.util.Map<Integer, String> pendingNotes = new java.util.concurrent.ConcurrentHashMap<>();

    /** Called by the command after staff clicks "Add Note" in the GUI and then runs /bug note <text>. */
    public void setPendingNote(int reportId, String note) {
        if (note != null && !note.trim().isEmpty()) {
            pendingNotes.put(reportId, note.trim());
        }
    }

    /** Consumes (and returns) a pending note when a staff member resolves the report. */
    public String consumePendingNote(int reportId) {
        return pendingNotes.remove(reportId);
    }

    public boolean hasPendingNote(int reportId) {
        return pendingNotes.containsKey(reportId);
    }

    // ==================== STAFF SELECTION TRACKING (for /bug note convenience) ====================

    private final java.util.Map<UUID, Integer> lastSelectedReportByStaff = new java.util.concurrent.ConcurrentHashMap<>();

    public void recordStaffReportSelection(UUID staffUuid, int reportId) {
        if (staffUuid != null && reportId > 0) {
            lastSelectedReportByStaff.put(staffUuid, reportId);
        }
    }

    public Integer getLastSelectedReport(UUID staffUuid) {
        return lastSelectedReportByStaff.get(staffUuid);
    }
}
