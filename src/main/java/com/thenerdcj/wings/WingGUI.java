package com.thenerdcj.wings;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for elytra wing cosmetics ({@link WingMainGUI} on {@link com.thenerdcj.gui.BaseGUI}).
 */
public class WingGUI {

    private final WingMainGUI mainGui;

    public WingGUI(FoliaSkyblock plugin) {
        this.mainGui = new WingMainGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }
}