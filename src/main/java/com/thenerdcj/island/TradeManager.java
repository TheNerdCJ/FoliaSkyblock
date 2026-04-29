package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TradeManager {

    private final List<Trade> trades = new ArrayList<>();

    public TradeManager(FoliaSkyblock plugin) {
        loadTrades(plugin);
    }

    private void loadTrades(FoliaSkyblock plugin) {
        trades.clear();

        File file = new File(plugin.getDataFolder(), "trades.yml");
        if (!file.exists()) {
            plugin.saveResource("trades.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> tradeList = config.getMapList("trades");

        for (Map<?, ?> map : tradeList) {
            try {
                String inputStr = (String) map.get("input");
                String outputStr = (String) map.get("output");
                int level = (int) map.get("level-required");
                String description = (String) map.get("description");

                String[] inputParts = inputStr.split(":");
                String[] outputParts = outputStr.split(":");

                Material inputMat = Material.valueOf(inputParts[0].toUpperCase());
                int inputAmount = inputParts.length > 1 ? Integer.parseInt(inputParts[1]) : 1;

                Material outputMat = Material.valueOf(outputParts[0].toUpperCase());
                int outputAmount = outputParts.length > 1 ? Integer.parseInt(outputParts[1]) : 1;

                trades.add(new Trade(
                        new ItemStack(inputMat, inputAmount),
                        new ItemStack(outputMat, outputAmount),
                        level,
                        description
                ));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load trade: " + map);
            }
        }

        plugin.getLogger().info("§aLoaded §f" + trades.size() + " §atrades from trades.yml");
    }

    public List<Trade> getAvailableTrades(int islandLevel) {
        return trades.stream()
                .filter(trade -> trade.levelRequired() <= islandLevel)
                .toList();
    }

    public Trade getTrade(int index) {
        if (index < 0 || index >= trades.size()) return null;
        return trades.get(index);
    }
}