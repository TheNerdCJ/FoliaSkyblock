package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;import com.thenerdcj.rank.RankData;import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * RankCommand - Handles all rank-related commands
 *
 * Commands:
 * /rank - Shows your current rank
 * /rank <player> - Shows another player's rank
 * /rank set <player> <rank> - Sets a player's rank (admin)
 * /rank list - Lists all available ranks
 * /rank reload - Reloads ranks from config (admin)
 * /rank vote <player> - Votes for a player (community voting system)
 */
public class RankCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public RankCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // /rank - Show own rank
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }

            showPlayerRank(sender, player.getUniqueId(), player.getName());
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                if (!sender.hasPermission("folia.rank.set")) {
                    sender.sendMessage("§cYou don't have permission to set ranks.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rank set <player> <rank>");
                    return true;
                }
                setPlayerRank(sender, args[1], args[2]);
                return true;

            case "list":
                listAllRanks(sender);
                return true;

            case "reload":
                if (!sender.hasPermission("folia.rank.reload")) {
                    sender.sendMessage("§cYou don't have permission to reload ranks.");
                    return true;
                }
                plugin.getRankManager().reloadRanks();
                sender.sendMessage("§aRanks reloaded successfully!");
                return true;

            case "vote":
                if (!(sender instanceof Player voter)) {
                    sender.sendMessage("§cOnly players can vote.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rank vote <player>");
                    return true;
                }
                voteForPlayer(voter, args[1]);
                return true;

            default:
                // /rank <player> - Show another player's rank
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can check other players' ranks.");
                    return true;
                }

                // Try to find the player
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                if (target != null && target.hasPlayedBefore()) {
                    showPlayerRank(sender, target.getUniqueId(), target.getName());
                } else {
                    sender.sendMessage("§cPlayer not found: " + args[0]);
                }
                return true;
        }
    }

    private void showPlayerRank(CommandSender sender, UUID uuid, String playerName) {
        plugin.getRankManager().getCurrentRankId(uuid).thenAccept(rankId -> {
            RankData rankData = plugin.getRankManager().getRankData(rankId);

            if (rankData != null) {
                var ser = LegacyComponentSerializer.legacyAmpersand(); String prefix = ser.serialize(ser.deserialize(rankData.getPrefix())); sender.sendMessage("§7" + playerName + "'s rank: " + prefix + " §7(" + rankId + ")");

                if (rankData.isStaff()) {
                    sender.sendMessage("§7This player is a §cstaff member§7.");
                }
                if (rankData.isDonor()) {
                    sender.sendMessage("§7This player is a §6donor§7.");
                }
            } else {
                sender.sendMessage("§7" + playerName + "'s rank: §e" + rankId);
            }
        });
    }

    private void setPlayerRank(CommandSender sender, String targetName, String rankId) {
        if (!plugin.getRankManager().rankExists(rankId)) {
            sender.sendMessage("§cRank not found: " + rankId);
            sender.sendMessage("§7Use /rank list to see available ranks.");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || !target.hasPlayedBefore()) {
            sender.sendMessage("§cPlayer not found: " + targetName);
            return;
        }

        plugin.getRankManager().setPlayerRank(target.getUniqueId(), rankId);

        RankData rankData = plugin.getRankManager().getRankData(rankId);
        String displayName = rankData != null ? rankData.getDisplayName() : rankId;

        sender.sendMessage("§aSet " + targetName + "'s rank to §e" + displayName + "§a!");

        // Notify the target if they're online
        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            onlineTarget.sendMessage("§aYour rank has been set to §e" + displayName + "§a!");
            plugin.getRankManager().applyRankPrefix(onlineTarget);
        }
    }

    private void listAllRanks(CommandSender sender) {
        List<RankData> ranks = plugin.getRankManager().getAllRanksSorted();

        sender.sendMessage("§6§lAvailable Ranks:");
        sender.sendMessage("§7Total ranks: §e" + ranks.size());
        sender.sendMessage("");

        for (RankData rank : ranks) {
            var ser = LegacyComponentSerializer.legacyAmpersand(); String prefix = ser.serialize(ser.deserialize(rank.getPrefix()));
            StringBuilder info = new StringBuilder();
            info.append(prefix).append(" §7- ").append(rank.getDisplayName());

            if (rank.isDefault()) {
                info.append(" §a(default)");
            }
            if (rank.isStaff()) {
                info.append(" §c(staff)");
            }
            if (rank.isDonor()) {
                info.append(" §6(donor)");
            }

            sender.sendMessage(info.toString());

            // Show permissions count
            sender.sendMessage("  §7Permissions: §e" + rank.getPermissions().size());
        }
    }

    private void voteForPlayer(Player voter, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (target == null || !target.hasPlayedBefore()) {
            voter.sendMessage("§cPlayer not found: " + targetName);
            return;
        }

        if (target.getUniqueId().equals(voter.getUniqueId())) {
            voter.sendMessage("§cYou cannot vote for yourself!");
            return;
        }

        // Check if voter has already voted for this player recently
        plugin.getRankManager().addVote(voter.getUniqueId(), target.getUniqueId()).thenAccept(success -> {
            if (success) {
                voter.sendMessage("§aYou voted for §e" + targetName + "§a!");
                voter.sendMessage("§7Your vote has been recorded. Thank you for helping the community!");

                // Notify target if online
                if (target.isOnline()) {
                    Player onlineTarget = target.getPlayer();
                    onlineTarget.sendMessage("§a§l" + voter.getName() + " §7voted for you!");
                    onlineTarget.sendMessage("§7Keep up the good work!");
                }
            } else {
                voter.sendMessage("§cYou have already voted for this player recently!");
                voter.sendMessage("§7You can vote again in 30 days.");
            }
        });
    }
}