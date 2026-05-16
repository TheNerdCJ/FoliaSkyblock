package com.thenerdcj.database;

import java.util.UUID;

public class Punishment {

    public enum Type {
        BAN, TEMPBAN, MUTE, KICK, WARN
    }

    private final int id;
    private final UUID targetUuid;
    private final UUID staffUuid;
    private final Type type;
    private final String reason;
    private final long issuedAt;
    private final long durationMillis; // 0 for permanent
    private final boolean active;

    public Punishment(int id, UUID targetUuid, UUID staffUuid, Type type, String reason,
                      long issuedAt, long durationMillis, boolean active) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.staffUuid = staffUuid;
        this.type = type;
        this.reason = reason;
        this.issuedAt = issuedAt;
        this.durationMillis = durationMillis;
        this.active = active;
    }

    public int getId() { return id; }
    public UUID getTargetUuid() { return targetUuid; }
    public UUID getStaffUuid() { return staffUuid; }
    public Type getType() { return type; }
    public String getReason() { return reason; }
    public long getIssuedAt() { return issuedAt; }
    public long getDurationMillis() { return durationMillis; }
    public boolean isActive() { return active; }

    public boolean isExpired() {
        if (durationMillis <= 0) return false; // permanent
        return System.currentTimeMillis() > (issuedAt + durationMillis);
    }
}
