package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Simple command for nametag visibility toggle.
 * /nametag toggle  - Toggles your overhead cosmetic tag visibility
 */
public class NametagCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public NametagCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (plugin.getPlayerNametagManager() == null) {
            player.sendMessage("§cNametag system not available.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            boolean nowVisible = plugin.getPlayerNametagManager().toggleNametagVisibility(player);
            if (nowVisible) {
                player.sendMessage("§aYour overhead nametag is now §evisible§a to others.");
            } else {
                player.sendMessage("§7Your overhead nametag is now §chidden§7 from others.");
            }
            return true;
        }

        player.sendMessage("§eUsage: /nametag toggle");
        return true;
    }
}
