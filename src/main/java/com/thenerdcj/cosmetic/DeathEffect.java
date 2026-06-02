package com.thenerdcj.cosmetic;

import com.thenerdcj.pets.PetRarity;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Cosmetic Death / Kill Effects for FoliaSkyblock.
 * 
 * Purely visual effects that trigger on player death or when killing mobs/bosses.
 * Play-to-Win only.
 * 
 * Inspired by Hypixel minigame cosmetics (final kill effects) and the project's
 * existing particle systems (ParticleTrailManager, Rune effects).
 */
public enum DeathEffect {

    NONE("None", "§7No effect", PetRarity.COMMON, 0, 0, null, null),

    // Basic / Early
    FIREWORK("Firework Burst", "§eClassic fireworks on death", PetRarity.UNCOMMON, 1, 20, Particle.FIREWORK, Sound.ENTITY_FIREWORK_ROCKET_BLAST),
    LIGHTNING("Lightning Strike", "§bDramatic lightning bolt", PetRarity.UNCOMMON, 2, 35, Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER),
    BLOOD("Blood Splatter", "§cRed particle burst", PetRarity.RARE, 3, 50, Particle.DAMAGE_INDICATOR, Sound.ENTITY_GENERIC_HURT),

    // Advanced
    SOUL_RELEASE("Soul Release", "§bSouls escaping", PetRarity.RARE, 3, 60, Particle.SCULK_SOUL, Sound.ENTITY_ALLAY_DEATH),
    DRAGON_BREATH("Dragon's End", "§cPurple dragon breath", PetRarity.EPIC, 4, 90, Particle.DRAGON_BREATH, Sound.ENTITY_ENDER_DRAGON_DEATH),
    GALAXY("Cosmic Fade", "§5Galaxy swirl on death", PetRarity.EPIC, 5, 120, Particle.END_ROD, Sound.BLOCK_END_PORTAL_SPAWN),

    // High-end / Prestige
    EXPLOSION("Massive Explosion", "§6Huge fiery blast", PetRarity.LEGENDARY, 6, 0, Particle.EXPLOSION, Sound.ENTITY_GENERIC_EXPLODE), // prestige reward
    ANGEL_ASCENT("Angel Ascent", "§fHoly light and wings", PetRarity.LEGENDARY, 6, 200, Particle.END_ROD, Sound.ENTITY_PLAYER_LEVELUP),

    // Additional variety
    ICE_SHATTER("Ice Shatter", "§bFreezing explosion", PetRarity.RARE, 3, 55, Particle.SNOWFLAKE, Sound.BLOCK_GLASS_BREAK),
    VOID_RIFT("Void Rift", "§8End portal collapse", PetRarity.EPIC, 5, 110, Particle.PORTAL, Sound.ENTITY_ENDERMAN_DEATH);

    private final String displayName;
    private final String description;
    private final PetRarity rarity;
    private final int minPrestige;
    private final int tokenCost;
    private final Particle particle;
    private final Sound sound;

    DeathEffect(String displayName, String description, PetRarity rarity,
                int minPrestige, int tokenCost, Particle particle, Sound sound) {
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
        this.particle = particle;
        this.sound = sound;
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

    public Particle getParticle() {
        return particle;
    }

    public Sound getSound() {
        return sound;
    }

    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Triggers the death effect at the given location.
     * Safe for Folia (caller should use proper scheduler).
     */
    public void trigger(Location loc, Player player) {
        if (loc == null || this.isNone() || particle == null) return;

        loc.getWorld().spawnParticle(particle, loc, 30, 0.8, 1.5, 0.8, 0.05);
        
        if (sound != null) {
            loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
        }

        // Extra flair for higher tiers
        if (this == GALAXY || this == EXPLOSION) {
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 1, 1, 1, 0.1);
        }
    }
}