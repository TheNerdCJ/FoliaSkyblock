package com.thenerdcj.util;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Centralized thread-safety utilities for FoliaSkyblock.
 * 
 * Heavily optimized for Folia's multi-threaded region system.
 * All scheduling respects Folia's GlobalRegionScheduler, RegionScheduler, and EntityScheduler.
 */
public final class ThreadSafety {

    private final FoliaSkyblock plugin;

    public ThreadSafety(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public boolean isFolia() {
        return plugin.isFolia();
    }

    // ==================== GLOBAL / MAIN THREAD SCHEDULING ====================

    public void runOnMainThread(Runnable task) {
        if (task == null) return;
        if (isFolia()) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runOnMainThreadLater(Runnable task, long delayTicks) {
        if (task == null) return;
        if (isFolia()) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /** Folia requires initial delay and period > 0 for fixed-rate tasks. */
    private static long foliaSafeTicks(long ticks) {
        return Math.max(1L, ticks);
    }

    public void runRepeatingOnMainThread(Runnable task, long initialDelayTicks, long periodTicks) {
        if (task == null) return;
        long initial = foliaSafeTicks(initialDelayTicks);
        long period = foliaSafeTicks(periodTicks);
        if (isFolia()) {
            plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), initial, period);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        }
    }

    // ==================== REGION / LOCATION SCHEDULING ====================

    public void runAtLocation(Location location, Runnable task) {
        if (task == null || location == null || location.getWorld() == null) return;
        if (isFolia()) {
            plugin.getServer().getRegionScheduler().run(plugin, location, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runAtLocationLater(Location location, Runnable task, long delayTicks) {
        if (task == null || location == null || location.getWorld() == null) return;
        if (isFolia()) {
            plugin.getServer().getRegionScheduler().runDelayed(plugin, location, t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    // ==================== ENTITY SCHEDULING (Best for minions, holograms, etc.) ====================

    public void runForEntity(Entity entity, Runnable task) {
        if (task == null || entity == null || !entity.isValid()) return;
        if (isFolia()) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runForEntityLater(Entity entity, Runnable task, long delayTicks) {
        if (task == null || entity == null || !entity.isValid()) return;
        if (isFolia()) {
            entity.getScheduler().runDelayed(plugin, t -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public void runRepeatingForEntity(Entity entity, Runnable task, long initialDelayTicks, long periodTicks) {
        if (task == null || entity == null || !entity.isValid()) return;
        long initial = foliaSafeTicks(initialDelayTicks);
        long period = foliaSafeTicks(periodTicks);
        if (isFolia()) {
            entity.getScheduler().runAtFixedRate(plugin, t -> task.run(), null, initial, period);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        }
    }

    // ==================== PLAYER SCHEDULING (for per-player effects like weather/particles/music) ====================

    /**
     * Schedules a repeating task bound to a specific player (Folia uses player scheduler for region affinity).
     * Returns the underlying task handle (ScheduledTask or BukkitTask) for cancellation if needed.
     */
    public Object runRepeatingForPlayer(Player player, Runnable task, long initialDelayTicks, long periodTicks) {
        if (task == null || player == null || !player.isOnline()) return null;
        long initial = foliaSafeTicks(initialDelayTicks);
        long period = foliaSafeTicks(periodTicks);
        if (isFolia()) {
            return player.getScheduler().runAtFixedRate(plugin, t -> task.run(), null, initial, period);
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        }
    }

    // ==================== ASYNC HELPERS ====================

    public void runAsync(Runnable task) {
        if (task == null) return;
        if (isFolia()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    // ==================== PLAYER / INVENTORY SAFE WRAPPERS ====================

    public void giveItemSafely(UUID uuid, ItemStack item, String onlineMessage) {
        runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.getInventory().addItem(item);
                if (onlineMessage != null && !onlineMessage.isEmpty()) {
                    player.sendMessage(onlineMessage);
                }
            } else {
                plugin.getDatabaseManager().storePendingItem(uuid, item);
            }
        });
    }

    public void removeItemSafely(Player player, ItemStack item) {
        if (player == null || item == null) return;
        runOnMainThread(() -> {
            if (player.isOnline()) {
                player.getInventory().removeItem(item);
            }
        });
    }

    public void sendMessageSafely(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        runOnMainThread(() -> {
            if (player.isOnline()) {
                player.sendMessage(message);
            }
        });
    }

    public void sendMessageSafely(UUID uuid, String message) {
        if (uuid == null || message == null || message.isEmpty()) return;
        runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        });
    }

    public <T> CompletableFuture<T> supplyOnMainThread(java.util.concurrent.Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runOnMainThread(() -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
