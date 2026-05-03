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
            plugin.getEconomyManager().getPlayerBalance(player.getUniqueId())
                    .thenAccept(balance -> {
                        player.sendMessage("§aYour balance: §e$" + String.format("%,.2f", balance));

                        // Also show island balance if they have an island
                        plugin.getIslandManager().getIslandByOwner(player.getUniqueId(), org.bukkit.World.Environment.NORMAL)
                                .thenAccept(island -> {
                                    if (island != null) {
                                        plugin.getEconomyManager().getIslandBalance(island.getGridPosition())
                                                .thenAccept(islandBalance -> {
                                                    player.sendMessage("§aIsland balance: §e$" + String.format("%,.2f", islandBalance));
                                                });
                                    }
                                });
                    });
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "top":
                player.sendMessage("§6=== Top Balances ===");
                List<DatabaseManager.TopBalanceEntry> topList = plugin.getDatabaseManager().getTopBalances(10);
                for (int i = 0; i < topList.size(); i++) {
                    DatabaseManager.TopBalanceEntry entry = topList.get(i);
                    String playerName = Bukkit.getOfflinePlayer(entry.uuid()).getName();
                    if (playerName == null) playerName = "Unknown";
                    player.sendMessage("§e" + (i + 1) + ". §f" + playerName + " §7- §a$" + String.format("%,.2f", entry.balance()));
                }
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
                // Check another player's balance
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null) {
                    plugin.getEconomyManager().getPlayerBalance(target.getUniqueId())
                            .thenAccept(balance -> {
                                player.sendMessage("§a" + target.getName() + "'s balance: §e$" + String.format("%,.2f", balance));
                            });
                } else {
                    player.sendMessage("§cPlayer not found: " + args[0]);
                }
                break;
        }

        return true;
    }
}