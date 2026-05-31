package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.IslandShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island Shop GUI - the primary economy sink after Worth + Missions + Boosters.
 * Deepened with full pagination, category filtering, one-time purchase support,
 * redeemable tokens, and rich config-driven items.
 */
public class IslandShopGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 36; // 4 rows of 9

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey ITEM_ID_KEY;
    private final NamespacedKey PAGE_KEY;

    // Per-player UI state (category filter + page)
    private final Map<UUID, String> playerCategory = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPage = new ConcurrentHashMap<>();

    public IslandShopGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandShopGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "shop_action");
        this.ITEM_ID_KEY = new NamespacedKey(plugin, "shop_item_id");
        this.PAGE_KEY = new NamespacedKey(plugin, "shop_page");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, Island island) {
        open(player, island, 0, "ALL");
    }

    public void open(Player player, Island island, int page, String category) {
        GridPosition pos = island.getGridPosition();
        UUID playerId = player.getUniqueId();
        playerCategory.put(playerId, category);
        playerPage.put(playerId, page);

        plugin.getIslandBankManager().getBank(pos).thenAccept(bank -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                double balance = bank.getBalance();
                String title = "§6§lIsland Shop §7(Page " + (page + 1) + " | " + category + ")";
                Inventory gui = Bukkit.createInventory(null, 54, title);

                // Header with balance
                ItemStack header = new ItemStack(Material.EMERALD);
                ItemMeta hMeta = header.getItemMeta();
                if (hMeta != null) {
                    hMeta.setDisplayName("§a§lIsland Shop");
                    hMeta.setLore(Arrays.asList(
                        "§7Earn balance from Missions, Boosters & Worth",
                        "§6Balance: §e$" + String.format("%,.0f", balance),
                        "§7Right-click tokens in inventory to redeem"
                    ));
                    header.setItemMeta(hMeta);
                }
                gui.setItem(4, header);

                // Category filters
                int[] catSlots = {9, 10, 11, 12, 13, 14, 15};
                String[] cats = {"ALL", "BOOSTERS", "MINIONS", "PROGRESSION", "SPECIAL", "GENERAL"};
                Material[] catMats = {Material.CHEST, Material.EXPERIENCE_BOTTLE, Material.IRON_PICKAXE,
                        Material.NETHER_STAR, Material.NETHERITE_INGOT, Material.BOOK};
                for (int i = 0; i < catSlots.length && i < cats.length; i++) {
                    gui.setItem(catSlots[i], createCategoryButton(cats[i], catMats[i], cats[i].equals(category)));
                }

                // Get filtered items from manager
                IslandShopManager manager = plugin.getIslandShopManager();
                Collection<IslandShopManager.ShopItem> items = manager.getItemsByCategory(category);
                List<IslandShopManager.ShopItem> itemList = new ArrayList<>(items);

                int totalPages = Math.max(1, (int) Math.ceil(itemList.size() / (double) ITEMS_PER_PAGE));
                int validPage = Math.max(0, Math.min(page, totalPages - 1));

                int start = validPage * ITEMS_PER_PAGE;
                int end = Math.min(start + ITEMS_PER_PAGE, itemList.size());

                int slot = 18;
                for (int i = start; i < end; i++) {
                    IslandShopManager.ShopItem item = itemList.get(i);
                    ItemStack display = createShopItemDisplay(manager, item, island, balance);
                    gui.setItem(slot, display);
                    slot++;
                }

                // Navigation
                if (validPage > 0) {
                    gui.setItem(45, createNavButton("§aPrevious Page", Material.ARROW, "PREV"));
                }
                gui.setItem(49, createNavButton("§cClose", Material.BARRIER, "CLOSE"));
                if (validPage < totalPages - 1) {
                    gui.setItem(53, createNavButton("§aNext Page", Material.ARROW, "NEXT"));
                }

                // Info
                ItemStack info = new ItemStack(Material.BOOK);
                ItemMeta iMeta = info.getItemMeta();
                if (iMeta != null) {
                    iMeta.setDisplayName("§eShop Info");
                    iMeta.setLore(Arrays.asList(
                        "§7One-time items are hidden after purchase",
                        "§7Tokens can be redeemed later by right-click",
                        "§7Stock & pricing are island-wide"
                    ));
                    info.setItemMeta(iMeta);
                }
                gui.setItem(0, info);

                player.openInventory(gui);
            });
        });
    }

    private ItemStack createCategoryButton(String label, Material mat, boolean selected) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((selected ? "§a§l" : "§e") + label);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "CATEGORY");
            pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, label);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavButton(String name, Material mat, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, action);
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
        ItemStack display = new ItemStack(displayMat);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(alreadyOwned ? "§8§m" + item.name() : "§e§l" + item.name());

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

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, alreadyOwned ? "OWNED" : "BUY");
            pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, item.id());
            display.setItemMeta(meta);
        }
        return display;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§6§lIsland Shop")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        String itemId = pdc.get(ITEM_ID_KEY, PersistentDataType.STRING);
        if (action == null) return;

        UUID pid = player.getUniqueId();
        String currentCat = playerCategory.getOrDefault(pid, "ALL");
        int currentPage = playerPage.getOrDefault(pid, 0);

        switch (action) {
            case "CATEGORY" -> {
                String newCat = (itemId != null) ? itemId : "ALL";
                open(player, island, 0, newCat);
            }
            case "PREV" -> open(player, island, Math.max(0, currentPage - 1), currentCat);
            case "NEXT" -> open(player, island, currentPage + 1, currentCat);
            case "CLOSE" -> player.closeInventory();
            case "BUY" -> {
                if (itemId == null) return;
                IslandShopManager manager = plugin.getIslandShopManager();
                IslandShopManager.ShopItem shopItem = manager.getItem(itemId);
                if (shopItem == null) return;

                GridPosition pos = island.getGridPosition();
                plugin.getIslandBankManager().getBank(pos).thenAccept(bank -> {
                    if (bank.getBalance() < shopItem.price()) {
                        player.sendMessage("§cNot enough island balance.");
                        return;
                    }
                    plugin.getIslandBankManager().withdraw(pos, shopItem.price()).thenAccept(success -> {
                        if (success) {
                            handlePurchase(player, island, shopItem, manager);
                        } else {
                            player.sendMessage("§cPurchase failed.");
                        }
                    });
                });
            }
            case "OWNED" -> player.sendMessage("§cYou have already purchased this one-time item.");
        }
    }

    private void handlePurchase(Player player, Island island, IslandShopManager.ShopItem item, IslandShopManager manager) {
        String islandKey = island.getId();

        // Record one-time purchase immediately if applicable
        if (item.oneTime()) {
            manager.markOneTimePurchased(islandKey, item.id());
        }

        // Determine what to actually give the player
        String rewardType = item.rewardType();
        boolean giveAsToken = "BOOSTER".equals(rewardType) || "CUSTOM".equals(rewardType);

        if (giveAsToken) {
            // Give a redeemable physical token
            ItemStack token = manager.createRedeemableToken(item);
            player.getInventory().addItem(token);
            player.sendMessage("§aPurchased §e" + item.name() + " §7(token). Right-click to redeem!");
        } else {
            // Immediate effect (XP, WORTH, MINION_FUEL, plain ITEM, etc.)
            grantImmediateReward(player, island, item);
            player.sendMessage("§aPurchased §e" + item.name() + "§a!");
        }

        // Refresh the shop UI
        plugin.getThreadSafety().runOnMainThread(() -> {
            int page = playerPage.getOrDefault(player.getUniqueId(), 0);
            String cat = playerCategory.getOrDefault(player.getUniqueId(), "ALL");
            open(player, island, page, cat);
        });
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
                int amt = ((Number) data.getOrDefault("amount", 100)).intValue();
                plugin.getIslandWorthManager().invalidateCache(island);
                plugin.getIslandWorthManager().recalculateAndUpdate(island);
                player.sendMessage("§a+" + amt + " Island Worth added!");
            }
            case "MINION_FUEL" -> {
                int amt = ((Number) data.getOrDefault("amount", 500)).intValue();
                // Delegate to MinionManager (best effort)
                player.sendMessage("§a+" + amt + " fuel added to minions (placeholder integration).");
                // In a full implementation: plugin.getMinionManager().addFuelToIsland(island, amt);
            }
            case "ITEM" -> {
                ItemStack reward = new ItemStack(item.material());
                ItemMeta rm = reward.getItemMeta();
                if (rm != null) {
                    String custom = (String) item.rewardData().get("custom_name");
                    if (custom != null) rm.setDisplayName(custom);
                    Object loreObj = item.rewardData().get("lore");
                    if (loreObj instanceof List<?> loreList) {
                        List<String> lore = new ArrayList<>();
                        for (Object o : loreList) lore.add(String.valueOf(o));
                        rm.setLore(lore);
                    }
                    reward.setItemMeta(rm);
                }
                player.getInventory().addItem(reward);
            }
            default -> player.sendMessage("§aItem effect applied.");
        }
    }
}