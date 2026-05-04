package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.AntiCheatManager;
import com.thenerdcj.island.IslandManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCheatListener implements Listener {

    private final FoliaSkyblock plugin;
    private final AntiCheatManager antiCheatManager;
    private final IslandManager islandManager;

    private final ConcurrentHashMap<UUID, Long> lastBreakTime = new ConcurrentHashMap<>();

    public AntiCheatListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.antiCheatManager = new AntiCheatManager(plugin);
        this.islandManager = plugin.getIslandManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("foliasb.bypass.anticheat")) return;

        player.getScheduler().run(plugin, scheduledTask -> {
            if (!antiCheatManager.checkPlayer(player)) {
                Location from = event.getFrom();
                player.teleport(from);
                player.sendMessage("§c[AntiCheat] Suspicious movement detected!");
            }
        }, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        Location loc = e.getBlock().getLocation();

        plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
            long now = System.currentTimeMillis();
            Long last = lastBreakTime.get(player.getUniqueId());

            if (last != null) {
                long delta = now - last;
                double threshold = 180.0;

                int efficiency = player.getInventory().getItemInMainHand()
                        .getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY);
                if (efficiency > 0) threshold *= (1.0 - Math.min(efficiency * 0.15, 0.8));

                if (antiCheatManager.getProfile(player.getUniqueId()) != null &&
                        antiCheatManager.getProfile(player.getUniqueId()).hasHighEnchantments()) {
                    threshold *= 0.5;
                }

                if (delta < threshold) {
                    e.setCancelled(true);
                    player.sendMessage("§c[AntiCheat] Breaking blocks too fast!");
                    return;
                }
            }

            lastBreakTime.put(player.getUniqueId(), now);

            if (isOre(e.getBlock().getType())) {
                if (antiCheatManager.getProfile(player.getUniqueId()) != null) {
                    antiCheatManager.getProfile(player.getUniqueId()).recordOreMined();
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;

        if (player.hasPermission("foliasb.bypass.anticheat")) return;

        if (antiCheatManager.getProfile(player.getUniqueId()) != null) {
            antiCheatManager.getProfile(player.getUniqueId()).recordAttack();
        }

        player.getScheduler().run(plugin, scheduledTask -> {
            if (!antiCheatManager.checkPlayer(player)) {
                e.setCancelled(true);
                player.sendMessage("§c[AntiCheat] Suspicious combat detected!");
            }
        }, null);
    }

    private boolean isOre(org.bukkit.Material material) {
        return material == org.bukkit.Material.COAL_ORE ||
                material == org.bukkit.Material.IRON_ORE ||
                material == org.bukkit.Material.GOLD_ORE ||
                material == org.bukkit.Material.DIAMOND_ORE ||
                material == org.bukkit.Material.EMERALD_ORE ||
                material == org.bukkit.Material.REDSTONE_ORE ||
                material == org.bukkit.Material.LAPIS_ORE ||
                material == org.bukkit.Material.NETHERITE_SCRAP ||
                material == org.bukkit.Material.ANCIENT_DEBRIS;
    }
}