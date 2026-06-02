package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.crate.CrateManager;
import com.thenerdcj.crate.CrateType;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * CrateGUI - Opening interface for the new crate/key system.
 *
 * Deep modernization (final pass):
 * - Last manual ItemStack + meta block in createCrateItem converted to GUIUtils.createItem + attachCratePDC helper.
 * - Title and other buttons already using MessageUtil.legacy + GUIUtils (preserved).
 * - Click handler already using resilient startsWith + PDC routing.
 * - Full consistency with the broader GUI migration effort.
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
        // Use MessageUtil.legacy for modern title handling (consistent with recent migrations)
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 27, 
            MessageUtil.legacy("§6§lCrate Opening"));

        // Header using GUIUtils (same package)
        gui.setItem(4, GUIUtils.createItem(Material.NETHER_STAR, "§e§lCrate Rewards",
                "§7Use keys from the shop or rewards",
                "§7to open crates for big prizes!"));

        int slot = 10;
        for (CrateType type : CrateType.values()) {
            ItemStack crate = createCrateItem(type);
            gui.setItem(slot, crate);
            slot += 2;
        }

        // Close button using GUIUtils
        gui.setItem(26, GUIUtils.createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));

        player.openInventory(gui);
    }

    private ItemStack createCrateItem(CrateType type) {
        // Modernized base creation
        ItemStack item = GUIUtils.createItem(type.getIcon(), "§e§l" + type.getDisplayName(),
                "§7Click to open if you have a key",
                "§7Rarity: §b" + type.name(),
                "",
                "§aLeft-click to open");

        attachCratePDC(item, type);
        return item;
    }

    private void attachCratePDC(ItemStack item, CrateType type) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(CRATE_TYPE_KEY, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Use startsWith for slightly more robust title matching (modern pattern)
        if (!event.getView().getTitle().startsWith("§6§lCrate")) return;
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

            if (plugin.getCrateManager().hasKey(player, type)) {
                plugin.getCrateManager().consumeKey(player, type);
                plugin.getCrateManager().openCrate(player, type);
                player.closeInventory();
            } else {
                SoundUtil.error(player);
                player.sendMessage("§cYou need a " + type.getDisplayName() + " Key!");
                player.closeInventory();
            }
        } catch (Exception ignored) {}
    }
}