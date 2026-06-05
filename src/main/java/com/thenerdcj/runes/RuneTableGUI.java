package com.thenerdcj.runes;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.BaseGUI;
import com.thenerdcj.gui.GUIUtils;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/**
 * Rune upgrade table (BaseGUI). Opened via {@link RuneGUI#openRuneTable(Player)}.
 */
public class RuneTableGUI extends BaseGUI {

    private static final String TITLE = "§5§lRune Table";

    public RuneTableGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    @Override
    protected String getTitlePrefix() {
        return TITLE;
    }

    @Override
    protected String getActionKeyName() {
        return "rune_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 27;
    }

    @Override
    protected int getTotalPages(Player player) {
        return 1;
    }

    @Override
    public void open(Player player, int page) {
        playerPages.put(player.getUniqueId(), 0);
        Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(TITLE));
        populatePage(gui, player, 0);
        addStandardNavigation(gui, 0, 1);
        player.openInventory(gui);
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        RuneManager manager = plugin.getRuneManager();
        if (manager == null) {
            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cRunes system unavailable"));
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand().clone();
        if (held.getType() != Material.AIR) {
            gui.setItem(22, held);
        } else {
            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cHold an item to rune it"));
        }

        Rune currentRune = manager.getRuneFromItem(held);
        int currentTier = manager.getRuneTierFromItem(held);
        int collCount = manager.getRuneCollectionCount(player.getUniqueId());
        int totalRunes = Rune.values().length - 1;

        gui.setItem(4, GUIUtils.createItem(Material.ENCHANTED_BOOK, "§5§lRune Table",
                "§7Apply or upgrade runes on your held item",
                "§7Collection: §a" + collCount + " / " + totalRunes + " §7runes",
                currentRune.isNone() ? "§7No rune currently applied"
                        : "§7Current: " + currentRune.getDisplayName() + " §a(T" + currentTier + "/" + currentRune.getMaxTier() + ")"));

        Set<Rune> owned = manager.getOwnedRunes(player.getUniqueId());
        int slot = 18;
        for (Rune rune : owned) {
            if (slot > 44) break;
            if (slot == 22) slot++;
            if (slot > 44) break;
            if (rune.isNone()) continue;

            boolean isCurrent = rune == currentRune;
            String tierInfo = isCurrent
                    ? " §a§lT" + currentTier + "/" + rune.getMaxTier()
                    : " §7Max " + rune.getMaxTier();

            ItemStack item = GUIUtils.createItem(Material.ENCHANTED_BOOK,
                    (isCurrent ? "§a§l★ " : "§e") + rune.getDisplayName() + tierInfo,
                    "§7" + rune.getDescription(),
                    "§7Rarity: " + rune.getRarity().getColorCode() + rune.getRarity().getDisplayName(),
                    "§7Max Tier: §e" + rune.getMaxTier() + "§7/3",
                    "",
                    "§eLeft-click to apply/upgrade tier");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "TABLE_APPLY_" + rune.name());
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void addStandardNavigation(Inventory gui, int page, int totalPages) {
        gui.setItem(45, GUIUtils.createNavButton(Material.ARROW, "§e§lBack to Wardrobe", ACTION_KEY, "BACK_TO_WARDROBE"));
        gui.setItem(49, GUIUtils.createNavButton(Material.BARRIER, "§c§lRemove Current Rune", ACTION_KEY, "TABLE_REMOVE"));
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        RuneManager manager = plugin.getRuneManager();
        if (manager == null) return;

        if ("BACK_TO_WARDROBE".equals(action)) {
            SoundUtil.click(player);
            player.closeInventory();
            if (plugin.getWardrobeGUI() != null) {
                plugin.getWardrobeGUI().openWardrobe(player);
            }
            return;
        }
        if ("TABLE_REMOVE".equals(action)) {
            SoundUtil.click(player);
            manager.applyRuneToItem(player, player.getInventory().getItemInMainHand(), Rune.NONE);
            open(player);
            return;
        }
        if (action != null && action.startsWith("TABLE_APPLY_")) {
            try {
                Rune rune = Rune.valueOf(action.substring(12));
                ItemStack held = player.getInventory().getItemInMainHand();
                int currentTier = manager.getRuneTierFromItem(held);
                int newTier = (manager.getRuneFromItem(held) == rune)
                        ? Math.min(rune.getMaxTier(), currentTier + 1) : 1;
                manager.applyRuneToItem(player, held, rune, newTier);
                open(player);
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler
    public void onRuneTableClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        if ("BACK_TO_WARDROBE".equals(action) || "TABLE_REMOVE".equals(action)) {
            handleAction(action, meta.getPersistentDataContainer(), player, 0, clicked);
            return;
        }
        if (action.startsWith("TABLE_APPLY_")) {
            SoundUtil.click(player);
            handleAction(action, meta.getPersistentDataContainer(), player, 0, clicked);
        }
    }
}