package com.thenerdcj.tags;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.GUIUtils;
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

import java.util.List;

/**
 * Dedicated GUI for managing cosmetic Player Tags.
 * Opened via /tags or from Wardrobe Tags tab.
 * Follows the exact same patterns as PetGUI.
 */
public class TagGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public TagGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "tag_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§5§lCosmetic Tags");

        var tagManager = plugin.getPlayerTagManager();
        if (tagManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cTags system unavailable"));
            player.openInventory(inv);
            return;
        }

        java.util.Set<com.thenerdcj.tags.TagInstance> owned = tagManager.getOwnedTags(player.getUniqueId());
        com.thenerdcj.tags.PlayerTag active = tagManager.getActiveTag(player.getUniqueId());

        // Header
        int collCount = tagManager.getTagCollectionCount(player.getUniqueId());
        inv.setItem(4, GUIUtils.createItem(Material.NAME_TAG, "§5§lYour Cosmetic Tags",
                "§7Appear in chat and tab list",
                "§7Collection: §a" + collCount + " §7unique tags"));

        if (owned.isEmpty() || (owned.size() == 1 && owned.contains(PlayerTag.NONE))) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo tags owned yet",
                    "§7Earn tags through prestige, slayer tokens,",
                    "§7and collection milestones."));
            player.openInventory(inv);
            return;
        }

        int slot = 18;
        for (com.thenerdcj.tags.TagInstance inst : owned) {
            com.thenerdcj.tags.PlayerTag tag = inst.getType();
            if (slot > 44) break;
            if (tag.isNone()) continue;

            boolean isActive = tag == active;

            ItemStack item = GUIUtils.createItem(Material.NAME_TAG,
                    (isActive ? "§a§l★ " : "§e") + tag.getTagText() + " §f" + tag.getDisplayName(),
                    "§7Rarity: " + tag.getRarity().getColorCode() + tag.getRarity().getDisplayName(),
                    "§7" + tag.getDescription(),
                    "",
                    isActive ? "§a§lCurrently Active" : "§eLeft-click §7to equip",
                    "§7Right-click §7for info");

            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "SELECT_" + tag.name());
            item.setItemMeta(meta);

            inv.setItem(slot++, item);
        }

        // Remove active button
        inv.setItem(49, GUIUtils.createItem(Material.BARRIER, "§c§lRemove Active Tag", "§7Click to clear your cosmetic tag"));

        // Back to Wardrobe
        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        PersistentDataContainer backPdc = back.getItemMeta().getPersistentDataContainer();
        backPdc.set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
        back.setItemMeta(back.getItemMeta());
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§5§lCosmetic Tags")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        var tagManager = plugin.getPlayerTagManager();
        if (tagManager == null) return;

        if (action.equals("REMOVE_ACTIVE")) {
            tagManager.setActiveTag(player, PlayerTag.NONE);
            open(player);
            return;
        }

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player, com.thenerdcj.wardrobe.WardrobeGUI.View.TAGS);
            return;
        }

        if (action.startsWith("SELECT_")) {
            String tagName = action.substring(7);
            try {
                PlayerTag tag = PlayerTag.valueOf(tagName);

                if (event.isRightClick()) {
                    // Show info
                    player.sendMessage("§e" + tag.getTagText() + " §f" + tag.getDisplayName());
                    player.sendMessage("§7" + tag.getDescription());
                    player.sendMessage("§7Rarity: " + tag.getRarity().getColorCode() + tag.getRarity().getDisplayName());
                } else {
                    tagManager.setActiveTag(player, tag);
                    open(player);
                }
            } catch (Exception ignored) {}
        }
    }
}
