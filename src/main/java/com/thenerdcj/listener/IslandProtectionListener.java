package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandPermission;
import com.thenerdcj.manager.GridManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Comprehensive Island Protection Listener for FoliaSkyblock.
 *
 * Handles protection for blocks, entities, redstone, explosions, pistons, endermen, etc.
 * Uses centralized permission checks via Island.hasPermission().
 */
public class IslandProtectionListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandManager islandManager;
    private final GridManager gridManager;
    private final int spawnProtectionRadius;

    public IslandProtectionListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
        this.gridManager = plugin.getGridManager();
        this.spawnProtectionRadius = plugin.getConfig().getInt("island.spawn-protection-radius", 128);
    }

    // ==================== CENTRAL PERMISSION CHECK ====================

    /**
     * Main permission check method.
     * Returns true if the player is allowed to perform the action at the location.
     */
    private boolean canPerformAction(Player player, Location location, IslandPermission permission) {
        if (location.getWorld() == null) return true;

        // Admin bypass
        if (player.hasPermission("foliasb.admin.bypass")) {
            return true;
        }

        Environment env = location.getWorld().getEnvironment();

        // Spawn protection (0,0 area)
        if (env == Environment.NORMAL) {
            double distance = location.distance(new Location(location.getWorld(), 0, location.getY(), 0));
            if (distance <= spawnProtectionRadius) {
                if (!player.hasPermission("foliasb.admin.editspawn")) {
                    player.sendMessage("§cYou cannot modify the protected spawn area.");
                    return false;
                }
            }

            var gridPos = gridManager.getGridPosition(location);
            if (gridManager.isSpawnGridPosition(gridPos)) {
                if (!player.hasPermission("foliasb.admin.editspawn")) {
                    player.sendMessage("§cYou cannot modify the default spawn island.");
                    return false;
                }
            }
        }

        // Get island at location
        Island island = islandManager.getIslandAt(location);
        if (island == null) {
            // No island here → only allow if player has bypass or it's their own unclaimed area (rare)
            return false;
        }

        // Check if player is within island bounds (64 block radius from center)
        Location center = island.getCenter(location.getWorld());
        if (center != null && location.distance(center) > 64) {
            return false;
        }

        // Permission check
        return island.hasPermission(player.getUniqueId(), permission);
    }

    /**
     * Simplified version for BUILD actions.
     */
    private boolean canBuild(Player player, Location location) {
        return canPerformAction(player, location, IslandPermission.BUILD);
    }

    /**
     * Simplified version for INTERACT actions.
     */
    private boolean canInteract(Player player, Location location) {
        return canPerformAction(player, location, IslandPermission.INTERACT);
    }

    // ==================== BLOCK EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot break blocks here!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot place blocks here!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        // Allow some interactions even without full build (like buttons/levers if redstone is allowed)
        if (!canInteract(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot use buckets here!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ==================== ENTITY & HANGING ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player) {
            if (!canBuild(player, event.getEntity().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity instanceof ItemFrame || entity instanceof Painting || entity instanceof ArmorStand) {
            if (!canInteract(event.getPlayer(), entity.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!canInteract(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ==================== EXPLOSIONS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Island island = islandManager.getIslandAt(block.getLocation());
            return island != null; // Prevent explosion damage on claimed islands
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        // Optional: prevent primed TNT/creepers near protected areas
        Location loc = event.getEntity().getLocation();
        Island island = islandManager.getIslandAt(loc);
        if (island != null) {
            // You can choose to cancel or reduce yield
            // event.setCancelled(true);
        }
    }

    // ==================== PISTONS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Island island = islandManager.getIslandAt(block.getLocation());
            if (island != null) {
                // Check if the piston is on the same island
                Island pistonIsland = islandManager.getIslandAt(event.getBlock().getLocation());
                if (pistonIsland == null || !pistonIsland.getGridPosition().equals(island.getGridPosition())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Island island = islandManager.getIslandAt(block.getLocation());
            if (island != null) {
                Island pistonIsland = islandManager.getIslandAt(event.getBlock().getLocation());
                if (pistonIsland == null || !pistonIsland.getGridPosition().equals(island.getGridPosition())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ==================== ENDERMAN & ENTITY CHANGE ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman) {
            Island island = islandManager.getIslandAt(event.getBlock().getLocation());
            if (island != null) {
                event.setCancelled(true);
            }
        }
    }

    // ==================== FIRE & BURN ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Island island = islandManager.getIslandAt(event.getBlock().getLocation());
        if (island != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) {
            if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        } else {
            // Natural fire (lightning, etc.)
            Island island = islandManager.getIslandAt(event.getBlock().getLocation());
            if (island != null) {
                event.setCancelled(true);
            }
        }
    }

    // ==================== VEHICLES ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        // Usually allowed, but you can restrict boat placement if desired
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getAttacker() instanceof Player player) {
            if (!canBuild(player, event.getVehicle().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    // ==================== PORTALS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        for (BlockState block : event.getBlocks()) {
            Island island = islandManager.getIslandAt(block.getLocation());
            if (island != null) {
                event.setCancelled(true);
                if (event.getEntity() instanceof Player player) {
                    player.sendMessage("§cYou cannot create portals on someone else's island.");
                }
                return;
            }
        }
    }

    // ==================== COMBAT PROTECTION (Optional) ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager && event.getEntity() instanceof Player victim) {
            // Optional: Prevent PvP between players on different islands
            if (!isSameIsland(damager, victim)) {
                // You can choose to allow or deny cross-island PvP
                // event.setCancelled(true);
            }
        }
    }

    private boolean isSameIsland(Player p1, Player p2) {
        if (p1.getWorld().getEnvironment() != p2.getWorld().getEnvironment()) return false;

        Island island1 = islandManager.getIsland(p1.getUniqueId(), p1.getWorld().getEnvironment());
        Island island2 = islandManager.getIsland(p2.getUniqueId(), p2.getWorld().getEnvironment());

        if (island1 == null || island2 == null) return false;
        return island1.getGridPosition().equals(island2.getGridPosition());
    }
}
