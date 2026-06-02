package com.thenerdcj.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Utility for serializing/deserializing ItemStacks to Base64.
 * Extracted/polished from DatabaseManager as part of DB modularization (highest priority).
 * Prefers modern ItemStack.serializeAsBytes() (1.21+ safe, no legacy BukkitObject streams).
 * Falls back to legacy for very old data if needed. Safe for DAOs + async.
 */
public final class ItemSerializer {

    private ItemSerializer() {}

    public static String itemToBase64(ItemStack item) {
        if (item == null) return null;
        try {
            // Preferred modern path (used in current DatabaseManager impl)
            byte[] bytes = item.serializeAsBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            // Legacy fallback (for data serialized with old BukkitObject* before extraction)
            try {
                java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                try (org.bukkit.util.io.BukkitObjectOutputStream dataOutput = new org.bukkit.util.io.BukkitObjectOutputStream(outputStream)) {
                    dataOutput.writeObject(item);
                }
                return Base64.getEncoder().encodeToString(outputStream.toByteArray());
            } catch (Exception legacyEx) {
                throw new IllegalStateException("Unable to serialize ItemStack (modern+legacy)", e);
            }
        }
    }

    public static ItemStack itemFromBase64(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            // Preferred modern
            byte[] bytes = Base64.getDecoder().decode(data);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            // Legacy fallback
            try (java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(Base64.getDecoder().decode(data));
                 org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream)) {
                return (ItemStack) dataInput.readObject();
            } catch (Exception legacyEx) {
                throw new IllegalStateException("Unable to deserialize ItemStack (modern+legacy)", e);
            }
        }
    }
}