package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Island Shop catalog and purchase logic.
 * Deepened version supporting rich config-driven items, one-time purchases,
 * multiple reward types, and redeemable tokens.
 */
public class IslandShopManager {

    private final FoliaSkyblock plugin;

    // id -> ShopItem
    private final Map<String, ShopItem> catalog = new LinkedHashMap<>();

    // islandKey -> set of purchased one-time item IDs (in-memory cache)
    private final Map<String, Set<String>> oneTimePurchases = new ConcurrentHashMap<>();

    public IslandShopManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadCatalog();
    }

    public void reload() {
        catalog.clear();
        loadCatalog();
    }

    private void loadCatalog() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("island.shop.items");
        if (root == null) {
            plugin.getLogger().warning("[IslandShop] No island.shop.items section in config — using empty catalog.");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection itemSec = root.getConfigurationSection(id);
            if (itemSec == null) continue;

            try {
                String name = itemSec.getString("name", id);
                Material material = Material.matchMaterial(itemSec.getString("material", "STONE"));
                if (material == null) material = Material.STONE;
                int price = itemSec.getInt("price", 1000);
                String category = itemSec.getString("category", "GENERAL").toUpperCase();
                String description = itemSec.getString("description", "§7A shop item");
                String rewardType = itemSec.getString("reward_type", "ITEM").toUpperCase();
                boolean oneTime = itemSec.getBoolean("one_time", false);
                int stock = itemSec.getInt("stock", -1);

                Map<String, Object> rewardData = new HashMap<>();
                ConfigurationSection dataSec = itemSec.getConfigurationSection("reward_data");
                if (dataSec != null) {
                    for (String key : dataSec.getKeys(false)) {
                        rewardData.put(key, dataSec.get(key));
                    }
                }

                ShopItem item = new ShopItem(id, name, material, price, category, description,
                        rewardType, rewardData, oneTime, stock);
                catalog.put(id, item);
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandShop] Failed to load shop item " + id + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("[IslandShop] Loaded " + catalog.size() + " shop items from config.");
    }

    public Collection<ShopItem> getAllItems() {
        return catalog.values();
    }

    public Collection<ShopItem> getItemsByCategory(String category) {
        if (category == null || category.equalsIgnoreCase("ALL")) {
            return getAllItems();
        }
        List<ShopItem> filtered = new ArrayList<>();
        String catUpper = category.toUpperCase();
        for (ShopItem item : catalog.values()) {
            if (item.category.equals(catUpper)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public ShopItem getItem(String id) {
        return catalog.get(id);
    }

    public boolean hasPurchasedOneTime(String islandKey, String itemId) {
        Set<String> purchased = oneTimePurchases.get(islandKey);
        return purchased != null && purchased.contains(itemId);
    }

    public void markOneTimePurchased(String islandKey, String itemId) {
        oneTimePurchases.computeIfAbsent(islandKey, k -> ConcurrentHashMap.newKeySet()).add(itemId);
        // Persist asynchronously
        plugin.getDatabaseManager().saveShopPurchase(islandKey, itemId);
    }

    public void loadOneTimePurchasesForIsland(String islandKey, Set<String> purchased) {
        oneTimePurchases.put(islandKey, new HashSet<>(purchased));
    }

    /**
     * Creates a redeemable physical token ItemStack for items that should be given as tokens
     * (mainly BOOSTER and some CUSTOM reward types).
     */
    public ItemStack createRedeemableToken(ShopItem item) {
        ItemStack token = new ItemStack(item.material);
        ItemMeta meta = token.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(item.name + " §7(Token)");
            List<String> lore = new ArrayList<>();
            lore.add(item.description);
            lore.add("");
            lore.add("§aRight-click to redeem");
            lore.add("§7Purchased from Island Shop");
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new org.bukkit.NamespacedKey(plugin, "shop_token"), PersistentDataType.STRING, item.id);
            pdc.set(new org.bukkit.NamespacedKey(plugin, "shop_token_type"), PersistentDataType.STRING, "REDEEM");
            token.setItemMeta(meta);
        }
        return token;
    }

    public record ShopItem(String id, String name, Material material, int price, String category,
                           String description, String rewardType, Map<String, Object> rewardData,
                           boolean oneTime, int stock) {}
}