package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.hologram.Hologram;
import com.thenerdcj.hologram.HologramData;
import com.thenerdcj.hologram.HologramManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple GUI for managing holograms.
 * Lists all active holograms with info and quick actions.
 * Click to delete or force refresh.
 */
public class HologramListGUI implements InventoryHolder, Listener {

    private final FoliaSkyblock plugin;
    private final HologramManager hologramManager;
    private Inventory inventory;

    public HologramListGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.hologramManager = plugin.getHologramManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        int size = 54; // 6 rows
        inventory = Bukkit.createInventory(this, size, "§6§lHologram Manager");

        // Fill with hologram items
        int slot = 0;
        for (Hologram holo : hologramManager.getActiveHolograms().values()) {
            if (slot >= size - 9) break; // Leave bottom row for controls

            HologramData data = holo.getData();
            ItemStack item = new ItemStack(Material.NAME_TAG);
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName("§e" + data.getName());

            List<String> lore = new ArrayList<>();
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

            meta.setLore(lore);
            item.setItemMeta(meta);

            inventory.setItem(slot++, item);
        }

        // Bottom row controls
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§cClose");
        close.setItemMeta(closeMeta);
        inventory.setItem(53, close);

        ItemStack refreshAll = new ItemStack(Material.CLOCK);
        ItemMeta refreshMeta = refreshAll.getItemMeta();
        refreshMeta.setDisplayName("§bRefresh All Dynamic");
        refreshMeta.setLore(List.of("§7Force refresh every dynamic hologram"));
        refreshAll.setItemMeta(refreshMeta);
        inventory.setItem(49, refreshAll);

        player.openInventory(inventory);
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

        String displayName = clicked.getItemMeta().getDisplayName();

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

        // Find hologram by name from display
        String holoName = displayName.replace("§e", "");
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
            hologramManager.deleteHologram(holo.getData().getId())
                    .thenAccept(success -> {
                        if (success) {
                            player.sendMessage("§cDeleted hologram: " + holoName);
                            // Re-open GUI after delete
                            plugin.getThreadSafety().runOnMainThread(() -> new HologramListGUI(plugin).open(player));
                        }
                    });
        }
    }
}
