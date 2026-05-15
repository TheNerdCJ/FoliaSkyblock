package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.TPAListGUI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Listener for TPAListGUI clicks.
 * Register in FoliaSkyblock registerListeners().
 */
public class TPAListener implements Listener {

    private final FoliaSkyblock plugin;
    private final TPAListGUI tpaListGUI;

    public TPAListener(FoliaSkyblock plugin, TPAListGUI tpaListGUI) {
        this.plugin = plugin;
        this.tpaListGUI = tpaListGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains("TPA Requests")) {
            tpaListGUI.handleClick(event);
        }
    }
}
