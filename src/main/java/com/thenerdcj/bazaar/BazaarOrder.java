package com.thenerdcj.bazaar;

import java.util.UUID;

public class BazaarOrder {
    private final String id;
    private final UUID playerUuid;
    private final String material;
    private final int amount;
    private final double pricePerUnit;
    private final boolean isBuyOrder;
    private final long createdAt;
    private final boolean filled;

    public BazaarOrder(String id, UUID playerUuid, String material, int amount,
                       double pricePerUnit, boolean isBuyOrder, long createdAt, boolean filled) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.material = material;
        this.amount = amount;
        this.pricePerUnit = pricePerUnit;
        this.isBuyOrder = isBuyOrder;
        this.createdAt = createdAt;
        this.filled = filled;
    }

    public String getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getMaterial() { return material; }
    public int getAmount() { return amount; }
    public double getPricePerUnit() { return pricePerUnit; }
    public boolean isBuyOrder() { return isBuyOrder; }
    public long getCreatedAt() { return createdAt; }
    public boolean isFilled() { return filled; }

    public double getTotalPrice() { return pricePerUnit * amount; }
}