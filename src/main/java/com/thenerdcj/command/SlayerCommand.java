package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.SlayerTier;
import com.thenerdcj.util.MessageUtil;
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
            MessageUtil.sendMessage(sender, "§cOnly players can use this command!");
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
                    MessageUtil.sendMessage(player, "§cNo permission.");
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
                MessageUtil.sendMessage(player, "§aSlayer quest started! Kill " + tier.getTargetEntity().name() + "s to progress.");
            }
        } catch (IllegalArgumentException e) {
            MessageUtil.sendMessage(player, "§cInvalid tier. Use /slayer for help. New tiers include skeleton, creeper, and boss slayers!");
        }
    }

    private void showProgress(Player player) {
        var quest = plugin.getBossManager().getActiveSlayerQuest(player);

        if (quest == null) {
            MessageUtil.sendMessage(player, "§7You don't have an active slayer quest.");
            return;
        }

        MessageUtil.sendMessage(player, "§6§l=== ACTIVE SLAYER QUEST ===");
        MessageUtil.sendMessage(player, "§e" + quest.getTier().getDisplayName());
        MessageUtil.sendMessage(player, "§7Progress: §e" + quest.getKills() + "§7/§e" + quest.getKillsRequired() +
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
            MessageUtil.sendMessage(player, "§cSlayer quest abandoned.");
        } else {
            MessageUtil.sendMessage(player, "§7You don't have an active slayer quest.");
        }
    }

    private void showRewards(Player player) {
        MessageUtil.sendMessage(player, "§6§l=== SLAYER REWARDS PREVIEW ===");
        for (SlayerTier tier : SlayerTier.values()) {
            if (tier.getTier() == 1) {
                MessageUtil.sendMessage(player, "§e" + tier.getDisplayName());
                for (var reward : tier.getRewards()) {
                    MessageUtil.sendMessage(player, "  " + reward.getRarityColor() + reward.getAmount() + "x " +
                            reward.getMaterial().name());
                }
            }
        }
    }

    private void showHelp(Player player) {
        MessageUtil.sendMessage(player, "§6§l=== SLAYER COMMANDS ===");
        MessageUtil.sendMessage(player, "§a/slayer §7- Open slayer GUI menu");
        MessageUtil.sendMessage(player, "§a/slayer top §7- View slayer leaderboards");
        MessageUtil.sendMessage(player, "§a/slayer achievements §7- View achievement progress");
        MessageUtil.sendMessage(player, "§a/slayer start <tier> §7- Start a slayer quest");
        MessageUtil.sendMessage(player, "§a/slayer progress §7- View current quest progress");
        MessageUtil.sendMessage(player, "§a/slayer abandon §7- Abandon current quest");
        MessageUtil.sendMessage(player, "§a/slayer tokens §7- View Slayer Token Leaderboards");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "§7Example: §a/slayer start zombie_i");

        plugin.getSlayerGUI().open(player);
    }

    private void handleAdminCommand(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(player, "§cUsage: /slayer admin <giveprogress|spawn> <tier>");
            return;
        }

        String sub = args[1].toLowerCase();

        if (sub.equals("giveprogress")) {
            try {
                SlayerTier tier = SlayerTier.valueOf(args[2].toUpperCase());
                for (int i = 0; i < 50; i++) {
                    plugin.getBossManager().recordSlayerKill(player, tier.getTargetEntity());
                }
                MessageUtil.sendMessage(player, "§aGave progress toward " + tier.getDisplayName());
            } catch (Exception e) {
                MessageUtil.sendMessage(player, "§cInvalid tier.");
            }
        } else if (sub.equals("spawn")) {
            MessageUtil.sendMessage(player, "§7Boss spawning admin tools coming soon.");
        }
    }
}