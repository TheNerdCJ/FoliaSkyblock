package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Lightweight helper for common database async patterns.
 * Goal: reduce the massive boilerplate in DatabaseManager and eventually
 * allow full migration away from the custom 10-thread ExecutorService
 * toward Folia's AsyncScheduler for better lifecycle integration.
 */
public final class DBOperations {

    private final FoliaSkyblock plugin;
    private final ExecutorService legacyExecutor; // temporary bridge during migration
    private final java.util.function.Supplier<Connection> connectionSupplier;
    private final boolean h2Dialect;

    public DBOperations(FoliaSkyblock plugin, ExecutorService legacyExecutor, java.util.function.Supplier<Connection> connectionSupplier) {
        this(plugin, legacyExecutor, connectionSupplier, false);
    }

    public DBOperations(FoliaSkyblock plugin, ExecutorService legacyExecutor, java.util.function.Supplier<Connection> connectionSupplier, boolean h2Dialect) {
        this.plugin = plugin;
        this.legacyExecutor = legacyExecutor;
        this.connectionSupplier = connectionSupplier;
        this.h2Dialect = h2Dialect;
    }

    public DBOperations(FoliaSkyblock plugin, ExecutorService legacyExecutor) {
        this(plugin, legacyExecutor, null, false);
    }

    public boolean isH2Dialect() {
        return h2Dialect;
    }

    /**
     * Run blocking DB work asynchronously.
     * On Folia, prefers the plugin AsyncScheduler when possible.
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> work) {
        if (plugin.isFolia() && plugin.getServer() != null) {
            CompletableFuture<T> future = new CompletableFuture<>();
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    future.complete(work.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        }
        return CompletableFuture.supplyAsync(work, legacyExecutor);
    }

    /**
     * Fire-and-forget async DB write (common pattern).
     */
    public void runAsync(Runnable work) {
        supplyAsync(() -> {
            work.run();
            return null;
        });
    }

    /**
     * Execute code with a fresh Connection (handles try-with-resources).
     * Useful for DAOs to reduce boilerplate.
     */
    public <T> T withConnection(Function<Connection, T> action) throws SQLException {
        if (connectionSupplier == null) {
            throw new IllegalStateException("No connection supplier configured for DBOperations");
        }
        try (Connection conn = connectionSupplier.get()) {
            return action.apply(conn);
        }
    }

    public FoliaSkyblock getPlugin() {
        return plugin;
    }
}