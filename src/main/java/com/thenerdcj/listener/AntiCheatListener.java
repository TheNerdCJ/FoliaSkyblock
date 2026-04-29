package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.anticheat.AntiCheatManager;
import com.thenerdcj.island.IslandManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AntiCheatListener - Fully optimized for Folia 1.21+
 * Uses RegionScheduler + AsyncScheduler for maximum performance.
 */
public class AntiCheatListener implements Listener {

    private final FoliaSkyblock plugin;
    private final AntiCheatManager antiCheatManager;
    private final IslandManager islandManager;

    // Fast-break tracking (thread-safe)
    private final ConcurrentHashMap<UUID, Long> lastBreakTime = new ConcurrentHashMap<>();

    public AntiCheatListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.antiCheatManager = new AntiCheatManager(plugin);
        this.islandManager = plugin.getIslandManager();
    }

    // ====================== BLOCK BREAK (RegionScheduler) ======================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        Location loc = e.getBlock().getLocation();

        plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
            long now = System.currentTimeMillis();
            Long last = lastBreakTime.get(player.getUniqueId());

            if (last != null) {
                long delta = now - last;
                double threshold = antiCheatManager.getConfig().getDouble("block.fastbreak.min-delay-ms", 180);

                // Efficiency tolerance
                int efficiency = player.getInventory().getItemInMainHand()
                        .getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY);
                if (efficiency > 0) threshold *= (1.0 - efficiency * 0.15);

                if (delta < threshold) {
                    antiCheatManager.addViolation(player, "FastBreak", 3);
                }
            }
            lastBreakTime.put(player.getUniqueId(), now);

            // X-Ray check
            if (antiCheatManager.getConfig().getBoolean("xray.enabled", true)) {
                if (isValuableBlock(e.getBlock().getType())) {
                    antiCheatManager.addViolation(player, "XRay (" + e.getBlock().getType().name() + ")", 4);
                }
            }
        });
    }

    private boolean isValuableBlock(Material mat) {
        return antiCheatManager.getConfig()
                .getStringList("xray.valuable-blocks")
                .contains(mat.name());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        Location loc = e.getBlock().getLocation();

        plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
            antiCheatManager.addViolation(player, "FastPlace/Scaffold", 2);
        });
    }

    // ====================== COMBAT (RegionScheduler) ======================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        Location loc = victim.getLocation();

        plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
            double distance = player.getLocation().distance(victim.getLocation());
            double maxReach = antiCheatManager.getConfig().getDouble("combat.reach.max-distance", 3.85);

            if (distance > maxReach + 0.3) {
                antiCheatManager.addViolation(player, "Reach (" + String.format("%.2f", distance) + ")", 4);
            }
        });
    }

    // ====================== MOVEMENT (AsyncScheduler) ======================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (player.isFlying() || player.getAllowFlight() || player.isOnGround()) return;

        Location from = e.getFrom();
        Location to = e.getTo();

        // Heavy calculations run async
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            double deltaX = to.getX() - from.getX();
            double deltaZ = to.getZ() - from.getZ();
            double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

            double baseThreshold = antiCheatManager.getConfig().getDouble("movement.speed.threshold", 0.68);
            double speedThreshold = baseThreshold;

            // Speed potion tolerance
            if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                int amp = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier();
                speedThreshold += amp * 0.15;
            }

            if (speed > speedThreshold) {
                antiCheatManager.addViolation(player, "SpeedHack", 3);
            }

            // Fly check
            if (!player.hasPotionEffect(PotionEffectType.LEVITATION)) {
                if (to.getY() > from.getY() + antiCheatManager.getConfig().getDouble("movement.fly.threshold", 0.42)) {
                    antiCheatManager.addViolation(player, "FlyHack", 4);
                }
            }
        });
    }

    public AntiCheatManager getAntiCheatManager() {
        return antiCheatManager;
    }
}