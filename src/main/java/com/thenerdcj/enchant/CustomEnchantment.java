package com.thenerdcj.enchant;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Custom Enchantment System for FoliaSkyblock
 *
 * Features:
 * - Higher enchantment levels than vanilla (up to X)
 * - Skyblock-specific enchantments
 * - No level cap on enchantments
 * - Custom XP costs for Skyblock economy
 */
public enum CustomEnchantment {

    // ========== SWORD ENCHANTMENTS ==========
    SHARPNESS("Sharpness", Enchantment.SHARPNESS, 10, "§c", "Increases melee damage"),
    SMITE("Smite", Enchantment.SMITE, 10, "§e", "Increases damage to undead"),
    BANE_OF_ARTHROPODS("Bane of Arthropods", Enchantment.BANE_OF_ARTHROPODS, 10, "§6", "Increases damage to arthropods"),
    KNOCKBACK("Knockback", Enchantment.KNOCKBACK, 5, "§7", "Increases knockback"),
    FIRE_ASPECT("Fire Aspect", Enchantment.FIRE_ASPECT, 5, "§c", "Sets target on fire"),
    LOOTING("Looting", Enchantment.LOOTING, 5, "§a", "Increases mob drops"),
    SWEEPING_EDGE("Sweeping Edge", Enchantment.SWEEPING_EDGE, 5, "§b", "Increases sweeping attack damage"),

    // ========== BOW ENCHANTMENTS ==========
    POWER("Power", Enchantment.POWER, 10, "§c", "Increases arrow damage"),
    PUNCH("Punch", Enchantment.PUNCH, 5, "§7", "Increases arrow knockback"),
    FLAME("Flame", Enchantment.FLAME, 3, "§c", "Sets arrows on fire"),
    INFINITY("Infinity", Enchantment.INFINITY, 1, "§b", "Unlimited arrows (requires 1 arrow)"),

    // ========== ARMOR ENCHANTMENTS ==========
    PROTECTION("Protection", Enchantment.PROTECTION, 10, "§b", "Reduces all damage"),
    FIRE_PROTECTION("Fire Protection", Enchantment.FIRE_PROTECTION, 10, "§c", "Reduces fire damage"),
    BLAST_PROTECTION("Blast Protection", Enchantment.BLAST_PROTECTION, 10, "§e", "Reduces explosion damage"),
    PROJECTILE_PROTECTION("Projectile Protection", Enchantment.PROJECTILE_PROTECTION, 10, "§a", "Reduces projectile damage"),
    FEATHER_FALLING("Feather Falling", Enchantment.FEATHER_FALLING, 10, "§7", "Reduces fall damage"),
    RESPIRATION("Respiration", Enchantment.RESPIRATION, 5, "§b", "Increases underwater breathing"),
    AQUA_AFFINITY("Aqua Affinity", Enchantment.AQUA_AFFINITY, 1, "§b", "Increases underwater mining speed"),
    THORNS("Thorns", Enchantment.THORNS, 5, "§c", "Damages attackers"),
    DEPTH_STRIDER("Depth Strider", Enchantment.DEPTH_STRIDER, 5, "§b", "Increases underwater movement speed"),
    SOUL_SPEED("Soul Speed", Enchantment.SOUL_SPEED, 5, "§5", "Increases speed on soul blocks"),

    // ========== TOOL ENCHANTMENTS ==========
    EFFICIENCY("Efficiency", Enchantment.EFFICIENCY, 10, "§a", "Increases mining speed"),
    SILK_TOUCH("Silk Touch", Enchantment.SILK_TOUCH, 1, "§b", "Mines blocks in their original form"),
    UNBREAKING("Unbreaking", Enchantment.UNBREAKING, 10, "§7", "Increases item durability"),
    FORTUNE("Fortune", Enchantment.FORTUNE, 5, "§a", "Increases block drops"),
    MENDING("Mending", Enchantment.MENDING, 1, "§d", "Repairs item with XP orbs"),

    // ========== SKYBLOCK-SPECIFIC ENCHANTMENTS ==========
    // These are custom enchantments not in vanilla Minecraft
    ULTIMATE_WISE("Ultimate Wise", null, 5, "§6", "Reduces ability mana cost by 10% per level"),
    ULTIMATE_JERRY("Ultimate Jerry", null, 5, "§e", "Increases damage to Jerry by 50% per level"),
    ULTIMATE_REITERATE("Ultimate Reiterate", null, 5, "§c", "Increases damage by 5% per level when repeating hits"),
    ULTIMATE_LAST_STAND("Ultimate Last Stand", null, 5, "§4", "Grants immunity to death once per minute"),
    ULTIMATE_WISE_2("Ultimate Wise II", null, 5, "§6", "Reduces ability mana cost by 15% per level"),
    ULTIMATE_JERRY_2("Ultimate Jerry II", null, 5, "§e", "Increases damage to Jerry by 75% per level"),
    ULTIMATE_REITERATE_2("Ultimate Reiterate II", null, 5, "§c", "Increases damage by 8% per level when repeating hits"),
    ULTIMATE_LAST_STAND_2("Ultimate Last Stand II", null, 5, "§4", "Grants immunity to death once per 30 seconds"),

    // ========== COMBAT ENCHANTMENTS ==========
    EXECUTE("Execute", null, 5, "§c", "Increases damage to low-health enemies"),
    FIRST_STRIKE("First Strike", null, 5, "§e", "Increases damage on first hit"),
    GIANT_KILLER("Giant Killer", null, 5, "§6", "Increases damage to higher-level enemies"),
    LIFE_STEAL("Life Steal", null, 5, "§c", "Heals you for a portion of damage dealt"),
    VENOMOUS("Venomous", null, 5, "§2", "Poisons the target"),
    THUNDERBOLT("Thunderbolt", null, 5, "§b", "Strikes lightning on hit"),
    CHIMERA("Chimera", null, 5, "§5", "Grants stats from your pet"),

    // ========== UTILITY ENCHANTMENTS ==========
    REPLENISH("Replenish", null, 5, "§a", "Automatically replants crops"),
    HARVESTING("Harvesting", null, 5, "§2", "Increases crop yield"),
    SUGAR_RUSH("Sugar Rush", null, 5, "§c", "Increases speed when holding sugar"),
    EXPERIENCE("Experience", null, 5, "§b", "Increases XP gained from mobs"),
    LOOTING_BAGS("Looting Bags", null, 5, "§6", "Increases loot from treasure bags"),

    // ========== EXPANDED CUSTOM ENCHANTS ==========
    DRAGON_HUNTER("Dragon Hunter", null, 5, "§c", "Increases damage to dragons and bosses"),
    OVERLOAD("Overload", null, 5, "§e", "Chance for powerful damage burst on hit"),
    CUBISM("Cubism", null, 5, "§a", "Increases damage to slimes, magma cubes and similar");

    private final String displayName;
    private final Enchantment vanillaEnchant;
    private final int maxLevel;
    private final String colorCode;
    private final String description;

    CustomEnchantment(String displayName, Enchantment vanillaEnchant, int maxLevel, String colorCode, String description) {
        this.displayName = displayName;
        this.vanillaEnchant = vanillaEnchant;
        this.maxLevel = maxLevel;
        this.colorCode = colorCode;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public Enchantment getVanillaEnchant() { return vanillaEnchant; }
    public int getMaxLevel() { return maxLevel; }
    public String getColorCode() { return colorCode; }
    public String getDescription() { return description; }

    /**
     * Check if this is a vanilla enchantment or custom Skyblock enchantment
     */
    public boolean isVanilla() {
        return vanillaEnchant != null;
    }

    /**
     * Apply this enchantment to an item at the specified level
     */
    public void apply(ItemStack item, int level) {
        if (level < 1 || level > maxLevel) return;

        if (isVanilla()) {
            item.addUnsafeEnchantment(vanillaEnchant, level);
        } else {
            // Custom enchantments stored in item lore/meta
            var meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();

                // Remove existing level of this enchantment
                lore.removeIf(line -> line.contains(displayName));

                // Add new level
                lore.add(colorCode + displayName + " " + toRoman(level));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }

    /**
     * Get the current level of this enchantment on an item
     */
    public int getLevel(ItemStack item) {
        if (isVanilla()) {
            return item.getEnchantmentLevel(vanillaEnchant);
        } else {
            // Check lore for custom enchantment
            var meta = item.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line.contains(displayName)) {
                        // Extract level from lore
                        String[] parts = line.split(" ");
                        if (parts.length > 0) {
                            return fromRoman(parts[parts.length - 1]);
                        }
                    }
                }
            }
            return 0;
        }
    }

    /**
     * Convert number to Roman numeral
     */
    public static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    /**
     * Convert Roman numeral to number
     */
    public static int fromRoman(String roman) {
        return switch (roman.toUpperCase()) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            case "VI" -> 6;
            case "VII" -> 7;
            case "VIII" -> 8;
            case "IX" -> 9;
            case "X" -> 10;
            default -> 1;
        };
    }

    /**
     * Get all enchantments applicable to a specific item type
     */
    public static List<CustomEnchantment> getApplicableEnchantments(org.bukkit.Material material) {
        List<CustomEnchantment> applicable = new ArrayList<>();

        String name = material.name();

        if (name.contains("SWORD")) {
            applicable.add(SHARPNESS);
            applicable.add(SMITE);
            applicable.add(BANE_OF_ARTHROPODS);
            applicable.add(KNOCKBACK);
            applicable.add(FIRE_ASPECT);
            applicable.add(LOOTING);
            applicable.add(SWEEPING_EDGE);
            applicable.add(EXECUTE);
            applicable.add(FIRST_STRIKE);
            applicable.add(GIANT_KILLER);
            applicable.add(LIFE_STEAL);
            applicable.add(VENOMOUS);
            applicable.add(THUNDERBOLT);
            applicable.add(DRAGON_HUNTER);
            applicable.add(OVERLOAD);
            applicable.add(CUBISM);
        } else if (name.contains("BOW")) {
            applicable.add(POWER);
            applicable.add(PUNCH);
            applicable.add(FLAME);
            applicable.add(INFINITY);
        } else if (name.contains("HELMET") || name.contains("CHESTPLATE") ||
                name.contains("LEGGINGS") || name.contains("BOOTS")) {
            applicable.add(PROTECTION);
            applicable.add(FIRE_PROTECTION);
            applicable.add(BLAST_PROTECTION);
            applicable.add(PROJECTILE_PROTECTION);
            if (name.contains("BOOTS")) {
                applicable.add(FEATHER_FALLING);
                applicable.add(DEPTH_STRIDER);
                applicable.add(SOUL_SPEED);
            }
            if (name.contains("HELMET")) {
                applicable.add(RESPIRATION);
                applicable.add(AQUA_AFFINITY);
            }
            applicable.add(THORNS);
        } else if (name.contains("PICKAXE") || name.contains("AXE") ||
                name.contains("SHOVEL") || name.contains("HOE")) {
            applicable.add(EFFICIENCY);
            applicable.add(SILK_TOUCH);
            applicable.add(UNBREAKING);
            applicable.add(FORTUNE);
            applicable.add(MENDING);
            if (name.contains("HOE")) {
                applicable.add(REPLENISH);
                applicable.add(HARVESTING);
            }
        }

        // Universal enchantments
        applicable.add(UNBREAKING);
        applicable.add(MENDING);
        applicable.add(EXPERIENCE);

        return applicable;
    }
}
