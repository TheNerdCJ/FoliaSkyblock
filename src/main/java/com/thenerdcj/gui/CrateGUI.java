package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.crate.CrateManager;
import com.thenerdcj.crate.CrateType;
import com.thenerdcj.island.Island;
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

import java.util.Arrays;

/**
 * CrateGUI - Opening interface for the new crate/key system.
 * Follows exact PDC + autoRegister pattern from IslandShopGUI / PrestigeGUI.
 */
public class CrateGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey CRATE_TYPE_KEY;

    public CrateGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public CrateGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.CRATE_TYPE_KEY = new NamespacedKey(plugin, "crate_type");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, Island island) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6§lCrate Opening");

        // Header
        ItemStack header = new ItemStack(Material.NETHER_STAR);
        ItemMeta hMeta = header.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName("§e§lCrate Rewards");
            hMeta.setLore(Arrays.asList(
                "§7Use keys from the shop or rewards",
                "§7to open crates for big prizes!"
            ));
            header.setItemMeta(hMeta);
        }
        gui.setItem(4, header);

        int slot = 10;
        for (CrateType type : CrateType.values()) {
            ItemStack crate = createCrateItem(type);
            gui.setItem(slot, crate);
            slot += 2;
        }

        // Close
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lClose");
            close.setItemMeta(cMeta);
        }
        gui.setItem(26, close);

        player.openInventory(gui);
    }

    private ItemStack createCrateItem(CrateType type) {
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + type.getDisplayName());
            meta.setLore(Arrays.asList(
                "§7Click to open if you have a key",
                "§7Rarity: §b" + type.name(),
                "",
                "§aLeft-click to open"
            ));

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CRATE_TYPE_KEY, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6§lCrate Opening")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String typeName = meta.getPersistentDataContainer().get(CRATE_TYPE_KEY, PersistentDataType.STRING);
        if (typeName == null) return;

        try {
            CrateType type = CrateType.valueOf(typeName);
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island == null) return;

            // Check for key using CrateManager
            if (plugin.getCrateManager().hasKey(player, type)) {
                plugin.getCrateManager().consumeKey(player, type);
                plugin.getCrateManager().openCrate(player, type);
                player.closeInventory();
            } else {
                player.sendMessage("§cYou need a " + type.getDisplayName() + " Key!");
                player.closeInventory();
            }
        } catch (Exception ignored) {}
    }
}