package com.thenerdcj.cosmetic;

import org.bukkit.Particle;
import org.bukkit.Color;

/**
 * Defines available personal particle trails/auras for players.
 * These are cosmetic effects that follow the player.
 *
 * Unlock via Prestige levels (some free at certain levels) + Slayer Token purchases in GUI/Shop.
 * Visual variety implemented in ParticleTrailManager#spawnTrailParticles.
 */
public enum ParticleTrail {

    NONE("None", null, null, 0, 0),

    // Low prestige / cheap starter trails
    FLAME_TRAIL("Flame Trail", Particle.FLAME, null, 0, 40),
    SMOKE_TRAIL("Smoke Trail", Particle.SMOKE, null, 0, 30),
    HAPPY_VILLAGER("Happy Villager Aura", Particle.HAPPY_VILLAGER, null, 1, 45),

    // Early-mid
    HEART_AURA("Heart Aura", Particle.HEART, null, 1, 70),
    MAGIC_AURA("Magic Aura", Particle.WITCH, null, 2, 75),
    ENDER_AURA("Ender Aura", Particle.PORTAL, null, 2, 90),

    // Mid game
    LAVA_TRAIL("Lava Trail", Particle.LAVA, null, 3, 95),
    SOUL_TRAIL("Soul Flame Trail", Particle.SOUL_FIRE_FLAME, null, 3, 110),
    CRIT_SPARK("Crit Spark Trail", Particle.CRIT, null, 4, 85),

    // High prestige / premium
    NOTE_TRAIL("Musical Note Trail", Particle.NOTE, null, 4, 140),
    DUST_TRAIL("Golden Dust", Particle.DUST, Color.fromRGB(255, 215, 0), 5, 160),
    RAINBOW_DUST("Rainbow Dust Aura", Particle.DUST, null, 5, 200),   // Special rainbow cycling in manager
    VOID_AURA("Void Portal Aura", Particle.REVERSE_PORTAL, null, 6, 180),

    // Prestige reward / very high visual flair
    DRAGON_BREATH("Dragon Breath Aura", Particle.DRAGON_BREATH, null, 7, 0),   // Prestige 7+ free reward
    PORTAL_AURA("Dimensional Portal", Particle.PORTAL, null, 6, 220),
    ELECTRIC_TRAIL("Electric Spark", Particle.ELECTRIC_SPARK, null, 8, 250);

    private final String displayName;
    private final Particle particle;
    private final Color color;           // For DUST etc. (null = use special logic in manager)
    private final int minPrestige;       // Minimum prestige level to unlock
    private final int tokenCost;         // Cost in Slayer Tokens (0 = prestige reward / free at level)

    ParticleTrail(String displayName, Particle particle, Color color, int minPrestige, int tokenCost) {
        this.displayName = displayName;
        this.particle = particle;
        this.color = color;
        this.minPrestige = minPrestige;
        this.tokenCost = tokenCost;
    }

    public String getDisplayName() { return displayName; }
    public Particle getParticle() { return particle; }
    public Color getColor() { return color; }
    public int getMinPrestige() { return minPrestige; }
    public int getTokenCost() { return tokenCost; }

    /**
     * Quick check (used by managers/GUIs). For actual gating use prestige + token checks + consume.
     */
    public boolean isUnlocked(int prestigeLevel, int tokensHeld) {
        if (this == NONE) return true;
        if (prestigeLevel < minPrestige) return false;
        return tokenCost == 0 || tokensHeld >= tokenCost;
    }
}