package com.thenerdcj.island;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * IslandRank - Ultra-efficient enum for Folia
 * Uses pre-computed EnumSet for O(1) permission checks
 */
public enum IslandRank {

    OWNER("Owner", EnumSet.allOf(IslandPermission.class)),
    MODERATOR("Moderator", EnumSet.of(
            IslandPermission.BUILD,
            IslandPermission.BREAK,
            IslandPermission.INTERACT,
            IslandPermission.INVITE,
            IslandPermission.KICK,
            IslandPermission.KICK_GUEST,
            IslandPermission.SET_RANK,
            IslandPermission.USE_TRADES,
            IslandPermission.VIEW_TOP
    )),
    HELPER("Helper", EnumSet.of(
            IslandPermission.BUILD,
            IslandPermission.BREAK,
            IslandPermission.INTERACT,
            IslandPermission.INVITE,
            IslandPermission.USE_TRADES,
            IslandPermission.VIEW_TOP
    )),
    GUEST("Guest", EnumSet.of(
            IslandPermission.BUILD,
            IslandPermission.BREAK,
            IslandPermission.INTERACT,
            IslandPermission.USE_TRADES
    ));

    private final String displayName;
    private final Set<IslandPermission> permissions;

    // Pre-computed permission map for maximum speed
    private static final Map<IslandRank, Set<IslandPermission>> RANK_PERMISSIONS = new EnumMap<>(IslandRank.class);

    static {
        for (IslandRank rank : values()) {
            RANK_PERMISSIONS.put(rank, rank.permissions);
        }
    }

    IslandRank(String displayName, Set<IslandPermission> permissions) {
        this.displayName = displayName;
        this.permissions = permissions;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean hasPermission(IslandPermission permission) {
        return RANK_PERMISSIONS.get(this).contains(permission);
    }

    public Set<IslandPermission> getPermissions() {
        return RANK_PERMISSIONS.get(this);
    }

    /**
     * Get next higher rank (for promotion)
     */
    public IslandRank getNextRank() {
        IslandRank[] ranks = values();
        int index = ordinal();
        return index < ranks.length - 1 ? ranks[index + 1] : this;
    }

    /**
     * Get previous lower rank (for demotion)
     */
    public IslandRank getPreviousRank() {
        IslandRank[] ranks = values();
        int index = ordinal();
        return index > 0 ? ranks[index - 1] : this;
    }
}