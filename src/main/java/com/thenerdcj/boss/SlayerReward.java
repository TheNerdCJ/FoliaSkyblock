package com.thenerdcj.boss;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Represents a reward from slayer quests with drop chance.
 * EXPANDED: Now supports special string rewards (CRATE_*, BOOSTER_*, PRESTIGE_*)
 */
public class SlayerReward {

    private final Material material;
    private final int amount;
    private final double dropChance;
    private final boolean isSpecial;
    private final String specialReward; // e.g. "CRATE_EPIC", "BOOSTER_CROP_60", "PRESTIGE_25"

    // Legacy constructor (vanilla items)
    public SlayerReward(Material material, int amount, double dropChance) {
        this(material, amount, dropChance, null);
    }

    // New constructor for modern rewards (crates, boosters, prestige)
    public SlayerReward(String specialReward, int amount, double dropChance) {
        this(null, amount, dropChance, specialReward);
    }

    private SlayerReward(Material material, int amount, double dropChance, String specialReward) {
        this.material = material;
        this.amount = amount;
        this.dropChance = Math.min(1.0, Math.max(0.0, dropChance));
        this.specialReward = specialReward;
        this.isSpecial = dropChance < 0.35 || specialReward != null;
    }

    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }
    public double getDropChance() { return dropChance; }
    public boolean isSpecial() { return isSpecial; }
    public String getSpecialReward() { return specialReward; }
    public boolean isSpecialReward() { return specialReward != null; }

    /**
     * Roll for this reward based on drop chance.
     * Returns ItemStack for vanilla items, or null for special rewards (handled separately).
     */
    public ItemStack rollReward() {
        if (isSpecialReward()) return null;
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