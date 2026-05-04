package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class BalanceCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public BalanceCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            // Show own balance
            double balance = plugin.getEconomyManager().getPlayerBalance(player.getUniqueId()).join();
            player.sendMessage("§aYour balance: §e$" + String.format("%,.2f", balance));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "top":
                player.sendMessage("§6=== Top Balances ===");
                plugin.getDatabaseManager().getTopBalances(10).thenAccept(topList -> {
                    for (int i = 0; i < topList.size(); i++) {
                        DatabaseManager.TopBalanceEntry entry = topList.get(i);
                        String playerName = Bukkit.getOfflinePlayer(entry.uuid()).getName();
                        if (playerName == null) playerName = "Unknown";
                        player.sendMessage("§e" + (i + 1) + ". §f" + playerName + " §7- §a$" + String.format("%,.2f", entry.balance()));
                    }
                });
                break;

            case "island":
                plugin.getIslandManager().getIslandByOwner(player.getUniqueId(), org.bukkit.World.Environment.NORMAL)
                        .thenAccept(island -> {
                            if (island != null) {
                                plugin.getEconomyManager().getIslandBalance(island.getGridPosition())
                                        .thenAccept(balance -> {
                                            player.sendMessage("§aYour island balance: §e$" + String.format("%,.2f", balance));
                                        });
                            } else {
                                player.sendMessage("§cYou don't have an island!");
                            }
                        });
                break;

            default:
                // Try to find another player's balance
                UUID targetUuid = null;
                if (args.length > 0) {
                    targetUuid = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
                }

                if (targetUuid != null) {
                    double balance = plugin.getEconomyManager().getPlayerBalance(targetUuid).join();
                    String name = Bukkit.getOfflinePlayer(targetUuid).getName();
                    player.sendMessage("§a" + name + "'s balance: §e$" + String.format("%,.2f", balance));
                } else {
                    player.sendMessage("§cUsage: /balance [top|island|player]");
                }
                break;
        }

        return true;
    }
}