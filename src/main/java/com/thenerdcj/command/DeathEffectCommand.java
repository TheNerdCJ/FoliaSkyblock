package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeathEffectCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public DeathEffectCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (plugin.getDeathEffectManager() == null || plugin.getDeathEffectGUI() == null) {
            player.sendMessage("§cDeath Effects system not available.");
            return true;
        }

        plugin.getDeathEffectGUI().open(player);
        return true;
    }
}