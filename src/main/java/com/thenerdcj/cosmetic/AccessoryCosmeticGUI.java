package com.thenerdcj.cosmetic;

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

public class AccessoryCosmeticGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public AccessoryCosmeticGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "accessory_cosmetic_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        AccessoryCosmeticManager manager = plugin.getAccessoryCosmeticManager();
        if (manager == null) {
            player.sendMessage("§cAccessories system not available.");
            return;
        }

        String title = "§6§lAccessories";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<AccessoryCosmetic> owned = manager.getOwned(player.getUniqueId());
        AccessoryCosmetic current = manager.getActive(player.getUniqueId());
        int total = AccessoryCosmetic.values().length - 1;

        inv.setItem(4, GUIUtils.createItem(Material.NETHER_STAR, "§6§lCosmetic Accessories",
                "§7Small floating visual items around you",
                "§7Collection: §a" + owned.size() + " / " + total));

        int slot = 19;
        for (AccessoryCosmetic a : AccessoryCosmetic.values()) {
            if (slot > 44) break;
            if (a.isNone()) continue;

            boolean has = owned.contains(a);
            boolean isActive = a == current;

            ItemStack item = GUIUtils.createItem(Material.NETHER_STAR,
                    (isActive ? "§a§l★ " : has ? "§e" : "§7") + a.getRarity().getColorCode() + a.getDisplayName(),
                    "§7" + a.getDescription(),
                    has ? (isActive ? "§aCurrently Active" : "§eClick to equip") : "§cLocked - Unlock via prestige or Slayer Shop");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "ACCESSORY_" + a.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack none = GUIUtils.createItem(Material.BARRIER, "§cRemove Accessory");
        ItemMeta noneMeta = none.getItemMeta();
        if (noneMeta != null) {
            noneMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "ACCESSORY_NONE");
            none.setItemMeta(noneMeta);
        }
        inv.setItem(18, none);

        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6§lAccessories")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        AccessoryCosmeticManager manager = plugin.getAccessoryCosmeticManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        if (action.equals("ACCESSORY_NONE")) {
            manager.setActive(player, AccessoryCosmetic.NONE);
            open(player);
            return;
        }

        if (action.startsWith("ACCESSORY_")) {
            try {
                AccessoryCosmetic a = AccessoryCosmetic.valueOf(action.substring(10));
                if (!manager.has(player.getUniqueId(), a) && !a.isNone()) {
                    player.sendMessage("§cYou have not unlocked this accessory.");
                    return;
                }
                manager.setActive(player, a);
                open(player);
            } catch (Exception ignored) {}
        }
    }
}
