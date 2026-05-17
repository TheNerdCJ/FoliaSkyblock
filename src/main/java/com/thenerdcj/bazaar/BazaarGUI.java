package com.thenerdcj.bazaar;

import com.thenerdcj.FoliaSkyblock;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full-featured BazaarGUI with all modern enhancements:
 * - Pagination for items and orders
 * - Custom InventoryHolder + PersistentDataContainer (robust clicks)
 * - Confirmation dialogs
 * - Anvil GUI for amount and price-per-unit input when creating orders
 * - Clean flows for Instant Buy/Sell + Limit Orders (Buy/Sell Orders)
 */
public class BazaarGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final BazaarManager bazaarManager;

    private final Map<UUID, BazaarGUIHolder> openGUIs = new ConcurrentHashMap<>();
    private final Map<UUID, PendingOrder> pendingOrders = new ConcurrentHashMap<>();

    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey MATERIAL_KEY;
    private final NamespacedKey PAGE_KEY;
    private final NamespacedKey ORDER_ID_KEY;
    private final NamespacedKey INPUT_TYPE_KEY; // For Anvil

    public BazaarGUI(FoliaSkyblock plugin, BazaarManager bazaarManager) {
        this.plugin = plugin;
        this.bazaarManager = bazaarManager;
        this.ACTION_KEY = new NamespacedKey(plugin, "bazaar_action");
        this.MATERIAL_KEY = new NamespacedKey(plugin, "bazaar_material");
        this.PAGE_KEY = new NamespacedKey(plugin, "bazaar_page");
        this.ORDER_ID_KEY = new NamespacedKey(plugin, "bazaar_order_id");
        this.INPUT_TYPE_KEY = new NamespacedKey(plugin, "bazaar_input_type");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ==================== CUSTOM HOLDER ====================
    public static class BazaarGUIHolder implements InventoryHolder {
        private final String context;      // "main", "item_detail", "orders", "confirm"
        private final String material;
        private final int page;
        private final String orderId;      // For specific order actions
        private final String pendingAction;

        public BazaarGUIHolder(String context, String material, int page, String orderId, String pendingAction) {
            this.context = context;
            this.material = material;
            this.page = page;
            this.orderId = orderId;
            this.pendingAction = pendingAction;
        }

        public String getContext() { return context; }
        public String getMaterial() { return material; }
        public int getPage() { return page; }
        public String getOrderId() { return orderId; }
        public String getPendingAction() { return pendingAction; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public static class PendingOrder {
        public final String material;
        public final String type; // "buy_order" or "sell_order"
        public Integer amount;
        public Double pricePerUnit;

        public PendingOrder(String material, String type) {
            this.material = material;
            this.type = type;
        }
    }

    // ==================== MAIN BAZAAR BROWSER ====================
    public void openMainBazaar(Player player) {
        openMainBazaar(player, 0);
    }

    public void openMainBazaar(Player player, int page) {
        Map<String, BazaarItem> items = bazaarManager.getAllBazaarItems();
        List<BazaarItem> itemList = new ArrayList<>(items.values());

        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil((double) itemList.size() / itemsPerPage);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        Inventory gui = Bukkit.createInventory(
                new BazaarGUIHolder("main", null, page, null, null),
                54, Component.text("§6§lBazaar"));

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, itemList.size());

        for (int i = start; i < end; i++) {
            addBazaarItemToGUI(gui, i - start, itemList.get(i));
        }

        for (int i = end - start; i < 45; i++) {
            gui.setItem(i, createGlassPane());
        }

        if (page > 0) gui.setItem(45, createNavButton("§a§l« Previous", "prev", page));
        if (page < totalPages - 1) gui.setItem(53, createNavButton("§a§lNext »", "next", page));
        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), (BazaarGUIHolder) gui.getHolder());
    }

    private void addBazaarItemToGUI(Inventory gui, int slot, BazaarItem item) {
        ItemStack stack = new ItemStack(Material.valueOf(item.getMaterial()));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("§e" + item.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7Instant Buy: §a$" + String.format("%.2f", item.getBuyPrice()) + " §7/ea"));
        lore.add(Component.text("§7Instant Sell: §c$" + String.format("%.2f", item.getSellPrice()) + " §7/ea"));
        lore.add(Component.text(""));
        lore.add(Component.text("§aClick to manage this item"));
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "open_item");
        pdc.set(MATERIAL_KEY, PersistentDataType.STRING, item.getMaterial());

        stack.setItemMeta(meta);
        gui.setItem(slot, stack);
    }

    // ==================== ITEM DETAIL MENU ====================
    public void openItemDetail(Player player, String material) {
        BazaarItem item = bazaarManager.getBazaarItem(material);
        if (item == null) {
            player.sendMessage("§cItem not found on Bazaar.");
            return;
        }

        Inventory gui = Bukkit.createInventory(
                new BazaarGUIHolder("item_detail", material, 0, null, null),
                54,
                "§6§l" + item.getDisplayName() + " §7Bazaar"
        );

        // Info item
        ItemStack info = new ItemStack(Material.valueOf(material));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e" + item.getDisplayName());
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Instant Buy Price: §a$" + String.format("%.2f", item.getBuyPrice()));
        infoLore.add("§7Instant Sell Price: §c$" + String.format("%.2f", item.getSellPrice()));
        infoLore.add("§7Stock: §e" + item.getStock());
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        // Action buttons
        gui.setItem(20, createActionButton("§a§lInstant Buy", "instant_buy", material, Material.GREEN_WOOL));
        gui.setItem(22, createActionButton("§c§lInstant Sell", "instant_sell", material, Material.RED_WOOL));
        gui.setItem(24, createActionButton("§b§lCreate Buy Order", "create_buy_order", material, Material.BLUE_WOOL));
        gui.setItem(29, createActionButton("§6§lCreate Sell Order", "create_sell_order", material, Material.ORANGE_WOOL));
        gui.setItem(31, createActionButton("§d§lView Buy Orders", "view_buy_orders", material, Material.PURPLE_WOOL));
        gui.setItem(33, createActionButton("§d§lView Sell Orders", "view_sell_orders", material, Material.PURPLE_WOOL));

        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), (BazaarGUIHolder) gui.getHolder());
    }

    private ItemStack createActionButton(String name, String action, String material, Material icon) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, action);
        pdc.set(MATERIAL_KEY, PersistentDataType.STRING, material);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== ORDERS VIEW (Paginated) ====================
    public void openOrdersView(Player player, String material, boolean isBuyOrders, int page) {
        List<BazaarOrder> orders = isBuyOrders
                ? bazaarManager.getBuyOrders(material)
                : bazaarManager.getSellOrders(material);

        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil((double) orders.size() / itemsPerPage);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        String title = isBuyOrders ? "§bBuy Orders" : "§6Sell Orders";
        Inventory gui = Bukkit.createInventory(
                new BazaarGUIHolder("orders", material, page, null, isBuyOrders ? "buy" : "sell"),
                54,
                "§6§l" + title + " §7(Page " + (page + 1) + "/" + Math.max(1, totalPages) + ")"
        );

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, orders.size());

        for (int i = start; i < end; i++) {
            addOrderItem(gui, i - start, orders.get(i), isBuyOrders);
        }

        for (int i = end - start; i < 45; i++) gui.setItem(i, createGlassPane());

        if (page > 0) gui.setItem(45, createNavButton("§a§l« Prev", "prev", page));
        if (page < totalPages - 1) gui.setItem(53, createNavButton("§a§lNext »", "next", page));
        gui.setItem(49, createBackButton(material));

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), (BazaarGUIHolder) gui.getHolder());
    }

    private void addOrderItem(Inventory gui, int slot, BazaarOrder order, boolean isBuy) {
        ItemStack item = new ItemStack(Material.valueOf(order.getMaterial()));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((isBuy ? "§bBuy" : "§6Sell") + " Order");

        List<String> lore = new ArrayList<>();
        lore.add("§7Amount: §e" + order.getAmount());
        lore.add("§7Price/Unit: §a$" + String.format("%.2f", order.getPricePerUnit()));
        lore.add("§7Total: §6$" + String.format("%,.0f", order.getTotalPrice()));
        lore.add("");
        lore.add("§7Click to §afulfill §7this order (if possible)");
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "fulfill_order");
        pdc.set(ORDER_ID_KEY, PersistentDataType.STRING, order.getId());
        pdc.set(MATERIAL_KEY, PersistentDataType.STRING, order.getMaterial());

        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }

    // ==================== ANVIL INPUT FOR AMOUNT & PRICE ====================
    private void openAnvilInput(Player player, String material, String inputType, String orderType) {
        // inputType: "amount" or "price"
        // orderType: "buy_order" or "sell_order"

        AnvilInventory anvil = (AnvilInventory) Bukkit.createInventory(null, InventoryType.ANVIL, "§6Enter " + inputType);

        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName("§eEnter " + (inputType.equals("amount") ? "amount" : "price per unit"));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "anvil_input");
        pdc.set(MATERIAL_KEY, PersistentDataType.STRING, material);
        pdc.set(INPUT_TYPE_KEY, PersistentDataType.STRING, inputType);
        // We store orderType in pendingOrders instead for simplicity

        paper.setItemMeta(meta);
        anvil.setItem(0, paper);

        player.openInventory(anvil);

        // Track pending state
        PendingOrder pending = pendingOrders.getOrDefault(player.getUniqueId(), new PendingOrder(material, orderType));
        pendingOrders.put(player.getUniqueId(), pending);
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (event.getInventory().getItem(0) == null) return;

        ItemMeta meta = event.getInventory().getItem(0).getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!"anvil_input".equals(pdc.get(ACTION_KEY, PersistentDataType.STRING))) return;

        // Allow any text input
        event.setResult(new ItemStack(Material.PAPER));
    }

    // ==================== CLICK HANDLER (Main Logic) ====================
    @EventHandler
    public void onBazaarClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BazaarGUIHolder guiHolder)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        String material = pdc.get(MATERIAL_KEY, PersistentDataType.STRING);
        String orderId = pdc.get(ORDER_ID_KEY, PersistentDataType.STRING);
        Integer pageData = pdc.get(PAGE_KEY, PersistentDataType.INTEGER);
        int currentPage = guiHolder.getPage();

        switch (action) {
            case "close":
                player.closeInventory();
                openGUIs.remove(player.getUniqueId());
                break;

            case "prev":
                int prev = (pageData != null ? pageData : currentPage) - 1;
                player.closeInventory();
                if ("main".equals(guiHolder.getContext())) {
                    openMainBazaar(player, Math.max(0, prev));
                } else if ("orders".equals(guiHolder.getContext())) {
                    boolean isBuy = "buy".equals(guiHolder.getPendingAction());
                    openOrdersView(player, guiHolder.getMaterial(), isBuy, Math.max(0, prev));
                }
                break;

            case "next":
                int next = (pageData != null ? pageData : currentPage) + 1;
                player.closeInventory();
                if ("main".equals(guiHolder.getContext())) {
                    openMainBazaar(player, next);
                } else if ("orders".equals(guiHolder.getContext())) {
                    boolean isBuy = "buy".equals(guiHolder.getPendingAction());
                    openOrdersView(player, guiHolder.getMaterial(), isBuy, next);
                }
                break;

            case "open_item":
                if (material != null) {
                    player.closeInventory();
                    openItemDetail(player, material);
                }
                break;

            case "instant_buy":
                if (material != null) {
                    player.closeInventory();
                    openInstantConfirm(player, material, true);
                }
                break;

            case "instant_sell":
                if (material != null) {
                    player.closeInventory();
                    openInstantConfirm(player, material, false);
                }
                break;

            case "create_buy_order":
                if (material != null) {
                    player.closeInventory();
                    startOrderCreation(player, material, "buy_order");
                }
                break;

            case "create_sell_order":
                if (material != null) {
                    player.closeInventory();
                    startOrderCreation(player, material, "sell_order");
                }
                break;

            case "view_buy_orders":
                if (material != null) {
                    player.closeInventory();
                    openOrdersView(player, material, true, 0);
                }
                break;

            case "view_sell_orders":
                if (material != null) {
                    player.closeInventory();
                    openOrdersView(player, material, false, 0);
                }
                break;

            case "fulfill_order":
                if (orderId != null && material != null) {
                    // TODO: Implement fulfill logic (match against existing orders or instant)
                    player.sendMessage("§eFulfill order feature coming soon! (Order ID: " + orderId + ")");
                    player.closeInventory();
                }
                break;

            case "back":
                if (material != null) {
                    player.closeInventory();
                    openItemDetail(player, material);
                }
                break;

            case "confirm_instant_buy":
                if (material != null) {
                    player.closeInventory();
                    bazaarManager.instantBuy(player, material, 1).thenAccept(success -> {
                        if (success) openItemDetail(player, material);
                    });
                }
                break;

            case "confirm_instant_sell":
                if (material != null) {
                    player.closeInventory();
                    bazaarManager.instantSell(player, material, 1).thenAccept(success -> {
                        if (success) openItemDetail(player, material);
                    });
                }
                break;
        }
    }

    private ItemStack createBackButton(String material) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7« Back to Item");
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "back");
        pdc.set(MATERIAL_KEY, PersistentDataType.STRING, material);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== CONFIRMATION DIALOGS ====================
    private void openInstantConfirm(Player player, String material, boolean isBuy) {
        BazaarItem item = bazaarManager.getBazaarItem(material);
        if (item == null) return;

        String action = isBuy ? "confirm_instant_buy" : "confirm_instant_sell";
        String title = isBuy ? "§aConfirm Instant Buy?" : "§cConfirm Instant Sell?";
        double price = isBuy ? item.getBuyPrice() : item.getSellPrice();

        Inventory confirm = Bukkit.createInventory(
                new BazaarGUIHolder("confirm", material, 0, null, isBuy ? "buy" : "sell"),
                27,
                title
        );

        ItemStack info = new ItemStack(Material.valueOf(material));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e1x " + item.getDisplayName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Price: §6$" + String.format("%.2f", price));
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        confirm.setItem(13, info);

        ItemStack confirmBtn = new ItemStack(isBuy ? Material.GREEN_WOOL : Material.RED_WOOL);
        ItemMeta cMeta = confirmBtn.getItemMeta();
        cMeta.setDisplayName(isBuy ? "§a§lCONFIRM BUY" : "§c§lCONFIRM SELL");
        PersistentDataContainer cPdc = cMeta.getPersistentDataContainer();
        cPdc.set(ACTION_KEY, PersistentDataType.STRING, action);
        cPdc.set(MATERIAL_KEY, PersistentDataType.STRING, material);
        confirmBtn.setItemMeta(cMeta);
        confirm.setItem(11, confirmBtn);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta caMeta = cancel.getItemMeta();
        caMeta.setDisplayName("§c§lCANCEL");
        PersistentDataContainer caPdc = caMeta.getPersistentDataContainer();
        caPdc.set(ACTION_KEY, PersistentDataType.STRING, "close");
        cancel.setItemMeta(caMeta);
        confirm.setItem(15, cancel);

        player.openInventory(confirm);
        openGUIs.put(player.getUniqueId(), (BazaarGUIHolder) confirm.getHolder());
    }

    // ==================== ORDER CREATION WITH ANVIL INPUT ====================
    private void startOrderCreation(Player player, String material, String orderType) {
        PendingOrder pending = new PendingOrder(material, orderType);
        pendingOrders.put(player.getUniqueId(), pending);

        // First ask for amount
        openAnvilInput(player, material, "amount", orderType);
        player.sendMessage("§ePlease enter the §6amount §ein the Anvil and click the output slot.");
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.PAPER) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!"anvil_input".equals(pdc.get(ACTION_KEY, PersistentDataType.STRING))) return;

        String material = pdc.get(MATERIAL_KEY, PersistentDataType.STRING);
        String inputType = pdc.get(INPUT_TYPE_KEY, PersistentDataType.STRING);
        PendingOrder pending = pendingOrders.get(player.getUniqueId());

        if (pending == null || !pending.material.equals(material)) {
            player.closeInventory();
            return;
        }

        // Get the text the player typed in the Anvil
        String inputText = event.getInventory().getItem(2) != null &&
                event.getInventory().getItem(2).hasItemMeta()
                ? event.getInventory().getItem(2).getItemMeta().getDisplayName()
                : null;

        if (inputText == null || inputText.isEmpty()) {
            player.sendMessage("§cPlease enter a valid number.");
            return;
        }

        try {
            if ("amount".equals(inputType)) {
                pending.amount = Integer.parseInt(inputText.replaceAll("[^0-9]", ""));
                player.closeInventory();
                // Now ask for price
                openAnvilInput(player, material, "price", pending.type);
                player.sendMessage("§eNow enter the §6price per unit§e in the Anvil.");
            } else if ("price".equals(inputType)) {
                pending.pricePerUnit = Double.parseDouble(inputText.replaceAll("[^0-9.]", ""));
                player.closeInventory();

                // Final confirmation + create order
                createOrderWithConfirmation(player, pending);
                pendingOrders.remove(player.getUniqueId());
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid number. Please try again.");
            player.closeInventory();
            pendingOrders.remove(player.getUniqueId());
        }
    }

    private void createOrderWithConfirmation(Player player, PendingOrder pending) {
        if (pending.amount == null || pending.pricePerUnit == null) {
            player.sendMessage("§cOrder creation failed. Missing amount or price.");
            return;
        }

        String actionName = pending.type.equals("buy_order") ? "Buy Order" : "Sell Order";
        double total = pending.pricePerUnit * pending.amount;

        Inventory confirm = Bukkit.createInventory(null, 27, "§6§lConfirm " + actionName);

        ItemStack info = new ItemStack(Material.valueOf(pending.material));
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§e" + pending.amount + "x " + pending.material);
        List<String> lore = new ArrayList<>();
        lore.add("§7Price per unit: §a$" + String.format("%.2f", pending.pricePerUnit));
        lore.add("§7Total Value: §6$" + String.format("%,.0f", total));
        meta.setLore(lore);
        info.setItemMeta(meta);
        confirm.setItem(13, info);

        ItemStack confirmBtn = new ItemStack(Material.GREEN_WOOL);
        ItemMeta cMeta = confirmBtn.getItemMeta();
        cMeta.setDisplayName("§a§lCONFIRM CREATE " + actionName.toUpperCase());
        PersistentDataContainer cPdc = cMeta.getPersistentDataContainer();
        cPdc.set(ACTION_KEY, PersistentDataType.STRING, "confirm_create_order");
        cPdc.set(MATERIAL_KEY, PersistentDataType.STRING, pending.material);
        // We use pendingOrders map instead of storing everything in PDC for simplicity
        confirmBtn.setItemMeta(cMeta);
        confirm.setItem(11, confirmBtn);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta caMeta = cancel.getItemMeta();
        caMeta.setDisplayName("§c§lCANCEL");
        PersistentDataContainer caPdc = caMeta.getPersistentDataContainer();
        caPdc.set(ACTION_KEY, PersistentDataType.STRING, "close");
        cancel.setItemMeta(caMeta);
        confirm.setItem(15, cancel);

        player.openInventory(confirm);
    }

    @EventHandler
    public void onConfirmCreateOrder(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        // We can also check title or use a specific holder if needed
        if (event.getView().getTitle().contains("Confirm")) {
            // This is a bit loose — in production use a proper holder or map check
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (!"confirm_create_order".equals(action)) return;

        event.setCancelled(true);
        String material = pdc.get(MATERIAL_KEY, PersistentDataType.STRING);
        PendingOrder pending = pendingOrders.remove(player.getUniqueId());

        if (pending == null || pending.amount == null || pending.pricePerUnit == null) {
            player.sendMessage("§cOrder data lost. Please try again.");
            player.closeInventory();
            return;
        }

        player.closeInventory();

        if ("buy_order".equals(pending.type)) {
            bazaarManager.createBuyOrder(player, material, pending.amount, pending.pricePerUnit);
        } else {
            bazaarManager.createSellOrder(player, material, pending.amount, pending.pricePerUnit);
        }
    }

    // ==================== UTILITY ====================
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

    public void open(Player player) {
        openMainBazaar(player);
    }
}
