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

    public IslandDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
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
        runAsync(() -> {
            try {
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
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandWorth failed: " + e.getMessage());
            }
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
        runAsync(() -> {
            try {
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
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandBankBalance failed: " + e.getMessage());
            }
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
        GridPosition pos = settings.getGridPosition();
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO island_settings " +
                            "(grid_x, grid_z, dimension, pvp_enabled, visitors_allowed, explosions_enabled, " +
                            "fire_spread_enabled, mob_spawning_enabled, crop_trampling_enabled, animal_spawning_enabled, " +
                            "leaf_decay_enabled, border_color, border_size, border_markers_enabled, warp_enabled, warp_description) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[IslandDAO] saveIslandSettings failed: " + e.getMessage());
            }
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
}