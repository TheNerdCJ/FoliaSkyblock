package com.thenerdcj.crate;

import org.bukkit.Material;
import java.util.*;

/**
 * Defines different crate types for the new Crate/Key/Loot system.
 * Rewards are weighted and can include money, items, boosters, prestige points, etc.
 */
public enum CrateType {
    COMMON("Common Crate", Material.CHEST, 1.0, 5000),
    RARE("Rare Crate", Material.TRAPPED_CHEST, 3.0, 15000),
    EPIC("Epic Crate", Material.ENDER_CHEST, 6.0, 45000),
    LEGENDARY("Legendary Crate", Material.SHULKER_BOX, 12.0, 120000);

    private final String displayName;
    private final Material icon;
    private final double rarityWeight;
    private final int baseKeyPrice;

    CrateType(String displayName, Material icon, double rarityWeight, int baseKeyPrice) {
        this.displayName = displayName;
        this.icon = icon;
        this.rarityWeight = rarityWeight;
        this.baseKeyPrice = baseKeyPrice;
    }

    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public double getRarityWeight() { return rarityWeight; }
    public int getBaseKeyPrice() { return baseKeyPrice; }

    public static CrateType getRandom() {
        double total = Arrays.stream(values()).mapToDouble(CrateType::getRarityWeight).sum();
        double r = new Random().nextDouble() * total;
        for (CrateType type : values()) {
            r -= type.getRarityWeight();
            if (r <= 0) return type;
        }
        return COMMON;
    }
}