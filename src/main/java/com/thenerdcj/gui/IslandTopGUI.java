package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.IslandWorthManager;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Paginated Island Top / Leaderboard GUI
 * Categories: Worth, Worth Level, Members
 *
 * Deep modernization pass:
 * - Manual skull (PLAYER_HEAD + SkullMeta) creation in the leaderboard loop converted to GUIUtils + helper.
 * - createNavItem refactored to GUIUtils.createItem.
 * - All titles now use MessageUtil.legacy.
 * - Preserved async worth loading, category switching, pagination, and click logic.
 *
 * Large scale compression/integration pass: wired from /is top command (removed "Full GUI coming soon..." placeholder),
 * switched fetch to use (limit, offset) DB-paginated path from IslandWorthManager for true server-side paging in GUI pages,
 * alt categories use bounded buffer + in-mem sort for compression (no full scans). Registered in FoliaSkyblock.
 * Advances leaderboard/top compression, paged GUIs, use of DB offset pagination in player features.
 */
public class IslandTopGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey TOP_OWNER_KEY;
    private static final int ITEMS_PER_PAGE = 45;

    public IslandTopGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandTopGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.TOP_OWNER_KEY = new NamespacedKey(plugin, "top_owner");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, Category category, int page) {
        final int finalPage = Math.max(0, page);
        final Category finalCat = (category == null ? Category.WORTH : category);

        // Large scale compression/optim: now use *dedicated* per-category DB-paginated queries (getTop...ByLevel / ByMemberCount)
        // with uniform (limit, offset) for *every* category + GUI page. No more worth-buffer + client sort hack for alt cats.
        // True server-side ORDER BY for the chosen metric + LIMIT OFFSET. Rank numbers account for page offset.
        // Advances the explicit "Per-category dedicated paginated tops" suggestion (and "use the offset support", "more paged in GUIs").
        int fetchLimit = ITEMS_PER_PAGE;
        int fetchOffset = finalPage * ITEMS_PER_PAGE;

        // Fetch the correctly ordered + paged data for the chosen category
        java.util.concurrent.CompletableFuture<java.util.List<IslandWorthManager.IslandTopEntry>> dataF;
        if (finalCat == Category.WORTH) {
            dataF = plugin.getIslandWorthManager().getTopIslandsByWorth(fetchLimit, fetchOffset);
        } else if (finalCat == Category.LEVEL) {
            dataF = plugin.getIslandWorthManager().getTopIslandsByLevel(fetchLimit, fetchOffset);
        } else {
            dataF = plugin.getIslandWorthManager().getTopIslandsByMemberCount(fetchLimit, fetchOffset);
        }

        dataF.thenAccept(topList -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                String title = "§6§lIsland Top - " + finalCat.display + " §7(Page " + (finalPage + 1) + ")";
                Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy(title));

                // Data is already correctly sorted + paged from the dedicated query; just slice (usually the whole small list)
                java.util.List<IslandWorthManager.IslandTopEntry> displayList = new ArrayList<>(topList);
                int start = 0;
                int end = Math.min(ITEMS_PER_PAGE, displayList.size());

                int slot = 10;
                for (int i = start; i < end; i++) {
                    IslandWorthManager.IslandTopEntry entry = displayList.get(i);
                    int globalRank = finalPage * ITEMS_PER_PAGE + i + 1;

                    List<String> lore = Arrays.asList(
                        "§7Worth: §6" + String.format("%,.0f", entry.worth),
                        "§7Worth Level: §b" + entry.level,
                        "§7Members: §a" + entry.memberCount,
                        "",
                        "§aLeft-click: §fVisit island",
                        "§cShift+Left (staff): §fInspect"
                    );

                    ItemStack item = createTopIslandSkull(entry.owner, "§e#" + globalRank + " §f" + entry.displayName, lore);
                    // PDC attached inside createTopIslandSkull for owner (robust click -> visit/inspect without name/DB roundtrips on hot path)
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
        return GUIUtils.createItem(material, name);
    }

    private void attachTopPDC(ItemStack item, java.util.UUID ownerUuid) {
        if (item == null || item.getItemMeta() == null || ownerUuid == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(TOP_OWNER_KEY, PersistentDataType.STRING, ownerUuid.toString());
        item.setItemMeta(meta);
    }

    private ItemStack createTopIslandSkull(java.util.UUID ownerUuid, String displayName, List<String> lore) {
        ItemStack skull = GUIUtils.createItem(Material.PLAYER_HEAD, displayName, lore.toArray(new String[0]));
        ItemMeta meta = skull.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            try {
                org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerUuid);
                skullMeta.setOwningPlayer(offline);
                skull.setItemMeta(skullMeta);
            } catch (Exception ignored) {}
        }
        // Attach owner for robust click handling (visit/inspect) without extra lookups - compression for large tops lists
        attachTopPDC(skull, ownerUuid);
        return skull;
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
        } else {
            // Top list entry (skulls) - use PDC for owner (compression: no name lookup or extra query)
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() != null) {
                String ownerStr = clicked.getItemMeta().getPersistentDataContainer().get(TOP_OWNER_KEY, PersistentDataType.STRING);
                if (ownerStr != null) {
                    try {
                        java.util.UUID owner = java.util.UUID.fromString(ownerStr);
                        handleTopEntryClick(player, owner, event.isShiftClick());
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void handleTopEntryClick(Player player, java.util.UUID owner, boolean shiftClick) {
        boolean isStaff = player.hasPermission("foliasb.admin.inspect") || player.hasPermission("foliasb.staff") || player.hasPermission("foliasb.admin");
        if (shiftClick && isStaff && plugin.getAdminIslandInspectGUI() != null) {
            plugin.getAdminIslandInspectGUI().open(player, owner);
            return;
        }

        // Normal visit: resolve island (prefer current dim, fallback to NORMAL), teleport to spawn
        org.bukkit.World.Environment env = player.getWorld().getEnvironment();
        com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(owner, env);
        if (island == null) {
            island = plugin.getIslandManager().getIsland(owner, org.bukkit.World.Environment.NORMAL);
        }
        if (island != null && island.getSpawnLocation() != null) {
            player.closeInventory();
            player.teleport(island.getSpawnLocation());
            String name = Bukkit.getOfflinePlayer(owner).getName();
            player.sendMessage("§aTeleported to top island of §e" + (name != null ? name : owner.toString().substring(0, 8)) + "§a.");
        } else {
            player.sendMessage("§cCould not locate the island for that top entry (may be in another dimension or reset).");
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