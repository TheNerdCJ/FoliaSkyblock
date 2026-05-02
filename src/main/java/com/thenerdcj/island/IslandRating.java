package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;

import java.util.UUID;

/**
 * Island Rating - Player rating for an island
 */
public class IslandRating {

    private final GridPosition gridPosition;
    private final UUID playerUuid;
    private int rating; // 1-5 stars
    private long timestamp;

    public IslandRating(GridPosition gridPosition, UUID playerUuid, int rating) {
        this.gridPosition = gridPosition;
        this.playerUuid = playerUuid;
        this.rating = Math.max(1, Math.min(5, rating));
        this.timestamp = System.currentTimeMillis();
    }

    public GridPosition getGridPosition() { return gridPosition; }
    public UUID getPlayerUuid() { return playerUuid; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = Math.max(1, Math.min(5, rating)); }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}