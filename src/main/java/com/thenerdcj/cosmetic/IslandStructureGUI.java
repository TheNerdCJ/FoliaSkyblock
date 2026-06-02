package com.thenerdcj.cosmetic;

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

import java.util.Set;

/**
 * Basic catalog GUI for unlocked Island Structures.
 */
public class IslandStructureGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public IslandStructureGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "island_structure_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        IslandStructureManager manager = plugin.getIslandStructureManager();
        if (manager == null) {
            player.sendMessage("§cIsland Structures system not available yet.");
            return;
        }

        String title = "§6§lIsland Structures Catalog";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<IslandStructureCosmetic> owned = manager.getOwnedStructures(player.getUniqueId());
        int total = IslandStructureCosmetic.values().length - 1;

        inv.setItem(4, GUIUtils.createItem(Material.BRICKS, "§6§lIsland Structures",
                "§7Larger decorative clusters for your island",
                "§7Collection: §a" + owned.size() + " / " + total));

        int slot = 19;
        for (IslandStructureCosmetic s : IslandStructureCosmetic.values()) {
            if (slot > 44) break;
            if (s.isNone()) continue;

            boolean has = owned.contains(s);

            ItemStack item = GUIUtils.createItem(Material.BRICKS,
                    (has ? "§e" : "§7") + s.getRarity().getColorCode() + s.getDisplayName(),
                    "§7" + s.getDescription(),
                    "§7Category: " + s.getCategory().getDisplayName(),
                    has ? "§aOwned §7- §eClick: get tool §b| Shift+Click: preview hologram" : "§cLocked");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "STRUCT_" + s.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6§lIsland Structures Catalog")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        IslandStructureManager manager = plugin.getIslandStructureManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        if (action.startsWith("STRUCT_")) {
            try {
                IslandStructureCosmetic s = IslandStructureCosmetic.valueOf(action.substring(7));
                if (manager.hasStructure(player.getUniqueId(), s)) {
                    if (player.isSneaking()) {
                        // Shift+Click in GUI = live preview hologram (deeper UX)
                        manager.previewStructure(player, s);
                        player.closeInventory();
                        return;
                    }
                    ItemStack tool = new ItemStack(Material.BRICKS);
                    ItemMeta toolMeta = tool.getItemMeta();
                    if (toolMeta != null) {
                        toolMeta.setDisplayName("§6Structure Placement Tool");
                        toolMeta.setLore(java.util.Arrays.asList(
                                "§7" + s.getDisplayName(),
                                "§eRight-click a block to place"
                        ));
                        toolMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "PLACE_STRUCT_" + s.name());
                        tool.setItemMeta(toolMeta);
                    }
                    player.getInventory().addItem(tool);
                    player.sendMessage("§aReceived placement tool for " + s.getDisplayName());
                    player.closeInventory();
                } else {
                    player.sendMessage("§cYou have not unlocked this structure.");
                }
            } catch (Exception ignored) {}
        }
    }
}
