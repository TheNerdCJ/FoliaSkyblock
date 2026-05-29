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

    public EconomyManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
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

    // ==================== TRANSACTION HELPERS (Future Use) ====================

    /**
     * Example: Transfer from player balance to island balance (Play-to-Win safe)
     */
    public CompletableFuture<Boolean> transferPlayerToIsland(UUID player, GridPosition pos, double amount) {
        return removePlayerBalance(player, amount)
                .thenCompose(success -> {
                    if (success) {
                        return addIslandBalance(pos, amount);
                    }
                    return CompletableFuture.completedFuture(false);
                });
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
}
