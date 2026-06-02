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

public class IslandWeatherGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public IslandWeatherGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "island_weather_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        IslandWeatherCosmeticManager manager = plugin.getIslandWeatherCosmeticManager();
        if (manager == null) {
            player.sendMessage("§cIsland Weather system not available.");
            return;
        }

        String title = "§3§lIsland Weather Effects";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<IslandWeatherCosmetic> owned = manager.getOwnedWeather(player.getUniqueId());
        int total = IslandWeatherCosmetic.values().length - 1;

        inv.setItem(4, GUIUtils.createItem(Material.WATER_BUCKET, "§3§lCosmetic Island Weather",
                "§7Particle weather overlays for your island",
                "§7Collection: §a" + owned.size() + " / " + total,
                "§7(Visible to visitors - cosmetic only)"));

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        IslandWeatherCosmetic current = (island != null) ? manager.getActiveWeather(island.getId()) : IslandWeatherCosmetic.NONE;

        int slot = 19;
        for (IslandWeatherCosmetic w : IslandWeatherCosmetic.values()) {
            if (slot > 44) break;
            if (w.isNone()) continue;

            boolean has = owned.contains(w);
            boolean isActive = w == current;

            ItemStack item = GUIUtils.createItem(Material.WATER_BUCKET,
                    (isActive ? "§a§l★ " : has ? "§e" : "§7") + w.getRarity().getColorCode() + w.getDisplayName(),
                    "§7" + w.getDescription(),
                    "§7" + w.getFlavor(),
                    has ? (isActive ? "§aCurrently Active on Island" : "§eClick to set as island weather") : "§cLocked - Unlock via prestige or Slayer Shop");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "WEATHER_" + w.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack none = GUIUtils.createItem(Material.BARRIER, "§cClear Weather");
        ItemMeta noneMeta = none.getItemMeta();
        if (noneMeta != null) {
            noneMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "WEATHER_NONE");
            none.setItemMeta(noneMeta);
        }
        inv.setItem(18, none);

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
        if (!event.getView().getTitle().equals("§3§lIsland Weather Effects")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        IslandWeatherCosmeticManager manager = plugin.getIslandWeatherCosmeticManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cYou must be on your island (or have one) to set weather effects.");
            return;
        }

        if (action.equals("WEATHER_NONE")) {
            manager.setActiveWeather(island, IslandWeatherCosmetic.NONE);
            player.sendMessage("§aIsland weather cleared.");
            open(player);
            return;
        }

        if (action.startsWith("WEATHER_")) {
            try {
                IslandWeatherCosmetic w = IslandWeatherCosmetic.valueOf(action.substring(8));
                if (!manager.hasWeather(player.getUniqueId(), w)) {
                    player.sendMessage("§cYou have not unlocked this weather effect.");
                    return;
                }
                manager.setActiveWeather(island, w);
                player.sendMessage("§aIsland weather set: " + w.getRarity().getColorCode() + w.getDisplayName());
                open(player);
            } catch (Exception ignored) {}
        }
    }
}
