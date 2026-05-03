package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyManager {

    private final FoliaSkyblock plugin;

    public EconomyManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Double> getBalance(UUID uuid) {
        return plugin.getDatabaseManager().getPlayerBalance(uuid);
    }

    public CompletableFuture<Boolean> setBalance(UUID uuid, double amount) {
        return plugin.getDatabaseManager().setPlayerBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, double amount) {
        return plugin.getDatabaseManager().addPlayerBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, double amount) {
        return plugin.getDatabaseManager().removePlayerBalance(uuid, amount);
    }

    // ====================== ISLAND BALANCE (for upgrades & trades) ======================
    public CompletableFuture<Double> getIslandBalance(com.thenerdcj.database.GridPosition pos) {
        return plugin.getDatabaseManager().getIslandBalance(
                pos.getX(), pos.getZ(), pos.getDimension().name()
        );
    }

    public CompletableFuture<Boolean> setIslandBalance(com.thenerdcj.database.GridPosition pos, double amount) {
        return plugin.getDatabaseManager().setIslandBalance(
                pos.getX(), pos.getZ(), pos.getDimension().name(), amount
        );
    }

    public CompletableFuture<Boolean> addIslandBalance(com.thenerdcj.database.GridPosition pos, double amount) {
        return plugin.getDatabaseManager().addIslandBalance(
                pos.getX(), pos.getZ(), pos.getDimension().name(), amount
        );
    }

    public CompletableFuture<Boolean> removeIslandBalance(com.thenerdcj.database.GridPosition pos, double amount) {
        return plugin.getDatabaseManager().removeIslandBalance(
                pos.getX(), pos.getZ(), pos.getDimension().name(), amount
        );
    }

    // ====================== PLAYER BALANCE (alias for compatibility) ======================
    public CompletableFuture<Double> getPlayerBalance(UUID uuid) {
        return getBalance(uuid);
    }
    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return addBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return removeBalance(uuid, amount);
    }
}