package com.thenerdcj.wings;

import com.thenerdcj.pets.PetRarity;
import org.bukkit.Particle;

/**
 * Cosmetic Elytra Wing styles for FoliaSkyblock.
 * 
 * When equipped, these provide special visual effects (particles) while gliding with an elytra.
 * Purely cosmetic, Play-to-Win only.
 * 
 * Design references:
 * - ElytraEssentials (cosmetic particle effects during flight + shop)
 * - Hypixel-style custom wing appearances and effects
 * - Resource pack wing models (we provide server-side particle equivalent + future model data support)
 * - Existing ParticleTrailManager advanced effects (wings, helix, etc.)
 * 
 * Fits the project: Same patterns as PetSkin, PlayerTag, ParticleTrail.
 */
public enum ElytraWing {

    NONE("None", "§7No special wings", PetRarity.COMMON, 0, 0, null, Particle.CLOUD),

    // Basic / Early
    FEATHER("Feather Wings", "§7Light and agile", PetRarity.COMMON, 0, 0, "Light gliding particles", Particle.CLOUD),
    LEAF("Leaf Wings", "§2Nature's embrace", PetRarity.COMMON, 1, 10, "Gentle leaf trail", Particle.HAPPY_VILLAGER),

    // Mid
    ANGEL("Angel Wings", "§fPure and holy", PetRarity.UNCOMMON, 2, 40, "Holy light particles", Particle.END_ROD),
    BAT("Bat Wings", "§8Night stalker", PetRarity.UNCOMMON, 2, 45, "Dark ash effect", Particle.ASH),

    // Advanced
    DRAGON("Dragon Wings", "§cMighty and fierce", PetRarity.RARE, 4, 120, "Fire and smoke", Particle.FLAME),
    FAIRY("Fairy Wings", "§dMagical sparkle", PetRarity.RARE, 3, 80, "Sparkle and dust", Particle.HAPPY_VILLAGER),

    // High-end / Prestige
    PHOENIX("Phoenix Wings", "§6Rebirth in flames", PetRarity.EPIC, 5, 200, "Intense fire trail", Particle.FLAME),
    VOID("Void Wings", "§5Cosmic darkness", PetRarity.EPIC, 5, 180, "Portal and end particles", Particle.PORTAL),

    // Legendary / Collection
    GALAXY("Galaxy Wings", "§bStellar beauty", PetRarity.LEGENDARY, 6, 0, "Star and nebula effect", Particle.END_ROD), // prestige reward
    CRYSTAL("Crystal Wings", "§bRefractive elegance", PetRarity.LEGENDARY, 7, 0, "Enchant and crystal", Particle.ENCHANT); // high prestige

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final String effectDescription;
    private final Particle primaryParticle;

    ElytraWing(String displayName, String description, PetRarity rarity, int minPrestige, int tokenCost, 
               String effectDescription, Particle primaryParticle) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.effectDescription = effectDescription;
        this.primaryParticle = primaryParticle;
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

    public String getEffectDescription() {
        return effectDescription;
    }

    public Particle getPrimaryParticle() {
        return primaryParticle;
    }

    public boolean isNone() {
        return this == NONE;
    }
}
