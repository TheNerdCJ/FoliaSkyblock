package com.thenerdcj.wings;

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
 * Elytra wing cosmetics list (BaseGUI). Opened via {@link WingGUI#open(Player)}.
 */
public class WingMainGUI extends BaseGUI {

    public WingMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    @Override
    protected String getTitlePrefix() {
        return "§b§lElytra Wing Cosmetics";
    }

    @Override
    protected String getActionKeyName() {
        return "wing_action";
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
        playerPages.put(player.getUniqueId(), page);
        Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(getTitlePrefix()));
        addHeader(gui, player, page);
        populatePage(gui, player, page);
        addStandardNavigation(gui, page, getTotalPages(player));
        player.openInventory(gui);
    }

    @Override
    protected void addHeader(Inventory gui, Player player, int page) {
        ElytraWingManager manager = plugin.getElytraWingManager();
        if (manager == null) {
            gui.setItem(4, GUIUtils.createItem(Material.BARRIER, "§cWings system unavailable"));
            return;
        }
        gui.setItem(4, GUIUtils.createItem(Material.ELYTRA, "§b§lYour Elytra Wings",
                "§7Special visual effects while gliding",
                "§7Collection: §a" + manager.getWingCollectionCount(player.getUniqueId()) + " §7styles"));
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        ElytraWingManager manager = plugin.getElytraWingManager();
        if (manager == null) {
            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cWings system unavailable"));
            return;
        }

        Set<ElytraWing> owned = manager.getOwnedWings(player.getUniqueId());
        ElytraWing active = manager.getActiveWing(player.getUniqueId());

        if (owned.isEmpty()) {
            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo wing styles unlocked yet",
                    "§7Earn them through prestige and slayer progression."));
            return;
        }

        int slot = 18;
        for (ElytraWing wing : owned) {
            if (slot > 44) break;
            if (wing.isNone()) continue;

            boolean isActive = wing == active;
            ItemStack item = GUIUtils.createItem(Material.ELYTRA,
                    (isActive ? "§a§l★ " : "§e") + wing.getDisplayName(),
                    "§7" + wing.getDescription(),
                    "§7Rarity: " + wing.getRarity().getColorCode() + wing.getRarity().getDisplayName(),
                    "",
                    isActive ? "§aCurrently Equipped" : "§eLeft-click to equip",
                    "§7Right-click to claim cosmetic elytra item");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "SELECT_" + wing.name());
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void addStandardNavigation(Inventory gui, int page, int totalPages) {
        gui.setItem(45, GUIUtils.createNavButton(Material.ARROW, "§e§lBack to Wardrobe", ACTION_KEY, "BACK_TO_WARDROBE"));
        gui.setItem(49, GUIUtils.createNavButton(Material.ELYTRA, "§6§lClaim Cosmetic Elytra", ACTION_KEY, "CLAIM_ITEM"));
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        ElytraWingManager manager = plugin.getElytraWingManager();
        if (manager == null) return;

        if ("CLAIM_ITEM".equals(action)) {
            SoundUtil.click(player);
            ElytraWing active = manager.getActiveWing(player.getUniqueId());
            if (!active.isNone()) {
                player.getInventory().addItem(manager.getCosmeticElytraItem(active));
                player.sendMessage("§aReceived cosmetic elytra for " + active.getDisplayName());
            } else {
                player.sendMessage("§cYou need to equip a wing style first.");
            }
            open(player);
            return;
        }
        if ("BACK_TO_WARDROBE".equals(action)) {
            SoundUtil.click(player);
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player, com.thenerdcj.wardrobe.WardrobeGUI.View.WINGS);
        }
    }

    @EventHandler
    public void onWingMainClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(getTitlePrefix())) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        if ("CLAIM_ITEM".equals(action) || "BACK_TO_WARDROBE".equals(action)) {
            handleAction(action, meta.getPersistentDataContainer(), player, 0, clicked);
            return;
        }

        if (!action.startsWith("SELECT_")) return;

        ElytraWingManager manager = plugin.getElytraWingManager();
        if (manager == null) return;

        try {
            ElytraWing wing = ElytraWing.valueOf(action.substring(7));
            SoundUtil.click(player);
            if (event.isRightClick()) {
                player.getInventory().addItem(manager.getCosmeticElytraItem(wing));
                player.sendMessage("§aReceived cosmetic elytra for " + wing.getDisplayName());
            } else {
                manager.setActiveWing(player, wing);
            }
            open(player);
        } catch (Exception ignored) {
        }
    }
}