package com.thenerdcj.wardrobe;

import com.thenerdcj.FoliaSkyblock;
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
import java.util.Map;

/**
 * Interactive Wardrobe GUI.
 *
 * Supports Armor and Equipment presets (addressing the top community request).
 * Follows the exact patterns used by MinionsGUI, IslandUpgradeGUI, etc.
 */
public class WardrobeGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final WardrobeManager wardrobeManager;
    private final NamespacedKey ACTION_KEY;

    // View modes
    enum View { ARMOR, EQUIPMENT }

    public WardrobeGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public WardrobeGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.wardrobeManager = plugin.getWardrobeManager();
        this.ACTION_KEY = new NamespacedKey(plugin, "wardrobe_action");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void openWardrobe(Player player) {
        openWardrobe(player, View.ARMOR);
    }

    public void openWardrobe(Player player, View view) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lWardrobe §8- " + (view == View.ARMOR ? "§bArmor" : "§dEquipment"));

        // Fillers (consistent with project style)
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName("§8 ");
        filler.setItemMeta(fm);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Title item
        ItemStack title = new ItemStack(Material.ARMOR_STAND);
        ItemMeta tm = title.getItemMeta();
        tm.setDisplayName("§6§lWardrobe");
        tm.setLore(Arrays.asList(
            "§7Save and quickly swap gear loadouts",
            "§7Supports both §bArmor §7and §dEquipment",
            "",
            "§eLeft-click §7a set to equip",
            "§7Shift+Left §7= Save current gear to slot",
            "§7Right-click §7= More options",
            "",
            "§8Collect unique equipment for Island XP"
        ));
        title.setItemMeta(tm);
        inv.setItem(4, title);

        // Tab buttons
        ItemStack armorTab = createTabItem("§b§lArmor Sets", view == View.ARMOR, "VIEW_ARMOR");
        ItemStack equipTab = createTabItem("§d§lEquipment Sets", view == View.EQUIPMENT, "VIEW_EQUIPMENT");
        inv.setItem(1, armorTab);
        inv.setItem(2, equipTab);

        // Render the 9 slots
        Map<Integer, WardrobeSet> presets = (view == View.ARMOR)
                ? wardrobeManager.getArmorPresets(player.getUniqueId())
                : wardrobeManager.getEquipmentPresets(player.getUniqueId());

        int[] slotPositions = {19, 20, 21, 22, 23, 24, 25, 28, 29}; // nice 3x3-ish layout

        int maxSlots = wardrobeManager.getMaxSlots(player);

        for (int i = 0; i < WardrobeManager.DEFAULT_MAX_SLOTS; i++) {
            int pos = slotPositions[i];
            WardrobeSet set = presets.get(i);
            boolean unlocked = i < maxSlots;

            ItemStack item;
            if (!unlocked) {
                item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§c§lLocked Slot " + (i + 1));
                meta.setLore(Arrays.asList(
                    "§7Unlock more wardrobe slots",
                    "§7by upgrading §6Wardrobe Slots §7on your island."
                ));
                item.setItemMeta(meta);
            } else if (set != null && !set.isEmpty()) {
                item = set.createDisplayItem();
            } else {
                item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§7§lEmpty Slot " + (i + 1));
                meta.setLore(Arrays.asList(
                    "§7Shift+Left-click §7while wearing gear",
                    "§7to save this loadout."
                ));
                item.setItemMeta(meta);
            }

            if (unlocked) {
                // Store action data only for unlocked slots
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                pdc.set(ACTION_KEY, PersistentDataType.STRING, (view == View.ARMOR ? "ARMOR_" : "EQUIP_") + i);
                item.setItemMeta(item.getItemMeta());
            }

            inv.setItem(pos, item);
        }

        // Bottom info — now includes collection progress for better visibility of the light XP system
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§e§lWardrobe Info");

        int collectionCount = wardrobeManager.getEquipmentCollectionCount(player.getUniqueId());

        im.setLore(Arrays.asList(
            "§7Saved sets: §a" + presets.size(),
            "§7Max slots: §a" + maxSlots + " §7(upgrade via /is upgrade)",
            "",
            "§7Equipment Collection: §a" + collectionCount + " §7unique pieces",
            "§8Save new equipment types to earn Island XP"
        ));
        info.setItemMeta(im);
        inv.setItem(49, info);

        // Dedicated Collection display item (small polish feature)
        ItemStack collectionItem = new ItemStack(Material.CHEST);
        ItemMeta cm = collectionItem.getItemMeta();
        cm.setDisplayName("§6§lEquipment Collection");
        cm.setLore(Arrays.asList(
            "§7Unique pieces collected: §a" + collectionCount,
            "§7Save new equipment materials in any slot",
            "§7to grow your collection and earn Island XP.",
            "",
            "§eUse §6/wardrobe collection §eto view details"
        ));
        collectionItem.setItemMeta(cm);
        inv.setItem(47, collectionItem);

        player.openInventory(inv);
    }

    private ItemStack createTabItem(String name, boolean selected, String action) {
        ItemStack item = new ItemStack(selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTitle().startsWith("§6§lWardrobe")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;

            PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
            String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
            if (action == null) return;

            boolean isShift = event.isShiftClick();
            boolean isRight = event.isRightClick();

            if (action.equals("VIEW_ARMOR")) {
                openWardrobe(player, View.ARMOR);
                return;
            }
            if (action.equals("VIEW_EQUIPMENT")) {
                openWardrobe(player, View.EQUIPMENT);
                return;
            }

            // Slot actions: ARMOR_3 or EQUIP_5
            if (action.startsWith("ARMOR_") || action.startsWith("EQUIP_")) {
                boolean isArmor = action.startsWith("ARMOR_");
                int slot = Integer.parseInt(action.substring(action.indexOf('_') + 1));

                if (isShift) {
                    // Save current gear
                    String defaultName = (isArmor ? "Armor Set " : "Equipment Set ") + (slot + 1);
                    if (isArmor) {
                        wardrobeManager.saveCurrentArmor(player, slot, defaultName);
                    } else {
                        wardrobeManager.saveCurrentEquipment(player, slot, defaultName);
                    }
                    // Refresh
                    openWardrobe(player, isArmor ? View.ARMOR : View.EQUIPMENT);
                } else if (isRight) {
                    // Open options menu (rename / clear)
                    plugin.getWardrobeSlotOptionsGUI().openOptions(player, slot, isArmor, isArmor ? "ARMOR" : "EQUIP");
                } else {
                    // Equip
                    int armorSlot = isArmor ? slot : -1;
                    int equipSlot = isArmor ? -1 : slot;

                    // If in armor view, equip the armor slot + last used equipment (or 0)
                    // For simplicity in v1: equip the chosen category + default 0 in the other
                    if (isArmor) {
                        wardrobeManager.equipSet(player, slot, 0);
                    } else {
                        wardrobeManager.equipSet(player, 0, slot);
                    }
                    player.closeInventory();
                }
            }
        }
    }
}