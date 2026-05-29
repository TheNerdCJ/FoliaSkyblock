package com.thenerdcj.island;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a player purchases an island upgrade.
 *
 * This event is fired *before* the upgrade is applied and persisted.
 * Plugins can listen to this to add custom requirements, modify cost,
 * or cancel the upgrade.
 *
 * Inspired by upgrade events in SuperiorSkyblock2 and IridiumSkyblock.
 */
public class IslandUpgradeEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final Island island;
    private final IslandUpgrade upgrade;
    private final int oldLevel;
    private final int newLevel;

    private boolean cancelled = false;
    private double costMultiplier = 1.0; // Allow plugins to modify effective cost

    public IslandUpgradeEvent(Player player, Island island, IslandUpgrade upgrade, int oldLevel, int newLevel) {
        this.player = player;
        this.island = island;
        this.upgrade = upgrade;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() {
        return player;
    }

    public Island getIsland() {
        return island;
    }

    public IslandUpgrade getUpgrade() {
        return upgrade;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    /**
     * Allows other plugins to make the upgrade more or less expensive.
     */
    public double getCostMultiplier() {
        return costMultiplier;
    }

    public void setCostMultiplier(double costMultiplier) {
        this.costMultiplier = Math.max(0.1, costMultiplier);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
