package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.enchant.CustomEnchantment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Anvil Listener for FoliaSkyblock
 *
 * Features:
 * - No repair cost limit (can repair any item regardless of prior repairs)
 * - Custom enchantment combining with higher levels
 * - No level cap on combined enchantments
 * - Repair using player balance instead of XP levels
 */
public class AnvilListener implements Listener {

    private final FoliaSkyblock plugin;

    public AnvilListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Registration handled centrally in FoliaSkyblock to avoid double-listener issues
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getItem(0);
        ItemStack second = inventory.getItem(1);

        if (first == null) return;

        // Handle repair (item + same item or repair material)
        if (second != null && canRepair(first, second)) {
            ItemStack result = first.clone();

            // Remove repair cost limit - always allow repair
            if (result.getItemMeta() instanceof Repairable repairable) {
                repairable.setRepairCost(0); // Reset repair cost
            }

            // Repair the item (restore durability)
            if (second.getType() == first.getType()) {
                // Combining two of the same item
                int maxDurability = first.getType().getMaxDurability();
                int currentDurability = maxDurability - first.getDurability();
                int repairAmount = (int) (maxDurability * 0.25); // Repair 25% per item

                int newDurability = Math.max(0, first.getDurability() - repairAmount);
                result.setDurability((short) newDurability);
            } else if (isRepairMaterial(first.getType(), second.getType())) {
                // Using repair material (iron ingot, diamonds, etc.)
                int maxDurability = first.getType().getMaxDurability();
                int repairAmount = (int) (maxDurability * 0.25);

                int newDurability = Math.max(0, first.getDurability() - repairAmount);
                result.setDurability((short) newDurability);
            }

            event.setResult(result);
            return;
        }

        // Handle enchantment combining (book + item, or item + item)
        if (second != null) {
            ItemStack result = combineEnchantments(first, second);
            if (result != null) {
                event.setResult(result);
            }
        }
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof AnvilInventory)) return;

        // Only handle result slot (slot 2)
        if (event.getSlot() != 2) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

        AnvilInventory anvil = (AnvilInventory) event.getInventory();
        ItemStack first = anvil.getItem(0);
        ItemStack second = anvil.getItem(1);

        if (first == null) return;

        // Calculate cost
        int cost = calculateAnvilCost(first, second, result);

        // Check if player can afford (hybrid: XP levels + player balance)
        int xpCost = Math.max(1, cost / 10); // 1 XP level per $10
        double balanceCost = cost;

        // Async balance check to avoid blocking main thread
        plugin.getEconomyManager().getBalance(player.getUniqueId()).thenAccept(playerBalance -> {
            if (player.getLevel() < xpCost) {
                plugin.getThreadSafety().sendMessageSafely(player, "§cYou need §e" + xpCost + " XP levels§c to use the anvil!");
                event.setCancelled(true);
                return;
            }

            if (playerBalance < balanceCost) {
                plugin.getThreadSafety().sendMessageSafely(player, "§cYou need §e$" + balanceCost + "§c to use the anvil!");
                event.setCancelled(true);
                return;
            }

            // Deduct XP levels (must be on main)
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.setLevel(player.getLevel() - xpCost);
            });

            // Deduct from player balance (async is fine)
            plugin.getEconomyManager().removeBalance(player.getUniqueId(), balanceCost).thenAccept(success -> {
                if (success) {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

                    plugin.getThreadSafety().runOnMainThread(() -> {
                        anvil.setItem(0, null);
                        if (second != null) {
                            second.setAmount(second.getAmount() - 1);
                            if (second.getAmount() <= 0) {
                                anvil.setItem(1, null);
                            }
                        }
                    });

                    plugin.getThreadSafety().sendMessageSafely(player, "§a§lAnvil used! §7Cost: §e" + xpCost + " levels §7+ §e$" + balanceCost);
                }
            });
        });
    }

    private boolean canRepair(ItemStack item, ItemStack repairItem) {
        // Same item type (combining)
        if (item.getType() == repairItem.getType()) return true;

        // Repair materials
        return isRepairMaterial(item.getType(), repairItem.getType());
    }

    private boolean isRepairMaterial(Material itemType, Material repairType) {
        String itemName = itemType.name();
        String repairName = repairType.name();

        // Iron tools/armor -> Iron Ingot
        if ((itemName.contains("IRON") || itemName.contains("CHAINMAIL")) && repairName.equals("IRON_INGOT")) {
            return true;
        }

        // Diamond tools/armor -> Diamond
        if (itemName.contains("DIAMOND") && repairName.equals("DIAMOND")) {
            return true;
        }

        // Gold tools/armor -> Gold Ingot
        if (itemName.contains("GOLD") && repairName.equals("GOLD_INGOT")) {
            return true;
        }

        // Leather armor -> Leather
        if (itemName.contains("LEATHER") && repairName.equals("LEATHER")) {
            return true;
        }

        // Stone tools -> Cobblestone
        if ((itemName.contains("STONE") || itemName.contains("WOODEN")) && repairName.equals("COBBLESTONE")) {
            return true;
        }

        // Netherite -> Netherite Ingot
        if (itemName.contains("NETHERITE") && repairName.equals("NETHERITE_INGOT")) {
            return true;
        }

        return false;
    }

    private ItemStack combineEnchantments(ItemStack first, ItemStack second) {
        ItemStack result = first.clone();

        // Get enchantments from second item (could be book or another item)
        var secondMeta = second.getItemMeta();
        if (secondMeta == null) return null;

        var resultMeta = result.getItemMeta();
        if (resultMeta == null) return null;

        boolean changed = false;

        // Combine vanilla enchantments
        for (var entry : secondMeta.getEnchants().entrySet()) {
            var enchant = entry.getKey();
            int level1 = resultMeta.getEnchantLevel(enchant);
            int level2 = entry.getValue();

            // Take the higher level, or add 1 if same level (up to max)
            int newLevel = Math.max(level1, level2);
            if (level1 == level2 && level1 < enchant.getMaxLevel()) {
                newLevel = level1 + 1;
            }

            // Allow higher than vanilla max (up to 10 for most)
            int maxAllowed = Math.min(10, enchant.getMaxLevel() * 2);
            newLevel = Math.min(newLevel, maxAllowed);

            resultMeta.addEnchant(enchant, newLevel, true);
            changed = true;
        }

        // Combine custom enchantments from lore
        if (secondMeta.getLore() != null) {
            List<String> resultLore = resultMeta.getLore();
            if (resultLore == null) resultLore = new ArrayList<>();

            for (String line : secondMeta.getLore()) {
                // Check if this line contains a custom enchantment
                for (CustomEnchantment custom : CustomEnchantment.values()) {
                    if (!custom.isVanilla() && line.contains(custom.getDisplayName())) {
                        int level1 = custom.getLevel(result);
                        int level2 = custom.getLevel(second);

                        int newLevel = Math.max(level1, level2);
                        if (level1 == level2 && level1 < custom.getMaxLevel()) {
                            newLevel = level1 + 1;
                        }

                        newLevel = Math.min(newLevel, custom.getMaxLevel());

                        // Remove old level
                        resultLore.removeIf(l -> l.contains(custom.getDisplayName()));

                        // Add new level
                        resultLore.add(custom.getColorCode() + custom.getDisplayName() + " " +
                                CustomEnchantment.toRoman(newLevel));

                        changed = true;
                        break;
                    }
                }
            }

            resultMeta.setLore(resultLore);
        }

        if (!changed) return null;

        result.setItemMeta(resultMeta);
        return result;
    }

    private int calculateAnvilCost(ItemStack first, ItemStack second, ItemStack result) {
        int cost = 0;

        // Base cost for using anvil
        cost += 10;

        // Cost for repairs
        if (second != null && canRepair(first, second)) {
            cost += 20;
        }

        // Cost for enchantment combining
        if (second != null && second.getItemMeta() != null) {
            int enchantCount = second.getItemMeta().getEnchants().size();
            cost += enchantCount * 15;

            // Extra cost for custom enchantments
            if (second.getItemMeta().getLore() != null) {
                for (String line : second.getItemMeta().getLore()) {
                    for (CustomEnchantment custom : CustomEnchantment.values()) {
                        if (!custom.isVanilla() && line.contains(custom.getDisplayName())) {
                            cost += 25; // Custom enchantments cost more
                            break;
                        }
                    }
                }
            }
        }

        // Cost based on result item rarity
        String resultName = result.getType().name();
        if (resultName.contains("NETHERITE")) cost += 50;
        else if (resultName.contains("DIAMOND")) cost += 30;
        else if (resultName.contains("IRON")) cost += 15;

        return Math.max(5, cost); // Minimum cost of 5
    }
}