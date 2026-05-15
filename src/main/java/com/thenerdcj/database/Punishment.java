package com.thenerdcj.database;

import java.util.UUID;

/**
 * Represents a logged punishment (ban, tempban, mute, warn, kick).
 * Used for staff history and active punishment checking.
 */
public class Punishment {

    public enum Type {
        BAN, TEMPBAN, KICK, MUTE, WARN
    }

    private final int id;
    private final UUID targetUuid;
    private final UUID staffUuid;
    private final Type type;
    private final String reason;
    private final long timestamp;
    private final long duration;      // 0 = permanent
    private final boolean active;

    public Punishment(int id, UUID targetUuid, UUID staffUuid, Type type,
                      String reason, long timestamp, long duration, boolean active) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.staffUuid = staffUuid;
        this.type = type;
        this.reason = reason;
        this.timestamp = timestamp;
        this.duration = duration;
        this.active = active;
    }

    // Getters
    public int getId() { return id; }
    public UUID getTargetUuid() { return targetUuid; }
    public UUID getStaffUuid() { return staffUuid; }
    public Type getType() { return type; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }
    public long getDuration() { return duration; }
    public boolean isActive() { return active; }

    public boolean isExpired() {
        if (duration <= 0) return false; // permanent
        return System.currentTimeMillis() > (timestamp + duration);
    }

    @Override
    public String toString() {
        return type + " by " + staffUuid + " - " + reason;
    }
}
