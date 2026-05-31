package com.thenerdcj.wardrobe;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small options menu shown when right-clicking a wardrobe slot.
 * Supports Rename (via chat) and Clear/Delete.
 */
public class WardrobeSlotOptionsGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final WardrobeManager wardrobeManager;
    private final NamespacedKey ACTION_KEY;

    // Temporary storage for players currently renaming a set: UUID -> (slot, type, originalView)
    private final Map<UUID, RenameContext> pendingRenames = new ConcurrentHashMap<>();

    public WardrobeSlotOptionsGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public WardrobeSlotOptionsGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.wardrobeManager = plugin.getWardrobeManager();
        this.ACTION_KEY = new NamespacedKey(plugin, "wardrobe_option");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void openOptions(Player player, int slot, boolean isArmor, String originalView) {
        String title = "§6§lWardrobe Options §8- Slot " + (slot + 1);
        Inventory gui = Bukkit.createInventory(null, 27, title);

        // Fill background
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName("§8 ");
        filler.setItemMeta(fm);
        for (int i = 0; i < 27; i++) gui.setItem(i, filler);

        // Current set info
        WardrobeSet currentSet = isArmor 
            ? wardrobeManager.getArmorSet(player.getUniqueId(), slot)
            : wardrobeManager.getEquipmentSet(player.getUniqueId(), slot);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e§lCurrent: " + (currentSet != null ? currentSet.getName() : "Empty"));
        infoMeta.setLore(Arrays.asList(
            "§7Slot: §f" + (slot + 1),
            "§7Type: §f" + (isArmor ? "Armor" : "Equipment")
        ));
        info.setItemMeta(infoMeta);
        gui.setItem(13, info);

        // Rename button
        ItemStack rename = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = rename.getItemMeta();
        renameMeta.setDisplayName("§a§lRename");
        renameMeta.setLore(Arrays.asList("§7Change the name of this loadout"));
        PersistentDataContainer pdc = renameMeta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "RENAME_" + slot + "_" + (isArmor ? "ARMOR" : "EQUIP") + "_" + originalView);
        rename.setItemMeta(renameMeta);
        gui.setItem(10, rename);

        // Clear button
        ItemStack clear = new ItemStack(Material.BARRIER);
        ItemMeta clearMeta = clear.getItemMeta();
        clearMeta.setDisplayName("§c§lClear / Delete");
        clearMeta.setLore(Arrays.asList("§7Remove this loadout permanently"));
        pdc = clearMeta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "CLEAR_" + slot + "_" + (isArmor ? "ARMOR" : "EQUIP") + "_" + originalView);
        clear.setItemMeta(clearMeta);
        gui.setItem(16, clear);

        // Cancel
        ItemStack cancel = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§7§lCancel");
        cancel.setItemMeta(cancelMeta);
        gui.setItem(22, cancel);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("§6§lWardrobe Options")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        if (action.startsWith("RENAME_")) {
            handleRenameClick(player, action);
        } else if (action.startsWith("CLEAR_")) {
            handleClearClick(player, action);
        } else if (action.equals("CANCEL")) {
            player.closeInventory();
        }
    }

    private void handleRenameClick(Player player, String actionData) {
        // Parse: RENAME_{slot}_{type}_{view}
        String[] parts = actionData.split("_");
        int slot = Integer.parseInt(parts[1]);
        String type = parts[2];
        String originalView = parts[3];

        player.closeInventory();
        player.sendMessage("§eEnter a new name for this loadout in chat (or type 'cancel'):");

        pendingRenames.put(player.getUniqueId(), new RenameContext(slot, type, originalView));
    }

    private void handleClearClick(Player player, String actionData) {
        String[] parts = actionData.split("_");
        int slot = Integer.parseInt(parts[1]);
        String type = parts[2];
        String originalView = parts[3];

        UUID uuid = player.getUniqueId();

        if ("ARMOR".equals(type)) {
            wardrobeManager.clearArmorSet(uuid, slot);
        } else {
            wardrobeManager.clearEquipmentSet(uuid, slot);
        }

        player.closeInventory();
        player.sendMessage("§aLoadout in slot " + (slot + 1) + " has been cleared.");

        // Re-open main wardrobe
        boolean isArmor = "ARMOR".equals(type);
        plugin.getWardrobeGUI().openWardrobe(player, isArmor ? WardrobeGUI.View.ARMOR : WardrobeGUI.View.EQUIPMENT);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        RenameContext context = pendingRenames.remove(uuid);
        if (context == null) return;

        event.setCancelled(true);

        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7Rename cancelled.");
            // Re-open main GUI on main thread
            plugin.getThreadSafety().runOnMainThread(() -> {
                boolean isArmor = "ARMOR".equals(context.type);
                plugin.getWardrobeGUI().openWardrobe(player, isArmor ? WardrobeGUI.View.ARMOR : WardrobeGUI.View.EQUIPMENT);
            });
            return;
        }

        // Rename on main thread
        plugin.getThreadSafety().runOnMainThread(() -> {
            wardrobeManager.renameSet(uuid, context.slot, context.type, message);

            player.sendMessage("§aLoadout renamed to: §e" + message);

            boolean isArmor = "ARMOR".equals(context.type);
            plugin.getWardrobeGUI().openWardrobe(player, isArmor ? WardrobeGUI.View.ARMOR : WardrobeGUI.View.EQUIPMENT);
        });
    }

    private static class RenameContext {
        final int slot;
        final String type;
        final String originalView;

        RenameContext(int slot, String type, String originalView) {
            this.slot = slot;
            this.type = type;
            this.originalView = originalView;
        }
    }
}