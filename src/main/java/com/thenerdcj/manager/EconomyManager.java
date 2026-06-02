package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * EconomyManager – Updated to take full advantage of DatabaseManager caching.
 *
 * Responsibilities:
 * - Player balance (used for Chest Shops)
 * - Island balance (used for Island Upgrades)
 * - Clean async API
 * - Leverages in-memory caching from DatabaseManager for hot island data
 */
public class EconomyManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;

    // Upkeep config (wired for perf/economy sink)
    private boolean upkeepEnabled = false;
    private double upkeepPercentPerHour = 0.5;
    private double upkeepMinBalance = 1000.0;

    public EconomyManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        loadUpkeepConfig();
    }

    private void loadUpkeepConfig() {
        var c = plugin.getConfig();
        upkeepEnabled = c.getBoolean("island.upkeep.enabled", false);
        upkeepPercentPerHour = c.getDouble("island.upkeep.percent-per-hour", 0.5);
        upkeepMinBalance = c.getDouble("island.upkeep.min-balance", 1000.0);
    }

    // ==================== PLAYER BALANCE (Chest Shops) ====================

    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return databaseManager.getPlayerBalance(uuid);
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double balance) {
        return databaseManager.setPlayerBalance(uuid, balance);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return databaseManager.addPlayerBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return databaseManager.removePlayerBalance(uuid, amount);
    }

    // ==================== ISLAND BALANCE (Upgrades) – Uses Caching ====================

    /**
     * Get island balance using GridPosition (leverages in-memory cache)
     */
    public CompletableFuture<Double> getIslandBalance(GridPosition pos) {
        return databaseManager.getIslandBalance(pos);
    }

    /**
     * Set island balance (updates cache immediately + marks dirty)
     */
    public CompletableFuture<Boolean> setIslandBalance(GridPosition pos, double balance) {
        return databaseManager.setIslandBalance(pos, balance);
    }

    public CompletableFuture<Boolean> addIslandBalance(GridPosition pos, double amount) {
        return databaseManager.addIslandBalance(pos, amount);
    }

    public CompletableFuture<Boolean> removeIslandBalance(GridPosition pos, double amount) {
        return databaseManager.removeIslandBalance(pos, amount);
    }

    // ==================== CONVENIENCE METHODS ====================

    /**
     * Get island balance by owner + dimension (most common usage)
     */
    public CompletableFuture<Double> getIslandBalance(UUID ownerUuid, World.Environment dimension) {
        GridPosition pos = plugin.getIslandManager().getGridPosition(ownerUuid, dimension);
        if (pos == null) {
            return CompletableFuture.completedFuture(0.0);
        }
        // Proper async chaining — no blocking join inside supplyAsync
        return databaseManager.getIslandBalance(pos);
    }

    public CompletableFuture<Boolean> addIslandBalance(UUID ownerUuid, World.Environment dimension, double amount) {
        GridPosition pos = plugin.getIslandManager().getGridPosition(ownerUuid, dimension);
        if (pos == null) return CompletableFuture.completedFuture(false);
        return addIslandBalance(pos, amount);
    }

    public CompletableFuture<Boolean> removeIslandBalance(UUID ownerUuid, World.Environment dimension, double amount) {
        return addIslandBalance(ownerUuid, dimension, -amount);
    }

    // ==================== HARDENED PLAY-TO-WIN SAFE ECONOMY METHODS ====================

    /**
     * Safely removes player balance only if they have enough. Prevents negative balances.
     * This is the preferred method for all player balance deductions.
     */
    public CompletableFuture<Boolean> tryRemovePlayerBalance(UUID uuid, double amount) {
        if (amount <= 0) return CompletableFuture.completedFuture(true);
        return getPlayerBalance(uuid).thenCompose(current -> {
            if (current < amount) return CompletableFuture.completedFuture(false);
            return removePlayerBalance(uuid, amount);
        });
    }

    /**
     * Safely removes island balance only if the island has enough. Prevents negative balances.
     * This is the preferred method for all island balance deductions (upgrades, etc.).
     */
    public CompletableFuture<Boolean> tryRemoveIslandBalance(GridPosition pos, double amount) {
        if (amount <= 0) return CompletableFuture.completedFuture(true);
        return getIslandBalance(pos).thenCompose(current -> {
            if (current < amount) return CompletableFuture.completedFuture(false);
            return removeIslandBalance(pos, amount);
        });
    }

    /**
     * Safe, atomic-style transfer from player to island balance.
     * Uses tryRemove to guarantee no negative balances can occur.
     */
    public CompletableFuture<Boolean> safeTransferPlayerToIsland(UUID playerUuid, GridPosition pos, double amount) {
        if (amount <= 0) return CompletableFuture.completedFuture(false);

        return tryRemovePlayerBalance(playerUuid, amount).thenCompose(success -> {
            if (!success) return CompletableFuture.completedFuture(false);
            return addIslandBalance(pos, amount);
        });
    }

    // Legacy transfer methods kept for compatibility but marked deprecated in favor of safe versions above.
    @Deprecated
    public CompletableFuture<Boolean> transferPlayerToIsland(UUID player, GridPosition pos, double amount) {
        return safeTransferPlayerToIsland(player, pos, amount);
    }

    /**
     * Example: Transfer from island balance to player balance
     */
    public CompletableFuture<Boolean> transferIslandToPlayer(GridPosition pos, UUID player, double amount) {
        return removeIslandBalance(pos, amount)
                .thenCompose(success -> {
                    if (success) {
                        return addPlayerBalance(player, amount);
                    }
                    return CompletableFuture.completedFuture(false);
                });
    }

    // ==================== COMPATIBILITY ALIASES (for legacy code) ====================
    // Many parts of the codebase were written expecting these simpler names.
    // Prefer the getPlayerBalance / setPlayerBalance / removePlayerBalance methods going forward.

    @Deprecated
    public CompletableFuture<Double> getBalance(UUID uuid) {
        return getPlayerBalance(uuid);
    }

    @Deprecated
    public CompletableFuture<Boolean> setBalance(UUID uuid, double balance) {
        return setPlayerBalance(uuid, balance);
    }

    @Deprecated
    public CompletableFuture<Boolean> removeBalance(UUID uuid, double amount) {
        return removePlayerBalance(uuid, amount);
    }

    // ==================== ECONOMY SINKS (for inflation control / Play-to-Win sustainability) ====================

    /**
     * Island upkeep / tax sink.
     * Called periodically (if enabled in config) to drain a small % or flat from island balance.
     * Prevents pure money creation without sinks (taxes, prestige, fuel, trading fees).
     * Uses the hardened tryRemove path internally.
     * Full periodic task wired in FoliaSkyblock (hourly repeating on main, per-island apply async).
     * Config loaded at construction.
     */
    public CompletableFuture<Double> applyIslandUpkeepTax(GridPosition pos) {
        // Full impl for optimization suggestions: uses loaded config (wired in ctor), applies % tax using hardened tryRemove.
        // Called from periodic Folia task (see FoliaSkyblock).
        if (!upkeepEnabled) return CompletableFuture.completedFuture(0.0);
        return getIslandBalance(pos).thenCompose(balance -> {
            if (balance <= upkeepMinBalance) return CompletableFuture.completedFuture(0.0);
            double pct = upkeepPercentPerHour / 100.0;
            double tax = Math.max(0, balance * pct);
            if (tax <= 0) return CompletableFuture.completedFuture(0.0);
            return tryRemoveIslandBalance(pos, tax).thenApply(success -> {
                if (success) {
                    plugin.getLogger().info("[Economy] Applied island upkeep tax of " + String.format("%.2f", tax) + " to " + pos);
                    return tax;
                }
                return 0.0;
            });
        });
    }

    /**
     * Harden note: callers should prefer tryRemove* and safeTransfer* exclusively for mutations.
     * Direct add/remove still exist for internal/trusted paths but are being phased to private where possible.
     */
}
