package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Coordinator for island upgrades ({@link IslandUpgradeMainGUI} on {@link com.thenerdcj.gui.BaseGUI}).
 */
public class IslandUpgradeGUI {

    private final IslandUpgradeMainGUI mainGui;

    public IslandUpgradeGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandUpgradeGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.mainGui = new IslandUpgradeMainGUI(plugin, autoRegister);
    }

    public void open(Player player, Island island) {
        mainGui.open(player, island);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        mainGui.onInventoryClick(event);
    }
}