package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class BiomeSelectionGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final String GUI_TITLE = "§6§lSelect Your Island Biome";

    public BiomeSelectionGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        if (!player.hasPermission("foliasb.donor.biome") && !player.hasPermission("foliasb.donor")) {
            player.sendMessage("§cOnly donors can select custom biomes!");
            player.sendMessage("§7Purchase a rank at our store to unlock this feature.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 45, GUI_TITLE);

        gui.setItem(4, createTitleItem());

        gui.setItem(10, createBiomeItem(Material.GRASS_BLOCK, "§aPlains", "§7Standard grass island\n§7Good for beginners", "PLAINS"));
        gui.setItem(12, createBiomeItem(Material.OAK_LOG, "§2Forest", "§7Dense trees and wood\n§7Great for building", "FOREST"));
        gui.setItem(14, createBiomeItem(Material.SAND, "§eDesert", "§7Sand and cacti\n§7Unique aesthetic", "DESERT"));
        gui.setItem(16, createBiomeItem(Material.SPRUCE_LOG, "§bTaiga", "§7Snowy forest vibe\n§7Spruce wood focus", "TAIGA"));
        gui.setItem(20, createBiomeItem(Material.JUNGLE_LOG, "§2Jungle", "§7Tropical paradise\n§7Rich with resources", "JUNGLE"));

        int playerLevel = getPlayerIslandLevel(player);

        if (playerLevel >= 15) {
            gui.setItem(28, createBiomeItem(Material.NETHERRACK, "§cNether Wastes", "§7Unlocks at level 15\n§7Nether island", "NETHER_WASTES"));
        } else {
            gui.setItem(28, createLockedItem("§cNether Wastes", "§7Requires level 15"));
        }

        if (playerLevel >= 30) {
            gui.setItem(34, createBiomeItem(Material.END_STONE, "§5The End", "§7Unlocks at level 30\n§7End island", "THE_END"));
        } else {
            gui.setItem(34, createLockedItem("§5The End", "§7Requires level 30"));
        }

        for (int i = 0; i < 45; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, createGlassPane());
            }
        }

        player.openInventory(gui);
    }

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lChoose Your Starting Biome");
        meta.setLore(Arrays.asList("§7Select a biome for your first island", "§7Donors can change biomes later with /is reset"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBiomeItem(Material material, String name, String lore, String biomeKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore.split("\n")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLockedItem(String name, String lore) {
        ItemStack item = new ItemStack(Material.BARRIER);
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

    private int getPlayerIslandLevel(Player player) {
        com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), World.Environment.NORMAL);
        return island != null ? island.getLevel() : 1;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.BARRIER) {
            return;
        }

        String displayName = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "";

        String biome = null;
        if (displayName.contains("Plains")) biome = "PLAINS";
        else if (displayName.contains("Forest")) biome = "FOREST";
        else if (displayName.contains("Desert")) biome = "DESERT";
        else if (displayName.contains("Taiga")) biome = "TAIGA";
        else if (displayName.contains("Jungle")) biome = "JUNGLE";
        else if (displayName.contains("Nether")) biome = "NETHER_WASTES";
        else if (displayName.contains("End")) biome = "THE_END";

        if (biome != null) {
            player.closeInventory();
            plugin.getIslandManager().createIsland(player, biome, World.Environment.NORMAL);
            player.sendMessage("§aCreating your §e" + biome + "§a island...");
        }
    }
}