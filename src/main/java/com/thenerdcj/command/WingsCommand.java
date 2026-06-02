package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /wings command - opens the dedicated Elytra Wing Cosmetics GUI.
 */
public class WingsCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public WingsCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (plugin.getElytraWingManager() == null || plugin.getWingGUI() == null) {
            player.sendMessage("§cElytra Wing system is not available.");
            return true;
        }

        plugin.getWingGUI().open(player);
        return true;
    }
}
