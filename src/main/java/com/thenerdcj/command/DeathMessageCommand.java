package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for Death Messages cosmetic.
 */
public class DeathMessageCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public DeathMessageCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (plugin.getDeathMessageManager() == null || plugin.getDeathMessageGUI() == null) {
            player.sendMessage("§cDeath Messages system not loaded.");
            return true;
        }

        plugin.getDeathMessageGUI().open(player);
        return true;
    }
}