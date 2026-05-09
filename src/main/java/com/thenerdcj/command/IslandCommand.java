package com.thenerdcj.command;
import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandRank;
import com.thenerdcj.island.IslandUpgrade;
import com.thenerdcj.island.IslandUpgradeRecommender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class IslandCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;
    private static final List<String> ALLOWED_BIOMES = Arrays.asList(
            "PLAINS", "FOREST", "DESERT", "TAIGA", "JUNGLE"
    );

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
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reset":
                if (player.hasPermission("foliasb.donor") || player.hasPermission("foliasb.donor.biome")) {
                    // Open confirmation GUI (with live balance)
                    plugin.getResetConfirmationGUI().open(player);
                } else {
                    // Non-donors get simple reset to default biome
                    plugin.getIslandManager().resetIsland(player, player.getWorld().getEnvironment());
                }
                return true;
            case "create":
                handleCreate(player, args);
                break;

            case "home":
                handleHome(player);
                break;

            case "tp":
            case "teleport":
                if (args.length > 1) handleTeleport(player, args[1]);
                else player.sendMessage("§cUsage: /island tp <player>");
                break;

            case "invite":
                if (args.length > 1) handleInvite(player, args[1]);
                else player.sendMessage("§cUsage: /island invite <player>");
                break;

            case "accept":
                handleAccept(player);
                break;

            case "kick":
                if (args.length > 1) handleKick(player, args[1]);
                else player.sendMessage("§cUsage: /island kick <player>");
                break;

            case "rank":
                handleRank(player, args);
                break;

            case "top":
                handleTop(player);
                break;

            case "setspawn":
                handleSetSpawn(player);
                break;

            case "upgrade":
                handleUpgrade(player, args);
                break;

            case "trade":
                handleTrade(player);
                break;

            case "bank":
                handleBank(player);
                break;

            case "quests":
                handleQuests(player);
                break;

            case "warp":
                if (args.length > 1) handleWarp(player, args[1]);
                else player.sendMessage("§cUsage: /island warp <player>");
                break;

            case "setwarp":
                handleSetWarp(player);
                break;

            case "chat":
                handleChat(player);
                break;

            case "settings":
                handleSettings(player);
                break;

            case "browse":
                handleBrowse(player);
                break;

            default:
                player.sendMessage("§cUnknown subcommand. Use §b/island§c for help.");
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6╔══════════════════════════════════════╗");
        player.sendMessage("§6║          §eFoliaSkyblock Help          §6║");
        player.sendMessage("§6╚══════════════════════════════════════╝");
        player.sendMessage("§e/island create [biome] §7- Create new island (donors can choose biome)");
        player.sendMessage("§e/island home §7- Teleport to your island");
        player.sendMessage("§e/island tp <player> §7- Teleport to another player's island");
        player.sendMessage("§e/island invite <player> §7- Invite player to your island");
        player.sendMessage("§e/island accept §7- Accept island invite");
        player.sendMessage("§e/island kick <player> §7- Kick member from island");
        player.sendMessage("§e/island rank <player> <rank> §7- Set member rank (GUEST/HELPER/MODERATOR)");
        player.sendMessage("§e/island top §7- View top islands leaderboard");
        player.sendMessage("§e/island setspawn §7- Set your island spawn point");
        player.sendMessage("§e/island upgrade [UPGRADE] §7- View or purchase island upgrades (AI recommendations)");
        player.sendMessage("§e/island trade §7- Open the island trade shop (uses island balance)");
        player.sendMessage("§e/island settings §7- Open island settings GUI (PvP, visitors, etc.)");
        player.sendMessage("§e/island bank §7- Open island bank (deposit/withdraw money)");
        player.sendMessage("§e/island browse §7- Browse public islands (sorted by popularity)");
        player.sendMessage("§e/island quests §7- Open quest log (daily/weekly missions)");
        player.sendMessage("§e/island warp <player> §7- Teleport to another player's island warp");
        player.sendMessage("§e/island setwarp §7- Set your island's warp location");
        player.sendMessage("§e/island chat §7- Toggle island-only chat");
    }

    private void handleCreate(Player player, String[] args) {
        boolean isDonor = player.hasPermission("foliasb.donor") || player.hasPermission("foliasb.create.biome");

        if (args.length >= 2 && isDonor) {
            String biomeName = args[1].toUpperCase();

            if (biomeName.equals("NETHER_WASTES") || biomeName.equals("THE_END")) {
                player.sendMessage("§cNether and End islands are §lprogression-locked§c!");
                player.sendMessage("§7• Nether: Build a portal on your island");
                player.sendMessage("§7• End: Fall through the void in the End dimension");
                return;
            }

            if (!ALLOWED_BIOMES.contains(biomeName)) {
                player.sendMessage("§cInvalid biome. Allowed: §e" + String.join(", ", ALLOWED_BIOMES));
                return;
            }
            plugin.getIslandManager().createIsland(player, biomeName, World.Environment.NORMAL);
            player.sendMessage("§aCreating your §e" + biomeName + "§a island...");
        } else if (isDonor) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                com.thenerdcj.gui.BiomeSelectionGUI gui = new com.thenerdcj.gui.BiomeSelectionGUI(plugin);
                gui.open(player);
            });
        } else {
            plugin.getIslandManager().createIsland(player, "PLAINS", World.Environment.NORMAL);
            player.sendMessage("§aCreating your island...");
        }
    }

    private void handleHome(Player player) {
        plugin.getIslandManager().teleportToIsland(player, player.getUniqueId());
    }

    private void handleTeleport(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }
        plugin.getIslandManager().teleportToIsland(player, target.getUniqueId());
    }

    private void handleInvite(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }
        plugin.getIslandManager().inviteToParty(player, target);
    }

    private void handleAccept(Player player) {
        plugin.getIslandManager().acceptPartyInvite(player);
    }

    private void handleKick(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }
        plugin.getIslandManager().removeMemberFromIsland(player.getUniqueId(), target.getUniqueId());
    }

    private void handleRank(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /island rank <player> <GUEST|HELPER|MODERATOR>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return;
        }
        try {
            IslandRank rank = IslandRank.valueOf(args[2].toUpperCase());
            plugin.getIslandManager().setMemberRank(player.getUniqueId(), target.getUniqueId(), rank);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid rank! Use: GUEST, HELPER, MODERATOR");
        }
    }

    private void handleTop(Player player) {
        player.sendMessage("§6§lTop Islands:");
        player.sendMessage("§71. §eExampleIsland §7- Level 45");
        player.sendMessage("§72. §eAnotherIsland §7- Level 38");
    }

    private void handleSetSpawn(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cYou don't have an island in this dimension.");
            return;
        }

        if (!island.isOwner(player.getUniqueId())) {
            player.sendMessage("§cOnly the island owner can set the spawn point.");
            return;
        }

        player.sendMessage("§aIsland spawn point set to your current location!");
    }

    private void handleUpgrade(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());

        if (island == null) {
            player.sendMessage("§cYou don't have an island! Use §b/island create§c first.");
            return;
        }

        if (args.length < 2) {
            // Show AI recommendations
            player.sendMessage("§6§l╔══════════════════════════════════════╗");
            player.sendMessage("§6§l║     §eIsland Upgrade Recommendations   §6§l║");
            player.sendMessage("§6§l╚══════════════════════════════════════╝");

            IslandUpgradeRecommender recommender = new IslandUpgradeRecommender(plugin);
            List<IslandUpgradeRecommender.UpgradeRecommendation> recommendations =
                    recommender.getRecommendations(player, island);

            if (recommendations.isEmpty()) {
                player.sendMessage("§7No recommendations available. All upgrades are maxed!");
                return;
            }

            for (IslandUpgradeRecommender.UpgradeRecommendation rec : recommendations) {
                int cost = rec.upgrade.getCostForLevel(rec.currentLevel);
                player.sendMessage(String.format(
                        "§b%s §7(Level %d → %d) §6$%,d §7- %s",
                        rec.upgrade.name(),
                        rec.currentLevel,
                        rec.recommendedLevel,
                        cost,
                        rec.reason
                ));
            }

            player.sendMessage("§7Use §b/island upgrade <UPGRADE>§7 to purchase");
            return;
        }

        // Purchase specific upgrade
        String upgradeName = args[1].toUpperCase();
        try {
            IslandUpgrade upgrade = IslandUpgrade.valueOf(upgradeName);
            plugin.getIslandUpgradeManager().purchaseUpgrade(player, island, upgrade);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid upgrade! Use §b/island upgrade§c to see available options.");
        }
    }

    private void handleTrade(Player player) {
        plugin.getTradeGUI().openTradeGUI(player);
    }

    private void handleSettings(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());

        if (island == null) {
            player.sendMessage("§cYou don't have an island! Use §b/island create§c first.");
            return;
        }

        if (!island.isOwner(player.getUniqueId())) {
            player.sendMessage("§cOnly the island owner can access settings.");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            com.thenerdcj.gui.IslandSettingsGUI gui = new com.thenerdcj.gui.IslandSettingsGUI(plugin);
            gui.open(player, island);
        });
    }

    private void handleBank(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());

        if (island == null) {
            player.sendMessage("§cYou don't have an island! Use §b/island create§c first.");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            com.thenerdcj.gui.IslandBankGUI gui = new com.thenerdcj.gui.IslandBankGUI(plugin);
            gui.open(player, island);
        });
    }

    private void handleQuests(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());

        if (island == null) {
            player.sendMessage("§cYou don't have an island! Use §b/island create§c first.");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            com.thenerdcj.gui.QuestLogGUI gui = new com.thenerdcj.gui.QuestLogGUI(plugin);
            gui.open(player, island.getGridPosition().toString());
        });
    }

    private void handleWarp(Player player, String targetName) {
        org.bukkit.entity.Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§cPlayer not found or not online.");
            return;
        }

        Island targetIsland = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
        if (targetIsland == null) {
            player.sendMessage("§cThat player doesn't have an island.");
            return;
        }

        plugin.getIslandWarpManager().getWarp(targetIsland.getGridPosition()).thenAccept(warp -> {
            if (!warp.isEnabled() || warp.getWarpLocation() == null) {
                player.sendMessage("§cThat island doesn't have a warp set.");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(warp.getWarpLocation());
                player.sendMessage("§aTeleported to §e" + target.getName() + "§a's island warp!");
            });
        });
    }

    private void handleSetWarp(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());

        if (island == null) {
            player.sendMessage("§cYou don't have an island! Use §b/island create§c first.");
            return;
        }

        if (!island.isOwner(player.getUniqueId())) {
            player.sendMessage("§cOnly the island owner can set the warp.");
            return;
        }

        plugin.getIslandWarpManager().setWarp(island.getGridPosition(), player.getLocation()).thenAccept(success -> {
            if (success) {
                player.sendMessage("§aIsland warp set to your current location!");
            } else {
                player.sendMessage("§cFailed to set warp. Please try again.");
            }
        });
    }

    private void handleChat(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());

        if (island == null) {
            player.sendMessage("§cYou don't have an island! Use §b/island create§c first.");
            return;
        }

        plugin.getIslandChatManager().toggleIslandChat(player);
    }

    private void handleBrowse(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            com.thenerdcj.gui.IslandBrowseGUI gui = new com.thenerdcj.gui.IslandBrowseGUI(plugin);
            gui.open(player, 0);
        });
    }
}