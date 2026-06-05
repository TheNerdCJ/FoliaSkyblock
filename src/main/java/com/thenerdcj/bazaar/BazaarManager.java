package com.thenerdcj.bazaar;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.bazaar.BazaarItem;
import com.thenerdcj.bazaar.BazaarOrder;
import com.thenerdcj.util.MessageUtil;
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
            MessageUtil.info(plugin.getLogger(), "§aLoaded " + orders.size() + " bazaar orders");
        });
    }

    public BazaarItem getBazaarItem(String material) {
        return bazaarItems.get(material);
    }

    public Map<String, BazaarItem> getAllBazaarItems() {
        return new HashMap<>(bazaarItems);
    }

    public CompletableFuture<Boolean> instantBuy(Player buyer, String material, int amount) {
        if (buyer == null) return CompletableFuture.completedFuture(false);

        BazaarItem item = bazaarItems.get(material);
        if (item == null) {
            plugin.getThreadSafety().sendMessageSafely(buyer, "§cThis item is not available on the Bazaar!");
            return CompletableFuture.completedFuture(false);
        }

        if (!item.canBuy()) {
            plugin.getThreadSafety().sendMessageSafely(buyer, "§cThis item is out of stock!");
            return CompletableFuture.completedFuture(false);
        }

        double totalCost = item.getBuyPrice() * amount;

        // Fully async balance check — no blocking .join()
        return plugin.getEconomyManager().getPlayerBalance(buyer.getUniqueId()).thenCompose(balance -> {
            if (balance < totalCost) {
                plugin.getThreadSafety().sendMessageSafely(buyer,
                        "§cYou need §e$" + String.format("%,.0f", totalCost) + " §cto buy " + amount + "x " + item.getDisplayName());
                return CompletableFuture.completedFuture(false);
            }

            // Economy changes are safe from async
            plugin.getEconomyManager().removePlayerBalance(buyer.getUniqueId(), totalCost);

            ItemStack itemStack = new ItemStack(Material.valueOf(material), amount);

            // Inventory modification MUST be on main thread
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            plugin.getThreadSafety().runOnMainThread(() -> {
                buyer.getInventory().addItem(itemStack);
                plugin.getThreadSafety().sendMessageSafely(buyer,
                        "§aBought §e" + amount + "x " + item.getDisplayName() + " §afor §e$" + String.format("%,.0f", totalCost));
                result.complete(true);
            });

            return result;
        });
    }

    public CompletableFuture<Boolean> instantSell(Player seller, String material, int amount) {
        if (seller == null) return CompletableFuture.completedFuture(false);

        BazaarItem item = bazaarItems.get(material);
        if (item == null) {
            plugin.getThreadSafety().sendMessageSafely(seller, "§cThis item cannot be sold to the Bazaar!");
            return CompletableFuture.completedFuture(false);
        }

        if (!item.canSell()) {
            plugin.getThreadSafety().sendMessageSafely(seller, "§cThis item cannot be sold!");
            return CompletableFuture.completedFuture(false);
        }

        ItemStack toSell = new ItemStack(Material.valueOf(material), amount);
        if (!seller.getInventory().containsAtLeast(toSell, amount)) {
            plugin.getThreadSafety().sendMessageSafely(seller, "§cYou don't have enough " + item.getDisplayName() + "!");
            return CompletableFuture.completedFuture(false);
        }

        // Economy is safe async
        double grossPayout = item.getSellPrice() * amount;
        double tax = grossPayout * BAZAAR_TAX;
        double netPayout = grossPayout - tax;
        plugin.getEconomyManager().addPlayerBalance(seller.getUniqueId(), netPayout);

        // Inventory removal + messaging must be on main thread
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        plugin.getThreadSafety().runOnMainThread(() -> {
            seller.getInventory().removeItem(toSell);
            plugin.getThreadSafety().sendMessageSafely(seller,
                    "§aSold §e" + amount + "x " + item.getDisplayName() + " §afor §e$" + String.format("%,.0f", netPayout));
            plugin.getThreadSafety().sendMessageSafely(seller,
                    "§7(§c-" + String.format("%,.0f", tax) + " §7tax)");
            result.complete(true);
        });

        return result;
    }

    public CompletableFuture<String> createBuyOrder(Player buyer, String material, int amount, double pricePerUnit) {
        if (buyer == null) return CompletableFuture.completedFuture(null);

        double totalCost = pricePerUnit * amount;

        // Fully async balance check
        return plugin.getEconomyManager().getPlayerBalance(buyer.getUniqueId()).thenCompose(balance -> {
            if (balance < totalCost) {
                plugin.getThreadSafety().sendMessageSafely(buyer,
                        "§cYou need §e$" + String.format("%,.0f", totalCost) + " §cto create this order!");
                return CompletableFuture.completedFuture(null);
            }

            plugin.getEconomyManager().removePlayerBalance(buyer.getUniqueId(), totalCost);

            String orderId = UUID.randomUUID().toString().substring(0, 8);
            BazaarOrder order = new BazaarOrder(
                    orderId, buyer.getUniqueId(), material, amount, pricePerUnit, true,
                    System.currentTimeMillis(), false
            );

            buyOrders.computeIfAbsent(material, k -> new ArrayList<>()).add(order);
            plugin.getDatabaseManager().saveBazaarOrder(order);

            plugin.getThreadSafety().sendMessageSafely(buyer,
                    "§aBuy order created! §e" + amount + "x " + material + " @ $" + String.format("%.2f", pricePerUnit) + "/ea");

            matchOrders(material);

            return CompletableFuture.completedFuture(orderId);
        });
    }

    public CompletableFuture<String> createSellOrder(Player seller, String material, int amount, double pricePerUnit) {
        if (seller == null) return CompletableFuture.completedFuture(null);

        ItemStack toSell = new ItemStack(Material.valueOf(material), amount);
        if (!seller.getInventory().containsAtLeast(toSell, amount)) {
            plugin.getThreadSafety().sendMessageSafely(seller, "§cYou don't have enough " + material + "!");
            return CompletableFuture.completedFuture(null);
        }

        // Inventory removal must happen on main thread
        CompletableFuture<String> result = new CompletableFuture<>();

        plugin.getThreadSafety().runOnMainThread(() -> {
            ItemStack toRemove = toSell.clone();
            seller.getInventory().removeItem(toRemove);

            // Now do the rest of the work (can be async)
            String orderId = UUID.randomUUID().toString().substring(0, 8);
            BazaarOrder order = new BazaarOrder(
                    orderId, seller.getUniqueId(), material, amount, pricePerUnit, false,
                    System.currentTimeMillis(), false
            );

            sellOrders.computeIfAbsent(material, k -> new ArrayList<>()).add(order);
            plugin.getDatabaseManager().saveBazaarOrder(order);

            plugin.getThreadSafety().sendMessageSafely(seller,
                    "§aSell order created! §e" + amount + "x " + material + " @ $" + String.format("%.2f", pricePerUnit) + "/ea");

            matchOrders(material);

            result.complete(orderId);
        });

        return result;
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
        double payout = tradePrice * amount * (1 - BAZAAR_TAX);

        ItemStack item = new ItemStack(Material.valueOf(buy.getMaterial()), amount);

        // Give item to buyer safely (main thread)
        plugin.getThreadSafety().giveItemSafely(
                buy.getPlayerUuid(),
                item,
                "§aBazaar order filled! Bought §e" + amount + "x " + buy.getMaterial()
        );

        // Pay the seller (economy is async-safe)
        plugin.getEconomyManager().addPlayerBalance(sell.getPlayerUuid(), payout);

        // Notify seller safely
        plugin.getThreadSafety().sendMessageSafely(
                sell.getPlayerUuid(),
                "§aBazaar order filled! Sold §e" + amount + "x " + sell.getMaterial() +
                        " §afor §e$" + String.format("%,.0f", payout)
        );

        plugin.getDatabaseManager().markBazaarOrderFilled(buy.getId());
        plugin.getDatabaseManager().markBazaarOrderFilled(sell.getId());
    }

    public List<BazaarOrder> getBuyOrders(String material) {
        return buyOrders.getOrDefault(material, new ArrayList<>());
    }

    public List<BazaarOrder> getSellOrders(String material) {
        return sellOrders.getOrDefault(material, new ArrayList<>());
    }

    /**
     * Finds a specific order by ID across buy and sell orders.
     */
    private BazaarOrder findOrderById(String orderId) {
        for (List<BazaarOrder> orders : buyOrders.values()) {
            for (BazaarOrder order : orders) {
                if (order.getId().equals(orderId)) return order;
            }
        }
        for (List<BazaarOrder> orders : sellOrders.values()) {
            for (BazaarOrder order : orders) {
                if (order.getId().equals(orderId)) return order;
            }
        }
        return null;
    }

    /**
     * Fulfills an existing order from the GUI.
     * The player takes the opposite side of the order.
     *
     * @param orderId   The ID of the order to fulfill
     * @param fulfiller The player clicking to fulfill
     */
    public CompletableFuture<Boolean> fulfillOrder(String orderId, Player fulfiller) {
        if (orderId == null || fulfiller == null) {
            return CompletableFuture.completedFuture(false);
        }

        BazaarOrder order = findOrderById(orderId);
        if (order == null) {
            plugin.getThreadSafety().sendMessageSafely(fulfiller, "§cOrder no longer exists.");
            return CompletableFuture.completedFuture(false);
        }

        if (order.getPlayerUuid().equals(fulfiller.getUniqueId())) {
            plugin.getThreadSafety().sendMessageSafely(fulfiller, "§cYou cannot fulfill your own order.");
            return CompletableFuture.completedFuture(false);
        }

        int amount = order.getAmount();
        double pricePerUnit = order.getPricePerUnit();
        String material = order.getMaterial();
        double totalValue = pricePerUnit * amount;

        if (order.isBuyOrder()) {
            // Fulfiller is SELLING to this buy order
            return fulfillAgainstBuyOrder(order, fulfiller, amount, totalValue, material);
        } else {
            // Fulfiller is BUYING from this sell order
            return fulfillAgainstSellOrder(order, fulfiller, amount, totalValue, material);
        }
    }

    private CompletableFuture<Boolean> fulfillAgainstBuyOrder(BazaarOrder buyOrder, Player seller, int amount, double totalValue, String material) {
        // Check seller has the items (must be on main thread for inventory check)
        return CompletableFuture.supplyAsync(() -> {
            ItemStack toSell = new ItemStack(Material.valueOf(material), amount);
            if (!seller.getInventory().containsAtLeast(toSell, amount)) {
                plugin.getThreadSafety().sendMessageSafely(seller, "§cYou don't have enough " + material + " to fulfill this order!");
                return false;
            }

            // Remove items from seller on main thread
            plugin.getThreadSafety().runOnMainThread(() -> {
                ItemStack removeStack = new ItemStack(Material.valueOf(material), amount);
                seller.getInventory().removeItem(removeStack);

                // Give items to the original buyer
                ItemStack itemsForBuyer = new ItemStack(Material.valueOf(material), amount);
                plugin.getThreadSafety().giveItemSafely(
                        buyOrder.getPlayerUuid(),
                        itemsForBuyer,
                        "§aYour buy order for " + amount + "x " + material + " has been fulfilled!"
                );
            });

            // Pay the seller (fulfiller) - money comes from the escrow the buyer already paid
            double payout = totalValue * (1 - BAZAAR_TAX);
            plugin.getEconomyManager().addPlayerBalance(seller.getUniqueId(), payout);

            plugin.getThreadSafety().sendMessageSafely(seller,
                    "§aYou fulfilled a buy order! Sold " + amount + "x " + material + " for §e$" + String.format("%,.0f", payout) + " (after tax)");

            // Remove the order from memory and mark filled in DB
            removeOrderFromMemory(buyOrder);
            plugin.getDatabaseManager().markBazaarOrderFilled(buyOrder.getId());

            return true;
        });
    }

    private CompletableFuture<Boolean> fulfillAgainstSellOrder(BazaarOrder sellOrder, Player buyer, int amount, double totalValue, String material) {
        // Buyer needs to have enough money
        return plugin.getEconomyManager().getPlayerBalance(buyer.getUniqueId()).thenCompose(balance -> {
            if (balance < totalValue) {
                plugin.getThreadSafety().sendMessageSafely(buyer, "§cYou don't have enough money to fulfill this sell order!");
                return CompletableFuture.completedFuture(false);
            }

            // Take money from buyer
            plugin.getEconomyManager().removePlayerBalance(buyer.getUniqueId(), totalValue);

            // Give items to buyer on main thread
            plugin.getThreadSafety().runOnMainThread(() -> {
                ItemStack items = new ItemStack(Material.valueOf(material), amount);
                buyer.getInventory().addItem(items);
            });

            // Pay the original seller (minus tax)
            double payout = totalValue * (1 - BAZAAR_TAX);
            plugin.getEconomyManager().addPlayerBalance(sellOrder.getPlayerUuid(), payout);

            plugin.getThreadSafety().sendMessageSafely(buyer,
                    "§aYou fulfilled a sell order! Bought " + amount + "x " + material + " for §e$" + String.format("%,.0f", totalValue));

            plugin.getThreadSafety().sendMessageSafely(sellOrder.getPlayerUuid(),
                    "§aYour sell order for " + amount + "x " + material + " has been fulfilled! You received §e$" + String.format("%,.0f", payout) + " (after tax)");

            // Remove order
            removeOrderFromMemory(sellOrder);
            plugin.getDatabaseManager().markBazaarOrderFilled(sellOrder.getId());

            return CompletableFuture.completedFuture(true);
        });
    }

    private void removeOrderFromMemory(BazaarOrder order) {
        String material = order.getMaterial();
        if (order.isBuyOrder()) {
            List<BazaarOrder> buys = buyOrders.get(material);
            if (buys != null) buys.removeIf(o -> o.getId().equals(order.getId()));
        } else {
            List<BazaarOrder> sells = sellOrders.get(material);
            if (sells != null) sells.removeIf(o -> o.getId().equals(order.getId()));
        }
    }
}