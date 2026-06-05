package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Coordinator for island browse GUI ({@link IslandBrowseMainGUI} on {@link BaseGUI}).
 */
public class IslandBrowseGUI {

    private final IslandBrowseMainGUI mainGui;

    public IslandBrowseGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandBrowseGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.mainGui = new IslandBrowseMainGUI(plugin, autoRegister);
    }

    public void open(Player player, int page) {
        mainGui.open(player, page);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        mainGui.onInventoryClick(event);
    }
}