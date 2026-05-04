package com.thenerdcj.boss;

import org.bukkit.Material;

/**
 * Achievement categories for organization
 * PUBLIC to allow access from GUI classes in other packages
 */
public enum AchievementCategory {
    BEGINNER("§a§lBEGINNER", Material.LIME_DYE),
    INTERMEDIATE("§e§lINTERMEDIATE", Material.YELLOW_DYE),
    ADVANCED("§9§lADVANCED", Material.BLUE_DYE),
    LEGENDARY("§6§lLEGENDARY", Material.ORANGE_DYE);

    private final String displayName;
    private final Material icon;

    AchievementCategory(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
}