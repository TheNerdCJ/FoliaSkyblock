package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandWarp;
import com.thenerdcj.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island browse / discovery GUI (BaseGUI + PDC). Opened via {@link IslandBrowseGUI#open(Player, int)}.
 */
public class IslandBrowseMainGUI extends BaseGUI {

    private static final int ITEMS_PER_PAGE = 45;

    private final NamespacedKey GRID_POS_KEY;

    private record BrowseSession(List<GridPosition> islands, int totalPages) {}

    private final Map<UUID, BrowseSession> sessions = new ConcurrentHashMap<>();

    public IslandBrowseMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
        this.GRID_POS_KEY = new NamespacedKey(plugin, "browse_grid_pos");
    }

    public IslandBrowseMainGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
        this.GRID_POS_KEY = new NamespacedKey(plugin, "browse_grid_pos");
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lIsland Browse";
    }

    @Override
    protected String getActionKeyName() {
        return "island_browse_action";
    }

    @Override
    protected int getItemsPerPage() {
        return ITEMS_PER_PAGE;
    }

    public void open(Player player, int requestedPage) {
        if (!player.hasPermission("foliasb.browse") && !player.hasPermission("foliasb.browse.islands")) {
            player.sendMessage("§cYou don't have permission to browse islands!");
            return;
        }

        final int page = requestedPage;
        plugin.getIslandRatingManager().getTopRatedIslands(100).thenAccept(topRated ->
                plugin.getIslandWarpManager().getAllPublicWarps(500).thenAccept(allWarps ->
                        plugin.getThreadSafety().runOnMainThread(() -> {
                            List<GridPosition> sortedIslands = new ArrayList<>();
                            for (GridPosition pos : topRated.keySet()) {
                                if (allWarps.containsKey(pos)) {
                                    sortedIslands.add(pos);
                                }
                            }
                            for (GridPosition pos : allWarps.keySet()) {
                                if (!sortedIslands.contains(pos)) {
                                    sortedIslands.add(pos);
                                }
                            }

                            int totalIslands = sortedIslands.size();
                            int totalPages = totalIslands > 0
                                    ? (int) Math.ceil(totalIslands / (double) ITEMS_PER_PAGE)
                                    : 1;
                            int validPage = Math.max(0, Math.min(page, totalPages - 1));

                            playerPages.put(player.getUniqueId(), validPage);
                            sessions.put(player.getUniqueId(), new BrowseSession(sortedIslands, totalPages));

                            String title = getTitlePrefix() + " §7(Page " + (validPage + 1) + "/" + totalPages + ")";
                            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(title));

                            gui.setItem(4, GUIUtils.createItem(Material.COMPASS, "§6§lIsland Discovery",
                                    "§7Browse public islands",
                                    "§7Sorted by popularity",
                                    "§7Total Islands: §e" + totalIslands));

                            int startIndex = validPage * ITEMS_PER_PAGE;
                            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalIslands);
                            int slot = 9;
                            for (int i = startIndex; i < endIndex; i++) {
                                GridPosition pos = sortedIslands.get(i);
                                IslandWarp warp = allWarps.get(pos);
                                if (warp != null && warp.isEnabled()) {
                                    double rating = topRated.getOrDefault(pos, 0.0);
                                    gui.setItem(slot++, createIslandItem(pos, warp, rating));
                                }
                            }

                            addBrowseNavigation(gui, validPage, totalPages);
                            player.openInventory(gui);
                        })));
    }

    private void addBrowseNavigation(Inventory gui, int page, int totalPages) {
        if (page > 0) {
            gui.setItem(PREV_SLOT, GUIUtils.createNavButton(Material.ARROW, "§a§lPrevious Page", ACTION_KEY, "PREV"));
        }
        gui.setItem(CLOSE_SLOT, GUIUtils.createNavButton(Material.BARRIER, "§c§lClose", ACTION_KEY, "CLOSE"));
        if (page < totalPages - 1) {
            gui.setItem(NEXT_SLOT, GUIUtils.createNavButton(Material.ARROW, "§a§lNext Page", ACTION_KEY, "NEXT"));
        }
    }

    @Override
    protected int getTotalPages(Player player) {
        BrowseSession session = sessions.get(player.getUniqueId());
        return session != null ? session.totalPages() : 1;
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        if ("VISIT_ISLAND".equals(action)) {
            String posStr = pdc.get(GRID_POS_KEY, PersistentDataType.STRING);
            if (posStr == null) {
                return;
            }
            try {
                GridPosition pos = GridPosition.fromString(posStr);
                Island island = plugin.getIslandManager().getIslandByPosition(pos);
                if (island != null && island.getSpawnLocation() != null) {
                    player.closeInventory();
                    player.teleport(island.getSpawnLocation());
                    String ownerName = Bukkit.getOfflinePlayer(island.getOwnerUuid()).getName();
                    player.sendMessage("§aTeleported to §e" + (ownerName != null ? ownerName : "island") + "§a!");
                }
            } catch (Exception ignored) {
            }
        }
    }

    private ItemStack createIslandItem(GridPosition pos, IslandWarp warp, double rating) {
        String ownerName = "Unknown";
        try {
            Island island = plugin.getIslandManager().getIslandByPosition(pos);
            if (island != null) {
                ownerName = plugin.getNameCache().getName(island.getOwnerUuid());
            }
        } catch (Exception ignored) {
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7Rating: §e" + String.format("%.1f", rating) + " §6★");
        lore.add("§7Location: §b" + pos.x() + ", " + pos.z());
        lore.add("");
        lore.add("§aClick to teleport!");

        ItemStack item = GUIUtils.createItem(Material.PLAYER_HEAD, "§e§l" + ownerName + "'s Island", lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "VISIT_ISLAND");
            meta.getPersistentDataContainer().set(GRID_POS_KEY, PersistentDataType.STRING, pos.toString());
            if (meta instanceof SkullMeta skullMeta) {
                try {
                    Island island = plugin.getIslandManager().getIslandByPosition(pos);
                    if (island != null) {
                        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(island.getOwnerUuid()));
                    }
                } catch (Exception ignored) {
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}