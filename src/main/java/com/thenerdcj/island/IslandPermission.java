package com.thenerdcj.island;

/**
 * IslandPermission - Clean enum for all island permissions
 */
public enum IslandPermission {

    // Building & World Interaction
    BUILD("Build on the island"),
    BREAK("Break blocks on the island"),
    INTERACT("Interact with blocks, entities, and containers"),
    USE_TRADES("Use the level-gated trade system"),

    // Party Management
    INVITE("Invite players to the island"),
    KICK("Kick any non-owner player"),
    KICK_GUEST("Only kick guests"),
    SET_RANK("Change member ranks"),
    MANAGE_MEMBERS("Full member management"),

    // Information & Leaderboards
    VIEW_TOP("View island leaderboards"),
    VIEW_MEMBERS("View island member list"),

    // Teleport & Movement
    TELEPORT_HOME("Teleport to island home"),
    TELEPORT_MEMBERS("Teleport to other members"),
    VISIT("Visit as a guest when visitors are allowed"),

    // Advanced
    CHANGE_BIOME("Change island biome"),
    UPGRADE_LEVEL("Upgrade island level"),
    ACCESS_ENDER_CHEST("Access ender chest on island"),
    USE_REDSTONE("Use redstone devices");

    private final String description;

    IslandPermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}