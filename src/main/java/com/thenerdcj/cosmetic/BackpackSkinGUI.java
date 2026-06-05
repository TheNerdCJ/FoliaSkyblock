package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for backpack skin GUI ({@link BackpackSkinMainGUI} on {@link com.thenerdcj.gui.BaseGUI}).
 */
public class BackpackSkinGUI {

    private final BackpackSkinMainGUI mainGui;

    public BackpackSkinGUI(FoliaSkyblock plugin) {
        this.mainGui = new BackpackSkinMainGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }
}