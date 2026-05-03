package com.thenerdcj.bazaar;

public class BazaarItem {
    private final String material;
    private final String displayName;
    private final double buyPrice;
    private final double sellPrice;
    private final int stock;

    public BazaarItem(String material, String displayName, double buyPrice, double sellPrice, int stock) {
        this.material = material;
        this.displayName = displayName;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stock = stock;
    }

    public String getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public double getBuyPrice() { return buyPrice; }
    public double getSellPrice() { return sellPrice; }
    public int getStock() { return stock; }

    public boolean canBuy() { return stock > 0; }
    public boolean canSell() { return true; }
}