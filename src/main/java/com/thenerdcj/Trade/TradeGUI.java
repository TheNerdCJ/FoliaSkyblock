package com.thenerdcj.Trade;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

/**
 * Complete TradeGUI with level-gated trades from trades.yml
 * Uses ISLAND balance (not player balance) for purchases
 */
public class TradeGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final Map<UUID, String> openTrades = new HashMap<>();
    private final Map<String, TradeItem> tradeItems = new HashMap<>();

    public TradeGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadTradesFromConfig();
    }

    /**
     * Load trades from trades.yml
     */
    private void loadTradesFromConfig() {
        File tradesFile = new File(plugin.getDataFolder(), "trades.yml");
        if (!tradesFile.exists()) {
            plugin.saveResource("trades.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(tradesFile);

        for (String key : config.getKeys(false)) {
            try {
                String material = config.getString(key + ".material", "DIAMOND");
                String name = config.getString(key + ".name", key);
                int cost = config.getInt(key + ".cost", 100);
                int levelReq = config.getInt(key + ".level", 1);
                String description = config.getString(key + ".description", "§7Special trade item");

                TradeItem item = new TradeItem(key, Material.valueOf(material), name, cost, levelReq, description);
                tradeItems.put(key, item);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load trade item: " + key);
            }
        }

        plugin.getLogger().info("§aLoaded " + tradeItems.size() + " trade items from trades.yml");
    }

    /**
     * Open the trade GUI for a player
     */
    public void openTradeGUI(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        int level = island != null ? island.getLevel() : 1;

        Inventory gui = Bukkit.createInventory(null, 54, "§6§lTrade Shop §7(Level " + level + ")");

        int slot = 0;

        // Add all loaded trade items
        for (TradeItem trade : tradeItems.values()) {
            if (level >= trade.levelReq && slot < 45) {
                addTradeItem(gui, slot++, trade);
            }
        }

        // Fill empty slots with glass panes
        for (int i = slot; i < 45; i++) {
            gui.setItem(i, createGlassPane());
        }

        // Navigation buttons
        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
        openTrades.put(player.getUniqueId(), "main");
    }

    private void addTradeItem(Inventory gui, int slot, TradeItem trade) {
        ItemStack item = new ItemStack(trade.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(trade.name);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + trade.description);
        lore.add("");
        lore.add("§eCost: §6$" + trade.cost);
        lore.add("§eLevel Required: §a" + trade.levelReq);
        lore.add("");
        lore.add("§aClick to purchase!");
        meta.setLore(lore);
        item.setItemMeta(meta);

        gui.setItem(slot, item);
    }

    private ItemStack createGlassPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§lClose");
        meta.setLore(Collections.singletonList("§7Click to close trade shop"));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onTradeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openTrades.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String displayName = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "";

        if (displayName.contains("Close")) {
            player.closeInventory();
            openTrades.remove(player.getUniqueId());
            return;
        }

        // Find matching trade item
        for (TradeItem trade : tradeItems.values()) {
            if (displayName.contains(trade.name.replace("§a", "").replace("§c", "").replace("§5", "").replace("§6", ""))) {
                processPurchase(player, trade);
                return;
            }
        }
    }

    private void processPurchase(Player player, TradeItem trade) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        int playerLevel = island != null ? island.getLevel() : 1;

        if (playerLevel < trade.levelReq) {
            player.sendMessage("§cYou need level §e" + trade.levelReq + "§c to purchase this!");
            return;
        }

        if (island == null) {
            player.sendMessage("§cYou must be on your island to make purchases!");
            return;
        }

        // Check island balance using EconomyManager
        double islandBalance = plugin.getEconomyManager().getIslandBalance(island.getGridPosition()).join();

        if (islandBalance < trade.cost) {
            player.sendMessage("§cYour island doesn't have enough money! Cost: §6$" + trade.cost);
            player.sendMessage("§7Current island balance: §6$" + String.format("%.2f", islandBalance));
            return;
        }

        // Deduct from island balance
        plugin.getEconomyManager().removeIslandBalance(island.getGridPosition(), trade.cost).join();

        // Give the item
        ItemStack purchasedItem = new ItemStack(trade.material);
        ItemMeta meta = purchasedItem.getItemMeta();
        meta.setDisplayName(trade.name);
        purchasedItem.setItemMeta(meta);

        player.getInventory().addItem(purchasedItem);
        player.sendMessage("§aSuccessfully purchased §e" + trade.name + "§a for §6$" + trade.cost);
        player.sendMessage("§7Island balance remaining: §6$" + String.format("%.2f", islandBalance - trade.cost));

        player.closeInventory();
        openTrades.remove(player.getUniqueId());
    }

    /**
     * Inner class to hold trade item data
     */
    private static class TradeItem {
        String key;
        Material material;
        String name;
        int cost;
        int levelReq;
        String description;

        TradeItem(String key, Material material, String name, int cost, int levelReq, String description) {
            this.key = key;
            this.material = material;
            this.name = name;
            this.cost = cost;
            this.levelReq = levelReq;
            this.description = description;
        }
    }

    public void open(Player player, Island island) {
        // Store the island context if needed
        if (island != null) {
            openTrades.put(player.getUniqueId(), island.getGridPosition().toString());
        }
        openTradeGUI(player);
    }
}