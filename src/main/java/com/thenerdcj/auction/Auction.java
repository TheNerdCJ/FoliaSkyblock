package com.thenerdcj.auction;

import java.util.UUID;

public class Auction {
    private final String id;
    private final UUID sellerUuid;
    private final String itemMaterial;
    private final int itemAmount;
    private final double startingPrice;
    private final double currentBid;
    private final UUID currentBidder;
    private final long endTime;
    private final boolean active;

    public Auction(String id, UUID sellerUuid, String itemMaterial, int itemAmount,
                   double startingPrice, double currentBid, UUID currentBidder, long endTime, boolean active) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.itemMaterial = itemMaterial;
        this.itemAmount = itemAmount;
        this.startingPrice = startingPrice;
        this.currentBid = currentBid;
        this.currentBidder = currentBidder;
        this.endTime = endTime;
        this.active = active;
    }

    public String getId() { return id; }
    public UUID getSellerUuid() { return sellerUuid; }
    public String getItemMaterial() { return itemMaterial; }
    public int getItemAmount() { return itemAmount; }
    public double getStartingPrice() { return startingPrice; }
    public double getCurrentBid() { return currentBid; }
    public UUID getCurrentBidder() { return currentBidder; }
    public long getEndTime() { return endTime; }
    public boolean isActive() { return active; }
    public boolean isExpired() { return System.currentTimeMillis() > endTime; }
    public long getTimeRemaining() { return Math.max(0, endTime - System.currentTimeMillis()); }
}