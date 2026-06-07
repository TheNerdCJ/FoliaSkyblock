package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.skills.PlayerSkillManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * SkillListener - Awards per-player skill XP.
 * Delegates to PlayerSkillManager.
 * Respects anti-cheat via manager.
 * Separate from island skills to avoid any conflicts/bugs.
 * Folia: events on region, manager handles async/DB.
 */
public class SkillListener implements Listener {

    private final FoliaSkyblock plugin;

    public SkillListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        PlayerSkillManager sm = plugin.getPlayerSkillManager();
        if (sm == null || p == null) return;

        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
        // Manager will check anti-cheat internally
        sm.processBlockBreak(p, e.getBlock().getType(), true);
        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[SkillListener] PROFILE: processBlockBreak took " + (ns / 1_000_000.0) + " ms");
        }

        // Step 5: Hook skills to quests (per-player and island)
        if (plugin.getQuestManager() != null) {
            com.thenerdcj.quest.Quest.QuestCategory qcat = mapSkillToQuestCategory(e.getBlock().getType());
            String playerKey = p.getUniqueId().toString();
            plugin.getQuestManager().addProgressToIsland(playerKey, qcat, 1);
            com.thenerdcj.island.Island isl = plugin.getIslandManager() != null ? plugin.getIslandManager().getIsland(p.getUniqueId(), p.getWorld().getEnvironment()) : null;
            if (isl != null) {
                plugin.getQuestManager().addProgressToIsland(isl.getId(), qcat, 1);
            }
        }

        // Handle actual extra drops for active abilities (mining super breaker, wood feller) - safe, post-vanilla, no worth/anti-cheat impact
        com.thenerdcj.skills.SkillType activeSkill = null;
        if (sm.isAbilityActive(p.getUniqueId(), com.thenerdcj.skills.SkillType.MINING)) {
            activeSkill = com.thenerdcj.skills.SkillType.MINING;
        } else if (sm.isAbilityActive(p.getUniqueId(), com.thenerdcj.skills.SkillType.WOODCUTTING)) {
            activeSkill = com.thenerdcj.skills.SkillType.WOODCUTTING;
        }
        if (activeSkill != null) {
            sm.spawnExtraDropsForAbility(p, e.getBlock(), activeSkill);
        }

        // Passive: high level mining gives brief speed buff on break (non-spammy, 1s)
        if (e.getBlock().getType().name().contains("ORE") || e.getBlock().getType() == org.bukkit.Material.STONE) {
            int miningLevel = sm.getSkillLevel(p.getUniqueId(), com.thenerdcj.skills.SkillType.MINING);
            if (miningLevel >= 30) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 20, 0, false, false));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player p)) return;
        PlayerSkillManager sm = plugin.getPlayerSkillManager();
        if (sm != null) {
            long start = 0;
            if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
            sm.processMobKill(p, e.getEntityType());
            if (start != 0) {
                long ns = System.nanoTime() - start;
                if (ns > 500_000L) plugin.getLogger().info("[SkillListener] PROFILE: processMobKill took " + (ns / 1_000_000.0) + " ms");
            }

            // Step 5: Hook mob kills from skills to combat quest progress
            if (plugin.getQuestManager() != null) {
                String playerKey = p.getUniqueId().toString();
                plugin.getQuestManager().addProgressToIsland(playerKey, com.thenerdcj.quest.Quest.QuestCategory.COMBAT, 1);
                com.thenerdcj.island.Island isl = plugin.getIslandManager() != null ? plugin.getIslandManager().getIsland(p.getUniqueId(), p.getWorld().getEnvironment()) : null;
                if (isl != null) plugin.getQuestManager().addProgressToIsland(isl.getId(), com.thenerdcj.quest.Quest.QuestCategory.COMBAT, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        PlayerSkillManager sm = plugin.getPlayerSkillManager();
        if (sm != null) {
            long start = 0;
            if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
            sm.processFish(p);
            if (start != 0) {
                long ns = System.nanoTime() - start;
                if (ns > 500_000L) plugin.getLogger().info("[SkillListener] PROFILE: processFish took " + (ns / 1_000_000.0) + " ms");
            }

            // Step 5: Hook fish from skills to exploration quest progress
            if (plugin.getQuestManager() != null) {
                String playerKey = p.getUniqueId().toString();
                plugin.getQuestManager().addProgressToIsland(playerKey, com.thenerdcj.quest.Quest.QuestCategory.EXPLORATION, 1);
                com.thenerdcj.island.Island isl = plugin.getIslandManager() != null ? plugin.getIslandManager().getIsland(p.getUniqueId(), p.getWorld().getEnvironment()) : null;
                if (isl != null) plugin.getQuestManager().addProgressToIsland(isl.getId(), com.thenerdcj.quest.Quest.QuestCategory.EXPLORATION, 1);
            }
        }
    }

    private com.thenerdcj.quest.Quest.QuestCategory mapSkillToQuestCategory(org.bukkit.Material material) {
        if (material.name().contains("ORE") || material == org.bukkit.Material.STONE) return com.thenerdcj.quest.Quest.QuestCategory.MINING;
        if (material.name().contains("LOG") || material.name().contains("LEAVES")) return com.thenerdcj.quest.Quest.QuestCategory.FARMING;
        return com.thenerdcj.quest.Quest.QuestCategory.CHALLENGE;
    }
}