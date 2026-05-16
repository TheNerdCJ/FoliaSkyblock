package com.thenerdcj.island;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class IslandSkillLevelUpEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Island island;
    private final Island.Skill skill;
    private final int newLevel;

    public IslandSkillLevelUpEvent(Island island, Island.Skill skill, int newLevel) {
        this.island = island;
        this.skill = skill;
        this.newLevel = newLevel;
    }

    public Island getIsland() {
        return island;
    }

    public Island.Skill getSkill() {
        return skill;
    }

    public int getNewLevel() {
        return newLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
