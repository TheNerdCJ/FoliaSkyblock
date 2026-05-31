package com.thenerdcj.Trade;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/**
 * Enhanced TradeGUI with:
 * - Pagination support
 * - Custom InventoryHolder for reliable state tracking
 * - PersistentDataContainer on items for robust action identification (no fragile display name parsing)
 * - Confirmation dialog before purchase
 */
public class TradeGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final Map<UUID, TradeGUIHolder> openGUIs = new HashMap<>(); // Better tracking
    private final Map<String, TradeItem> tradeItems = new HashMap<>();
    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey ITEM_KEY;
    private final NamespacedKey PAGE_KEY;

    public TradeGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public TradeGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "trade_action");
        this.ITEM_KEY = new NamespacedKey(plugin, "trade_item_key");
        this.PAGE_KEY = new NamespacedKey(plugin, "trade_page");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
        loadTradesFromConfig();
    }

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
     * Custom InventoryHolder to hold GUI state (page, context, etc.)
     */
    public static class TradeGUIHolder implements InventoryHolder {
        private final int currentPage;
        private final String context; // e.g., "main", island info, or pending purchase key
        private final TradeItem pendingPurchase; // For confirmation dialog

        public TradeGUIHolder(int currentPage, String context, TradeItem pendingPurchase) {
            this.currentPage = currentPage;
            this.context = context;
            this.pendingPurchase = pendingPurchase;
        }

        public int getCurrentPage() { return currentPage; }
        public String getContext() { return context; }
        public TradeItem getPendingPurchase() { return pendingPurchase; }

        @Override
        public Inventory getInventory() {
            return null; // Not needed for holder pattern in this use case
        }
    }

    public void openTradeGUI(Player player) {
        openTradeGUI(player, 0);
    }

    public void openTradeGUI(Player player, int page) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        int level = island != null ? island.getLevel() : 1;

        List<TradeItem> availableItems = new ArrayList<>();
        for (TradeItem trade : tradeItems.values()) {
            if (level >= trade.levelReq) {
                availableItems.add(trade);
            }
        }

        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil((double) availableItems.size() / itemsPerPage);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        Inventory gui = Bukkit.createInventory(new TradeGUIHolder(page, "main", null), 54,
                "§6§lTrade Shop §7(Page " + (page + 1) + "/" + Math.max(1, totalPages) + " | Lvl " + level + ")");

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            addTradeItem(gui, i - startIndex, availableItems.get(i));
        }

        // Fill empty slots
        for (int i = endIndex - startIndex; i < 45; i++) {
            gui.setItem(i, createGlassPane());
        }

        // Navigation
        if (page > 0) {
            gui.setItem(45, createNavButton("§a§lPrevious Page", "prev", page));
        }
        if (page < totalPages - 1) {
            gui.setItem(53, createNavButton("§a§lNext Page", "next", page));
        }
        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), (TradeGUIHolder) gui.getHolder());
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

        // Use PersistentDataContainer for reliable identification
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "purchase");
        pdc.set(ITEM_KEY, PersistentDataType.STRING, trade.key);

        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }

    private ItemStack createNavButton(String name, String action, int currentPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, action);
        pdc.set(PAGE_KEY, PersistentDataType.INTEGER, currentPage);
        item.setItemMeta(meta);
        return item;
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
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "close");
        item.setItemMeta(meta);
        return item;
    }

    // ==================== CONFIRMATION DIALOG ====================
    private void openConfirmationDialog(Player player, TradeItem trade) {
        Inventory confirmInv = Bukkit.createInventory(new TradeGUIHolder(0, "confirm", trade), 27, "§c§lConfirm Purchase?");

        // Info item
        ItemStack info = new ItemStack(trade.material);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(trade.name);
        List<String> lore = new ArrayList<>();
        lore.add("§7Cost: §6$" + trade.cost);
        lore.add("§7Your Island Balance will be deducted.");
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        confirmInv.setItem(13, info);

        // Confirm button
        ItemStack confirm = new ItemStack(Material.GREEN_WOOL);
        ItemMeta cMeta = confirm.getItemMeta();
        cMeta.setDisplayName("§a§lCONFIRM PURCHASE");
        PersistentDataContainer cPdc = cMeta.getPersistentDataContainer();
        cPdc.set(ACTION_KEY, PersistentDataType.STRING, "confirm_purchase");
        cPdc.set(ITEM_KEY, PersistentDataType.STRING, trade.key);
        confirm.setItemMeta(cMeta);
        confirmInv.setItem(11, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta caMeta = cancel.getItemMeta();
        caMeta.setDisplayName("§c§lCANCEL");
        PersistentDataContainer caPdc = caMeta.getPersistentDataContainer();
        caPdc.set(ACTION_KEY, PersistentDataType.STRING, "cancel");
        cancel.setItemMeta(caMeta);
        confirmInv.setItem(15, cancel);

        player.openInventory(confirmInv);
        openGUIs.put(player.getUniqueId(), (TradeGUIHolder) confirmInv.getHolder());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof TradeGUIHolder guiHolder)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        String itemKey = pdc.get(ITEM_KEY, PersistentDataType.STRING);
        Integer pageData = pdc.get(PAGE_KEY, PersistentDataType.INTEGER);

        switch (action) {
            case "close":
            case "cancel":
                player.closeInventory();
                openGUIs.remove(player.getUniqueId());
                break;

            case "prev":
                int prevPage = (pageData != null ? pageData : guiHolder.getCurrentPage()) - 1;
                player.closeInventory();
                openTradeGUI(player, Math.max(0, prevPage));
                break;

            case "next":
                int nextPage = (pageData != null ? pageData : guiHolder.getCurrentPage()) + 1;
                player.closeInventory();
                openTradeGUI(player, nextPage);
                break;

            case "purchase":
                if (itemKey != null && tradeItems.containsKey(itemKey)) {
                    TradeItem trade = tradeItems.get(itemKey);
                    player.closeInventory();
                    openConfirmationDialog(player, trade);
                }
                break;

            case "confirm_purchase":
                if (itemKey != null && tradeItems.containsKey(itemKey)) {
                    TradeItem trade = tradeItems.get(itemKey);
                    player.closeInventory();
                    processPurchase(player, trade);
                    openGUIs.remove(player.getUniqueId());
                }
                break;
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

        // Do economy work asynchronously, then schedule final actions on main thread (Folia-safe)
        plugin.getEconomyManager().getIslandBalance(island.getGridPosition()).thenAccept(islandBalance -> {
            if (islandBalance < trade.cost) {
                plugin.getThreadSafety().sendMessageSafely(player, "§cNot enough island funds! Need §e$" + trade.cost);
                return;
            }

            plugin.getEconomyManager().removeIslandBalance(island.getGridPosition(), trade.cost).thenRun(() -> {
                plugin.getThreadSafety().runOnMainThread(() -> {
                    ItemStack purchasedItem = new ItemStack(trade.material);
                    ItemMeta meta = purchasedItem.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(trade.name);
                        purchasedItem.setItemMeta(meta);
                    }
                    player.getInventory().addItem(purchasedItem);
                    player.sendMessage("§aPurchased §e" + trade.name + " §afor §6$" + trade.cost);
                    player.sendMessage("§7Remaining island balance: §6$" + String.format("%.2f", islandBalance - trade.cost));
                });
            });
        });
    }

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
        openTradeGUI(player);
    }
}
