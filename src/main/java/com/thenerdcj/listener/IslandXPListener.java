package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.IslandXPManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

public class IslandXPListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandXPManager xpManager;

    public IslandXPListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.xpManager = new IslandXPManager(plugin);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("xp.enabled", true)) return;

        Material type = event.getBlock().getType();

        if (isMiningBlock(type)) {
            xpManager.awardMiningXP(event.getPlayer(), type);
            if (plugin.getChallengeManager() != null) {
                plugin.getChallengeManager().updateProgress(event.getPlayer().getUniqueId(), "MINING", 1);
            }
        } else if (isFarmingBlock(type)) {
            xpManager.awardFarmingXP(event.getPlayer(), type);
            if (plugin.getChallengeManager() != null) {
                plugin.getChallengeManager().updateProgress(event.getPlayer().getUniqueId(), "FARMING", 1);
            }
        } else if (isForagingBlock(type)) {
            xpManager.awardForagingXP(event.getPlayer(), type);
        } else if (isExcavationBlock(type)) {
            xpManager.awardExcavationXP(event.getPlayer(), type);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        if (!plugin.getConfig().getBoolean("xp.enabled", true)) return;

        xpManager.awardCombatXP(event.getEntity().getKiller(), event.getEntityType());

        if (plugin.getChallengeManager() != null) {
            plugin.getChallengeManager().updateProgress(event.getEntity().getKiller().getUniqueId(), "COMBAT", 1);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!plugin.getConfig().getBoolean("xp.enabled", true)) return;

        xpManager.awardFishingXP(event.getPlayer(), 2.5);
    }

    private boolean isMiningBlock(Material type) {
        return type.name().contains("ORE") || type == Material.STONE || type == Material.COBBLESTONE;
    }

    private boolean isFarmingBlock(Material type) {
        return type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES || type == Material.BEETROOTS;
    }

    private boolean isForagingBlock(Material type) {
        return type == Material.OAK_LOG || type == Material.BIRCH_LOG || type == Material.SPRUCE_LOG || type == Material.JUNGLE_LOG;
    }

    private boolean isExcavationBlock(Material type) {
        return type == Material.DIRT || type == Material.SAND || type == Material.GRAVEL;
    }
}