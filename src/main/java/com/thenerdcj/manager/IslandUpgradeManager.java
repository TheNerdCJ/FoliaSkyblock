package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.manager.EconomyManager;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * IslandUpgradeManager – Fully updated to work with the clean IslandUpgrade enum.
 *
 * The current level is now passed in as a parameter (correct architecture).
 * Cost is calculated dynamically using getCostForLevel(currentLevel).
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
     *
     * @param island        The island receiving the upgrade
     * @param upgrade       The type of upgrade being purchased
     * @param currentLevel  The CURRENT level of this upgrade on the island (before this purchase)
     * @param player        The player initiating the purchase
     */
    public CompletableFuture<Boolean> applyUpgrade(Island island, IslandUpgrade upgrade, int currentLevel, Player player) {
        GridPosition pos = island.getGridPosition();

        // Calculate the cost for the NEXT level
        int cost = upgrade.getCostForLevel(currentLevel);

        return economyManager.getIslandBalance(pos).thenCompose(currentBalance -> {

            if (currentBalance < cost) {
                player.sendMessage("§cYour island does not have enough balance for this upgrade.");
                return CompletableFuture.completedFuture(false);
            }

            return economyManager.removeIslandBalance(pos, cost).thenCompose(success -> {
                if (!success) {
                    player.sendMessage("§cFailed to deduct island balance. Please try again.");
                    return CompletableFuture.completedFuture(false);
                }

                // Apply the upgrade effect (passing the new level)
                boolean applied = applyUpgradeEffect(island, upgrade, currentLevel + 1);

                if (applied) {
                    player.sendMessage("§aUpgrade purchased successfully! §7(-$" + cost + ")");
                    // TODO: Persist (currentLevel + 1) for this island + upgrade type in the database
                    return CompletableFuture.completedFuture(true);
                } else {
                    // Refund if application failed
                    return economyManager.addIslandBalance(pos, cost).thenApply(refund -> {
                        player.sendMessage("§cUpgrade failed to apply. Your balance has been refunded.");
                        return false;
                    });
                }
            });
        });
    }

    /**
     * Internal method that applies the actual upgrade effect.
     *
     * @param island    The island
     * @param upgrade   The upgrade type
     * @param newLevel  The level being upgraded TO
     */
    private boolean applyUpgradeEffect(Island island, IslandUpgrade upgrade, int newLevel) {
        // TODO: Implement your real upgrade logic here.
        //
        // Example:
        // switch (upgrade) {
        //     case ISLAND_SIZE     -> island.expandBorder(newLevel);
        //     case MINION_SLOTS    -> island.increaseMinionSlots(1);
        //     case MEMBER_LIMIT    -> island.increaseMemberLimit(1);
        //     case CROP_GROWTH     -> island.increaseCropGrowthBonus(0.25);
        //     case ORE_GENERATOR   -> island.upgradeOreGenerator(newLevel);
        //     ...
        // }

        System.out.println("[IslandUpgradeManager] Applied " + upgrade.getDisplayName() + " → Level " + newLevel);
        return true;
    }

    // ==================== HELPER METHODS (Cached) ====================

    public CompletableFuture<Double> getIslandBalance(Island island) {
        return economyManager.getIslandBalance(island.getGridPosition());
    }

    public CompletableFuture<Boolean> addIslandBalance(Island island, double amount) {
        return economyManager.addIslandBalance(island.getGridPosition(), amount);
    }

    public CompletableFuture<Boolean> removeIslandBalance(Island island, double amount) {
        return economyManager.removeIslandBalance(island.getGridPosition(), amount);
    }
}
