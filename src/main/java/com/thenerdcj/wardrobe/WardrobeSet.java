package com.thenerdcj.wardrobe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Represents a single saved wardrobe preset (loadout).
 *
 * Supports both Armor (helmet/chest/legs/boots) and Equipment (necklace/cloak/belt/bracelet)
 * as requested across Skyblock community threads for proper activity loadouts.
 *
 * Designed to be stored via DatabaseManager item serialization.
 */
public class WardrobeSet {

    public static final int ARMOR_SIZE = 4;      // helmet, chest, legs, boots
    public static final int EQUIPMENT_SIZE = 4;  // necklace, cloak, belt, bracelet (matching Hypixel equipment slots)

    private String name;
    private Material icon;
    private final ItemStack[] armor;
    private final ItemStack[] equipment;

    public WardrobeSet(String name, Material icon) {
        this.name = name != null ? name : "Unnamed Set";
        this.icon = icon != null ? icon : Material.LEATHER_CHESTPLATE;
        this.armor = new ItemStack[ARMOR_SIZE];
        this.equipment = new ItemStack[EQUIPMENT_SIZE];
    }

    /**
     * Full constructor used when loading from database.
     */
    public WardrobeSet(String name, Material icon, ItemStack[] armor, ItemStack[] equipment) {
        this.name = name != null ? name : "Unnamed Set";
        this.icon = icon != null ? icon : Material.LEATHER_CHESTPLATE;
        this.armor = (armor != null && armor.length == ARMOR_SIZE) ? armor.clone() : new ItemStack[ARMOR_SIZE];
        this.equipment = (equipment != null && equipment.length == EQUIPMENT_SIZE) ? equipment.clone() : new ItemStack[EQUIPMENT_SIZE];
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Unnamed Set" : name;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = (icon == null || icon.isAir()) ? Material.LEATHER_CHESTPLATE : icon;
    }

    public ItemStack[] getArmor() {
        return armor.clone();
    }

    public ItemStack[] getEquipment() {
        return equipment.clone();
    }

    public void setArmorItem(int index, ItemStack item) {
        if (index >= 0 && index < ARMOR_SIZE) {
            armor[index] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
    }

    public void setEquipmentItem(int index, ItemStack item) {
        if (index >= 0 && index < EQUIPMENT_SIZE) {
            equipment[index] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
    }

    public ItemStack getArmorItem(int index) {
        return (index >= 0 && index < ARMOR_SIZE) ? armor[index] : null;
    }

    public ItemStack getEquipmentItem(int index) {
        return (index >= 0 && index < EQUIPMENT_SIZE) ? equipment[index] : null;
    }

    /**
     * Returns true if this set contains no items in either armor or equipment.
     */
    public boolean isEmpty() {
        for (ItemStack i : armor) if (i != null && !i.getType().isAir()) return false;
        for (ItemStack i : equipment) if (i != null && !i.getType().isAir()) return false;
        return true;
    }

    /**
     * Creates a nice display ItemStack for use in the Wardrobe GUI.
     */
    public ItemStack createDisplayItem() {
        ItemStack display = new ItemStack(icon);
        var meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l" + name);
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Armor pieces: §e" + countNonAir(armor) + "/" + ARMOR_SIZE);
            lore.add("§7Equipment pieces: §e" + countNonAir(equipment) + "/" + EQUIPMENT_SIZE);
            lore.add("");
            lore.add("§eLeft-click §7to equip");
            lore.add("§7Shift + Left-click §7to overwrite with current gear");
            lore.add("§7Right-click §7for options (rename/clear)");
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private int countNonAir(ItemStack[] arr) {
        int count = 0;
        for (ItemStack i : arr) {
            if (i != null && !i.getType().isAir()) count++;
        }
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WardrobeSet that)) return false;
        return Objects.equals(name, that.name) &&
               icon == that.icon &&
               java.util.Arrays.equals(armor, that.armor) &&
               java.util.Arrays.equals(equipment, that.equipment);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, icon);
        result = 31 * result + java.util.Arrays.hashCode(armor);
        result = 31 * result + java.util.Arrays.hashCode(equipment);
        return result;
    }
}