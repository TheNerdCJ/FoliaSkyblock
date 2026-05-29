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
}
