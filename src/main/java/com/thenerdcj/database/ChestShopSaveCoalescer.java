package com.thenerdcj.database;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Batches chest shop row writes (latest row per sign location wins per flush).
 */
public class ChestShopSaveCoalescer {

    private final ConcurrentHashMap<String, ChestShopDAO.ChestShopRow> pending = new ConcurrentHashMap<>();

    public static String locationKey(ChestShopDAO.ChestShopRow row) {
        return row.world() + ":" + row.x() + ":" + row.y() + ":" + row.z();
    }

    public void queue(ChestShopDAO.ChestShopRow row) {
        pending.put(locationKey(row), row);
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public int pendingCount() {
        return pending.size();
    }

    public Collection<ChestShopDAO.ChestShopRow> drain() {
        Map<String, ChestShopDAO.ChestShopRow> snapshot = Map.copyOf(pending);
        pending.keySet().removeAll(snapshot.keySet());
        return snapshot.values();
    }

    public void requeue(Collection<ChestShopDAO.ChestShopRow> rows) {
        for (ChestShopDAO.ChestShopRow row : rows) {
            pending.putIfAbsent(locationKey(row), row);
        }
    }
}