package com.thenerdcj.runes;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for cosmetic rune GUIs ({@link RuneMainGUI}, {@link RuneTableGUI} on {@link com.thenerdcj.gui.BaseGUI}).
 */
public class RuneGUI {

    private final RuneMainGUI mainGui;
    private final RuneTableGUI tableGui;

    public RuneGUI(FoliaSkyblock plugin) {
        this.mainGui = new RuneMainGUI(plugin);
        this.tableGui = new RuneTableGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }

    public void openRuneTable(Player player) {
        tableGui.open(player);
    }
}