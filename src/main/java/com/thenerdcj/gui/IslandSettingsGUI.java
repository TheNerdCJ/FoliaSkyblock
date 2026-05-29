package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class IslandSettingsGUI implements Listener {

    private final FoliaSkyblock plugin;

    public IslandSettingsGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, Island island) {
        GridPosition pos = island.getGridPosition();

        plugin.getIslandSettingsManager().getSettings(pos).thenAccept(settings -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                Inventory gui = Bukkit.createInventory(null, 54, "§6§lIsland Settings");

                gui.setItem(4, createItem(Material.NETHER_STAR, "§6§lIsland Settings",
                        "§7Configure your island's behavior"));

                gui.setItem(10, createToggleItem(Material.DIAMOND_SWORD, "§cPvP",
                        "§7Allow players to fight on your island", settings.isPvpEnabled()));
                gui.setItem(12, createToggleItem(Material.PLAYER_HEAD, "§aVisitors",
                        "§7Allow other players to visit your island", settings.isVisitorsAllowed()));
                gui.setItem(14, createToggleItem(Material.TNT, "§cExplosions",
                        "§7Allow explosions on your island", settings.isExplosionsEnabled()));
                gui.setItem(16, createToggleItem(Material.FLINT_AND_STEEL, "§6Fire Spread",
                        "§7Allow fire to spread on your island", settings.isFireSpreadEnabled()));
                gui.setItem(19, createToggleItem(Material.ZOMBIE_HEAD, "§5Mob Spawning",
                        "§7Allow hostile mobs to spawn", settings.isMobSpawningEnabled()));
                gui.setItem(21, createToggleItem(Material.WHEAT, "§2Crop Trampling",
                        "§7Allow players to trample crops", settings.isCropTramplingEnabled()));
                gui.setItem(23, createToggleItem(Material.COW_SPAWN_EGG, "§eAnimal Spawning",
                        "§7Allow passive mobs to spawn", settings.isAnimalSpawningEnabled()));
                gui.setItem(25, createToggleItem(Material.OAK_LEAVES, "§aLeaf Decay",
                        "§7Allow leaves to decay naturally", settings.isLeafDecayEnabled()));
                gui.setItem(28, createItem(Material.BLUE_STAINED_GLASS_PANE, "§9Border Color",
                        "§7Current: §b" + settings.getBorderColor()));
                gui.setItem(30, createItem(Material.STICK, "§eBorder Size",
                        "§7Current: §f" + settings.getBorderSize() + " blocks"));
                gui.setItem(32, createToggleItem(Material.ENDER_PEARL, "§5Island Warp",
                        "§7Allow others to warp to your island", settings.isWarpEnabled()));
                gui.setItem(34, createItem(Material.WRITABLE_BOOK, "§dWarp Description",
                        "§7" + (settings.getWarpDescription().isEmpty() ? "No description set" : settings.getWarpDescription())));
                gui.setItem(49, createItem(Material.BOOK, "§eHow Settings Work",
                        "§7• Green = Setting is enabled", "§7• Red = Setting is disabled"));

                for (int i = 0; i < 54; i++) {
                    if (gui.getItem(i) == null) gui.setItem(i, createGlassPane());
                }

                player.openInventory(gui);
                player.setMetadata("island_settings_pos", new org.bukkit.metadata.FixedMetadataValue(plugin, pos.toString()));
            });
        });
    }

    private ItemStack createToggleItem(Material material, String name, String lore, boolean enabled) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        String status = enabled ? "§a§lENABLED" : "§c§lDISABLED";
        meta.setLore(Arrays.asList(lore, "", "§7Status: " + status, "§7Click to toggle"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlassPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6§lIsland Settings")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata("island_settings_pos")) return;

        String posStr = player.getMetadata("island_settings_pos").get(0).asString();
        GridPosition pos = GridPosition.fromString(posStr);

        String itemName = event.getCurrentItem().getItemMeta().getDisplayName();

        if (itemName.contains("PvP")) toggleSetting(player, pos, "PVP", "PvP");
        else if (itemName.contains("Visitors")) toggleSetting(player, pos, "VISITORS", "Visitors");
        else if (itemName.contains("Explosions")) toggleSetting(player, pos, "EXPLOSIONS", "Explosions");
        else if (itemName.contains("Fire Spread")) toggleSetting(player, pos, "FIRE", "Fire Spread");
        else if (itemName.contains("Mob Spawning")) toggleSetting(player, pos, "MOBS", "Mob Spawning");
        else if (itemName.contains("Crop Trampling")) toggleSetting(player, pos, "CROPS", "Crop Trampling");
        else if (itemName.contains("Animal Spawning")) toggleSetting(player, pos, "ANIMALS", "Animal Spawning");
        else if (itemName.contains("Leaf Decay")) toggleSetting(player, pos, "LEAVES", "Leaf Decay");
        else if (itemName.contains("Island Warp")) toggleSetting(player, pos, "WARP", "Island Warp");
    }

    private void toggleSetting(Player player, GridPosition pos, String key, String name) {
        plugin.getIslandSettingsManager().toggleSetting(pos, key).thenAccept(newValue -> {
            player.sendMessage("§e" + name + " has been " + (newValue ? "§aenabled" : "§cdisabled") + "§e.");
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                if (island != null) new IslandSettingsGUI(plugin).open(player, island);
            });
        });
    }
}