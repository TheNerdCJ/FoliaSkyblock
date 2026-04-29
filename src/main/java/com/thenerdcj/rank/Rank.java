package com.thenerdcj.rank;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a single rank loaded from ranks.yml
 * Works for both Staff and Donor ranks
 */
public class Rank {

    private final String id;                    // e.g. "helper", "legend", "admin"
    private final String prefix;
    private final int priority;
    private final Set<String> permissions;

    public Rank(String id, ConfigurationSection section) {
        this.id = id.toLowerCase();
        this.prefix = section.getString("prefix", "§7" + id);
        this.priority = section.getInt("priority", 0);
        this.permissions = new HashSet<>(section.getStringList("permissions"));
    }

    public String getId() {
        return id;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getPriority() {
        return priority;
    }

    public Set<String> getPermissions() {
        return new HashSet<>(permissions); // Return copy for safety
    }

    /**
     * Checks if this rank grants the specified permission.
     * Supports wildcard permissions like "foliaskyblock.command.*"
     */
    public boolean hasPermission(String permission) {
        if (permission == null) return false;

        // Direct match or full wildcard
        if (permissions.contains("*") || permissions.contains(permission)) {
            return true;
        }

        // Wildcard support (e.g. "foliaskyblock.command.*")
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

    @Override
    public String toString() {
        return "Rank{" +
                "id='" + id + '\'' +
                ", prefix='" + prefix + '\'' +
                ", priority=" + priority +
                '}';
    }
}