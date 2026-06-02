package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /collections (or /collection) - Opens the core island collections GUI.
 * Shows unique discoveries, progress, and motivates gathering.
 */
public class CollectionCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public CollectionCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use collections.");
            return true;
        }

        if (plugin.getCollectionManager() == null || plugin.getCollectionsGUI() == null) {
            player.sendMessage("§cCollections system is not loaded.");
            return true;
        }

        plugin.getCollectionsGUI().open(player);
        return true;
    }
}