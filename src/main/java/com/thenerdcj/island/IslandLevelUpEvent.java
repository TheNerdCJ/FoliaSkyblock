package com.thenerdcj.island;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired when an island levels up
 */
public class IslandLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Island island;
    private final int oldLevel;
    private final int newLevel;
    private final UUID playerUuid;

    public IslandLevelUpEvent(Island island, int oldLevel, int newLevel, UUID playerUuid) {
        super(!Bukkit.isPrimaryThread()); // Async if not on main thread
        this.island = island;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.playerUuid = playerUuid;
    }

    public Island getIsland() {
        return island;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public Player getPlayer() {
        return playerUuid != null ? Bukkit.getPlayer(playerUuid) : null;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}