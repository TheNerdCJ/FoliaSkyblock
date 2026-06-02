package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

/**
 * GUI for Cosmetic Death Messages.
 * Follows pattern of DeathEffectGUI etc.
 */
public class DeathMessageGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final String ACTION_KEY = "death_message_action";

    public DeathMessageGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        DeathMessageManager manager = plugin.getDeathMessageManager();
        if (manager == null) {
            player.sendMessage("§cDeath Messages system unavailable.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "§4§lDeath Messages");

        // Fillers
        ItemStack filler = GUIUtils.createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        DeathMessageCosmetic active = manager.getActiveDeathMessage(player.getUniqueId());
        Set<DeathMessageCosmetic> owned = manager.getOwnedMessages(player.getUniqueId());

        int count = manager.getMessageCollectionCount(player.getUniqueId());
        int total = DeathMessageCosmetic.values().length - 1;

        // Title
        ItemStack title = GUIUtils.createItem(Material.WRITABLE_BOOK, "§4§lCosmetic Death Messages",
                "§7Custom kill/death announcements",
                "§7Collection: §a" + count + " / " + total);
        inv.setItem(4, title);

        // Back to Wardrobe
        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        ItemMeta bm = back.getItemMeta();
        PersistentDataContainer bpdc = bm.getPersistentDataContainer();
        bpdc.set(new org.bukkit.NamespacedKey(plugin, ACTION_KEY), PersistentDataType.STRING, "BACK_WARDROBE");
        back.setItemMeta(bm);
        inv.setItem(0, back);

        // Active display
        ItemStack activeItem = GUIUtils.createItem(Material.NAME_TAG, "§e§lActive: " + (active.isNone() ? "None" : active.getDisplayName()));
        inv.setItem(8, activeItem);

        // None button
        ItemStack noneBtn = GUIUtils.createItem(Material.BARRIER, "§c§lDisable Messages");
        ItemMeta nm = noneBtn.getItemMeta();
        PersistentDataContainer npdc = nm.getPersistentDataContainer();
        npdc.set(new org.bukkit.NamespacedKey(plugin, ACTION_KEY), PersistentDataType.STRING, "NONE");
        noneBtn.setItemMeta(nm);
        inv.setItem(18, noneBtn);

        // List messages
        int slot = 19;
        for (DeathMessageCosmetic msg : DeathMessageCosmetic.values()) {
            if (msg.isNone()) continue;
            if (slot > 44) break;

            boolean has = owned.contains(msg);
            boolean isActive = msg == active;

            ItemStack item = GUIUtils.createItem(
                has ? (isActive ? Material.LIME_DYE : Material.PAPER) : Material.GRAY_DYE,
                (isActive ? "§a§l★ " : has ? "§f" : "§7") + msg.getDisplayName(),
                "§7" + msg.getDescription(),
                "§7Rarity: " + msg.getRarity().getColorCode() + msg.getRarity().getDisplayName(),
                has ? (isActive ? "§aCurrently Active" : "§eClick to activate") : "§cLocked"
            );

            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new org.bukkit.NamespacedKey(plugin, ACTION_KEY), PersistentDataType.STRING, "MSG_" + msg.name());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("§4§lDeath Messages")) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        ItemMeta meta = clicked.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(new org.bukkit.NamespacedKey(plugin, ACTION_KEY), PersistentDataType.STRING);
        if (action == null) return;

        DeathMessageManager manager = plugin.getDeathMessageManager();
        if (manager == null) return;

        if (action.equals("BACK_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        if (action.equals("NONE")) {
            manager.setActiveDeathMessage(player, DeathMessageCosmetic.NONE);
            open(player); // refresh
            return;
        }

        if (action.startsWith("MSG_")) {
            String msgName = action.substring(4);
            try {
                DeathMessageCosmetic msg = DeathMessageCosmetic.valueOf(msgName);
                if (manager.hasMessage(player.getUniqueId(), msg)) {
                    manager.setActiveDeathMessage(player, msg);
                } else {
                    player.sendMessage("§cYou have not unlocked this message.");
                }
            } catch (Exception ignored) {}
            open(player); // refresh
            return;
        }
    }
}