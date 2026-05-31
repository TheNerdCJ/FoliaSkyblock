package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.SlayerTier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SlayerCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public SlayerCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /slayer start <tier>");
                    return true;
                }
                startSlayerQuest(player, args[1]);
                break;

            case "progress":
                showProgress(player);
                break;

            case "top":
            case "leaderboard":
            case "lb":
                showLeaderboard(player);
                break;

            case "achievements":
            case "achieve":
            case "ach":
                showAchievements(player);
                break;

            case "abandon":
                abandonQuest(player);
                break;

            case "rewards":
                showRewards(player);
                break;

            case "tokens":
            case "tokenlb":
            case "tokenleaderboard":
                plugin.getSlayerTokenLeaderboardGUI().open(player);
                break;

            case "admin":
                if (!player.hasPermission("foliasb.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                handleAdminCommand(player, args);
                break;

            default:
                showHelp(player);
        }

        return true;
    }

    private void startSlayerQuest(Player player, String tierName) {
        try {
            SlayerTier tier = SlayerTier.valueOf(tierName.toUpperCase());
            boolean success = plugin.getBossManager().startSlayerQuest(player, tier);

            if (success) {
                player.sendMessage("§aSlayer quest started! Kill " + tier.getTargetEntity().name() + "s to progress.");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid tier. Use /slayer for help. New tiers include skeleton, creeper, and boss slayers!");
        }
    }

    private void showProgress(Player player) {
        var quest = plugin.getBossManager().getActiveSlayerQuest(player);

        if (quest == null) {
            player.sendMessage("§7You don't have an active slayer quest.");
            return;
        }

        player.sendMessage("§6§l=== ACTIVE SLAYER QUEST ===");
        player.sendMessage("§e" + quest.getTier().getDisplayName());
        player.sendMessage("§7Progress: §e" + quest.getKills() + "§7/§e" + quest.getKillsRequired() +
                " §7kills (§6" + (int)(quest.getProgress() * 100) + "%§7)");
    }

    private void showLeaderboard(Player player) {
        plugin.getSlayerLeaderboardGUI().open(player);
    }

    private void showAchievements(Player player) {
        plugin.getSlayerAchievementGUI().open(player);
    }

    private void abandonQuest(Player player) {
        boolean success = plugin.getBossManager().abandonSlayerQuest(player);
        if (success) {
            player.sendMessage("§cSlayer quest abandoned.");
        } else {
            player.sendMessage("§7You don't have an active slayer quest.");
        }
    }

    private void showRewards(Player player) {
        player.sendMessage("§6§l=== SLAYER REWARDS PREVIEW ===");
        for (SlayerTier tier : SlayerTier.values()) {
            if (tier.getTier() == 1) {
                player.sendMessage("§e" + tier.getDisplayName());
                for (var reward : tier.getRewards()) {
                    player.sendMessage("  " + reward.getRarityColor() + reward.getAmount() + "x " +
                            reward.getMaterial().name());
                }
            }
        }
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l=== SLAYER COMMANDS ===");
        player.sendMessage("§a/slayer §7- Open slayer GUI menu");
        player.sendMessage("§a/slayer top §7- View slayer leaderboards");
        player.sendMessage("§a/slayer achievements §7- View achievement progress");
        player.sendMessage("§a/slayer start <tier> §7- Start a slayer quest");
        player.sendMessage("§a/slayer progress §7- View current quest progress");
        player.sendMessage("§a/slayer abandon §7- Abandon current quest");
        player.sendMessage("§a/slayer tokens §7- View Slayer Token Leaderboards");
        player.sendMessage("");
        player.sendMessage("§7Example: §a/slayer start zombie_i");

        plugin.getSlayerGUI().open(player);
    }

    private void handleAdminCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /slayer admin <giveprogress|spawn> <tier>");
            return;
        }

        String sub = args[1].toLowerCase();

        if (sub.equals("giveprogress")) {
            try {
                SlayerTier tier = SlayerTier.valueOf(args[2].toUpperCase());
                for (int i = 0; i < 50; i++) {
                    plugin.getBossManager().recordSlayerKill(player, tier.getTargetEntity());
                }
                player.sendMessage("§aGave progress toward " + tier.getDisplayName());
            } catch (Exception e) {
                player.sendMessage("§cInvalid tier.");
            }
        } else if (sub.equals("spawn")) {
            player.sendMessage("§7Boss spawning admin tools coming soon.");
        }
    }
}