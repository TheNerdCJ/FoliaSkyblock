package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for power orb skin GUI ({@link PowerOrbSkinMainGUI}).
 */
public class PowerOrbSkinGUI {

    private final PowerOrbSkinMainGUI mainGui;

    public PowerOrbSkinGUI(FoliaSkyblock plugin) {
        this.mainGui = new PowerOrbSkinMainGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }
}