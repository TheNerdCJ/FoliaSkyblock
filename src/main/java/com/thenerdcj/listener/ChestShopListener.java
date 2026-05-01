package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.shop.ChestShop;
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
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ChestShop Listener - Handles shop creation and transactions
 * Uses PLAYER economy (not island economy)
 */
public class ChestShopListener implements Listener {

    private final FoliaSkyblock plugin;
    private final Map<Location, ChestShop> shops = new HashMap<>();

    public ChestShopListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Handle sign creation (shop setup)
     */
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if this is a shop sign
        if (!event.getLine(0).equalsIgnoreCase("[Shop]")) {
            return;
        }

        // Must be placed on a chest
        Block attached = getAttachedChest(block);
        if (attached == null || attached.getType() != Material.CHEST) {
            player.sendMessage("§cYou must place the shop sign on a chest!");
            event.setCancelled(true);
            return;
        }

        // Parse shop info from sign
        String priceLine = event.getLine(2);
        String itemLine = event.getLine(3);

        if (priceLine.isEmpty() || itemLine.isEmpty()) {
            player.sendMessage("§cInvalid shop format! Use:");
            player.sendMessage("§7[Shop]");
            player.sendMessage("§7B <buy>: <sell> S");
            player.sendMessage("§7<Item Name>");
            event.setCancelled(true);
            return;
        }

        // SECURITY: Force owner to be the player who placed the sign
        // This prevents players from impersonating other players and stealing their money
        UUID actualOwner = player.getUniqueId();
        String actualOwnerName = player.getName();

        // AUTO-CORRECT: Fix common formatting mistakes
        priceLine = autoCorrectPriceLine(priceLine);
        itemLine = autoCorrectItemLine(itemLine);

        // Re-validate after auto-correction
        if (priceLine.isEmpty() || itemLine.isEmpty()) {
            player.sendMessage("§cCould not auto-correct shop format. Please use: B <buy>: <sell> S");
            event.setCancelled(true);
            return;
        }

        // Parse prices (format: "B 10: 5 S")
        int buyPrice = 0;
        int sellPrice = 0;
        try {
            String[] parts = priceLine.split(":");
            if (parts.length == 2) {
                buyPrice = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                sellPrice = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid price format! Use: B <buy>: <sell> S");
            event.setCancelled(true);
            return;
        }

        // Get item type from chest
        Material itemType = getItemFromChest(attached);
        if (itemType == null) {
            player.sendMessage("§cChest must contain the item you want to sell!");
            event.setCancelled(true);
            return;
        }

        // Create shop with the ACTUAL player as owner
        ChestShop shop = new ChestShop(
                actualOwner,  // SECURITY: Always use the actual player who placed the sign
                attached.getLocation(),
                block.getLocation(),
                itemType,
                buyPrice,
                sellPrice,
                1 // Default 1 item per transaction
        );

        shops.put(block.getLocation(), shop);

        // Format the sign nicely - FORCE the correct owner name
        event.setLine(0, "§a[Shop]");
        event.setLine(1, "§e" + actualOwnerName);  // SECURITY: Always show the real owner
        event.setLine(2, "§bB " + buyPrice + ": " + sellPrice + " S");
        event.setLine(3, "§f" + itemType.name());

        player.sendMessage("§aShop created successfully!");
        player.sendMessage("§7Left-click to §abuy§7, Right-click to §csell§7.");
    }

    /**
     * Handle player interaction with shop sign
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) {
            return;
        }

        ChestShop shop = shops.get(block.getLocation());
        if (shop == null) {
            return; // Not a shop sign
        }

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // BUY
            handleBuy(player, shop);
        } else {
            // SELL
            handleSell(player, shop);
        }
    }

    private void handleBuy(Player buyer, ChestShop shop) {
        if (shop.isOwner(buyer.getUniqueId())) {
            buyer.sendMessage("§cYou cannot buy from your own shop!");
            return;
        }

        double cost = shop.getBuyTotal();
        double balance = plugin.getEconomyManager().getBalance(buyer.getUniqueId()).join();

        if (balance < cost) {
            buyer.sendMessage("§cYou don't have enough money! Cost: §6$" + cost);
            return;
        }

        // Check if chest has items
        Chest chest = (Chest) shop.getChestLocation().getBlock().getState();
        Inventory chestInv = chest.getInventory();

        if (!chestInv.contains(shop.getItemType())) {
            buyer.sendMessage("§cThis shop is out of stock!");
            return;
        }

        // Complete transaction
        plugin.getEconomyManager().removePlayerBalance(buyer.getUniqueId(), cost).join();
        plugin.getEconomyManager().addPlayerBalance(shop.getOwner(), cost).join();

        // Give item to buyer
        buyer.getInventory().addItem(new ItemStack(shop.getItemType(), shop.getAmount()));

        // Remove item from chest
        chestInv.removeItem(new ItemStack(shop.getItemType(), shop.getAmount()));

        buyer.sendMessage("§aBought §e" + shop.getAmount() + "x " + shop.getItemType().name() +
                "§a for §6$" + cost);
    }

    private void handleSell(Player seller, ChestShop shop) {
        if (shop.isOwner(seller.getUniqueId())) {
            seller.sendMessage("§cYou cannot sell to your own shop!");
            return;
        }

        if (shop.getSellPrice() <= 0) {
            seller.sendMessage("§cThis shop does not buy items!");
            return;
        }

        double earnings = shop.getSellTotal();

        // Check if seller has the item
        if (!seller.getInventory().contains(shop.getItemType())) {
            seller.sendMessage("§cYou don't have any " + shop.getItemType().name() + " to sell!");
            return;
        }

        // Complete transaction
        plugin.getEconomyManager().addPlayerBalance(seller.getUniqueId(), earnings).join();

        // Remove item from seller
        seller.getInventory().removeItem(new ItemStack(shop.getItemType(), shop.getAmount()));

        // Add item to chest
        Chest chest = (Chest) shop.getChestLocation().getBlock().getState();
        chest.getInventory().addItem(new ItemStack(shop.getItemType(), shop.getAmount()));

        seller.sendMessage("§aSold §e" + shop.getAmount() + "x " + shop.getItemType().name() +
                "§a for §6$" + earnings);
    }

    private Block getAttachedChest(Block signBlock) {
        // Check all adjacent blocks for a chest
        for (org.bukkit.block.BlockFace face : org.bukkit.block.BlockFace.values()) {
            Block relative = signBlock.getRelative(face);
            if (relative.getType() == Material.CHEST) {
                return relative;
            }
        }
        return null;
    }

    private Material getItemFromChest(Block chestBlock) {
        Chest chest = (Chest) chestBlock.getState();
        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return item.getType();
            }
        }
        return null;
    }

    // ====================== AUTO-CORRECTION HELPERS ======================

    /**
     * Auto-corrects common price line mistakes
     * Examples:
     *   "B10:5S" → "B 10: 5 S"
     *   "10:5" → "B 10: 5 S"
     *   "buy 10 sell 5" → "B 10: 5 S"
     */
    private String autoCorrectPriceLine(String line) {
        if (line == null || line.isEmpty()) return "";

        line = line.trim().toUpperCase();

        // Already correct format
        if (line.matches("B\\s*\\d+\\s*:\\s*\\d+\\s*S")) {
            return line.replaceAll("\\s+", " ");
        }

        // Extract numbers
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(line);
        java.util.List<Integer> numbers = new java.util.ArrayList<>();
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group(1)));
        }

        if (numbers.size() >= 2) {
            int buy = numbers.get(0);
            int sell = numbers.get(1);
            return "B " + buy + ": " + sell + " S";
        }

        if (numbers.size() == 1) {
            // Only one number - assume it's buy price, sell = 0
            return "B " + numbers.get(0) + ": 0 S";
        }

        return "";
    }

    /**
     * Auto-corrects item name (uppercase, remove spaces for Material enum)
     */
    private String autoCorrectItemLine(String line) {
        if (line == null || line.isEmpty()) return "";

        line = line.trim().toUpperCase().replace(" ", "_");

        // Try to find a matching Material
        try {
            Material.valueOf(line);
            return line;
        } catch (IllegalArgumentException e) {
            // Try common aliases
            if (line.equals("IRON")) return "IRON_INGOT";
            if (line.equals("GOLD")) return "GOLD_INGOT";
            if (line.equals("LAPIZ")) return "LAPIS_LAZULI";
            if (line.equals("REDSTONE")) return "REDSTONE";
        }

        return line; // Return as-is, will fail later if invalid
    }
}
