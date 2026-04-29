package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandPermission;
import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * IslandProtectionListener - Highly optimized for Folia 1.21+
 * Uses cached island lookups (O(1)) and pre-computed EnumSet permissions.
 */
public class IslandProtectionListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandManager islandManager;
    private final int spawnProtectionRadius;

    public IslandProtectionListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
        this.spawnProtectionRadius = plugin.getConfig().getInt("island.spawn-protection-radius", 150);
    }

    /**
     * Ultra-fast permission check using cached island data.
     * This is the hot path - keep it as fast as possible.
     */
    private boolean canPerformAction(Player player, Location location) {
        if (location.getWorld() == null) return true;

        // Admin bypass (fastest check first)
        if (player.hasPermission("foliaskyblock.admin.bypass")) {
            return true;
        }

        Environment env = location.getWorld().getEnvironment();

        // Spawn protection (only in overworld)
        if (env == Environment.NORMAL) {
            double distance = location.distance(new Location(location.getWorld(), 0, location.getY(), 0));
            if (distance <= spawnProtectionRadius) {
                return false;
            }
        }

        // Get cached island for this player + dimension (O(1) lookup)
        Island island = islandManager.getIsland(player.getUniqueId(), env);
        if (island == null) {
            return false; // Player has no island in this dimension
        }

        // Distance check from island center
        Location center = island.getCenter(location.getWorld());
        if (center == null || location.distance(center) > 64) { // 64 block radius
            return false;
        }

        // Final permission check (uses pre-computed EnumSet - O(1))
        return island.hasPermission(player.getUniqueId(), IslandPermission.BUILD);
    }

    /**
     * Check if two players are on the exact same island (same dimension + same grid)
     */
    private boolean isSameIsland(Player p1, Player p2) {
        Environment env = p1.getWorld().getEnvironment();
        if (env != p2.getWorld().getEnvironment()) return false;

        Island island1 = islandManager.getIsland(p1.getUniqueId(), env);
        Island island2 = islandManager.getIsland(p2.getUniqueId(), env);

        if (island1 == null || island2 == null) return false;

        return island1.getGridPosition().equals(island2.getGridPosition());
    }

    // ====================== BLOCK EVENTS ======================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!canPerformAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!canPerformAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (!canPerformAction(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!canPerformAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canPerformAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ====================== ENTITY EVENTS ======================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player damager)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        // Prevent friendly fire on the same island
        if (isSameIsland(damager, victim)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!(e.getRemover() instanceof Player player)) return;
        if (!canPerformAction(player, e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Enderman)) return;
        // Prevent endermen from griefing islands
        if (!canPerformAction(null, e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }
}