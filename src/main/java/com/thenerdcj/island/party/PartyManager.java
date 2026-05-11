package com.thenerdcj.island.party;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.IslandRank;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PartyManager - Handles party invites, limits, and ranks for island parties.
 * Supports balanced XP (via divisor in IslandManager) for fair single vs multiplayer progression.
 * Play to Win: No pay-to-win party advantages, max size from config/upgrades.
 */
public class PartyManager {

    private final FoliaSkyblock plugin;
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();

    // Configurable via config.yml or defaults (Play to Win balanced)
    private int maxPartySize = 4; // default, can be upgraded via IslandUpgradeManager
    private int inviteTimeoutSeconds = 300; // 5 minutes
    private IslandRank defaultInviteRank = IslandRank.GUEST;

    public PartyManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.maxPartySize = config.getInt("party.max-size", 4);
        this.inviteTimeoutSeconds = config.getInt("party.invite-timeout-seconds", 300);
        // default rank from config if needed
    }

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public int getInviteTimeoutSeconds() {
        return inviteTimeoutSeconds;
    }

    public IslandRank getDefaultInviteRank() {
        return defaultInviteRank;
    }

    public void addPendingInvite(UUID inviter, UUID target, com.thenerdcj.island.Island island) {
        pendingInvites.put(target, new PendingInvite(inviter, island, System.currentTimeMillis()));
    }

    public PendingInvite getPendingInvite(UUID target) {
        PendingInvite invite = pendingInvites.get(target);
        if (invite != null && System.currentTimeMillis() - invite.timestamp > inviteTimeoutSeconds * 1000L) {
            pendingInvites.remove(target);
            return null;
        }
        return invite;
    }

    public void removePendingInvite(UUID target) {
        pendingInvites.remove(target);
    }

    public boolean hasPendingInvite(UUID target) {
        PendingInvite invite = pendingInvites.get(target);
        if (invite == null) return false;
        if (System.currentTimeMillis() - invite.timestamp > inviteTimeoutSeconds * 1000L) {
            pendingInvites.remove(target);
            return false;
        }
        return true;
    }

    public static class PendingInvite {
        public final UUID inviterUuid;
        public final com.thenerdcj.island.Island island;
        public final long timestamp;

        public PendingInvite(UUID inviterUuid, com.thenerdcj.island.Island island, long timestamp) {
            this.inviterUuid = inviterUuid;
            this.island = island;
            this.timestamp = timestamp;
        }

        public UUID getInviter() {
            return inviterUuid;
        }

        public com.thenerdcj.island.Island getIsland() {
            return island;
        }

        public World.Environment getDimension() {
            return island != null ? island.getDimension() : null;
        }
    }
}
