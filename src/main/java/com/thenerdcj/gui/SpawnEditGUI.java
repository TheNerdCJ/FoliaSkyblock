package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.MessageUtil;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Dedicated SpawnEditGUI for admins (task batch polish).
 * Allows setting a target's island spawn to admin's current location (fix for 0,0, grief, etc.).
 * Accessed via /isadmin spawngui <player> or from inspect.
 * Folia: set on main thread.
 * Security: foliasb.admin only.
 * PtW/design: admin tool only, no player power.
 */
public class SpawnEditGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey targetKey;

    public SpawnEditGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.targetKey = new NamespacedKey(plugin, "spawnedit_target");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player staff, UUID targetOwner) {
        if (!staff.hasPermission("foliasb.admin")) {
            staff.sendMessage("§cNo permission.");
            return;
        }
        Island island = plugin.getIslandManager().getIsland(targetOwner, staff.getWorld().getEnvironment());
        if (island == null) {
            staff.sendMessage("§cNo island for target in this dim.");
            return;
        }
        String targetName = plugin.getNameCache().getName(targetOwner);

        Inventory gui = Bukkit.createInventory(null, 27, "§c§lSpawn Edit - " + targetName);
        ItemStack info = GUIUtils.createItem(Material.COMPASS, "§bCurrent Spawn Info",
                "§7Owner: §e" + targetName,
                "§7Dim: §e" + island.getDimension().name(),
                "§7Click below to set to YOUR location (admin fix).");
        gui.setItem(13, info);

        ItemStack setBtn = GUIUtils.createItem(Material.EMERALD_BLOCK, "§a§lSET SPAWN TO HERE",
                "§7Sets the island spawn to your current position.",
                "§cAdmin only tool.");
        ItemMeta meta = setBtn.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, targetOwner.toString());
            setBtn.setItemMeta(meta);
        }
        gui.setItem(22, setBtn);

        ItemStack cancel = GUIUtils.createItem(Material.BARRIER, "§cCancel");
        gui.setItem(26, cancel);

        staff.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player staff)) return;
        if (!e.getView().getTitle().contains("Spawn Edit")) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (clicked.getType() == Material.BARRIER) {
            staff.closeInventory();
            return;
        }

        if (clicked.getType() == Material.EMERALD_BLOCK) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;
            String targetStr = meta.getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
            if (targetStr == null) return;
            UUID target = UUID.fromString(targetStr);
            Island island = plugin.getIslandManager().getIsland(target, staff.getWorld().getEnvironment());
            if (island != null && staff.hasPermission("foliasb.admin")) {
                island.setSpawnLocation(staff.getLocation());
                MessageUtil.sendMessage(staff, "§aSpawn set for target island to your location.");
                staff.closeInventory();
                // Optional: re-open inspect if had
            }
        }
    }
}