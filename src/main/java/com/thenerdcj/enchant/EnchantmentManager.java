package com.thenerdcj.enchant;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Enchantment Manager for FoliaSkyblock
 *
 * Handles:
 * - Custom enchantment application
 * - Enchantment book creation
 * - XP cost calculations
 * - Integration with enchanting table and anvil
 */
public class EnchantmentManager {

    private final FoliaSkyblock plugin;

    public EnchantmentManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Create an enchantment book with a specific enchantment and level
     */
    public ItemStack createEnchantmentBook(CustomEnchantment enchantment, int level) {
        if (level < 1 || level > enchantment.getMaxLevel()) {
            level = Math.min(Math.max(1, level), enchantment.getMaxLevel());
        }

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(enchantment.getColorCode() + "§l" + enchantment.getDisplayName() + " " +
                    CustomEnchantment.toRoman(level));

            List<String> lore = new ArrayList<>();
            lore.add("§7" + enchantment.getDescription());
            lore.add("");
            lore.add("§7Level: §e" + level + "§7/§e" + enchantment.getMaxLevel());
            lore.add("§7Right-click to apply to item");
            lore.add("");
            lore.add("§8Skyblock Custom Enchantment");

            meta.setLore(lore);
            book.setItemMeta(meta);
        }

        return book;
    }

    /**
     * Apply a random enchantment to an item (for enchanting table)
     */
    public void applyRandomEnchantment(ItemStack item, Player player) {
        List<CustomEnchantment> applicable = CustomEnchantment.getApplicableEnchantments(item.getType());

        if (applicable.isEmpty()) return;

        // Pick a random enchantment
        CustomEnchantment enchant = applicable.get(new Random().nextInt(applicable.size()));

        // Determine level (weighted towards lower levels)
        int maxLevel = Math.min(enchant.getMaxLevel(), 5);
        int level = 1 + new Random().nextInt(maxLevel);

        // Apply
        enchant.apply(item, level);

        player.sendMessage("§a§lEnchanted! §7" + enchant.getDisplayName() + " " +
                CustomEnchantment.toRoman(level) + " applied!");
    }

    /**
     * Get all enchantments on an item (both vanilla and custom)
     */
    public Map<CustomEnchantment, Integer> getAllEnchantments(ItemStack item) {
        Map<CustomEnchantment, Integer> enchantments = new HashMap<>();

        if (item == null || item.getItemMeta() == null) return enchantments;

        // Get vanilla enchantments
        for (var entry : item.getItemMeta().getEnchants().entrySet()) {
            for (CustomEnchantment custom : CustomEnchantment.values()) {
                if (custom.isVanilla() && custom.getVanillaEnchant() == entry.getKey()) {
                    enchantments.put(custom, entry.getValue());
                    break;
                }
            }
        }

        // Get custom enchantments from lore
        if (item.getItemMeta().getLore() != null) {
            for (String line : item.getItemMeta().getLore()) {
                for (CustomEnchantment custom : CustomEnchantment.values()) {
                    if (!custom.isVanilla() && line.contains(custom.getDisplayName())) {
                        int level = custom.getLevel(item);
                        if (level > 0) {
                            enchantments.put(custom, level);
                        }
                        break;
                    }
                }
            }
        }

        return enchantments;
    }

    /**
     * Check if an item has a specific enchantment
     */
    public boolean hasEnchantment(ItemStack item, CustomEnchantment enchantment) {
        return enchantment.getLevel(item) > 0;
    }

    /**
     * Remove an enchantment from an item
     */
    public void removeEnchantment(ItemStack item, CustomEnchantment enchantment) {
        if (enchantment.isVanilla()) {
            item.removeEnchantment(enchantment.getVanillaEnchant());
        } else {
            // Remove from lore
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                List<String> lore = new ArrayList<>(meta.getLore());
                lore.removeIf(line -> line.contains(enchantment.getDisplayName()));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }

    /**
     * Get the total enchantment power of an item (for balancing)
     */
    public int getEnchantmentPower(ItemStack item) {
        int power = 0;

        Map<CustomEnchantment, Integer> enchants = getAllEnchantments(item);
        for (var entry : enchants.entrySet()) {
            // Higher level enchantments contribute more power
            power += entry.getValue() * (entry.getKey().isVanilla() ? 1 : 2);
        }

        return power;
    }

    /**
     * Create a random enchantment book (for loot/rewards)
     */
    public ItemStack createRandomEnchantmentBook(int minLevel, int maxLevel) {
        CustomEnchantment[] values = CustomEnchantment.values();
        CustomEnchantment enchant = values[new Random().nextInt(values.length)];

        int level = minLevel + new Random().nextInt(Math.min(maxLevel, enchant.getMaxLevel()) - minLevel + 1);

        return createEnchantmentBook(enchant, level);
    }
}
