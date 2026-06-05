package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;

/**
 * Coordinator for generator GUI ({@link GeneratorMainGUI} on {@link BaseGUI}).
 */
public class GeneratorGUI {

    private final GeneratorMainGUI mainGui;

    public GeneratorGUI(FoliaSkyblock plugin) {
        this.mainGui = new GeneratorMainGUI(plugin);
    }

    public void open(Player player, Island island) {
        mainGui.open(player, island);
    }
}