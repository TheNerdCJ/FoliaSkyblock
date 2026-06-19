package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.Island.Skill;
import com.thenerdcj.island.IslandSettings;
import com.thenerdcj.island.IslandUpgrade;
import com.thenerdcj.island.IslandWarp;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Access Object for all per-island persistence.
 * Extracted as part of finishing full DatabaseManager modularization.
 * 
 * Handles:
 * - Core island rows (grid, owner, dim, biome, seed)
 * - Per-dimension resets
 * - Island upgrades, levels, skills, milestones, fuel, boosters
 * - Active state (music, weather)
 * - Collections, prestige, banks, worth, settings, warps, ratings (fully promoted; managers delegate)
 * 
 * Uses the DBOperations bridge during the migration phase (same as other DAOs).
 * Future: full connection ownership via dbOps.
 * 
 * Table creation remains centralized in DatabaseManager.initDatabase() for now.
 */
public class IslandDAO extends BaseDAO {

    private IslandPersistenceCoalescer persistenceCoalescer;
    private boolean coalesceWrites = true;
    private final java.util.concurrent.atomic.AtomicInteger coalescedFlushCount = new java.util.concurrent.atomic.AtomicInteger();

    public IslandDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    public void setPersistenceCoalescer(IslandPersistenceCoalescer coalescer) {
        this.persistenceCoalescer = coalescer;
    }

    public void setCoalesceWritesEnabled(boolean enabled) {
        this.coalesceWrites = enabled;
    }

    public int getCoalescedFlushCount() {
        return coalescedFlushCount.get();
    }

    public void resetCoalescedFlushCount() {
        coalescedFlushCount.set(0);
    }

    public CompletableFuture<Void> flushCoalescedWrites() {
        if (persistenceCoalescer == null || !persistenceCoalescer.hasPending()) {
            return CompletableFuture.completedFuture(null);
        }
        IslandPersistenceCoalescer.DrainResult batch = persistenceCoalescer.drain();
        if (batch.worth().isEmpty() && batch.bank().isEmpty() && batch.settings().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return supplyAsync(() -> {
            flushCoalescedBatch(batch);
            return null;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[IslandDAO] coalesced flush failed: " + ex.getMessage());
            persistenceCoalescer.requeue(batch);
            return null;
        }).thenApply(v -> null);
    }

    @Override
    public void initialize() {
        // Schema ownership stays in DatabaseManager for this phase of modularization.
        // DAOs can declare required tables in a future cleanup.
    }

    // ==================== CORE ISLAND ====================

    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID ownerUuid, String dimension, String biome) {
        return saveIsland(gridX, gridZ, ownerUuid, dimension, biome, 0L);
    }

    public CompletableFuture<Boolean> saveIsland(int gridX, int gridZ, UUID ownerUuid, String dimension, String biome, long generationSeed) {
        return supplyAsync(() -> {
            String key = makeIslandKey(gridX, gridZ, dimension);
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO islands (grid_x, grid_z, owner_uuid, dimension, biome, level, last_reset, generation_seed) " +
                            "VALUES (?, ?, ?, ?, ?, 1, 0, ?)")) {
                        ps.setInt(1, gridX);
                        ps.setInt(2, gridZ);
                        ps.setString(3, ownerUuid.toString());
                        ps.setString(4, dimension);
                        ps.setString(5, biome);
                        ps.setLong(6, generationSeed);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIsland failed for " + key + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Synchronous load for compatibility with IslandManager (current usage pattern).
     */
    public Island getIslandByOwner(UUID ownerUuid, org.bukkit.World.Environment dimension) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT grid_x, grid_z, biome, generation_seed FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                    ps.setString(1, ownerUuid.toString());
                    ps.setString(2, dimension.name());
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        int x = rs.getInt("grid_x");
                        int z = rs.getInt("grid_z");
                        String biome = rs.getString("biome");
                        long genSeed = rs.getLong("generation_seed");

                        GridPosition pos = new GridPosition(x, z, dimension);
                        Island island = new Island(pos, ownerUuid, biome != null ? biome : "PLAINS", dimension);
                        if (genSeed != 0) {
                            island.setGenerationSeed(genSeed);
                        }
                        return island;
                    }
                    return null;
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[IslandDAO] getIslandByOwner failed: " + e.getMessage());
            return null;
        }
    }

    public CompletableFuture<Boolean> deleteIsland(UUID ownerUuid, org.bukkit.World.Environment dimension) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    int gx = 0, gz = 0;
                    // Pre-select grid before delete so we can clean grid-based tables (banks/worth/settings/ratings) reliably
                    try (PreparedStatement gq = conn.prepareStatement(
                            "SELECT grid_x, grid_z FROM islands WHERE owner_uuid = ? AND dimension = ? LIMIT 1")) {
                        gq.setString(1, ownerUuid.toString());
                        gq.setString(2, dimension.name());
                        ResultSet grs = gq.executeQuery();
                        if (grs.next()) {
                            gx = grs.getInt(1);
                            gz = grs.getInt(2);
                        }
                    } catch (SQLException ignored) {}
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM islands WHERE owner_uuid = ? AND dimension = ?")) {
                        ps.setString(1, ownerUuid.toString());
                        ps.setString(2, dimension.name());
                        ps.executeUpdate();
                        // Clean related per-island data (keyed + grid tables)
                        cleanupIslandData(makeIslandKeyForDelete(ownerUuid, dimension), gx, gz, dimension.name(), conn);
                        return true;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] deleteIsland failed: " + e.getMessage());
                return false;
            }
        });
    }

    private void cleanupIslandData(String islandKey, int gridX, int gridZ, String dimension, Connection conn) throws SQLException {
        // Clean common per-island tables. Safe if tables don't exist or rows don't.
        // Key-based tables (island_* using island_key)
        String[] keyCleanups = {
            "DELETE FROM island_upgrades WHERE island_key = ?",
            "DELETE FROM island_levels WHERE island_key = ?",
            "DELETE FROM island_skills WHERE island_key = ?",
            "DELETE FROM island_milestones WHERE island_key = ?",
            "DELETE FROM island_fuel WHERE island_key = ?",
            "DELETE FROM island_boosters WHERE island_key = ?",
            "DELETE FROM island_collections WHERE island_key = ?",
            "DELETE FROM island_active_weather WHERE island_key = ?",
            "DELETE FROM island_active_music WHERE island_key = ?"
        };
        for (String sql : keyCleanups) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, islandKey);
                ps.executeUpdate();
            } catch (SQLException ignored) {
                // Table may use different key or not exist yet; safe during migration.
            }
        }
        // Grid-based tables (banks, worth, settings, ratings) - use explicit grid+dim
        String[] gridCleanups = {
            "DELETE FROM island_banks WHERE grid_x = ? AND grid_z = ? AND dimension = ?",
            "DELETE FROM island_worth WHERE grid_x = ? AND grid_z = ? AND dimension = ?",
            "DELETE FROM island_settings WHERE grid_x = ? AND grid_z = ? AND dimension = ?",
            "DELETE FROM island_ratings WHERE grid_x = ? AND grid_z = ? AND dimension = ?"
        };
        for (String sql : gridCleanups) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, gridX);
                ps.setInt(2, gridZ);
                ps.setString(3, dimension);
                ps.executeUpdate();
            } catch (SQLException ignored) {
                // Safe best-effort during delete/reset.
            }
        }
    }

    public void recordIslandReset(UUID playerUuid, org.bukkit.World.Environment dimension) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO player_dimension_resets (player_uuid, dimension, last_reset) VALUES (?, ?, ?)")) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, dimension.name());
                        ps.setLong(3, System.currentTimeMillis());
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] recordIslandReset failed: " + e.getMessage());
            }
        });
    }

    public long getLastDimensionReset(UUID playerUuid, World.Environment dimension) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_reset FROM player_dimension_resets WHERE player_uuid = ? AND dimension = ?")) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, dimension.name());
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? rs.getLong("last_reset") : 0L;
                } catch (SQLException e) {
                    plugin.getLogger().warning("[IslandDAO] getLastDimensionReset sql: " + e.getMessage());
                    return 0L;
                }
            });
        } catch (Exception e) {
            return 0L;
        }
    }

    // ==================== ISLAND STATE HELPERS (moved from god class) ====================

    public CompletableFuture<Boolean> saveIslandUpgrade(String islandKey, IslandUpgrade upgrade, int level) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_upgrades (island_key, upgrade_type, level) VALUES (?, ?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setString(2, upgrade.name());
                        ps.setInt(3, level);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandUpgrade failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Integer> getIslandUpgradeLevel(String islandKey, IslandUpgrade upgrade) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT level FROM island_upgrades WHERE island_key = ? AND upgrade_type = ?")) {
                        ps.setString(1, islandKey);
                        ps.setString(2, upgrade.name());
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getInt("level") : 0;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandDAO] getIslandUpgradeLevel failed: " + e.getMessage());
                return 0;
            }
        });
    }

    public CompletableFuture<Boolean> saveMinionData(String islandKey, int minionType, int level) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_minions (island_key, minion_type, level) VALUES (?, ?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setInt(2, minionType);
                        ps.setInt(3, level);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandDAO] saveMinionData failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Map<Integer, Integer>> loadMinionData(String islandKey) {
        return supplyAsync(() -> {
            Map<Integer, Integer> data = new HashMap<>();
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT minion_type, level FROM island_minions WHERE island_key = ?")) {
                        ps.setString(1, islandKey);
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            data.put(rs.getInt("minion_type"), rs.getInt("level"));
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return data;
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandDAO] loadMinionData failed: " + e.getMessage());
                return data;
            }
        });
    }

    public CompletableFuture<Map<IslandUpgrade, Integer>> loadIslandUpgrades(String islandKey) {
        return supplyAsync(() -> {
            Map<IslandUpgrade, Integer> map = new EnumMap<>(IslandUpgrade.class);
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT upgrade_type, level FROM island_upgrades WHERE island_key = ?")) {
                        ps.setString(1, islandKey);
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            try {
                                IslandUpgrade up = IslandUpgrade.valueOf(rs.getString("upgrade_type"));
                                map.put(up, rs.getInt("level"));
                            } catch (Exception ignored) {}
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return map;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] loadIslandUpgrades failed: " + e.getMessage());
                return map;
            }
        });
    }

    public CompletableFuture<Boolean> saveIslandLevel(String islandKey, int level, double xp) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_levels (island_key, xp, level) VALUES (?, ?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setDouble(2, xp);
                        ps.setInt(3, level);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandLevel failed: " + e.getMessage());
                return false;
            }
        });
    }

    // Similar patterns for skills, milestones, fuel, boosters, collections, active music/weather, etc.
    // (Implemented for the most critical ones used by IslandManager; others can be moved in follow-up passes.)

    public CompletableFuture<Boolean> saveIslandSkills(String islandKey, Map<Skill, Double> xpMap, Map<Skill, Integer> levelMap) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_skills (island_key, skill_name, xp, level) VALUES (?, ?, ?, ?)")) {
                        for (Map.Entry<Skill, Double> e : xpMap.entrySet()) {
                            ps.setString(1, islandKey);
                            ps.setString(2, e.getKey().name());
                            ps.setDouble(3, e.getValue());
                            ps.setInt(4, levelMap.getOrDefault(e.getKey(), 1));
                            ps.executeUpdate();
                        }
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandSkills failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Map<Skill, Object[]>> loadIslandSkills(String islandKey) {
        return supplyAsync(() -> {
            Map<Skill, Object[]> map = new EnumMap<>(Skill.class);
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT skill_name, xp, level FROM island_skills WHERE island_key = ?")) {
                        ps.setString(1, islandKey);
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            try {
                                Skill s = Skill.valueOf(rs.getString("skill_name"));
                                map.put(s, new Object[]{rs.getDouble("xp"), rs.getInt("level")});
                            } catch (Exception ignored) {}
                        }
                        return map;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] loadIslandSkills failed: " + e.getMessage());
                return map;
            }
        });
    }

    public CompletableFuture<Boolean> saveIslandMilestones(String islandKey, Set<String> milestoneIds) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM island_milestones WHERE island_key = ?");
                         PreparedStatement ins = conn.prepareStatement(
                                 "INSERT OR REPLACE INTO island_milestones (island_key, milestone_id, completed_at) VALUES (?, ?, ?)")) {
                        del.setString(1, islandKey);
                        del.executeUpdate();
                        long now = System.currentTimeMillis();
                        for (String id : milestoneIds) {
                            ins.setString(1, islandKey);
                            ins.setString(2, id);
                            ins.setLong(3, now);
                            ins.executeUpdate();
                        }
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandMilestones failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Set<String>> loadIslandMilestones(String islandKey) {
        return supplyAsync(() -> {
            Set<String> set = ConcurrentHashMap.newKeySet();
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT milestone_id FROM island_milestones WHERE island_key = ?")) {
                        ps.setString(1, islandKey);
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) set.add(rs.getString(1));
                        return set;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] loadIslandMilestones failed: " + e.getMessage());
                return set;
            }
        });
    }

    public CompletableFuture<Boolean> saveIslandFuel(String islandKey, int fuelAmount) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_fuel (island_key, fuel_amount) VALUES (?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setInt(2, fuelAmount);
                        ps.executeUpdate();
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandFuel failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Integer> loadIslandFuel(String islandKey) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT fuel_amount FROM island_fuel WHERE island_key = ?")) {
                        ps.setString(1, islandKey);
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getInt(1) : 1000;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                return 1000;
            }
        });
    }

    // Active music/weather (island-bound cosmetics)
    public void saveIslandActiveMusic(String islandKey, String musicId) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_active_music (island_key, music_id, updated_at) VALUES (?, ?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setString(2, musicId);
                        ps.setLong(3, System.currentTimeMillis());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandActiveMusic failed: " + e.getMessage());
            }
        });
    }

    public String loadIslandActiveMusic(String islandKey) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT music_id FROM island_active_music WHERE island_key = ?")) {
                    ps.setString(1, islandKey);
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? rs.getString(1) : null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            return null;
        }
    }

    public void saveIslandActiveWeather(String islandKey, String weatherId) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_active_weather (island_key, weather_id, updated_at) VALUES (?, ?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setString(2, weatherId);
                        ps.setLong(3, System.currentTimeMillis());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandActiveWeather failed: " + e.getMessage());
            }
        });
    }

    public String loadIslandActiveWeather(String islandKey) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT weather_id FROM island_active_weather WHERE island_key = ?")) {
                    ps.setString(1, islandKey);
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? rs.getString(1) : null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            return null;
        }
    }

    // Island collections (per-island discovery)
    public void saveIslandCollection(String islandKey, String itemKey, UUID discoveredBy) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO island_collections (island_key, item_key, discovered_by, discovered_at) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setString(2, itemKey);
                        ps.setString(3, discoveredBy != null ? discoveredBy.toString() : null);
                        ps.setLong(4, System.currentTimeMillis());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandCollection failed: " + e.getMessage());
            }
        });
    }

    public Set<String> loadIslandCollections(String islandKey) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT item_key FROM island_collections WHERE island_key = ?")) {
                    ps.setString(1, islandKey);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[IslandDAO] loadIslandCollections failed: " + e.getMessage());
            return set;
        }
    }

    public int getIslandCollectionCount(String islandKey) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM island_collections WHERE island_key = ?")) {
                    ps.setString(1, islandKey);
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? rs.getInt(1) : 0;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            return 0;
        }
    }

    // Simple helpers
    private String makeIslandKey(int gridX, int gridZ, String dimension) {
        return gridX + "," + gridZ + "," + dimension;
    }

    private String makeIslandKeyForDelete(UUID owner, org.bukkit.World.Environment dim) {
        // Fallback; actual key usage in callers is often owner_dim or grid based.
        return owner.toString() + "_" + dim.name().toLowerCase();
    }
    // The methods below are thin pass-throughs or can be promoted when callers are updated.

    public void saveIslandPrestige(String islandKey, int prestigeLevel) {
        // Delegates to existing PrestigeDAO during transition (or duplicate logic here for ownership).
        // For full modularization, move the logic here or have PrestigeDAO focus on player vs island split.
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_prestige (island_key, prestige_level, last_prestiged) VALUES (?, ?, ?)")) {
                        ps.setString(1, islandKey);
                        ps.setInt(2, prestigeLevel);
                        ps.setLong(3, System.currentTimeMillis());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandPrestige (bridge) failed: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Integer> loadIslandPrestige(String islandKey) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT prestige_level FROM island_prestige WHERE island_key = ?")) {
                        ps.setString(1, islandKey);
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getInt(1) : 0;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                return 0;
            }
        });
    }

    // Island state persistence fully promoted into DAO (banks, settings, worth fixed to grid columns + GridPosition for consistency with schema).
    // Managers now delegate (no more direct getConnection in IslandBankManager / IslandSettingsManager / worth paths).
    // Addresses TODO + optimization for full modularization + reliable persistence+drift.

    // Worth persistence (grid PK matching schema; key consistency fixed to GridPosition.toString() / grid+dim)
    public void saveIslandWorth(GridPosition pos, double worth, int worthLevel, long lastCalculated) {
        if (coalesceWrites && persistenceCoalescer != null) {
            persistenceCoalescer.queueWorth(pos, worth, worthLevel, lastCalculated);
            return;
        }
        runAsync(() -> writeIslandWorthImmediate(pos, worth, worthLevel, lastCalculated));
    }

    public CompletableFuture<Boolean> saveIslandWorthAsync(GridPosition pos, double worth, int worthLevel, long lastCalculated) {
        if (coalesceWrites && persistenceCoalescer != null) {
            persistenceCoalescer.queueWorth(pos, worth, worthLevel, lastCalculated);
            return CompletableFuture.completedFuture(true);
        }
        return supplyAsync(() -> {
            writeIslandWorthImmediate(pos, worth, worthLevel, lastCalculated);
            return true;
        });
    }

    private void flushCoalescedBatch(IslandPersistenceCoalescer.DrainResult batch) {
        if (batch.worth().isEmpty() && batch.bank().isEmpty() && batch.settings().isEmpty()) {
            return;
        }
        withConnection(conn -> {
            try {
                boolean prevAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                if (!batch.worth().isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_worth (grid_x, grid_z, dimension, worth, worth_level, last_calculated) VALUES (?, ?, ?, ?, ?, ?)")) {
                        for (var e : batch.worth().entrySet()) {
                            GridPosition pos = e.getKey();
                            IslandPersistenceCoalescer.WorthSnapshot s = e.getValue();
                            ps.setInt(1, pos.x());
                            ps.setInt(2, pos.z());
                            ps.setString(3, pos.getDimension().name());
                            ps.setDouble(4, s.worth());
                            ps.setInt(5, s.worthLevel());
                            ps.setLong(6, s.lastCalculated());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                if (!batch.bank().isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_banks (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                        for (var e : batch.bank().entrySet()) {
                            GridPosition pos = e.getKey();
                            ps.setInt(1, pos.x());
                            ps.setInt(2, pos.z());
                            ps.setString(3, pos.getDimension().name());
                            ps.setDouble(4, e.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                if (!batch.settings().isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_settings " +
                            "(grid_x, grid_z, dimension, pvp_enabled, visitors_allowed, explosions_enabled, " +
                            "fire_spread_enabled, mob_spawning_enabled, crop_trampling_enabled, animal_spawning_enabled, " +
                            "leaf_decay_enabled, border_color, border_size, border_markers_enabled, warp_enabled, warp_description, keep_inventory_enabled) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        for (var e : batch.settings().entrySet()) {
                            bindIslandSettings(ps, e.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                conn.commit();
                coalescedFlushCount.incrementAndGet();
                } catch (SQLException ex) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        ex.addSuppressed(rollbackEx);
                    }
                    throw ex;
                } finally {
                    conn.setAutoCommit(prevAutoCommit);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private static void bindIslandSettings(PreparedStatement ps, IslandSettings settings) throws SQLException {
        GridPosition pos = settings.getGridPosition();
        ps.setInt(1, pos.x());
        ps.setInt(2, pos.z());
        ps.setString(3, pos.getDimension().name());
        ps.setBoolean(4, settings.isPvpEnabled());
        ps.setBoolean(5, settings.isVisitorsAllowed());
        ps.setBoolean(6, settings.isExplosionsEnabled());
        ps.setBoolean(7, settings.isFireSpreadEnabled());
        ps.setBoolean(8, settings.isMobSpawningEnabled());
        ps.setBoolean(9, settings.isCropTramplingEnabled());
        ps.setBoolean(10, settings.isAnimalSpawningEnabled());
        ps.setBoolean(11, settings.isLeafDecayEnabled());
        ps.setString(12, settings.getBorderColor());
        ps.setInt(13, settings.getBorderSize());
        ps.setBoolean(14, settings.isBorderMarkersEnabled());
        ps.setBoolean(15, settings.isWarpEnabled());
        ps.setString(16, settings.getWarpDescription());
        ps.setBoolean(17, settings.isKeepInventoryEnabled());
    }

    void writeIslandWorthImmediate(GridPosition pos, double worth, int worthLevel, long lastCalculated) {
        withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO island_worth (grid_x, grid_z, dimension, worth, worth_level, last_calculated) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, pos.x());
                ps.setInt(2, pos.z());
                ps.setString(3, pos.getDimension().name());
                ps.setDouble(4, worth);
                ps.setInt(5, worthLevel);
                ps.setLong(6, lastCalculated);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public CompletableFuture<Object[]> loadIslandWorth(GridPosition pos) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT worth, worth_level, last_calculated FROM island_worth WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            return new Object[]{rs.getDouble("worth"), rs.getInt("worth_level"), rs.getLong("last_calculated")};
                        }
                        return new Object[]{0.0, 1, 0L};
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                return new Object[]{0.0, 1, 0L};
            }
        });
    }

    // Bank persistence (extracted from IslandBankManager for DB modularization)
    public CompletableFuture<Double> loadIslandBankBalance(GridPosition pos) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT balance FROM island_banks WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getDouble(1) : 0.0;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                return 0.0;
            }
        });
    }

    public void saveIslandBankBalance(GridPosition pos, double balance) {
        if (coalesceWrites && persistenceCoalescer != null) {
            persistenceCoalescer.queueBank(pos, balance);
            return;
        }
        runAsync(() -> writeIslandBankImmediate(pos, balance));
    }

    public CompletableFuture<Boolean> saveIslandBankBalanceAsync(GridPosition pos, double balance) {
        if (coalesceWrites && persistenceCoalescer != null) {
            persistenceCoalescer.queueBank(pos, balance);
            return CompletableFuture.completedFuture(true);
        }
        return supplyAsync(() -> {
            writeIslandBankImmediate(pos, balance);
            return true;
        });
    }

    void writeIslandBankImmediate(GridPosition pos, double balance) {
        withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO island_banks (grid_x, grid_z, dimension, balance) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, pos.x());
                ps.setInt(2, pos.z());
                ps.setString(3, pos.getDimension().name());
                ps.setDouble(4, balance);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    // Settings persistence (extracted from IslandSettingsManager for DB modularization + consistency)
    public CompletableFuture<IslandSettings> loadIslandSettings(GridPosition pos) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT * FROM island_settings WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            IslandSettings s = new IslandSettings(pos);
                            s.setPvpEnabled(rs.getBoolean("pvp_enabled"));
                            s.setVisitorsAllowed(rs.getBoolean("visitors_allowed"));
                            s.setExplosionsEnabled(rs.getBoolean("explosions_enabled"));
                            s.setFireSpreadEnabled(rs.getBoolean("fire_spread_enabled"));
                            s.setMobSpawningEnabled(rs.getBoolean("mob_spawning_enabled"));
                            s.setCropTramplingEnabled(rs.getBoolean("crop_trampling_enabled"));
                            s.setAnimalSpawningEnabled(rs.getBoolean("animal_spawning_enabled"));
                            s.setLeafDecayEnabled(rs.getBoolean("leaf_decay_enabled"));
                            s.setBorderColor(rs.getString("border_color"));
                            s.setBorderSize(rs.getInt("border_size"));
                            s.setBorderMarkersEnabled(rs.getBoolean("border_markers_enabled"));
                            s.setWarpEnabled(rs.getBoolean("warp_enabled"));
                            s.setWarpDescription(rs.getString("warp_description"));
                            s.setKeepInventoryEnabled(rs.getBoolean("keep_inventory_enabled"));
                            return s;
                        } else {
                            IslandSettings ns = new IslandSettings(pos);
                            return ns;
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                return new IslandSettings(pos);
            }
        });
    }

    public void saveIslandSettings(IslandSettings settings) {
        if (coalesceWrites && persistenceCoalescer != null) {
            persistenceCoalescer.queueSettings(settings);
            return;
        }
        runAsync(() -> writeIslandSettingsImmediate(settings));
    }

    public CompletableFuture<Boolean> saveIslandSettingsAsync(IslandSettings settings) {
        if (coalesceWrites && persistenceCoalescer != null) {
            persistenceCoalescer.queueSettings(settings);
            return CompletableFuture.completedFuture(true);
        }
        return supplyAsync(() -> {
            writeIslandSettingsImmediate(settings);
            return true;
        });
    }

    void writeIslandSettingsImmediate(IslandSettings settings) {
        withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO island_settings " +
                    "(grid_x, grid_z, dimension, pvp_enabled, visitors_allowed, explosions_enabled, " +
                    "fire_spread_enabled, mob_spawning_enabled, crop_trampling_enabled, animal_spawning_enabled, " +
                    "leaf_decay_enabled, border_color, border_size, border_markers_enabled, warp_enabled, warp_description, keep_inventory_enabled) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bindIslandSettings(ps, settings);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    // Warp persistence (promoted from IslandWarpManager for full DB modularization + grid consistency)
    public CompletableFuture<IslandWarp> loadIslandWarp(GridPosition pos) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT * FROM island_warps WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            IslandWarp warp = new IslandWarp(pos);
                            org.bukkit.World world = org.bukkit.Bukkit.getWorld(rs.getString("world"));
                            if (world != null) {
                                org.bukkit.Location loc = new org.bukkit.Location(
                                        world,
                                        rs.getDouble("x"),
                                        rs.getDouble("y"),
                                        rs.getDouble("z"),
                                        (float) rs.getDouble("yaw"),
                                        (float) rs.getDouble("pitch")
                                );
                                warp.setWarpLocation(loc);
                                warp.setEnabled(rs.getBoolean("enabled"));
                            }
                            return warp;
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return new IslandWarp(pos);
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] loadIslandWarp failed: " + e.getMessage());
                return new IslandWarp(pos);
            }
        });
    }

    public void saveIslandWarp(IslandWarp warp) {
        GridPosition pos = warp.getGridPosition();
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_warps (grid_x, grid_z, dimension, world, x, y, z, yaw, pitch, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        org.bukkit.Location loc = warp.getWarpLocation();
                        ps.setString(4, loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "world");
                        ps.setDouble(5, loc != null ? loc.getX() : 0);
                        ps.setDouble(6, loc != null ? loc.getY() : 64);
                        ps.setDouble(7, loc != null ? loc.getZ() : 0);
                        ps.setDouble(8, loc != null ? loc.getYaw() : 0);
                        ps.setDouble(9, loc != null ? loc.getPitch() : 0);
                        ps.setBoolean(10, warp.isEnabled());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandWarp failed: " + e.getMessage());
            }
        });
    }

    // Rating persistence (promoted from IslandRatingManager; grid PK, supports per-player + aggregates)
    public void rateIsland(GridPosition pos, UUID playerUuid, int rating) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_ratings (grid_x, grid_z, dimension, player_uuid, rating, timestamp) VALUES (?, ?, ?, ?, ?, ?)")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ps.setString(4, playerUuid.toString());
                        ps.setInt(5, Math.max(1, Math.min(5, rating)));
                        ps.setLong(6, System.currentTimeMillis());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] rateIsland failed: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Double> getAverageRating(GridPosition pos) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT AVG(rating) as avg_rating FROM island_ratings WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getDouble("avg_rating") : 0.0;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                return 0.0;
            }
        });
    }

    public CompletableFuture<Integer> getRatingCount(GridPosition pos) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) as count FROM island_ratings WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getInt("count") : 0;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                return 0;
            }
        });
    }

    public CompletableFuture<Map<GridPosition, Double>> getTopRatedIslands(int limit) {
        return getTopRatedIslands(limit, 0);
    }

    /**
     * DB-paginated top rated islands query for large scale (1000+ islands/players).
     * Uses server-side LIMIT + OFFSET so only the requested page of the leaderboard is loaded
     * (data compression, no full ratings scan in memory). Complements event sinks (topsDirty)
     * and per-island RegionScheduler stagger notes for globals/leaderboards.
     * See IMPROVEMENTS.md "For 1000+ islands: make leaderboard/top queries fully DB paginated",
     * "staggered RegionScheduler for more (e.g. global tops, leaderboards)".
     */
    public CompletableFuture<Map<GridPosition, Double>> getTopRatedIslands(int limit, int offset) {
        return supplyAsync(() -> {
            Map<GridPosition, Double> top = new LinkedHashMap<>();
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT grid_x, grid_z, dimension, AVG(rating) as avg_rating, COUNT(*) as count " +
                                    "FROM island_ratings " +
                                    "GROUP BY grid_x, grid_z, dimension " +
                                    "HAVING count >= 1 " +
                                    "ORDER BY avg_rating DESC, count DESC " +
                                    "LIMIT ? OFFSET ?")) {
                        ps.setInt(1, limit);
                        ps.setInt(2, Math.max(0, offset));
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            GridPosition p = new GridPosition(
                                    rs.getInt("grid_x"),
                                    rs.getInt("grid_z"),
                                    org.bukkit.World.Environment.valueOf(rs.getString("dimension"))
                            );
                            top.put(p, rs.getDouble("avg_rating"));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] getTopRatedIslands failed: " + e.getMessage());
            }
            return top;
        });
    }

    public CompletableFuture<Integer> getPlayerRating(GridPosition pos, UUID playerUuid) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT rating FROM island_ratings WHERE grid_x = ? AND grid_z = ? AND dimension = ? AND player_uuid = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.getDimension().name());
                        ps.setString(4, playerUuid.toString());
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? rs.getInt("rating") : 0;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                return 0;
            }
        });
    }

    // Global public warps for browse (promoted; used by IslandBrowseGUI etc. for /is browse etc.)
    public CompletableFuture<Map<GridPosition, IslandWarp>> loadAllPublicWarps() {
        return loadAllPublicWarps(0); // 0 = no limit (all)
    }

    /**
     * DB-compressed load for public warps (large scale: many islands may have public warps enabled).
     * limit=0 means no limit (backward compat for current browse). Use positive limit to cap
     * result size for memory/work compression on 100s-1000+ servers. Pair with event-driven
     * invalidation (e.g. on warp enable/disable) + per-island Region notes.
     */
    public CompletableFuture<Map<GridPosition, IslandWarp>> loadAllPublicWarps(int limit) {
        return supplyAsync(() -> {
            Map<GridPosition, IslandWarp> publicWarps = new ConcurrentHashMap<>();
            try {
                withConnection(conn -> {
                    String sql = "SELECT * FROM island_warps WHERE enabled = 1";
                    if (limit > 0) sql += " LIMIT ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        if (limit > 0) ps.setInt(1, limit);
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            GridPosition pos = new GridPosition(
                                    rs.getInt("grid_x"),
                                    rs.getInt("grid_z"),
                                    org.bukkit.World.Environment.valueOf(rs.getString("dimension"))
                            );
                            IslandWarp warp = new IslandWarp(pos);
                            org.bukkit.World world = org.bukkit.Bukkit.getWorld(rs.getString("world"));
                            if (world != null) {
                                org.bukkit.Location loc = new org.bukkit.Location(
                                        world,
                                        rs.getDouble("x"),
                                        rs.getDouble("y"),
                                        rs.getDouble("z"),
                                        (float) rs.getDouble("yaw"),
                                        (float) rs.getDouble("pitch")
                                );
                                warp.setWarpLocation(loc);
                                warp.setEnabled(rs.getBoolean("enabled"));
                                publicWarps.put(pos, warp);
                            }
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] loadAllPublicWarps failed: " + e.getMessage());
            }
            return publicWarps;
        });
    }

    // ==================== TASK BATCH: FULL DB-PAGINATED TOPS FOR PAPI (and large server /is top) ====================
    // Replaces simple cache iteration in PAPI expansion. Uses SQL ORDER BY + LIMIT OFFSET for 500+ islands scale.
    // Inter-class: called from IslandManager (new wrappers) -> DBManager/IslandDAO -> PAPI expansion and GUIs.
    // Folia: fully async CompletableFuture. PtW: pure play data (no pay multipliers).

    /**
     * Shared query helper for the two rich per-category top queries (level + members).
     * Both previously duplicated the same SELECT (COALESCE for level, worth subq from island_worth for display,
     * memberCount subq from island_members, same LEFT JOIN + result mapping to TopIslandEntry 5-arg ctor).
     * Only the ORDER BY clause differed. This centralizes the SQL construction + execution + mapping.
     *
     * Benefits for large scale + persistence:
     * - Single place to update if we extend the common projection (e.g. pull last_*_rank columns from island_worth
     *   for richer top entries without duplicating subqueries).
     * - Easier to add filters, change joins, or switch to a more formal query builder later.
     * - Reduces maintenance surface for the persisted tops/ranks paths (cross-ref snapshot columns + my-rank COUNTs).
     * - Keeps zero new deps, pure JDBC in withConnection style.
     */
    private java.util.List<TopIslandEntry> fetchRichPagedTopIslands(java.sql.Connection conn, String orderByClause, int limit, int offset) {
        java.util.List<TopIslandEntry> top = new java.util.ArrayList<>();
        String sql = "SELECT i.owner_uuid, COALESCE(il.level, i.level) as lvl, i.dimension, " +
                     "COALESCE((SELECT worth FROM island_worth ww WHERE ww.grid_x = i.grid_x AND ww.grid_z = i.grid_z AND ww.dimension = i.dimension), 0) as w, " +
                     "COALESCE((SELECT ww.member_count FROM island_worth ww WHERE ww.grid_x = i.grid_x AND ww.grid_z = i.grid_z AND ww.dimension = i.dimension), 0) as mc, " +
                     "COALESCE((SELECT ip.prestige_level FROM island_prestige ip WHERE ip.island_key = i.owner_uuid || '_' || i.dimension), 0) as prestige " +
                     "FROM islands i LEFT JOIN island_levels il ON (i.owner_uuid || '_' || i.dimension = il.island_key) " +
                     orderByClause + " LIMIT ? OFFSET ?";
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            ps.setInt(2, Math.max(0, offset));
            java.sql.ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                try {
                    UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                    int lvl = rs.getInt("lvl");
                    String dim = rs.getString("dimension");
                    double worth = rs.getDouble("w");
                    int mc = rs.getInt("mc");
                    int prestige = rs.getInt("prestige");
                    top.add(new TopIslandEntry(owner, lvl, dim, worth, mc, prestige));
                } catch (Exception ignored) {}
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
        return top;
    }

    /**
     * DB-paginated top islands by XP/level (from island_levels joined or islands level).
     * For PAPI %f oliaskyblock_top_level_N% etc.
     */
    public CompletableFuture<java.util.List<TopIslandEntry>> getTopIslandsByLevel(int limit, int offset) {
        return supplyAsync(() -> {
            java.util.List<TopIslandEntry> top = new java.util.ArrayList<>();
            try {
                return withConnection(conn -> {
                    // Delegate to shared query helper (see fetchRichPagedTopIslands). Centralizes the rich SELECT
                    // (level COALESCE + worth/member subqs) + mapping. Only ORDER BY is specific here.
                    // Compression win: ~25 lines of duplicated SQL+loop removed; future changes (e.g. including
                    // persisted last_*_rank columns from island_worth for top entries) touch one place.
                    return fetchRichPagedTopIslands(conn, "ORDER BY lvl DESC", limit, offset);
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] getTopIslandsByLevel failed: " + e.getMessage());
                return top;
            }
        });
    }

    /**
     * DB-paginated top islands by member count (for /is top members + IslandTopGUI MEMBERS category).
     * Uses subquery for count (from island_members). Pulls level + worth too for rich display in GUI lores.
     * True server-side pagination (LIMIT OFFSET) for large scale compression (no loading all islands).
     * Complements the worth and level tops; advances per-category dedicated paginated leaderboards.
     */
    public CompletableFuture<java.util.List<TopIslandEntry>> getTopIslandsByMemberCount(int limit, int offset) {
        return supplyAsync(() -> {
            java.util.List<TopIslandEntry> top = new java.util.ArrayList<>();
            try {
                return withConnection(conn -> {
                    // Delegate to shared query helper (see fetchRichPagedTopIslands javadoc for dupe-reduction rationale).
                    // ORDER BY specific to member count primary, level secondary (consistent with prior impl).
                    return fetchRichPagedTopIslands(conn, "ORDER BY mc DESC, lvl DESC", limit, offset);
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] getTopIslandsByMemberCount failed: " + e.getMessage());
                return top;
            }
        });
    }

    /**
     * DB-paginated top by worth (from island_worth table).
     * Returns entries with worth populated.
     */
    public CompletableFuture<java.util.List<TopIslandEntry>> getTopIslandsByWorth(int limit, int offset) {
        return supplyAsync(() -> {
            java.util.List<TopIslandEntry> top = new java.util.ArrayList<>();
            try {
                return withConnection(conn -> {
                    String sql = "SELECT w.grid_x, w.grid_z, w.dimension, w.worth, w.worth_level, i.owner_uuid " +
                                 "FROM island_worth w JOIN islands i ON (w.grid_x = i.grid_x AND w.grid_z = i.grid_z AND w.dimension = i.dimension) " +
                                 "ORDER BY w.worth DESC, w.worth_level DESC LIMIT ? OFFSET ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, Math.max(1, limit));
                        ps.setInt(2, Math.max(0, offset));
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            try {
                                UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                                int lvl = rs.getInt("worth_level");
                                String dim = rs.getString("dimension");
                                double worth = rs.getDouble("worth");
                                TopIslandEntry e = new TopIslandEntry(owner, lvl, dim, worth);
                                top.add(e);
                            } catch (Exception ignored) {}
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return top;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] getTopIslandsByWorth failed: " + e.getMessage());
                return top;
            }
        });
    }

    // Small shared helpers for the live COUNT fallbacks in my-rank queries (the "rank" side of top/rank queries).
    // These were previously inlined (slightly different subq for level vs simple for worth). Extracted alongside
    // the rich tops fetch helper to fulfill the "shared query builder/helper in IslandDAO (reduce duplication in
    // the similar subqueries for worth/level/member + rank COUNTs)" suggestion. Makes it trivial to evolve the
    // rank computation (e.g. tie-break rules, dimension scoping, or using persisted snapshots more) in one place.
    private int computeHigherWorthCount(java.sql.Connection conn, double myWorth) {
        try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM island_worth WHERE worth > ?")) {
            ps.setDouble(1, myWorth);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) + 1;
            return 1;
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int computeHigherLevelCount(java.sql.Connection conn, int myLevel) {
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM (" +
                "SELECT COALESCE(il.level, i.level) as lvl FROM islands i LEFT JOIN island_levels il ON (i.owner_uuid || '_' || i.dimension = il.island_key)" +
                ") sub WHERE lvl > ?")) {
            ps.setInt(1, myLevel);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) + 1;
            return 1;
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Efficient my-worth-rank using the persisted island_worth table.
     * Returns 1-based rank (number of islands with strictly higher worth + 1).
     * Uses COUNT subquery for O(log N) or better with index (no full materialization of tops list).
     * Global across dimensions (matches the worth tops leaderboard behavior).
     * For PAPI %f oliaskyblock_my_worth_rank% and /is rank worth.
     * Persistence-backed (island_worth is the source of truth for worth tops).
     */
    public CompletableFuture<Integer> getMyWorthRank(GridPosition pos) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    // Get my worth for this pos (grid+dim)
                    double myWorth = 0.0;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT worth FROM island_worth WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.dimension() != null ? pos.dimension().name() : "NORMAL");
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            myWorth = rs.getDouble(1);
                        }
                    } catch (SQLException sqle) {
                        throw new RuntimeException(sqle);
                    }
                    if (myWorth <= 0) return 999999; // not ranked or no worth yet

                    // Prefer persisted snapshot if available (O(1) after initial compute; refreshed on changes).
                    int snap = loadLastWorthRankSnapshot(pos);
                    if (snap > 0) {
                        return snap;
                    }

                    // Compute live (COUNT on persisted data) -- now via shared helper (see computeHigherWorthCount).
                    int computed = computeHigherWorthCount(conn, myWorth);

                    // Persist the snapshot for future fast reads (compression/persistence win for large scale + frequent access).
                    saveIslandRankSnapshot(pos, computed, 0); // level snapshot can be filled similarly
                    return computed;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] getMyWorthRank failed: " + e.getMessage());
                return 0;
            }
        });
    }

    /**
     * Efficient my-level-rank using persisted levels.
     * Similar COUNT on the effective level.
     */
    public CompletableFuture<Integer> getMyLevelRank(GridPosition pos) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    int myLevel = 0;
                    // Prefer island_levels, fallback islands.level (same as top query)
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COALESCE(il.level, i.level) as lvl FROM islands i LEFT JOIN island_levels il ON (i.owner_uuid || '_' || i.dimension = il.island_key) WHERE i.grid_x = ? AND i.grid_z = ? AND i.dimension = ?")) {
                        ps.setInt(1, pos.x());
                        ps.setInt(2, pos.z());
                        ps.setString(3, pos.dimension() != null ? pos.dimension().name() : "NORMAL");
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) myLevel = rs.getInt("lvl");
                    } catch (SQLException sqle) {
                        throw new RuntimeException(sqle);
                    }
                    if (myLevel <= 0) return 999999;

                    // Prefer persisted snapshot if available.
                    int snap = loadLastLevelRankSnapshot(pos);
                    if (snap > 0) {
                        return snap;
                    }

                    // Compute live (COUNT on effective level) -- now via shared helper (see computeHigherLevelCount).
                    int computed = computeHigherLevelCount(conn, myLevel);

                    // Persist snapshot.
                    saveIslandRankSnapshot(pos, 0, computed);
                    return computed;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] getMyLevelRank failed: " + e.getMessage());
                return 0;
            }
        });
    }

    /**
     * Save the last computed ranks for this pos into the persisted island_worth row (snapshot for fast future reads).
     * Called after rank computation on significant changes (prestige, full worth recalc) or periodic refresh for top islands.
     */
    public void saveIslandRankSnapshot(GridPosition pos, int worthRank, int levelRank) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE island_worth SET last_worth_rank = ?, last_level_rank = ? WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, worthRank);
                        ps.setInt(2, levelRank);
                        ps.setInt(3, pos.x());
                        ps.setInt(4, pos.z());
                        ps.setString(5, pos.getDimension() != null ? pos.getDimension().name() : "NORMAL");
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandRankSnapshot failed: " + e.getMessage());
            }
        });
    }

    /**
     * Save rank snapshot by owner+dimension (convenience for refresh-from-tops-window).
     * Does a cheap grid lookup then delegates to the column update. Useful after a top list
     * (from cache pre-warm or GUI page 0) is materialized: we can stamp the *current* ranks
     * (from list position) into the last_* columns for O(1) my-rank for those islands without
     * re-running COUNTs. Complements the per-island save on calc/prestige.
     */
    public void saveIslandRankSnapshotByOwner(UUID owner, String dimension, int worthRank, int levelRank) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    int gx = 0, gz = 0;
                    String dim = (dimension != null ? dimension : "NORMAL");
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT grid_x, grid_z FROM islands WHERE owner_uuid = ? AND dimension = ? LIMIT 1")) {
                        ps.setString(1, owner.toString());
                        ps.setString(2, dim);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            gx = rs.getInt(1);
                            gz = rs.getInt(2);
                        } else {
                            return null;
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    if (gx == 0 && gz == 0) return null;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE island_worth SET last_worth_rank = ?, last_level_rank = ? WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, worthRank);
                        ps.setInt(2, levelRank);
                        ps.setInt(3, gx);
                        ps.setInt(4, gz);
                        ps.setString(5, dim);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandRankSnapshotByOwner failed: " + e.getMessage());
            }
        });
    }

    /**
     * Load the last persisted rank snapshot for this pos (if any).
     * Returns the worthRank (or 0 if none).
     */
    public int loadLastWorthRankSnapshot(GridPosition pos) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_worth_rank FROM island_worth WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                    ps.setInt(1, pos.x());
                    ps.setInt(2, pos.z());
                    ps.setString(3, pos.getDimension() != null ? pos.getDimension().name() : "NORMAL");
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return 0;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[IslandDAO] loadLastWorthRankSnapshot failed: " + e.getMessage());
            return 0;
        }
    }

    public int loadLastLevelRankSnapshot(GridPosition pos) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_level_rank FROM island_worth WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                    ps.setInt(1, pos.x());
                    ps.setInt(2, pos.z());
                    ps.setString(3, pos.getDimension() != null ? pos.getDimension().name() : "NORMAL");
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return 0;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[IslandDAO] loadLastLevelRankSnapshot failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Find islands that have persisted worth but are missing rank snapshots (last_*_rank <=0).
     * Used by backfill task to ensure O(1) my-ranks for as many islands as possible without forcing COUNT on every access.
     * Limits to avoid long runs on very large servers.
     */
    public java.util.List<GridPosition> findIslandsNeedingRankSnapshotBackfill(int limit) {
        try {
            return withConnection(conn -> {
                java.util.List<GridPosition> list = new java.util.ArrayList<>();
                String sql = "SELECT grid_x, grid_z, dimension FROM island_worth WHERE worth > 0 AND (last_worth_rank <= 0 OR last_level_rank <= 0) LIMIT ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, Math.max(1, limit));
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        int gx = rs.getInt(1);
                        int gz = rs.getInt(2);
                        String d = rs.getString(3);
                        World.Environment env = World.Environment.NORMAL;
                        try {
                            if (d != null) env = World.Environment.valueOf(d);
                        } catch (Exception ignored) {}
                        list.add(new GridPosition(gx, gz, env));
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return list;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[IslandDAO] findIslandsNeedingRankSnapshotBackfill failed: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Fire-and-forget backfill for missing rank snapshots (up to maxBatch).
     * For each needing pos, calling getMy* will hit the compute+save path (since snap missing) and persist the COUNT result.
     * Intended to be called from low-frequency global Folia task (e.g. 30min) or on startup delayed.
     * Complements the window-stamp on cache refresh (which covers the "hot" top islands for free using list positions, no COUNT).
     */
    public void backfillMissingRankSnapshots(int maxBatch) {
        runAsync(() -> {
            try {
                java.util.List<GridPosition> needing = findIslandsNeedingRankSnapshotBackfill(maxBatch);
                int processed = 0;
                for (GridPosition pos : needing) {
                    // These are CFs; calling starts the async work which will compute live (if still missing) and save snapshot as side-effect.
                    getMyWorthRank(pos);
                    getMyLevelRank(pos);
                    processed++;
                }
                if (processed > 0) {
                    plugin.getLogger().info("[IslandDAO] Backfilled rank snapshots for " + processed + " islands (one-time COUNT + persist for missing).");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandDAO] backfillMissingRankSnapshots error (non-fatal): " + e.getMessage());
            }
        });
    }

    // ==================== PERSISTED AGGREGATES (member_count + prestige_level snapshots on island_worth) ====================
    // For O(1) access in tops, holograms, PAPI without live subqueries (per IMPROVEMENTS "Persist more aggregates for O(1)/near-O(1)").
    // member_count replaces COUNT subq from island_members in rich top queries + allows fast ORDER BY.
    // prestige_level provides snapshot alongside island_prestige table for display speed.

    public void saveIslandMemberCount(GridPosition pos, int count) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE island_worth SET member_count = ? WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, count);
                        ps.setInt(2, pos.x());
                        ps.setInt(3, pos.z());
                        ps.setString(4, pos.getDimension() != null ? pos.getDimension().name() : "NORMAL");
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandMemberCount failed: " + e.getMessage());
            }
        });
    }

    public void saveIslandPrestigeLevel(GridPosition pos, int level) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE island_worth SET prestige_level = ? WHERE grid_x = ? AND grid_z = ? AND dimension = ?")) {
                        ps.setInt(1, level);
                        ps.setInt(2, pos.x());
                        ps.setInt(3, pos.z());
                        ps.setString(4, pos.getDimension() != null ? pos.getDimension().name() : "NORMAL");
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandPrestigeLevel failed: " + e.getMessage());
            }
        });
    }

    // ==================== MUSEUM PERSIST (task batch: full persist for museum donations + tokens) ====================
    // Per-donation DB rows table (island_museum_donations) for count/rarity support + zero-dep (addresses Gson provided scope runtime issues on non-standard servers; no Gson dep needed).
    // Tokens in island_museum. Inter-class: MuseumManager <-> IslandDAO (via DB) <-> persist.
    // Folia async. Play-to-Win: counts from actual donations only. Rarity derived in manager (e.g. material name).

    public CompletableFuture<Boolean> saveMuseumData(String islandKey, java.util.Map<String, Integer> donatedCounts, int tokens) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try {
                        // Update tokens (keep island_museum for tokens)
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT OR REPLACE INTO island_museum (island_key, donated, tokens) VALUES (?, '', ?)")) {
                            ps.setString(1, islandKey);
                            ps.setInt(2, tokens);
                            ps.executeUpdate();
                        }
                        // Clear old donations for this key
                        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM island_museum_donations WHERE island_key = ?")) {
                            ps.setString(1, islandKey);
                            ps.executeUpdate();
                        }
                        // Insert per-donation rows with count (for count/rarity)
                        if (donatedCounts != null) {
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "INSERT INTO island_museum_donations (island_key, material, count) VALUES (?, ?, ?)")) {
                                for (java.util.Map.Entry<String, Integer> e : donatedCounts.entrySet()) {
                                    ps.setString(1, islandKey);
                                    ps.setString(2, e.getKey());
                                    ps.setInt(3, e.getValue());
                                    ps.executeUpdate();
                                }
                            }
                        }
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandDAO] saveMuseumData failed: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Object[]> loadMuseumData(String islandKey) {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    try {
                        int tokens = 0;
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT tokens FROM island_museum WHERE island_key = ?")) {
                            ps.setString(1, islandKey);
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) tokens = rs.getInt("tokens");
                        }
                        java.util.Map<String, Integer> donated = new java.util.HashMap<>();
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT material, count FROM island_museum_donations WHERE island_key = ?")) {
                            ps.setString(1, islandKey);
                            ResultSet rs = ps.executeQuery();
                            while (rs.next()) {
                                donated.put(rs.getString("material"), rs.getInt("count"));
                            }
                        }
                        return new Object[]{donated, tokens};
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandDAO] loadMuseumData failed: " + e.getMessage());
                return new Object[]{new java.util.HashMap<String, Integer>(), 0};
            }
        });
    }

    // ==================== SEASONAL RESET SUPPORT (Option B full impl) ====================

    /**
     * Returns all current island grid positions for physical clear pass + grid reset after wipe.
     * Used by SeasonManager for staggered RegionScheduler clears (Option B).
     * Async but often .join() in reset orchestration on admin thread for simplicity (small data).
     */
    public CompletableFuture<java.util.List<GridPosition>> getAllIslandGrids() {
        return supplyAsync(() -> {
            java.util.List<GridPosition> grids = new java.util.ArrayList<>();
            try {
                return withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT grid_x, grid_z, dimension FROM islands")) {
                        ResultSet rs = ps.executeQuery();
                        while (rs.next()) {
                            int gx = rs.getInt("grid_x");
                            int gz = rs.getInt("grid_z");
                            String dimStr = rs.getString("dimension");
                            try {
                                org.bukkit.World.Environment env = org.bukkit.World.Environment.valueOf(dimStr);
                                grids.add(new GridPosition(gx, gz, env));
                            } catch (Exception ignored) {
                                grids.add(new GridPosition(gx, gz, org.bukkit.World.Environment.NORMAL));
                            }
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return grids;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] getAllIslandGrids failed: " + e.getMessage());
                return grids;
            }
        });
    }

    /**
     * Performs full server-wide seasonal data wipe for island-bound progress (Option B).
     * Selectively deletes all competitive/island state while leaving player cosmetics, skills, slayer, ranks, player_balances (caller decides).
     * Reuses and extends the per-island cleanupIslandData patterns for correctness and safety.
     * Also clears auctions/bazaar active orders (fresh economy for new season).
     * Returns rough count of islands processed (for logging/audit).
     *
     * IMPORTANT: This is destructive. Caller must have done confirmation + backup + dry-run.
     * Folia/async safe (all via supplyAsync + withConnection).
     */
    public CompletableFuture<Integer> performSeasonalIslandWipe() {
        return supplyAsync(() -> {
            try {
                return withConnection(conn -> {
                    int wipedIslands = 0;
                    // First, collect grids for reference (and later physical clear uses pre-wipe list)
                    java.util.List<GridPosition> grids = new java.util.ArrayList<>();
                    try (PreparedStatement gq = conn.prepareStatement("SELECT grid_x, grid_z, dimension FROM islands")) {
                        ResultSet grs = gq.executeQuery();
                        while (grs.next()) {
                            int gx = grs.getInt(1);
                            int gz = grs.getInt(2);
                            String d = grs.getString(3);
                            try {
                                grids.add(new GridPosition(gx, gz, org.bukkit.World.Environment.valueOf(d)));
                            } catch (Exception ignored) {
                                grids.add(new GridPosition(gx, gz, org.bukkit.World.Environment.NORMAL));
                            }
                        }
                    } catch (SQLException ignored) {}

                    // Delete core islands (this invalidates island_id for members)
                    try {
                        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM islands")) {
                            wipedIslands = ps.executeUpdate();
                        }
                    } catch (SQLException ignored) {}

                    // Bulk delete island_members (orphaned by islands delete, but explicit for safety)
                    try {
                        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM island_members")) {
                            ps.executeUpdate();
                        }
                    } catch (SQLException ignored) {}

                    // Per-island key + grid cleanups for all known island_* tables (extended from cleanupIslandData)
                    // Key-based (island_key = owner_dim usually)
                    String[] keyTables = {
                        "island_upgrades", "island_levels", "island_skills", "island_milestones",
                        "island_fuel", "island_boosters", "island_collections",
                        "island_active_weather", "island_active_music",
                        "island_missions", "island_prestige", "island_shop_purchases",
                        "island_museum", "island_museum_donations",
                        "minion_skin_assignments",
                        "island_active_quests", "island_story_progress"
                        // Full reset of all quests (incl. MAIN_STORY progress) on seasonal wipes, as requested.
                        // Quests persist only across server restarts via DB load in generate/ensure.
                        // Prestige (player choice) clears for story replay with power; seasonal is full server reset.
                    };
                    for (String t : keyTables) {
                        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + t)) {
                            ps.executeUpdate();
                        } catch (SQLException ignored) { /* table may not exist in old schema */ }
                    }

                    // Grid+dim tables
                    String[] gridTables = {
                        "island_banks", "island_worth", "island_settings", "island_ratings",
                        "island_warps", "island_balances"
                    };
                    for (String t : gridTables) {
                        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + t)) {
                            ps.executeUpdate();
                        } catch (SQLException ignored) {}
                    }

                    // Placed cosmetics per island (ownership player_*_furniture etc stay for persistence)
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM island_placed_furniture")) { ps.executeUpdate(); } catch (SQLException ignored) {}
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM island_placed_structures")) { ps.executeUpdate(); } catch (SQLException ignored) {}

                    // Market reset for new season economy (active only; history can stay or be archived separately)
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auctions WHERE sold = 0")) { ps.executeUpdate(); } catch (SQLException ignored) {}
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bazaar_orders WHERE filled = 0")) { ps.executeUpdate(); } catch (SQLException ignored) {}

                    // Reset dimension cooldowns for fresh season (player can re-experience early game cooldowns)
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_dimension_resets")) { ps.executeUpdate(); } catch (SQLException ignored) {}

                    // Note: player_balances, player_skills, slayer_*, player_ranks, all player_* cosmetics are DELIBERATELY untouched here.
                    // They provide the "persistence on donor items".

                    plugin.getLogger().info("[IslandDAO] Seasonal wipe complete. Islands removed: " + wipedIslands + ", grids cleared in DB: " + grids.size());
                    return wipedIslands;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] performSeasonalIslandWipe failed: " + e.getMessage());
                return 0;
            }
        });
    }

    /**
     * Optional: stamp the season on newly created islands (for future per-season analytics/tops).
     * Called from IslandManager create success path if SeasonManager present.
     */
    // stampIslandSeason omitted for initial seasonal wipe (optional for future per-season analytics; can be added cleanly later without breaking core reset).
}