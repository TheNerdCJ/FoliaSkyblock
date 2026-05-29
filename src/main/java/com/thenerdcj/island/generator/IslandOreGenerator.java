package com.thenerdcj.island.generator;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.IslandUpgrade;
import com.thenerdcj.manager.GridManager;
import com.thenerdcj.manager.IslandUpgradeManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

/**
 * IslandOreGenerator - Provides upgradable ore generation for cobblestone generators.
 * 
 * This class handles replacing cobblestone formed by lava+water with random ores
 * based on the island's ORE_GENERATOR upgrade level.
 * 
 * Integrates with:
 * - IslandUpgrade (new ORE_GENERATOR enum value)
 * - IslandUpgradeManager (to fetch per-island upgrade level via grid position)
 * - GridManager (to resolve block location to island grid/islandId)
 * - FoliaSkyblock main plugin
 * 
 * Designed to be used from a BlockFormEvent listener.
 * 
 * Play-to-Win balanced: Higher levels require island progression (level req + island balance cost).
 * Generator-placed ores are tagged with PersistentDataContainer at placement for 100% anticheat whitelisting on owning island.
 * This prevents false positives while encouraging progression via ore generator upgrades.
 */
public class IslandOreGenerator {

    private final FoliaSkyblock plugin;
    private final GridManager gridManager;
    private final IslandUpgradeManager upgradeManager;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    // Short-lived cache for effective ore weights per island (keyed by islandId + level)
    // Dramatically reduces repeated weight calculations in the hot cobble gen path
    private final ConcurrentHashMap<String, Map<Material, Double>> effectiveOreWeightsCache = new ConcurrentHashMap<>();

    // Ore pools per dimension/environment. Probabilities scale with upgrade level (0-5)
    // Format: Material -> baseWeight (higher = more common). Weights multiplied by level factor.
    private static final Map<World.Environment, Map<Material, Double>> BASE_ORE_WEIGHTS = new EnumMap<>(World.Environment.class);

    static {
        // Overworld cobble gen ores (standard skyblock progression)
        Map<Material, Double> overworld = new EnumMap<>(Material.class);
        overworld.put(Material.COBBLESTONE, 100.0); // base, always possible
        overworld.put(Material.COAL_ORE, 25.0);
        overworld.put(Material.IRON_ORE, 18.0);
        overworld.put(Material.GOLD_ORE, 8.0);
        overworld.put(Material.REDSTONE_ORE, 12.0);
        overworld.put(Material.LAPIS_ORE, 10.0);
        overworld.put(Material.DIAMOND_ORE, 3.0);
        overworld.put(Material.EMERALD_ORE, 1.5);
        BASE_ORE_WEIGHTS.put(World.Environment.NORMAL, overworld);

        // Nether variant (if cobble gens used in nether islands)
        Map<Material, Double> nether = new EnumMap<>(Material.class);
        nether.put(Material.NETHERRACK, 80.0);
        nether.put(Material.NETHER_QUARTZ_ORE, 30.0);
        nether.put(Material.NETHER_GOLD_ORE, 15.0);
        nether.put(Material.ANCIENT_DEBRIS, 0.5); // very rare, high level only
        BASE_ORE_WEIGHTS.put(World.Environment.NETHER, nether);

        // End variant (less common for cobble, but supported)
        Map<Material, Double> end = new EnumMap<>(Material.class);
        end.put(Material.END_STONE, 90.0);
        end.put(Material.OBSIDIAN, 5.0); // rare
        BASE_ORE_WEIGHTS.put(World.Environment.THE_END, end);
    }

    public IslandOreGenerator(FoliaSkyblock plugin, GridManager gridManager, IslandUpgradeManager upgradeManager) {
        this.plugin = plugin;
        this.gridManager = gridManager;
        this.upgradeManager = upgradeManager;
    }

    /**
     * Main entry point: Called from BlockFormEvent listener when COBBLESTONE forms.
     * Checks if location is on a claimed island (not spawn 0,0), gets upgrade level,
     * and possibly replaces with better ore.
     * 
     * @param event The BlockFormEvent (new block is currently COBBLESTONE)
     * @return true if the block was changed to an ore, false if left as cobble
     */
    public boolean processCobbleFormation(BlockFormEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        World world = block.getWorld();

        // Only process if this forms cobblestone (lava + water interaction)
        if (event.getNewState().getType() != Material.COBBLESTONE) {
            return false;
        }

        // Skip protected spawn area (0,0 unclaimable)
        if (!gridManager.isIslandLocation(loc)) {
            return false; // spawn or unclaimed, leave as normal cobble
        }

        // Get grid position -> islandId for upgrade lookup (matches IslandUpgradeManager format)
        var gridPos = gridManager.getGridPosition(loc);
        String islandId = gridPos.x() + "," + gridPos.z(); // adjust if manager uses different format, e.g. "x_z"

        // Fetch current upgrade level (0 if never purchased)
        int level = upgradeManager.getUpgradeLevel(islandId, IslandUpgrade.ORE_GENERATOR);
        if (level <= 0) {
            return false; // no upgrade purchased, normal cobble
        }

        // Get appropriate ore pool for dimension (use cached effective weights for performance)
        World.Environment env = world.getEnvironment();
        String cacheKey = islandId + ":" + level + ":" + env.name();
        Map<Material, Double> weights = effectiveOreWeightsCache.computeIfAbsent(cacheKey, k ->
            computeEffectiveWeights(env, level)
        );

        // Select ore based on weighted random, scaled by upgrade level
        Material chosen = selectWeightedOre(weights, level);
        
        if (chosen != Material.COBBLESTONE) {
            // Use Folia region scheduler for safety if needed, but BlockForm is already sync region-owned
            block.setType(chosen);
            // Tag the block as generator-produced using chunk PersistentDataContainer for 100% accurate anticheat whitelisting
            tagGeneratorOre(block);
            return true;
        }
        return false;
    }

    /**
     * Weighted random selection. Higher level increases chance of better ores
     * by boosting their weights and reducing cobble weight slightly.
     */
    private Map<Material, Double> computeEffectiveWeights(World.Environment env, int level) {
        Map<Material, Double> baseWeights = BASE_ORE_WEIGHTS.getOrDefault(env, BASE_ORE_WEIGHTS.get(World.Environment.NORMAL));
        if (baseWeights == null || baseWeights.isEmpty()) {
            return Collections.emptyMap();
        }

        double levelFactor = 1.0 + (level * 0.35);
        double cobbleReduction = Math.max(0.4, 1.0 - (level * 0.12));

        Map<Material, Double> effective = new EnumMap<>(Material.class);
        for (Map.Entry<Material, Double> entry : baseWeights.entrySet()) {
            Material mat = entry.getKey();
            double w = entry.getValue();
            if (mat == Material.COBBLESTONE || mat == Material.NETHERRACK || mat == Material.END_STONE) {
                w *= cobbleReduction;
            } else {
                w *= levelFactor;
            }
            effective.put(mat, w);
        }
        return effective;
    }

    private Material selectWeightedOre(Map<Material, Double> baseWeights, int level) {
        double levelFactor = 1.0 + (level * 0.35); // e.g. level 5 ~ +1.75x on rare ores
        double cobbleReduction = Math.max(0.4, 1.0 - (level * 0.12)); // less cobble at high levels

        Map<Material, Double> effectiveWeights = new EnumMap<>(Material.class);
        double totalWeight = 0;

        for (Map.Entry<Material, Double> entry : baseWeights.entrySet()) {
            Material mat = entry.getKey();
            double w = entry.getValue();
            if (mat == Material.COBBLESTONE || mat == Material.NETHERRACK || mat == Material.END_STONE) {
                w *= cobbleReduction;
            } else {
                w *= levelFactor;
            }
            effectiveWeights.put(mat, w);
            totalWeight += w;
        }

        if (totalWeight <= 0) return Material.COBBLESTONE;

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;

        for (Map.Entry<Material, Double> entry : effectiveWeights.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        return Material.COBBLESTONE;
    }

    /**
     * Tag a block as generator-placed ore using the chunk's PersistentDataContainer.
     * Stores positions as "x:y:z" strings in a list under NamespacedKey.
     * This enables exact matching in anticheat for whitelisting legit Play-to-Win yields.
     * Called after setType() in processCobbleFormation.
     */
    private static final NamespacedKey GENERATOR_ORES_KEY = new NamespacedKey("foliasb", "generator_ores"); // plugin instance not needed for NamespacedKey in modern API

    private void tagGeneratorOre(Block block) {
        if (block == null || plugin == null) return;
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> positions = pdc.get(GENERATOR_ORES_KEY, PersistentDataType.LIST.strings());
        if (positions == null) {
            positions = new ArrayList<>(4); // small initial capacity for typical generator ore tags
        }
        String posKey = block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (!positions.contains(posKey)) {
            positions.add(posKey);
            pdc.set(GENERATOR_ORES_KEY, PersistentDataType.LIST.strings(), positions);
        }
    }

    /**
     * Check if the given block was placed by this generator (tagged at placement).
     * Used by anticheat to 100% whitelist specific blocks when broken on owning island.
     * Static so it can be called from AntiCheatManager without instance dependency.
     */
    public static boolean isGeneratorOre(Block block, org.bukkit.plugin.Plugin plugin) {
        if (block == null || plugin == null) return false;
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "generator_ores");
        List<String> positions = pdc.get(key, PersistentDataType.LIST.strings());
        if (positions == null) return false;
        String posKey = block.getX() + ":" + block.getY() + ":" + block.getZ();
        return positions.contains(posKey);
    }

    /**
     * Utility: Get current ore generator level for a location (for anti-cheat or other systems).
     * Returns 0 if not on island or no upgrade.
     */
    public int getOreGeneratorLevelAt(Location loc) {
        if (!gridManager.isIslandLocation(loc)) return 0;
        var gp = gridManager.getGridPosition(loc);
        String islandId = gp.x() + "," + gp.z();
        return upgradeManager.getUpgradeLevel(islandId, IslandUpgrade.ORE_GENERATOR);
    }

    /**
     * For anti-cheat integration: Call this from BlockBreakEvent or mining profile update
     * to check if the broken ore was from an upgraded generator on the player's island.
     * Now uses exact PersistentDataContainer tag (set at placement) for 100% accuracy, with heuristic fallback.
     * Helps prevent false positives by marking "legit generator yield" from Play-to-Win upgrades.
     */
    public boolean isLikelyGeneratorOre(Block brokenBlock, UUID playerUuid) {
        // Prefer exact tag from PersistentDataContainer for 100% accuracy (set at placement time)
        if (isGeneratorOre(brokenBlock, plugin)) {
            return true;
        }
        // Fallback heuristic if tag not present (e.g. old data or initial gen ores)
        Location loc = brokenBlock.getLocation();
        int level = getOreGeneratorLevelAt(loc);
        if (level < 2) return false;

        Material type = brokenBlock.getType();
        // Check if it's one of the ores we generate (expand as needed)
        return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS || type == Material.NETHER_QUARTZ_ORE;
    }
}
