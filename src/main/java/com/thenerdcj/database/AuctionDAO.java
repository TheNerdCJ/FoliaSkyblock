package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.auction.Auction;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Extracted DAO for Auction persistence.
 * Pilot step in DatabaseManager compression / modernization.
 * Uses DBOperations for async execution and reduced boilerplate.
 */
public class AuctionDAO extends BaseDAO {

    public AuctionDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Schema centrally managed for now.
    }

    public CompletableFuture<List<Auction>> getActiveAuctions() {
        return supplyAsync(() -> {
            List<Auction> auctions = new ArrayList<>();
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM auctions WHERE sold = 0")) {

                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            String id = rs.getString("id");
                            UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));

                            String itemBase64 = rs.getString("item_base64");
                            double price = rs.getDouble("price");
                            long endTime = rs.getLong("end_time");

                            String buyerStr = rs.getString("buyer_uuid");
                            UUID currentBidder = (buyerStr != null && !buyerStr.isEmpty())
                                    ? UUID.fromString(buyerStr) : null;

                            Material material = Material.STONE;
                            int amount = 1;

                            try {
                                ItemStack item = plugin.getDatabaseManager().itemFromBase64(itemBase64);
                                if (item != null && item.getType() != Material.AIR) {
                                    material = item.getType();
                                    amount = Math.max(1, item.getAmount());
                                }
                            } catch (Exception ignored) {}

                            Auction auction = new Auction(
                                    id, sellerUuid, material.name(), amount,
                                    price, price, currentBidder, endTime, true
                            );
                            auctions.add(auction);
                        }
                        return auctions;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[AuctionDAO] getActiveAuctions failed: " + e.getMessage());
                return auctions;
            }
        });
    }

    public CompletableFuture<Boolean> saveAuction(Auction auction) {
        return supplyAsync(() -> {
            if (auction == null) return false;

            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO auctions " +
                            "(id, seller_uuid, item_base64, price, end_time, sold, buyer_uuid) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)")) {

                        ItemStack simpleItem = new ItemStack(
                                Material.valueOf(auction.getItemMaterial()),
                                Math.max(1, auction.getItemAmount())
                        );
                        String itemBase64 = plugin.getDatabaseManager().itemToBase64(simpleItem);

                        ps.setString(1, auction.getId());
                        ps.setString(2, auction.getSellerUuid().toString());
                        ps.setString(3, itemBase64);
                        ps.setDouble(4, auction.getCurrentBid());
                        ps.setLong(5, auction.getEndTime());
                        ps.setBoolean(6, false);
                        ps.setString(7, auction.getCurrentBidder() != null ? auction.getCurrentBidder().toString() : null);

                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[AuctionDAO] saveAuction failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> updateAuction(Auction auction) {
        // Same as save for SQLite REPLACE semantics in this schema
        return saveAuction(auction);
    }

    public CompletableFuture<Boolean> markAuctionSold(String auctionId, UUID buyerUuid) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE auctions SET sold = 1, buyer_uuid = ? WHERE id = ?")) {
                        ps.setString(1, buyerUuid != null ? buyerUuid.toString() : null);
                        ps.setString(2, auctionId);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[AuctionDAO] markAuctionSold failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> markAuctionExpired(String auctionId) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE auctions SET sold = 1 WHERE id = ?")) {
                        ps.setString(1, auctionId);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[AuctionDAO] markAuctionExpired failed: " + e.getMessage());
                return false;
            }
        });
    }
}