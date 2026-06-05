package com.thenerdcj.shop;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.ChestShopDAO;
import com.thenerdcj.database.ChestShopSaveCoalescer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * ChestShopManager - Complete Chest Shop System
 *
 * Features:
 * - Player economy integration (not island balance)
 * - Sign format: [Shop] / Buy: X / Sell: Y / PlayerName
 * - Auto-correct sign format when player is nearby
 * - Prevents players from setting other players' names
 * - Database persistence via {@link ChestShopDAO}
 * - Async operations for Folia
 */
public class ChestShopManager {

    private final FoliaSkyblock plugin;
    private final ChestShopDAO chestShopDAO;
    private final Map<Location, ChestShop> activeShops = new ConcurrentHashMap<>();
    /** In-memory chunk index for O(shops-in-chunk) lookups (lazy-load / region scans). */
    private final Map<String, Set<Location>> shopsByChunk = new ConcurrentHashMap<>();
    private final Set<Location> hydrating = ConcurrentHashMap.newKeySet();
    private final int maxLoadedShops;
    private final boolean lazyLoadShops;
    private final boolean coalesceShopSaves;
    private final ChestShopSaveCoalescer shopSaveCoalescer = new ChestShopSaveCoalescer();
    private final Set<String> hydratingChunkKeys = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastChunkHydrateMs = new ConcurrentHashMap<>();
    /** Per-chunk DB hydrate cooldown (allows re-hydrate after eviction, unlike a permanent set). */
    private final Map<String, Long> chunkHydrateCooldownUntil = new ConcurrentHashMap<>();
    private final boolean hydrateShopsOnChunkEnter;
    private final long chunkHydrateDebounceMs;
    private final long chunkHydrateCooldownMs;

    public ChestShopManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.chestShopDAO = plugin.getDatabaseManager().getChestShopDAO();
        this.maxLoadedShops = plugin.getConfig() != null
                ? plugin.getConfig().getInt("island.perf.max-chest-shops-loaded", 5000)
                : 5000;
        this.lazyLoadShops = plugin.getConfig() != null
                && plugin.getConfig().getBoolean("island.perf.lazy-load-chest-shops", false);
        this.coalesceShopSaves = plugin.getConfig() != null
                && plugin.getConfig().getBoolean("island.perf.coalesce-chest-shop-saves", false);
        this.hydrateShopsOnChunkEnter = plugin.getConfig() != null
                && plugin.getConfig().getBoolean("island.perf.hydrate-shops-on-chunk-enter", true);
        this.chunkHydrateDebounceMs = plugin.getConfig() != null
                ? plugin.getConfig().getLong("island.perf.hydrate-shops-debounce-ms", 500L)
                : 500L;
        this.chunkHydrateCooldownMs = plugin.getConfig() != null
                ? plugin.getConfig().getLong("island.perf.hydrate-shops-chunk-cooldown-ms", 60_000L)
                : 60_000L;
        if (lazyLoadShops) {
            plugin.getLogger().info("[ChestShop] Lazy hydration enabled (shops load on interact, cap "
                    + maxLoadedShops + " in memory).");
            if (hydrateShopsOnChunkEnter) {
                Bukkit.getPluginManager().registerEvents(new ShopChunkHydrateListener(), plugin);
                plugin.getLogger().info("[ChestShop] Chunk-enter bulk hydrate enabled (loadByChunk).");
            }
        } else {
            loadAllShops();
        }
        if (coalesceShopSaves && plugin.getThreadSafety() != null) {
            long intervalMs = plugin.getConfig().getLong("island.perf.coalesce-shop-save-interval-ms", 500L);
            long periodTicks = Math.max(1L, intervalMs / 50L);
            plugin.getThreadSafety().runRepeatingOnMainThread(() ->
                    plugin.getThreadSafety().runAsync(this::flushCoalescedShopSaves), periodTicks, periodTicks);
            plugin.getLogger().info("[ChestShop] Save coalescing enabled (interval " + intervalMs + "ms).");
        }
    }

    public int getPendingShopSaveCount() {
        return shopSaveCoalescer.pendingCount();
    }

    public void flushCoalescedShopSaves() {
        if (!shopSaveCoalescer.hasPending() || chestShopDAO == null) {
            return;
        }
        var batch = shopSaveCoalescer.drain();
        chestShopDAO.flushBatch(batch).thenAccept(ok -> {
            if (!ok) {
                shopSaveCoalescer.requeue(batch);
            }
        });
    }

    /** @deprecated Use {@link com.thenerdcj.database.DatabaseManager#createTables()}. */
    @Deprecated
    public static void createTable(FoliaSkyblock plugin) {
        ChestShopDAO dao = plugin.getDatabaseManager().getChestShopDAO();
        if (dao != null) {
            dao.ensureTable();
        }
    }

    private void loadAllShops() {
        if (chestShopDAO == null) {
            return;
        }
        chestShopDAO.loadAll().thenAccept(rows -> {
            int loaded = ingestRows(rows, maxLoadedShops);
            plugin.getLogger().info("[ChestShop] Loaded " + loaded + " chest shops");
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[ChestShop] Failed to load shops: " + ex.getMessage());
            return null;
        });
    }

    private int ingestRows(List<ChestShopDAO.ChestShopRow> rows, int cap) {
        int loaded = 0;
        for (ChestShopDAO.ChestShopRow row : rows) {
            if (loaded >= cap) {
                plugin.getLogger().warning("[ChestShop] In-memory cap " + cap
                        + " reached; extra shops remain DB-only until lazy hydration.");
                break;
            }
            if (hydrateRow(row)) {
                loaded++;
            }
        }
        return loaded;
    }

    private boolean hydrateRow(ChestShopDAO.ChestShopRow row) {
        World world = Bukkit.getWorld(row.world());
        if (world == null) {
            return false;
        }
        Location loc = new Location(world, row.x(), row.y(), row.z());
        if (activeShops.containsKey(loc)) {
            return false;
        }
        try {
            ChestShop shop = new ChestShop(
                    row.ownerUuid(),
                    loc,
                    loc,
                    Material.valueOf(row.itemType()),
                    (int) row.buyPrice(),
                    (int) row.sellPrice(),
                    row.stock());
            indexShopLocation(loc, shop);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * Ensures a shop at the sign location is in memory (lazy-load from DB when enabled).
     */
    public CompletableFuture<ChestShop> ensureShopAt(Location signLocation) {
        if (signLocation == null || signLocation.getWorld() == null) {
            return CompletableFuture.completedFuture(null);
        }
        ChestShop cached = activeShops.get(signLocation);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (!lazyLoadShops || chestShopDAO == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!hydrating.add(signLocation)) {
            return CompletableFuture.completedFuture(activeShops.get(signLocation));
        }
        return chestShopDAO.loadAt(
                signLocation.getWorld().getName(),
                signLocation.getBlockX(),
                signLocation.getBlockY(),
                signLocation.getBlockZ()
        ).thenApply(opt -> {
            hydrating.remove(signLocation);
            if (opt.isEmpty()) {
                return null;
            }
            if (activeShops.size() < maxLoadedShops) {
                hydrateRow(opt.get());
            }
            return activeShops.get(signLocation);
        }).exceptionally(ex -> {
            hydrating.remove(signLocation);
            plugin.getLogger().warning("[ChestShop] Lazy hydrate failed: " + ex.getMessage());
            return null;
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

        indexShopLocation(signLocation, shop);

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
                indexShopLocation(signLoc, updatedShop);
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
        return ensureShopAt(signLocation).thenCompose(shop -> {
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
                            indexShopLocation(signLocation, updatedShop);
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
        });
    }

    /**
     * Handle player selling to shop - fully async where possible.
     */
    public CompletableFuture<Boolean> sellToShop(Player seller, Location signLocation, int amount) {
        return ensureShopAt(signLocation).thenCompose(shop -> {
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
                            indexShopLocation(signLocation, updatedShop);
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
        });
    }

    /**
     * Auto-correct sign format when player is nearby
     */
    public void autoCorrectSignFormat(Player player, Sign sign) {
        Location loc = sign.getLocation();
        ensureShopAt(loc).thenAccept(shop -> {
        if (shop == null) return;

        // Only correct if player is the owner or has permission
        if (!shop.getOwner().equals(player.getUniqueId()) && !player.hasPermission("foliaskyblock.admin")) {
            return;
        }

        plugin.getThreadSafety().runOnMainThread(() -> {
            updateSignDisplay(loc, shop);
            player.sendMessage("§aSign format auto-corrected!");
        });
        });
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
        if (chestShopDAO == null || shop.getSignLocation().getWorld() == null) {
            return;
        }
        Location loc = shop.getSignLocation();
        ChestShopDAO.ChestShopRow row = new ChestShopDAO.ChestShopRow(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                shop.getOwner(),
                getOwnerName(shop),
                shop.getItemType().name(),
                shop.getBuyPrice(),
                shop.getSellPrice(),
                shop.getAmount());
        if (coalesceShopSaves) {
            shopSaveCoalescer.queue(row);
            return;
        }
        chestShopDAO.save(row);
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
        untrackShop(loc);
        if (chestShopDAO == null || loc.getWorld() == null) {
            return;
        }
        chestShopDAO.delete(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Get all shops owned by player
     */
    public List<ChestShop> getShopsByOwner(UUID ownerUuid) {
        return activeShops.values().stream()
                .filter(shop -> shop.getOwner().equals(ownerUuid))
                .toList();
    }

    /**
     * Loaded shops in the chunk containing {@code location} (empty if none hydrated).
     */
    public List<ChestShop> getShopsInChunk(Location location) {
        if (location == null || location.getWorld() == null) {
            return List.of();
        }
        Set<Location> locs = shopsByChunk.get(chunkKey(location));
        if (locs == null || locs.isEmpty()) {
            return List.of();
        }
        List<ChestShop> shops = new ArrayList<>(locs.size());
        for (Location loc : locs) {
            ChestShop shop = activeShops.get(loc);
            if (shop != null) {
                shops.add(shop);
            }
        }
        return shops;
    }

    private static String chunkKey(Location loc) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        return loc.getWorld().getName() + ':' + cx + ':' + cz;
    }

    private void untrackShop(Location loc) {
        activeShops.remove(loc);
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        Set<Location> set = shopsByChunk.get(chunkKey(loc));
        if (set != null) {
            set.remove(loc);
            if (set.isEmpty()) {
                shopsByChunk.remove(chunkKey(loc));
            }
        }
    }

    private void indexShopLocation(Location loc, ChestShop shop) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        activeShops.put(loc, shop);
        shopsByChunk.computeIfAbsent(chunkKey(loc), k -> ConcurrentHashMap.newKeySet()).add(loc);
    }

    /**
     * Bulk-hydrates shops in the player's current chunk (lazy-load + memory cap).
     */
    public void hydrateChunkOnPlayerEnter(Player player) {
        if (!lazyLoadShops || !hydrateShopsOnChunkEnter || chestShopDAO == null || player == null) {
            return;
        }
        Location loc = player.getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastChunkHydrateMs.get(player.getUniqueId());
        if (last != null && now - last < chunkHydrateDebounceMs) {
            return;
        }
        lastChunkHydrateMs.put(player.getUniqueId(), now);

        String key = chunkKey(loc);
        long nowMs = System.currentTimeMillis();
        Long cooldownUntil = chunkHydrateCooldownUntil.get(key);
        if (cooldownUntil != null && nowMs < cooldownUntil) {
            return;
        }
        if (!hydratingChunkKeys.add(key)) {
            return;
        }
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        chestShopDAO.loadByChunk(loc.getWorld().getName(), cx, cz).thenAccept(rows -> {
            hydratingChunkKeys.remove(key);
            chunkHydrateCooldownUntil.put(key, System.currentTimeMillis() + chunkHydrateCooldownMs);
            plugin.getThreadSafety().runOnMainThread(() -> {
                int budget = maxLoadedShops - activeShops.size();
                for (ChestShopDAO.ChestShopRow row : rows) {
                    if (budget <= 0) {
                        break;
                    }
                    if (hydrateRow(row)) {
                        budget--;
                    }
                }
            });
        }).exceptionally(ex -> {
            hydratingChunkKeys.remove(key);
            chunkHydrateCooldownUntil.remove(key);
            return null;
        });
    }

    private final class ShopChunkHydrateListener implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onPlayerMove(PlayerMoveEvent event) {
            if (event.getTo() == null) {
                return;
            }
            Chunk from = event.getFrom().getChunk();
            Chunk to = event.getTo().getChunk();
            if (from.getX() == to.getX() && from.getZ() == to.getZ()
                    && from.getWorld().equals(to.getWorld())) {
                return;
            }
            hydrateChunkOnPlayerEnter(event.getPlayer());
        }
    }
}