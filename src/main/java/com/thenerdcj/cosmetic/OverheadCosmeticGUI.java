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

public class OverheadCosmeticGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public OverheadCosmeticGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "overhead_cosmetic_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        OverheadCosmeticManager manager = plugin.getOverheadCosmeticManager();
        if (manager == null) {
            player.sendMessage("§cOverhead Cosmetics system not available.");
            return;
        }

        String title = "§5§lOverhead Cosmetics";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<OverheadCosmetic> owned = manager.getOwned(player.getUniqueId());
        OverheadCosmetic current = manager.getActive(player.getUniqueId());

        inv.setItem(4, GUIUtils.createItem(Material.END_ROD, "§5§lAdvanced Overhead Cosmetics",
                "§7Floating visual effects above your head",
                "§7Includes titles (Runic, Void, Celestial, Slayer Sigil + more), halos, auras, crowns",
                "§7Collection: §a" + owned.size() + " / " + (OverheadCosmetic.values().length - 1)));

        int slot = 19;
        for (OverheadCosmetic c : OverheadCosmetic.values()) {
            if (slot > 44) break;
            if (c.isNone()) continue;

            boolean has = owned.contains(c);
            boolean isActive = c == current;

            ItemStack item = GUIUtils.createItem(Material.END_ROD,
                    (isActive ? "§a§l★ " : has ? "§e" : "§7") + c.getRarity().getColorCode() + c.getDisplayName(),
                    "§7" + c.getDescription(),
                    has ? (isActive ? "§aCurrently Active" : "§eClick to equip") : "§cLocked - Unlock via prestige or Slayer Shop");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OVERHEAD_" + c.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack none = GUIUtils.createItem(Material.BARRIER, "§cRemove Effect");
        ItemMeta noneMeta = none.getItemMeta();
        if (noneMeta != null) {
            noneMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OVERHEAD_NONE");
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
        if (!event.getView().getTitle().equals("§5§lOverhead Cosmetics")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        OverheadCosmeticManager manager = plugin.getOverheadCosmeticManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        if (action.equals("OVERHEAD_NONE")) {
            manager.setActive(player, OverheadCosmetic.NONE);
            open(player);
            return;
        }

        if (action.startsWith("OVERHEAD_")) {
            try {
                OverheadCosmetic c = OverheadCosmetic.valueOf(action.substring(9));
                if (!manager.has(player.getUniqueId(), c) && !c.isNone()) {
                    player.sendMessage("§cYou have not unlocked this overhead cosmetic.");
                    return;
                }
                manager.setActive(player, c);
                open(player);
            } catch (Exception ignored) {}
        }
    }
}
