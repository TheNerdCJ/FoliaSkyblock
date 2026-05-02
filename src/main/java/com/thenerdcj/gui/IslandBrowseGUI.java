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

    public void open(Player player, int page) {
        // Check permission
        if (!player.hasPermission("foliasb.browse") && !player.hasPermission("foliasb.browse.islands")) {
            player.sendMessage("§cYou don't have permission to browse islands!");
            return;
        }

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

                    int totalPages = (int) Math.ceil(sortedIslands.size() / (double) ITEMS_PER_PAGE);
                    if (totalPages == 0) totalPages = 1;

                    if (page < 0) page = 0;
                    if (page >= totalPages) page = totalPages - 1;

                    Inventory gui = Bukkit.createInventory(null, 54, "§6§lIsland Browse §7(Page " + (page + 1) + "/" + totalPages + ")");

                    // Header
                    gui.setItem(4, createItem(Material.COMPASS, "§6§lIsland Discovery",
                            "§7Browse public islands",
                            "§7Sorted by popularity",
                            "§7Total Islands: §e" + sortedIslands.size()));

                    // Calculate page range
                    int startIndex = page * ITEMS_PER_PAGE;
                    int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, sortedIslands.size());

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

                    // Navigation buttons
                    if (page > 0) {
                        gui.setItem(45, createItem(Material.ARROW, "§a§lPrevious Page", "§7Click to go back"));
                    }
                    if (page < totalPages - 1) {
                        gui.setItem(53, createItem(Material.ARROW, "§a§lNext Page", "§7Click to go forward"));
                    }

                    // Close button
                    gui.setItem(49, createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));

                    player.openInventory(gui);
                    player.setMetadata("browse_page", new org.bukkit.metadata.FixedMetadataValue(plugin, page));
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
        Island island = plugin.getIslandManager().getIslandByPosition(pos);
        if (island != null && island.getOwnerUuid() != null) {
            org.bukkit.OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwnerUuid());
            ownerName = owner.getName() != null ? owner.getName() : "Unknown";
            meta.setOwningPlayer(owner);
        }

        meta.setDisplayName("§e§l" + ownerName + "'s Island");

        List<String> lore = new ArrayList<>();
        lore.add("§7Rating: " + getStarDisplay(rating) + " §7(" + String.format("%.1f", rating) + "/5)");
        lore.add("§7Location: §f" + pos.getX() + ", " + pos.getZ());
        lore.add("§7Dimension: §f" + pos.getDimension().name());
        lore.add("");
        lore.add("§a§lClick to Teleport!");
        lore.add("§7(Uses your warp permission)");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String getStarDisplay(double rating) {
        StringBuilder stars = new StringBuilder();
        int fullStars = (int) Math.floor(rating);
        boolean hasHalf = (rating - fullStars) >= 0.5;

        for (int i = 0; i < fullStars; i++) {
            stars.append("§6★");
        }
        if (hasHalf) {
            stars.append("§e☆");
        }
        for (int i = fullStars + (hasHalf ? 1 : 0); i < 5; i++) {
            stars.append("§7☆");
        }
        return stars.toString();
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

        // Navigation
        if (itemName.contains("Previous Page")) {
            int currentPage = player.hasMetadata("browse_page") ? player.getMetadata("browse_page").get(0).asInt() : 0;
            player.closeInventory();
            new IslandBrowseGUI(plugin).open(player, currentPage - 1);
            return;
        }

        if (itemName.contains("Next Page")) {
            int currentPage = player.hasMetadata("browse_page") ? player.getMetadata("browse_page").get(0).asInt() : 0;
            player.closeInventory();
            new IslandBrowseGUI(plugin).open(player, currentPage + 1);
            return;
        }

        if (itemName.contains("Close")) {
            player.closeInventory();
            return;
        }

        // Island teleport
        if (clicked.getType() == Material.PLAYER_HEAD) {
            if (!player.hasMetadata("browse_islands")) {
                player.sendMessage("§cError: Could not find island data. Please reopen the GUI.");
                return;
            }

            @SuppressWarnings("unchecked")
            List<GridPosition> islands = (List<GridPosition>) player.getMetadata("browse_islands").get(0).value();
            int currentPage = player.hasMetadata("browse_page") ? player.getMetadata("browse_page").get(0).asInt() : 0;

            int slotIndex = event.getSlot() - 9; // Adjust for header row
            int islandIndex = currentPage * 45 + slotIndex;

            if (islandIndex >= 0 && islandIndex < islands.size()) {
                GridPosition pos = islands.get(islandIndex);

                plugin.getIslandWarpManager().getWarp(pos).thenAccept(warp -> {
                    if (warp != null && warp.isEnabled() && warp.getWarpLocation() != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.teleport(warp.getWarpLocation());
                            player.sendMessage("§aTeleported to island!");
                            player.closeInventory();
                        });
                    } else {
                        player.sendMessage("§cThis island's warp is no longer available.");
                    }
                });
            }
        }
    }
}