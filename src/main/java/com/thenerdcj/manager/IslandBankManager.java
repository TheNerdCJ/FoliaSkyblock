package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.database.IslandDAO;
import com.thenerdcj.island.IslandBank;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island Bank Manager - Folia-optimized bank system
 */
public class IslandBankManager {

    private final FoliaSkyblock plugin;
    // Real LRU for large scale (access-order LinkedHashMap like worthCache for compression)
    private final Map<GridPosition, IslandBank> bankCache = Collections.synchronizedMap(new LinkedHashMap<GridPosition, IslandBank>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GridPosition, IslandBank> eldest) {
            return size() > 500;
        }
    });

    public IslandBankManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Table creation is now centralized in DatabaseManager.createTables()
        // Persistence fully delegated to IslandDAO (withConnection)
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCache, 36000L, 36000L);
    }

    public CompletableFuture<IslandBank> getBank(GridPosition pos) {
        IslandBank cached = bankCache.get(pos);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        // Delegate to IslandDAO (withConnection promoted; removes direct conn from manager - DB modularization step)
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        return dao.loadIslandBankBalance(pos).thenApply(bal -> {
            IslandBank bank = new IslandBank(pos);
            bank.setBalance(bal);
            bankCache.put(pos, bank);
            return bank;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load island bank via DAO: " + ex.getMessage());
            return new IslandBank(pos);
        });
    }

    public CompletableFuture<Void> saveBank(IslandBank bank) {
        GridPosition pos = bank.getGridPosition();
        // Delegate to IslandDAO (fire-and-forget write via runAsync + withConnection)
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        dao.saveIslandBankBalance(pos, bank.getBalance());
        bankCache.put(pos, bank);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> deposit(GridPosition pos, double amount) {
        return getBank(pos).thenCompose(bank -> {
            bank.deposit(amount);
            return saveBank(bank).thenApply(v -> true);
        });
    }

    public CompletableFuture<Boolean> withdraw(GridPosition pos, double amount) {
        return getBank(pos).thenCompose(bank -> {
            boolean success = bank.withdraw(amount);
            if (success) return saveBank(bank).thenApply(v -> true);
            return CompletableFuture.completedFuture(false);
        });
    }

    public double getBalanceSync(GridPosition pos) {
        IslandBank bank = bankCache.get(pos);
        return bank != null ? bank.getBalance() : 0.0;
    }

    private void cleanupCache() {
        // Real LRU now via LinkedHashMap access-order removeEldest (auto evicts when >500); no manual needed.
    }
}
