package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.island.Island;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
            showPlayerBalance(player, player.getUniqueId());
            return true;
        }

        // /bal <player>
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target != null) {
            showPlayerBalance(player, target.getUniqueId());
        } else {
            player.sendMessage("§cPlayer not found or is offline.");
        }

        return true;
    }

    private void showPlayerBalance(Player viewer, UUID targetUuid) {
        plugin.getDatabaseManager().getPlayerBalance(targetUuid)
                .thenAccept(balance -> {
                    double playerBal = balance != null ? balance : 0.0;

                    viewer.sendMessage("§6§l=== Balances ===");
                    viewer.sendMessage("§7Player Balance: §a$" + String.format("%,.2f", playerBal));

                    // Get island balance asynchronously
                    plugin.getIslandManager().getIslandByOwner(targetUuid, viewer.getWorld().getEnvironment())
                            .thenAccept(island -> {
                                if (island != null) {
                                    viewer.sendMessage("§7Island Balance: §a$" + String.format("%,.2f", island.getBalance()));
                                } else {
                                    viewer.sendMessage("§7Island Balance: §cNo island in this dimension");
                                }
                            });
                });
    }
}