package com.thenerdcj.boss;

import org.bukkit.Material;

/**
 * Reward for unlocking an achievement
 *
 * This class is PUBLIC to allow access from GUI classes in other packages
 */
public class AchievementReward {

    private final Material material;
    private final int amount;

    public AchievementReward(Material material, int amount) {
        this.material = material;
        this.amount = amount;
    }

    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }

    @Override
    public String toString() {
        return amount + "x " + material.name();
    }
}