package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandWarp;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Island Browse GUI - Scrollable island discovery
 * Shows top-rated islands first, then all public islands
 */
public class IslandBrowseGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final int ITEMS_PER_PAGE = 45; // 5 rows of 9

    public IslandBrowseGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int requestedPage) {
        // Check permission
        if (!player.hasPermission("foliasb.browse") && !player.hasPermission("foliasb.browse.islands")) {
            player.sendMessage("§cYou don't have permission to browse islands!");
            return;
        }

        // Use final variable for lambda
        final int page = requestedPage;

        // Get top rated islands first, then all public islands
        plugin.getIslandRatingManager().getTopRatedIslands(100).thenAccept(topRated -> {
            // Get all islands with warps
            plugin.getIslandWarpManager().getAllPublicWarps().thenAccept(allWarps -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Combine and sort: top rated first, then others
                    List<GridPosition> sortedIslands = new ArrayList<>();

                    // Add top rated islands first
                    for (GridPosition pos : topRated.keySet()) {
                        if (allWarps.containsKey(pos)) {
                            sortedIslands.add(pos);
                        }
                    }

                    // Add remaining islands with warps
                    for (GridPosition pos : allWarps.keySet()) {
                        if (!sortedIslands.contains(pos)) {
                            sortedIslands.add(pos);
                        }
                    }

                    // Calculate total pages (use final variables)
                    final int totalIslands = sortedIslands.size();
                    final int totalPages = totalIslands > 0 ? (int) Math.ceil(totalIslands / (double) ITEMS_PER_PAGE) : 1;

                    // Clamp page to valid range (use final variable)
                    final int validPage = Math.max(0, Math.min(page, totalPages - 1));

                    Inventory gui = Bukkit.createInventory(null, 54, "§6§lIsland Browse §7(Page " + (validPage + 1) + "/" + totalPages + ")");

                    // Header
                    gui.setItem(4, createItem(Material.COMPASS, "§6§lIsland Discovery",
                            "§7Browse public islands",
                            "§7Sorted by popularity",
                            "§7Total Islands: §e" + totalIslands));

                    // Calculate page range (use final variables)
                    final int startIndex = validPage * ITEMS_PER_PAGE;
                    final int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalIslands);

                    // Add island items
                    int slot = 9; // Start from second row
                    for (int i = startIndex; i < endIndex; i++) {
                        GridPosition pos = sortedIslands.get(i);
                        IslandWarp warp = allWarps.get(pos);

                        if (warp != null && warp.isEnabled()) {
                            double rating = topRated.getOrDefault(pos, 0.0);
                            ItemStack item = createIslandItem(pos, warp, rating);
                            gui.setItem(slot++, item);
                        }
                    }

                    // Navigation buttons (use final validPage)
                    if (validPage > 0) {
                        gui.setItem(45, createItem(Material.ARROW, "§a§lPrevious Page", "§7Click to go back"));
                    }
                    if (validPage < totalPages - 1) {
                        gui.setItem(53, createItem(Material.ARROW, "§a§lNext Page", "§7Click to go forward"));
                    }

                    // Close button
                    gui.setItem(49, createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));

                    player.openInventory(gui);
                    player.setMetadata("browse_page", new org.bukkit.metadata.FixedMetadataValue(plugin, validPage));
                    player.setMetadata("browse_islands", new org.bukkit.metadata.FixedMetadataValue(plugin, sortedIslands));
                });
            });
        });
    }

    private ItemStack createIslandItem(GridPosition pos, IslandWarp warp, double rating) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        // Get owner name
        String ownerName = "Unknown";
        try {
            Island island = plugin.getIslandManager().getIslandByPosition(pos);
            if (island != null) {
                ownerName = Bukkit.getOfflinePlayer(island.getOwnerUuid()).getName();
            }
        } catch (Exception e) {
            // Owner name remains "Unknown"
        }

        meta.setDisplayName("§e§l" + ownerName + "'s Island");

        List<String> lore = new ArrayList<>();
        lore.add("§7Rating: §e" + String.format("%.1f", rating) + " §6★");
        lore.add("§7Location: §b" + pos.x() + ", " + pos.z());
        lore.add("");
        lore.add("§aClick to teleport!");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("§6§lIsland Browse")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        String itemName = clicked.getItemMeta().getDisplayName();

        if (itemName.contains("Close")) {
            player.closeInventory();
            return;
        }

        if (itemName.contains("Previous Page")) {
            int currentPage = player.getMetadata("browse_page").get(0).asInt();
            open(player, currentPage - 1);
            return;
        }

        if (itemName.contains("Next Page")) {
            int currentPage = player.getMetadata("browse_page").get(0).asInt();
            open(player, currentPage + 1);
            return;
        }

        // Handle island click
        if (clicked.getType() == Material.PLAYER_HEAD) {
            List<GridPosition> islands = (List<GridPosition>) player.getMetadata("browse_islands").get(0).value();
            int currentPage = player.getMetadata("browse_page").get(0).asInt();
            int startIndex = currentPage * ITEMS_PER_PAGE;

            int slotIndex = event.getSlot() - 9; // Adjust for header row
            int islandIndex = startIndex + slotIndex;

            if (islandIndex >= 0 && islandIndex < islands.size()) {
                GridPosition pos = islands.get(islandIndex);
                Island island = plugin.getIslandManager().getIslandByPosition(pos);

                if (island != null) {
                    player.closeInventory();
                    player.teleport(island.getSpawnLocation());
                    player.sendMessage("§aTeleported to §e" + Bukkit.getOfflinePlayer(island.getOwnerUuid()).getName() + "'s§a island!");
                }
            }
        }
    }
}