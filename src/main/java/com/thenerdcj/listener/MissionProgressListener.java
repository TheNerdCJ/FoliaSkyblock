package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.mission.Mission;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.InventoryClickEvent; // for trading simulation

/**
 * Drives the Mission system: translates gameplay events (block break/place, mob
 * kill, fishing) into {@link com.thenerdcj.mission.MissionManager} progress.
 *
 * (Formerly named ChallengeProgressListener — the standalone Challenge system it
 * was named after was removed in favor of the unified Mission system.)
 */
public class MissionProgressListener implements Listener {

    private final FoliaSkyblock plugin;

    public MissionProgressListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();
        if (plugin.getMissionManager() != null) {
            plugin.getMissionManager().addProgress(player.getUniqueId(), Mission.ObjectiveType.BREAK_BLOCKS, type.name(), 1);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlockPlaced().getType();
        if (plugin.getMissionManager() != null) {
            plugin.getMissionManager().addProgress(player.getUniqueId(), Mission.ObjectiveType.PLACE_BLOCKS, type.name(), 1);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player player) {
            if (plugin.getMissionManager() != null) {
                plugin.getMissionManager().addProgress(player.getUniqueId(), Mission.ObjectiveType.KILL_MOBS, "ANY", 1);
            }
        }
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getPlayer() != null) {
            if (plugin.getMissionManager() != null) {
                plugin.getMissionManager().addProgress(event.getPlayer().getUniqueId(), Mission.ObjectiveType.FISH_ITEMS, "ANY", 1);
            }
        }
    }

    // Note: For SELL/BUY, we would hook into Bazaar/Auction/ChestShop listeners in future expansions.
    // For SLAYER, hook into Slayer kill events when available.
}