package com.thenerdcj.cosmetic;

import org.bukkit.Particle;
import org.bukkit.Color;

/**
 * Defines available personal particle trails/auras for players.
 * These are **purely cosmetic** effects (Play-to-Win compliant).
 *
 * Unlock via:
 * - Prestige levels (many free at thresholds, especially high prestige rewards)
 * - Slayer Token purchases in /slayer shop
 *
 * Over 30 unique visual styles available, including shaped effects (wings, helix, rings, galaxy, etc.).
 * Implementation lives in ParticleTrailManager#spawnTrailParticles.
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
    ELECTRIC_TRAIL("Electric Spark", Particle.ELECTRIC_SPARK, null, 8, 250),

    // ===== EXPANDED TRAILS (Cosmetic only - Play to Win) =====

    // Nature / Light
    LEAF_TRAIL("Falling Leaves", Particle.CHERRY_LEAVES, null, 1, 55),
    SPARKLE_AURA("Sparkle Aura", Particle.WAX_ON, null, 2, 80),
    HOLY_AURA("Holy Light", Particle.END_ROD, null, 4, 130),

    // Fire / Energy
    FIREWORK_TRAIL("Firework Sparks", Particle.FIREWORK, null, 3, 100),
    PULSE_AURA("Energy Pulse", Particle.CRIT, null, 3, 90),
    METEOR_TRAIL("Meteor Trail", Particle.LAVA, null, 5, 150),

    // Water / Ice
    BUBBLE_AURA("Bubble Stream", Particle.BUBBLE_POP, null, 2, 85),
    FROST_AURA("Frost Aura", Particle.SNOWFLAKE, null, 4, 125),

    // Advanced Shapes & Effects (require special handling in manager)
    WING_AURA("Angel Wings", Particle.END_ROD, null, 5, 180),
    ORBITING_ORBS("Orbiting Orbs", Particle.ENCHANT, null, 4, 140),
    FOOTSTEP_TRAIL("Glowing Footsteps", Particle.DUST, Color.fromRGB(180, 220, 255), 2, 95),
    HELIX_TRAIL("DNA Helix", Particle.PORTAL, null, 6, 200),
    DOUBLE_HELIX("Double Helix", Particle.REVERSE_PORTAL, null, 7, 260),
    RING_AURA("Rotating Rings", Particle.DUST, Color.fromRGB(255, 100, 200), 5, 170),
    GALAXY_AURA("Galaxy Swirl", Particle.END_ROD, null, 8, 280),
    SHADOW_AURA("Shadow Veil", Particle.ASH, null, 6, 190),
    ASCENDING_SPIRAL("Ascending Spiral", Particle.ENCHANTED_HIT, null, 7, 240),

    // High prestige free rewards (tokenCost = 0)
    COSMIC_WINGS("Cosmic Wings", Particle.END_ROD, null, 9, 0),
    ETERNAL_FLAME("Eternal Flame", Particle.SOUL_FIRE_FLAME, null, 8, 0),

    // ===== EVEN MORE TRAILS (further expansion) =====

    // Nature expansion
    VINE_TRAIL("Entangling Vines", Particle.CHERRY_LEAVES, null, 2, 70),
    FLOWER_AURA("Flower Bloom", Particle.HAPPY_VILLAGER, null, 1, 60),
    MUSHROOM_TRAIL("Mushroom Spores", Particle.SPORE_BLOSSOM_AIR, null, 3, 105),

    // Magic / Arcane
    ARCANE_AURA("Arcane Runes", Particle.ENCHANT, null, 4, 135),
    STAR_TRAIL("Twinkling Stars", Particle.FIREWORK, null, 3, 115),
    RUNE_CIRCLE("Floating Runes", Particle.ENCHANTED_HIT, null, 6, 210),

    // Elemental
    WIND_TRAIL("Whirling Wind", Particle.CLOUD, null, 2, 75),
    EARTH_AURA("Stone Dust", Particle.DUST, Color.fromRGB(139, 90, 43), 4, 145),
    THUNDER_AURA("Thunder Crackle", Particle.ELECTRIC_SPARK, null, 5, 165),

    // Fun / Visual bursts
    CONFETTI_BURST("Confetti Explosion", Particle.FIREWORK, null, 5, 155),
    HEART_RING("Heart Circle", Particle.HEART, null, 4, 120),
    STAR_BURST("Star Shower", Particle.FIREWORK, null, 6, 195),

    // Ultimate high-end
    PHOENIX_AURA("Phoenix Flames", Particle.DRAGON_BREATH, null, 8, 0),  // prestige reward
    NEBULA_AURA("Nebula Cloud", Particle.PORTAL, null, 9, 300),

    // ===== MASSIVE EXPANSION - References: rlTrails (footprints, spheres, wings, vortex, waves, circles), PlayerParticles (120 particles, 38 styles, halos, spheres, combinations), Super Trails PRO (pulse, slinky, shooter, magician, lava pop, hearts, magic), ParticleHats (halo, cape, arch, crystal, vortex) =====

    // Footprints / Ground block trails (rlTrails style - leave marks on ground)
    FOOTPRINT_STONE("Stone Footprints", Particle.DUST, null, 2, 80),
    FOOTPRINT_LILY("Lily Pad Footprints", Particle.DUST, null, 3, 100),
    FOOTPRINT_CREEPER("Creeper Footprints", Particle.DUST, null, 4, 130),
    FOOTPRINT_RAINBOW("Rainbow Footprints", Particle.DUST, null, 5, 160),

    // Spheres / Full body surrounding (very popular - Guardian/Galaxy Sphere)
    GUARDIAN_SPHERE("Guardian Sphere", Particle.BUBBLE_POP, null, 4, 140),
    GALAXY_SPHERE("Galaxy Sphere", Particle.END_ROD, null, 6, 200),
    CRYSTAL_SPHERE("Crystal Sphere", Particle.ENCHANT, null, 5, 170),

    // Expanded Wings (animated back - Dragon, Bat, Fairy, Rainbow variants)
    DRAGON_WINGS("Dragon Wings", Particle.DRAGON_BREATH, null, 6, 210),
    BAT_WINGS("Bat Wings", Particle.ASH, null, 4, 145),
    FAIRY_WINGS("Fairy Wings", Particle.HAPPY_VILLAGER, null, 3, 125),
    RAINBOW_WINGS("Rainbow Wings", Particle.DUST, null, 7, 250),

    // Halos (above head - ParticleHats classic)
    HALO_GOLD("Golden Halo", Particle.END_ROD, null, 3, 110),
    HALO_ANGEL("Angel Halo", Particle.FIREWORK, null, 5, 180),
    HALO_DEMON("Demon Horns", Particle.LAVA, null, 5, 175),

    // Vortex / Advanced downward helix (rlTrails Void Vortex)
    VOID_VORTEX("Void Vortex", Particle.REVERSE_PORTAL, null, 7, 230),
    WATER_VORTEX("Water Vortex", Particle.BUBBLE_POP, null, 4, 150),

    // Waves / Expanding ground rings (Ground Wave / Shockwave)
    GROUND_WAVE("Ground Wave", Particle.DUST, null, 3, 115),
    SHOCKWAVE("Shockwave Ring", Particle.POOF, null, 6, 190),

    // Arches / More shapes
    CRYSTAL_ARCH("Crystal Arch", Particle.ENCHANT, null, 5, 165),
    MAGIC_ARCH("Magic Arch", Particle.WITCH, null, 4, 135),

    // Themed combos (Sorcerer Circle, Guardian, Mythic, fun ones like Super Trails examples)
    MAGIC_CIRCLE("Sorcerer Circle", Particle.ENCHANT, null, 5, 155),
    GUARDIAN_AURA("Guardian Aura", Particle.BUBBLE_POP, null, 4, 140),
    MYTHIC_AURA("Mythic Aura", Particle.DRAGON_BREATH, null, 8, 270),
    CUTE_HEARTS("Cute Hearts", Particle.HEART, null, 2, 90),
    ANGRY_SPARKS("Angry Sparks", Particle.CRIT, null, 3, 105),
    LAVA_POP("Lava Pop", Particle.LAVA, null, 4, 120),
    SLINKY_TRAIL("Slinky Trail", Particle.NOTE, null, 5, 160),
    SHOOTER_TRAIL("Shooter Trail", Particle.CRIT, null, 4, 130),
    MAGICIAN_CIRCLE("Magician Circle", Particle.WITCH, null, 6, 205);

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