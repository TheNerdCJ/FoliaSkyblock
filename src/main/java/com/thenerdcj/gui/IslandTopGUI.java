package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Coordinator for island top leaderboard ({@link IslandTopMainGUI} on {@link BaseGUI}).
 */
public class IslandTopGUI {

    private final IslandTopMainGUI mainGui;

    public IslandTopGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandTopGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.mainGui = new IslandTopMainGUI(plugin, autoRegister);
    }

    public void open(Player player, Category category, int page) {
        mainGui.open(player, category, page);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        mainGui.onInventoryClick(event);
    }

    public enum Category {
        WORTH("Worth"),
        LEVEL("Level"),
        MEMBERS("Members");

        public final String display;

        Category(String display) {
            this.display = display;
        }
    }
}