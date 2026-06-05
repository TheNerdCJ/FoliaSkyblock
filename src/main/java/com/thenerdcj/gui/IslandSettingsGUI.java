package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Coordinator for island settings ({@link IslandSettingsMainGUI} on {@link BaseGUI}).
 */
public class IslandSettingsGUI {

    private final IslandSettingsMainGUI mainGui;

    public IslandSettingsGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandSettingsGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.mainGui = new IslandSettingsMainGUI(plugin, autoRegister);
    }

    public void open(Player player, Island island) {
        mainGui.open(player, island);
    }

    /** Delegates to {@link IslandSettingsMainGUI#onInventoryClick} for tests and legacy callers. */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        mainGui.onInventoryClick(event);
    }
}