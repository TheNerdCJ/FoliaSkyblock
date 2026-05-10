package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * IslandXPListener - Awards XP to islands for various in-game actions.
 * 
 * XP is automatically balanced by party size via IslandManager.addIslandXp()
 * (solo players receive full XP; larger parties receive diminishing returns
 *  to keep progression fair between solo and group play).
 * 
 * Extend this class or add more @EventHandler methods for custom XP sources
 * (e.g. farming specific crops, completing challenges, boss kills, etc.).
 */
public class IslandXPListener implements Listener {

    private final FoliaSkyblock plugin;

    public IslandXPListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Block breaking XP (mining, farming, woodcutting, etc.)
     * Gives small amounts for most blocks, higher for ores and valuable resources.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        Material type = event.getBlock().getType();
        double xp = getXpForBlock(type);

        if (xp > 0) {
            plugin.getIslandManager().addIslandXp(player, xp);
        }
    }

    private double getXpForBlock(Material material) {
        return switch (material) {
            // Ores - high value
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 25.0;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 20.0;
            case ANCIENT_DEBRIS -> 50.0;
            case NETHERITE_BLOCK -> 100.0; // rare crafted
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> 8.0;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 5.0;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE, LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 4.0;
            case COAL_ORE, DEEPSLATE_COAL_ORE, COPPER_ORE, DEEPSLATE_COPPER_ORE -> 2.0;

            // Stone / basic mining
            case STONE, DEEPSLATE, COBBLESTONE, GRANITE, DIORITE, ANDESITE -> 0.5;
            case NETHERRACK, END_STONE -> 0.3;

            // Wood / farming
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG,
                 MANGROVE_LOG, CHERRY_LOG, CRIMSON_STEM, WARPED_STEM -> 1.5;
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, SUGAR_CANE, CACTUS,
                 PUMPKIN, MELON, BAMBOO -> 1.0;
            case OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES, ACACIA_LEAVES,
                 DARK_OAK_LEAVES, MANGROVE_LEAVES, CHERRY_LEAVES -> 0.2; // leaves optional

            default -> 0.0; // no XP for most other blocks (prevents spam from building)
        };
    }

    /**
     * Mob kill XP (good for slayer / combat progression)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player player)) return;
        if (!player.isOnline()) return;

        // Base XP per mob kill (can be expanded with entity type checks)
        double xp = 8.0;

        // Bonus for certain dangerous mobs
        String entityName = event.getEntity().getType().name();
        if (entityName.contains("BLAZE") || entityName.contains("WITHER") || entityName.contains("ENDER_DRAGON")
                || entityName.contains("ELDER_GUARDIAN") || entityName.contains("WARDEN")) {
            xp = 50.0;
        } else if (entityName.contains("ZOMBIE") || entityName.contains("SKELETON") || entityName.contains("CREEPER")
                || entityName.contains("SPIDER") || entityName.contains("ENDERMAN")) {
            xp = 10.0;
        }

        plugin.getIslandManager().addIslandXp(player, xp);
    }

    /**
     * Fishing XP
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        double xp = 3.0;

        // Bonus for treasure (enchanted books, etc.)
        // getCaught() returns Entity (specifically org.bukkit.entity.Item for caught items)
        if (event.getCaught() instanceof Item caughtItem) {
            ItemStack caughtStack = caughtItem.getItemStack();
            if (caughtStack != null && (caughtStack.getType() == Material.ENCHANTED_BOOK || caughtStack.getType() == Material.NAME_TAG
                    || caughtStack.getType() == Material.NAUTILUS_SHELL || caughtStack.getType() == Material.HEART_OF_THE_SEA)) {
                xp = 15.0;
            }
        }

        plugin.getIslandManager().addIslandXp(player, xp);
    }

    // ====================== FUTURE EXTENSIONS ======================
    // You can add:
    // - PlayerInteractEvent for special island actions
    // - BlockPlaceEvent (with cooldown to avoid building spam)
    // - Custom events from challenges/bosses
    // - Crop growth / harvest tracking
    //
    // Example:
    // @EventHandler
    // public void onCustomAction(CustomXPEvent e) {
    //     plugin.getIslandManager().addIslandXp(e.getPlayer(), e.getXpAmount());
    // }
}