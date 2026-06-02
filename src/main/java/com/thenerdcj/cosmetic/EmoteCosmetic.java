package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic Emotes for chat and visual flair.
 * Play-to-Win, prestige/slayer gated.
 * When used (/emote <name>), plays particles and sends a formatted message.
 */
public enum EmoteCosmetic {

    NONE("None", "§7No emote", PetRarity.COMMON, 0, 0, "none"),

    WAVE("Wave", "§7Friendly wave", PetRarity.COMMON, 1, 20, "wave"),
    CHEER("Cheer", "§eEnthusiastic cheer", PetRarity.UNCOMMON, 2, 40, "cheer"),
    DANCE("Dance", "§dFun dance", PetRarity.UNCOMMON, 2, 45, "dance"),
    BOW("Bow", "§fRespectful bow", PetRarity.RARE, 3, 70, "bow"),
    LAUGH("Laugh", "§aHearty laugh", PetRarity.RARE, 3, 75, "laugh"),
    FACEPALM("Facepalm", "§7Disappointed facepalm", PetRarity.EPIC, 4, 120, "facepalm"),
    FLEX("Flex", "§cShow off muscles", PetRarity.EPIC, 4, 130, "flex"),
    SALUTE("Salute", "§6Military salute", PetRarity.LEGENDARY, 5, 200, "salute"),
    SPIN("Spin", "§5Dramatic spin", PetRarity.LEGENDARY, 6, 0, "spin"), // prestige

    // Polish variety
    THUMBS_UP("Thumbs Up", "§aApproving thumbs up", PetRarity.UNCOMMON, 2, 35, "thumbs"),
    SHRUG("Shrug", "§7Indifferent shrug", PetRarity.RARE, 3, 65, "shrug"),

    // Emote expansion (continue autonomous)
    CLAP("Clap", "§aEnthusiastic clapping", PetRarity.UNCOMMON, 2, 40, "clap"),
    POINT("Point", "§eAccusatory point", PetRarity.RARE, 3, 70, "point"),
    CRY("Cry", "§9Sad tears", PetRarity.EPIC, 4, 110, "cry"),
    VICTORY("Victory", "§6Triumphant pose", PetRarity.LEGENDARY, 5, 180, "victory"),

    // Emote polish variety
    NOD("Nod", "§7Affirmative nod", PetRarity.COMMON, 1, 15, "nod"),
    HIGH_FIVE("High Five", "§dEnergetic high five", PetRarity.UNCOMMON, 2, 38, "highfive");

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final String effectKey;

    EmoteCosmetic(String displayName, String description, PetRarity rarity,
                  int minPrestige, int tokenCost, String effectKey) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.effectKey = effectKey;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PetRarity getRarity() { return rarity; }
    public int getMinPrestige() { return minPrestige; }
    public int getTokenCost() { return tokenCost; }
    public String getEffectKey() { return effectKey; }

    public boolean isNone() { return this == NONE; }
}
