package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.IslandWorthManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

/**
 * Paginated Island Top / Leaderboard GUI
 * Categories: Worth, Worth Level, Members
 */
public class IslandTopGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final int ITEMS_PER_PAGE = 45;

    public IslandTopGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandTopGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, Category category, int page) {
        final int finalPage = Math.max(0, page);

        // Fetch real leaderboard data asynchronously
        plugin.getIslandWorthManager().getTopIslandsByWorth(45).thenAccept(topList -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                Inventory gui = Bukkit.createInventory(null, 54, "§6§lIsland Top - " + category.display + " §7(Page " + (finalPage + 1) + ")");

                int start = finalPage * ITEMS_PER_PAGE;
                int end = Math.min(start + ITEMS_PER_PAGE, topList.size());

                int slot = 10;
                for (int i = start; i < end; i++) {
                    IslandWorthManager.IslandTopEntry entry = topList.get(i);

                    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                    ItemMeta meta = item.getItemMeta();

                    if (meta instanceof SkullMeta skullMeta) {
                        // Try to set real player head using NameCache name
                        try {
                            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.owner);
                            skullMeta.setOwningPlayer(offline);
                        } catch (Exception ignored) {}
                    }

                    if (meta != null) {
                        meta.setDisplayName("§e#" + (i + 1) + " §f" + entry.displayName);
                        meta.setLore(Arrays.asList(
                            "§7Worth: §6" + String.format("%,.0f", entry.worth),
                            "§7Worth Level: §b" + entry.level,
                            "§7Members: §a" + entry.memberCount,
                            "",
                            "§8Click for more info (future)"
                        ));
                        item.setItemMeta(meta);
                    }
                    gui.setItem(slot, item);
                    slot++;
                    if ((slot - 9) % 9 == 0) slot += 2;
                }

                // Navigation
                gui.setItem(45, createNavItem("§aPrevious", Material.ARROW));
                gui.setItem(49, createNavItem("§eWorth", Material.GOLD_INGOT));
                gui.setItem(50, createNavItem("§eLevel", Material.EXPERIENCE_BOTTLE));
                gui.setItem(51, createNavItem("§eMembers", Material.PLAYER_HEAD));
                gui.setItem(53, createNavItem("§aNext", Material.ARROW));

                player.openInventory(gui);
            });
        });
    }

    private ItemStack createNavItem(String name, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§6§lIsland Top")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        int slot = event.getRawSlot();

        // Extract current page and category from title (simple parsing)
        int currentPage = 0;
        Category currentCat = Category.WORTH;
        try {
            if (title.contains("Worth")) currentCat = Category.WORTH;
            else if (title.contains("Level")) currentCat = Category.LEVEL;
            else if (title.contains("Members")) currentCat = Category.MEMBERS;

            String pagePart = title.substring(title.lastIndexOf("Page ") + 5);
            currentPage = Integer.parseInt(pagePart.replace(")", "").trim()) - 1;
        } catch (Exception ignored) {}

        if (slot == 45) {
            open(player, currentCat, Math.max(0, currentPage - 1));
        } else if (slot == 53) {
            open(player, currentCat, currentPage + 1);
        } else if (slot == 49) {
            open(player, Category.WORTH, 0);
        } else if (slot == 50) {
            open(player, Category.LEVEL, 0);
        } else if (slot == 51) {
            open(player, Category.MEMBERS, 0);
        }
    }

    public enum Category {
        WORTH("Worth"),
        LEVEL("Level"),
        MEMBERS("Members");

        public final String display;

        Category(String display) {
            this.display = display;
        }
    }
}