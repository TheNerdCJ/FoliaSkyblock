package com.thenerdcj.shop;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChestShopManager - Complete Chest Shop System
 *
 * Features:
 * - Player economy integration (not island balance)
 * - Sign format: [Shop] / Buy: X / Sell: Y / PlayerName
 * - Auto-correct sign format when player is nearby
 * - Prevents players from setting other players' names
 * - Database persistence
 * - Async operations for Folia
 */
public class ChestShopManager {

    private final FoliaSkyblock plugin;
    private final Map<Location, ChestShop> activeShops = new ConcurrentHashMap<>();

    public ChestShopManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadAllShops();
    }

    /**
     * Create the chest_shops table in database
     */
    public static void createTable(FoliaSkyblock plugin) {
        String sql = """
            CREATE TABLE IF NOT EXISTS chest_shops (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                world VARCHAR(64) NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                owner_uuid VARCHAR(36) NOT NULL,
                owner_name VARCHAR(16) NOT NULL,
                item_type VARCHAR(64) NOT NULL,
                buy_price DOUBLE NOT NULL,
                sell_price DOUBLE NOT NULL,
                stock INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(world, x, y, z)
            )
            """;

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.getLogger().info("§a[ChestShop] chest_shops table created/verified");
        } catch (SQLException e) {
            plugin.getLogger().severe("§c[ChestShop] Failed to create chest_shops table: " + e.getMessage());
        }
    }

    /**
     * Load all chest shops from database
     */
    private void loadAllShops() {
        String sql = "SELECT * FROM chest_shops";

        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Location loc = new Location(
                            Bukkit.getWorld(rs.getString("world")),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z")
                    );

                    if (loc.getWorld() != null) {
                        ChestShop shop = new ChestShop(
                                UUID.fromString(rs.getString("owner_uuid")),
                                loc,
                                loc,
                                Material.valueOf(rs.getString("item_type")),
                                (int)rs.getDouble("buy_price"),
                                (int)rs.getDouble("sell_price"),
                                rs.getInt("stock")
                        );

                        activeShops.put(loc, shop);
                    }
                }
                plugin.getLogger().info("§a[ChestShop] Loaded " + activeShops.size() + " chest shops");
            } catch (SQLException e) {
                plugin.getLogger().severe("§c[ChestShop] Failed to load shops: " + e.getMessage());
            }
        });
    }

    /**
     * Create a new chest shop
     */
    public boolean createShop(Player player, Location signLocation, Material itemType, double buyPrice, double sellPrice) {
        // Validate sign format
        if (!isValidSignLocation(signLocation)) {
            player.sendMessage("§cInvalid shop location! Sign must be placed on a chest.");
            return false;
        }

        // Check if shop already exists
        if (activeShops.containsKey(signLocation)) {
            player.sendMessage("§cA shop already exists at this location!");
            return false;
        }

        // Create shop
        ChestShop shop = new ChestShop(
                player.getUniqueId(),
                signLocation,
                signLocation,
                itemType,
                (int)buyPrice,
                (int)sellPrice,
                0
        );

        activeShops.put(signLocation, shop);

        // Save to database
        saveShopToDatabase(shop);

        // Update sign
        updateSignDisplay(signLocation, shop);

        player.sendMessage("§a§lShop created successfully!");
        player.sendMessage("§7Item: §e" + itemType.name());
        player.sendMessage("§7Buy Price: §e$" + String.format("%.2f", buyPrice));
        player.sendMessage("§7Sell Price: §e$" + String.format("%.2f", sellPrice));

        return true;
    }

    /**
     * Update shop stock when items are added/removed
     */
    public void updateStock(Location chestLocation, int newStock) {
        for (Map.Entry<Location, ChestShop> entry : activeShops.entrySet()) {
            Location signLoc = entry.getKey();
            if (isAdjacentChest(signLoc, chestLocation)) {
                ChestShop shop = entry.getValue();
                ChestShop updatedShop = new ChestShop(
                        shop.getOwner(),
                        shop.getChestLocation(),
                        shop.getSignLocation(),
                        shop.getItemType(),
                        shop.getBuyPrice(),
                        shop.getSellPrice(),
                        newStock
                );
                activeShops.put(signLoc, updatedShop);
                updateSignDisplay(signLoc, updatedShop);
                saveShopToDatabase(updatedShop);
                break;
            }
        }
    }

    /**
     * Handle player purchasing from shop - fully async where possible.
     */
    public CompletableFuture<Boolean> purchaseFromShop(Player buyer, Location signLocation, int amount) {
        ChestShop shop = activeShops.get(signLocation);
        if (shop == null) {
            plugin.getThreadSafety().sendMessageSafely(buyer, "§cThis shop no longer exists!");
            return CompletableFuture.completedFuture(false);
        }

        if (shop.getOwner().equals(buyer.getUniqueId())) {
            plugin.getThreadSafety().sendMessageSafely(buyer, "§cYou cannot buy from your own shop!");
            return CompletableFuture.completedFuture(false);
        }

        if (shop.getAmount() < amount) {
            plugin.getThreadSafety().sendMessageSafely(buyer, "§cNot enough stock! Only " + shop.getAmount() + " available.");
            return CompletableFuture.completedFuture(false);
        }

        double totalPrice = shop.getBuyPrice() * amount;

        return plugin.getEconomyManager().getPlayerBalance(buyer.getUniqueId()).thenCompose(buyerBalance -> {
            if (buyerBalance < totalPrice) {
                plugin.getThreadSafety().sendMessageSafely(buyer, "§cYou need $" + String.format("%.2f", totalPrice) + " to buy " + amount + "!");
                return CompletableFuture.completedFuture(false);
            }

            // Economy mutations are thread-safe via DB layer
            return plugin.getEconomyManager().removePlayerBalance(buyer.getUniqueId(), totalPrice)
                    .thenCompose(removed -> plugin.getEconomyManager().addPlayerBalance(shop.getOwner(), totalPrice))
                    .thenApply(success -> {
                        if (!success) {
                            // Refund if second leg failed (rare)
                            plugin.getEconomyManager().addPlayerBalance(buyer.getUniqueId(), totalPrice);
                            return false;
                        }

                        // Schedule all world/inventory changes on main thread
                        plugin.getThreadSafety().runOnMainThread(() -> {
                            // Update stock
                            ChestShop updatedShop = new ChestShop(
                                    shop.getOwner(),
                                    shop.getChestLocation(),
                                    shop.getSignLocation(),
                                    shop.getItemType(),
                                    shop.getBuyPrice(),
                                    shop.getSellPrice(),
                                    shop.getAmount() - amount
                            );
                            activeShops.put(signLocation, updatedShop);
                            updateSignDisplay(signLocation, updatedShop);
                            saveShopToDatabase(updatedShop);

                            // Give items to buyer
                            ItemStack items = new ItemStack(shop.getItemType(), amount);
                            Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(items);
                            if (!overflow.isEmpty()) {
                                for (ItemStack item : overflow.values()) {
                                    buyer.getWorld().dropItem(buyer.getLocation(), item);
                                }
                                plugin.getThreadSafety().sendMessageSafely(buyer, "§eSome items dropped at your feet (inventory full)!");
                            }

                            // Notify seller
                            Player seller = Bukkit.getPlayer(shop.getOwner());
                            if (seller != null && seller.isOnline()) {
                                plugin.getThreadSafety().sendMessageSafely(seller,
                                        "§a" + buyer.getName() + " bought " + amount + "x " + shop.getItemType().name() + " for $" + String.format("%.2f", totalPrice));
                            }

                            plugin.getThreadSafety().sendMessageSafely(buyer,
                                    "§aPurchased " + amount + "x " + shop.getItemType().name() + " for $" + String.format("%.2f", totalPrice));
                        });

                        return true;
                    });
        });
    }

    /**
     * Handle player selling to shop - fully async where possible.
     */
    public CompletableFuture<Boolean> sellToShop(Player seller, Location signLocation, int amount) {
        ChestShop shop = activeShops.get(signLocation);
        if (shop == null) {
            plugin.getThreadSafety().sendMessageSafely(seller, "§cThis shop no longer exists!");
            return CompletableFuture.completedFuture(false);
        }

        if (shop.getOwner().equals(seller.getUniqueId())) {
            plugin.getThreadSafety().sendMessageSafely(seller, "§cYou cannot sell to your own shop!");
            return CompletableFuture.completedFuture(false);
        }

        int playerHas = countItemsInInventory(seller, shop.getItemType());
        if (playerHas < amount) {
            plugin.getThreadSafety().sendMessageSafely(seller, "§cYou only have " + playerHas + " " + shop.getItemType().name() + "!");
            return CompletableFuture.completedFuture(false);
        }

        double totalPrice = shop.getSellPrice() * amount;

        return plugin.getEconomyManager().getPlayerBalance(shop.getOwner()).thenCompose(ownerBalance -> {
            if (ownerBalance < totalPrice) {
                plugin.getThreadSafety().sendMessageSafely(seller, "§cShop owner doesn't have enough money!");
                return CompletableFuture.completedFuture(false);
            }

            return plugin.getEconomyManager().removePlayerBalance(shop.getOwner(), totalPrice)
                    .thenCompose(removed -> plugin.getEconomyManager().addPlayerBalance(seller.getUniqueId(), totalPrice))
                    .thenApply(success -> {
                        if (!success) {
                            plugin.getEconomyManager().addPlayerBalance(shop.getOwner(), totalPrice); // refund
                            return false;
                        }

                        plugin.getThreadSafety().runOnMainThread(() -> {
                            removeItemsFromInventory(seller, shop.getItemType(), amount);

                            ChestShop updatedShop = new ChestShop(
                                    shop.getOwner(),
                                    shop.getChestLocation(),
                                    shop.getSignLocation(),
                                    shop.getItemType(),
                                    shop.getBuyPrice(),
                                    shop.getSellPrice(),
                                    shop.getAmount() + amount
                            );
                            activeShops.put(signLocation, updatedShop);
                            updateSignDisplay(signLocation, updatedShop);
                            saveShopToDatabase(updatedShop);

                            Player owner = Bukkit.getPlayer(shop.getOwner());
                            if (owner != null && owner.isOnline()) {
                                plugin.getThreadSafety().sendMessageSafely(owner,
                                        "§a" + seller.getName() + " sold " + amount + "x " + shop.getItemType().name() + " for $" + String.format("%.2f", totalPrice));
                            }

                            plugin.getThreadSafety().sendMessageSafely(seller,
                                    "§aSold " + amount + "x " + shop.getItemType().name() + " for $" + String.format("%.2f", totalPrice));
                        });

                        return true;
                    });
        });
    }

    /**
     * Auto-correct sign format when player is nearby
     */
    public void autoCorrectSignFormat(Player player, Sign sign) {
        Location loc = sign.getLocation();
        ChestShop shop = activeShops.get(loc);
        if (shop == null) return;

        // Only correct if player is the owner or has permission
        if (!shop.getOwner().equals(player.getUniqueId()) && !player.hasPermission("foliaskyblock.admin")) {
            return;
        }

        updateSignDisplay(loc, shop);
        player.sendMessage("§aSign format auto-corrected!");
    }

    /**
     * Update sign display with current shop info
     */
    private void updateSignDisplay(Location loc, ChestShop shop) {
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Sign sign)) return;

        sign.setLine(0, "§1[Shop]");
        sign.setLine(1, "§0" + shop.getItemType().name());
        sign.setLine(2, "§aB: §2$" + shop.getBuyPrice() + " §cS: §4$" + shop.getSellPrice());
        sign.setLine(3, "§0" + getOwnerName(shop));
        sign.update();
    }

    /**
     * Get owner name from UUID
     */
    private String getOwnerName(ChestShop shop) {
        Player owner = Bukkit.getPlayer(shop.getOwner());
        if (owner != null) {
            return owner.getName();
        }
        return "Unknown";
    }

    /**
     * Validate sign is placed on a chest
     */
    private boolean isValidSignLocation(Location loc) {
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Sign)) return false;

        Location[] adjacent = {
                loc.clone().add(1, 0, 0),
                loc.clone().add(-1, 0, 0),
                loc.clone().add(0, 0, 1),
                loc.clone().add(0, 0, -1),
                loc.clone().add(0, -1, 0)
        };

        for (Location adj : adjacent) {
            if (adj.getBlock().getType() == Material.CHEST || adj.getBlock().getType() == Material.TRAPPED_CHEST) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdjacentChest(Location signLoc, Location chestLoc) {
        return Math.abs(signLoc.getX() - chestLoc.getX()) <= 1 &&
                Math.abs(signLoc.getY() - chestLoc.getY()) <= 1 &&
                Math.abs(signLoc.getZ() - chestLoc.getZ()) <= 1;
    }

    private int countItemsInInventory(Player player, Material type) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == type) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItemsFromInventory(Player player, Material type, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == type) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }

    /**
     * Save shop to database (async)
     */
    private void saveShopToDatabase(ChestShop shop) {
        CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO chest_shops 
                (world, x, y, z, owner_uuid, owner_name, item_type, buy_price, sell_price, stock)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, shop.getSignLocation().getWorld().getName());
                stmt.setInt(2, shop.getSignLocation().getBlockX());
                stmt.setInt(3, shop.getSignLocation().getBlockY());
                stmt.setInt(4, shop.getSignLocation().getBlockZ());
                stmt.setString(5, shop.getOwner().toString());
                stmt.setString(6, getOwnerName(shop));
                stmt.setString(7, shop.getItemType().name());
                stmt.setDouble(8, shop.getBuyPrice());
                stmt.setDouble(9, shop.getSellPrice());
                stmt.setInt(10, shop.getAmount());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("§c[ChestShop] Failed to save shop: " + e.getMessage());
            }
        });
    }

    /**
     * Get shop at location
     */
    public ChestShop getShopAt(Location loc) {
        return activeShops.get(loc);
    }

    /**
     * Remove shop
     */
    public void removeShop(Location loc) {
        activeShops.remove(loc);

        CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM chest_shops WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, loc.getWorld().getName());
                stmt.setInt(2, loc.getBlockX());
                stmt.setInt(3, loc.getBlockY());
                stmt.setInt(4, loc.getBlockZ());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("§c[ChestShop] Failed to delete shop: " + e.getMessage());
            }
        });
    }

    /**
     * Get all shops owned by player
     */
    public List<ChestShop> getShopsByOwner(UUID ownerUuid) {
        return activeShops.values().stream()
                .filter(shop -> shop.getOwner().equals(ownerUuid))
                .toList();
    }
}