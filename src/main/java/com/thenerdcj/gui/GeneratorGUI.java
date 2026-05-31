package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * GeneratorGUI - Per-island generator customization (MVP stub).
 * Future: toggle generator types, view stats, upgrade individual generators.
 */
public class GeneratorGUI implements Listener {

    private final FoliaSkyblock plugin;

    public GeneratorGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, Island island) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6§lIsland Generators");

        ItemStack info = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lGenerator Status");
            meta.setLore(Arrays.asList(
                "§7Ore Generator Level: §b" + plugin.getIslandUpgradeManager().getOreGeneratorLevel(island),
                "§7Cobble gens produce better ores",
                "",
                "§7Full customization coming soon!",
                "§7(Per-island type toggles, speed, etc.)"
            ));
            info.setItemMeta(meta);
        }
        gui.setItem(13, info);

        player.openInventory(gui);
    }
}