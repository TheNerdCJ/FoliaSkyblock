package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for join/leave message GUI ({@link JoinLeaveMessageMainGUI} on {@link com.thenerdcj.gui.BaseGUI}).
 */
public class JoinLeaveMessageGUI {

    private final JoinLeaveMessageMainGUI mainGui;

    public JoinLeaveMessageGUI(FoliaSkyblock plugin) {
        this.mainGui = new JoinLeaveMessageMainGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }
}