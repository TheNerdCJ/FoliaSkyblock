package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandUpgrade;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class IslandUpgradeManager {
    private final FoliaSkyblock plugin;

    public IslandUpgradeManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Map<String, Integer>> getIslandUpgrades(String islandId) {
        return plugin.getDatabaseManager().loadIslandUpgrades(islandId);
    }

    public CompletableFuture<Boolean> purchaseUpgrade(Player player, Island island, IslandUpgrade upgrade) {
        return CompletableFuture.supplyAsync(() -> {
            String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();

            Map<String, Integer> upgrades = plugin.getDatabaseManager()
                    .loadIslandUpgrades(islandId)
                    .join();

            int currentLevel = upgrades.getOrDefault(upgrade.name(), 0);

            if (currentLevel >= upgrade.getMaxLevel()) {
                player.sendMessage("§cThis upgrade is already at maximum level!");
                return false;
            }

            double cost = upgrade.getCost() * (currentLevel + 1);
            double balance = plugin.getEconomyManager().getIslandBalance(island.getGridPosition()).join();

            if (balance < cost) {
                player.sendMessage("§cNot enough island funds! Need §e$" + String.format("%,.0f", cost));
                return false;
            }

            plugin.getEconomyManager().removeIslandBalance(island.getGridPosition(), cost).join();
            int newLevel = currentLevel + 1;
            plugin.getDatabaseManager()
                    .saveIslandUpgrade(islandId, upgrade.name(), newLevel)
                    .join();

            player.sendMessage("§a§lUpgrade Purchased! §e" + upgrade.getDisplayName() + " §7Level §e" + newLevel);
            return true;
        });
    }

    public int getUpgradeLevel(String islandId, IslandUpgrade upgrade) {
        Map<String, Integer> upgrades = plugin.getDatabaseManager()
                .loadIslandUpgrades(islandId)
                .join();
        return upgrades.getOrDefault(upgrade.name(), 0);
    }
}