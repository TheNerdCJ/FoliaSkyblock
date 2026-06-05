package com.thenerdcj.tags;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;

/**
 * Coordinator for cosmetic tag GUI ({@link TagMainGUI} on {@link com.thenerdcj.gui.BaseGUI}).
 */
public class TagGUI {

    private final TagMainGUI mainGui;

    public TagGUI(FoliaSkyblock plugin) {
        this.mainGui = new TagMainGUI(plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }
}