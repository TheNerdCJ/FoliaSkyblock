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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * IslandProtectionListener - Fully dimension-aware and compatible with latest IslandManager
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
     * Main protection check - works in any dimension
     */
    private boolean canPerformAction(Player player, Location location) {
        if (location.getWorld() == null) return true;

        // Admin bypass
        if (player.hasPermission("foliaskyblock.admin.bypass")) {
            return true;
        }

        Environment env = location.getWorld().getEnvironment();

        // Spawn island protection (only in overworld)
        if (env == Environment.NORMAL) {
            double distance = location.distance(new Location(location.getWorld(), 0, location.getY(), 0));
            if (distance <= spawnProtectionRadius) {
                return false;
            }
        }

        // Get player's island in the current dimension
        Island island = islandManager.getIsland(player.getUniqueId(), env);
        if (island == null) {
            return false; // Not on any island in this dimension
        }

        // Check distance from island center
        Location islandCenter = island.getCenter(location.getWorld());
        if (islandCenter == null || location.distance(islandCenter) > 60) {
            return false;
        }

        // Check island permission
        return island.hasPermission(player.getUniqueId(), IslandPermission.BUILD);
    }

    /**
     * Check if two players are on the exact same island (same dimension + same grid position)
     */
    private boolean isSameIsland(Player p1, Player p2) {
        Environment env = p1.getWorld().getEnvironment();
        if (env != p2.getWorld().getEnvironment()) return false;

        Island island1 = islandManager.getIsland(p1.getUniqueId(), env);
        Island island2 = islandManager.getIsland(p2.getUniqueId(), env);

        if (island1 == null || island2 == null) return false;

        return island1.getGridPosition().equals(island2.getGridPosition());
    }

    // ====================== EVENT HANDLERS ======================
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player damager)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        // Disable friendly fire on the same island (same dimension + same grid)
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
}