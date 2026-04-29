package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PlayerCommand implements CommandExecutor, TabCompleter {

    private final FoliaSkyblock plugin;

    public PlayerCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "spawn":
                player.teleport(Bukkit.getWorld("world").getSpawnLocation());
                player.sendMessage("§aTeleported to spawn.");
                break;

            case "home":
                player.teleport(plugin.getIslandManager().getIslandHome(player));
                player.sendMessage("§aTeleported to your island home.");
                break;

            case "tpa":
                if (args.length < 1) {
                    player.sendMessage("§cUsage: /tpa <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                player.sendMessage("§aTPA request sent to " + target.getName());
                target.sendMessage("§e" + player.getName() + " §7wants to teleport to you. Use §a/tpaccept §7or §c/tpdeny");
                break;

            case "tpaccept", "tpac":
                player.sendMessage("§aTeleport request accepted!");
                break;

            case "tpdeny", "tpdecline":
                player.sendMessage("§cTeleport request denied.");
                break;

            case "tpignore":
                player.sendMessage("§aYou will no longer receive TPA requests.");
                break;

            case "pending":
                player.sendMessage("§aYou have no pending TPA requests.");
                break;

            case "rules":
                player.sendMessage("§6=== Server Rules ===");
                player.sendMessage("§e1. No griefing");
                player.sendMessage("§e2. Be respectful");
                player.sendMessage("§e3. No cheating");
                break;

            case "bal":
                plugin.getDatabaseManager().getPlayerBalance(player.getUniqueId())
                        .thenAccept(balance -> player.sendMessage("§aYour balance: §e$" + String.format("%,.2f", balance)));
                break;

            default:
                player.sendMessage("§cUnknown command.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && (alias.equalsIgnoreCase("tpa") || alias.equalsIgnoreCase("tpignore"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }
        return new ArrayList<>();
    }
}