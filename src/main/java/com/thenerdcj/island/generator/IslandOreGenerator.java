package com.thenerdcj.island.generator;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandUpgrade;
import com.thenerdcj.manager.GridManager;
import com.thenerdcj.manager.IslandUpgradeManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Upgradable cobblestone generator ore replacement (lava + water {@link BlockFormEvent}).
 * Generator-placed ores are PDC-tagged for anti-cheat whitelisting on the owning island.
 */
public class IslandOreGenerator implements Listener {

    private final FoliaSkyblock plugin;
    private final GridManager gridManager;
    private final IslandUpgradeManager upgradeManager;
    private final NamespacedKey generatorOresKey;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    private final ConcurrentHashMap<String, Map<Material, Double>> effectiveOreWeightsCache = new ConcurrentHashMap<>();

    private static final Map<World.Environment, Map<Material, Double>> BASE_ORE_WEIGHTS = new EnumMap<>(World.Environment.class);

    static {
        Map<Material, Double> overworld = new EnumMap<>(Material.class);
        overworld.put(Material.COBBLESTONE, 100.0);
        overworld.put(Material.COAL_ORE, 25.0);
        overworld.put(Material.IRON_ORE, 18.0);
        overworld.put(Material.GOLD_ORE, 8.0);
        overworld.put(Material.REDSTONE_ORE, 12.0);
        overworld.put(Material.LAPIS_ORE, 10.0);
        overworld.put(Material.DIAMOND_ORE, 3.0);
        overworld.put(Material.EMERALD_ORE, 1.5);
        BASE_ORE_WEIGHTS.put(World.Environment.NORMAL, overworld);

        Map<Material, Double> nether = new EnumMap<>(Material.class);
        nether.put(Material.NETHERRACK, 80.0);
        nether.put(Material.NETHER_QUARTZ_ORE, 30.0);
        nether.put(Material.NETHER_GOLD_ORE, 15.0);
        nether.put(Material.ANCIENT_DEBRIS, 0.5);
        BASE_ORE_WEIGHTS.put(World.Environment.NETHER, nether);

        Map<Material, Double> end = new EnumMap<>(Material.class);
        end.put(Material.END_STONE, 90.0);
        end.put(Material.OBSIDIAN, 5.0);
        BASE_ORE_WEIGHTS.put(World.Environment.THE_END, end);
    }

    public IslandOreGenerator(FoliaSkyblock plugin, GridManager gridManager, IslandUpgradeManager upgradeManager) {
        this.plugin = plugin;
        this.gridManager = gridManager;
        this.upgradeManager = upgradeManager;
        this.generatorOresKey = new NamespacedKey(plugin, "generator_ores");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCobbleForm(BlockFormEvent event) {
        processCobbleFormation(event);
    }

    /**
     * @return true if the block was changed to an ore, false if left as cobble
     */
    public boolean processCobbleFormation(BlockFormEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        World world = block.getWorld();

        if (event.getNewState().getType() != Material.COBBLESTONE) {
            return false;
        }

        if (!gridManager.isIslandLocation(loc)) {
            return false;
        }

        Island island = plugin.getIslandManager().getIslandAt(loc);
        if (island == null) {
            return false;
        }

        int level = upgradeManager.getUpgradeLevel(island, IslandUpgrade.ORE_GENERATOR);
        if (level <= 0) {
            return false;
        }

        World.Environment env = world.getEnvironment();
        String cacheKey = island.getId() + ":" + level + ":" + env.name();
        Map<Material, Double> weights = effectiveOreWeightsCache.computeIfAbsent(cacheKey,
                k -> computeEffectiveWeights(env, level));

        Material chosen = selectWeightedOre(weights);
        if (chosen == Material.COBBLESTONE || chosen == Material.NETHERRACK || chosen == Material.END_STONE) {
            return false;
        }

        block.setType(chosen);
        tagGeneratorOre(block);
        return true;
    }

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

    private Material selectWeightedOre(Map<Material, Double> weights) {
        if (weights == null || weights.isEmpty()) {
            return Material.COBBLESTONE;
        }

        double totalWeight = 0;
        for (double w : weights.values()) {
            totalWeight += w;
        }
        if (totalWeight <= 0) {
            return Material.COBBLESTONE;
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (Map.Entry<Material, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        return Material.COBBLESTONE;
    }

    private void tagGeneratorOre(Block block) {
        if (block == null) {
            return;
        }
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> positions = pdc.get(generatorOresKey, PersistentDataType.LIST.strings());
        if (positions == null) {
            positions = new ArrayList<>(4);
        }
        String posKey = block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (!positions.contains(posKey)) {
            positions.add(posKey);
            pdc.set(generatorOresKey, PersistentDataType.LIST.strings(), positions);
        }
    }

    public static boolean isGeneratorOre(Block block, org.bukkit.plugin.Plugin plugin) {
        if (block == null || plugin == null) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(plugin, "generator_ores");
        // Legacy tags used hardcoded namespace "foliasb"
        NamespacedKey legacyKey = new NamespacedKey("foliasb", "generator_ores");
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String posKey = block.getX() + ":" + block.getY() + ":" + block.getZ();
        for (NamespacedKey k : List.of(key, legacyKey)) {
            List<String> positions = pdc.get(k, PersistentDataType.LIST.strings());
            if (positions != null && positions.contains(posKey)) {
                return true;
            }
        }
        return false;
    }

    public int getOreGeneratorLevelAt(Location loc) {
        if (!gridManager.isIslandLocation(loc)) {
            return 0;
        }
        Island island = plugin.getIslandManager().getIslandAt(loc);
        return upgradeManager.getUpgradeLevel(island, IslandUpgrade.ORE_GENERATOR);
    }

    public boolean isLikelyGeneratorOre(Block brokenBlock, UUID playerUuid) {
        if (isGeneratorOre(brokenBlock, plugin)) {
            return true;
        }
        Location loc = brokenBlock.getLocation();
        int level = getOreGeneratorLevelAt(loc);
        if (level < 2) {
            return false;
        }

        Material type = brokenBlock.getType();
        return type.name().endsWith("_ORE")
                || type == Material.ANCIENT_DEBRIS
                || type == Material.NETHER_QUARTZ_ORE;
    }
}