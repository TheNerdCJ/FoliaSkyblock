package com.thenerdcj.runes;

import com.thenerdcj.pets.PetRarity;
import org.bukkit.Particle;

/**
 * Cosmetic Runes for FoliaSkyblock.
 * 
 * Runes are applied to weapons/tools and produce visual particle effects.
 * Purely cosmetic, Play-to-Win only.
 * 
 * Inspired by Hypixel Skyblock Runes + general cosmetic plugins.
 */
public enum Rune {

    NONE("None", "§7No rune applied", PetRarity.COMMON, 0, 0, null),

    // Common / Early
    BLOOD("Blood", "§cBlood spatter on hit", PetRarity.COMMON, 0, 0, Particle.DAMAGE_INDICATOR),
    SNOW("Snow", "§fSnow explosion on hit", PetRarity.COMMON, 0, 5, Particle.SNOWFLAKE),
    FLAME("Flame", "§6Small flames on hit", PetRarity.COMMON, 1, 10, Particle.FLAME),

    // Uncommon
    LIGHTNING("Lightning", "§eElectric sparks", PetRarity.UNCOMMON, 2, 35, Particle.ELECTRIC_SPARK),
    SOUL("Soul", "§bSoul particles on hit", PetRarity.UNCOMMON, 2, 40, Particle.SCULK_SOUL),
    MAGIC("Magic", "§5Enchant particles", PetRarity.UNCOMMON, 2, 30, Particle.ENCHANT),

    // Rare
    RAINBOW("Rainbow", "§dColorful burst", PetRarity.RARE, 3, 90, Particle.HAPPY_VILLAGER),
    VOID("Void", "§8End portal effect", PetRarity.RARE, 4, 110, Particle.PORTAL),
    DRAGON("Dragon", "§cDragon breath on hit", PetRarity.RARE, 4, 130, Particle.DRAGON_BREATH),

    // Epic+
    GALAXY("Galaxy", "§bCosmic stars", PetRarity.EPIC, 5, 180, Particle.END_ROD),
    PHOENIX("Phoenix", "§6Fiery rebirth particles", PetRarity.EPIC, 5, 200, Particle.FLAME),
    BLOOD_MOON("Blood Moon", "§4Heavy blood + smoke", PetRarity.LEGENDARY, 6, 0, Particle.DAMAGE_INDICATOR),

    // Additional runes for more variety
    ICE("Ice", "§bFreezing shards on hit", PetRarity.UNCOMMON, 2, 45, Particle.SNOWFLAKE),
    VENOM("Venom", "§2Poisonous green mist", PetRarity.RARE, 3, 95, Particle.HAPPY_VILLAGER),
    THUNDER("Thunder", "§eLightning strikes on crit", PetRarity.EPIC, 5, 160, Particle.ELECTRIC_SPARK),
    NEBULA("Nebula", "§5Starry cosmic cloud", PetRarity.LEGENDARY, 6, 0, Particle.END_ROD); // prestige reward

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final Particle effectParticle;
    private final int maxTier; // 1-3 like Hypixel runes

    Rune(String displayName, String description, PetRarity rarity, 
         int minPrestige, int tokenCost, Particle effectParticle) {
        this(displayName, description, rarity, minPrestige, tokenCost, effectParticle, 3);
    }

    Rune(String displayName, String description, PetRarity rarity, 
         int minPrestige, int tokenCost, Particle effectParticle, int maxTier) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.effectParticle = effectParticle;
        this.maxTier = Math.max(1, maxTier);
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

    public Particle getEffectParticle() {
        return effectParticle;
    }

    public int getMaxTier() {
        return maxTier;
    }

    public boolean isNone() {
        return this == NONE;
    }
}
