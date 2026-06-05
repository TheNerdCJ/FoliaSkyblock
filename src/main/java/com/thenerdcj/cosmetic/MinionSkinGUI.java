package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for minion skin GUI ({@link MinionSkinMainGUI}).
 */
public class MinionSkinGUI {

    private final MinionSkinMainGUI mainGui;

    public MinionSkinGUI(FoliaSkyblock plugin) {
        this.mainGui = new MinionSkinMainGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }
}