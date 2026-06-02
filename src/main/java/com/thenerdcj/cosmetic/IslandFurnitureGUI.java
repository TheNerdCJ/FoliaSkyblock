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
 * Basic catalog GUI for unlocked Island Furniture.
 * Foundation version - shows owned pieces and allows "preview/place" (placement logic expands later).
 */
public class IslandFurnitureGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public IslandFurnitureGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "island_furniture_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        IslandFurnitureManager manager = plugin.getIslandFurnitureManager();
        if (manager == null) {
            player.sendMessage("§cIsland Furniture system not available yet.");
            return;
        }

        String title = "§6§lIsland Furniture Catalog";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<IslandFurnitureType> owned = manager.getOwnedFurniture(player.getUniqueId());
        int total = IslandFurnitureType.values().length - 1;

        inv.setItem(4, GUIUtils.createItem(Material.CHEST, "§6§lIsland Furniture",
                "§7Decorative housing cosmetics",
                "§7Collection: §a" + owned.size() + " / " + total));

        int slot = 19;
        for (IslandFurnitureType f : IslandFurnitureType.values()) {
            if (slot > 44) break;
            if (f.isNone()) continue;

            boolean has = owned.contains(f);

            String setInfo = ! "Basic".equals(f.getFurnitureSet()) ? "§7Set: " + f.getFurnitureSet() : "";
            ItemStack item = GUIUtils.createItem(Material.CHEST,
                    (has ? "§e" : "§7") + f.getRarity().getColorCode() + f.getDisplayName(),
                    "§7" + f.getDescription(),
                    "§7Category: " + f.getCategory().getDisplayName(),
                    setInfo,
                    has ? "§aOwned §7- §eClick: get tool §b| Shift+Click: preview hologram" : "§cLocked");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "FURN_" + f.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        // Back
        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        // Per-island furniture ownership display (polish)
        ItemStack viewPlaced = GUIUtils.createItem(Material.PAPER, "§6§lView Placed on Island",
                "§7See what furniture is currently placed on this island",
                "§7(Click to list in chat)");
        ItemMeta viewMeta = viewPlaced.getItemMeta();
        if (viewMeta != null) {
            viewMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "VIEW_PLACED");
            viewPlaced.setItemMeta(viewMeta);
        }
        inv.setItem(40, viewPlaced);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6§lIsland Furniture Catalog")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        IslandFurnitureManager manager = plugin.getIslandFurnitureManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        if (action.startsWith("FURN_")) {
            try {
                IslandFurnitureType f = IslandFurnitureType.valueOf(action.substring(5));
                if (manager.hasFurniture(player.getUniqueId(), f)) {
                    if (player.isSneaking()) {
                        // Shift+Click in GUI = live preview hologram (deeper UX)
                        manager.previewFurniture(player, f);
                        player.closeInventory();
                        return;
                    }
                    // Give placement tool item (normal click)
                    ItemStack tool = new ItemStack(Material.CHEST);
                    ItemMeta toolMeta = tool.getItemMeta();
                    if (toolMeta != null) {
                        toolMeta.setDisplayName("§6Furniture Placement Tool");
                        toolMeta.setLore(java.util.Arrays.asList(
                                "§7" + f.getDisplayName(),
                                "§eRight-click a block to place",
                                "§7Shift + Right-click furniture to remove"
                        ));
                        toolMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "PLACE_" + f.name());
                        tool.setItemMeta(toolMeta);
                    }
                    player.getInventory().addItem(tool);
                    player.sendMessage("§aReceived placement tool for " + f.getDisplayName());
                    player.closeInventory();
                } else {
                    player.sendMessage("§cYou have not unlocked this furniture.");
                }
            } catch (Exception ignored) {}
        }

        if (action.equals("VIEW_PLACED")) {
            // Per-island furniture ownership display
            com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island == null) {
                player.sendMessage("§cYou must be on your island.");
                return;
            }
            String key = island.getId();
            java.util.List<IslandFurnitureManager.PlacedFurniture> placed = manager.getPlacedFurnitureForIsland(key);
            if (placed.isEmpty()) {
                player.sendMessage("§7No furniture placed on this island yet.");
            } else {
                player.sendMessage("§6§lFurniture placed on this island:");
                java.util.Map<String, Integer> count = new java.util.HashMap<>();
                java.util.Map<String, String> owners = new java.util.HashMap<>();
                for (IslandFurnitureManager.PlacedFurniture p : placed) {
                    count.merge(p.furnitureId(), 1, Integer::sum);
                    if (p.extraData() != null && p.extraData().length() > 0) {
                        owners.put(p.furnitureId(), p.extraData());
                    }
                }
                for (java.util.Map.Entry<String, Integer> e : count.entrySet()) {
                    try {
                        IslandFurnitureType t = IslandFurnitureType.valueOf(e.getKey());
                        String ownerInfo = owners.containsKey(e.getKey()) ? " §7(by " + owners.get(e.getKey()).substring(0,8) + "...)" : "";
                        player.sendMessage("§7- " + t.getRarity().getColorCode() + t.getDisplayName() + " §7x" + e.getValue() + ownerInfo);
                    } catch (Exception ex) {
                        player.sendMessage("§7- " + e.getKey() + " x" + e.getValue());
                    }
                }
                // Show active set bonuses (pride)
                java.util.Set<String> active = manager.getActiveSetBonuses(key);
                if (!active.isEmpty()) {
                    player.sendMessage("§a★ Active set pride: §e" + String.join(", ", active) + " §7(pieces glow + visitors see bursts)");
                }
            }
            player.closeInventory();
        }
    }
}
