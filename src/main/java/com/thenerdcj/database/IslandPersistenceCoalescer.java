package com.thenerdcj.database;

import com.thenerdcj.island.IslandSettings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory queue for batched island writes (worth, bank, settings).
 * {@link IslandDAO#flushCoalescedWrites()} drains this map on a timer and on shutdown.
 */
public class IslandPersistenceCoalescer {

    public record WorthSnapshot(double worth, int worthLevel, long lastCalculated) {}

    private final ConcurrentHashMap<GridPosition, WorthSnapshot> pendingWorth = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GridPosition, Double> pendingBank = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GridPosition, IslandSettings> pendingSettings = new ConcurrentHashMap<>();

    public void queueWorth(GridPosition pos, double worth, int worthLevel, long lastCalculated) {
        pendingWorth.put(pos, new WorthSnapshot(worth, worthLevel, lastCalculated));
    }

    public void queueBank(GridPosition pos, double balance) {
        pendingBank.put(pos, balance);
    }

    public void queueSettings(IslandSettings settings) {
        if (settings != null && settings.getGridPosition() != null) {
            pendingSettings.put(settings.getGridPosition(), settings);
        }
    }

    public boolean hasPending() {
        return !pendingWorth.isEmpty() || !pendingBank.isEmpty() || !pendingSettings.isEmpty();
    }

    public int pendingCount() {
        return pendingWorth.size() + pendingBank.size() + pendingSettings.size();
    }

    /** Drains pending maps into immutable snapshots (caller must write then handle re-queue on failure). */
    public DrainResult drain() {
        Map<GridPosition, WorthSnapshot> worth = Map.copyOf(pendingWorth);
        Map<GridPosition, Double> bank = Map.copyOf(pendingBank);
        Map<GridPosition, IslandSettings> settings = Map.copyOf(pendingSettings);
        pendingWorth.keySet().removeAll(worth.keySet());
        pendingBank.keySet().removeAll(bank.keySet());
        pendingSettings.keySet().removeAll(settings.keySet());
        return new DrainResult(worth, bank, settings);
    }

    public void requeue(DrainResult batch) {
        batch.worth().forEach(pendingWorth::putIfAbsent);
        batch.bank().forEach(pendingBank::putIfAbsent);
        batch.settings().forEach(pendingSettings::putIfAbsent);
    }

    public record DrainResult(
            Map<GridPosition, WorthSnapshot> worth,
            Map<GridPosition, Double> bank,
            Map<GridPosition, IslandSettings> settings) {}
}