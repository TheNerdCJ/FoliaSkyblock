package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Coordinator for island prestige ({@link PrestigeMainGUI} on {@link BaseGUI}).
 */
public class PrestigeGUI implements Listener {

    private final PrestigeMainGUI mainGui;

    public PrestigeGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public PrestigeGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.mainGui = new PrestigeMainGUI(plugin, false);
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