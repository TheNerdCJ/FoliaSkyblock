package com.thenerdcj.bazaar;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.bazaar.BazaarItem;
import com.thenerdcj.bazaar.BazaarOrder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BazaarManager {
    private final FoliaSkyblock plugin;
    private final Map<String, BazaarItem> bazaarItems = new ConcurrentHashMap<>();
    private final Map<String, List<BazaarOrder>> buyOrders = new ConcurrentHashMap<>();
    private final Map<String, List<BazaarOrder>> sellOrders = new ConcurrentHashMap<>();

    private static final double BAZAAR_TAX = 0.0125;

    public BazaarManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadBazaarItems();
        loadOrders();
    }

    private void loadBazaarItems() {
        addBazaarItem("COBBLESTONE", "Cobblestone", 2.5, 2.0, 10000);
        addBazaarItem("STONE", "Stone", 3.0, 2.5, 5000);
        addBazaarItem("WHEAT", "Wheat", 5.0, 4.0, 5000);
        addBazaarItem("CARROT", "Carrot", 4.0, 3.0, 5000);
        addBazaarItem("POTATO", "Potato", 4.0, 3.0, 5000);
        addBazaarItem("DIAMOND", "Diamond", 100.0, 80.0, 1000);
        addBazaarItem("EMERALD", "Emerald", 50.0, 40.0, 2000);
        addBazaarItem("IRON_INGOT", "Iron Ingot", 15.0, 12.0, 5000);
        addBazaarItem("GOLD_INGOT", "Gold Ingot", 20.0, 16.0, 3000);
        addBazaarItem("ENDER_PEARL", "Ender Pearl", 25.0, 20.0, 2000);
    }

    private void addBazaarItem(String material, String displayName, double buyPrice, double sellPrice, int stock) {
        bazaarItems.put(material, new BazaarItem(material, displayName, buyPrice, sellPrice, stock));
    }

    private void loadOrders() {
        plugin.getDatabaseManager().getActiveBazaarOrders().thenAccept(orders -> {
            for (BazaarOrder order : orders) {
                if (order.isBuyOrder()) {
                    buyOrders.computeIfAbsent(order.getMaterial(), k -> new ArrayList<>()).add(order);
                } else {
                    sellOrders.computeIfAbsent(order.getMaterial(), k -> new ArrayList<>()).add(order);
                }
            }
            plugin.getLogger().info("§aLoaded " + orders.size() + " bazaar orders");
        });
    }

    public BazaarItem getBazaarItem(String material) {
        return bazaarItems.get(material);
    }

    public Map<String, BazaarItem> getAllBazaarItems() {
        return new HashMap<>(bazaarItems);
    }

    public CompletableFuture<Boolean> instantBuy(Player buyer, String material, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            BazaarItem item = bazaarItems.get(material);
            if (item == null) {
                buyer.sendMessage("§cThis item is not available on the Bazaar!");
                return false;
            }

            if (!item.canBuy()) {
                buyer.sendMessage("§cThis item is out of stock!");
                return false;
            }

            double totalCost = item.getBuyPrice() * amount;
            double balance = plugin.getEconomyManager().getPlayerBalance(buyer.getUniqueId()).join();

            if (balance < totalCost) {
                buyer.sendMessage("§cYou need §e$" + String.format("%,.0f", totalCost) + " §cto buy " + amount + "x " + item.getDisplayName());
                return false;
            }

            plugin.getEconomyManager().removePlayerBalance(buyer.getUniqueId(), totalCost);

            ItemStack itemStack = new ItemStack(Material.valueOf(material), amount);
            buyer.getInventory().addItem(itemStack);

            buyer.sendMessage("§aBought §e" + amount + "x " + item.getDisplayName() + " §afor §e$" + String.format("%,.0f", totalCost));

            return true;
        });
    }

    public CompletableFuture<Boolean> instantSell(Player seller, String material, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            BazaarItem item = bazaarItems.get(material);
            if (item == null) {
                seller.sendMessage("§cThis item cannot be sold to the Bazaar!");
                return false;
            }

            if (!item.canSell()) {
                seller.sendMessage("§cThis item cannot be sold!");
                return false;
            }

            ItemStack toSell = new ItemStack(Material.valueOf(material), amount);
            if (!seller.getInventory().containsAtLeast(toSell, amount)) {
                seller.sendMessage("§cYou don't have enough " + item.getDisplayName() + "!");
                return false;
            }

            seller.getInventory().removeItem(toSell);

            double grossPayout = item.getSellPrice() * amount;
            double tax = grossPayout * BAZAAR_TAX;
            double netPayout = grossPayout - tax;

            plugin.getEconomyManager().addPlayerBalance(seller.getUniqueId(), netPayout);

            seller.sendMessage("§aSold §e" + amount + "x " + item.getDisplayName() + " §afor §e$" + String.format("%,.0f", netPayout));
            seller.sendMessage("§7(§c-" + String.format("%,.0f", tax) + " §7tax)");

            return true;
        });
    }

    public CompletableFuture<String> createBuyOrder(Player buyer, String material, int amount, double pricePerUnit) {
        return CompletableFuture.supplyAsync(() -> {
            double totalCost = pricePerUnit * amount;
            double balance = plugin.getEconomyManager().getPlayerBalance(buyer.getUniqueId()).join();

            if (balance < totalCost) {
                buyer.sendMessage("§cYou need §e$" + String.format("%,.0f", totalCost) + " §cto create this order!");
                return null;
            }

            plugin.getEconomyManager().removePlayerBalance(buyer.getUniqueId(), totalCost);

            String orderId = UUID.randomUUID().toString().substring(0, 8);
            BazaarOrder order = new BazaarOrder(
                    orderId, buyer.getUniqueId(), material, amount, pricePerUnit, true,
                    System.currentTimeMillis(), false
            );

            buyOrders.computeIfAbsent(material, k -> new ArrayList<>()).add(order);
            plugin.getDatabaseManager().saveBazaarOrder(order);

            buyer.sendMessage("§aBuy order created! §e" + amount + "x " + material + " @ $" + String.format("%.2f", pricePerUnit) + "/ea");

            matchOrders(material);

            return orderId;
        });
    }

    public CompletableFuture<String> createSellOrder(Player seller, String material, int amount, double pricePerUnit) {
        return CompletableFuture.supplyAsync(() -> {
            ItemStack toSell = new ItemStack(Material.valueOf(material), amount);
            if (!seller.getInventory().containsAtLeast(toSell, amount)) {
                seller.sendMessage("§cYou don't have enough " + material + "!");
                return null;
            }

            seller.getInventory().removeItem(toSell);

            String orderId = UUID.randomUUID().toString().substring(0, 8);
            BazaarOrder order = new BazaarOrder(
                    orderId, seller.getUniqueId(), material, amount, pricePerUnit, false,
                    System.currentTimeMillis(), false
            );

            sellOrders.computeIfAbsent(material, k -> new ArrayList<>()).add(order);
            plugin.getDatabaseManager().saveBazaarOrder(order);

            seller.sendMessage("§aSell order created! §e" + amount + "x " + material + " @ $" + String.format("%.2f", pricePerUnit) + "/ea");

            matchOrders(material);

            return orderId;
        });
    }

    private void matchOrders(String material) {
        List<BazaarOrder> buys = buyOrders.getOrDefault(material, new ArrayList<>());
        List<BazaarOrder> sells = sellOrders.getOrDefault(material, new ArrayList<>());

        buys.sort((a, b) -> Double.compare(b.getPricePerUnit(), a.getPricePerUnit()));
        sells.sort(Comparator.comparingDouble(BazaarOrder::getPricePerUnit));

        Iterator<BazaarOrder> buyIter = buys.iterator();
        while (buyIter.hasNext()) {
            BazaarOrder buy = buyIter.next();
            if (buy.isFilled()) continue;

            Iterator<BazaarOrder> sellIter = sells.iterator();
            while (sellIter.hasNext()) {
                BazaarOrder sell = sellIter.next();
                if (sell.isFilled()) continue;

                if (buy.getPricePerUnit() >= sell.getPricePerUnit()) {
                    int matchAmount = Math.min(buy.getAmount(), sell.getAmount());

                    executeTrade(buy, sell, matchAmount);

                    if (buy.getAmount() == matchAmount) {
                        buyIter.remove();
                    }

                    if (sell.getAmount() == matchAmount) {
                        sellIter.remove();
                    }

                    break;
                }
            }
        }
    }

    private void executeTrade(BazaarOrder buy, BazaarOrder sell, int amount) {
        double tradePrice = sell.getPricePerUnit();

        Player buyer = Bukkit.getPlayer(buy.getPlayerUuid());
        ItemStack item = new ItemStack(Material.valueOf(buy.getMaterial()), amount);

        if (buyer != null && buyer.isOnline()) {
            buyer.getInventory().addItem(item);
            buyer.sendMessage("§aBazaar order filled! Bought §e" + amount + "x " + buy.getMaterial());
        } else {
            plugin.getDatabaseManager().storePendingItem(buy.getPlayerUuid(), item);
        }

        double payout = tradePrice * amount * (1 - BAZAAR_TAX);
        plugin.getEconomyManager().addPlayerBalance(sell.getPlayerUuid(), payout);

        Player seller = Bukkit.getPlayer(sell.getPlayerUuid());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage("§aBazaar order filled! Sold §e" + amount + "x " + sell.getMaterial() + " §afor §e$" + String.format("%,.0f", payout));
        }

        plugin.getDatabaseManager().markBazaarOrderFilled(buy.getId());
        plugin.getDatabaseManager().markBazaarOrderFilled(sell.getId());
    }

    public List<BazaarOrder> getBuyOrders(String material) {
        return buyOrders.getOrDefault(material, new ArrayList<>());
    }

    public List<BazaarOrder> getSellOrders(String material) {
        return sellOrders.getOrDefault(material, new ArrayList<>());
    }
}