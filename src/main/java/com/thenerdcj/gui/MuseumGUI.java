package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.MuseumManager;
import org.bukkit.Bukkit; // kept for createInventory / other; scheduler calls routed via ThreadSafety
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;

/**
 * Museum GUI (Hypixel depth for task 4).
 * View donated, donate held item, see tokens (spend for cosmetics in future).
 * Play-to-Win: donate only play-earned uniques; rewards cosmetic/prestige progress.
 */
public class MuseumGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final MuseumManager museumManager;

    public MuseumGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.museumManager = plugin.getMuseumManager(); // assume wired in main
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, "§6§lMuseum - Donate & Display");
        String islandKey = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment()) != null ?
                plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment()).getId() : "";

        if (museumManager != null) museumManager.loadForIsland(islandKey); // ensure persist loaded

        int tokens = museumManager != null ? museumManager.getTokens(islandKey) : 0;
        java.util.Map<String, Integer> donatedMap = museumManager != null ? museumManager.getDonated(islandKey) : java.util.Collections.emptyMap();
        java.util.Set<String> donated = donatedMap.keySet();

        // Header
        ItemStack header = GUIUtils.createItem(Material.BOOK, "§6§lYour Museum",
                "§7Tokens: §e" + tokens,
                "§7Donated uniques: §a" + donated.size(),
                "§7Donate rare/play items for tokens + small XP (PtW)");
        gui.setItem(4, header);

        // Donate button (donates held item)
        ItemStack donate = GUIUtils.createItem(Material.HOPPER, "§a§lDonate Held Item",
                "§7Place rare item in cursor or inventory and click.",
                "§cPlay-to-Win: only items from progression/trade.");
        gui.setItem(20, donate);

        // Spend shop buttons (token spend for cosmetic, PtW only)
        ItemStack spendCos = GUIUtils.createItem(Material.EMERALD, "§a§lSpend 50 for Cosmetic",
                "§7Cosmetic reward (crate/trail). No power.");
        gui.setItem(22, spendCos);

        ItemStack spendTitle = GUIUtils.createItem(Material.NAME_TAG, "§b§lSpend 100 for Title",
                "§7Cosmetic title unlock.");
        gui.setItem(24, spendTitle);

        // List some donated (simple)
        int slot = 10;
        for (String mat : donated) {
            if (slot > 34) break;
            try {
                Material m = Material.valueOf(mat);
                gui.setItem(slot++, GUIUtils.createItem(m, "§f" + mat, "§7Donated - museum display"));
            } catch (Exception ignored) {}
        }

        // Filler
        ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) if (gui.getItem(i) == null) gui.setItem(i, glass);

        player.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().contains("Museum")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (e.getCurrentItem().getType() == Material.HOPPER) {
            // donate held
            ItemStack held = p.getItemOnCursor();
            if (held != null && held.getType() != Material.AIR && museumManager != null) {
                if (museumManager.donate(p, held)) {
                    p.setItemOnCursor(null);
                    p.sendMessage("§aDonated!");
                    // refresh
                    plugin.getThreadSafety().runOnMainThread(() -> open(p));
                }
            } else {
                p.sendMessage("§cHold a unique item to donate (cursor).");
            }
            return;
        }

        // Task batch: spend shop for tokens -> cosmetic (PtW)
        if (e.getCurrentItem().getType() == Material.EMERALD) {
            if (museumManager != null && museumManager.spendTokens(p, 50, "cosmetic")) {
                plugin.getThreadSafety().runOnMainThread(() -> open(p));
            }
            return;
        }
        if (e.getCurrentItem().getType() == Material.NAME_TAG) {
            if (museumManager != null && museumManager.spendTokens(p, 100, "title")) {
                plugin.getThreadSafety().runOnMainThread(() -> open(p));
            }
            return;
        }
    }
}