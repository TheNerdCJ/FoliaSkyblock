package com.thenerdcj.boss;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Represents a reward from slayer quests with drop chance
 */
public class SlayerReward {

    private final Material material;
    private final int amount;
    private final double dropChance; // 0.0 to 1.0
    private final boolean isSpecial; // Special drops have rarity indicators

    public SlayerReward(Material material, int amount, double dropChance) {
        this.material = material;
        this.amount = amount;
        this.dropChance = Math.min(1.0, Math.max(0.0, dropChance));
        this.isSpecial = dropChance < 0.3; // Low chance = special/rare
    }

    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }
    public double getDropChance() { return dropChance; }
    public boolean isSpecial() { return isSpecial; }

    /**
     * Roll for this reward based on drop chance
     */
    public ItemStack rollReward() {
        if (new Random().nextDouble() <= dropChance) {
            return new ItemStack(material, amount);
        }
        return null;
    }

    /**
     * Get the rarity color for display
     */
    public String getRarityColor() {
        if (dropChance >= 0.7) return "§f"; // Common - White
        if (dropChance >= 0.4) return "§a"; // Uncommon - Green
        if (dropChance >= 0.2) return "§9"; // Rare - Blue
        if (dropChance >= 0.1) return "§5"; // Epic - Purple
        return "§6"; // Legendary - Gold
    }

    /**
     * Get the rarity name
     */
    public String getRarityName() {
        if (dropChance >= 0.7) return "Common";
        if (dropChance >= 0.4) return "Uncommon";
        if (dropChance >= 0.2) return "Rare";
        if (dropChance >= 0.1) return "Epic";
        return "Legendary";
    }
}