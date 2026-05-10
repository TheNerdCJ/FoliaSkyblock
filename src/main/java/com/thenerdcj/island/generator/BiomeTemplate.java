package com.thenerdcj.island.generator;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import java.util.*;

/**
 * Defines block palettes, generation parameters, and rules for each biome.
 * Expanded for greater customization and randomization support while maintaining
 * specific biome identity for donor selection and Play-to-Win balance.
 *
 * Easy to extend: add new BiomeTemplate constants, register in BIOME_REGISTRY,
 * update allowed lists per dimension if needed.
 */
public class BiomeTemplate {

    // ----- fields (expanded for customization) -----
    private final Biome biome;
    private final String displayName;
    private final Material baseBlock;
    private final Material surfaceBlock;
    private final Material oreBlock; // primary ore visual
    private final Material treeLog;
    private final Material treeLeaves;
    private final Material specialBlock;
    private final List<Material> allowedOres;

    // Randomization & customization params (per biome, easily tweaked)
    private final double treeChance;          // base chance/prob factor for trees
    private final double oreChance;           // base for ore placement
    private final int minRadius;
    private final int maxRadius;              // allows slight size variation per island
    private final double vegetationDensity;   // 0.0-1.0 multiplier for grass/flowers/etc.
    private final double pondChance;          // chance to attempt pond feature
    private final double rockChance;          // chance for rock formations
    private final double specialFeatureChance; // for unique biome structures (well, lava pool, chorus etc.)
    private final double oreDensityMultiplier; // slight randomizable density control (balanced avg)

    // ----- constructor -----
    public BiomeTemplate(Biome biome, String displayName,
                                 Material baseBlock, Material surfaceBlock,
                                 Material oreBlock, Material treeLog, Material treeLeaves,
                                 Material specialBlock, List<Material> allowedOres,
                                 double treeChance, double oreChance,
                                 int minRadius, int maxRadius,
                                 double vegetationDensity, double pondChance, double rockChance,
                                 double specialFeatureChance, double oreDensityMultiplier) {
        this.biome = biome;
        this.displayName = displayName;
        this.baseBlock = baseBlock;
        this.surfaceBlock = surfaceBlock;
        this.oreBlock = oreBlock;
        this.treeLog = treeLog;
        this.treeLeaves = treeLeaves;
        this.specialBlock = specialBlock;
        this.allowedOres = allowedOres != null ? allowedOres : new ArrayList<>();
        this.treeChance = treeChance;
        this.oreChance = oreChance;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.vegetationDensity = vegetationDensity;
        this.pondChance = pondChance;
        this.rockChance = rockChance;
        this.specialFeatureChance = specialFeatureChance;
        this.oreDensityMultiplier = oreDensityMultiplier;
    }

    // ----- getters (added new ones) -----
    public Biome getBiome() { return biome; }
    public String getDisplayName() { return displayName; }
    public Material getBaseBlock() { return baseBlock; }
    public Material getSurfaceBlock() { return surfaceBlock; }
    public Material getOreBlock() { return oreBlock; }
    public Material getTreeLog() { return treeLog; }
    public Material getTreeLeaves() { return treeLeaves; }
    public Material getSpecialBlock() { return specialBlock; }
    public List<Material> getAllowedOres() { return allowedOres; }
    public double getTreeChance() { return treeChance; }
    public double getOreChance() { return oreChance; }
    public int getMinRadius() { return minRadius; }
    public int getMaxRadius() { return maxRadius; }
    public double getVegetationDensity() { return vegetationDensity; }
    public double getPondChance() { return pondChance; }
    public double getRockChance() { return rockChance; }
    public double getSpecialFeatureChance() { return specialFeatureChance; }
    public double getOreDensityMultiplier() { return oreDensityMultiplier; }

    // ----- Predefined templates - tuned for balance & variety -----
    // Overworld
    public static final BiomeTemplate PLAINS = new BiomeTemplate(
            Biome.PLAINS, "Plains",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.40, 0.09,
            7, 11,  // size range for randomization
            0.65, 0.25, 0.15, 0.10, 1.0);

    public static final BiomeTemplate FOREST = new BiomeTemplate(
            Biome.FOREST, "Forest",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE),
            0.70, 0.11,
            8, 12,
            0.55, 0.20, 0.18, 0.08, 1.1);

    public static final BiomeTemplate DESERT = new BiomeTemplate(
            Biome.DESERT, "Desert",
            Material.SANDSTONE, Material.SAND,
            Material.GOLD_ORE, null, null,
            Material.SAND, List.of(Material.GOLD_ORE, Material.IRON_ORE),
            0.05, 0.13,
            6, 10,
            0.15, 0.05, 0.35, 0.12, 0.9);  // low veg, some rocks, special wells?

    public static final BiomeTemplate TAIGA = new BiomeTemplate(
            Biome.TAIGA, "Taiga",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.IRON_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.IRON_ORE, Material.COAL_ORE),
            0.60, 0.10,
            7, 11,
            0.50, 0.15, 0.22, 0.07, 1.0);

    public static final BiomeTemplate JUNGLE = new BiomeTemplate(
            Biome.JUNGLE, "Jungle",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.EMERALD_ORE, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.EMERALD_ORE, Material.DIAMOND_ORE),
            0.85, 0.08,
            8, 13,
            0.75, 0.30, 0.12, 0.15, 1.2);  // dense, ponds, special (vines, melons)

    // Nether & End (limited trees)
    public static final BiomeTemplate NETHER = new BiomeTemplate(
            Biome.NETHER_WASTES, "Nether",
            Material.NETHERRACK, Material.NETHERRACK,
            Material.NETHER_QUARTZ_ORE, null, null,
            Material.SOUL_SAND, List.of(Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS),
            0.0, 0.18,
            6, 9,
            0.10, 0.25, 0.20, 0.18, 1.0);  // lava ponds, rocks, special fire/glowstone

    public static final BiomeTemplate END = new BiomeTemplate(
            Biome.THE_END, "End",
            Material.END_STONE, Material.END_STONE,
            Material.OBSIDIAN, null, null,
            Material.END_STONE, List.of(Material.OBSIDIAN),
            0.0, 0.06,
            5, 8,
            0.05, 0.08, 0.10, 0.22, 0.8);  // chorus, obsidian pillars small, low density

    // ----- registry -----
    private static final Map<Biome, BiomeTemplate> BIOME_REGISTRY = new HashMap<>();

    static {
        BIOME_REGISTRY.put(Biome.PLAINS, PLAINS);
        BIOME_REGISTRY.put(Biome.FOREST, FOREST);
        BIOME_REGISTRY.put(Biome.DESERT, DESERT);
        BIOME_REGISTRY.put(Biome.TAIGA, TAIGA);
        BIOME_REGISTRY.put(Biome.JUNGLE, JUNGLE);
        BIOME_REGISTRY.put(Biome.NETHER_WASTES, NETHER);
        BIOME_REGISTRY.put(Biome.THE_END, END);
        // Easy to add more e.g. Biome.CHERRY_GROVE by creating new template + register + add to allowed list
    }

    public static BiomeTemplate getTemplate(Biome biome) {
        return BIOME_REGISTRY.getOrDefault(biome, PLAINS);
    }

    public static List<BiomeTemplate> getAllTemplates() {
        return new ArrayList<>(BIOME_REGISTRY.values());
    }

    public static List<Biome> getAllowedOverworldBiomes() {
        return List.of(Biome.PLAINS, Biome.FOREST, Biome.DESERT, Biome.TAIGA, Biome.JUNGLE);
    }

    public static List<Biome> getAllowedNetherBiomes() {
        return List.of(Biome.NETHER_WASTES);
    }

    public static List<Biome> getAllowedEndBiomes() {
        return List.of(Biome.THE_END);
    }
}
