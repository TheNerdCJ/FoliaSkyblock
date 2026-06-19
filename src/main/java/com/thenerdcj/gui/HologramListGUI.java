package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.hologram.Hologram;
import com.thenerdcj.hologram.HologramData;
import com.thenerdcj.hologram.HologramManager;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
 * GUI for managing holograms.
 * Modernized with PDC, GUIUtils, Folia-safe actions (teleportAsync).
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
        int size = 54;
        inventory = Bukkit.createInventory(this, size, MessageUtil.legacy("§6§lHologram Manager"));

        int slot = 0;
        for (Hologram holo : hologramManager.getActiveHolograms().values()) {
            if (slot >= size - 9) break;

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
            lore.add("§aLeft Click §7→ Edit lines (GUI)");
            lore.add("§eShift+Left §7→ Teleport to hologram");
            lore.add("§cRight Click §7→ Delete");
            if (data.isDynamic()) {
                lore.add("§bShift+Right §7→ Force refresh (dynamic)");
            }
            lore.add("§7/holo movehere <name> to move");

            ItemStack item = GUIUtils.createItem(Material.NAME_TAG, "§e" + data.getName(), lore);
            attachHologramPDC(item, data.getName());
            inventory.setItem(slot++, item);
        }

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

        NamespacedKey holoNameKey = new NamespacedKey(plugin, "hologram_name");
        String holoName = meta.getPersistentDataContainer().get(holoNameKey, PersistentDataType.STRING);
        if (holoName == null) {
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

        HologramData data = holo.getData();

        if (event.isLeftClick()) {
            if (event.isShiftClick()) {
                // Shift+Left = Teleport (Folia async)
                World w = Bukkit.getWorld(data.getWorldName());
                if (w != null) {
                    Location tpLoc = new Location(w, data.getX(), data.getY() + 0.5, data.getZ());
                    final String tpName = holoName;
                    player.teleportAsync(tpLoc).thenAccept(success -> {
                        if (success) {
                            player.sendMessage("§aTeleported to hologram §e" + tpName);
                        } else {
                            player.sendMessage("§cTeleport failed.");
                        }
                    });
                } else {
                    player.sendMessage("§cHologram world '" + data.getWorldName() + "' is not loaded.");
                }
            } else {
                // Primary left click: open the dedicated line editor GUI (two-column)
                plugin.getHologramEditorGUI().open(player, holoName);
            }
        } else if (event.isRightClick()) {
            if (event.isShiftClick() && data.isDynamic()) {
                hologramManager.refreshDynamicContent(holo);
                player.sendMessage("§aForced refresh for dynamic hologram " + holoName);
            } else {
                final String nameToDelete = holoName;
                hologramManager.deleteHologram(data.getId())
                        .thenAccept(success -> {
                            if (success) {
                                player.sendMessage("§cDeleted hologram: " + nameToDelete);
                                plugin.getThreadSafety().runOnMainThread(() -> new HologramListGUI(plugin).open(player));
                            }
                        });
            }
        }
    }
}