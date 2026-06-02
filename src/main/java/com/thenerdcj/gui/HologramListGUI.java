package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.hologram.Hologram;
import com.thenerdcj.hologram.HologramData;
import com.thenerdcj.hologram.HologramManager;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Simple GUI for managing holograms.
 * Lists all active holograms with info and quick actions.
 * Click to delete or force refresh.
 *
 * Deep modernization pass:
 * - All manual ItemStack + ItemMeta creation (hologram list items, close, refreshAll) converted to GUIUtils.createItem + attachHologramPDC helper.
 * - Title now uses MessageUtil.legacy.
 * - Added PDC (hologram_name) for robust identification in click handler (replacing brittle displayName stripping).
 * - Preserved InventoryHolder pattern, Folia async delete/refresh paths, and all logic.
 */
public class HologramListGUI implements InventoryHolder, Listener {

    private final FoliaSkyblock plugin;
    private final HologramManager hologramManager;
    private Inventory inventory;

    public HologramListGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public HologramListGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.hologramManager = plugin.getHologramManager();
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player) {
        int size = 54; // 6 rows
        inventory = Bukkit.createInventory(this, size, MessageUtil.legacy("§6§lHologram Manager"));

        // Fill with hologram items (modernized creation + PDC)
        int slot = 0;
        for (Hologram holo : hologramManager.getActiveHolograms().values()) {
            if (slot >= size - 9) break; // Leave bottom row for controls

            HologramData data = holo.getData();

            List<String> lore = new java.util.ArrayList<>();
            lore.add("§7World: §f" + data.getWorldName());
            lore.add(String.format("§7Location: §f%.1f, %.1f, %.1f", data.getX(), data.getY(), data.getZ()));
            lore.add("§7Lines: §f" + data.getLines().size());
            lore.add(data.isDynamic() ? "§aDynamic §7(" + data.getDynamicType() + ")" : "§7Static");
            if (data.isDynamic()) {
                lore.add("§7Refresh: §f" + data.getUpdateInterval() + "s");
            }
            lore.add("");
            lore.add("§eLeft Click §7→ Force Refresh");
            lore.add("§cRight Click §7→ Delete");
            lore.add("§7Use /holo commands for editing lines");

            ItemStack item = GUIUtils.createItem(Material.NAME_TAG, "§e" + data.getName(), lore);
            attachHologramPDC(item, data.getName());
            inventory.setItem(slot++, item);
        }

        // Bottom row controls - modernized
        ItemStack close = GUIUtils.createItem(Material.BARRIER, "§cClose");
        inventory.setItem(53, close);

        ItemStack refreshAll = GUIUtils.createItem(Material.CLOCK, "§bRefresh All Dynamic",
                "§7Force refresh every dynamic hologram");
        inventory.setItem(49, refreshAll);

        player.openInventory(inventory);
    }

    private void attachHologramPDC(ItemStack item, String hologramName) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, "hologram_name");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, hologramName);
            item.setItemMeta(meta);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HologramListGUI)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String displayName = meta.getDisplayName();

        // Robust identification via PDC (modernized)
        NamespacedKey holoNameKey = new NamespacedKey(plugin, "hologram_name");
        String holoName = meta.getPersistentDataContainer().get(holoNameKey, PersistentDataType.STRING);
        if (holoName == null) {
            // Fallback
            holoName = displayName.replace("§e", "");
        }

        if (displayName.equals("§cClose")) {
            player.closeInventory();
            return;
        }

        if (displayName.equals("§bRefresh All Dynamic")) {
            for (Hologram h : hologramManager.getActiveHolograms().values()) {
                if (h.getData().isDynamic()) {
                    hologramManager.refreshDynamicContent(h);
                }
            }
            player.sendMessage("§aForced refresh on all dynamic holograms.");
            player.closeInventory();
            return;
        }

        Hologram holo = hologramManager.getHologramByName(holoName);

        if (holo == null) {
            player.sendMessage("§cHologram no longer exists.");
            player.closeInventory();
            return;
        }

        if (event.isLeftClick()) {
            if (holo.getData().isDynamic()) {
                hologramManager.refreshDynamicContent(holo);
                player.sendMessage("§aForced refresh for " + holoName);
            } else {
                player.sendMessage("§7This is a static hologram.");
            }
        } else if (event.isRightClick()) {
            final String nameToDelete = holoName; // effectively final for lambda
            hologramManager.deleteHologram(holo.getData().getId())
                    .thenAccept(success -> {
                        if (success) {
                            player.sendMessage("§cDeleted hologram: " + nameToDelete);
                            // Re-open GUI after delete
                            plugin.getThreadSafety().runOnMainThread(() -> new HologramListGUI(plugin).open(player));
                        }
                    });
        }
    }
}
