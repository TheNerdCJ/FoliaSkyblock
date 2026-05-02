package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.rank.RankData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public RankCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            plugin.getRankManager().getUpvoteCount(player.getUniqueId())
                    .thenAccept(votes -> {
                        RankData rank = plugin.getRankManager().getRank(player.getUniqueId());
                        player.sendMessage("§aYour rank: §e" + rank.getDisplayName());
                        player.sendMessage("§aYour votes: §e" + votes);
                    });
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list":
                player.sendMessage("§6=== Available Ranks ===");
                for (RankData rank : plugin.getRankManager().getAllRanks()) {
                    player.sendMessage("§e" + rank.getDisplayName() + " §7(" + rank.getCategory() + ")");
                }
                break;

            case "vote":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /rank vote <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                plugin.getRankManager().castVote(player.getUniqueId(), target.getUniqueId())
                        .thenAccept(success -> {
                            if (success) {
                                player.sendMessage("§aVote cast successfully!");
                            } else {
                                player.sendMessage("§cYou cannot vote for this player right now.");
                            }
                        });
                break;

            default:
                player.sendMessage("§cUnknown subcommand. Use /rank list or /rank vote <player>");
        }

        return true;
    }
}