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

    public void open(Player player, boolean isReset) {
        if (!player.hasPermission("foliasb.donor.biome") && !player.hasPermission("foliasb.donor")) {
            player.sendMessage("§cOnly donors can select custom biomes!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 45, GUI_TITLE + (isReset ? " §7(Reset)" : ""));

        gui.setItem(4, createTitleItem(isReset));

        gui.setItem(10, createBiomeItem(Material.GRASS_BLOCK, "§aPlains", "PLAINS", isReset));
        gui.setItem(12, createBiomeItem(Material.OAK_LOG, "§2Forest", "FOREST", isReset));
        gui.setItem(14, createBiomeItem(Material.SAND, "§eDesert", "DESERT", isReset));
        gui.setItem(16, createBiomeItem(Material.SPRUCE_LOG, "§bTaiga", "TAIGA", isReset));
        gui.setItem(20, createBiomeItem(Material.JUNGLE_LOG, "§2Jungle", "JUNGLE", isReset));

        int playerLevel = getPlayerIslandLevel(player);

        if (playerLevel >= 15) {
            gui.setItem(28, createBiomeItem(Material.NETHERRACK, "§cNether Wastes", "NETHER_WASTES", isReset));
        } else {
            gui.setItem(28, createLockedItem("§cNether Wastes", "§7Requires island level 15"));
        }

        if (playerLevel >= 30) {
            gui.setItem(34, createBiomeItem(Material.END_STONE, "§5The End", "THE_END", isReset));
        } else {
            gui.setItem(34, createLockedItem("§5The End", "§7Requires island level 30"));
        }

        for (int i = 0; i < 45; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, createGlassPane());
            }
        }

        player.openInventory(gui);
    }

    public void open(Player player) {
        open(player, false);
    }

    private ItemStack createTitleItem(boolean isReset) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (isReset) {
            meta.setDisplayName("§6§lChoose New Biome for Reset");
            meta.setLore(Arrays.asList("§7Select a new biome", "§cWarning: Island will be reset!"));
        } else {
            meta.setDisplayName("§6§lChoose Your Starting Biome");
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBiomeItem(Material material, String name, String biomeKey, boolean isReset) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (isReset) {
            meta.setLore(Arrays.asList("§7Click to reset to this biome"));
        }
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "biome_key"),
                org.bukkit.persistence.PersistentDataType.STRING, biomeKey
        );
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
        if (!event.getView().getTitle().startsWith(GUI_TITLE)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.BARRIER) return;

        String biome = null;
        if (clicked.getItemMeta() != null) {
            biome = clicked.getItemMeta().getPersistentDataContainer()
                    .get(new org.bukkit.NamespacedKey(plugin, "biome_key"),
                            org.bukkit.persistence.PersistentDataType.STRING);
        }

        if (biome != null) {
            player.closeInventory();
            boolean isResetMode = event.getView().getTitle().contains("(Reset)");

            if (isResetMode) {
                plugin.getIslandManager().resetIslandWithBiome(player, biome, World.Environment.NORMAL);
            } else {
                plugin.getIslandManager().createIsland(player, biome, World.Environment.NORMAL);
            }
        }
    }
}