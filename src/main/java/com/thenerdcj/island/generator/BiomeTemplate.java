package com.thenerdcj.island.generator;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.*;

/**
 * Defines block palettes and generation rules for each biome.
 * This allows easy customization and extension for new biomes.
 */
public class BiomeTemplate {

    private final Biome biome;
    private final String displayName;
    private final Material baseBlock;
    private final Material surfaceBlock;
    private final Material oreBlock;
    private final Material treeLog;
    private final Material treeLeaves;
    private final Material specialBlock;
    private final List<Material> allowedOres;
    private final double treeChance;
    private final double oreChance;

    public BiomeTemplate(Biome biome, String displayName,
                         Material baseBlock, Material surfaceBlock,
                         Material oreBlock, Material treeLog, Material treeLeaves,
                         Material specialBlock, List<Material> allowedOres,
                         double treeChance, double oreChance) {
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
    }

    // ==================== GETTERS ====================
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

    // ==================== PREDEFINED BIOME TEMPLATES ====================

    public static final BiomeTemplate PLAINS = new BiomeTemplate(
            Biome.PLAINS, "Plains",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE),
            0.35, 0.08
    );

    public static final BiomeTemplate FOREST = new BiomeTemplate(
            Biome.FOREST, "Forest",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.COAL_ORE, Material.OAK_LOG, Material.OAK_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE),
            0.65, 0.10
    );

    public static final BiomeTemplate DESERT = new BiomeTemplate(
            Biome.DESERT, "Desert",
            Material.SANDSTONE, Material.SAND,
            Material.GOLD_ORE, null, null,
            Material.SAND, List.of(Material.GOLD_ORE, Material.IRON_ORE),
            0.0, 0.12
    );

    public static final BiomeTemplate TAIGA = new BiomeTemplate(
            Biome.TAIGA, "Taiga",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.IRON_ORE, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.IRON_ORE, Material.COAL_ORE),
            0.55, 0.09
    );

    public static final BiomeTemplate JUNGLE = new BiomeTemplate(
            Biome.JUNGLE, "Jungle",
            Material.DIRT, Material.GRASS_BLOCK,
            Material.EMERALD_ORE, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES,
            Material.GRASS_BLOCK, List.of(Material.EMERALD_ORE, Material.DIAMOND_ORE),
            0.75, 0.07
    );

    public static final BiomeTemplate NETHER = new BiomeTemplate(
            Biome.NETHER_WASTES, "Nether",
            Material.NETHERRACK, Material.NETHERRACK,
            Material.NETHER_QUARTZ_ORE, null, null,
            Material.SOUL_SAND, List.of(Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS),
            0.0, 0.15
    );

    public static final BiomeTemplate END = new BiomeTemplate(
            Biome.THE_END, "End",
            Material.END_STONE, Material.END_STONE,
            Material.OBSIDIAN, null, null,
            Material.END_STONE, List.of(Material.OBSIDIAN),
            0.0, 0.05
    );

    // ==================== BIOME REGISTRY ====================

    private static final Map<Biome, BiomeTemplate> BIOME_REGISTRY = new EnumMap<>(Biome.class);

    static {
        BIOME_REGISTRY.put(Biome.PLAINS, PLAINS);
        BIOME_REGISTRY.put(Biome.FOREST, FOREST);
        BIOME_REGISTRY.put(Biome.DESERT, DESERT);
        BIOME_REGISTRY.put(Biome.TAIGA, TAIGA);
        BIOME_REGISTRY.put(Biome.JUNGLE, JUNGLE);
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
        return List.of(Biome.PLAINS, Biome.FOREST, Biome.DESERT, Biome.TAIGA, Biome.JUNGLE);
    }

    public static List<Biome> getAllowedNetherBiomes() {
        return List.of(Biome.NETHER_WASTES);
    }

    public static List<Biome> getAllowedEndBiomes() {
        return List.of(Biome.THE_END);
    }
}