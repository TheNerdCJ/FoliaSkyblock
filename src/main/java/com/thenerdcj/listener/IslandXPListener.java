package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * IslandXPListener - Comprehensive XP source listener for island progression.
 * 
 * Expanded with sources referenced from:
 * - Hypixel Skyblock Skills (Mining, Farming, Foraging, Fishing, Combat, Crafting/Carpentry, Enchanting, Alchemy, Taming)
 * - IridiumSkyblock (block value based leveling, missions, generators)
 * - SuperiorSkyblock2 (detailed action XP: spawners, minions, crops, ores, mobs, fishing, crafting)
 * - BentoBox (addon level system from various island activities)
 * 
 * All XP contributes to ISLAND level (not player), designed for dimension unlocks & boss fights.
 * Party XP is balanced: all members contribute but with shared pool and anti-boost measures.
 * 
 * Play to Win: All sources are grind-based, fair for solo/multiplayer parties. No donor XP boosts.
 * AntiCheat integration prevents XP exploits (macros, dupes, afk farms beyond limits).
 * 
 * Folia compatible: Listener runs in region thread; heavy DB saves are async via managers.
 */
public class IslandXPListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandManager islandManager;

    // XP values per action - balanced for progression (tuned to reach dim unlocks at reasonable playtime)
    // References: Hypixel ~100-500xp per action scaled, Iridium block values ~1-100 per block
    private final Map<Material, Double> blockBreakXP = new EnumMap<>(Material.class);
    private final Map<Material, Double> blockPlaceXP = new EnumMap<>(Material.class);
    private final Map<EntityType, Double> mobKillXP = new EnumMap<>(EntityType.class);
    private final Map<Material, Double> craftXP = new EnumMap<>(Material.class);

    public IslandXPListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
        registerXPValues();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void registerXPValues() {
        // === MINING / BLOCK BREAK (core for early progression, like Hypixel Mining skill + Iridium block values) ===
        blockBreakXP.put(Material.STONE, 1.0);
        blockBreakXP.put(Material.COBBLESTONE, 0.5);
        blockBreakXP.put(Material.DEEPSLATE, 1.5);
        blockBreakXP.put(Material.COAL_ORE, 8.0);
        blockBreakXP.put(Material.DEEPSLATE_COAL_ORE, 10.0);
        blockBreakXP.put(Material.IRON_ORE, 15.0);
        blockBreakXP.put(Material.DEEPSLATE_IRON_ORE, 18.0);
        blockBreakXP.put(Material.GOLD_ORE, 25.0);
        blockBreakXP.put(Material.DEEPSLATE_GOLD_ORE, 30.0);
        blockBreakXP.put(Material.REDSTONE_ORE, 12.0);
        blockBreakXP.put(Material.LAPIS_ORE, 20.0);
        blockBreakXP.put(Material.DIAMOND_ORE, 50.0);
        blockBreakXP.put(Material.DEEPSLATE_DIAMOND_ORE, 60.0);
        blockBreakXP.put(Material.EMERALD_ORE, 40.0);
        blockBreakXP.put(Material.NETHER_GOLD_ORE, 22.0);
        blockBreakXP.put(Material.ANCIENT_DEBRIS, 100.0); // Endgame mining
        blockBreakXP.put(Material.OBSIDIAN, 15.0);

        // === FARMING (crops, like Hypixel Farming + Iridium/Superior crop XP) ===
        blockBreakXP.put(Material.WHEAT, 2.0);
        blockBreakXP.put(Material.CARROTS, 2.5);
        blockBreakXP.put(Material.POTATOES, 2.5);
        blockBreakXP.put(Material.BEETROOTS, 3.0);
        blockBreakXP.put(Material.PUMPKIN, 4.0);
        blockBreakXP.put(Material.MELON, 4.0);
        blockBreakXP.put(Material.SUGAR_CANE, 1.5);
        blockBreakXP.put(Material.CACTUS, 1.5);
        blockBreakXP.put(Material.BAMBOO, 1.0);
        blockBreakXP.put(Material.NETHER_WART, 3.5);
        blockBreakXP.put(Material.COCOA, 3.0);

        // === FORAGING / WOOD (Hypixel Foraging skill) ===
        blockBreakXP.put(Material.OAK_LOG, 3.0);
        blockBreakXP.put(Material.BIRCH_LOG, 3.0);
        blockBreakXP.put(Material.SPRUCE_LOG, 3.0);
        blockBreakXP.put(Material.JUNGLE_LOG, 3.5);
        blockBreakXP.put(Material.ACACIA_LOG, 3.0);
        blockBreakXP.put(Material.DARK_OAK_LOG, 3.0);
        blockBreakXP.put(Material.MANGROVE_LOG, 3.5);
        blockBreakXP.put(Material.CHERRY_LOG, 4.0);
        blockBreakXP.put(Material.CRIMSON_STEM, 4.0);
        blockBreakXP.put(Material.WARPED_STEM, 4.0);

        // === BLOCK PLACE (building XP, lower to encourage exploration over creative spam - Iridium style) ===
        blockPlaceXP.put(Material.STONE_BRICKS, 0.5);
        blockPlaceXP.put(Material.OAK_PLANKS, 0.3);
        blockPlaceXP.put(Material.GLASS, 0.2);
        // Only valuable building blocks give meaningful XP

        // === MOB KILLS / COMBAT (Hypixel Combat + Slayer reference) ===
        mobKillXP.put(EntityType.ZOMBIE, 5.0);
        mobKillXP.put(EntityType.SKELETON, 6.0);
        mobKillXP.put(EntityType.SPIDER, 5.5);
        mobKillXP.put(EntityType.CREEPER, 8.0);
        mobKillXP.put(EntityType.ENDERMAN, 15.0);
        mobKillXP.put(EntityType.BLAZE, 12.0);
        mobKillXP.put(EntityType.GHAST, 20.0);
        mobKillXP.put(EntityType.WITHER_SKELETON, 18.0);
        mobKillXP.put(EntityType.PIGLIN, 7.0);
        mobKillXP.put(EntityType.HOGLIN, 10.0);
        mobKillXP.put(EntityType.ELDER_GUARDIAN, 50.0);
        mobKillXP.put(EntityType.WARDEN, 150.0);
        mobKillXP.put(EntityType.ENDER_DRAGON, 500.0);
        mobKillXP.put(EntityType.WITHER, 300.0);
        // Slayer mobs get bonus in slayer system

        // === CRAFTING (Hypixel Carpentry + crafting XP in Superior) ===
        craftXP.put(Material.DIAMOND_SWORD, 25.0);
        craftXP.put(Material.DIAMOND_PICKAXE, 20.0);
        craftXP.put(Material.DIAMOND_AXE, 15.0);
        craftXP.put(Material.IRON_HELMET, 12.0);
        craftXP.put(Material.ENCHANTING_TABLE, 40.0);
        craftXP.put(Material.BREWING_STAND, 30.0);
        craftXP.put(Material.ENDER_CHEST, 35.0);
        craftXP.put(Material.SHULKER_BOX, 50.0); // Endgame craft
        craftXP.put(Material.BEACON, 100.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        // AntiCheat check first (prevents macro/X-ray/XP dupes)
        if (plugin.getAntiCheatManager().isFlaggedForXPExploit(player, event.getBlock().getType())) {
            return; // Silently deny XP for flagged
        }

        Island island = getPlayerIsland(player);
        if (island == null) return;

        Block block = event.getBlock();
        double xp = blockBreakXP.getOrDefault(block.getType(), 0.5); // Default low XP for unknown

        // Bonus for silk touch rare blocks or fortune (but no overfarm)
        if (event.getPlayer().getInventory().getItemInMainHand().containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) {
            xp *= 0.7; // Silk touch less XP (gathering not breaking value)
        }

        // Party balance: if island has members, slight XP share but main player gets full (prevents boost abuse)
        if (island.getMemberCount() > 1) {
            xp *= 0.85; // 15% reduction for party play to keep solo viable (as per spec balanced party XP)
        }

        addIslandXP(island, xp, player, "§7Block Break: " + block.getType().name().toLowerCase().replace('_', ' '));

        // Core collection tracking (first discovery on this island)
        if (plugin.getCollectionManager() != null) {
            plugin.getCollectionManager().discoverBlock(island.getId(), block.getType(), player.getUniqueId(), player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Island island = getPlayerIsland(player);
        if (island == null) return;

        double xp = blockPlaceXP.getOrDefault(event.getBlock().getType(), 0.1);
        if (xp > 0.1) {
            addIslandXP(island, xp, player, "§7Building: " + event.getBlock().getType().name().toLowerCase().replace('_', ' '));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player player = event.getEntity().getKiller();
        Island island = getPlayerIsland(player);
        if (island == null) return;

        EntityType type = event.getEntityType();
        double xp = mobKillXP.getOrDefault(type, 3.0);

        // Slayer bonus XP (integrated with slayer tiers)
        if (isSlayerMob(type)) {
            xp *= 1.5; // Bonus for slayer progression
            // Could trigger slayer progress here or in separate SlayerListener
        }

        // Party XP balance
        if (island.getMemberCount() > 1) xp *= 0.9;

        // Apply ISLAND_XP booster
        if (plugin.getBoosterManager() != null) {
            xp *= plugin.getBoosterManager().getBoosterMultiplier(island, com.thenerdcj.booster.BoosterType.ISLAND_XP);
        }

        // Apply Prestige multiplier (layers on top of everything)
        if (plugin.getPrestigeManager() != null) {
            xp *= plugin.getPrestigeManager().getPrestigeMultiplier(island, com.thenerdcj.manager.PrestigeManager.PrestigeMultiplierType.XP);
        }

        addIslandXP(island, xp, player, "§7Mob Kill: " + type.name().toLowerCase().replace('_', ' '));
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH && event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        Player player = event.getPlayer();
        Island island = getPlayerIsland(player);
        if (island == null) return;

        double xp = 10.0; // Base fishing XP (Hypixel Fishing skill reference)
        if (event.getCaught() instanceof org.bukkit.entity.Item caughtItem && caughtItem.getItemStack().getType() == Material.COD) {
            xp = 8.0;
        } else if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            xp = 25.0; // Treasure or mob
        }

        addIslandXP(island, xp, player, "§7Fishing");
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Island island = getPlayerIsland(player);
        if (island == null) return;

        ItemStack result = event.getRecipe().getResult();
        double xp = craftXP.getOrDefault(result.getType(), 5.0);

        // Scale with amount crafted (batch)
        xp *= result.getAmount();

        addIslandXP(island, xp, player, "§7Crafting: " + result.getType().name().toLowerCase().replace('_', ' '));
    }

    // Helper: Get or create island for player (communicates with IslandManager)
    private Island getPlayerIsland(Player player) {
        if (player == null) return null;
        Island island = islandManager.getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            // Fallback: try owner lookup or create basic (but usually player should have island)
            island = islandManager.getIslandByOwner(player.getUniqueId(), player.getWorld().getEnvironment());
        }
        return island;
    }

    private boolean isSlayerMob(EntityType type) {
        return type == EntityType.ZOMBIE || type == EntityType.SPIDER || type == EntityType.ENDERMAN || type == EntityType.BLAZE || type == EntityType.WOLF;
    }

    // Core method: Add XP, handle level up, dimension unlock checks, save async where possible
    // Communicates with: Island (addXp, getLevel, levelUp), DatabaseManager (via IslandManager), BossManager/Dimension for unlocks
    private void addIslandXP(Island island, double xp, Player player, String source) {
        if (island == null || xp <= 0) return;

        // Apply Prestige XP multiplier (affects everything)
        if (plugin.getPrestigeManager() != null) {
            xp *= plugin.getPrestigeManager().getPrestigeMultiplier(island, com.thenerdcj.manager.PrestigeManager.PrestigeMultiplierType.XP);
        }

        double currentXP = island.getXp();
        int currentLevel = island.getLevel();
        island.addXp(xp); // Assumes Island has this method that also marks dirty for save

        // Level up check
        int newLevel = island.getLevel();
        if (newLevel > currentLevel) {
            // Notify all island members (the crafter/actor is included if they are a member/owner).
            // Removed redundant explicit send to 'player' which was causing duplicate messages for the player triggering the XP.
            String levelUpMsg = "§a§lISLAND LEVEL UP! §7Your island reached Level §e" + newLevel + "§7! (+" + String.format("%.1f", xp) + " XP from " + source + ")";
            island.getOnlineMembers().forEach(member -> {
                if (member.isOnline()) {
                    member.getScheduler().run(plugin, t -> member.sendMessage(levelUpMsg), null);
                }
            });

            // Check for dimension unlocks / boss access (communicates with BossManager / DimensionIslandListener)
            checkDimensionUnlocks(island, player, newLevel);

            // Play to Win: Level ups give fair perks, no paywall
            if (newLevel % 10 == 0) {
                plugin.getEconomyManager().addIslandBalance(island.getGridPosition(), 500.0 * (newLevel / 10)); // Bonus island balance on milestones
            }
        } else {
            // Occasional XP gain message (not spammy)
            if (Math.random() < 0.05) {
                MessageUtil.sendActionBar(player, "§7+" + String.format("%.1f", xp) + " Island XP §8(" + source + ")");
            }
        }

        // AntiCheat: Report high XP gains for monitoring
        if (xp > 100) {
            plugin.getAntiCheatManager().reportHighXPGain(player, xp, source);
        }
    }

    private void checkDimensionUnlocks(Island island, Player player, int level) {
        // References spec: leveling encourages progression to dimensions & bosses
        // Communicates with WorldManager / DimensionIslandListener / BossManager
        if (level >= 25 && !island.hasUnlockedNether()) {
            island.unlockDimension("Nether");
            player.sendMessage("§5§lDIMENSION UNLOCKED: §dThe Nether §7- Access your dimension island with /is warp nether");
            // Could auto create dimension island here via IslandManager
        }
        if (level >= 51 && !island.hasUnlockedEnd()) {
            island.unlockDimension("End");
            player.sendMessage("§5§lENDGAME UNLOCKED: §dThe End §7- Defeat the Ender Dragon and endgame bosses!");
        }
        // Slayer tier unlocks based on level/combat XP
        if (level >= 10) {
            // Unlock basic slayer
        }
    }
}