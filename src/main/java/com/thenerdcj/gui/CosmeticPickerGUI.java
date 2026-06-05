package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Shared layout for wardrobe cosmetic pickers: header @4, remove @18, grid @19–44, wardrobe back @45.
 */
public abstract class CosmeticPickerGUI extends BaseGUI {

    protected CosmeticPickerGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    protected abstract Material getHeaderIcon();

    protected abstract String[] getHeaderLore(Player player);

    protected abstract String getRemoveButtonLabel();

    protected abstract String getNoneActionId();

    protected abstract void populateSkinGrid(Inventory gui, Player player);

    protected abstract void handlePickerAction(String action, Player player);

    @Override
    protected final void populatePage(Inventory gui, Player player, int page) {
        populateSkinGrid(gui, player);
    }

    @Override
    protected int getItemsPerPage() {
        return 26;
    }

    @Override
    protected int getTotalPages(Player player) {
        return 1;
    }

    @Override
    public void open(Player player, int page) {
        if (!isSystemAvailable(player)) {
            return;
        }
        playerPages.put(player.getUniqueId(), page);
        Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(getTitlePrefix()));
        if (useGlassBackground()) {
            ItemStack glass = GUIUtils.createItem(getGlassMaterial(), "§8 ");
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                gui.setItem(i, glass);
            }
        }
        gui.setItem(4, GUIUtils.createItem(getHeaderIcon(), getTitlePrefix(), getHeaderLore(player)));
        decorateHeaderExtras(gui, player);
        gui.setItem(18, GUIUtils.createNavButton(Material.BARRIER, getRemoveButtonLabel(), ACTION_KEY, getNoneActionId()));
        populateSkinGrid(gui, player);
        addStandardNavigation(gui, page, 1);
        player.openInventory(gui);
    }

    protected boolean useGlassBackground() {
        return false;
    }

    protected Material getGlassMaterial() {
        return Material.BLACK_STAINED_GLASS_PANE;
    }

    /** Optional slots filled after the header (e.g. active cosmetic indicator). */
    protected void decorateHeaderExtras(Inventory gui, Player player) {
    }

    protected boolean isSystemAvailable(Player player) {
        return true;
    }

    protected int getBackButtonSlot() {
        return 45;
    }

    protected String getBackButtonLabel() {
        return "§e§lBack to Wardrobe";
    }

    protected String getBackActionId() {
        return "BACK_TO_WARDROBE";
    }

    @Override
    protected void addStandardNavigation(Inventory gui, int page, int totalPages) {
        gui.setItem(getBackButtonSlot(),
                GUIUtils.createNavButton(Material.ARROW, getBackButtonLabel(), ACTION_KEY, getBackActionId()));
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        if (getBackActionId().equals(action)) {
            SoundUtil.click(player);
            player.closeInventory();
            onBackFromPicker(player);
            return;
        }
        if (getNoneActionId().equals(action)) {
            SoundUtil.click(player);
            handlePickerAction(action, player);
            open(player);
            return;
        }
        SoundUtil.click(player);
        handlePickerAction(action, player);
    }

    protected void onBackFromPicker(Player player) {
        plugin.getWardrobeGUI().openWardrobe(player);
    }
}