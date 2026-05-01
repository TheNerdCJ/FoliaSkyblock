package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;

/**
 * Comprehensive, balanced Island XP system.
 * Inspired by Hypixel Skyblock, Skyblock.net, and other top Skyblock servers.
 * All values are carefully balanced to prevent XP farming while rewarding progression.
 */
public class IslandXPManager {

    private final FoliaSkyblock plugin;

    // ==================== XP TABLES ====================
    private final Map<Material, Double> miningXP = new EnumMap<>(Material.class);
    private final Map<Material, Double> farmingXP = new EnumMap<>(Material.class);
    private final Map<Material, Double> foragingXP = new EnumMap<>(Material.class);
    private final Map<Material, Double> excavationXP = new EnumMap<>(Material.class);
    private final Map<EntityType, Double> combatXP = new EnumMap<>(EntityType.class);

    public IslandXPManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadBalancedXPValues();
    }

    private void loadBalancedXPValues() {
        // ==================== MINING (Ores + Stone) ====================
        miningXP.put(Material.STONE, 0.4);
        miningXP.put(Material.COBBLESTONE, 0.3);
        miningXP.put(Material.DEEPSLATE, 0.6);
        miningXP.put(Material.COAL_ORE, 2.0);
        miningXP.put(Material.IRON_ORE, 3.5);
        miningXP.put(Material.GOLD_ORE, 5.0);
        miningXP.put(Material.REDSTONE_ORE, 4.0);
        miningXP.put(Material.LAPIS_ORE, 4.5);
        miningXP.put(Material.DIAMOND_ORE, 12.0);
        miningXP.put(Material.EMERALD_ORE, 15.0);
        miningXP.put(Material.NETHERITE_SCRAP, 25.0);
        miningXP.put(Material.ANCIENT_DEBRIS, 22.0);
        miningXP.put(Material.NETHER_GOLD_ORE, 3.0);
        miningXP.put(Material.NETHER_QUARTZ_ORE, 2.5);

        // ==================== FARMING (Crops) ====================
        farmingXP.put(Material.WHEAT, 0.8);
        farmingXP.put(Material.CARROTS, 0.9);
        farmingXP.put(Material.POTATOES, 0.9);
        farmingXP.put(Material.BEETROOTS, 1.0);
        farmingXP.put(Material.MELON, 1.5);
        farmingXP.put(Material.PUMPKIN, 1.5);
        farmingXP.put(Material.SUGAR_CANE, 0.6);
        farmingXP.put(Material.CACTUS, 0.7);
        farmingXP.put(Material.BROWN_MUSHROOM, 1.2);
        farmingXP.put(Material.RED_MUSHROOM, 1.2);
        farmingXP.put(Material.NETHER_WART, 1.8);

        // ==================== FORAGING (Logs) ====================
        foragingXP.put(Material.OAK_LOG, 1.2);
        foragingXP.put(Material.BIRCH_LOG, 1.2);
        foragingXP.put(Material.SPRUCE_LOG, 1.2);
        foragingXP.put(Material.JUNGLE_LOG, 1.2);
        foragingXP.put(Material.ACACIA_LOG, 1.2);
        foragingXP.put(Material.DARK_OAK_LOG, 1.2);
        foragingXP.put(Material.MANGROVE_LOG, 1.2);
        foragingXP.put(Material.CHERRY_LOG, 1.2);
        foragingXP.put(Material.CRIMSON_STEM, 1.5);
        foragingXP.put(Material.WARPED_STEM, 1.5);

        // ==================== EXCAVATION (Dirt, Sand, Gravel) ====================
        excavationXP.put(Material.DIRT, 0.4);
        excavationXP.put(Material.SAND, 0.5);
        excavationXP.put(Material.GRAVEL, 0.6);
        excavationXP.put(Material.CLAY, 0.8);
        excavationXP.put(Material.SOUL_SAND, 0.7);
        excavationXP.put(Material.SOUL_SOIL, 0.7);
        excavationXP.put(Material.RED_SAND, 0.6);

        // ==================== COMBAT (Mobs) ====================
        combatXP.put(EntityType.ZOMBIE, 3.0);
        combatXP.put(EntityType.SKELETON, 3.5);
        combatXP.put(EntityType.SPIDER, 3.0);
        combatXP.put(EntityType.CREEPER, 5.0);
        combatXP.put(EntityType.ENDERMAN, 8.0);
        combatXP.put(EntityType.BLAZE, 6.0);
        combatXP.put(EntityType.GHAST, 7.0);
        combatXP.put(EntityType.WITHER_SKELETON, 10.0);
        combatXP.put(EntityType.PIGLIN_BRUTE, 12.0);
        combatXP.put(EntityType.PHANTOM, 10.0);
        combatXP.put(EntityType.DROWNED, 5.0);
        combatXP.put(EntityType.HUSK, 4.5);
        combatXP.put(EntityType.STRAY, 5.5);
        combatXP.put(EntityType.WITCH, 9.0);
        combatXP.put(EntityType.PILLAGER, 8.0);
        combatXP.put(EntityType.VINDICATOR, 10.0);
        combatXP.put(EntityType.RAVAGER, 25.0);
        combatXP.put(EntityType.EVOKER, 22.0);
        combatXP.put(EntityType.ELDER_GUARDIAN, 35.0);
        combatXP.put(EntityType.WARDEN, 50.0);
        combatXP.put(EntityType.ENDER_DRAGON, 100.0);
        combatXP.put(EntityType.WITHER, 80.0);
    }

    // ==================== PUBLIC AWARD METHODS ====================

    public void awardMiningXP(Player player, Material material) {
        double xp = miningXP.getOrDefault(material, 0.3);
        addXpToIsland(player, xp, "Mining");
    }

    public void awardFarmingXP(Player player, Material material) {
        double xp = farmingXP.getOrDefault(material, 0.8);
        addXpToIsland(player, xp, "Farming");
    }

    public void awardForagingXP(Player player, Material material) {
        double xp = foragingXP.getOrDefault(material, 1.2);
        addXpToIsland(player, xp, "Foraging");
    }

    public void awardExcavationXP(Player player, Material material) {
        double xp = excavationXP.getOrDefault(material, 0.4);
        addXpToIsland(player, xp, "Excavation");
    }

    public void awardCombatXP(Player player, EntityType entityType) {
        double xp = combatXP.getOrDefault(entityType, 3.0);
        addXpToIsland(player, xp, "Combat");
    }

    public void awardFishingXP(Player player, double baseXp) {
        addXpToIsland(player, baseXp, "Fishing");
    }

    public void awardSpecialActionXP(Player player, double baseXp, String actionName) {
        addXpToIsland(player, baseXp, actionName);
    }

    // ==================== CORE XP METHOD ====================
    private void addXpToIsland(Player player, double baseXp, String source) {
        if (player == null || baseXp <= 0) return;

        Island island = plugin.getIslandManager().getIsland(
                player.getUniqueId(),
                player.getWorld().getEnvironment()
        );

        if (island == null) return;

        int partySize = island.getMemberCount();
        island.addXp(baseXp, partySize);

        // Optional action bar message
        if (plugin.getConfig().getBoolean("xp.show-gain-messages", true)) {
            player.sendActionBar("§a+" + String.format("%.1f", baseXp) + " XP §7(" + source + ")");
        }
    }

    // ==================== GETTERS ====================
    public double getMiningXP(Material material) { return miningXP.getOrDefault(material, 0.3); }
    public double getFarmingXP(Material material) { return farmingXP.getOrDefault(material, 0.8); }
    public double getForagingXP(Material material) { return foragingXP.getOrDefault(material, 1.2); }
    public double getExcavationXP(Material material) { return excavationXP.getOrDefault(material, 0.4); }
    public double getCombatXP(EntityType entityType) { return combatXP.getOrDefault(entityType, 3.0); }
}
