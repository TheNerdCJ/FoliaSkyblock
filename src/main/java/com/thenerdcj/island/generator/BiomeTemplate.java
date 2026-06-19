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
            3, 5,   // smaller starting island (compact PMC-style skyblock starters)
            0.65, 0.25, 0.15, 0.10, 1.0);

    public static final BiomeTemplate FOREST = new BiomeTemplate(
            Biome.FOREST, "Forest",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE),
            0.70, 0.11,
            3, 5,   // smaller starting island (compact PMC-style skyblock starters)
            0.55, 0.20, 0.18, 0.08, 1.1);

    public static final BiomeTemplate DESERT = new BiomeTemplate(
            Biome.DESERT, "Desert",
            Material.SANDSTONE, Material.SAND,
            Material.GOLD_ORE, null, null,
            Material.SAND, List.of(Material.GOLD_ORE, Material.IRON_ORE),
            0.05, 0.13,
            2, 4,   // smaller starting island (compact PMC-style skyblock starters)
            0.15, 0.05, 0.35, 0.12, 0.9);  // low veg, some rocks, special wells?

    public static final BiomeTemplate TAIGA = new BiomeTemplate(
            Biome.TAIGA, "Taiga",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.IRON_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.IRON_ORE, Material.COAL_ORE),
            0.60, 0.10,
            3, 5,   // smaller starting island (compact PMC-style skyblock starters)
            0.50, 0.15, 0.22, 0.07, 1.0);

    public static final BiomeTemplate JUNGLE = new BiomeTemplate(
            Biome.JUNGLE, "Jungle",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.EMERALD_ORE, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.EMERALD_ORE, Material.DIAMOND_ORE),
            0.85, 0.08,
            3, 5,   // smaller starting island (compact PMC-style skyblock starters)
            0.75, 0.30, 0.12, 0.15, 1.2);  // dense, ponds, special (vines, melons)

    // Expanded overworld biomes (donor selection)
    public static final BiomeTemplate SAVANNA = new BiomeTemplate(
            Biome.SAVANNA, "Savanna",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.ACACIA_LOG, Material.ACACIA_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.30, 0.10,
            3, 5,
            0.40, 0.10, 0.25, 0.08, 1.0);

    public static final BiomeTemplate BIRCH_FOREST = new BiomeTemplate(
            Biome.BIRCH_FOREST, "Birch Forest",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.BIRCH_LOG, Material.BIRCH_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.60, 0.09,
            3, 5,
            0.55, 0.15, 0.15, 0.07, 1.0);

    public static final BiomeTemplate CHERRY_GROVE = new BiomeTemplate(
            Biome.CHERRY_GROVE, "Cherry Grove",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.CHERRY_LOG, Material.CHERRY_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.45, 0.08,
            3, 5,
            0.65, 0.12, 0.10, 0.10, 1.1);

    public static final BiomeTemplate FLOWER_FOREST = new BiomeTemplate(
            Biome.FLOWER_FOREST, "Flower Forest",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.55, 0.09,
            3, 5,
            0.80, 0.18, 0.12, 0.08, 1.0);

    public static final BiomeTemplate MEADOW = new BiomeTemplate(
            Biome.MEADOW, "Meadow",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.25, 0.08,
            3, 5,
            0.70, 0.15, 0.10, 0.09, 1.0);

    // Further expanded overworld biomes for selection
    public static final BiomeTemplate DARK_FOREST = new BiomeTemplate(
            Biome.DARK_FOREST, "Dark Forest",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.75, 0.10,
            3, 5,
            0.50, 0.20, 0.15, 0.12, 1.0);

    public static final BiomeTemplate SWAMP = new BiomeTemplate(
            Biome.SWAMP, "Swamp",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.40, 0.12,
            3, 5,
            0.45, 0.35, 0.10, 0.15, 1.0);

    public static final BiomeTemplate MANGROVE_SWAMP = new BiomeTemplate(
            Biome.MANGROVE_SWAMP, "Mangrove Swamp",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.MANGROVE_LOG, Material.MANGROVE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.50, 0.10,
            3, 5,
            0.55, 0.40, 0.08, 0.18, 1.0);

    public static final BiomeTemplate BADLANDS = new BiomeTemplate(
            Biome.BADLANDS, "Badlands",
            Material.TERRACOTTA, Material.RED_SAND,
            Material.GOLD_ORE, null, null,
            Material.RED_SAND, List.of(Material.GOLD_ORE, Material.IRON_ORE),
            0.05, 0.15,
            2, 4,
            0.10, 0.05, 0.30, 0.10, 0.9);

    // Further expansion of overworld biomes
    public static final BiomeTemplate GROVE = new BiomeTemplate(
            Biome.GROVE, "Grove",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.40, 0.09,
            3, 5,
            0.50, 0.15, 0.12, 0.08, 1.0);

    public static final BiomeTemplate SNOWY_PLAINS = new BiomeTemplate(
            Biome.SNOWY_PLAINS, "Snowy Plains",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.15, 0.08,
            3, 5,
            0.30, 0.10, 0.10, 0.05, 1.0);

    public static final BiomeTemplate SNOWY_TAIGA = new BiomeTemplate(
            Biome.SNOWY_TAIGA, "Snowy Taiga",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.IRON_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.IRON_ORE, Material.COAL_ORE),
            0.50, 0.09,
            3, 5,
            0.45, 0.12, 0.15, 0.06, 1.0);

    public static final BiomeTemplate MUSHROOM_FIELDS = new BiomeTemplate(
            Biome.MUSHROOM_FIELDS, "Mushroom Fields",
            Material.DIRT, Material.MYCELIUM,
            Material.COAL_ORE, null, null,
            Material.MYCELIUM, List.of(Material.COAL_ORE),
            0.0, 0.05,
            3, 5,
            0.80, 0.20, 0.10, 0.15, 0.8);

    // Even further expansion
    public static final BiomeTemplate OLD_GROWTH_PINE_TAIGA = new BiomeTemplate(
            Biome.OLD_GROWTH_PINE_TAIGA, "Old Growth Pine Taiga",
            Material.DIRT, Material.PODZOL,
            Material.COAL_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.PODZOL, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.80, 0.10,
            3, 5,
            0.40, 0.15, 0.20, 0.10, 1.0);

    public static final BiomeTemplate OLD_GROWTH_SPRUCE_TAIGA = new BiomeTemplate(
            Biome.OLD_GROWTH_SPRUCE_TAIGA, "Old Growth Spruce Taiga",
            Material.DIRT, Material.PODZOL,
            Material.COAL_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.PODZOL, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.75, 0.10,
            3, 5,
            0.45, 0.12, 0.18, 0.09, 1.0);

    public static final BiomeTemplate WINDSWEPT_FOREST = new BiomeTemplate(
            Biome.WINDSWEPT_FOREST, "Windswept Forest",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.35, 0.10,
            3, 5,
            0.35, 0.10, 0.25, 0.08, 1.0);

    public static final BiomeTemplate BAMBOO_JUNGLE = new BiomeTemplate(
            Biome.BAMBOO_JUNGLE, "Bamboo Jungle",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.EMERALD_ORE, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.EMERALD_ORE, Material.DIAMOND_ORE),
            0.60, 0.08,
            3, 5,
            0.65, 0.25, 0.12, 0.12, 1.1);

    // Further expansion of overworld biomes
    public static final BiomeTemplate WINDSWEPT_HILLS = new BiomeTemplate(
            Biome.WINDSWEPT_HILLS, "Windswept Hills",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.20, 0.12,
            3, 5,
            0.25, 0.08, 0.35, 0.06, 1.0);

    public static final BiomeTemplate STONY_SHORE = new BiomeTemplate(
            Biome.STONY_SHORE, "Stony Shore",
            Material.STONE, Material.STONE,
            Material.COAL_ORE, null, null,
            Material.STONE, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.0, 0.10,
            2, 4,
            0.05, 0.05, 0.40, 0.05, 1.0);

    public static final BiomeTemplate BEACH = new BiomeTemplate(
            Biome.BEACH, "Beach",
            Material.SAND, Material.SAND,
            Material.GOLD_ORE, null, null,
            Material.SAND, List.of(Material.GOLD_ORE, Material.IRON_ORE),
            0.0, 0.08,
            2, 4,
            0.05, 0.05, 0.20, 0.04, 0.9);

    public static final BiomeTemplate SNOWY_BEACH = new BiomeTemplate(
            Biome.SNOWY_BEACH, "Snowy Beach",
            Material.SAND, Material.SAND,
            Material.COAL_ORE, null, null,
            Material.SAND, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.0, 0.08,
            2, 4,
            0.05, 0.05, 0.15, 0.04, 0.9);

    // Further expansion of overworld biomes for selection
    public static final BiomeTemplate STONY_PEAKS = new BiomeTemplate(
            Biome.STONY_PEAKS, "Stony Peaks",
            Material.STONE, Material.STONE,
            Material.COAL_ORE, null, null,
            Material.STONE, List.of(Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE),
            0.0, 0.12,
            2, 4,
            0.05, 0.03, 0.50, 0.05, 1.0);

    public static final BiomeTemplate JAGGED_PEAKS = new BiomeTemplate(
            Biome.JAGGED_PEAKS, "Jagged Peaks",
            Material.STONE, Material.STONE,
            Material.COAL_ORE, null, null,
            Material.STONE, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.0, 0.10,
            2, 4,
            0.05, 0.03, 0.45, 0.04, 1.0);

    public static final BiomeTemplate FROZEN_PEAKS = new BiomeTemplate(
            Biome.FROZEN_PEAKS, "Frozen Peaks",
            Material.STONE, Material.STONE,
            Material.COAL_ORE, null, null,
            Material.STONE, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.0, 0.10,
            2, 4,
            0.05, 0.03, 0.40, 0.04, 1.0);

    public static final BiomeTemplate SNOWY_SLOPES = new BiomeTemplate(
            Biome.SNOWY_SLOPES, "Snowy Slopes",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.20, 0.08,
            3, 5,
            0.30, 0.08, 0.20, 0.05, 1.0);

    // Even further expansion of overworld biomes for selection
    public static final BiomeTemplate OLD_GROWTH_BIRCH_FOREST = new BiomeTemplate(
            Biome.OLD_GROWTH_BIRCH_FOREST, "Old Growth Birch Forest",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.BIRCH_LOG, Material.BIRCH_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.70, 0.09,
            3, 5,
            0.55, 0.15, 0.15, 0.07, 1.0);

    public static final BiomeTemplate SPARSE_JUNGLE = new BiomeTemplate(
            Biome.SPARSE_JUNGLE, "Sparse Jungle",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.EMERALD_ORE, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.EMERALD_ORE, Material.DIAMOND_ORE),
            0.50, 0.08,
            3, 5,
            0.50, 0.20, 0.10, 0.10, 1.1);

    public static final BiomeTemplate ICE_SPIKES = new BiomeTemplate(
            Biome.ICE_SPIKES, "Ice Spikes",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.10, 0.08,
            3, 5,
            0.15, 0.10, 0.10, 0.08, 1.0);

    public static final BiomeTemplate SUNFLOWER_PLAINS = new BiomeTemplate(
            Biome.SUNFLOWER_PLAINS, "Sunflower Plains",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.30, 0.08,
            3, 5,
            0.70, 0.20, 0.10, 0.06, 1.0);

    public static final BiomeTemplate WOODED_BADLANDS = new BiomeTemplate(
            Biome.WOODED_BADLANDS, "Wooded Badlands",
            Material.TERRACOTTA, Material.RED_SAND,
            Material.GOLD_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.RED_SAND, List.of(Material.GOLD_ORE, Material.IRON_ORE),
            0.25, 0.12,
            2, 4,
            0.20, 0.05, 0.25, 0.08, 0.9);

    // Nether & End (limited trees)
    public static final BiomeTemplate NETHER = new BiomeTemplate(
            Biome.NETHER_WASTES, "Nether",
            Material.NETHERRACK, Material.NETHERRACK,
            Material.NETHER_QUARTZ_ORE, null, null,
            Material.SOUL_SAND, List.of(Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS),
            0.0, 0.18,
            2, 4,   // smaller starting island (compact PMC-style skyblock starters)
            0.10, 0.25, 0.20, 0.18, 1.0);  // lava ponds, rocks, special fire/glowstone

    public static final BiomeTemplate END = new BiomeTemplate(
            Biome.THE_END, "End",
            Material.END_STONE, Material.END_STONE,
            Material.OBSIDIAN, null, null,
            Material.END_STONE, List.of(Material.OBSIDIAN),
            0.0, 0.06,
            2, 3,   // smaller starting island (compact PMC-style skyblock starters)
            0.05, 0.08, 0.10, 0.22, 0.8);  // chorus, obsidian pillars small, low density

    // ----- registry -----
    private static final Map<Biome, BiomeTemplate> BIOME_REGISTRY = new HashMap<>();

    static {
        BIOME_REGISTRY.put(Biome.PLAINS, PLAINS);
        BIOME_REGISTRY.put(Biome.FOREST, FOREST);
        BIOME_REGISTRY.put(Biome.DESERT, DESERT);
        BIOME_REGISTRY.put(Biome.TAIGA, TAIGA);
        BIOME_REGISTRY.put(Biome.JUNGLE, JUNGLE);
        BIOME_REGISTRY.put(Biome.SAVANNA, SAVANNA);
        BIOME_REGISTRY.put(Biome.BIRCH_FOREST, BIRCH_FOREST);
        BIOME_REGISTRY.put(Biome.CHERRY_GROVE, CHERRY_GROVE);
        BIOME_REGISTRY.put(Biome.FLOWER_FOREST, FLOWER_FOREST);
        BIOME_REGISTRY.put(Biome.MEADOW, MEADOW);
        BIOME_REGISTRY.put(Biome.DARK_FOREST, DARK_FOREST);
        BIOME_REGISTRY.put(Biome.SWAMP, SWAMP);
        BIOME_REGISTRY.put(Biome.MANGROVE_SWAMP, MANGROVE_SWAMP);
        BIOME_REGISTRY.put(Biome.BADLANDS, BADLANDS);
        BIOME_REGISTRY.put(Biome.GROVE, GROVE);
        BIOME_REGISTRY.put(Biome.SNOWY_PLAINS, SNOWY_PLAINS);
        BIOME_REGISTRY.put(Biome.SNOWY_TAIGA, SNOWY_TAIGA);
        BIOME_REGISTRY.put(Biome.MUSHROOM_FIELDS, MUSHROOM_FIELDS);
        BIOME_REGISTRY.put(Biome.OLD_GROWTH_PINE_TAIGA, OLD_GROWTH_PINE_TAIGA);
        BIOME_REGISTRY.put(Biome.OLD_GROWTH_SPRUCE_TAIGA, OLD_GROWTH_SPRUCE_TAIGA);
        BIOME_REGISTRY.put(Biome.WINDSWEPT_FOREST, WINDSWEPT_FOREST);
        BIOME_REGISTRY.put(Biome.BAMBOO_JUNGLE, BAMBOO_JUNGLE);
        BIOME_REGISTRY.put(Biome.WINDSWEPT_HILLS, WINDSWEPT_HILLS);
        BIOME_REGISTRY.put(Biome.STONY_SHORE, STONY_SHORE);
        BIOME_REGISTRY.put(Biome.BEACH, BEACH);
        BIOME_REGISTRY.put(Biome.SNOWY_BEACH, SNOWY_BEACH);
        BIOME_REGISTRY.put(Biome.STONY_PEAKS, STONY_PEAKS);
        BIOME_REGISTRY.put(Biome.JAGGED_PEAKS, JAGGED_PEAKS);
        BIOME_REGISTRY.put(Biome.FROZEN_PEAKS, FROZEN_PEAKS);
        BIOME_REGISTRY.put(Biome.SNOWY_SLOPES, SNOWY_SLOPES);
        BIOME_REGISTRY.put(Biome.OLD_GROWTH_BIRCH_FOREST, OLD_GROWTH_BIRCH_FOREST);
        BIOME_REGISTRY.put(Biome.SPARSE_JUNGLE, SPARSE_JUNGLE);
        BIOME_REGISTRY.put(Biome.ICE_SPIKES, ICE_SPIKES);
        BIOME_REGISTRY.put(Biome.SUNFLOWER_PLAINS, SUNFLOWER_PLAINS);
        BIOME_REGISTRY.put(Biome.WOODED_BADLANDS, WOODED_BADLANDS);
        BIOME_REGISTRY.put(Biome.NETHER_WASTES, NETHER);
        BIOME_REGISTRY.put(Biome.THE_END, END);
    }

    public static BiomeTemplate getTemplate(Biome biome) {
        return BIOME_REGISTRY.getOrDefault(biome, PLAINS);
    }

    public static List<BiomeTemplate> getAllTemplates() {
        return new ArrayList<>(BIOME_REGISTRY.values());
    }

    public static List<Biome> getAllowedOverworldBiomes() {
        return List.of(Biome.PLAINS, Biome.FOREST, Biome.DESERT, Biome.TAIGA, Biome.JUNGLE,
                Biome.SAVANNA, Biome.BIRCH_FOREST, Biome.CHERRY_GROVE, Biome.FLOWER_FOREST, Biome.MEADOW,
                Biome.DARK_FOREST, Biome.SWAMP, Biome.MANGROVE_SWAMP, Biome.BADLANDS,
                Biome.GROVE, Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA, Biome.MUSHROOM_FIELDS,
                Biome.OLD_GROWTH_PINE_TAIGA, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.WINDSWEPT_FOREST, Biome.BAMBOO_JUNGLE,
                Biome.WINDSWEPT_HILLS, Biome.STONY_SHORE, Biome.BEACH, Biome.SNOWY_BEACH,
                Biome.STONY_PEAKS, Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.SNOWY_SLOPES,
                Biome.OLD_GROWTH_BIRCH_FOREST, Biome.SPARSE_JUNGLE, Biome.ICE_SPIKES, Biome.SUNFLOWER_PLAINS, Biome.WOODED_BADLANDS);
    }

    public static List<Biome> getAllowedNetherBiomes() {
        return List.of(Biome.NETHER_WASTES);
    }

    public static List<Biome> getAllowedEndBiomes() {
        return List.of(Biome.THE_END);
    }
}
