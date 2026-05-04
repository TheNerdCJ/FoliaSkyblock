package com.thenerdcj.shop;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Pattern;

/**
 * ChestShopListener - Handles all chest shop interactions
 *
 * Features:
 * - Sign placement validation
 * - Auto-correct sign format
 * - Buy/Sell transactions
 * - Shop removal protection
 */
public class ChestShopListener implements Listener {

    private final FoliaSkyblock plugin;
    private final Pattern SHOP_PATTERN = Pattern.compile("(?i)^\\[shop\\]$");

    public ChestShopListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle sign placement - Create shop from sign
     */
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        String[] lines = event.getLines();

        // Check if this is a shop sign
        if (!SHOP_PATTERN.matcher(lines[0]).matches()) {
            return;
        }

        // Validate format: Line 0 = [Shop], Line 1 = Item, Line 2 = B:price S:price, Line 3 = PlayerName
        if (lines[1].isEmpty() || lines[2].isEmpty()) {
            player.sendMessage("§cInvalid shop format!");
            player.sendMessage("§7Line 1: Item name (e.g., DIAMOND)");
            player.sendMessage("§7Line 2: B:10 S:5 (buy $10, sell $5)");
            event.setCancelled(true);
            return;
        }

        // Parse prices from line 2
        double buyPrice = 0, sellPrice = 0;
        try {
            String priceLine = lines[2].toUpperCase().replace(" ", "");
            if (priceLine.contains("B:")) {
                String[] parts = priceLine.split("B:")[1].split("S:");
                buyPrice = Double.parseDouble(parts[0].replace("$", ""));
                if (parts.length > 1) {
                    sellPrice = Double.parseDouble(parts[1].replace("$", ""));
                }
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid price format! Use: B:10 S:5");
            event.setCancelled(true);
            return;
        }

        // Parse item type
        Material itemType;
        try {
            itemType = Material.valueOf(lines[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid item: " + lines[1]);
            event.setCancelled(true);
            return;
        }

        // SECURITY: Prevent setting other players' names
        String intendedOwner = lines[3].isEmpty() ? player.getName() : lines[3];
        if (!intendedOwner.equalsIgnoreCase(player.getName()) && !player.hasPermission("foliaskyblock.admin")) {
            player.sendMessage("§c§lSECURITY: You can only create shops for yourself!");
            player.sendMessage("§7Line 4 will be set to your name automatically.");
            intendedOwner = player.getName();
        }

        // Create the shop
        Location signLoc = event.getBlock().getLocation();
        boolean success = plugin.getChestShopManager().createShop(
                player, signLoc, itemType, buyPrice, sellPrice
        );

        if (success) {
            // Update sign with correct format
            event.setLine(0, "§1[Shop]");
            event.setLine(1, "§0" + itemType.name());
            event.setLine(2, "§aB: §2$" + String.format("%.0f", buyPrice) + " §cS: §4$" + String.format("%.0f", sellPrice));
            event.setLine(3, "§0" + player.getName());
            player.sendMessage("§a§l✓ Shop created successfully!");
        } else {
            event.setCancelled(true);
        }
    }

    /**
     * Handle player interaction with shop sign
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Check if clicked on sign
        if (!(block.getState() instanceof Sign sign)) return;

        Location loc = block.getLocation();
        ChestShop shop = plugin.getChestShopManager().getShopAt(loc);
        if (shop == null) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        // Auto-correct sign format if owner is nearby
        if (shop.getOwner().equals(player.getUniqueId())) {
            plugin.getChestShopManager().autoCorrectSignFormat(player, sign);
        }

        // Show shop info
        player.sendMessage("§6§l=== SHOP INFO ===");
        player.sendMessage("§7Owner: §e" + getOwnerName(shop));
        player.sendMessage("§7Item: §e" + shop.getItemType().name());
        player.sendMessage("§7Buy Price: §a$" + shop.getBuyPrice() + " §7each");
        player.sendMessage("§7Sell Price: §c$" + shop.getSellPrice() + " §7each");
        player.sendMessage("§7Stock: §e" + shop.getAmount());

        // Show instructions
        if (!shop.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("");
            player.sendMessage("§7Left-click with item to §cSELL");
            player.sendMessage("§7Right-click to §aBUY");
        } else {
            player.sendMessage("");
            player.sendMessage("§7This is your shop!");
            player.sendMessage("§7Stock updates automatically when you add/remove items.");
        }
    }

    /**
     * Handle buying (right-click on sign with empty hand)
     */
    @EventHandler
    public void onShopPurchase(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;

        ChestShop shop = plugin.getChestShopManager().getShopAt(block.getLocation());
        if (shop == null) return;

        Player player = event.getPlayer();

        // Don't allow owner to buy from own shop
        if (shop.getOwner().equals(player.getUniqueId())) {
            return;
        }

        // Buy 1 item by default
        plugin.getChestShopManager().purchaseFromShop(player, block.getLocation(), 1);
        event.setCancelled(true);
    }

    /**
     * Handle selling (left-click on sign with item in hand)
     */
    @EventHandler
    public void onShopSell(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;

        ChestShop shop = plugin.getChestShopManager().getShopAt(block.getLocation());
        if (shop == null) return;

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        // Don't allow owner to sell to own shop
        if (shop.getOwner().equals(player.getUniqueId())) {
            return;
        }

        // Check if holding the correct item
        if (heldItem.getType() != shop.getItemType() || heldItem.getAmount() == 0) {
            player.sendMessage("§cYou must hold " + shop.getItemType().name() + " to sell!");
            return;
        }

        // Sell all held items
        int amount = heldItem.getAmount();
        plugin.getChestShopManager().sellToShop(player, block.getLocation(), amount);
        event.setCancelled(true);
    }

    /**
     * Protect shop signs from being broken by non-owners
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if breaking a shop sign
        if (block.getState() instanceof Sign) {
            ChestShop shop = plugin.getChestShopManager().getShopAt(block.getLocation());
            if (shop != null) {
                if (!shop.getOwner().equals(player.getUniqueId()) && !player.hasPermission("foliaskyblock.admin")) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou cannot break this shop! Only the owner can.");
                    return;
                }

                // Owner is breaking their shop
                plugin.getChestShopManager().removeShop(block.getLocation());
                player.sendMessage("§aShop removed.");
            }
        }

        // Check if breaking a chest that has a shop
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            // Find adjacent shop sign
            Location[] adjacent = {
                    block.getLocation().add(0, 1, 0),
                    block.getLocation().add(1, 0, 0),
                    block.getLocation().add(-1, 0, 0),
                    block.getLocation().add(0, 0, 1),
                    block.getLocation().add(0, 0, -1)
            };

            for (Location loc : adjacent) {
                ChestShop shop = plugin.getChestShopManager().getShopAt(loc);
                if (shop != null) {
                    if (!shop.getOwner().equals(player.getUniqueId()) && !player.hasPermission("foliaskyblock.admin")) {
                        event.setCancelled(true);
                        player.sendMessage("§cYou cannot break this shop chest! Only the owner can.");
                        return;
                    }

                    // Remove the shop when chest is broken
                    plugin.getChestShopManager().removeShop(loc);
                    player.sendMessage("§cShop removed (chest broken).");
                }
            }
        }
    }

    /**
     * Helper method to get owner name from shop
     */
    private String getOwnerName(ChestShop shop) {
        Player owner = Bukkit.getPlayer(shop.getOwner());
        if (owner != null) {
            return owner.getName();
        }
        return "Unknown";
    }
}