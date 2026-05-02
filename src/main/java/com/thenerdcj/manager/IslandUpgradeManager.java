package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandUpgrade;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages island upgrades purchased with island balance.
 * Separate from the leveling system.
 */
public class IslandUpgradeManager {

    private final FoliaSkyblock plugin;

    // Cache: Island ID -> Map<Upgrade, Level>
    private final Map<String, Map<IslandUpgrade, Integer>> upgradeCache = new ConcurrentHashMap<>();

    public IslandUpgradeManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadAllUpgrades();
    }

    private void loadAllUpgrades() {
        plugin.getLogger().info("§aIsland Upgrade Manager initialized (lazy-load from database)");
    }

    public int getUpgradeLevel(GridPosition pos, IslandUpgrade upgrade) {
        String islandId = pos.toString();

        if (upgradeCache.containsKey(islandId)) {
            return upgradeCache.get(islandId).getOrDefault(upgrade, 0);
        }

        try {
            Map<String, Integer> dbUpgrades = plugin.getDatabaseManager().loadIslandUpgrades(islandId);
            Map<IslandUpgrade, Integer> cachedUpgrades = new HashMap<>();

            for (Map.Entry<String, Integer> entry : dbUpgrades.entrySet()) {
                try {
                    IslandUpgrade up = IslandUpgrade.valueOf(entry.getKey());
                    cachedUpgrades.put(up, entry.getValue());
                } catch (IllegalArgumentException ignored) {}
            }

            upgradeCache.put(islandId, cachedUpgrades);
            return cachedUpgrades.getOrDefault(upgrade, 0);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load upgrades for island " + islandId);
            return 0;
        }
    }

    public boolean purchaseUpgrade(Player player, Island island, IslandUpgrade upgrade) {
        GridPosition pos = island.getGridPosition();
        int currentLevel = getUpgradeLevel(pos, upgrade);
        int cost = upgrade.getCostForLevel(currentLevel);

        if (island.getLevel() < upgrade.getLevelReq()) {
            player.sendMessage("§cYour island needs to be level §e" + upgrade.getLevelReq() + "§c to purchase this upgrade!");
            return false;
        }

        double balance = plugin.getEconomyManager().getIslandBalance(pos).join();
        if (balance < cost) {
            player.sendMessage("§cNot enough island balance! Need §e$" + cost);
            return false;
        }

        plugin.getEconomyManager().removeIslandBalance(pos, cost);

        Map<IslandUpgrade, Integer> islandUpgrades = upgradeCache.computeIfAbsent(pos.toString(), k -> new HashMap<>());
        islandUpgrades.put(upgrade, currentLevel + 1);

        plugin.getDatabaseManager().saveIslandUpgrade(pos.toString(), upgrade.name(), currentLevel + 1);

        player.sendMessage("§a§lUpgrade Purchased! §e" + upgrade.getDisplayName() + " §7(Level " + (currentLevel + 1) + ")");
        return true;
    }

    public void saveAllUpgrades() {
        int saved = 0;
        for (Map.Entry<String, Map<IslandUpgrade, Integer>> entry : upgradeCache.entrySet()) {
            String islandId = entry.getKey();
            for (Map.Entry<IslandUpgrade, Integer> upgradeEntry : entry.getValue().entrySet()) {
                plugin.getDatabaseManager().saveIslandUpgrade(islandId, upgradeEntry.getKey().name(), upgradeEntry.getValue());
                saved++;
            }
        }
        plugin.getLogger().info("§aSaved " + saved + " island upgrades to database.");
    }
}