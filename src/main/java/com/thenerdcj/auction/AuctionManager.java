package com.thenerdcj.auction;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.auction.Auction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionManager {
    private final FoliaSkyblock plugin;
    private final Map<String, Auction> activeAuctions = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> playerAuctions = new ConcurrentHashMap<>();

    private static final long AUCTION_DURATION = 24 * 60 * 60 * 1000; // 24 hours
    private static final double AUCTION_TAX = 0.05; // 5% tax on sale

    public AuctionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadActiveAuctions();

        // Check for expired auctions every minute
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkExpiredAuctions, 20L * 60, 20L * 60);
    }

    private void loadActiveAuctions() {
        plugin.getDatabaseManager().getActiveAuctions().thenAccept(auctions -> {
            for (Auction auction : auctions) {
                if (!auction.isExpired()) {
                    activeAuctions.put(auction.getId(), auction);
                }
            }
            plugin.getLogger().info("§aLoaded " + activeAuctions.size() + " active auctions");
        });
    }

    public CompletableFuture<String> createAuction(Player seller, ItemStack item, double startingPrice) {
        return CompletableFuture.supplyAsync(() -> {
            if (item == null || item.getType() == Material.AIR) {
                seller.sendMessage("§cYou must hold an item to auction!");
                return null;
            }

            if (startingPrice < 1) {
                seller.sendMessage("§cStarting price must be at least $1!");
                return null;
            }

            if (!seller.getInventory().containsAtLeast(item, item.getAmount())) {
                seller.sendMessage("§cYou don't have enough items!");
                return null;
            }

            seller.getInventory().removeItem(item);

            String auctionId = UUID.randomUUID().toString().substring(0, 8);
            long endTime = System.currentTimeMillis() + AUCTION_DURATION;

            Auction auction = new Auction(
                    auctionId, seller.getUniqueId(), item.getType().name(), item.getAmount(),
                    startingPrice, startingPrice, null, endTime, true
            );

            activeAuctions.put(auctionId, auction);
            playerAuctions.computeIfAbsent(seller.getUniqueId(), k -> new ArrayList<>()).add(auctionId);

            plugin.getDatabaseManager().saveAuction(auction);

            Bukkit.broadcastMessage("§6§l[AUCTION] §e" + seller.getName() + " §7listed §b" +
                    item.getAmount() + "x " + item.getType().name() + " §7for §a$" + String.format("%,.0f", startingPrice));

            return auctionId;
        });
    }

    public CompletableFuture<Boolean> placeBid(Player bidder, String auctionId, double bidAmount) {
        return CompletableFuture.supplyAsync(() -> {
            Auction auction = activeAuctions.get(auctionId);

            if (auction == null) {
                bidder.sendMessage("§cAuction not found!");
                return false;
            }

            if (auction.isExpired()) {
                bidder.sendMessage("§cThis auction has ended!");
                return false;
            }

            if (bidder.getUniqueId().equals(auction.getSellerUuid())) {
                bidder.sendMessage("§cYou cannot bid on your own auction!");
                return false;
            }

            if (bidAmount <= auction.getCurrentBid()) {
                bidder.sendMessage("§cBid must be higher than current bid of §e$" + String.format("%,.0f", auction.getCurrentBid()));
                return false;
            }

            double balance = plugin.getEconomyManager().getPlayerBalance(bidder.getUniqueId()).join();
            if (balance < bidAmount) {
                bidder.sendMessage("§cYou don't have enough money! Need §e$" + String.format("%,.0f", bidAmount));
                return false;
            }

            if (auction.getCurrentBidder() != null) {
                plugin.getEconomyManager().addPlayerBalance(auction.getCurrentBidder(), auction.getCurrentBid());
                Player previousBidder = Bukkit.getPlayer(auction.getCurrentBidder());
                if (previousBidder != null) {
                    previousBidder.sendMessage("§eYour bid on §b" + auction.getItemMaterial() + " §ewas outbid!");
                }
            }

            plugin.getEconomyManager().removePlayerBalance(bidder.getUniqueId(), bidAmount);

            Auction updatedAuction = new Auction(
                    auction.getId(), auction.getSellerUuid(), auction.getItemMaterial(), auction.getItemAmount(),
                    auction.getStartingPrice(), bidAmount, bidder.getUniqueId(), auction.getEndTime(), true
            );

            activeAuctions.put(auctionId, updatedAuction);
            plugin.getDatabaseManager().updateAuction(updatedAuction);

            bidder.sendMessage("§aBid placed! §e$" + String.format("%,.0f", bidAmount) + " §aon §b" + auction.getItemMaterial());

            return true;
        });
    }

    private void checkExpiredAuctions() {
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, Auction> entry : activeAuctions.entrySet()) {
            Auction auction = entry.getValue();

            if (auction.isExpired()) {
                toRemove.add(entry.getKey());
                endAuction(auction);
            }
        }

        for (String id : toRemove) {
            activeAuctions.remove(id);
        }
    }

    private void endAuction(Auction auction) {
        if (auction.getCurrentBidder() != null) {
            Player winner = Bukkit.getPlayer(auction.getCurrentBidder());
            ItemStack item = new ItemStack(Material.valueOf(auction.getItemMaterial()), auction.getItemAmount());

            if (winner != null && winner.isOnline()) {
                winner.getInventory().addItem(item);
                winner.sendMessage("§a§lYou won the auction! §e" + auction.getItemAmount() + "x " + auction.getItemMaterial());
            } else {
                plugin.getDatabaseManager().storePendingItem(auction.getCurrentBidder(), item);
            }

            double payout = auction.getCurrentBid() * (1 - AUCTION_TAX);
            plugin.getEconomyManager().addPlayerBalance(auction.getSellerUuid(), payout);

            Player seller = Bukkit.getPlayer(auction.getSellerUuid());
            if (seller != null && seller.isOnline()) {
                seller.sendMessage("§a§lAuction sold! §e$" + String.format("%,.0f", payout) + " §7(after 5% tax)");
            }

            plugin.getDatabaseManager().markAuctionSold(auction.getId(), auction.getCurrentBidder());
        } else {
            Player seller = Bukkit.getPlayer(auction.getSellerUuid());
            ItemStack item = new ItemStack(Material.valueOf(auction.getItemMaterial()), auction.getItemAmount());

            if (seller != null && seller.isOnline()) {
                seller.getInventory().addItem(item);
                seller.sendMessage("§eYour auction for §b" + auction.getItemMaterial() + " §eended with no bids. Item returned.");
            } else {
                plugin.getDatabaseManager().storePendingItem(auction.getSellerUuid(), item);
            }

            plugin.getDatabaseManager().markAuctionExpired(auction.getId());
        }
    }

    public Map<String, Auction> getActiveAuctions() {
        return new HashMap<>(activeAuctions);
    }

    public Auction getAuction(String id) {
        return activeAuctions.get(id);
    }
}