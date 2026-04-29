package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StaffCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public StaffCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("folia.moderator.mute") && !sender.hasPermission("folia.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "mute":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /mute <player> [duration] [reason]");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                player.sendMessage("§cMuted " + target.getName());
                target.sendMessage("§cYou have been muted.");
                break;

            case "unmute":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /unmute <player>");
                    return true;
                }
                Player unmuteTarget = Bukkit.getPlayer(args[0]);
                if (unmuteTarget == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                player.sendMessage("§aUnmuted " + unmuteTarget.getName());
                break;

            case "setspawn":
                player.getWorld().setSpawnLocation(player.getLocation());
                player.sendMessage("§aSpawn location set!");
                break;

            case "pending":
                player.sendMessage("§aPending staff actions shown in GUI.");
                break;

            default:
                player.sendMessage("§cUnknown staff command.");
        }
        return true;
    }
}