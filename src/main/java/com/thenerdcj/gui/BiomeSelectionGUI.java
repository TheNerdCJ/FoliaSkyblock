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

/**
 * Donor Biome Selection GUI with Progression System
 * Allows donors to choose Overworld biomes freely
 * Nether and End are level-gated and progression-locked
 */
public class BiomeSelectionGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final String GUI_TITLE = "§6§lChoose Your Island Biome";

    public BiomeSelectionGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the biome selection GUI for a donor player
     */
    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, GUI_TITLE);

        // Title decoration
        gui.setItem(4, createTitleItem());

        // Row 2: Overworld biomes (5 options) - Always available
        gui.setItem(10, createBiomeItem(Material.GRASS_BLOCK, "§aPlains", "§7Standard grass island\n§7Good for beginners", "PLAINS"));
        gui.setItem(12, createBiomeItem(Material.OAK_LOG, "§2Forest", "§7Dense trees and wood\n§7Great for building", "FOREST"));
        gui.setItem(14, createBiomeItem(Material.SAND, "§eDesert", "§7Sand and cacti\n§7Unique aesthetic", "DESERT"));
        gui.setItem(16, createBiomeItem(Material.SPRUCE_LOG, "§bTaiga", "§7Snowy forest vibe\n§7Spruce wood focus", "TAIGA"));
        gui.setItem(20, createBiomeItem(Material.JUNGLE_LOG, "§2Jungle", "§7Tropical paradise\n§7Rich with resources", "JUNGLE"));

        // Row 3: Dimension islands (level-gated)
        int playerLevel = getPlayerIslandLevel(player);
        int netherReq = plugin.getConfig().getInt("island.dimension_requirements.nether", 10);
        int endReq = plugin.getConfig().getInt("island.dimension_requirements.end", 25);

        if (playerLevel >= netherReq) {
            gui.setItem(28, createBiomeItem(Material.NETHERRACK, "§cNether", "§7Netherrack and fire\n§7Quartz and gold ores\n\n§a§lUNLOCKED - Click to create!", "NETHER_WASTES"));
        } else {
            gui.setItem(28, createLockedItem("§cNether", netherReq, playerLevel));
        }

        if (playerLevel >= endReq) {
            gui.setItem(30, createBiomeItem(Material.END_STONE, "§5End", "§7End stone and obsidian\n§7Chorus flowers\n\n§a§lUNLOCKED - Click to create!", "THE_END"));
        } else {
            gui.setItem(30, createLockedItem("§5End", endReq, playerLevel));
        }

        // Info about progression
        gui.setItem(22, createProgressionInfoItem());

        // Bottom row: Close and info
        gui.setItem(36, createInfoItem());
        gui.setItem(40, createCloseItem());
        gui.setItem(44, createInfoItem());

        player.openInventory(gui);
        player.sendMessage("§a§lDonor Perk: §eChoose your favorite biome!");
    }

    private int getPlayerIslandLevel(Player player) {
        var island = plugin.getIslandManager().getIsland(player.getUniqueId(), World.Environment.NORMAL);
        return island != null ? island.getLevel() : 1;
    }

    private ItemStack createBiomeItem(Material material, String name, String lore, String biomeName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(name);

        java.util.List<String> loreList = new java.util.ArrayList<>();
        for (String line : lore.split("\n")) {
            loreList.add(line);
        }
        loreList.add("");
        loreList.add("§a§lClick to select this biome!");

        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§l✦ DONOR BIOME SELECTION ✦");
        meta.setLore(Arrays.asList(
                "§7Choose any Overworld biome",
                "§7for your starting island!",
                "",
                "§c§lNether & End are progression-locked"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProgressionInfoItem() {
        ItemStack item = new ItemStack(Material.OBSIDIAN);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§5§lProgression Locked");
        meta.setLore(Arrays.asList(
                "§7Nether and End islands",
                "§7are unlocked naturally:",
                "",
                "§c• Nether: Build a portal",
                "§5• End: Fall through void"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLockedItem(String name, int requiredLevel, int currentLevel) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name + " §c§l(LOCKED)");
        meta.setLore(Arrays.asList(
                "§7Required Island Level: §e" + requiredLevel,
                "§7Your Current Level: §c" + currentLevel,
                "",
                "§7Level up your island to unlock!"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lDonor Biome Selection");
        meta.setLore(Arrays.asList(
                "§7As a donor, you can choose",
                "§7any biome for your island!",
                "",
                "§eNormal players get random biomes."
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§lClose");
        meta.setLore(List.of("§7Click to cancel island creation"));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String displayName = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "";

        if (displayName.contains("Close") || displayName.contains("Barrier")) {
            player.closeInventory();
            player.sendMessage("§cIsland creation cancelled.");
            return;
        }

        if (displayName.contains("Plains")) {
            createIslandWithBiome(player, "PLAINS");
        } else if (displayName.contains("Forest")) {
            createIslandWithBiome(player, "FOREST");
        } else if (displayName.contains("Desert")) {
            createIslandWithBiome(player, "DESERT");
        } else if (displayName.contains("Taiga")) {
            createIslandWithBiome(player, "TAIGA");
        } else if (displayName.contains("Jungle")) {
            createIslandWithBiome(player, "JUNGLE");
        } else if (displayName.contains("Nether") && displayName.contains("UNLOCKED")) {
            createIslandWithBiome(player, "NETHER_WASTES");
        } else if (displayName.contains("End") && displayName.contains("UNLOCKED")) {
            createIslandWithBiome(player, "THE_END");
        } else if (displayName.contains("LOCKED")) {
            player.sendMessage("§cYou have not reached the required island level yet!");
        }
    }

    private void createIslandWithBiome(Player player, String biomeName) {
        player.closeInventory();
        player.sendMessage("§aCreating your §e" + biomeName + "§a island...");

        plugin.getIslandManager().createIsland(player, biomeName, World.Environment.NORMAL);
    }
}