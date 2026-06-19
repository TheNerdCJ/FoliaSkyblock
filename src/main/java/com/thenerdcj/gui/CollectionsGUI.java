package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.CollectionManager;
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

/**
 * CollectionsGUI - Core island item discovery / collection progress viewer.
 * Shows total unique, per-category progress, recent style list of discovered.
 * Rewards and milestones are announced in chat by the manager.
 * Purely informational + motivational for Play-to-Win grinding.
 */
public class CollectionsGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public CollectionsGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "collections_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        CollectionManager cm = plugin.getCollectionManager();
        if (cm == null) {
            player.sendMessage("§cCollections system not available.");
            return;
        }

        String islandKey = getPlayerIslandKey(player);
        int total = cm.getCollectionCount(islandKey);
        int known = cm.getAllKnownKeys().size();

        Inventory inv = Bukkit.createInventory(null, 54, "§6§lIsland Collections §8- " + (total + "/" + known) + " | Click to view category");

        // Fillers (GUIUtils for consistency)
        ItemStack filler = GUIUtils.createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Title
        ItemStack title = GUIUtils.createItem(Material.BOOK, "§6§lIsland Collections",
                "§7Unique items discovered on this island",
                "§7Progress: §a" + total + " / " + known,
                "",
                "§7Discover by gathering, mining, fighting, fishing on your island");
        inv.setItem(4, title);

        // Total progress item
        ItemStack progress = GUIUtils.createItem(Material.CHEST, "§e§lTotal Unique: §a" + total,
                "§7of " + known + " tracked collectibles",
                "§7Milestones at 5/10/25/50/100/200");
        inv.setItem(8, progress);

        // Category summary (row 1-2)
        Map<String, Integer> catCounts = cm.getCategoryCounts(islandKey);
        int slot = 9;
        for (String cat : Arrays.asList("MINING", "FARMING", "COMBAT", "FISHING", "FORAGING", "MISC")) {
            int count = catCounts.getOrDefault(cat, 0);
            int catTotal = countKnownInCategory(cat);
            Material mat = getCategoryMaterial(cat);
            ItemStack catItem = GUIUtils.createItem(mat,
                    cm.getCategoryDisplay(cat) + " §7" + count + "/" + catTotal,
                    "§7Discover items in this category on your island");
            PersistentDataContainer pdc = catItem.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "CAT_" + cat);
            catItem.setItemMeta(catItem.getItemMeta());
            inv.setItem(slot++, catItem);
        }

        // Sample discovered list (grid, pageless for MVP - show up to 21 recent-ish)
        Set<String> discovered = cm.getDiscoveredForIsland(islandKey);
        List<String> list = new ArrayList<>(discovered);
        Collections.sort(list);

        int[] grid = {18,19,20,21,22,23,24, 27,28,29,30,31,32,33, 36,37,38,39,40,41,42};
        int idx = 0;
        for (int i = 0; i < list.size() && idx < grid.length; i++, idx++) {
            String key = list.get(i);
            String name = cm.getDisplayName(key);
            String cat = key.contains(":") ? key.substring(0, key.indexOf(':')) : "MISC";
            ItemStack it = GUIUtils.createItem(getCategoryMaterial(cat), "§f" + name,
                    "§7Category: " + cm.getCategoryDisplay(cat),
                    "§aDiscovered!");
            inv.setItem(grid[idx], it);
        }

        // Info / help
        ItemStack help = GUIUtils.createItem(Material.PAPER, "§6§lHow Collections Work",
                "§7Break blocks, harvest crops, kill mobs, fish",
                "§7on your island to log unique discoveries.",
                "§7Milestones give Island XP + Slayer Tokens.",
                "§7High totals unlock special cosmetic rewards.",
                "§eEasy to browse — click categories. Collect more = stronger island!");
        inv.setItem(49, help);

        // Close
        ItemStack close = GUIUtils.createItem(Material.BARRIER, "§cClose");
        PersistentDataContainer cp = close.getItemMeta().getPersistentDataContainer();
        cp.set(ACTION_KEY, PersistentDataType.STRING, "CLOSE");
        close.setItemMeta(close.getItemMeta());
        inv.setItem(53, close);

        player.openInventory(inv);
    }

    private String getPlayerIslandKey(Player player) {
        if (plugin.getIslandManager() == null) return player.getUniqueId().toString() + "_normal";
        var island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        return island != null ? island.getId() : (player.getUniqueId().toString() + "_" + player.getWorld().getEnvironment().name().toLowerCase());
    }

    private int countKnownInCategory(String cat) {
        int c = 0;
        java.util.Set<String> keys = plugin.getCollectionManager() != null ? plugin.getCollectionManager().getAllKnownKeys() : java.util.Collections.<String>emptySet();
        for (String k : keys) {
            if (k.startsWith(cat + ":")) c++;
        }
        return c;
    }

    private Material getCategoryMaterial(String cat) {
        return switch (cat) {
            case "MINING" -> Material.DIAMOND_PICKAXE;
            case "FARMING" -> Material.WHEAT;
            case "COMBAT" -> Material.DIAMOND_SWORD;
            case "FISHING" -> Material.FISHING_ROD;
            case "FORAGING" -> Material.OAK_LOG;
            default -> Material.CHEST;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("§6§lIsland Collections")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        if (action.equals("CLOSE")) {
            player.closeInventory();
            return;
        }

        if (action.startsWith("CAT_")) {
            // Could expand to filtered view in future; for now just refresh or message
            player.sendMessage("§7Category details shown in the grid above. Keep discovering!");
            // Re-open to refresh if wanted
            player.closeInventory();
            open(player);
        }
    }
}