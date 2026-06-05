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
 * Cosmetic runes picker (BaseGUI). Opened via {@link RuneGUI#open(Player)}.
 */
public class RuneMainGUI extends BaseGUI {

    public RuneMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    @Override
    protected String getTitlePrefix() {
        return "§5§lCosmetic Runes";
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
        playerPages.put(player.getUniqueId(), page);
        Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(getTitlePrefix()));
        addHeader(gui, player, page);
        populatePage(gui, player, page);
        addStandardNavigation(gui, page, getTotalPages(player));
        player.openInventory(gui);
    }

    @Override
    protected void addHeader(Inventory gui, Player player, int page) {
        RuneManager manager = plugin.getRuneManager();
        if (manager == null) {
            gui.setItem(4, GUIUtils.createItem(Material.BARRIER, "§cRunes system unavailable"));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        Rune currentRune = manager.getRuneFromItem(held);
        int currentTier = manager.getRuneTierFromItem(held);
        int collCount = manager.getRuneCollectionCount(player.getUniqueId());
        int totalRunes = Rune.values().length - 1;

        gui.setItem(4, GUIUtils.createItem(Material.ENCHANTED_BOOK, "§5§lCosmetic Runes",
                "§7Apply visual effects to your weapons/tools",
                "§7Collection: §a" + collCount + " / " + totalRunes + " §7runes unlocked",
                "§7Held: " + (currentRune.isNone() ? "§7none" : currentRune.getDisplayName() + " §aT" + currentTier)));
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        RuneManager manager = plugin.getRuneManager();
        if (manager == null) return;

        Set<Rune> owned = manager.getOwnedRunes(player.getUniqueId());
        ItemStack held = player.getInventory().getItemInMainHand();
        Rune currentRune = manager.getRuneFromItem(held);
        int currentTier = manager.getRuneTierFromItem(held);

        if (owned.isEmpty()) {
            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo runes unlocked yet",
                    "§7Earn via prestige, slayer tokens, or milestones."));
            return;
        }

        int slot = 18;
        for (Rune rune : owned) {
            if (slot > 44) break;
            if (rune.isNone()) continue;

            boolean isCurrent = rune == currentRune;
            String tierDisplay = isCurrent
                    ? " §a§l(T" + currentTier + "/" + rune.getMaxTier() + ")"
                    : " §7(Max " + rune.getMaxTier() + ")";

            ItemStack item = GUIUtils.createItem(Material.ENCHANTED_BOOK,
                    (isCurrent ? "§a§l★ " : "§e") + rune.getDisplayName() + tierDisplay,
                    "§7" + rune.getDescription(),
                    "§7Rarity: " + rune.getRarity().getColorCode() + rune.getRarity().getDisplayName(),
                    "§7Max Tier: §e" + rune.getMaxTier() + "§7/3",
                    "",
                    isCurrent ? "§aCurrently on held item" : "§7Not applied to held",
                    "§eClick to apply (T1) or upgrade");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "APPLY_" + rune.name());
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void addStandardNavigation(Inventory gui, int page, int totalPages) {
        gui.setItem(45, GUIUtils.createNavButton(Material.ARROW, "§e§lBack to Wardrobe", ACTION_KEY, "BACK_TO_WARDROBE"));
        gui.setItem(49, GUIUtils.createNavButton(Material.BARRIER, "§c§lRemove Rune from Held Item", ACTION_KEY, "REMOVE"));
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
        if ("REMOVE".equals(action)) {
            SoundUtil.click(player);
            manager.applyRuneToItem(player, player.getInventory().getItemInMainHand(), Rune.NONE);
            open(player);
            return;
        }
        if (action != null && action.startsWith("APPLY_")) {
            try {
                Rune rune = Rune.valueOf(action.substring(6));
                manager.applyRuneToItem(player, player.getInventory().getItemInMainHand(), rune);
                player.closeInventory();
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler
    public void onRuneMainClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(getTitlePrefix())) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        if ("BACK_TO_WARDROBE".equals(action) || "REMOVE".equals(action)) {
            handleAction(action, meta.getPersistentDataContainer(), player, 0, clicked);
            return;
        }
        if (action.startsWith("APPLY_")) {
            SoundUtil.click(player);
            handleAction(action, meta.getPersistentDataContainer(), player, 0, clicked);
        }
    }
}