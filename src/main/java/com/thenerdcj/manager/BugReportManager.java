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
}
