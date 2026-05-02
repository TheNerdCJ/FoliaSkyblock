package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.IslandBank;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island Bank Manager - Folia-optimized bank system
 */
public class IslandBankManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final Map<GridPosition, IslandBank> bankCache = new ConcurrentHashMap<>();

    public IslandBankManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        createBankTable();
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupCache, 36000L, 36000L);
    }

    private void createBankTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS island_banks (
                grid_x INTEGER,
                grid_z INTEGER,
                dimension TEXT,
                balance REAL DEFAULT 0.0,
                PRIMARY KEY (grid_x, grid_z, dimension)
            )
            """;
        databaseManager.executeUpdate(sql);
    }

    public CompletableFuture<IslandBank> getBank(GridPosition pos) {
        IslandBank cached = bankCache.get(pos);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT balance FROM island_banks WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                ResultSet rs = stmt.executeQuery();

                IslandBank bank = new IslandBank(pos);
                if (rs.next()) bank.setBalance(rs.getDouble("balance"));

                bankCache.put(pos, bank);
                return bank;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load island bank: " + e.getMessage());
                return new IslandBank(pos);
            }
        });
    }

    public CompletableFuture<Void> saveBank(IslandBank bank) {
        GridPosition pos = bank.getGridPosition();
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO island_banks (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                stmt.setInt(1, pos.getX());
                stmt.setInt(2, pos.getZ());
                stmt.setString(3, pos.getDimension().name());
                stmt.setDouble(4, bank.getBalance());
                stmt.executeUpdate();
                bankCache.put(pos, bank);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save island bank: " + e.getMessage());
            }
        });
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
        if (bankCache.size() > 500) bankCache.clear();
    }
}
