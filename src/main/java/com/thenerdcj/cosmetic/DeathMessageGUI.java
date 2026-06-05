package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for death message GUI ({@link DeathMessageMainGUI} on {@link com.thenerdcj.gui.BaseGUI}).
 */
public class DeathMessageGUI {

    private final DeathMessageMainGUI mainGui;

    public DeathMessageGUI(FoliaSkyblock plugin) {
        this.mainGui = new DeathMessageMainGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }
}