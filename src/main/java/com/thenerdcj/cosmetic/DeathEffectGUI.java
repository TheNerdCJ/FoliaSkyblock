package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for death effect GUI ({@link DeathEffectMainGUI} on {@link com.thenerdcj.gui.BaseGUI}).
 */
public class DeathEffectGUI {

    private final DeathEffectMainGUI mainGui;

    public DeathEffectGUI(FoliaSkyblock plugin) {
        this.mainGui = new DeathEffectMainGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }
}