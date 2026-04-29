package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final FoliaSkyblock plugin;
    private final RankManager rankManager;

    public RankCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "vote" -> handleVote(player, args);
            case "set" -> handleSet(player, args);
            case "check" -> handleCheck(player, args);
            case "list" -> handleList(player);
            case "reload" -> handleReload(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleVote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /rank vote <player>");
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cYou cannot vote for yourself.");
            return;
        }

        plugin.getDatabaseManager().voteForPlayer(player.getUniqueId(), target.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aYou voted for §e" + targetName + "§a!");
                        // Trigger auto-promotion
                        rankManager.checkForAutoPromotion(target.getUniqueId());
                    } else {
                        player.sendMessage("§cYou have already voted for this player recently.");
                    }
                });
    }

    private void handleSet(Player player, String[] args) {
        if (!player.hasPermission("foliaskyblock.admin.rank.set")) {
            player.sendMessage("§cYou do not have permission to set ranks.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cUsage: /rank set <player> <rank>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String rankId = args[2];

        RankManager.Rank rank = rankManager.getRank(rankId);
        if (rank == null) {
            player.sendMessage("§cInvalid rank! Available ranks: " + String.join(", ", rankManager.getAllRanks().keySet()));
            return;
        }

        plugin.getDatabaseManager().setRank(target.getUniqueId(), rankId)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aSet §e" + target.getName() + "'s §arank to §e" + rankId.toUpperCase());
                        Player onlineTarget = target.getPlayer();
                        if (onlineTarget != null) {
                            rankManager.applyRankPrefix(onlineTarget);
                        }
                    } else {
                        player.sendMessage("§cFailed to set rank.");
                    }
                });
    }

    private void handleCheck(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /rank check <player>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        rankManager.getPlayerRankId(target.getUniqueId())
                .thenAccept(rankId -> {
                    player.sendMessage("§e" + target.getName() + "'s rank is: §f" + rankId.toUpperCase());
                });
    }

    private void handleList(Player player) {
        player.sendMessage("§6=== Available Ranks ===");
        rankManager.getAllRanks().values().forEach(rank -> {
            player.sendMessage("§e" + rank.getId().toUpperCase() + " §7- " + rank.getFormattedPrefix());
        });
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("foliaskyblock.admin.rank.reload")) {
            player.sendMessage("§cYou do not have permission.");
            return;
        }
        rankManager.reloadRanks();
        player.sendMessage("§aRanks reloaded successfully!");
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== Rank Commands ===");
        player.sendMessage("§e/rank vote <player> §7- Vote for a player");
        player.sendMessage("§e/rank check <player> §7- Check a player's rank");
        player.sendMessage("§e/rank list §7- List all ranks");
        if (player.hasPermission("foliaskyblock.admin.rank.set")) {
            player.sendMessage("§e/rank set <player> <rank> §7- Set a player's rank");
        }
        if (player.hasPermission("foliaskyblock.admin.rank.reload")) {
            player.sendMessage("§e/rank reload §7- Reload ranks.yml");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("vote", "set", "check", "list", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return List.of("member", "helper", "moderator", "admin");
        }
        return new ArrayList<>();
    }
}