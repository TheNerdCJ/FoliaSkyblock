package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for helmet skin GUI ({@link HelmetSkinMainGUI} on {@link com.thenerdcj.gui.BaseGUI}).
 */
public class HelmetSkinGUI {

    private final HelmetSkinMainGUI mainGui;

    public HelmetSkinGUI(FoliaSkyblock plugin) {
        this.mainGui = new HelmetSkinMainGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }
}