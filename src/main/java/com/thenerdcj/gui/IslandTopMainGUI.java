package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.IslandWorthManager;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paginated island top leaderboard (BaseGUI + PDC). Opened via {@link IslandTopGUI#open(Player, Category, int)}.
 */
public class IslandTopMainGUI extends BaseGUI {

    private final NamespacedKey TOP_OWNER_KEY;
    private final Map<UUID, IslandTopGUI.Category> playerCategories = new ConcurrentHashMap<>();

    public IslandTopMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
        this.TOP_OWNER_KEY = new NamespacedKey(plugin, "top_owner");
    }

    public IslandTopMainGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
        this.TOP_OWNER_KEY = new NamespacedKey(plugin, "top_owner");
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lIsland Top";
    }

    @Override
    protected String getActionKeyName() {
        return "island_top_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 45;
    }

    @Override
    protected int getTotalPages(Player player) {
        return Integer.MAX_VALUE;
    }

    public void open(Player player, IslandTopGUI.Category category, int page) {
        final int finalPage = Math.max(0, page);
        final IslandTopGUI.Category finalCat = category == null ? IslandTopGUI.Category.WORTH : category;
        playerPages.put(player.getUniqueId(), finalPage);
        playerCategories.put(player.getUniqueId(), finalCat);

        int fetchLimit = getItemsPerPage();
        int fetchOffset = finalPage * getItemsPerPage();

        var dataF = switch (finalCat) {
            case WORTH -> plugin.getIslandWorthManager().getTopIslandsByWorth(fetchLimit, fetchOffset);
            case LEVEL -> plugin.getIslandWorthManager().getTopIslandsByLevel(fetchLimit, fetchOffset);
            case MEMBERS -> plugin.getIslandWorthManager().getTopIslandsByMemberCount(fetchLimit, fetchOffset);
        };

        dataF.thenAccept(topList -> plugin.getThreadSafety().runOnMainThread(() -> {
            String title = getTitlePrefix() + " - " + finalCat.display + " §7(Page " + (finalPage + 1) + ")";
            Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(title));

            int slot = 10;
            for (int i = 0; i < topList.size() && i < getItemsPerPage(); i++) {
                IslandWorthManager.IslandTopEntry entry = topList.get(i);
                int globalRank = finalPage * getItemsPerPage() + i + 1;
                List<String> lore = Arrays.asList(
                        "§7Worth: §6" + String.format("%,.0f", entry.worth),
                        "§7Worth Level: §b" + entry.level,
                        "§7Members: §a" + entry.memberCount,
                        "",
                        "§aLeft-click: §fVisit island",
                        "§cShift+Left (staff): §fInspect"
                );
                gui.setItem(slot, createTopIslandSkull(entry.owner, "§e#" + globalRank + " §f" + entry.displayName, lore));
                slot++;
                if ((slot - 9) % 9 == 0) {
                    slot += 2;
                }
            }

            addTopNavigation(gui, finalPage);
            player.openInventory(gui);
        }));
    }

    private void addTopNavigation(Inventory gui, int page) {
        if (page > 0) {
            gui.setItem(45, GUIUtils.createNavButton(Material.ARROW, "§aPrevious", ACTION_KEY, "PREV"));
        }
        gui.setItem(49, GUIUtils.createNavButton(Material.GOLD_INGOT, "§eWorth", ACTION_KEY, "CAT_WORTH"));
        gui.setItem(50, GUIUtils.createNavButton(Material.EXPERIENCE_BOTTLE, "§eLevel", ACTION_KEY, "CAT_LEVEL"));
        gui.setItem(51, GUIUtils.createNavButton(Material.PLAYER_HEAD, "§eMembers", ACTION_KEY, "CAT_MEMBERS"));
        gui.setItem(53, GUIUtils.createNavButton(Material.ARROW, "§aNext", ACTION_KEY, "NEXT"));
    }

    @Override
    public void open(Player player, int page) {
        IslandTopGUI.Category cat = playerCategories.getOrDefault(player.getUniqueId(), IslandTopGUI.Category.WORTH);
        open(player, cat, page);
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        IslandTopGUI.Category cat = playerCategories.getOrDefault(player.getUniqueId(), IslandTopGUI.Category.WORTH);
        switch (action) {
            case "PREV" -> open(player, cat, Math.max(0, currentPage - 1));
            case "NEXT" -> open(player, cat, currentPage + 1);
            case "CAT_WORTH" -> open(player, IslandTopGUI.Category.WORTH, 0);
            case "CAT_LEVEL" -> open(player, IslandTopGUI.Category.LEVEL, 0);
            case "CAT_MEMBERS" -> open(player, IslandTopGUI.Category.MEMBERS, 0);
            default -> {
            }
        }
    }

    @Override
    protected void handleLegacyClick(Player player, ItemStack clicked, int currentPage) {
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD || clicked.getItemMeta() == null) {
            return;
        }
        String ownerStr = clicked.getItemMeta().getPersistentDataContainer().get(TOP_OWNER_KEY, PersistentDataType.STRING);
        if (ownerStr == null) {
            return;
        }
        try {
            UUID owner = UUID.fromString(ownerStr);
            handleTopEntryClick(player, owner, false);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().getTitle().startsWith(getTitlePrefix())) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }
        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action != null) {
            SoundUtil.click(player);
            handleAction(action, meta.getPersistentDataContainer(), player, currentPage, clicked);
            return;
        }
        if (event.isShiftClick() && clicked.getType() == Material.PLAYER_HEAD) {
            String ownerStr = meta.getPersistentDataContainer().get(TOP_OWNER_KEY, PersistentDataType.STRING);
            if (ownerStr != null) {
                try {
                    handleTopEntryClick(player, UUID.fromString(ownerStr), true);
                } catch (Exception ignored) {
                }
            }
            return;
        }
        handleLegacyClick(player, clicked, currentPage);
    }

    private void handleTopEntryClick(Player player, UUID owner, boolean shiftClick) {
        boolean isStaff = player.hasPermission("foliasb.admin.inspect")
                || player.hasPermission("foliasb.staff")
                || player.hasPermission("foliasb.admin");
        if (shiftClick && isStaff && plugin.getAdminIslandInspectGUI() != null) {
            plugin.getAdminIslandInspectGUI().open(player, owner);
            return;
        }

        var env = player.getWorld().getEnvironment();
        com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(owner, env);
        if (island == null) {
            island = plugin.getIslandManager().getIsland(owner, org.bukkit.World.Environment.NORMAL);
        }
        if (island != null && island.getSpawnLocation() != null) {
            player.closeInventory();
            player.teleport(island.getSpawnLocation());
            String name = Bukkit.getOfflinePlayer(owner).getName();
            player.sendMessage("§aTeleported to top island of §e"
                    + (name != null ? name : owner.toString().substring(0, 8)) + "§a.");
        } else {
            player.sendMessage("§cCould not locate the island for that top entry (may be in another dimension or reset).");
        }
    }

    private ItemStack createTopIslandSkull(UUID ownerUuid, String displayName, List<String> lore) {
        ItemStack skull = GUIUtils.createItem(Material.PLAYER_HEAD, displayName, lore.toArray(new String[0]));
        ItemMeta meta = skull.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            try {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerUuid));
                skull.setItemMeta(skullMeta);
            } catch (Exception ignored) {
            }
        }
        if (skull.getItemMeta() != null) {
            ItemMeta m = skull.getItemMeta();
            m.getPersistentDataContainer().set(TOP_OWNER_KEY, PersistentDataType.STRING, ownerUuid.toString());
            skull.setItemMeta(m);
        }
        return skull;
    }
}