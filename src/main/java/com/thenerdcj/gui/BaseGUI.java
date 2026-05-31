package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base for GUIs to eliminate massive duplication of:
 * - createItem / nav patterns
 * - pagination state (playerPages)
 * - title guards
 * - standard nav (45/49/53)
 * - basic PDC action routing
 *
 * Subclasses implement populatePage + handleAction.
 */
public abstract class BaseGUI implements Listener {

    protected final FoliaSkyblock plugin;
    protected final NamespacedKey ACTION_KEY;
    protected final Map<UUID, Integer> playerPages = new HashMap<>();

    protected static final int INVENTORY_SIZE = 54;
    protected static final int PREV_SLOT = 45;
    protected static final int CLOSE_SLOT = 49;
    protected static final int NEXT_SLOT = 53;

    public BaseGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public BaseGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, getActionKeyName());
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    protected abstract String getTitlePrefix();
    protected abstract String getActionKeyName();
    protected abstract int getItemsPerPage();

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);

        String title = getTitlePrefix() + " §7(Page " + (page + 1) + ")";
        Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, title);

        addHeader(gui, player, page);
        populatePage(gui, player, page);
        addStandardNavigation(gui, page, getTotalPages(player));

        player.openInventory(gui);
    }

    protected void addHeader(Inventory gui, Player player, int page) {
        // Subclasses can override for custom header (info book, balance, etc.)
    }

    protected abstract void populatePage(Inventory gui, Player player, int page);

    protected int getTotalPages(Player player) {
        return 1; // Subclasses override for real pagination
    }

    protected void addStandardNavigation(Inventory gui, int page, int totalPages) {
        if (page > 0) {
            gui.setItem(PREV_SLOT, GUIUtils.createNavButton(Material.ARROW, "§a§lPrevious Page", ACTION_KEY, "PREV"));
        }
        gui.setItem(CLOSE_SLOT, GUIUtils.createNavButton(Material.BARRIER, "§c§lClose", ACTION_KEY, "CLOSE"));
        if (page < totalPages - 1) {
            gui.setItem(NEXT_SLOT, GUIUtils.createNavButton(Material.ARROW, "§a§lNext Page", ACTION_KEY, "NEXT"));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith(getTitlePrefix())) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);

        if (action != null) {
            switch (action) {
                case "PREV" -> open(player, Math.max(0, currentPage - 1));
                case "NEXT" -> open(player, Math.min(getTotalPages(player) - 1, currentPage + 1));
                case "CLOSE" -> player.closeInventory();
                default -> handleAction(action, pdc, player, currentPage, clicked);
            }
            return;
        }

        // Fallback for legacy name-based clicks (can be removed later)
        handleLegacyClick(player, clicked, currentPage);
    }

    protected abstract void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked);

    protected void handleLegacyClick(Player player, ItemStack clicked, int currentPage) {
        // Optional override for old GUIs during migration
    }

    protected void refresh(Player player) {
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);
        open(player, page);
    }
}