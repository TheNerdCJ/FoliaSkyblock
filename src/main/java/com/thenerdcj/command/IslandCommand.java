package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.IslandRank;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class IslandCommand implements CommandExecutor, TabCompleter {

    private final FoliaSkyblock plugin;

    public IslandCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§a=== Island Commands ===");
            player.sendMessage("§e/island create §7- Create a new island");
            player.sendMessage("§e/island home §7- Teleport to your island");
            player.sendMessage("§e/island invite <player> §7- Invite player");
            player.sendMessage("§e/island accept §7- Accept invite");
            player.sendMessage("§e/island kick <player> §7- Kick member");
            player.sendMessage("§e/island rank <player> <rank> §7- Set member rank");
            player.sendMessage("§e/island top §7- View top islands");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                plugin.getIslandManager().createIsland(player, "PLAINS", World.Environment.NORMAL);
                player.sendMessage("§aCreating your island...");
                break;

            case "home":
                player.teleport(plugin.getIslandManager().getIslandHome(player));
                player.sendMessage("§aTeleported to your island home.");
                break;

            case "invite":
                if (args.length < 2) { player.sendMessage("§cUsage: /island invite <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage("§cPlayer not found."); return true; }
                plugin.getIslandManager().inviteToParty(player, target);
                player.sendMessage("§aInvite sent to " + target.getName());
                break;

            case "accept":
                plugin.getIslandManager().acceptPartyInvite(player);
                player.sendMessage("§aInvite accepted!");
                break;

            case "kick":
                if (args.length < 2) { player.sendMessage("§cUsage: /island kick <player>"); return true; }
                Player kickTarget = Bukkit.getPlayer(args[1]);
                if (kickTarget != null) {
                    plugin.getIslandManager().removeMemberFromIsland(player.getUniqueId(), kickTarget.getUniqueId());
                    player.sendMessage("§aKicked " + kickTarget.getName());
                }
                break;

            case "rank":
                if (args.length < 3) { player.sendMessage("§cUsage: /island rank <player> <GUEST|HELPER|MODERATOR>"); return true; }
                Player rankTarget = Bukkit.getPlayer(args[1]);
                if (rankTarget != null) {
                    try {
                        IslandRank rank = IslandRank.valueOf(args[2].toUpperCase());
                        plugin.getIslandManager().setMemberRank(player.getUniqueId(), rankTarget.getUniqueId(), rank);
                        player.sendMessage("§aSet " + rankTarget.getName() + "'s rank to " + rank);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("§cInvalid rank. Use: GUEST, HELPER, MODERATOR");
                    }
                }
                break;

            case "top":
                player.sendMessage("§aTop islands feature coming soon!");
                break;

            default:
                player.sendMessage("§cUnknown subcommand. Use /island for help.");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("create");
            completions.add("home");
            completions.add("invite");
            completions.add("accept");
            completions.add("kick");
            completions.add("rank");
            completions.add("top");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("rank")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("rank")) {
            completions.add("GUEST");
            completions.add("HELPER");
            completions.add("MODERATOR");
        }

        return completions;
    }
}