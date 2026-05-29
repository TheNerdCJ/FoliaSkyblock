package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandUpgrade;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * Simple but functional Auto-Seller for the AUTO_SELLER island upgrade.
 * Sells common farm items from online island members' inventories at a reduced rate
 * and deposits the proceeds into the island bank.
 *
 * Runs on a long interval (safe for Folia).
 */
public class AutoSellerManager {

    private final FoliaSkyblock plugin;

    // Items that the auto-seller will automatically sell
    private static final Set<Material> AUTO_SELL_MATERIALS = EnumSet.of(
            Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT,
            Material.SUGAR_CANE, Material.PUMPKIN, Material.MELON_SLICE
    );

    private static final double AUTO_SELL_PRICE_MULTIPLIER = 0.75; // 75% of bazaar instant-sell price

    public AutoSellerManager(FoliaSkyblock plugin) {
        this.plugin = plugin;

        // Run every 20 minutes (safe, long-interval task)
        plugin.getThreadSafety().runRepeatingOnMainThread(this::runAutoSellCycle, 20L * 60 * 20, 20L * 60 * 20);
    }

    void runAutoSellCycle() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island == null) continue;

            int level = plugin.getIslandUpgradeManager().getUpgradeLevel(island.getId(), IslandUpgrade.AUTO_SELLER);
            if (level <= 0) continue;

            // Sell items from this player's inventory
            double earned = sellPlayerInventory(player, island);

            if (earned > 0) {
                plugin.getIslandBankManager().deposit(island.getGridPosition(), earned);
                player.sendActionBar("§7[Auto-Sell] §a+$" + String.format("%,.0f", earned) + " §7to island bank");
            }
        }
    }

    double sellPlayerInventory(Player player, Island island) {
        double total = 0;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!AUTO_SELL_MATERIALS.contains(item.getType())) continue;

            // Get bazaar instant sell price if available, otherwise use a reasonable default
            double unitPrice = getSellPrice(item.getType());
            double earned = unitPrice * item.getAmount() * AUTO_SELL_PRICE_MULTIPLIER;

            total += earned;

            // Remove the items
            player.getInventory().setItem(i, null);
        }

        return total;
    }

    double getSellPrice(Material material) {
        // Try to get from Bazaar if possible, otherwise use sensible defaults
        try {
            var bazaarItem = plugin.getBazaarManager().getBazaarItem(material.name());
            if (bazaarItem != null) {
                return bazaarItem.getSellPrice();
            }
        } catch (Exception ignored) {}

        // Fallback prices
        return switch (material) {
            case WHEAT, CARROT, POTATO, BEETROOT -> 4.0;
            case SUGAR_CANE -> 3.0;
            case PUMPKIN -> 8.0;
            case MELON_SLICE -> 2.5;
            default -> 1.0;
        };
    }
}