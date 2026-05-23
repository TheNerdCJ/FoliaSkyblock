package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandPermission;
import com.thenerdcj.database.GridPosition;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandManager islandManager;

    public IslandProtectionListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
    }

    private boolean canBuild(Player player, Location location) {
        if (player.hasPermission("foliasb.admin.bypass")) return true;

        Island island = islandManager.getIslandAt(location);
        if (island == null) {
            // Spawn protection at 0,0
            if (Math.abs(location.getBlockX()) <= 50 && Math.abs(location.getBlockZ()) <= 50) {
                return false;
            }
            return false; // Wilderness protection
        }

        return island.hasPermission(player.getUniqueId(), IslandPermission.BUILD);
    }

    private boolean canInteract(Player player, Location location) {
        if (player.hasPermission("foliasb.admin.bypass")) return true;

        Island island = islandManager.getIslandAt(location);
        if (island == null) return false;

        return island.hasPermission(player.getUniqueId(), IslandPermission.INTERACT);
    }

    // ==================== BLOCK EVENTS ====================

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Component.text("§cYou cannot break blocks here!"));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Component.text("§cYou cannot place blocks here!"));
        }
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (!canInteract(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ==================== BUCKETS ====================

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ==================== EXPLOSIONS ====================

    @EventHandler
    public void onExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> {
            Island island = islandManager.getIslandAt(block.getLocation());
            return island == null || !island.isMember(UUID.fromString("00000000-0000-0000-0000-000000000000")); // Prevent all explosions in islands
        });
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(block -> {
            Island island = islandManager.getIslandAt(block.getLocation());
            return island != null;
        });
    }

    // ==================== PISTONS ====================

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
            for (Block block : e.getBlocks()) {
                if (islandManager.getIslandAt(block.getLocation()) == null) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
            for (Block block : e.getBlocks()) {
                if (islandManager.getIslandAt(block.getLocation()) == null) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ==================== ENTITIES ====================

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player player) {
            if (!canBuild(player, e.getEntity().getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player player) {
            if (!canBuild(player, e.getEntity().getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        if (!canBuild(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ==================== FIRE & ENDERMAN ====================

    @EventHandler
    public void onBlockBurn(BlockBurnEvent e) {
        if (islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null && !canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Enderman) {
            if (islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
                e.setCancelled(true);
            }
        }
    }

    // ==================== VEHICLES & PORTALS ====================

    @EventHandler
    public void onVehicleCreate(VehicleCreateEvent e) {
        Island island = islandManager.getIslandAt(e.getVehicle().getLocation());
        if (island == null) {
            // Optional: Block vehicle creation in wilderness
            // e.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        if (e.getAttacker() instanceof Player player) {
            if (!canBuild(player, e.getVehicle().getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent e) {
        for (BlockState state : e.getBlocks()) {
            if (islandManager.getIslandAt(state.getLocation()) != null) {
                // Optional: Allow or block portal creation
                // e.setCancelled(true); // Uncomment to disable portals on islands
            }
        }
    }

    // ==================== SPAWN PROTECTION ====================

    @EventHandler
    public void onSpawnInteract(PlayerInteractEvent e) {
        Location loc = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : e.getPlayer().getLocation();
        if (Math.abs(loc.getBlockX()) <= 50 && Math.abs(loc.getBlockZ()) <= 50) {
            if (!e.getPlayer().hasPermission("foliasb.admin.bypass")) {
                e.setCancelled(true);
            }
        }
    }
}