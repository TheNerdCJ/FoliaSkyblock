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

        // Check for expired auctions every minute (background async work)
        // Use ThreadSafety for Folia-aware async repeating
        // Note: This is intentionally async; deliveries inside are scheduled to main via ThreadSafety
        plugin.getThreadSafety().runRepeatingOnMainThread(this::checkExpiredAuctions, 20L * 60, 20L * 60); // This will run on main - better to have async version

        // Actually, for background expiration, better to keep async. Using direct for now.
        // For full Folia, we could add runRepeatingAsync to ThreadSafety in future.
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
        if (seller == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Fast synchronous validation (cheap checks)
        if (item == null || item.getType() == Material.AIR) {
            seller.sendMessage("§cYou must hold an item to auction!");
            return CompletableFuture.completedFuture(null);
        }

        if (startingPrice < 1) {
            seller.sendMessage("§cStarting price must be at least $1!");
            return CompletableFuture.completedFuture(null);
        }

        // We must touch the player's inventory on the main thread
        CompletableFuture<String> result = new CompletableFuture<>();

        runOnMainThread(() -> {
            // Clone to avoid modifying the original reference the player is holding
            ItemStack toSell = item.clone();

            if (!seller.getInventory().containsAtLeast(toSell, toSell.getAmount())) {
                seller.sendMessage("§cYou don't have enough items!");
                result.complete(null);
                return;
            }

            // Safe inventory modification - we are on main thread
            seller.getInventory().removeItem(toSell);

            // Now perform the heavier work (DB + in-memory updates) asynchronously
            createAuctionRecord(seller, toSell, startingPrice, result);
        });

        return result;
    }

    /**
     * Performs the actual auction creation after the item has been safely removed on main thread.
     */
    private void createAuctionRecord(Player seller, ItemStack item, double startingPrice, CompletableFuture<String> resultFuture) {
        CompletableFuture.runAsync(() -> {
            try {
                String auctionId = UUID.randomUUID().toString().substring(0, 8);
                long endTime = System.currentTimeMillis() + AUCTION_DURATION;

                Auction auction = new Auction(
                        auctionId,
                        seller.getUniqueId(),
                        item.getType().name(),
                        item.getAmount(),
                        startingPrice,
                        startingPrice,
                        null,
                        endTime,
                        true
                );

                activeAuctions.put(auctionId, auction);
                playerAuctions.computeIfAbsent(seller.getUniqueId(), k -> new ArrayList<>()).add(auctionId);

                plugin.getDatabaseManager().saveAuction(auction).thenRun(() -> {
                    // Broadcast can be done from async, but we schedule it to main for cleanliness
                    runOnMainThread(() -> {
                        Bukkit.broadcastMessage("§6§l[AUCTION] §e" + seller.getName() + " §7listed §b" +
                                item.getAmount() + "x " + item.getType().name() + " §7for §a$" +
                                String.format("%,.0f", startingPrice));
                    });
                });

                resultFuture.complete(auctionId);
            } catch (Exception e) {
                plugin.getLogger().severe("[Auction] Failed to create auction: " + e.getMessage());
                // Try to give the item back on main thread if something went wrong
                runOnMainThread(() -> seller.getInventory().addItem(item));
                resultFuture.complete(null);
            }
        });
    }

    public CompletableFuture<Boolean> placeBid(Player bidder, String auctionId, double bidAmount) {
        Auction auction = activeAuctions.get(auctionId);

        if (auction == null) {
            sendMessageSafely(bidder, "§cAuction not found!");
            return CompletableFuture.completedFuture(false);
        }
        if (auction.isExpired()) {
            sendMessageSafely(bidder, "§cThis auction has ended!");
            return CompletableFuture.completedFuture(false);
        }
        if (bidder.getUniqueId().equals(auction.getSellerUuid())) {
            sendMessageSafely(bidder, "§cYou cannot bid on your own auction!");
            return CompletableFuture.completedFuture(false);
        }
        if (bidAmount <= auction.getCurrentBid()) {
            sendMessageSafely(bidder, "§cBid must be higher than current bid of §e$" + String.format("%,.0f", auction.getCurrentBid()));
            return CompletableFuture.completedFuture(false);
        }

        // Async balance check — no blocking join inside supplyAsync
        return plugin.getEconomyManager().getPlayerBalance(bidder.getUniqueId()).thenCompose(balance -> {
            if (balance < bidAmount) {
                sendMessageSafely(bidder, "§cYou don't have enough money! Need §e$" + String.format("%,.0f", bidAmount));
                return CompletableFuture.completedFuture(false);
            }

            // Refund previous bidder (economy is thread-safe via DB)
            if (auction.getCurrentBidder() != null) {
                plugin.getEconomyManager().addPlayerBalance(auction.getCurrentBidder(), auction.getCurrentBid());

                Player previousBidder = Bukkit.getPlayer(auction.getCurrentBidder());
                sendMessageSafely(previousBidder, "§eYour bid on §b" + auction.getItemMaterial() + " §ewas outbid!");
            }

            plugin.getEconomyManager().removePlayerBalance(bidder.getUniqueId(), bidAmount);

            Auction updatedAuction = new Auction(
                    auction.getId(), auction.getSellerUuid(), auction.getItemMaterial(), auction.getItemAmount(),
                    auction.getStartingPrice(), bidAmount, bidder.getUniqueId(), auction.getEndTime(), true
            );

            activeAuctions.put(auctionId, updatedAuction);
            plugin.getDatabaseManager().updateAuction(updatedAuction);

            sendMessageSafely(bidder, "§aBid placed! §e$" + String.format("%,.0f", bidAmount) + " §aon §b" + auction.getItemMaterial());

            return CompletableFuture.completedFuture(true);
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
            // Winner path
            ItemStack item = new ItemStack(Material.valueOf(auction.getItemMaterial()), auction.getItemAmount());
            double payout = auction.getCurrentBid() * (1 - AUCTION_TAX);

            // Give item to winner (or store as pending) - MUST be on main thread
            giveItemToPlayerOrStore(auction.getCurrentBidder(), item,
                    "§a§lYou won the auction! §e" + auction.getItemAmount() + "x " + auction.getItemMaterial());

            // Pay the seller (economy is async-safe)
            plugin.getEconomyManager().addPlayerBalance(auction.getSellerUuid(), payout);

            // Notify seller (safe to schedule)
            runOnMainThread(() -> {
                Player seller = Bukkit.getPlayer(auction.getSellerUuid());
                if (seller != null && seller.isOnline()) {
                    seller.sendMessage("§a§lAuction sold! §e$" + String.format("%,.0f", payout) + " §7(after 5% tax)");
                }
            });

            plugin.getDatabaseManager().markAuctionSold(auction.getId(), auction.getCurrentBidder());

        } else {
            // No bids - return item to seller
            ItemStack item = new ItemStack(Material.valueOf(auction.getItemMaterial()), auction.getItemAmount());

            giveItemToPlayerOrStore(auction.getSellerUuid(), item,
                    "§eYour auction for §b" + auction.getItemMaterial() + " §eended with no bids. Item returned.");

            plugin.getDatabaseManager().markAuctionExpired(auction.getId());
        }
    }

    /**
     * Safely gives an item to a player if they are online (on main thread),
     * otherwise stores it in the pending items system.
     */
    private void giveItemToPlayerOrStore(UUID uuid, ItemStack item, String onlineMessage) {
        plugin.getThreadSafety().giveItemSafely(uuid, item, onlineMessage);
    }

    public Map<String, Auction> getActiveAuctions() {
        return new HashMap<>(activeAuctions);
    }

    public Auction getAuction(String id) {
        return activeAuctions.get(id);
    }

    // ==================== THREAD-SAFETY DELEGATION ====================

    private void runOnMainThread(Runnable task) {
        plugin.getThreadSafety().runOnMainThread(task);
    }

    private void sendMessageSafely(Player player, String message) {
        plugin.getThreadSafety().sendMessageSafely(player, message);
    }
}