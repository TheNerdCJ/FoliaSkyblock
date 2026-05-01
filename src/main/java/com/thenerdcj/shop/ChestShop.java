package com.thenerdcj.shop;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.UUID;

public class ChestShop {

    private final UUID owner;
    private final Location chestLocation;
    private final Location signLocation;
    private final Material itemType;
    private final int buyPrice;
    private final int sellPrice;
    private final int amount;

    public ChestShop(UUID owner, Location chestLocation, Location signLocation,
                     Material itemType, int buyPrice, int sellPrice, int amount) {
        this.owner = owner;
        this.chestLocation = chestLocation;
        this.signLocation = signLocation;
        this.itemType = itemType;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.amount = amount;
    }

    public UUID getOwner() { return owner; }
    public Location getChestLocation() { return chestLocation; }
    public Location getSignLocation() { return signLocation; }
    public Material getItemType() { return itemType; }
    public int getBuyPrice() { return buyPrice; }
    public int getSellPrice() { return sellPrice; }
    public int getAmount() { return amount; }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public double getBuyTotal() {
        return buyPrice * amount;
    }

    public double getSellTotal() {
        return sellPrice * amount;
    }
}