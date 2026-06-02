package com.thenerdcj.bazaar;

import com.thenerdcj.FoliaSkyblock;
import net.kyori.adventure.text.Component;
import com.thenerdcj.gui.GUIUtils;
import com.thenerdcj.util.MessageUtil;
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
import java.util.concurrent.CompletableFuture;
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
        this(plugin, bazaarManager, true);
    }

    public BazaarGUI(FoliaSkyblock plugin, BazaarManager bazaarManager, boolean autoRegister) {
        this.plugin = plugin;
        this.bazaarManager = bazaarManager;
        this.ACTION_KEY = new NamespacedKey(plugin, "bazaar_action");
        this.MATERIAL_KEY = new NamespacedKey(plugin, "bazaar_material");
        this.PAGE_KEY = new NamespacedKey(plugin, "bazaar_page");
        this.ORDER_ID_KEY = new NamespacedKey(plugin, "bazaar_order_id");
        this.INPUT_TYPE_KEY = new NamespacedKey(plugin, "bazaar_input_type");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
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
                54, MessageUtil.legacy("§6§lBazaar"));

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, itemList.size());

        for (int i = start; i < end; i++) {
            addBazaarItemToGUI(gui, i - start, itemList.get(i));
        }

        for (int i = end - start; i < 45; i++) {
            gui.setItem(i, createGlassPane());
        }

        if (page > 0) gui.setItem(45, createBazaarNavItem(Material.ARROW, "§a§l« Previous", "prev", page));
        if (page < totalPages - 1) gui.setItem(53, createBazaarNavItem(Material.ARROW, "§a§lNext »", "next", page));
        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), (BazaarGUIHolder) gui.getHolder());
    }

    private void addBazaarItemToGUI(Inventory gui, int slot, BazaarItem item) {
        ItemStack stack = GUIUtils.createItem(Material.valueOf(item.getMaterial()), "§e" + item.getDisplayName());

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtil.legacy("§7Instant Buy: §a$" + String.format("%.2f", item.getBuyPrice()) + " §7/ea"));
            lore.add(MessageUtil.legacy("§7Instant Sell: §c$" + String.format("%.2f", item.getSellPrice()) + " §7/ea"));
            lore.add(MessageUtil.legacy(""));
            lore.add(MessageUtil.legacy("§aClick to manage this item"));
            meta.lore(lore);

            GUIUtils.setPDCString(meta, ACTION_KEY, "open_item");
            GUIUtils.setPDCString(meta, MATERIAL_KEY, item.getMaterial());

            stack.setItemMeta(meta);
        }
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
                MessageUtil.legacy("§6§l" + item.getDisplayName() + " §7Bazaar")
        );

        // Info item - modernized
        ItemStack info = GUIUtils.createItem(Material.valueOf(material), "§e" + item.getDisplayName());
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            List<String> infoLore = new ArrayList<>();
            infoLore.add("§7Instant Buy Price: §a$" + String.format("%.2f", item.getBuyPrice()));
            infoLore.add("§7Instant Sell Price: §c$" + String.format("%.2f", item.getSellPrice()));
            infoLore.add("§7Stock: §e" + item.getStock());
            infoMeta.setLore(infoLore);
            info.setItemMeta(infoMeta);
        }
        gui.setItem(4, info);

        // Action buttons - using modern helper
        gui.setItem(20, createBazaarActionItem(Material.GREEN_WOOL, "§a§lInstant Buy", "instant_buy", material));
        gui.setItem(22, createBazaarActionItem(Material.RED_WOOL, "§c§lInstant Sell", "instant_sell", material));
        gui.setItem(24, createBazaarActionItem(Material.BLUE_WOOL, "§b§lCreate Buy Order", "create_buy_order", material));
        gui.setItem(29, createBazaarActionItem(Material.ORANGE_WOOL, "§6§lCreate Sell Order", "create_sell_order", material));
        gui.setItem(31, createBazaarActionItem(Material.PURPLE_WOOL, "§d§lView Buy Orders", "view_buy_orders", material));
        gui.setItem(33, createBazaarActionItem(Material.PURPLE_WOOL, "§d§lView Sell Orders", "view_sell_orders", material));

        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), (BazaarGUIHolder) gui.getHolder());
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

        String titleBase = isBuyOrders ? "Buy Orders" : "Sell Orders";
        String color = isBuyOrders ? "§b" : "§6";
        Inventory gui = Bukkit.createInventory(
                new BazaarGUIHolder("orders", material, page, null, isBuyOrders ? "buy" : "sell"),
                54,
                MessageUtil.legacy("§6§l" + color + titleBase + " §7(Page " + (page + 1) + "/" + Math.max(1, totalPages) + ")")
        );

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, orders.size());

        for (int i = start; i < end; i++) {
            addOrderItem(gui, i - start, orders.get(i), isBuyOrders);
        }

        for (int i = end - start; i < 45; i++) gui.setItem(i, createGlassPane());

        if (page > 0) gui.setItem(45, createBazaarNavItem(Material.ARROW, "§a§l« Prev", "prev", page));
        if (page < totalPages - 1) gui.setItem(53, createBazaarNavItem(Material.ARROW, "§a§lNext »", "next", page));
        gui.setItem(49, createBackButton(material));

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), (BazaarGUIHolder) gui.getHolder());
    }

    private void addOrderItem(Inventory gui, int slot, BazaarOrder order, boolean isBuy) {
        // Use GUIUtils for base item creation + manual meta only for complex lore/PDC
        ItemStack item = GUIUtils.createItem(Material.valueOf(order.getMaterial()), (isBuy ? "§bBuy" : "§6Sell") + " Order");

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
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
        }
        gui.setItem(slot, item);
    }

    // ==================== ANVIL INPUT FOR AMOUNT & PRICE ====================
    private void openAnvilInput(Player player, String material, String inputType, String orderType) {
        // inputType: "amount" or "price"
        // orderType: "buy_order" or "sell_order"

        AnvilInventory anvil = (AnvilInventory) Bukkit.createInventory(null, InventoryType.ANVIL, 
            MessageUtil.legacy("§6Enter " + inputType));

        ItemStack paper = GUIUtils.createItem(Material.PAPER, "§eEnter " + (inputType.equals("amount") ? "amount" : "price per unit"));
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "anvil_input");
            pdc.set(MATERIAL_KEY, PersistentDataType.STRING, material);
            pdc.set(INPUT_TYPE_KEY, PersistentDataType.STRING, inputType);
            paper.setItemMeta(meta);
        }
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // Clean up pending state if they close any of our GUIs mid-flow
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BazaarGUIHolder) {
            // Only remove pending if they are not in the middle of anvil input for our keys
            // (anvil close is handled in the click path or on error)
            // For safety we leave pending until explicit consumption or explicit close action
        }

        // If they close the anvil while we were waiting for input, clean pending for hygiene
        if (event.getInventory().getType() == InventoryType.ANVIL) {
            ItemStack first = event.getInventory().getItem(0);
            if (first != null && first.hasItemMeta()) {
                PersistentDataContainer pdc = first.getItemMeta().getPersistentDataContainer();
                if ("anvil_input".equals(pdc.get(ACTION_KEY, PersistentDataType.STRING))) {
                    pendingOrders.remove(player.getUniqueId());
                    openGUIs.remove(player.getUniqueId());
                }
            }
        }
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
                pendingOrders.remove(player.getUniqueId());
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
                    startInstantTrade(player, material, true);
                }
                break;

            case "instant_sell":
                if (material != null) {
                    player.closeInventory();
                    startInstantTrade(player, material, false);
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
                    // Fulfill the order - player takes the opposite side
                    final boolean buyOrders = "buy".equals(guiHolder.getPendingAction());
                    bazaarManager.fulfillOrder(orderId, player).thenAccept(success -> {
                        if (success) {
                            plugin.getThreadSafety().runOnMainThread(() -> {
                                openOrdersView(player, material, buyOrders, 0);
                            });
                        }
                    });
                    player.closeInventory();
                }
                break;

            case "back":
                if (material != null) {
                    player.closeInventory();
                    pendingOrders.remove(player.getUniqueId());
                    openItemDetail(player, material);
                }
                break;

            case "confirm_instant_buy":
            case "confirm_instant_sell":
                if (material != null) {
                    PendingOrder p = pendingOrders.remove(player.getUniqueId());
                    int qty = (p != null && p.amount != null && p.amount > 0) ? p.amount : 1;
                    boolean isBuy = "confirm_instant_buy".equals(action);

                    player.closeInventory();
                    CompletableFuture<Boolean> fut = isBuy
                            ? bazaarManager.instantBuy(player, material, qty)
                            : bazaarManager.instantSell(player, material, qty);

                    fut.thenAccept(success -> {
                        if (success) {
                            plugin.getThreadSafety().runOnMainThread(() -> openItemDetail(player, material));
                        }
                    });
                }
                break;

            case "confirm_create_order":
                if (material != null) {
                    PendingOrder pending = pendingOrders.remove(player.getUniqueId());
                    player.closeInventory();
                    openGUIs.remove(player.getUniqueId());

                    if (pending == null || pending.amount == null || pending.pricePerUnit == null) {
                        player.sendMessage("§cOrder data lost. Please try again.");
                        return;
                    }

                    if ("buy_order".equals(pending.type)) {
                        bazaarManager.createBuyOrder(player, material, pending.amount, pending.pricePerUnit)
                                .thenAccept(createdOrderId -> {
                                    if (createdOrderId != null) {
                                        plugin.getThreadSafety().runOnMainThread(() -> openItemDetail(player, material));
                                    }
                                });
                    } else {
                        bazaarManager.createSellOrder(player, material, pending.amount, pending.pricePerUnit)
                                .thenAccept(createdOrderId -> {
                                    if (createdOrderId != null) {
                                        plugin.getThreadSafety().runOnMainThread(() -> openItemDetail(player, material));
                                    }
                                });
                    }
                }
                break;
        }
    }

    private ItemStack createBackButton(String material) {
        ItemStack item = GUIUtils.createItem(Material.ARROW, "§7« Back to Item");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GUIUtils.setPDCString(meta, ACTION_KEY, "back");
            GUIUtils.setPDCString(meta, MATERIAL_KEY, material);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== CONFIRMATION DIALOGS ====================
    /**
     * Opens instant trade confirmation. Uses the amount from the PendingOrder map when available,
     * otherwise defaults to 1.
     */
    private void openInstantConfirm(Player player, String material, boolean isBuy, int amount) {
        BazaarItem item = bazaarManager.getBazaarItem(material);
        if (item == null) return;

        double unitPrice = isBuy ? item.getBuyPrice() : item.getSellPrice();
        double gross = unitPrice * amount;
        double tax = isBuy ? 0.0 : gross * 0.0125;
        double total = isBuy ? gross : gross - tax;

        String action = isBuy ? "confirm_instant_buy" : "confirm_instant_sell";
        String title = isBuy ? "§aConfirm Instant Buy" : "§cConfirm Instant Sell";
        String qtyLine = "§e" + amount + "x " + item.getDisplayName();

        Inventory confirm = Bukkit.createInventory(
                new BazaarGUIHolder("confirm_instant", material, 0, null, isBuy ? "buy" : "sell"),
                27,
                title
        );

        // Info item
        ItemStack info = GUIUtils.createItem(Material.valueOf(material), qtyLine);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Unit Price: §6$" + String.format("%.2f", unitPrice));
            if (!isBuy) {
                lore.add("§7Gross: §6$" + String.format("%,.0f", gross));
                lore.add("§7Tax (1.25%): §c-$" + String.format("%,.0f", tax));
            }
            lore.add("§7Total: §a$" + String.format("%,.0f", total));
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        confirm.setItem(13, info);

        // Confirm / Cancel using helpers
        String confirmName = (isBuy ? "§a§lCONFIRM BUY " : "§c§lCONFIRM SELL ") + amount + "x";
        confirm.setItem(11, createBazaarActionItem(isBuy ? Material.GREEN_WOOL : Material.RED_WOOL, confirmName, action, material));
        confirm.setItem(15, createBazaarActionItem(Material.RED_WOOL, "§c§lCANCEL", "close", null));

        player.openInventory(confirm);
        openGUIs.put(player.getUniqueId(), (BazaarGUIHolder) confirm.getHolder());
    }

    // Overload for legacy / simple calls (defaults to 1)
    private void openInstantConfirm(Player player, String material, boolean isBuy) {
        openInstantConfirm(player, material, isBuy, 1);
    }

    // ==================== ORDER CREATION WITH ANVIL INPUT ====================
    private void startOrderCreation(Player player, String material, String orderType) {
        PendingOrder pending = new PendingOrder(material, orderType);
        pendingOrders.put(player.getUniqueId(), pending);

        // First ask for amount
        openAnvilInput(player, material, "amount", orderType);
        player.sendMessage("§ePlease enter the §6amount §ein the Anvil and click the output slot.");
    }

    /**
     * Starts instant buy or sell flow by first prompting for quantity via Anvil.
     */
    private void startInstantTrade(Player player, String material, boolean isBuy) {
        String type = isBuy ? "instant_buy" : "instant_sell";
        PendingOrder pending = new PendingOrder(material, type);
        pendingOrders.put(player.getUniqueId(), pending);

        openAnvilInput(player, material, "amount", type);
        player.sendMessage("§eEnter the §6amount §eyou want to " + (isBuy ? "buy" : "sell") + " instantly.");
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

        event.setCancelled(true);

        String material = pdc.get(MATERIAL_KEY, PersistentDataType.STRING);
        String inputType = pdc.get(INPUT_TYPE_KEY, PersistentDataType.STRING);
        PendingOrder pending = pendingOrders.get(player.getUniqueId());

        if (pending == null || !pending.material.equals(material)) {
            player.closeInventory();
            pendingOrders.remove(player.getUniqueId());
            return;
        }

        // Get the text the player typed in the Anvil (result slot)
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

                if (pending.type.startsWith("instant_")) {
                    // Instant trade: show confirm with live pricing from BazaarItem
                    openInstantConfirm(player, pending.material, "instant_buy".equals(pending.type), pending.amount);
                    // pending stays in map so confirm handler can read the chosen amount
                } else {
                    // Limit order: next collect price per unit
                    openAnvilInput(player, material, "price", pending.type);
                    player.sendMessage("§eNow enter the §6price per unit§e in the Anvil.");
                }
            } else if ("price".equals(inputType)) {
                pending.pricePerUnit = Double.parseDouble(inputText.replaceAll("[^0-9.]", ""));
                player.closeInventory();

                // Final confirmation + create order
                createOrderWithConfirmation(player, pending);
                // pending stays until the confirm click consumes it
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
            pendingOrders.remove(player.getUniqueId());
            return;
        }

        String actionName = "buy_order".equals(pending.type) ? "Buy Order" : "Sell Order";
        double total = pending.pricePerUnit * pending.amount;

        Inventory confirm = Bukkit.createInventory(
                new BazaarGUIHolder("confirm_create", pending.material, 0, null, pending.type),
                27,
                MessageUtil.legacy("§6§lConfirm " + actionName)
        );

        // Info item
        String materialName = pending.material;
        ItemStack info = GUIUtils.createItem(Material.valueOf(materialName), "§e" + pending.amount + "x " + materialName);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Price per unit: §a$" + String.format("%.2f", pending.pricePerUnit));
            lore.add("§7Total Value: §6$" + String.format("%,.0f", total));
            if ("sell_order".equals(pending.type)) {
                double tax = total * 0.0125;
                lore.add("§7You will receive: §a$" + String.format("%,.0f", total - tax) + " §7(after tax)");
            }
            meta.setLore(lore);
            info.setItemMeta(meta);
        }
        confirm.setItem(13, info);

        // Confirm / Cancel buttons using helpers
        confirm.setItem(11, createBazaarActionItem(Material.GREEN_WOOL, "§a§lCONFIRM CREATE " + actionName.toUpperCase(), "confirm_create_order", pending.material));
        confirm.setItem(15, createBazaarActionItem(Material.RED_WOOL, "§c§lCANCEL", "close", null));

        player.openInventory(confirm);
        openGUIs.put(player.getUniqueId(), (BazaarGUIHolder) confirm.getHolder());
    }

    // NOTE: Order confirmation handling consolidated into onBazaarClick using proper BazaarGUIHolder + pendingOrders map.
    // The previous loose title-sniffing handler has been removed to prevent duplicate processing.

    // ==================== UTILITY (Modernized with GUIUtils) ====================
    private ItemStack createGlassPane() {
        return GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private ItemStack createCloseButton() {
        return createBazaarActionItem(Material.BARRIER, "§c§lClose", "close", null);
    }

    private boolean isBuyOrdersFromContext(BazaarGUIHolder holder) {
        return "buy".equals(holder != null ? holder.getPendingAction() : null);
    }

    // ==================== Bazaar-specific GUI helpers (to reduce boilerplate) ====================

    private ItemStack createBazaarActionItem(Material material, String name, String action, String materialValue) {
        ItemStack item = GUIUtils.createItem(material, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GUIUtils.setPDCString(meta, ACTION_KEY, action);
            if (materialValue != null) {
                GUIUtils.setPDCString(meta, MATERIAL_KEY, materialValue);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBazaarNavItem(Material material, String name, String action, int page) {
        ItemStack item = GUIUtils.createItem(material, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GUIUtils.setPDCString(meta, ACTION_KEY, action);
            meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, page);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        openMainBazaar(player);
    }
}
