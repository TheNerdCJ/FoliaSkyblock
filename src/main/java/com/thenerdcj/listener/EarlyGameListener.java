package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.quest.Quest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * EarlyGameListener - Safe progress hooks for the early-game / onboarding quest system.
 *
 * Purpose: Balance the now-rich late-game (skills, collections, housing, cosmetics, enchants)
 * by giving new players clear, rewarding first steps on their island.
 *
 * Design:
 * - Uses QuestManager's FIRST type quests (seeded on island create).
 * - Strictly anti-cheat guarded (isFlaggedForXPExploit + MONITOR like IslandXP/Skill).
 * - No modifications to core anti-cheat, island XP, or skill systems (post-process only).
 * - Folia compatible (events are main-thread; progress is memory only).
 * - Works alongside ChallengeProgressListener (missions) and SkillListener (no conflicts).
 *
 * First quests target=1 or low; completing them gives light rewards + free starter cosmetic.
 */
public class EarlyGameListener implements Listener {

    private final FoliaSkyblock plugin;

    public EarlyGameListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;

        // Anti-cheat guard first (identical pattern to IslandXPListener / PlayerSkillManager)
        // Use both XP and specific quest exploit for early game balance protection
        if (plugin.getAntiCheatManager() != null &&
            (plugin.getAntiCheatManager().isFlaggedForXPExploit(player, event.getBlock().getType()) ||
             plugin.getAntiCheatManager().isFlaggedForQuestExploit(player))) {
            return; // silently ignore for flagged behavior
        }

        Material type = event.getBlock().getType();
        Quest.QuestCategory category = mapBreakToQuestCategory(type);

        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().addProgressToIsland(island.getId(), category, 1);
        }
        if (plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().reportHighQuestProgress(player, "early_" + category.name().toLowerCase());
        }
        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[EarlyGameListener] PROFILE: addProgress took " + (ns / 1_000_000.0) + " ms");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;

        // Light guard (place is harder to macro for exploit than break, but consistent)
        if (plugin.getAntiCheatManager() != null &&
            (plugin.getAntiCheatManager().isFlaggedForXPExploit(player, event.getBlockPlaced().getType()) ||
             plugin.getAntiCheatManager().isFlaggedForQuestExploit(player))) {
            return;
        }

        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().addProgressToIsland(island.getId(), Quest.QuestCategory.BUILDING, 1);
        }
        if (plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().reportHighQuestProgress(player, "early_build");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player player)) return;
        if (!player.isOnline()) return;

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;

        // Anti-cheat: treat hostile as representative for combat quests
        if (plugin.getAntiCheatManager() != null &&
            (plugin.getAntiCheatManager().isFlaggedForXPExploit(player, Material.IRON_SWORD) ||
             plugin.getAntiCheatManager().isFlaggedForQuestExploit(player))) {
            return;
        }

        if (plugin.getQuestManager() != null) {
            String mobType = event.getEntityType().name();
            plugin.getQuestManager().addProgressToIsland(island.getId(), Quest.QuestCategory.COMBAT, mobType, 1);
        }
        if (plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().reportHighQuestProgress(player, "early_combat");
        }
    }

    // ==================== HELPERS ====================

    private Quest.QuestCategory mapBreakToQuestCategory(Material material) {
        if (isHarvestCrop(material)) {
            return Quest.QuestCategory.FARMING;
        }
        if (isMiningBlock(material)) {
            return Quest.QuestCategory.MINING;
        }
        // Default any other break (dirt, grass, etc.) to building/expansion feel for early game
        return Quest.QuestCategory.BUILDING;
    }

    private boolean isHarvestCrop(Material m) {
        return m == Material.WHEAT || m == Material.CARROTS || m == Material.POTATOES
            || m == Material.BEETROOTS || m == Material.NETHER_WART || m == Material.SUGAR_CANE
            || m == Material.MELON || m == Material.PUMPKIN || m == Material.COCOA;
    }

    private boolean isMiningBlock(Material m) {
        // Stone-like + ores for "first dig" onboarding
        return m == Material.STONE || m == Material.COBBLESTONE || m == Material.DEEPSLATE
            || m.name().endsWith("_ORE") || m == Material.GRAVEL || m == Material.DIRT
            || m == Material.SAND || m == Material.NETHERRACK;
    }

    // === STORY DIMENSION GATES ===
    // Players must complete the relevant story chapters (all overworld hostiles/bosses before nether, all nether before end).
    // The gate chapters (16 for nether, 19+ for end) set the milestones, but we also hard-enforce on portal travel.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(org.bukkit.event.player.PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        org.bukkit.Location toLoc = event.getTo();
        if (toLoc == null || toLoc.getWorld() == null) return;

        org.bukkit.World.Environment toEnv = toLoc.getWorld().getEnvironment();
        if (toEnv != org.bukkit.World.Environment.NETHER && toEnv != org.bukkit.World.Environment.THE_END) {
            return;
        }

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;

        if (toEnv == org.bukkit.World.Environment.NETHER && !island.hasUnlockedNether()) {
            event.setCancelled(true);
            player.sendMessage("§c§lStory Gate: §r§cYou must defeat every major overworld hostile mob and boss (complete the relevant story chapters) before you can enter the Nether.");
            player.sendMessage("§7Open §e/quest §7and check the current chapter (Story tab).");
            return;
        }

        if (toEnv == org.bukkit.World.Environment.THE_END && !island.hasUnlockedEnd()) {
            event.setCancelled(true);
            player.sendMessage("§c§lStory Gate: §r§cYou must defeat every major nether mob and boss (complete the relevant story chapters) before you can enter the End.");
            player.sendMessage("§7Open §e/quest §7and finish the nether phase first.");
        }
    }
}
