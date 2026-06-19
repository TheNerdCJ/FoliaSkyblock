package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.enchant.CustomEnchantment;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom Enchanting Table GUI for FoliaSkyblock
 *
 * Features:
 * - No enchantment level limit (up to X)
 * - Custom Skyblock enchantments
 * - XP costs scaled for Skyblock economy
 * - Enchantment books with custom levels
 */
public class EnchantingTableGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public EnchantingTableGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public EnchantingTableGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, ItemStack itemToEnchant) {
        if (itemToEnchant == null || itemToEnchant.getType() == Material.AIR) {
            player.sendMessage("§cYou must hold an item to enchant!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§5§lEnchanting Table §7(Custom Levels)"));

        // Header
        gui.setItem(4, GUIUtils.createItem(Material.ENCHANTING_TABLE, "§5§lEnchanting Table",
                "§7Enchant your items with powerful magic!",
                "§7No level limits - Up to X enchantments!",
                "",
                "§eClick an enchantment to apply it",
                "§7Navigation: Click options or Close button below."));

        // Item to enchant display
        gui.setItem(22, itemToEnchant.clone());

        // Get applicable enchantments
        List<CustomEnchantment> applicable = CustomEnchantment.getApplicableEnchantments(itemToEnchant.getType());

        // Display enchantment options (slots 10-44, 6 per row)
        int slot = 10;
        for (CustomEnchantment enchant : applicable) {
            if (slot > 44) break;

            int currentLevel = enchant.getLevel(itemToEnchant);
            int nextLevel = currentLevel + 1;

            if (nextLevel > enchant.getMaxLevel()) {
                // Maxed out
                gui.setItem(slot, GUIUtils.createItem(Material.BARRIER,
                        "§c§l" + enchant.getDisplayName() + " §7(MAX)",
                        "§7Level: §c" + enchant.getMaxLevel() + "§7/§c" + enchant.getMaxLevel(),
                        "§7" + enchant.getDescription(),
                        "",
                        "§cThis enchantment is maxed out!"));
            } else {
                // Can be upgraded
                int xpCost = calculateXPCost(enchant, nextLevel);
                boolean canAfford = player.getLevel() >= xpCost;

                Material icon = canAfford ? Material.ENCHANTED_BOOK : Material.BOOK;
                String status = canAfford ? "§a§lClick to enchant!" : "§c§lNot enough XP!";

                gui.setItem(slot, GUIUtils.createItem(icon,
                        enchant.getColorCode() + "§l" + enchant.getDisplayName() + " " +
                                (nextLevel > 1 ? CustomEnchantment.toRoman(nextLevel) : ""),
                        "§7Level: §e" + nextLevel + "§7/§e" + enchant.getMaxLevel(),
                        "§7" + enchant.getDescription(),
                        "",
                        "§7Cost: §e" + xpCost + " levels §7+ §e$" + (xpCost * 10),
                        status));
            }
            slot++;

            // Skip to next row
            if ((slot - 10) % 7 == 0) slot += 2;
        }

        // Close button
        gui.setItem(49, GUIUtils.createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));

        player.openInventory(gui);
    }

    private int calculateXPCost(CustomEnchantment enchant, int level) {
        // Base cost increases with level
        int baseCost = 5;

        // Higher levels cost more
        int levelMultiplier = level * 2;

        // Custom enchantments cost more
        int customMultiplier = enchant.isVanilla() ? 1 : 2;

        // Max level enchantments cost extra
        int maxLevelBonus = level >= enchant.getMaxLevel() ? 10 : 0;

        return Math.max(1, (baseCost + levelMultiplier + maxLevelBonus) * customMultiplier);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("§5§lEnchanting Table")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Close button
        if (clicked.getType() == Material.BARRIER && clicked.getItemMeta() != null &&
                clicked.getItemMeta().getDisplayName().contains("Close")) {
            player.closeInventory();
            return;
        }

        // Get the item being enchanted (in slot 22)
        ItemStack itemToEnchant = event.getInventory().getItem(22);
        if (itemToEnchant == null) return;

        // Find which enchantment was clicked
        String displayName = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "";

        for (CustomEnchantment enchant : CustomEnchantment.values()) {
            if (displayName.contains(enchant.getDisplayName())) {
                // Apply enchantment
                int currentLevel = enchant.getLevel(itemToEnchant);
                int nextLevel = currentLevel + 1;

                if (nextLevel > enchant.getMaxLevel()) {
                    player.sendMessage("§cThis enchantment is already at max level!");
                    return;
                }

                int xpCost = calculateXPCost(enchant, nextLevel);

                if (player.getLevel() < xpCost) {
                    player.sendMessage("§cYou need §e" + xpCost + " levels§c to enchant!");
                    return;
                }

                // Deduct XP levels
                player.setLevel(player.getLevel() - xpCost);

                // Also deduct from player balance (hybrid cost)
                double balanceCost = xpCost * 10; // $10 per XP level
                plugin.getEconomyManager().removePlayerBalance(player.getUniqueId(), balanceCost);

                // Apply enchantment (upgraded PDC + lore)
                plugin.getEnchantmentManager().applyEnchantment(itemToEnchant, enchant, nextLevel);

                // Update the display
                event.getInventory().setItem(22, itemToEnchant);

                // Play sound
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

                // Refresh GUI
                player.sendMessage("§a§lEnchanted! §7" + enchant.getDisplayName() + " " +
                        CustomEnchantment.toRoman(nextLevel) + " applied! §7(Cost: §e" + xpCost + " levels §7+ §e$" + (xpCost * 10) + "§7)");

                // Reopen to refresh
                plugin.getThreadSafety().runOnMainThread(() -> {
                    player.closeInventory();
                    open(player, itemToEnchant);
                });

                return;
            }
        }
    }
}