package com.thenerdcj.gui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * Shared GUI utilities to reduce massive duplication across the 24+ GUI classes.
 * Extracted as part of the compression & optimization pass.
 */
public final class GUIUtils {

    private GUIUtils() {}

    public static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a navigation button with embedded PDC action.
     */
    public static ItemStack createNavButton(Material mat, String name, NamespacedKey actionKey, String actionValue) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (actionKey != null && actionValue != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(actionKey, PersistentDataType.STRING, actionValue);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void setPDCAction(ItemMeta meta, NamespacedKey key, String value) {
        if (meta != null && key != null && value != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
    }

    public static String getPDCAction(ItemMeta meta, NamespacedKey key) {
        if (meta == null || key == null) return null;
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static void setPDCString(ItemMeta meta, NamespacedKey key, String value) {
        if (meta != null && key != null && value != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
    }
}