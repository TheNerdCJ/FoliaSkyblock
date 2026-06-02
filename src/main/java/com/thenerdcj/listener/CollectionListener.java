package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * CollectionListener - Hooks gathering actions to the Core Collections system.
 * Only logs on the player's own island (or island they are on) for the first time the island sees it.
 * Folia-safe: lightweight checks, delegates to manager (DB async).
 */
public class CollectionListener implements Listener {

    private final FoliaSkyblock plugin;

    public CollectionListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (plugin.getCollectionManager() == null) return;

        Island island = plugin.getIslandManager() != null ?
                plugin.getIslandManager().getIslandAt(e.getBlock().getLocation()) : null;
        if (island == null) return;

        // Only count if the player has build rights or is member (prevents visitors from "discovering" for the island unfairly)
        if (!island.hasPermission(p.getUniqueId(), com.thenerdcj.island.IslandPermission.BUILD) &&
            !p.getUniqueId().equals(island.getOwnerUuid())) {
            return;
        }

        String islandKey = island.getId();
        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
        plugin.getCollectionManager().discoverBlock(islandKey, e.getBlock().getType(), p.getUniqueId(), p);
        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[CollectionListener] PROFILE: discoverBlock took " + (ns / 1_000_000.0) + " ms");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (plugin.getCollectionManager() == null) return;
        if (!(e.getEntity().getKiller() instanceof Player p)) return;

        Island island = plugin.getIslandManager() != null ?
                plugin.getIslandManager().getIslandAt(e.getEntity().getLocation()) : null;
        if (island == null) return;

        // Combat collections for island members/killer on island
        if (!island.isMember(p.getUniqueId()) && !p.getUniqueId().equals(island.getOwnerUuid())) {
            return;
        }

        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
        plugin.getCollectionManager().discoverMobKill(island.getId(), e.getEntityType(), p.getUniqueId(), p);
        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[CollectionListener] PROFILE: discoverMobKill took " + (ns / 1_000_000.0) + " ms");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (plugin.getCollectionManager() == null) return;
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(e.getCaught() instanceof org.bukkit.entity.Item itemEntity)) return;
        ItemStack caughtItem = itemEntity.getItemStack();

        Player p = e.getPlayer();
        Island island = plugin.getIslandManager() != null ?
                plugin.getIslandManager().getIslandAt(e.getPlayer().getLocation()) : null;
        if (island == null) return;

        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
        plugin.getCollectionManager().discoverFish(island.getId(), caughtItem.getType(), p.getUniqueId(), p);
        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[CollectionListener] PROFILE: discoverFish took " + (ns / 1_000_000.0) + " ms");
        }
    }
}