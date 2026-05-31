package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Admin commands for fixing broken islands, balances, and data.
 * Permission: foliasb.admin
 */
public class AdminCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public AdminCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foliasb.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eAdmin Commands:");
            sender.sendMessage("§7/isadmin reset <player> [overworld|nether|end]");
            sender.sendMessage("§7/isadmin setlevel <player> <level> [dimension]");
            sender.sendMessage("§7/isadmin setbalance <player> <amount>");
            sender.sendMessage("§7/isadmin setislandbalance <player> <amount> [dimension]");
            sender.sendMessage("§7/isadmin givepending <player>");
            sender.sendMessage("§7/isadmin fixdata <player>");
            sender.sendMessage("§7/isadmin wardrobe give <player> <levels>");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reset":
                handleIslandReset(sender, args);
                break;

            case "setlevel":
                handleSetLevel(sender, args);
                break;

            // case "mission":
            //     handleMissionCommand(sender, args);
            //     break;

            case "booster":
                handleBoosterCommand(sender, args);
                break;
            case "border":
                handleBorderCommand(sender, args);
                break;
            case "slayer":
                handleSlayerAdminCommand(sender, args);
                break;

            case "setbalance":
                handleSetPlayerBalance(sender, args);
                break;

            case "setislandbalance":
                handleSetIslandBalance(sender, args);
                break;

            case "givepending":
                handleGivePending(sender, args);
                break;

            case "fixdata":
                handleFixData(sender, args);
                break;

            case "wardrobe":
                handleWardrobe(sender, args);
                break;

            default:
                sender.sendMessage("§cUnknown admin subcommand. Use /isadmin for help.");
        }

        return true;
    }

    private void handleIslandReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /isadmin reset <player> [overworld|nether|end]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or offline.");
            return;
        }

        World.Environment dimension = World.Environment.NORMAL;
        if (args.length >= 3) {
            try {
                dimension = World.Environment.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid dimension. Use: overworld, nether, end");
                return;
            }
        }

        plugin.getIslandManager().resetIslandWithBiome(target, null, dimension);
        sender.sendMessage("§aReset " + target.getName() + "'s island in " + dimension.name());
        target.sendMessage("§cYour island in " + dimension.name() + " has been reset by an admin.");
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /isadmin setlevel <player> <level> [overworld|nether|end]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid level number.");
            return;
        }

        World.Environment dim = World.Environment.NORMAL;
        if (args.length >= 4) {
            dim = World.Environment.valueOf(args[3].toUpperCase());
        }

        Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), dim);
        if (island != null) {
            island.setLevel(level);
            sender.sendMessage("§aSet " + target.getName() + "'s island level to " + level + " in " + dim.name());
        } else {
            sender.sendMessage("§cPlayer does not have an island in that dimension.");
        }
    }

    private void handleSetPlayerBalance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /isadmin setbalance <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount.");
            return;
        }

        plugin.getEconomyManager().setPlayerBalance(target.getUniqueId(), amount);
        sender.sendMessage("§aSet " + target.getName() + "'s balance to §e$" + amount);
    }

    private void handleSetIslandBalance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /isadmin setislandbalance <player> <amount> [dimension]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount.");
            return;
        }

        World.Environment dim = World.Environment.NORMAL;
        if (args.length >= 4) {
            dim = World.Environment.valueOf(args[3].toUpperCase());
        }

        Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), dim);
        if (island != null) {
            GridPosition pos = island.getGridPosition();
            plugin.getEconomyManager().setIslandBalance(pos, amount);
            sender.sendMessage("§aSet island balance for " + target.getName() + " to §e$" + amount);
        } else {
            sender.sendMessage("§cPlayer has no island in " + dim.name());
        }
    }

    private void handleGivePending(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /isadmin givepending <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        plugin.getDatabaseManager().getPendingItems(target.getUniqueId()).thenAccept(items -> {
            // Must give items on main thread
            plugin.getThreadSafety().runOnMainThread(() -> {
                for (var item : items) {
                    target.getInventory().addItem(item);
                }
                sender.sendMessage("§aGave pending items to " + target.getName());
                if (target.isOnline()) {
                    target.sendMessage("§aYou received your pending items from admin.");
                }
            });
        });
    }

    private void handleFixData(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /isadmin fixdata <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        // Reload island data
        plugin.getIslandManager().loadPlayerIslands(target);
        sender.sendMessage("§aReloaded island data for " + target.getName());
    }

    private void handleWardrobe(CommandSender sender, String[] args) {
        if (args.length < 3 || !"give".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§cUsage: /isadmin wardrobe give <player> <levels>");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or offline.");
            return;
        }

        int levels;
        try {
            levels = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number of levels.");
            return;
        }

        if (levels <= 0) {
            sender.sendMessage("§cLevels must be positive.");
            return;
        }

        // Apply to all dimensions the player has islands in (or at least main one)
        boolean applied = false;
        for (World.Environment dim : new World.Environment[]{World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END}) {
            Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), dim);
            if (island != null) {
                int current = island.getUpgradeLevel(com.thenerdcj.island.IslandUpgrade.WARDROBE_SLOTS);
                island.setUpgradeLevel(com.thenerdcj.island.IslandUpgrade.WARDROBE_SLOTS, current + levels);
                applied = true;
            }
        }

        if (applied) {
            sender.sendMessage("§aGave " + levels + " WARDROBE_SLOTS levels to " + target.getName() + "'s island(s).");
            target.sendMessage("§aAn admin has increased your wardrobe slot capacity!");
        } else {
            sender.sendMessage("§cPlayer has no islands to apply wardrobe upgrade to.");
        }
    }

    private void handleBoosterCommand(CommandSender sender, String[] args) {
        if (args.length < 5 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§cUsage: /isadmin booster give <player> <type> <minutes>");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        try {
            com.thenerdcj.booster.BoosterType type = com.thenerdcj.booster.BoosterType.valueOf(args[3].toUpperCase());
            long minutes = Long.parseLong(args[4]);
            long duration = minutes * 60 * 1000;

            Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
            if (island == null) {
                sender.sendMessage("§cPlayer has no island in current dimension.");
                return;
            }

            double multiplier = plugin.getConfig().getDouble("boosters.multipliers." + type.name(), 1.5);
            plugin.getBoosterManager().activateBooster(island, type, multiplier, duration);

            sender.sendMessage("§aGave " + type.name() + " booster to " + target.getName() + " for " + minutes + " minutes.");
            if (target.isOnline()) {
                target.sendMessage("§aAn admin has activated a " + type.getDisplayName() + " booster for you!");
            }
        } catch (Exception e) {
            sender.sendMessage("§cInvalid booster type or duration.");
        }
    }

    private void handleBorderCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /isadmin border <setsize|setcolor> <player> <value>");
            return;
        }

        String sub = args[1].toLowerCase();
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /isadmin border " + sub + " <player> <value>");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
        if (island == null) {
            sender.sendMessage("§cPlayer has no island in their current dimension.");
            return;
        }

        GridPosition pos = island.getGridPosition();

        try {
            if (sub.equals("setsize")) {
                int newSize = Integer.parseInt(args[3]);
                plugin.getIslandSettingsManager().getSettings(pos).thenAccept(settings -> {
                    settings.setBorderSize(newSize);
                    plugin.getIslandSettingsManager().saveSettings(settings);

                    // Also update effective radius display
                    plugin.getThreadSafety().runOnMainThread(() -> {
                        sender.sendMessage("§aSet border size for " + target.getName() + "'s island to §b" + newSize + " blocks.");
                        if (target.isOnline()) {
                            target.sendMessage("§aAn admin has changed your island border size to §b" + newSize + " blocks.");
                        }
                    });
                });
            } else if (sub.equals("setcolor")) {
                String color = args[3].toUpperCase();
                plugin.getIslandSettingsManager().getSettings(pos).thenAccept(settings -> {
                    settings.setBorderColor(color);
                    plugin.getIslandSettingsManager().saveSettings(settings);

                    plugin.getThreadSafety().runOnMainThread(() -> {
                        sender.sendMessage("§aSet border color for " + target.getName() + "'s island to §b" + color + ".");
                        if (target.isOnline()) {
                            target.sendMessage("§aAn admin has changed your island border color to §b" + color + ".");
                        }
                    });
                });
            } else {
                sender.sendMessage("§cUnknown border subcommand. Use setsize or setcolor.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number for size.");
        }
    }

    private void handleSlayerAdminCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /isadmin slayer <giveTokens|resetLeaderboard|forceBoss> <player|all>");
            return;
        }

        String sub = args[1].toLowerCase();

        if (sub.equals("givetokens")) {
            if (args.length < 4) {
                sender.sendMessage("§cUsage: /isadmin slayer giveTokens <player> <amount>");
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return;
            }
            try {
                int amt = Integer.parseInt(args[3]);
                plugin.getBossManager().awardSlayerTokens(target, amt);
                sender.sendMessage("§aGave " + amt + " Slayer Tokens to " + target.getName());
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid amount.");
            }
        } else if (sub.equals("resetleaderboard")) {
            plugin.getBossManager().checkAndResetTokenLeaderboard();
            plugin.getDatabaseManager().resetWeeklySlayerTokens();
            sender.sendMessage("§aSlayer Token leaderboard reset.");
        } else if (sub.equals("forceboss")) {
            sender.sendMessage("§7Force island boss (advanced admin feature).");
        }
    }
}
