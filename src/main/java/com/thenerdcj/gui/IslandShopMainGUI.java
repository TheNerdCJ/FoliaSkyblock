package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.IslandShopManager;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island shop (BaseGUI + PDC). Opened via {@link IslandShopGUI#open(Player, Island)}.
 */
public class IslandShopMainGUI extends BaseGUI {

    private static final int ITEMS_PER_PAGE = 36;

    private final NamespacedKey ITEM_ID_KEY;
    private final Map<UUID, Island> sessionIslands = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerCategory = new ConcurrentHashMap<>();

    public IslandShopMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
        this.ITEM_ID_KEY = new NamespacedKey(plugin, "shop_item_id");
    }

    public IslandShopMainGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
        this.ITEM_ID_KEY = new NamespacedKey(plugin, "shop_item_id");
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lIsland Shop";
    }

    @Override
    protected String getActionKeyName() {
        return "shop_action";
    }

    @Override
    protected int getItemsPerPage() {
        return ITEMS_PER_PAGE;
    }

    public void open(Player player, Island island) {
        open(player, island, 0, "ALL");
    }

    public void open(Player player, Island island, int page, String category) {
        if (island == null) {
            player.sendMessage("§cYou must be on an island to use the shop.");
            return;
        }
        sessionIslands.put(player.getUniqueId(), island);
        playerCategory.put(player.getUniqueId(), category);
        playerPages.put(player.getUniqueId(), page);

        GridPosition pos = island.getGridPosition();
        plugin.getIslandBankManager().getBank(pos).thenAccept(bank ->
                plugin.getThreadSafety().runOnMainThread(() -> buildShop(player, island, page, category, bank.getBalance())));
    }

    private void buildShop(Player player, Island island, int page, String category, double balance) {
        IslandShopManager manager = plugin.getIslandShopManager();
        Collection<IslandShopManager.ShopItem> items = manager.getItemsByCategory(category);
        List<IslandShopManager.ShopItem> itemList = new ArrayList<>(items);

        int totalPages = Math.max(1, (int) Math.ceil(itemList.size() / (double) ITEMS_PER_PAGE));
        int validPage = Math.max(0, Math.min(page, totalPages - 1));
        playerPages.put(player.getUniqueId(), validPage);

        String title = getTitlePrefix() + " §7(Page " + (validPage + 1) + " | " + category + ")";
        Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(title));

        gui.setItem(4, GUIUtils.createItem(Material.EMERALD, "§a§lIsland Shop",
                "§7Earn balance from Missions, Boosters & Worth",
                "§6Balance: §e$" + String.format("%,.0f", balance),
                "§7Right-click tokens in inventory to redeem"));

        int[] catSlots = {9, 10, 11, 12, 13, 14, 15};
        String[] cats = {"ALL", "BOOSTERS", "MINIONS", "PROGRESSION", "SPECIAL", "GENERAL"};
        Material[] catMats = {Material.CHEST, Material.EXPERIENCE_BOTTLE, Material.IRON_PICKAXE,
                Material.NETHER_STAR, Material.NETHERITE_INGOT, Material.BOOK};
        for (int i = 0; i < catSlots.length && i < cats.length; i++) {
            gui.setItem(catSlots[i], createCategoryButton(cats[i], catMats[i], cats[i].equals(category)));
        }

        int start = validPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, itemList.size());
        int slot = 18;
        for (int i = start; i < end; i++) {
            gui.setItem(slot++, createShopItemDisplay(manager, itemList.get(i), island, balance));
        }

        if (validPage > 0) {
            gui.setItem(PREV_SLOT, GUIUtils.createNavButton(Material.ARROW, "§a§lPrevious Page", ACTION_KEY, "PREV"));
        }
        gui.setItem(CLOSE_SLOT, GUIUtils.createNavButton(Material.BARRIER, "§c§lClose", ACTION_KEY, "CLOSE"));
        if (validPage < totalPages - 1) {
            gui.setItem(NEXT_SLOT, GUIUtils.createNavButton(Material.ARROW, "§a§lNext Page", ACTION_KEY, "NEXT"));
        }

        gui.setItem(0, GUIUtils.createItem(Material.BOOK, "§eShop Info",
                "§7One-time items are hidden after purchase",
                "§7Tokens can be redeemed later by right-click",
                "§7Stock & pricing are island-wide"));

        player.openInventory(gui);
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
    }

    @Override
    protected int getTotalPages(Player player) {
        return 1;
    }

    @Override
    public void open(Player player, int page) {
        Island island = sessionIslands.get(player.getUniqueId());
        if (island == null) {
            island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        }
        String cat = playerCategory.getOrDefault(player.getUniqueId(), "ALL");
        open(player, island, page, cat);
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        Island island = sessionIslands.get(player.getUniqueId());
        if (island == null) {
            return;
        }
        String cat = playerCategory.getOrDefault(player.getUniqueId(), "ALL");
        IslandShopManager manager = plugin.getIslandShopManager();

        switch (action) {
            case "PREV" -> open(player, island, Math.max(0, currentPage - 1), cat);
            case "NEXT" -> open(player, island, currentPage + 1, cat);
            case "CATEGORY" -> {
                String newCat = pdc.get(ITEM_ID_KEY, PersistentDataType.STRING);
                if (newCat != null) {
                    open(player, island, 0, newCat);
                }
            }
            case "BUY" -> {
                String itemId = pdc.get(ITEM_ID_KEY, PersistentDataType.STRING);
                if (itemId == null) {
                    return;
                }
                IslandShopManager.ShopItem item = manager.getItem(itemId);
                if (item == null) {
                    return;
                }
                if (item.oneTime() && manager.hasPurchasedOneTime(island.getId(), item.id())) {
                    player.sendMessage("§cYour island already owns this item.");
                    return;
                }
                GridPosition pos = island.getGridPosition();
                plugin.getIslandBankManager().getBank(pos).thenAccept(bank -> {
                    if (bank.getBalance() < item.price()) {
                        player.sendMessage("§cInsufficient island balance.");
                        SoundUtil.purchaseFail(player);
                        return;
                    }
                    plugin.getIslandBankManager().withdraw(pos, item.price()).thenAccept(success -> {
                        if (success) {
                            plugin.getThreadSafety().runOnMainThread(() ->
                                    handlePurchase(player, island, item, manager));
                        } else {
                            player.sendMessage("§cPurchase failed.");
                        }
                    });
                });
            }
            default -> {
            }
        }
    }

    @EventHandler
    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().getTitle().startsWith(getTitlePrefix())) {
            return;
        }
        super.onInventoryClick(event);
    }

    private ItemStack createCategoryButton(String label, Material mat, boolean selected) {
        ItemStack item = GUIUtils.createItem(mat, (selected ? "§a§l" : "§e") + label);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GUIUtils.setPDCString(meta, ACTION_KEY, "CATEGORY");
            GUIUtils.setPDCString(meta, ITEM_ID_KEY, label);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShopItemDisplay(IslandShopManager manager, IslandShopManager.ShopItem item,
                                            Island island, double balance) {
        String islandKey = island.getId();
        boolean alreadyOwned = item.oneTime() && manager.hasPurchasedOneTime(islandKey, item.id());
        boolean canAfford = balance >= item.price();

        Material displayMat = alreadyOwned ? Material.GRAY_STAINED_GLASS_PANE : item.material();
        String displayName = alreadyOwned ? "§8§m" + item.name() : "§e§l" + item.name();

        ItemStack display = GUIUtils.createItem(displayMat, displayName);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(item.description());
            lore.add("");
            lore.add("§6Price: §e$" + String.format("%,d", item.price()));
            lore.add("§7Category: §b" + item.category());
            if (item.oneTime()) {
                lore.add("§d§lONE-TIME PURCHASE");
            }
            if (item.stock() > 0) {
                lore.add("§7Stock: §e" + item.stock());
            }
            lore.add("");
            if (alreadyOwned) {
                lore.add("§8You already own this item.");
            } else if (canAfford) {
                lore.add("§aClick to purchase");
            } else {
                lore.add("§cInsufficient island balance");
            }
            meta.setLore(lore);
            GUIUtils.setPDCString(meta, ACTION_KEY, alreadyOwned ? "OWNED" : "BUY");
            GUIUtils.setPDCString(meta, ITEM_ID_KEY, item.id());
            display.setItemMeta(meta);
        }
        return display;
    }

    private void handlePurchase(Player player, Island island, IslandShopManager.ShopItem item, IslandShopManager manager) {
        if (item.oneTime()) {
            manager.markOneTimePurchased(island.getId(), item.id());
        }

        String rewardType = item.rewardType();
        boolean giveAsToken = "BOOSTER".equals(rewardType) || "CUSTOM".equals(rewardType);

        if (giveAsToken) {
            player.getInventory().addItem(manager.createRedeemableToken(item));
            player.sendMessage("§aPurchased §e" + item.name() + " §7(token). Right-click to redeem!");
        } else {
            grantImmediateReward(player, island, item);
            player.sendMessage("§aPurchased §e" + item.name() + "§a!");
        }

        int page = playerPages.getOrDefault(player.getUniqueId(), 0);
        String cat = playerCategory.getOrDefault(player.getUniqueId(), "ALL");
        open(player, island, page, cat);
    }

    private void grantImmediateReward(Player player, Island island, IslandShopManager.ShopItem item) {
        String rewardType = item.rewardType();
        Map<String, Object> data = item.rewardData();

        switch (rewardType) {
            case "XP" -> {
                int amt = ((Number) data.getOrDefault("amount", 100)).intValue();
                plugin.getIslandManager().addIslandXp(player, amt);
                player.sendMessage("§a+" + amt + " Island XP granted!");
            }
            case "WORTH" -> {
                plugin.getIslandWorthManager().invalidateCache(island);
                plugin.getIslandWorthManager().recalculateAndUpdate(island);
                player.sendMessage("§aIsland worth recalculated!");
            }
            case "MINION_FUEL" -> {
                int amt = ((Number) data.getOrDefault("amount", 500)).intValue();
                player.sendMessage("§a+" + amt + " fuel added to minions (placeholder integration).");
            }
            case "ITEM" -> {
                String displayName = (String) item.rewardData().getOrDefault("custom_name", item.name());
                List<String> lore = new ArrayList<>();
                Object loreObj = item.rewardData().get("lore");
                if (loreObj instanceof List<?> loreList) {
                    for (Object o : loreList) {
                        lore.add(String.valueOf(o));
                    }
                }
                player.getInventory().addItem(GUIUtils.createItem(item.material(), displayName, lore.toArray(new String[0])));
            }
            default -> player.sendMessage("§aItem effect applied.");
        }
    }
}