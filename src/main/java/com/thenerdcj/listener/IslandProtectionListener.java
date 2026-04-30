package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandPermission;
import com.thenerdcj.manager.IslandManager;
import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * IslandProtectionListener - Highly optimized protection system for Folia 1.21+
 *
 * Features:
 * - Cached island lookups (O(1) permission checks)
 * - Spawn protection at 0,0 (unclaimable admin island)
 * - Full party permission support
 * - Prevents griefing from endermen, explosions, pistons, etc.
 * - Dimension-aware protection
 */
public class IslandProtectionListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandManager islandManager;
    private final int spawnProtectionRadius;

    public IslandProtectionListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
        this.spawnProtectionRadius = plugin.getConfig().getInt("island.spawn-protection-radius", 128);
    }

    // ==================== CORE PERMISSION CHECK ====================

    /**
     * Ultra-fast permission check. This is the hot path - optimized heavily.
     */
    private boolean canPerformAction(Player player, Location location) {
        if (location.getWorld() == null) return true;

        // Admin bypass (fastest check first)
        if (player.hasPermission("foliasb.admin.bypass")) {
            return true;
        }

        Environment env = location.getWorld().getEnvironment();

        // Spawn protection at 0,0 (unclaimable admin island)
        if (env == Environment.NORMAL) {
            double distance = location.distance(new Location(location.getWorld(), 0, location.getY(), 0));
            if (distance <= spawnProtectionRadius) {
                if (!player.hasPermission("foliasb.admin.editspawn")) {
                    return false;
                }
            }
        }

        // Get cached island
        Island island = islandManager.getIsland(player.getUniqueId(), env);
        if (island == null) {
            return false; // No island = no build rights
        }

        // Check distance from island center (64 block radius)
        Location center = island.getCenter(location.getWorld());
        if (center == null || location.distance(center) > 64) {
            return false;
        }

        // Permission check using pre-computed EnumSet (O(1))
        return island.hasPermission(player.getUniqueId(), IslandPermission.BUILD);
    }

    /**
     * Check if two players are on the same island (for friendly fire protection)
     */
    private boolean isSameIsland(Player p1, Player p2) {
        Environment env = p1.getWorld().getEnvironment();
        if (env != p2.getWorld().getEnvironment()) return false;

        Island island1 = islandManager.getIsland(p1.getUniqueId(), env);
        Island island2 = islandManager.getIsland(p2.getUniqueId(), env);

        if (island1 == null || island2 == null) return false;

        return island1.getGridPosition().equals(island2.getGridPosition());
    }

    // ==================== BLOCK EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!canPerformAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cYou cannot break blocks here!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!canPerformAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cYou cannot place blocks here!");
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

    // ==================== PISTON & EXPLOSION PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (org.bukkit.block.Block block : e.getBlocks()) {
            Island island = islandManager.getIslandAt(block.getLocation());
            if (island != null && !island.hasPermission(null, IslandPermission.BUILD)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (org.bukkit.block.Block block : e.getBlocks()) {
            Island island = islandManager.getIslandAt(block.getLocation());
            if (island != null && !island.hasPermission(null, IslandPermission.BUILD)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> {
            Island island = islandManager.getIslandAt(block.getLocation());
            return island != null;
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(block -> {
            Island island = islandManager.getIslandAt(block.getLocation());
            return island != null;
        });
    }

    // ==================== ENTITY & HANGING PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player damager)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        // Prevent friendly fire on the same island
        if (isSameIsland(damager, victim)) {
            e.setCancelled(true);
            damager.sendMessage("§cYou cannot attack players on your island!");
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

    // ==================== PORTAL PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        for (org.bukkit.block.BlockState state : e.getBlocks()) {
            Island island = islandManager.getIslandAt(state.getLocation());
            if (island != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ==================== VEHICLE & HANGING ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(org.bukkit.event.vehicle.VehicleDestroyEvent e) {
        if (!(e.getAttacker() instanceof Player player)) return;
        if (!canPerformAction(player, e.getVehicle().getLocation())) {
            e.setCancelled(true);
        }
    }
}