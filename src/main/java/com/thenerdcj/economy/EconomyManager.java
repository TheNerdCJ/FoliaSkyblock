package com.thenerdcj.economy;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyManager {

    private final FoliaSkyblock plugin;

    private final double defaultPlayerBalance;
    private final double defaultIslandBalance;
    private final boolean allowNegativeBalances;
    private final String currencySymbol;

    public EconomyManager(FoliaSkyblock plugin) {
        this.plugin = plugin;

        // Load from the latest config.yml (economy section)
        this.defaultPlayerBalance = plugin.getConfig().getDouble("economy.default-player-balance", 0.0);
        this.defaultIslandBalance = plugin.getConfig().getDouble("economy.default-island-balance", 1500.0);
        this.allowNegativeBalances = plugin.getConfig().getBoolean("economy.allow-negative", false);
        this.currencySymbol = plugin.getConfig().getString("economy.currency-symbol", "$");

        plugin.getLogger().info("§aEconomyManager loaded from config.yml");
        plugin.getLogger().info("   §7• Player starting balance: " + currencySymbol + defaultPlayerBalance);
        plugin.getLogger().info("   §7• Island starting balance: " + currencySymbol + defaultIslandBalance);
        plugin.getLogger().info("   §7• Allow negative balances: " + allowNegativeBalances);
    }

    // ====================== PLAYER ECONOMY ======================
    public CompletableFuture<Double> getBalance(UUID uuid) {
        return plugin.getDatabaseManager().getPlayerBalance(uuid);
    }

    public CompletableFuture<Double> getBalance(Player player) {
        return getBalance(player.getUniqueId());
    }

    public CompletableFuture<Boolean> setPlayerBalance(UUID uuid, double amount) {
        if (!allowNegativeBalances && amount < 0) {
            amount = 0.0;
        }
        return plugin.getDatabaseManager().setPlayerBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> addPlayerBalance(UUID uuid, double amount) {
        return plugin.getDatabaseManager().addPlayerBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> removePlayerBalance(UUID uuid, double amount) {
        return plugin.getDatabaseManager().removePlayerBalance(uuid, amount);
    }

    // ====================== ISLAND ECONOMY ======================
    public CompletableFuture<Double> getIslandBalance(GridPosition pos) {
        return plugin.getDatabaseManager().getIslandBalance(pos);
    }

    public CompletableFuture<Boolean> setIslandBalance(GridPosition pos, double amount) {
        if (!allowNegativeBalances && amount < 0) {
            amount = 0.0;
        }
        return plugin.getDatabaseManager().setIslandBalance(pos, amount);
    }

    public CompletableFuture<Boolean> addIslandBalance(GridPosition pos, double amount) {
        return plugin.getDatabaseManager().addIslandBalance(pos, amount);
    }

    public CompletableFuture<Boolean> removeIslandBalance(GridPosition pos, double amount) {
        return plugin.getDatabaseManager().removeIslandBalance(pos, amount);
    }

    // ====================== CONFIG GETTERS ======================
    public double getDefaultPlayerBalance() {
        return defaultPlayerBalance;
    }

    public double getDefaultIslandBalance() {
        return defaultIslandBalance;
    }

    public boolean isNegativeAllowed() {
        return allowNegativeBalances;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }
}