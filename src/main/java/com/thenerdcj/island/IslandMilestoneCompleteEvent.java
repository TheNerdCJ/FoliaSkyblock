package com.thenerdcj.island;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class IslandMilestoneCompleteEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Island island;
    private final String milestoneId;
    private final double bonusXp;

    public IslandMilestoneCompleteEvent(Island island, String milestoneId, double bonusXp) {
        this.island = island;
        this.milestoneId = milestoneId;
        this.bonusXp = bonusXp;
    }

    public Island getIsland() {
        return island;
    }

    public String getMilestoneId() {
        return milestoneId;
    }

    public double getBonusXp() {
        return bonusXp;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}