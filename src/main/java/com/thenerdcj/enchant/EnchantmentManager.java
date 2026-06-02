package com.thenerdcj.enchant;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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
    private final NamespacedKey CUSTOM_ENCHANT_PREFIX_KEY;

    public EnchantmentManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.CUSTOM_ENCHANT_PREFIX_KEY = new NamespacedKey(plugin, "custom_enchant");
    }

    private NamespacedKey getCustomEnchantKey(CustomEnchantment enchant) {
        return new NamespacedKey(plugin, "custom_enchant_" + enchant.name().toLowerCase());
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
     * Apply enchantment using upgraded storage (PDC for customs + lore for display).
     * This is the recommended way; upgrades the old lore-only for custom enchants.
     */
    public void applyEnchantment(ItemStack item, CustomEnchantment enchantment, int level) {
        if (level < 1 || level > enchantment.getMaxLevel()) return;

        if (enchantment.isVanilla()) {
            item.addUnsafeEnchantment(enchantment.getVanillaEnchant(), level);
        } else {
            // Custom: store in PDC (authoritative) + update lore for display
            if (item.getItemMeta() == null) return;
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(getCustomEnchantKey(enchantment), PersistentDataType.INTEGER, level);

            // Update lore for visibility (remove old, add new)
            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();
            lore.removeIf(line -> line.contains(enchantment.getDisplayName()));
            lore.add(enchantment.getColorCode() + enchantment.getDisplayName() + " " + CustomEnchantment.toRoman(level));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
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

        // Apply (upgraded storage)
        this.applyEnchantment(item, enchant, level);

        player.sendMessage("§a§lEnchanted! §7" + enchant.getDisplayName() + " " +
                CustomEnchantment.toRoman(level) + " applied!");
    }

    /**
     * Get all enchantments on an item (both vanilla and custom)
     * Upgraded: customs read from PDC first (reliable), fallback to lore for old items.
     */
    public Map<CustomEnchantment, Integer> getAllEnchantments(ItemStack item) {
        Map<CustomEnchantment, Integer> enchantments = new HashMap<>();

        if (item == null || item.getItemMeta() == null) return enchantments;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Get vanilla enchantments
        for (var entry : meta.getEnchants().entrySet()) {
            for (CustomEnchantment custom : CustomEnchantment.values()) {
                if (custom.isVanilla() && custom.getVanillaEnchant() == entry.getKey()) {
                    enchantments.put(custom, entry.getValue());
                    break;
                }
            }
        }

        // Get custom enchantments from PDC (preferred) or lore (legacy)
        for (CustomEnchantment custom : CustomEnchantment.values()) {
            if (custom.isVanilla()) continue;
            Integer pdcLevel = pdc.get(getCustomEnchantKey(custom), PersistentDataType.INTEGER);
            if (pdcLevel != null && pdcLevel > 0) {
                enchantments.put(custom, pdcLevel);
                continue;
            }
            // legacy lore fallback
            if (meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line.contains(custom.getDisplayName())) {
                        int level = custom.getLevel(item); // uses lore parse
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
     * Upgraded to check PDC for customs.
     */
    public boolean hasEnchantment(ItemStack item, CustomEnchantment enchantment) {
        if (enchantment.isVanilla()) {
            return item.getEnchantmentLevel(enchantment.getVanillaEnchant()) > 0;
        }
        if (item == null || item.getItemMeta() == null) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer lvl = pdc.get(getCustomEnchantKey(enchantment), PersistentDataType.INTEGER);
        if (lvl != null && lvl > 0) return true;
        return enchantment.getLevel(item) > 0; // legacy
    }

    /**
     * Remove an enchantment from an item
     * Upgraded: removes from PDC for customs.
     */
    public void removeEnchantment(ItemStack item, CustomEnchantment enchantment) {
        if (enchantment.isVanilla()) {
            item.removeEnchantment(enchantment.getVanillaEnchant());
        } else {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.remove(getCustomEnchantKey(enchantment));
                // Remove from lore too
                if (meta.getLore() != null) {
                    List<String> lore = new ArrayList<>(meta.getLore());
                    lore.removeIf(line -> line.contains(enchantment.getDisplayName()));
                    meta.setLore(lore);
                }
                item.setItemMeta(meta);
            }
        }
    }

    /**
     * Create a random enchantment book (for loot/rewards)
     */
    public ItemStack createRandomEnchantmentBook(int minLevel, int maxLevel) {
        CustomEnchantment[] values = CustomEnchantment.values();
        CustomEnchantment enchant = values[new Random().nextInt(values.length)];

        int level = minLevel + new Random().nextInt(Math.min(maxLevel, enchant.getMaxLevel()) - minLevel + 1);

        ItemStack book = createEnchantmentBook(enchant, level);
        // Note: books are display only; real apply uses applyEnchantment on target item
        return book;
    }
}
