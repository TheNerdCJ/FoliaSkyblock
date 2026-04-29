package com.thenerdcj.island;

import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IslandParty {

    private final UUID ownerUuid;
    private final Map<UUID, IslandRank> members = new ConcurrentHashMap<>();

    public IslandParty(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        members.put(ownerUuid, IslandRank.OWNER);
    }

    // ====================== MEMBER MANAGEMENT ======================
    public boolean addMember(UUID memberUuid) {
        if (members.containsKey(memberUuid)) return false;
        members.put(memberUuid, IslandRank.GUEST);
        return true;
    }

    public boolean removeMember(UUID memberUuid) {
        if (memberUuid.equals(ownerUuid)) return false; // Can't remove owner
        return members.remove(memberUuid) != null;
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members.keySet());
    }

    public int getMemberCount() {
        return members.size();
    }

    // ====================== RANK MANAGEMENT ======================
    public boolean setRank(UUID setterUuid, UUID targetUuid, IslandRank newRank) {
        // Only owner or moderator can change ranks
        IslandRank setterRank = members.get(setterUuid);
        if (setterRank == null || (setterRank != IslandRank.OWNER && setterRank != IslandRank.MODERATOR)) {
            return false;
        }

        // Owner can't be demoted
        if (targetUuid.equals(ownerUuid) && newRank != IslandRank.OWNER) {
            return false;
        }

        // Moderators can't promote to owner
        if (setterRank == IslandRank.MODERATOR && newRank == IslandRank.OWNER) {
            return false;
        }

        members.put(targetUuid, newRank);
        return true;
    }

    public IslandRank getRank(UUID uuid) {
        return members.getOrDefault(uuid, IslandRank.GUEST);
    }

    public boolean hasRank(UUID uuid, IslandRank rank) {
        return getRank(uuid) == rank;
    }

    // ====================== OWNER MANAGEMENT ======================
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public boolean isOwner(UUID uuid) {
        return uuid.equals(ownerUuid);
    }

    /**
     * Transfers ownership to another member (owner only)
     */
    public boolean transferOwnership(UUID newOwnerUuid) {
        if (!members.containsKey(newOwnerUuid)) return false;
        if (newOwnerUuid.equals(ownerUuid)) return false;

        members.put(ownerUuid, IslandRank.MODERATOR); // Old owner becomes moderator
        members.put(newOwnerUuid, IslandRank.OWNER);
        return true;
    }

    // ====================== UTILITY ======================
    public List<UUID> getMembersByRank(IslandRank rank) {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, IslandRank> entry : members.entrySet()) {
            if (entry.getValue() == rank) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public String getInfo() {
        return "§6Party §7| §eMembers: " + getMemberCount() +
                " §7| §eOwner: " + Bukkit.getOfflinePlayer(ownerUuid).getName();
    }
}