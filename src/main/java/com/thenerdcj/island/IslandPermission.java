package com.thenerdcj.island;

/**
 * Ultra-lightweight permission enum optimized for Folia.
 * All checks are O(1) via EnumSet in IslandRank.
 */
public enum IslandPermission {

    // Core building
    BUILD("Build on island"),
    BREAK("Break blocks on island"),
    INTERACT("Use blocks, chests, etc."),

    // Party management
    INVITE("Invite players to island"),
    KICK("Kick members from island"),
    KICK_GUEST("Kick only guests"),
    SET_RANK("Change member ranks"),
    TRANSFER_OWNERSHIP("Transfer island ownership"),

    // Advanced
    ACCESS_NETHER("Access nether island"),
    ACCESS_END("Access end island"),
    USE_TRADES("Use island trade system"),
    VIEW_TOP("View top islands leaderboard");

    private final String description;

    IslandPermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Folia-safe permission node
     */
    public String getNode() {
        return "foliaskyblock.island." + this.name().toLowerCase();
    }
}