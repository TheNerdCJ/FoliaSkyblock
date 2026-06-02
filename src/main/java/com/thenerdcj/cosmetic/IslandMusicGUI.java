package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.GUIUtils;
import com.thenerdcj.island.Island;
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

public class IslandMusicGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public IslandMusicGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "island_music_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        IslandMusicManager manager = plugin.getIslandMusicManager();
        if (manager == null) {
            player.sendMessage("§cIsland Music system not available.");
            return;
        }

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        String islandKey = (island != null) ? island.getId() : null;
        IslandMusicType current = (islandKey != null) ? manager.getActiveMusic(islandKey) : IslandMusicType.NONE;

        String title = "§d§lIsland Music & Ambience";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<IslandMusicType> owned = manager.getOwnedMusic(player.getUniqueId());
        int total = IslandMusicType.values().length - 1;

        inv.setItem(4, GUIUtils.createItem(Material.NOTE_BLOCK, "§d§lIsland Music & Ambience",
                "§7Cosmetic sounds for your island",
                "§7Collection: §a" + owned.size() + " / " + total,
                "§7Active: " + (current.isNone() ? "§7None" : current.getRarity().getColorCode() + current.getDisplayName())));

        int slot = 19;
        for (IslandMusicType m : IslandMusicType.values()) {
            if (slot > 44) break;
            if (m.isNone()) continue;

            boolean has = owned.contains(m);
            boolean isActive = m == current;

            ItemStack item = GUIUtils.createItem(Material.NOTE_BLOCK,
                    (isActive ? "§a§l★ " : has ? "§e" : "§7") + m.getRarity().getColorCode() + m.getDisplayName(),
                    "§7" + m.getDescription(),
                    "§7" + m.getFlavor(),
                    has ? (isActive ? "§aCurrently playing on this island" : "§eClick to play on your island") : "§cLocked");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "MUSIC_" + m.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack noneItem = GUIUtils.createItem(Material.BARRIER, "§cStop Music",
                "§7Silence the island ambience");
        ItemMeta noneMeta = noneItem.getItemMeta();
        if (noneMeta != null) {
            noneMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "MUSIC_NONE");
            noneItem.setItemMeta(noneMeta);
        }
        inv.setItem(18, noneItem);

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
        if (!event.getView().getTitle().equals("§d§lIsland Music & Ambience")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        IslandMusicManager manager = plugin.getIslandMusicManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cYou must be on your island to change music.");
            return;
        }

        if (action.equals("MUSIC_NONE")) {
            manager.setActiveMusic(island, IslandMusicType.NONE);
            player.sendMessage("§aIsland music stopped.");
            open(player);
            return;
        }

        if (action.startsWith("MUSIC_")) {
            try {
                IslandMusicType m = IslandMusicType.valueOf(action.substring(6));
                if (!manager.hasMusic(player.getUniqueId(), m)) {
                    player.sendMessage("§cYou have not unlocked this ambient.");
                    return;
                }
                manager.setActiveMusic(island, m);
                player.sendMessage("§aNow playing: " + m.getRarity().getColorCode() + m.getDisplayName());
                open(player);
            } catch (Exception ignored) {}
        }
    }
}
