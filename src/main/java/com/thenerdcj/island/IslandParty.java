package com.thenerdcj.island;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IslandParty {

    private final UUID ownerUuid;
    private final Map<UUID, IslandRank> members = new ConcurrentHashMap<>();

    public IslandParty(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        members.put(ownerUuid, IslandRank.OWNER);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public boolean isOwner(UUID uuid) {
        return ownerUuid.equals(uuid);
    }

    public boolean addMember(UUID uuid) {
        if (members.containsKey(uuid)) return false;
        members.put(uuid, IslandRank.GUEST);
        return true;
    }

    public boolean removeMember(UUID uuid) {
        if (ownerUuid.equals(uuid)) return false;
        return members.remove(uuid) != null;
    }

    public boolean setRank(UUID setterUuid, UUID targetUuid, IslandRank newRank) {
        if (!isOwner(setterUuid)) {
            IslandRank setterRank = getRank(setterUuid);
            IslandRank targetRank = getRank(targetUuid);
            if (setterRank.getPriority() <= targetRank.getPriority()) {
                return false;
            }
        }
        if (ownerUuid.equals(targetUuid)) return false;

        members.put(targetUuid, newRank);
        return true;
    }

    public IslandRank getRank(UUID uuid) {
        return members.getOrDefault(uuid, IslandRank.GUEST);
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members.keySet());
    }

    public boolean hasPermission(UUID uuid, IslandPermission permission) {
        if (isOwner(uuid)) return true;
        IslandRank rank = getRank(uuid);
        return rank.hasPermission(permission);
    }

    public String getPartyInfo() {
        StringBuilder sb = new StringBuilder("§6Island Party Members:\n");
        members.forEach((uuid, rank) -> {
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : "Offline";
            sb.append("§e").append(name).append(" §7- §f").append(rank.name()).append("\n");
        });
        return sb.toString();
    }
}