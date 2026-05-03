package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.auction.Auction;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AuctionCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public AuctionCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§6=== Auction Commands ===");
            player.sendMessage("§e/auction create <price> §7- List held item for auction");
            player.sendMessage("§e/auction bid <id> <amount> §7- Place a bid");
            player.sendMessage("§e/auction list §7- View active auctions");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /auction create <starting price>");
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[1]);
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item.getType().isAir()) {
                        player.sendMessage("§cYou must hold an item to auction!");
                        return true;
                    }
                    plugin.getAuctionManager().createAuction(player, item, price);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid price!");
                }
                break;

            case "bid":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /auction bid <auction id> <amount>");
                    return true;
                }
                try {
                    String auctionId = args[1];
                    double bidAmount = Double.parseDouble(args[2]);
                    plugin.getAuctionManager().placeBid(player, auctionId, bidAmount);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid bid amount!");
                }
                break;

            case "list":
                player.sendMessage("§6=== Active Auctions ===");
                for (Auction auction : plugin.getAuctionManager().getActiveAuctions().values()) {
                    String timeLeft = formatTime(auction.getTimeRemaining());
                    player.sendMessage("§e" + auction.getId() + " §7- §b" + auction.getItemAmount() + "x " +
                            auction.getItemMaterial() + " §7@ §a$" + String.format("%,.0f", auction.getCurrentBid()) +
                            " §7(" + timeLeft + " left)");
                }
                break;

            default:
                player.sendMessage("§cUnknown subcommand. Use /auction for help.");
        }

        return true;
    }

    private String formatTime(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis % (1000 * 60 * 60)) / (1000 * 60);
        return hours + "h " + minutes + "m";
    }
}
