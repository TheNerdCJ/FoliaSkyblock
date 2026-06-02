package com.thenerdcj.runes;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.GUIUtils;
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

import java.util.Set;

/**
 * Dedicated GUI for Cosmetic Runes.
 */
public class RuneGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public RuneGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "rune_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§5§lCosmetic Runes");

        RuneManager manager = plugin.getRuneManager();
        if (manager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cRunes system unavailable"));
            player.openInventory(inv);
            return;
        }

        Set<Rune> owned = manager.getOwnedRunes(player.getUniqueId());
        ItemStack held = player.getInventory().getItemInMainHand();

        Rune currentRune = manager.getRuneFromItem(held);
        int currentTier = manager.getRuneTierFromItem(held);
        int collCount = manager.getRuneCollectionCount(player.getUniqueId());
        int totalRunes = Rune.values().length - 1; // exclude NONE

        inv.setItem(4, GUIUtils.createItem(Material.ENCHANTED_BOOK, "§5§lCosmetic Runes",
                "§7Apply visual effects to your weapons/tools",
                "§7Collection: §a" + collCount + " / " + totalRunes + " §7runes unlocked",
                "§7Max tiers shown per rune (tier up on held item)"));

        if (owned.isEmpty()) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo runes unlocked yet",
                    "§7Earn via prestige, slayer tokens, or milestones."));
            // Back button even on empty
            addBackButton(inv, 45);
            player.openInventory(inv);
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

            // Proper meta/PDC handling (fix previous broken pattern)
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(ACTION_KEY, PersistentDataType.STRING, "APPLY_" + rune.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        // Remove rune button
        ItemStack remove = GUIUtils.createItem(Material.BARRIER, "§c§lRemove Rune from Held Item",
                "§7Clears any rune from your held weapon/tool");
        ItemMeta rm = remove.getItemMeta();
        if (rm != null) {
            PersistentDataContainer removePdc = rm.getPersistentDataContainer();
            removePdc.set(ACTION_KEY, PersistentDataType.STRING, "REMOVE");
            remove.setItemMeta(rm);
        }
        inv.setItem(49, remove);

        addBackButton(inv, 45);

        player.openInventory(inv);
    }

    public void openRuneTable(Player player) {
        // More advanced "table" style GUI focused on the held item
        Inventory inv = Bukkit.createInventory(null, 54, "§5§lRune Table");

        RuneManager manager = plugin.getRuneManager();
        ItemStack held = player.getInventory().getItemInMainHand().clone();

        // Center: Held item preview
        if (held.getType() != Material.AIR) {
            inv.setItem(22, held);
        } else {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cHold an item to rune it"));
        }

        Rune currentRune = manager.getRuneFromItem(held);
        int currentTier = manager.getRuneTierFromItem(held);
        int collCount = manager.getRuneCollectionCount(player.getUniqueId());
        int totalRunes = Rune.values().length - 1;

        inv.setItem(4, GUIUtils.createItem(Material.ENCHANTED_BOOK, "§5§lRune Table",
                "§7Apply or upgrade runes on your held item",
                "§7Collection: §a" + collCount + " / " + totalRunes + " §7runes",
                currentRune.isNone() ? "§7No rune currently applied" : 
                "§7Current: " + currentRune.getDisplayName() + " §a(T" + currentTier + "/" + currentRune.getMaxTier() + ")"));

        // Show owned runes in a grid for selection
        Set<Rune> owned = manager.getOwnedRunes(player.getUniqueId());
        int slot = 18;
        for (Rune rune : owned) {
            if (slot > 44 || slot == 22) slot++;
            if (slot > 44) break;

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

            // Proper meta/PDC (fixed pattern)
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(ACTION_KEY, PersistentDataType.STRING, "TABLE_APPLY_" + rune.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        // Remove button
        ItemStack remove = GUIUtils.createItem(Material.BARRIER, "§c§lRemove Current Rune",
                "§7Clears the rune effect from held item");
        ItemMeta rm = remove.getItemMeta();
        if (rm != null) {
            PersistentDataContainer rp = rm.getPersistentDataContainer();
            rp.set(ACTION_KEY, PersistentDataType.STRING, "TABLE_REMOVE");
            remove.setItemMeta(rm);
        }
        inv.setItem(49, remove);

        addBackButton(inv, 45);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals("§5§lCosmetic Runes") && !title.equals("§5§lRune Table")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        RuneManager manager = plugin.getRuneManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            if (plugin.getWardrobeGUI() != null) {
                plugin.getWardrobeGUI().openWardrobe(player);
            }
            return;
        }

        if (action.equals("REMOVE") || action.equals("TABLE_REMOVE")) {
            ItemStack held = player.getInventory().getItemInMainHand();
            manager.applyRuneToItem(player, held, Rune.NONE);
            if (title.equals("§5§lRune Table")) openRuneTable(player);
            else open(player);
            return;
        }

        if (action.startsWith("APPLY_")) {
            String runeName = action.substring(6);
            try {
                Rune rune = Rune.valueOf(runeName);
                ItemStack held = player.getInventory().getItemInMainHand();
                manager.applyRuneToItem(player, held, rune);
                player.closeInventory();
            } catch (Exception ignored) {}
        }

        if (action.startsWith("TABLE_APPLY_")) {
            String runeName = action.substring(12);
            try {
                Rune rune = Rune.valueOf(runeName);
                ItemStack held = player.getInventory().getItemInMainHand();
                int currentTier = manager.getRuneTierFromItem(held);
                int newTier = (manager.getRuneFromItem(held) == rune) ? Math.min(rune.getMaxTier(), currentTier + 1) : 1;
                manager.applyRuneToItem(player, held, rune, newTier);
                openRuneTable(player);
            } catch (Exception ignored) {}
        }
    }

    private void addBackButton(Inventory inv, int slot) {
        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe",
                "§7Return to the main cosmetic wardrobe");
        ItemMeta bm = back.getItemMeta();
        if (bm != null) {
            PersistentDataContainer bp = bm.getPersistentDataContainer();
            bp.set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
            back.setItemMeta(bm);
        }
        inv.setItem(slot, back);
    }
}
