package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandPermission;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import net.kyori.adventure.text.Component;

public class IslandProtectionListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandManager islandManager;
    private final FileConfiguration config;

    // Configurable values
    private int spawnRadius;
    private boolean wildernessProtection;
    private boolean explosionProtection;
    private boolean pistonProtection;
    private boolean fireProtection;
    private boolean endermanGrief;

    public IslandProtectionListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
        this.config = plugin.getConfig();
        loadConfig();
    }

    public void loadConfig() {
        this.spawnRadius = config.getInt("protection.spawn-radius", 50);
        this.wildernessProtection = config.getBoolean("protection.wilderness-protection", true);
        this.explosionProtection = config.getBoolean("protection.explosion-protection", true);
        this.pistonProtection = config.getBoolean("protection.piston-protection", true);
        this.fireProtection = config.getBoolean("protection.fire-protection", true);
        this.endermanGrief = config.getBoolean("protection.enderman-grief", true);
    }

    private boolean canBuild(Player player, Location location) {
        if (player.hasPermission("foliasb.admin.bypass")) return true;

        Island island = islandManager.getIslandAt(location);

        if (island == null) {
            // Spawn protection
            if (isSpawnProtected(location)) {
                return false;
            }
            return !wildernessProtection; // Allow build in wilderness if disabled
        }

        return island.hasPermission(player.getUniqueId(), IslandPermission.BUILD);
    }

    private boolean canInteract(Player player, Location location) {
        if (player.hasPermission("foliasb.admin.bypass")) return true;

        Island island = islandManager.getIslandAt(location);
        if (island == null) return false;

        return island.hasPermission(player.getUniqueId(), IslandPermission.INTERACT);
    }

    private boolean isSpawnProtected(Location loc) {
        return Math.abs(loc.getBlockX()) <= spawnRadius &&
                Math.abs(loc.getBlockZ()) <= spawnRadius;
    }

    // ==================== BLOCK EVENTS ====================

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Component.text(config.getString("protection.messages.no-build", "§cYou cannot build here!")));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Component.text(config.getString("protection.messages.no-build", "§cYou cannot build here!")));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (!canInteract(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Component.text(config.getString("protection.messages.no-interact", "§cYou cannot interact here!")));
        }
    }

    // ==================== BUCKETS ====================

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    // ==================== EXPLOSIONS ====================

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (explosionProtection) {
            e.blockList().removeIf(block -> islandManager.getIslandAt(block.getLocation()) != null);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (explosionProtection) {
            e.blockList().removeIf(block -> islandManager.getIslandAt(block.getLocation()) != null);
        }
    }

    // ==================== PISTONS ====================

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (pistonProtection && islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
            for (org.bukkit.block.Block block : e.getBlocks()) {
                if (islandManager.getIslandAt(block.getLocation()) == null) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (pistonProtection && islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
            for (org.bukkit.block.Block block : e.getBlocks()) {
                if (islandManager.getIslandAt(block.getLocation()) == null) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ==================== VEHICLES ====================

    @EventHandler
    public void onVehicleCreate(VehicleCreateEvent e) {
        // Optional wilderness control
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        if (e.getAttacker() instanceof Player player) {
            if (!canBuild(player, e.getVehicle().getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    // ==================== HANGING & ARMOR STAND ====================

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

    // ==================== FIRE & GRIEFING ====================

    @EventHandler
    public void onBlockBurn(BlockBurnEvent e) {
        if (fireProtection && islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
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
        if (endermanGrief && e.getEntity() instanceof org.bukkit.entity.Enderman) {
            if (islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
                e.setCancelled(true);
            }
        }
    }

    // ==================== SPAWN PROTECTION ====================

    @EventHandler
    public void onSpawnInteract(PlayerInteractEvent e) {
        Location loc = e.getClickedBlock() != null ?
                e.getClickedBlock().getLocation() : e.getPlayer().getLocation();

        if (isSpawnProtected(loc)) {
            if (!e.getPlayer().hasPermission("foliasb.admin.bypass")) {
                e.setCancelled(true);
            }
        }
    }
}