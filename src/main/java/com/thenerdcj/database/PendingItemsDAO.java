package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * DAO for pending items (items waiting for player to claim, e.g. from trades, crates, etc.).
 * Extracted as part of DB modularization step 2 (pending items, misc).
 */
public class PendingItemsDAO extends BaseDAO {

    public PendingItemsDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // Table created centrally.
    }

    public CompletableFuture<List<ItemStack>> getPendingItems(UUID uuid) {
        return supplyAsync(() -> {
            List<ItemStack> items = new ArrayList<>();
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT item_base64 FROM pending_items WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            ItemStack item = ItemSerializer.itemFromBase64(rs.getString("item_base64"));
                            if (item != null) items.add(item);
                        }
                        return items;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[PendingItemsDAO] getPendingItems failed: " + e.getMessage());
                return items;
            }
        });
    }

    public boolean storePendingItem(UUID uuid, ItemStack item) {
        try {
            withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO pending_items (uuid, item_base64) VALUES (?, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, ItemSerializer.itemToBase64(item));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[PendingItemsDAO] storePendingItem failed: " + e.getMessage());
            return false;
        }
    }

    public CompletableFuture<Boolean> clearPendingItems(UUID uuid) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM pending_items WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[PendingItemsDAO] clearPendingItems failed: " + e.getMessage());
                return false;
            }
        });
    }
}