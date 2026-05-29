package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.bazaar.BazaarItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BazaarCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public BazaarCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            // Open the full GUI by default (standard in most Skyblock Bazaar recreations)
            plugin.getBazaarGUI().openMainBazaar(player);
            return true;
        }

        // Text commands for power users when arguments are provided
        if (args.length > 0) {
            String sub = args[0].toLowerCase();

            switch (sub) {
                case "buy":
                    if (args.length < 3) {
                        player.sendMessage("§cUsage: /bazaar buy <material> <amount>");
                        return true;
                    }
                    try {
                        String material = args[1].toUpperCase();
                        int amount = Integer.parseInt(args[2]);
                        plugin.getBazaarManager().instantBuy(player, material, amount);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid amount!");
                    }
                    break;

                case "sell":
                    if (args.length < 3) {
                        player.sendMessage("§cUsage: /bazaar sell <material> <amount>");
                        return true;
                    }
                    try {
                        String material = args[1].toUpperCase();
                        int amount = Integer.parseInt(args[2]);
                        plugin.getBazaarManager().instantSell(player, material, amount);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid amount!");
                    }
                    break;

                case "orders":
                    if (args.length < 2) {
                        player.sendMessage("§cUsage: /bazaar orders <material>");
                        return true;
                    }
                    String material = args[1].toUpperCase();
                    player.sendMessage("§6=== Buy Orders for " + material + " ===");
                    for (com.thenerdcj.bazaar.BazaarOrder order : plugin.getBazaarManager().getBuyOrders(material)) {
                        player.sendMessage("§a" + order.getAmount() + "x @ $" + String.format("%.2f", order.getPricePerUnit()));
                    }
                    player.sendMessage("§6=== Sell Orders for " + material + " ===");
                    for (com.thenerdcj.bazaar.BazaarOrder order : plugin.getBazaarManager().getSellOrders(material)) {
                        player.sendMessage("§c" + order.getAmount() + "x @ $" + String.format("%.2f", order.getPricePerUnit()));
                    }
                    break;

                case "createbuy":
                    if (args.length < 4) {
                        player.sendMessage("§cUsage: /bazaar createbuy <material> <amount> <price per unit>");
                        return true;
                    }
                    try {
                        String mat = args[1].toUpperCase();
                        int amt = Integer.parseInt(args[2]);
                        double price = Double.parseDouble(args[3]);
                        plugin.getBazaarManager().createBuyOrder(player, mat, amt, price);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid number format!");
                    }
                    break;

                case "createsell":
                    if (args.length < 4) {
                        player.sendMessage("§cUsage: /bazaar createsell <material> <amount> <price per unit>");
                        return true;
                    }
                    try {
                        String mat = args[1].toUpperCase();
                        int amt = Integer.parseInt(args[2]);
                        double price = Double.parseDouble(args[3]);
                        plugin.getBazaarManager().createSellOrder(player, mat, amt, price);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid number format!");
                    }
                    break;

                default:
                    player.sendMessage("§cUnknown subcommand. Use /bazaar for help.");
            }
        }

        return true;
    }
}
