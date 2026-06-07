package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;

/**
 * Cosmetic Join/Leave Messages for FoliaSkyblock.
 * 
 * Purely cosmetic text messages for player join and leave events.
 * Play-to-Win gated.
 * 
 * Use %player% placeholder for the player's display name (includes rank + active tag).
 * Separate join and leave formats per cosmetic.
 */
public enum JoinLeaveMessageCosmetic {

    NONE("None", "", "", PetRarity.COMMON, 0, 0),

    // Basic
    CLASSIC("Classic", "§e%player% joined the game", "§e%player% left the game", PetRarity.UNCOMMON, 1, 15),
    WARM("Warm Welcome", "§aWelcome, %player%!", "§cFarewell, %player%.", PetRarity.UNCOMMON, 1, 20),

    // Mid
    ADVENTURER("Adventurer", "§6%player% has arrived to explore!", "§6%player% has left the adventure.", PetRarity.RARE, 2, 40),
    SHADOW("Shadow Entry", "§8%player% slipped in quietly...", "§8%player% vanished into the shadows.", PetRarity.RARE, 3, 45),

    // Epic
    LEGENDARY("Legendary", "§b§l%player% has joined the server!", "§b§l%player% has departed.", PetRarity.EPIC, 4, 80),
    COSMIC("Cosmic", "§5%player% materialized from the cosmos.", "§5%player% returned to the stars.", PetRarity.EPIC, 5, 100),

    // High
    MYTHIC("Mythic", "§d§lA mythic presence arrives: %player%!", "§d§l%player% fades into legend.", PetRarity.LEGENDARY, 6, 0), // prestige gated
    VOID("Void Walker", "§7%player% stepped out of the void.", "§7%player% stepped back into the void.", PetRarity.LEGENDARY, 6, 150);

    private final String displayName;
    private final String joinFormat;
    private final String leaveFormat;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;

    JoinLeaveMessageCosmetic(String displayName, String joinFormat, String leaveFormat, PetRarity rarity,
                             int minPrestige, int tokenCost) {
        this.displayName = displayName;
        this.joinFormat = joinFormat;
        this.leaveFormat = leaveFormat;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getJoinFormat() {
        return joinFormat;
    }

    public String getLeaveFormat() {
        return leaveFormat;
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
}