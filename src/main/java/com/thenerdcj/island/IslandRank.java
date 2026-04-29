package com.thenerdcj.island;

public enum IslandRank {

    OWNER(100),
    MODERATOR(80),
    HELPER(50),
    GUEST(10);

    private final int priority;

    IslandRank(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public boolean hasPermission(IslandPermission permission) {
        return switch (this) {
            case OWNER -> true;
            case MODERATOR -> permission != IslandPermission.ADMIN_ONLY;
            case HELPER -> permission == IslandPermission.INTERACT || permission == IslandPermission.CHEST || permission == IslandPermission.BUILD;
            case GUEST -> permission == IslandPermission.INTERACT;
        };
    }
}