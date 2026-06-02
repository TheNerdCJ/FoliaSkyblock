package com.thenerdcj.wings;

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
 * Dedicated GUI for managing Elytra Wing cosmetics.
 * Opened via /wings.
 * Follows the same patterns as PetGUI and TagGUI.
 */
public class WingGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public WingGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "wing_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§b§lElytra Wing Cosmetics");

        ElytraWingManager manager = plugin.getElytraWingManager();
        if (manager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cWings system unavailable"));
            player.openInventory(inv);
            return;
        }

        Set<ElytraWing> owned = manager.getOwnedWings(player.getUniqueId());
        ElytraWing active = manager.getActiveWing(player.getUniqueId());

        // Header
        inv.setItem(4, GUIUtils.createItem(Material.ELYTRA, "§b§lYour Elytra Wings",
                "§7Special visual effects while gliding",
                "§7Collection: §a" + manager.getWingCollectionCount(player.getUniqueId()) + " §7styles"));

        if (owned.isEmpty()) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo wing styles unlocked yet",
                    "§7Earn them through prestige and slayer progression."));
            player.openInventory(inv);
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

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "SELECT_" + wing.name());

            item.setItemMeta(item.getItemMeta());
            inv.setItem(slot++, item);
        }

        // Claim item button
        ItemStack claim = GUIUtils.createItem(Material.ELYTRA, "§6§lClaim Cosmetic Elytra",
                "§7Gives you a special elytra item",
                "§7with the model of your active wings",
                "§8(Requires resource pack for visuals)");
        PersistentDataContainer claimPdc = claim.getItemMeta().getPersistentDataContainer();
        claimPdc.set(ACTION_KEY, PersistentDataType.STRING, "CLAIM_ITEM");
        claim.setItemMeta(claim.getItemMeta());
        inv.setItem(49, claim);

        // Back to Wardrobe
        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        PersistentDataContainer backPdc = back.getItemMeta().getPersistentDataContainer();
        backPdc.set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
        back.setItemMeta(back.getItemMeta());
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§b§lElytra Wing Cosmetics")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        ElytraWingManager manager = plugin.getElytraWingManager();
        if (manager == null) return;

        if (action.equals("CLAIM_ITEM")) {
            ElytraWing active = manager.getActiveWing(player.getUniqueId());
            if (!active.isNone()) {
                ItemStack cosmetic = manager.getCosmeticElytraItem(active);
                player.getInventory().addItem(cosmetic);
                player.sendMessage("§aReceived cosmetic elytra for " + active.getDisplayName());
            } else {
                player.sendMessage("§cYou need to equip a wing style first.");
            }
            open(player);
            return;
        }

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player, com.thenerdcj.wardrobe.WardrobeGUI.View.WINGS);
            return;
        }

        if (action.startsWith("SELECT_")) {
            String wingName = action.substring(7);
            try {
                ElytraWing wing = ElytraWing.valueOf(wingName);

                if (event.isRightClick()) {
                    // Right click = claim the item for this wing
                    ItemStack cosmetic = manager.getCosmeticElytraItem(wing);
                    player.getInventory().addItem(cosmetic);
                    player.sendMessage("§aReceived cosmetic elytra for " + wing.getDisplayName());
                } else {
                    manager.setActiveWing(player, wing);
                }
                open(player);
            } catch (Exception ignored) {}
        }
    }
}
