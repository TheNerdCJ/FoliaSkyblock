package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Coordinator for island bank ({@link IslandBankMainGUI} on {@link BaseGUI}).
 */
public class IslandBankGUI {

    private final IslandBankMainGUI mainGui;

    public IslandBankGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandBankGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.mainGui = new IslandBankMainGUI(plugin, autoRegister);
    }

    public void open(Player player, Island island) {
        mainGui.open(player, island);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        mainGui.onInventoryClick(event);
    }
}