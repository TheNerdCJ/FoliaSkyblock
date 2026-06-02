package com.thenerdcj.tags;

import com.thenerdcj.pets.PetRarity;

/**
 * Defines cosmetic player tags for FoliaSkyblock.
 *
 * Tags appear alongside the player's rank prefix (chat, tab list, display name).
 * Purely cosmetic, Play-to-Win only.
 *
 * Design inspired by:
 * - Hypixel SkyBlock level prefix colors + emblems
 * - Supreme Tags (GUI selection, categories, chat integration)
 * - NameTagEdit / TAB patterns (prefix/suffix composition)
 * - Our own PetType + ParticleTrail systems (rarity, prestige gating, collection XP, Slayer shop)
 */
public enum PlayerTag {

    // ===== BASIC / EARLY GAME =====
    NONE("None", "", "§7No tag equipped", PetRarity.COMMON, 0, 0),

    EXPLORER("Explorer", "§a✧", "§7First steps into the skyblock", PetRarity.COMMON, 0, 0),
    FARMER("Farmer", "§2🌾", "§7Harvested your first crops", PetRarity.COMMON, 0, 10),

    // ===== MID GAME =====
    SLAYER("Slayer", "§c☠", "§7Defeated your first boss", PetRarity.UNCOMMON, 1, 25),
    COLLECTOR("Collector", "§b★", "§7Growing your wardrobe & pet collection", PetRarity.UNCOMMON, 2, 40),

    // ===== PRESTIGE REWARDS =====
    PRESTIGIOUS("Prestigious", "§6✦", "§7Reached your first Prestige", PetRarity.RARE, 3, 0),   // prestige reward
    VETERAN("Veteran", "§e⚔", "§7Multiple Prestiges under your belt", PetRarity.RARE, 5, 0),

    // ===== HIGH END / SLAYER FOCUSED =====
    DRAGON_SLAYER("Dragon Slayer", "§5🐉", "§7Faced the mightiest foes", PetRarity.EPIC, 4, 150),
    LEGENDARY("Legendary", "§6§l★", "§7A true legend of the islands", PetRarity.LEGENDARY, 6, 0), // high prestige reward

    // ===== SPECIAL / COLLECTION MILESTONES =====
    PET_MASTER("Pet Master", "§d♥", "§7Collected many unique companions", PetRarity.EPIC, 5, 80),
    TRAILBLAZER("Trailblazer", "§5✨", "§7Master of cosmetic effects", PetRarity.RARE, 4, 60),

    // ===== ADDITIONAL TAGS (expanded selection) =====
    // Nature / Exploration line
    MINER("Miner", "§7⛏", "§7Deep rock explorer", PetRarity.COMMON, 1, 15),
    BUILDER("Builder", "§6⚒", "§7Master constructor", PetRarity.UNCOMMON, 2, 35),

    // Combat / Boss line
    BOSS_HUNTER("Boss Hunter", "§4☠", "§7Slayer of island bosses", PetRarity.RARE, 3, 70),
    MYTHIC_SLAYER("Mythic Slayer", "§5☠☠", "§7Legendary boss slayer", PetRarity.LEGENDARY, 7, 250),

    // Prestige & Endgame
    ASCENDED("Ascended", "§b✧", "§7Beyond the first prestige", PetRarity.EPIC, 4, 0),
    ETERNAL("Eternal", "§6♾", "§7True veteran of the skies", PetRarity.MYTHIC, 8, 0),

    // Fun / Special
    LUCKY("Lucky", "§a☘", "§7Fortune favors the bold", PetRarity.UNCOMMON, 2, 45),
    MYSTIC("Mystic", "§5✺", "§7Touched by ancient magic", PetRarity.RARE, 4, 95);

    private final String displayName;
    private final String tagText;           // The actual cosmetic text shown (supports colors)
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;

    PlayerTag(String displayName, String tagText, String description, PetRarity rarity, int minPrestige, int tokenCost) {
        this.displayName = displayName;
        this.tagText = tagText;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTagText() {
        return tagText;
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

    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Returns the formatted tag ready to be inserted (e.g. " §6✦ " or "")
     */
    public String getFormattedTag() {
        if (isNone() || tagText.isEmpty()) return "";
        return " " + tagText + "§r";
    }
}
