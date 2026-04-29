package com.thenerdcj.island;

import org.bukkit.permissions.Permission;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Highly optimized rank enum for Folia Skyblock.
 * Uses EnumSet + EnumMap for O(1) permission checks.
 */
public enum IslandRank {

    OWNER("Owner", "§6[Owner]"),
    MODERATOR("Moderator", "§b[Mod]"),
    HELPER("Helper", "§a[Helper]"),
    GUEST("Guest", "§7[Guest]");

    private final String displayName;
    private final String prefix;

    // Pre-computed permission sets for lightning-fast checks
    private static final EnumMap<IslandRank, Set<IslandPermission>> PERMISSIONS = new EnumMap<>(IslandRank.class);

    static {
        // OWNER - full access
        PERMISSIONS.put(OWNER, EnumSet.allOf(IslandPermission.class));

        // MODERATOR - almost everything except ownership transfer
        PERMISSIONS.put(MODERATOR, EnumSet.complementOf(
                EnumSet.of(IslandPermission.TRANSFER_OWNERSHIP)));

        // HELPER - limited management
        PERMISSIONS.put(HELPER, EnumSet.of(
                IslandPermission.BUILD,
                IslandPermission.BREAK,
                IslandPermission.INTERACT,
                IslandPermission.INVITE,
                IslandPermission.KICK_GUEST));

        // GUEST - basic access only
        PERMISSIONS.put(GUEST, EnumSet.of(
                IslandPermission.BUILD,
                IslandPermission.BREAK,
                IslandPermission.INTERACT));
    }

    IslandRank(String displayName, String prefix) {
        this.displayName = displayName;
        this.prefix = prefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Ultra-fast permission check (O(1) via EnumSet)
     */
    public boolean hasPermission(IslandPermission permission) {
        return PERMISSIONS.get(this).contains(permission);
    }

    /**
     * Get all permissions for this rank (returns immutable view)
     */
    public Set<IslandPermission> getPermissions() {
        return EnumSet.copyOf(PERMISSIONS.get(this));
    }

    /**
     * Check if this rank can promote/demote another rank
     */
    public boolean canManageRank(IslandRank targetRank) {
        return this.ordinal() < targetRank.ordinal(); // Higher rank = lower ordinal
    }

    /**
     * Folia-safe: returns a lightweight permission node string
     */
    public String getPermissionNode() {
        return "foliaskyblock.rank." + this.name().toLowerCase();
    }
}