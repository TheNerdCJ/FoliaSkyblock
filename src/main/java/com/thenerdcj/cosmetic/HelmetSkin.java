package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Cosmetic Helmet Skins for FoliaSkyblock.
 * 
 * These are purely visual overrides applied to helmets (similar to Hypixel Fire Sale Helmet Skins).
 * Play-to-Win only — earned via prestige, slayer tokens, and collection milestones.
 * 
 * Implementation uses PersistentDataContainer + CustomModelData for server-authoritative visuals.
 * Inspired by Hypixel Skyblock Helmet Skins + the project's existing PetSkin/Rune patterns.
 */
public enum HelmetSkin {

    NONE("None", "§7No skin applied", PetRarity.COMMON, 0, 0, 0),

    // Universal / Early skins
    CRIMSON_FLAME("Crimson Flame", "§cFiery crimson overlay", PetRarity.UNCOMMON, 2, 40, 1001),
    TERROR_SHADOW("Terror Shadow", "§8Dark terror motif", PetRarity.UNCOMMON, 2, 45, 1002),
    AURORA_GLOW("Aurora Glow", "§bIridescent aurora", PetRarity.RARE, 3, 70, 1003),

    // Premium / Set-specific
    BLAZING_CRIMSON("Blazing Crimson", "§6§lAnimated blaze effect", PetRarity.EPIC, 5, 160, 1004),
    CHAOS_TERROR("Chaos Terror", "§5§lCorrupted terror runes", PetRarity.EPIC, 5, 180, 1005),
    FERVOR_HOLLOW("Fervor Hollow", "§7§lEthereal hollow glow", PetRarity.LEGENDARY, 6, 0, 1006), // prestige reward

    // Collection / Slayer rewards
    SLAYER_CROWN("Slayer Crown", "§c⚔ Trophy crown", PetRarity.EPIC, 4, 200, 1007),
    COLLECTOR_HELM("Collector's Helm", "§6◆ Prestige collector", PetRarity.LEGENDARY, 7, 0, 1008), // high prestige

    // Additional variety
    FROST_GUARDIAN("Frost Guardian", "§b❄ Icy guardian motif", PetRarity.RARE, 3, 65, 1009),
    VOID_WALKER("Void Walker", "§8☄ End portal theme", PetRarity.EPIC, 5, 140, 1010);

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final int customModelData; // 0 = no override

    HelmetSkin(String displayName, String description, PetRarity rarity,
               int minPrestige, int tokenCost, int customModelData) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.customModelData = customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public PetRarity getRarity() {
        return rarity;
    }

    public int getMinPrestige() {
        return minPrestige;
    }

    public int getTokenCost() {
        return tokenCost;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Applies the cosmetic skin to a helmet ItemStack (modifies CustomModelData via PDC).
     * Returns the modified (or original) ItemStack.
     */
    public ItemStack applyToHelmet(ItemStack helmet) {
        if (helmet == null || helmet.getType() == Material.AIR || this.isNone()) {
            return helmet;
        }

        ItemMeta meta = helmet.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(this.customModelData);
            helmet.setItemMeta(meta);
        }
        return helmet;
    }

    /**
     * Removes any helmet skin from the given ItemStack.
     */
    public static ItemStack removeSkin(ItemStack helmet) {
        if (helmet == null || helmet.getType() == Material.AIR) {
            return helmet;
        }
        ItemMeta meta = helmet.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(null);
            helmet.setItemMeta(meta);
        }
        return helmet;
    }
}