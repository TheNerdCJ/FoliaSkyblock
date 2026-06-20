package com.thenerdcj.rank;

import java.util.*;

/**
 * RankData - Stores configurable rank information loaded from ranks.yml
 * Fully dynamic - works with any rank defined in the config file (LuckPerms-style)
 */
public class RankData {

    private final String rankId;
    private final String displayName;
    private final String prefix;
    private final String suffix;
    private final List<String> permissions;
    private final int priority;
    private final boolean isStaff;
    private final boolean isDonor;
    private final String parent;
    private final boolean isDefault;

    public RankData(String rankId, String displayName, String prefix, String suffix,
                    List<String> permissions, int priority, boolean isStaff,
                    boolean isDonor, String parent, boolean isDefault) {
        this.rankId = rankId;
        this.displayName = displayName;
        this.prefix = prefix != null ? prefix : "&7[" + rankId + "]";
        this.suffix = suffix != null ? suffix : "";
        this.permissions = permissions != null ? new ArrayList<>(permissions) : new ArrayList<>();
        this.priority = priority;
        this.isStaff = isStaff;
        this.isDonor = isDonor;
        this.parent = parent;
        this.isDefault = isDefault;

        // Auto-grant foliasb.staff to any rank marked staff: true in ranks.yml.
        // This ensures helper/mod/etc can use staff features like /reports /bug reports, /staff, etc.
        if (isStaff && !this.permissions.contains("foliasb.staff")) {
            this.permissions.add("foliasb.staff");
        }
    }

    // Getters
    public String getRankId() { return rankId; }
    public String getDisplayName() { return displayName; }
    public String getPrefix() { return prefix; }
    public String getSuffix() { return suffix; }
    public List<String> getPermissions() { return permissions; }
    public int getPriority() { return priority; }
    public boolean isStaff() { return isStaff; }
    public boolean isDonor() { return isDonor; }
    public String getParent() { return parent; }
    public boolean isDefault() { return isDefault; }

    /**
     * Check if this rank has a specific permission
     * Supports wildcards: "folia.*" matches "folia.island.create"
     */
    public boolean hasPermission(String permission) {
        if (permission == null) return false;

        // Direct match or global wildcard
        if (permissions.contains("*") || permissions.contains(permission)) {
            return true;
        }

        // Check wildcard permissions (e.g., "folia.*")
        for (String perm : permissions) {
            if (perm.endsWith(".*")) {
                String base = perm.substring(0, perm.length() - 1);
                if (permission.startsWith(base)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if this rank inherits from another rank
     */
    public boolean inheritsFrom(String rankId) {
        return parent != null && parent.equalsIgnoreCase(rankId);
    }

    @Override
    public String toString() {
        return "RankData{" +
                "rankId='" + rankId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", prefix='" + prefix + '\'' +
                ", priority=" + priority +
                ", isStaff=" + isStaff +
                ", isDonor=" + isDonor +
                ", isDefault=" + isDefault +
                '}';
    }
}