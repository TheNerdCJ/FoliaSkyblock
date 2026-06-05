package com.thenerdcj.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Batches island shop one-time purchase rows (latest timestamp per island+item wins per flush).
 */
public class IslandShopPurchaseCoalescer {

    public record PurchaseRow(String islandKey, String itemId, long purchasedAt) {}

    private static String key(String islandKey, String itemId) {
        return islandKey + '\u0000' + itemId;
    }

    private final ConcurrentHashMap<String, PurchaseRow> pending = new ConcurrentHashMap<>();

    public void queue(String islandKey, String itemId) {
        pending.put(key(islandKey, itemId),
                new PurchaseRow(islandKey, itemId, System.currentTimeMillis()));
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public int pendingCount() {
        return pending.size();
    }

    public List<PurchaseRow> drain() {
        Map<String, PurchaseRow> snapshot = Map.copyOf(pending);
        pending.keySet().removeAll(snapshot.keySet());
        return new ArrayList<>(snapshot.values());
    }

    public void requeue(List<PurchaseRow> rows) {
        for (PurchaseRow row : rows) {
            pending.putIfAbsent(key(row.islandKey(), row.itemId()), row);
        }
    }
}