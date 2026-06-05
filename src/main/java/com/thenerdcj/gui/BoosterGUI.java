package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Coordinator for island boosters ({@link BoosterMainGUI} on {@link BaseGUI}).
 */
public class BoosterGUI implements Listener {

    private final BoosterMainGUI mainGui;

    public BoosterGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public BoosterGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.mainGui = new BoosterMainGUI(plugin, false);
        if (autoRegister) {
            org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, Island island) {
        mainGui.open(player, island);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        mainGui.onInventoryClick(event);
    }
}