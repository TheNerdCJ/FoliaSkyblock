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
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Upgradable cobblestone generator ore replacement (lava + water {@link BlockFormEvent}).
 * Always active on islands (level 0 = base small ore chances for common + deepslate variants).
 * Higher ORE_GENERATOR upgrade levels boost rarer ores significantly (tiered + non-linear "neural" style scaling).
 * Supports every main ore + deepslate variants + nether/end with proper chances.
 * Generator-placed ores are PDC-tagged for anti-cheat whitelisting on the owning island.
 * Uses weighted random selection (standard from Skyblock ore gen plugins) + island upgrade integration.
 */
public class IslandOreGenerator implements Listener {

    private final FoliaSkyblock plugin;
    private final GridManager gridManager;
    private final IslandUpgradeManager upgradeManager;
    private final NamespacedKey generatorOresKey;
    private final NamespacedKey generatorOreBlockKey;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    private final int maxOreWeightCacheEntries;
    private final ConcurrentHashMap<String, Map<Material, Double>> effectiveOreWeightsCache = new ConcurrentHashMap<>();

    private static final Map<World.Environment, Map<Material, Double>> BASE_ORE_WEIGHTS = new EnumMap<>(World.Environment.class);

    static {
        Map<Material, Double> overworld = new EnumMap<>(Material.class);
        overworld.put(Material.COBBLESTONE, 85.0);
        overworld.put(Material.COAL_ORE, 28.0);
        overworld.put(Material.COPPER_ORE, 24.0);
        overworld.put(Material.IRON_ORE, 20.0);
        overworld.put(Material.GOLD_ORE, 10.0);
        overworld.put(Material.REDSTONE_ORE, 13.0);
        overworld.put(Material.LAPIS_ORE, 9.0);
        overworld.put(Material.DIAMOND_ORE, 4.0);
        overworld.put(Material.EMERALD_ORE, 2.0);
        // Deepslate variants for every ore chance (players building low gens or depth)
        overworld.put(Material.DEEPSLATE_COAL_ORE, 12.0);
        overworld.put(Material.DEEPSLATE_COPPER_ORE, 10.0);
        overworld.put(Material.DEEPSLATE_IRON_ORE, 8.0);
        overworld.put(Material.DEEPSLATE_GOLD_ORE, 4.0);
        overworld.put(Material.DEEPSLATE_REDSTONE_ORE, 5.0);
        overworld.put(Material.DEEPSLATE_LAPIS_ORE, 3.5);
        overworld.put(Material.DEEPSLATE_DIAMOND_ORE, 1.8);
        overworld.put(Material.DEEPSLATE_EMERALD_ORE, 0.8);
        BASE_ORE_WEIGHTS.put(World.Environment.NORMAL, overworld);

        Map<Material, Double> nether = new EnumMap<>(Material.class);
        nether.put(Material.NETHERRACK, 70.0);
        nether.put(Material.NETHER_QUARTZ_ORE, 32.0);
        nether.put(Material.NETHER_GOLD_ORE, 16.0);
        nether.put(Material.ANCIENT_DEBRIS, 1.2);
        nether.put(Material.GILDED_BLACKSTONE, 5.0);
        nether.put(Material.BLACKSTONE, 15.0); // filler variety
        BASE_ORE_WEIGHTS.put(World.Environment.NETHER, nether);

        Map<Material, Double> end = new EnumMap<>(Material.class);
        end.put(Material.END_STONE, 85.0);
        end.put(Material.OBSIDIAN, 8.0);
        BASE_ORE_WEIGHTS.put(World.Environment.THE_END, end);
    }

    public IslandOreGenerator(FoliaSkyblock plugin, GridManager gridManager, IslandUpgradeManager upgradeManager) {
        this.plugin = plugin;
        this.gridManager = gridManager;
        this.upgradeManager = upgradeManager;
        this.generatorOresKey = new NamespacedKey(plugin, "generator_ores");
        this.generatorOreBlockKey = new NamespacedKey(plugin, "generator_ore");
        int cacheCap = 2048;
        if (plugin.getConfig() != null) {
            cacheCap = plugin.getConfig().getInt("island.perf.max-ore-weight-cache-entries", 2048);
        }
        this.maxOreWeightCacheEntries = Math.max(256, cacheCap);
    }

    /**
     * Drops stale weight entries after upgrade level changes (memory compression).
     */
    public void invalidateOreWeightsForIsland(String islandId) {
        if (islandId == null || islandId.isEmpty()) {
            return;
        }
        String prefix = islandId + ":";
        effectiveOreWeightsCache.keySet().removeIf(k -> k.startsWith(prefix));
        while (effectiveOreWeightsCache.size() > maxOreWeightCacheEntries) {
            String first = effectiveOreWeightsCache.keys().nextElement();
            effectiveOreWeightsCache.remove(first);
        }
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

        // Accept generator formation for cobble/netherrack/basalt/endstone (cross-dimension support)
        // Standard detection used by Skyblock ore generator plugins.
        Material formed = event.getNewState().getType();
        if (formed != Material.COBBLESTONE && formed != Material.NETHERRACK && formed != Material.BASALT && formed != Material.END_STONE) {
            return false;
        }

        if (!gridManager.isIslandLocation(loc)) {
            return false;
        }

        Island island = plugin.getIslandManager().getIslandAt(loc);
        if (island == null) {
            return false;
        }

        // Use the ID-based lookup for robustness (handles cold loads + cache + async DB fallback).
        // Direct island.getUpgradeLevel() can return 0 if loadUpgrades future hasn't completed yet.
        // Level 0 = base generator (small chance for common ores). Higher levels boost rare ores.
        int level = Math.max(0, upgradeManager.getUpgradeLevel(island.getId(), IslandUpgrade.ORE_GENERATOR));

        World.Environment env = world.getEnvironment();
        String cacheKey = island.getId() + ":" + level + ":" + env.name();
        Map<Material, Double> weights = effectiveOreWeightsCache.computeIfAbsent(cacheKey,
                k -> computeEffectiveWeights(env, level));

        Material chosen = selectWeightedOre(weights);

        // Apply depth-aware deepslate for "every ore" variety when generator places underground
        chosen = applyDepthVariantIfOre(chosen, block.getY());

        if (isBaseMaterial(chosen, env)) {
            // For dimension correctness: replace cobble-form with proper dim base (e.g. netherrack in nether)
            Material properBase = getBaseMaterialForEnv(env);
            if (properBase != null) {
                event.getNewState().setType(properBase);
            }
            return false; // base filler, not a special ore
        }

        // Properly replace via the forming state (more reliable than direct setType inside form event)
        event.getNewState().setType(chosen);

        // Tag the forming state so the placed block carries the generator ore PDC for anti-cheat
        if (event.getNewState() instanceof PersistentDataHolder holder) {
            holder.getPersistentDataContainer().set(generatorOreBlockKey, PersistentDataType.BYTE, (byte) 1);
        }

        // Also record in legacy chunk PDC for compatibility with isGeneratorOre checks
        tagGeneratorOreChunkLegacy(block);

        return true;
    }

    private Map<Material, Double> computeEffectiveWeights(World.Environment env, int level) {
        Map<Material, Double> baseWeights = BASE_ORE_WEIGHTS.getOrDefault(env, BASE_ORE_WEIGHTS.get(World.Environment.NORMAL));
        if (baseWeights == null || baseWeights.isEmpty()) {
            return Collections.emptyMap();
        }

        // Level scaling inspired by common Skyblock ore gens (e.g. tiered boosts from Hypixel/Skyblock plugins).
        // Base level 0 gives small but real chances for common ores (generator always active on islands).
        // Higher levels dramatically favor rarer ores (every ore gets a chance, weighted).
        // Non-linear "neural-style" boost (tanh activation) for nicer progression curve like our NeuralCheatDetector.
        double raw = level * 0.30;
        double levelFactor = 1.0 + raw * (1 + Math.tanh(raw * 0.8) * 0.4);
        double cobbleReduction = Math.max(0.25, 1.0 - (level * 0.09));

        Map<Material, Double> effective = new EnumMap<>(Material.class);
        for (Map.Entry<Material, Double> entry : baseWeights.entrySet()) {
            Material mat = entry.getKey();
            double w = entry.getValue();

            if (mat == Material.COBBLESTONE || mat == Material.NETHERRACK || mat == Material.END_STONE || mat == Material.BLACKSTONE) {
                w *= cobbleReduction;
            } else {
                // Tiered boost so every ore gets chance but rares scale up with upgrade level
                double tier = getOreTierMultiplier(mat);
                w *= (levelFactor * tier);
            }
            effective.put(mat, Math.max(0.1, w)); // prevent zero weights
        }
        return effective;
    }

    /**
     * Tier multipliers for ore rarity scaling with island ORE_GENERATOR upgrade level.
     * Common = steady increase, Rare = strong boost at high levels.
     * This ensures "every ore with a chance" and upgrade system has visible impact.
     */
    private double getOreTierMultiplier(Material mat) {
        String name = mat.name();
        if (name.contains("DEEPSLATE_") && (name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("ANCIENT"))) {
            return 1.0 + 0.9; // deep rare get extra love
        }
        if (name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("ANCIENT_DEBRIS")) {
            return 1.0 + 0.75; // very rare
        }
        if (name.contains("GOLD") || name.contains("REDSTONE") || name.contains("LAPIS") || name.contains("GILDED")) {
            return 1.0 + 0.45; // uncommon
        }
        if (name.contains("COAL") || name.contains("COPPER") || name.contains("IRON") || name.contains("QUARTZ")) {
            return 1.0 + 0.25; // common ores
        }
        return 1.0 + 0.15;
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

    private boolean isBaseMaterial(Material mat, World.Environment env) {
        if (mat == null) return true;
        if (mat == Material.COBBLESTONE || mat == Material.NETHERRACK || mat == Material.END_STONE || mat == Material.BLACKSTONE) {
            return true;
        }
        // Treat deepslate stone-like? no, deepslate_*_ore are ores
        return false;
    }

    private Material getBaseMaterialForEnv(World.Environment env) {
        return switch (env) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.COBBLESTONE;
        };
    }

    /**
     * Returns deepslate variant of an ore when appropriate (depth or random) so generator can produce
     * every ore variant. Standard technique from many Skyblock ore gen plugins.
     */
    private Material applyDepthVariantIfOre(Material ore, int y) {
        if (ore == null || !ore.name().endsWith("_ORE") || ore.name().startsWith("DEEPSLATE_") || ore.name().startsWith("NETHER_")) {
            return ore;
        }
        // Higher chance of deepslate the lower the Y (or always a % for variety)
        boolean shouldDeep = (y <= 0) || (random.nextDouble() < 0.35);
        if (shouldDeep) {
            try {
                Material deep = Material.valueOf("DEEPSLATE_" + ore.name());
                return deep;
            } catch (IllegalArgumentException ignored) {
                // no deep equivalent, keep original
            }
        }
        return ore;
    }

    private void tagGeneratorOre(Block block) {
        if (block == null) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof PersistentDataHolder holder) {
            holder.getPersistentDataContainer().set(generatorOreBlockKey, PersistentDataType.BYTE, (byte) 1);
            state.update(true, false);
            return;
        }
        tagGeneratorOreChunkLegacy(block);
    }

    private void tagGeneratorOreChunkLegacy(Block block) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> positions = pdc.get(generatorOresKey, PersistentDataType.LIST.strings());
        if (positions == null) {
            positions = new java.util.ArrayList<>(4);
        }
        String posKey = block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (!positions.contains(posKey)) {
            if (positions.size() >= 512) {
                positions.remove(0);
            }
            positions.add(posKey);
            pdc.set(generatorOresKey, PersistentDataType.LIST.strings(), positions);
        }
    }

    public static boolean isGeneratorOre(Block block, org.bukkit.plugin.Plugin plugin) {
        if (block == null || plugin == null) {
            return false;
        }
        NamespacedKey blockKey = new NamespacedKey(plugin, "generator_ore");
        NamespacedKey legacyBlockKey = new NamespacedKey("foliasb", "generator_ore");
        BlockState state = block.getState();
        if (state instanceof PersistentDataHolder holder) {
            PersistentDataContainer blockPdc = holder.getPersistentDataContainer();
            if (blockPdc.has(blockKey, PersistentDataType.BYTE)
                    || blockPdc.has(legacyBlockKey, PersistentDataType.BYTE)) {
                return true;
            }
        }
        NamespacedKey chunkKey = new NamespacedKey(plugin, "generator_ores");
        NamespacedKey legacyChunkKey = new NamespacedKey("foliasb", "generator_ores");
        Chunk chunk = block.getChunk();
        PersistentDataContainer chunkPdc = chunk.getPersistentDataContainer();
        String posKey = block.getX() + ":" + block.getY() + ":" + block.getZ();
        for (NamespacedKey k : List.of(chunkKey, legacyChunkKey)) {
            List<String> positions = chunkPdc.get(k, PersistentDataType.LIST.strings());
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
        if (island == null) return 0;
        // Use ID-based for same robustness as the formation path.
        return upgradeManager.getUpgradeLevel(island.getId(), IslandUpgrade.ORE_GENERATOR);
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