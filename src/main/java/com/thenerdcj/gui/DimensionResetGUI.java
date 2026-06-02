package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * GUI for choosing which dimension to reset (part of per-dimension island reset feature).
 *
 * Deep modernization pass:
 * - All manual ItemStack + ItemMeta boilerplate replaced with GUIUtils.createItem (including filler glass).
 * - Title routed through MessageUtil.legacy for proper Adventure Component handling.
 * - Resilient title check in click handler (startsWith).
 * - PDC consistency: dimension selection buttons now carry NamespacedKey payload (matches ResetConfirmationGUI pattern).
 * - Preserved all Folia-safe async flows and per-dimension safety logic.
 */
public class DimensionResetGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final String GUI_TITLE = "§c§lReset Which Dimension?";

    public DimensionResetGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, MessageUtil.legacy(GUI_TITLE));

        NamespacedKey dimKey = new NamespacedKey(plugin, "target_dimension");

        // Overworld
        ItemStack overworld = createDimensionItem(
                Material.GRASS_BLOCK, "§a§lOverworld Island", World.Environment.NORMAL, player);
        attachDimensionPDC(overworld, dimKey, World.Environment.NORMAL);
        gui.setItem(11, overworld);

        // Nether
        ItemStack nether = createDimensionItem(
                Material.NETHERRACK, "§c§lNether Island", World.Environment.NETHER, player);
        attachDimensionPDC(nether, dimKey, World.Environment.NETHER);
        gui.setItem(13, nether);

        // End
        ItemStack end = createDimensionItem(
                Material.END_STONE, "§5§lEnd Island", World.Environment.THE_END, player);
        attachDimensionPDC(end, dimKey, World.Environment.THE_END);
        gui.setItem(15, end);

        // Info panel using GUIUtils for consistency
        gui.setItem(4, GUIUtils.createItem(Material.PAPER, "§e§lImportant Information",
                "§7Resetting a dimension §lonly§r§7 affects that dimension.",
                "§7Your main island and other dimensions stay completely safe.",
                "§cThis action cannot be undone!"));

        // Cancel button
        gui.setItem(22, GUIUtils.createItem(Material.REDSTONE_BLOCK, "§c§lCANCEL", "§7Click to go back"));

        // Fill empty slots with glass - modernized via GUIUtils (matches ResetConfirmationGUI)
        ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, glass);
        }

        player.openInventory(gui);
    }

    private void attachDimensionPDC(ItemStack item, NamespacedKey key, World.Environment dimension) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, dimension.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createDimensionItem(Material material, String displayName,
                                          World.Environment dimension, Player player) {
        long cooldown = plugin.getIslandManager().getDimensionResetCooldownRemainingHours(player, dimension);
        boolean onCooldown = cooldown > 0;

        return GUIUtils.createItem(material, displayName,
                "§7Reset your island in the §e" + dimension.name() + "§7 dimension only.",
                "",
                onCooldown
                        ? "§cOn Cooldown: §e" + cooldown + " hours remaining"
                        : "§aReady to reset",
                "§7Click to continue to confirmation.",
                "",
                "§cWARNING: All progress, builds and items in this dimension will be lost!");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Resilient title check (matches modernized ResetConfirmationGUI pattern)
        if (!event.getView().getTitle().startsWith("§c§lReset Which Dimension")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (clicked.getType() == Material.REDSTONE_BLOCK) {
            player.closeInventory();
            return;
        }

        // Prefer PDC payload for robustness (PDC consistency with ResetConfirmationGUI)
        World.Environment targetDim = null;

        ItemMeta meta = clicked.getItemMeta();
        if (meta != null) {
            String dimStr = meta.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "target_dimension"), PersistentDataType.STRING);
            if (dimStr != null) {
                try {
                    targetDim = World.Environment.valueOf(dimStr);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // Fallback to material detection (defensive)
        if (targetDim == null) {
            if (clicked.getType() == Material.GRASS_BLOCK) {
                targetDim = World.Environment.NORMAL;
            } else if (clicked.getType() == Material.NETHERRACK) {
                targetDim = World.Environment.NETHER;
            } else if (clicked.getType() == Material.END_STONE) {
                targetDim = World.Environment.THE_END;
            }
        }

        if (targetDim != null) {
            player.closeInventory();
            // Open the existing confirmation GUI with the chosen dimension
            plugin.getResetConfirmationGUI().open(player, targetDim);
        }
    }
}