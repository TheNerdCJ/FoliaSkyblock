package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.AntiCheatManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * AntiCheatListener - Wires Bukkit/Folia events to AntiCheatManager.
 * 
 * Uses Folia API (EntityScheduler, RegionScheduler) for thread-safe checks on high-concurrency servers.
 * Communicates directly with AntiCheatManager which in turn uses NeuralCheatDetector + PlayerBehaviorProfile.
 * 
 * Updated to support fastbreak tracking, xray heuristics, and custom generator awareness.
 */
public class AntiCheatListener implements Listener {

    private final FoliaSkyblock plugin;
    private final AntiCheatManager antiCheatManager;

    public AntiCheatListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.antiCheatManager = plugin.getAntiCheatManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("foliasb.bypass.anticheat")) return;

        // Folia EntityScheduler - safe per-player
        player.getScheduler().run(plugin, task -> {
            if (!antiCheatManager.checkPlayer(player)) {
                player.teleport(event.getFrom());
                player.sendMessage("§c[AntiCheat] Suspicious movement detected.");
            }
        }, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (player.hasPermission("foliasb.bypass.anticheat")) return;

        // Record for fastbreak + xray profile BEFORE check
        antiCheatManager.recordBlockBreak(player, e.getBlock());

        // Folia RegionScheduler for location-bound check
        plugin.getServer().getRegionScheduler().execute(plugin, e.getBlock().getLocation(), () -> {
            antiCheatManager.checkPlayer(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (player.hasPermission("foliasb.bypass.anticheat")) return;

        antiCheatManager.recordBlockPlaceTime(player); // for fastplace

        ItemStack item = e.getItemInHand();
        if (item.getType() == Material.SHULKER_BOX) {
            antiCheatManager.checkShulkerDuplication(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (player.hasPermission("foliasb.bypass.anticheat")) return;

        player.getScheduler().run(plugin, task -> antiCheatManager.checkPlayer(player), null);
    }

    // Dupe / inventory events
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent e) {
        if (e.getSource().getHolder() instanceof Player player) {
            antiCheatManager.recordItemTransaction(player, 1);
            antiCheatManager.checkContainerDuplication(player, e.getSource().getLocation().getBlock());
        }
        if (e.getDestination().getHolder() instanceof Player player2) {
            antiCheatManager.recordItemTransaction(player2, 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent e) {
        if (e.getBlock().getState() instanceof org.bukkit.block.Dispenser ||
            e.getBlock().getState() instanceof org.bukkit.block.Dropper) {

            for (Player p : e.getBlock().getWorld().getPlayers()) {
                if (p.getLocation().distance(e.getBlock().getLocation()) < 6) {
                    antiCheatManager.checkContainerDuplication(p, e.getBlock());
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        antiCheatManager.recordItemTransaction(e.getPlayer(), -1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent e) {
        ItemStack item = e.getItem().getItemStack();
        antiCheatManager.recordItemTransaction(e.getPlayer(), item.getAmount());

        if (antiCheatManager.scanForIllegalItem(e.getPlayer(), item)) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (player.hasPermission("foliasb.bypass.anticheat")) return;

        ItemStack current = e.getCurrentItem();
        if (current != null) {
            antiCheatManager.recordItemTransaction(player, current.getAmount());
            if (antiCheatManager.scanForIllegalItem(player, current)) {
                e.setCancelled(true);
            }
        }
    }
}
