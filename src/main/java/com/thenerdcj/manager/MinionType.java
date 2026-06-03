package com.thenerdcj.manager;

import org.bukkit.Material;

/**
 * Enum defining all available minion types in FoliaSkyblock.
 * Each type has a display name, icon, produced resource, and base production rate.
 */
public enum MinionType {
    WHEAT("Wheat", Material.WHEAT, Material.WHEAT, 1.0),
    COBBLESTONE("Cobblestone", Material.COBBLESTONE, Material.COBBLESTONE, 1.0),
    IRON("Iron", Material.IRON_INGOT, Material.IRON_INGOT, 0.8),
    GOLD("Gold", Material.GOLD_INGOT, Material.GOLD_INGOT, 0.7),
    DIAMOND("Diamond", Material.DIAMOND, Material.DIAMOND, 0.4),
    EMERALD("Emerald", Material.EMERALD, Material.EMERALD, 0.5),
    ENDER_PEARL("Ender Pearl", Material.ENDER_PEARL, Material.ENDER_PEARL, 0.6),
    SLIME("Slime", Material.SLIME_BALL, Material.SLIME_BALL, 0.9),
    SNOW("Snow", Material.SNOWBALL, Material.SNOWBALL, 1.2),
    CLAY("Clay", Material.CLAY_BALL, Material.CLAY, 0.85),
    FISHING("Fishing", Material.COD, Material.COD, 0.95),
    MOB("Mob", Material.ROTTEN_FLESH, Material.ROTTEN_FLESH, 0.75),
    // Expanded minions
    QUARTZ("Quartz", Material.QUARTZ, Material.QUARTZ, 0.65),
    BLAZE("Blaze", Material.BLAZE_ROD, Material.BLAZE_ROD, 0.45),
    WITHER_SKELETON("Wither Skeleton", Material.COAL, Material.COAL, 0.55), // drops coal + chance for skulls in future
    // Hypixel-aligned expansion (YT minion guides: more late-game + special)
    NETHERITE("Netherite", Material.NETHERITE_SCRAP, Material.NETHERITE_SCRAP, 0.25),
    GLOWSTONE("Glowstone", Material.GLOWSTONE_DUST, Material.GLOWSTONE, 0.7),
    GRAVEL("Gravel", Material.GRAVEL, Material.GRAVEL, 1.1),
    ACACIA("Acacia", Material.ACACIA_LOG, Material.ACACIA_LOG, 0.9),
    INFERNO("Inferno", Material.MAGMA_CREAM, Material.MAGMA_CREAM, 0.35), // special high-tier like Hypixel
    REVENANT("Revenant", Material.ROTTEN_FLESH, Material.ROTTEN_FLESH, 0.5); // slayer synergy

    private final String displayName;
    private final Material icon;
    private final Material resource;
    private final double productionMultiplier; // Higher = faster/better yields

    MinionType(String displayName, Material icon, Material resource, double productionMultiplier) {
        this.displayName = displayName;
        this.icon = icon;
        this.resource = resource;
        this.productionMultiplier = productionMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public Material getResource() {
        return resource;
    }

    public double getProductionMultiplier() {
        return productionMultiplier;
    }

    public static MinionType fromString(String name) {
        if (name == null) return WHEAT;
        try {
            return valueOf(name.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return WHEAT;
        }
    }

    public String getKey() {
        return name();
    }
}