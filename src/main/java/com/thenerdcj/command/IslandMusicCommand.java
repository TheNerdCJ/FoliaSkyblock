package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IslandMusicCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public IslandMusicCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (plugin.getIslandMusicManager() == null || plugin.getIslandMusicGUI() == null) {
            player.sendMessage("§cIsland Music system not available.");
            return true;
        }

        plugin.getIslandMusicGUI().open(player);
        return true;
    }
}
