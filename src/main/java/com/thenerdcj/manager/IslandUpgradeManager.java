package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.manager.EconomyManager;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * IslandUpgradeManager – Updated to use cached EconomyManager methods.
 *
 * Benefits:
 * - Much faster balance checks and deductions (in-memory cache)
 * - Consistent with the new dual-economy design
 * - Cleaner code and better performance under load
 */
public class IslandUpgradeManager {

    private final FoliaSkyblock plugin;
    private final EconomyManager economyManager;

    public IslandUpgradeManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }

    /**
     * Apply an upgrade to an island.
     * This is the main method you should call from GUI or commands.
     */
    public CompletableFuture<Boolean> applyUpgrade(Island island, IslandUpgrade upgrade, Player player) {
        GridPosition pos = island.getGridPosition();
        UUID owner = island.getOwnerUuid();

        // 1. Check if player has enough island balance (uses cache)
        return economyManager.getIslandBalance(pos).thenCompose(currentBalance -> {

            if (currentBalance < upgrade.getCost()) {
                player.sendMessage("§cYour island does not have enough balance for this upgrade.");
                return CompletableFuture.completedFuture(false);
            }

            // 2. Deduct the cost using cached method
            return economyManager.removeIslandBalance(pos, upgrade.getCost()).thenCompose(success -> {
                if (!success) {
                    player.sendMessage("§cFailed to deduct island balance. Please try again.");
                    return CompletableFuture.completedFuture(false);
                }

                // 3. Apply the upgrade (your existing logic)
                boolean applied = applyUpgradeEffect(island, upgrade);

                if (applied) {
                    player.sendMessage("§aUpgrade purchased successfully! §7(-$" + upgrade.getCost() + ")");
                    // Optional: Save upgrade level to database here
                    return CompletableFuture.completedFuture(true);
                } else {
                    // Refund if upgrade failed to apply
                    return economyManager.addIslandBalance(pos, upgrade.getCost()).thenApply(refundSuccess -> {
                        player.sendMessage("§cUpgrade failed to apply. Your balance has been refunded.");
                        return false;
                    });
                }
            });
        });
    }

    /**
     * Internal method that actually applies the upgrade effect.
     * Replace this with your real upgrade logic (e.g. generator speed, size increase, etc.)
     */
    private boolean applyUpgradeEffect(Island island, IslandUpgrade upgrade) {
        // TODO: Put your real upgrade logic here
        // Example:
        // if (upgrade instanceof GeneratorSpeedUpgrade) { ... }

        System.out.println("[IslandUpgradeManager] Applied upgrade level " + upgrade.getLevel());
        return true;
    }

    /**
     * Get current island balance (cached)
     */
    public CompletableFuture<Double> getIslandBalance(Island island) {
        return economyManager.getIslandBalance(island.getGridPosition());
    }

    /**
     * Add balance to an island (cached)
     */
    public CompletableFuture<Boolean> addIslandBalance(Island island, double amount) {
        return economyManager.addIslandBalance(island.getGridPosition(), amount);
    }

    /**
     * Remove balance from an island (cached)
     */
    public CompletableFuture<Boolean> removeIslandBalance(Island island, double amount) {
        return economyManager.removeIslandBalance(island.getGridPosition(), amount);
    }
}
