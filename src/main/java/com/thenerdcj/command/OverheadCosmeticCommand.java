package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OverheadCosmeticCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public OverheadCosmeticCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (plugin.getOverheadCosmeticManager() == null || plugin.getOverheadCosmeticGUI() == null) {
            player.sendMessage("§cOverhead Cosmetics system not available yet.");
            return true;
        }

        plugin.getOverheadCosmeticGUI().open(player);
        return true;
    }
}
