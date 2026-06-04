package com.thenerdcj.database;

import java.util.UUID;

/**
 * Model for player-submitted bug reports, exploits, and suggestions.
 * Persisted via BugReportDAO. Used by BugReportManager, BugReportListGUI, and staff tools.
 *
 * Categories distinguish real bugs from security issues (exploits) vs feedback.
 * Status workflow supports staff triage.
 */
public class BugReport {

    public enum Category {
        BUG("Bug", "§c"),
        EXPLOIT("Exploit / Security", "§4"),
        SUGGESTION("Suggestion / Feedback", "§a"),
        OTHER("Other", "§7");

        private final String displayName;
        private final String color;

        Category(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }

        public static Category fromString(String s) {
            if (s == null) return OTHER;
            String lower = s.toLowerCase();
            if (lower.contains("exploit") || lower.contains("security") || lower.contains("dupe") || lower.contains("hack")) return EXPLOIT;
            if (lower.contains("suggestion") || lower.contains("feedback") || lower.contains("idea")) return SUGGESTION;
            if (lower.contains("bug") || lower.contains("issue") || lower.contains("broken") || lower.contains("not work")) return BUG;
            return OTHER;
        }
    }

    public enum Status {
        OPEN("Open", "§e"),
        INVESTIGATING("Investigating", "§6"),
        FIXED("Fixed", "§a"),
        DUPLICATE("Duplicate", "§7"),
        WONTFIX("Won't Fix", "§c"),
        RESOLVED("Resolved", "§2");

        private final String displayName;
        private final String color;

        Status(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }

        public static Status fromString(String s) {
            if (s == null) return OPEN;
            try {
                return Status.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return OPEN;
            }
        }
    }

    private final int id;
    private final UUID reporterUuid;
    private final String reporterName;
    private final Category category;
    private final String description;
    private Status status;
    private final long createdAt;
    private Long resolvedAt;
    private UUID resolvedByUuid;
    private String staffNotes;

    public BugReport(int id, UUID reporterUuid, String reporterName, Category category,
                     String description, Status status, long createdAt,
                     Long resolvedAt, UUID resolvedByUuid, String staffNotes) {
        this.id = id;
        this.reporterUuid = reporterUuid;
        this.reporterName = reporterName != null ? reporterName : "Unknown";
        this.category = category != null ? category : Category.OTHER;
        this.description = description != null ? description : "";
        this.status = status != null ? status : Status.OPEN;
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.resolvedAt = resolvedAt;
        this.resolvedByUuid = resolvedByUuid;
        this.staffNotes = staffNotes;
    }

    /** Factory for new submissions (before DB insert). */
    public static BugReport createNew(UUID reporterUuid, String reporterName, Category category, String description) {
        return new BugReport(
            -1, // id assigned by DB
            reporterUuid,
            reporterName,
            category != null ? category : Category.fromString(description),
            description,
            Status.OPEN,
            System.currentTimeMillis(),
            null, null, null
        );
    }

    public int getId() { return id; }
    public UUID getReporterUuid() { return reporterUuid; }
    public String getReporterName() { return reporterName; }
    public Category getCategory() { return category; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public Long getResolvedAt() { return resolvedAt; }
    public UUID getResolvedByUuid() { return resolvedByUuid; }
    public String getStaffNotes() { return staffNotes; }

    public void setStatus(Status status) { this.status = status; }
    public void setResolvedAt(Long resolvedAt) { this.resolvedAt = resolvedAt; }
    public void setResolvedByUuid(UUID resolvedByUuid) { this.resolvedByUuid = resolvedByUuid; }
    public void setStaffNotes(String staffNotes) { this.staffNotes = staffNotes; }

    public boolean isOpen() {
        return status == Status.OPEN || status == Status.INVESTIGATING;
    }

    public String getShortDescription(int maxLen) {
        if (description.length() <= maxLen) return description;
        return description.substring(0, maxLen - 3) + "...";
    }

    public String getFormattedCreated() {
        // Simple relative or absolute; callers can format further
        long ageMs = System.currentTimeMillis() - createdAt;
        long mins = ageMs / (60 * 1000);
        if (mins < 60) return mins + "m ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }
}
