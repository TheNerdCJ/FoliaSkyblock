package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Coordinator for island shop ({@link IslandShopMainGUI} on {@link BaseGUI}).
 */
public class IslandShopGUI {

    private final IslandShopMainGUI mainGui;

    public IslandShopGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandShopGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.mainGui = new IslandShopMainGUI(plugin, autoRegister);
    }

    public void open(Player player, Island island) {
        mainGui.open(player, island);
    }

    public void open(Player player, Island island, int page, String category) {
        mainGui.open(player, island, page, category);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        mainGui.onInventoryClick(event);
    }
}