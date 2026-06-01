package com.thenerdcj.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Utility for serializing/deserializing ItemStacks to Base64.
 * Extracted from DatabaseManager to support full modularization.
 * Safe for use inside DAOs and async DB operations.
 */
public final class ItemSerializer {

    private ItemSerializer() {}

    public static String itemToBase64(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize ItemStack", e);
        }
    }

    public static ItemStack itemFromBase64(String data) {
        if (data == null || data.isEmpty()) return null;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to deserialize ItemStack", e);
        }
    }
}