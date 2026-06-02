package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic Minion Skins for FoliaSkyblock.
 *
 * Purely visual overrides for the ArmorStand-based minion system.
 * Play-to-Win only (prestige + slayer tokens + collection).
 *
 * Inspired by Hypixel Skyblock Minion Skins (head overrides, cute/animal themes, event exclusives).
 * When a skin is active for a player, all minions on their islands use the themed appearance.
 * Premium skins include light particle accents (Folia-safe via EntityScheduler).
 *
 * Future: Per-minion assignment once individual minion persistence is added.
 */
public enum MinionSkin {

    NONE("None", "§7No skin applied", PetRarity.COMMON, 0, 0, 0, null, null),

    // Common / Early unlocks (cute animal themes)
    BUNNY("Bunny Minion", "§fAdorable bunny ears", PetRarity.COMMON, 1, 25, 4001, "MHF_Bunny", "bunny"),
    BEE("Busy Bee", "§eStriped bee aesthetic", PetRarity.COMMON, 1, 30, 4002, "MHF_Bee", "bee"),

    // Uncommon
    DONUT("Donut Delight", "§6Sweet pink donut vibe", PetRarity.UNCOMMON, 2, 45, 4003, "MHF_Villager", "donut"),
    TROPICAL_BIRD("Tropical Bird", "§aColorful parrot friend", PetRarity.UNCOMMON, 2, 50, 4004, "MHF_Parrot", "bird"),
    LADYBUG("Lucky Ladybug", "§cPolka-dot charm", PetRarity.UNCOMMON, 2, 55, 4005, "MHF_Villager", "ladybug"),

    // Rare
    WHITE_TIGER("White Tiger", "§f§lMajestic stripes", PetRarity.RARE, 3, 90, 4006, "MHF_Villager", "tiger"),
    CELESTIAL("Celestial Guardian", "§bStarry cosmic minion", PetRarity.RARE, 3, 85, 4007, "MHF_Enderman", "celestial"),

    // Epic
    DRAGON("Baby Dragon", "§cScaled dragon hatchling", PetRarity.EPIC, 4, 140, 4008, "MHF_EnderDragon", "dragon"),
    ALLAY("Allay Helper", "§dEthereal light bearer", PetRarity.EPIC, 4, 150, 4009, "MHF_Allay", "allay"),

    // Legendary / High prestige or slayer
    SLAYER_BEAST("Slayer Beast", "§4⚔ Trophy hunter", PetRarity.LEGENDARY, 5, 220, 4010, "MHF_Wolf", "slayer"),
    SUPREME("Supreme Overlord", "§6§lUltimate minion prestige", PetRarity.LEGENDARY, 6, 0, 4011, "MHF_Villager", "supreme"), // prestige reward example

    // More minion variety (autonomous polish continuation)
    PHOENIX("Phoenix Minion", "§6Fiery rebirth friend", PetRarity.LEGENDARY, 6, 180, 4012, "MHF_Villager", "celestial"), // reuses celestial particles for now
    FROST_GOLEM("Frost Golem", "§bIcy guardian minion", PetRarity.EPIC, 4, 155, 4013, "MHF_Villager", null), // falls to happy villager default, simple variety

    // More minion variety refinement
    PUMPKIN("Pumpkin Pal", "§6Spooky gourd theme", PetRarity.UNCOMMON, 2, 48, 4014, "MHF_Villager", null),
    ROBOT("Robot Minion", "§7Mechanical steampunk", PetRarity.RARE, 3, 95, 4015, "MHF_Villager", "celestial"),

    // Continued minion variety (autonomous polish)
    GHOST("Ghostly Minion", "§fSpooky floating sheet", PetRarity.RARE, 3, 100, 4016, "MHF_Villager", null),
    ANCIENT_GOLEM("Ancient Golem", "§6Mossy stone sentinel", PetRarity.LEGENDARY, 5, 210, 4017, "MHF_Villager", "slayer"); // reuse particles

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final int customModelData;
    private final String overrideHeadOwner; // MHF_xxx or null -> use original icon + CMD
    private final String particleTheme; // hint for manager (e.g. "flame", "heart", null)

    MinionSkin(String displayName, String description, PetRarity rarity,
               int minPrestige, int tokenCost, int customModelData,
               String overrideHeadOwner, String particleTheme) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.customModelData = customModelData;
        this.overrideHeadOwner = overrideHeadOwner;
        this.particleTheme = particleTheme;
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

    public String getOverrideHeadOwner() {
        return overrideHeadOwner;
    }

    public String getParticleTheme() {
        return particleTheme;
    }

    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Returns the effective skull owner for helmet, or null to fall back to MinionType icon.
     */
    public String getEffectiveHeadOwner() {
        return overrideHeadOwner;
    }
}
