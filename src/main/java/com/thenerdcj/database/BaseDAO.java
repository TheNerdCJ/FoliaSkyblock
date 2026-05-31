package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for all Data Access Objects.
 * Provides common async execution and connection handling patterns.
 * This is the foundation for fully modularizing DatabaseManager.
 */
public abstract class BaseDAO {

    protected final FoliaSkyblock plugin;
    protected final DBOperations dbOps;

    public BaseDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        this.plugin = plugin;
        this.dbOps = dbOps;
    }

    /**
     * Execute a block of code with a fresh database connection.
     * Handles resource management.
     */
    protected <T> T withConnection(Function<Connection, T> action) {
        try {
            return dbOps.withConnection(action);
        } catch (SQLException e) {
            plugin.getLogger().severe("[" + this.getClass().getSimpleName() + "] Database operation failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Run a blocking database operation asynchronously using the project's DBOperations layer.
     * This ensures consistent Folia async behavior.
     */
    protected <T> CompletableFuture<T> supplyAsync(Supplier<T> work) {
        return dbOps.supplyAsync(work);
    }

    /**
     * Fire-and-forget async write operation.
     */
    protected void runAsync(Runnable work) {
        dbOps.runAsync(work);
    }

    public abstract void initialize();
}