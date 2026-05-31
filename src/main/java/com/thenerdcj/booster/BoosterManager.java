package com.thenerdcj.booster;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages temporary island boosters.
 * Boosters provide time-limited multipliers that stack on top of upgrades.
 *
 * Design goals:
 * - Folia-safe (uses ThreadSafety for scheduling)
 * - Per-island
 * - Simple activation + automatic expiration
 * - Easy to hook into existing listeners
 */
public class BoosterManager {

    private final FoliaSkyblock plugin;

    // islandKey -> active boosters
    private final Map<String, Map<BoosterType, ActiveBooster>> activeBoosters = new ConcurrentHashMap<>();

    public BoosterManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Activates a booster for an island.
     */
    public void activateBooster(Island island, BoosterType type, double multiplier, long durationMillis) {
        String key = getIslandKey(island);
        long expiresAt = System.currentTimeMillis() + durationMillis;

        ActiveBooster booster = new ActiveBooster(type, multiplier, expiresAt);

        activeBoosters
            .computeIfAbsent(key, k -> new ConcurrentHashMap<>())
            .put(type, booster);

        // Persist
        plugin.getDatabaseManager().saveIslandBooster(key, type.name(), multiplier, expiresAt);

        // Schedule expiration (Folia-safe)
        long delayTicks = Math.max(20, durationMillis / 50);
        plugin.getThreadSafety().runOnMainThreadLater(() -> {
            deactivateBooster(island, type);
        }, delayTicks);

        // Notify online players on the island (best effort)
        // Could be expanded later
    }

    public void deactivateBooster(Island island, BoosterType type) {
        String key = getIslandKey(island);
        Map<BoosterType, ActiveBooster> map = activeBoosters.get(key);
        if (map != null) {
            map.remove(type);
            if (map.isEmpty()) {
                activeBoosters.remove(key);
            }
        }
        plugin.getDatabaseManager().removeIslandBooster(key, type.name());
    }

    /**
     * Returns the current multiplier for a booster type on this island (1.0 = no boost).
     */
    public double getBoosterMultiplier(Island island, BoosterType type) {
        String key = getIslandKey(island);
        Map<BoosterType, ActiveBooster> map = activeBoosters.get(key);
        if (map == null) return 1.0;

        ActiveBooster booster = map.get(type);
        if (booster == null || System.currentTimeMillis() > booster.expiresAt) {
            return 1.0;
        }
        return booster.multiplier;
    }

    /**
     * Layered multiplier helper for listeners.
     * Example usage in CropGrowthListener:
     * double total = upgradeMultiplier * boosterManager.getCombinedMultiplier(island, BoosterType.CROP_GROWTH);
     */
    public double getCombinedMultiplier(Island island, BoosterType type) {
        return getBoosterMultiplier(island, type);
    }

    private String getIslandKey(Island island) {
        GridPosition pos = island.getGridPosition();
        return pos.x() + ":" + pos.z() + ":" + island.getDimension().name();
    }

    /**
     * Loads active boosters from database (call on island load).
     */
    public void loadBoostersForIsland(Island island) {
        String key = getIslandKey(island);
        plugin.getDatabaseManager().loadIslandBoosters(key).thenAccept(boosters -> {
            Map<BoosterType, ActiveBooster> map = new ConcurrentHashMap<>();
            long now = System.currentTimeMillis();

            for (var entry : boosters.entrySet()) {
                try {
                    BoosterType type = BoosterType.valueOf(entry.getKey());
                    double mult = entry.getValue().multiplier;
                    long exp = entry.getValue().expiresAt;

                    if (exp > now) {
                        map.put(type, new ActiveBooster(type, mult, exp));

                        // Re-schedule expiration
                        long delay = (exp - now) / 50;
                        plugin.getThreadSafety().runOnMainThreadLater(() -> {
                            deactivateBooster(island, type);
                        }, Math.max(20, delay));
                    }
                } catch (Exception ignored) {}
            }

            if (!map.isEmpty()) {
                activeBoosters.put(key, map);
            }
        });
    }

    private static class ActiveBooster {
        final BoosterType type;
        final double multiplier;
        final long expiresAt;

        ActiveBooster(BoosterType type, double multiplier, long expiresAt) {
            this.type = type;
            this.multiplier = multiplier;
            this.expiresAt = expiresAt;
        }
    }
}