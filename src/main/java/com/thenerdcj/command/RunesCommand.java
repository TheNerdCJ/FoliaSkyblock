package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RunesCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public RunesCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (plugin.getRuneManager() == null || plugin.getRuneGUI() == null) {
            player.sendMessage("§cRune system not available.");
            return true;
        }

        plugin.getRuneGUI().open(player);
        return true;
    }
}
