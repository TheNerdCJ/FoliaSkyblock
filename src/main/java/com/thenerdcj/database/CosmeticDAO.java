package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Data Access Object for player cosmetic ownership, active state, and collections.
 * 
 * Extracted as continuation of DB modularization to thin the god-class DatabaseManager.
 * 
 * Handles many player_* tables for cosmetics (trails, pets, tags, wings, runes, skins, furniture ownership,
 * music, overhead, emotes, structures, bubbles, accessories, etc.).
 * 
 * Island-placed state (e.g. placed_furniture) remains with IslandDAO for now.
 * 
 * Pattern: For each cosmetic type, typically:
 * - player_<type> for owned (with optional variant/skin)
 * - player_active_<type> for current active
 * - player_<type>_collection for discovery (if applicable)
 * 
 * This DAO provides reusable methods + specific for common ones.
 * More can be added following the same supplyAsync / bridge pattern.
 */
public class CosmeticDAO extends BaseDAO {

    public CosmeticDAO(FoliaSkyblock plugin, DBOperations dbOps) {
        super(plugin, dbOps);
    }

    @Override
    public void initialize() {
        // All player_* cosmetic tables created centrally in DatabaseManager.initDatabase().
    }

    // ==================== GENERIC HELPERS (for future expansion) ====================

    public void saveOwnedCosmetic(UUID uuid, String table, String idColumn, String idValue, String extraColumn, String extraValue) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO " + table + " (uuid, " + idColumn + (extraColumn != null ? ", " + extraColumn : "") + ") VALUES (?, ?" + (extraColumn != null ? ", ?" : "") + ")")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, idValue);
                        if (extraColumn != null) ps.setString(3, extraValue);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] saveOwnedCosmetic failed for " + table + ": " + e.getMessage());
            }
        });
    }

    // ==================== PARTICLE TRAILS ====================

    public void savePlayerTrails(UUID uuid, Set<String> trailIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_particle_trails WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement(
                                 "INSERT OR IGNORE INTO player_particle_trails (uuid, trail_id, unlocked_at) VALUES (?, ?, ?)")) {
                        del.setString(1, uuid.toString());
                        del.executeUpdate();
                        long now = System.currentTimeMillis();
                        for (String id : trailIds) {
                            ins.setString(1, uuid.toString());
                            ins.setString(2, id);
                            ins.setLong(3, now);
                            ins.executeUpdate();
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] savePlayerTrails failed: " + e.getMessage());
            }
        });
    }

    public Set<String> loadPlayerTrails(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT trail_id FROM player_particle_trails WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) set.add(rs.getString(1));
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                return set;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadPlayerTrails failed: " + e.getMessage());
            return set;
        }
    }

    public void saveActiveTrail(UUID uuid, String trailId) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO player_active_trail (uuid, trail_id, updated_at) VALUES (?, ?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, trailId);
                        ps.setLong(3, System.currentTimeMillis());
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] saveActiveTrail failed: " + e.getMessage());
            }
        });
    }

    public String loadActiveTrail(UUID uuid) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT trail_id FROM player_active_trail WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? rs.getString(1) : null;
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadActiveTrail failed: " + e.getMessage());
            return null;
        }
    }

    // Aliases / exact names used by managers and legacy calls for trails
    public Set<String> loadUnlockedParticleTrails(UUID uuid) {
        return loadPlayerTrails(uuid);
    }

    public void saveUnlockedParticleTrails(UUID uuid, Set<String> trails) {
        savePlayerTrails(uuid, trails);
    }

    public String loadActiveParticleTrail(UUID uuid) {
        return loadActiveTrail(uuid);
    }

    public void saveActiveParticleTrail(UUID uuid, String trailId) {
        saveActiveTrail(uuid, trailId);
    }

    // ==================== PETS (example of more complex with variant/skin) ====================

    public void savePlayerPets(UUID uuid, java.util.List<com.thenerdcj.pets.CosmeticPet> pets, com.thenerdcj.pets.CosmeticPet activePet) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement delete = conn.prepareStatement("DELETE FROM player_pets WHERE uuid = ?");
                         PreparedStatement insert = conn.prepareStatement(
                                 "INSERT OR REPLACE INTO player_pets (uuid, pet_type, custom_name, variant, skin) VALUES (?, ?, ?, ?, ?)")) {
                        delete.setString(1, uuid.toString());
                        delete.executeUpdate();
                        if (pets != null) {
                            for (com.thenerdcj.pets.CosmeticPet pet : pets) {
                                insert.setString(1, uuid.toString());
                                insert.setString(2, pet.getType().name());
                                insert.setString(3, pet.getCustomName());
                                insert.setString(4, pet.getVariant() != null ? pet.getVariant().name() : "NONE");
                                insert.setString(5, pet.getSkin() != null ? pet.getSkin().name() : "NONE");
                                insert.executeUpdate();
                            }
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] savePlayerPets failed: " + e.getMessage());
            }
        });
    }

    // loadPlayerPets is typically called into a manager; provide a basic loader
    public void loadPlayerPets(UUID uuid, com.thenerdcj.pets.PetManager manager) {
        try {
            withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_pets WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        try {
                            com.thenerdcj.pets.PetType type = com.thenerdcj.pets.PetType.valueOf(rs.getString("pet_type"));
                            String name = rs.getString("custom_name");
                            String varStr = rs.getString("variant");
                            com.thenerdcj.pets.PetVariant variant = com.thenerdcj.pets.PetVariant.NONE;
                            if (varStr != null && !varStr.isEmpty() && !"NONE".equalsIgnoreCase(varStr)) {
                                try { variant = com.thenerdcj.pets.PetVariant.valueOf(varStr); } catch (Exception ignored) {}
                            }
                            String skinStr = rs.getString("skin");
                            com.thenerdcj.pets.PetSkin skin = com.thenerdcj.pets.PetSkin.NONE;
                            if (skinStr != null && !skinStr.isEmpty() && !"NONE".equalsIgnoreCase(skinStr)) {
                                try { skin = com.thenerdcj.pets.PetSkin.valueOf(skinStr); } catch (Exception ignored) {}
                            }
                            manager.addPet(uuid, new com.thenerdcj.pets.CosmeticPet(type, name != null ? name : type.getDisplayName(), variant, skin));
                        } catch (Exception ignored) {}
                    }
                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadPlayerPets failed: " + e.getMessage());
        }
    }

    public void savePlayerPetSkins(UUID uuid, Set<String> skinIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_pet_skins WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_pet_skins (uuid, skin_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString());
                        del.executeUpdate();
                        for (String id : skinIds) {
                            ins.setString(1, uuid.toString());
                            ins.setString(2, id);
                            ins.executeUpdate();
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] savePlayerPetSkins failed: " + e.getMessage());
            }
        });
    }

    public Set<String> loadPlayerPetSkins(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT skin_id FROM player_pet_skins WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadPlayerPetSkins failed: " + e.getMessage());
            return set;
        }
    }

    // Collection helpers (example for pets)
    public void savePetCollectionEntry(UUID uuid, String petTypeName) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO player_pet_collection (uuid, pet_type) VALUES (?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, petTypeName);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] savePetCollectionEntry failed: " + e.getMessage());
            }
        });
    }

    public Set<String> loadPetCollection(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT pet_type FROM player_pet_collection WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadPetCollection failed: " + e.getMessage());
            return set;
        }
    }

    // ==================== TAGS (another representative) ====================

    public void savePlayerTags(UUID uuid, Set<String> tagEntries, String activeTagEntry) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_tags WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR REPLACE INTO player_tags (uuid, tag_id, variant) VALUES (?, ?, ?)")) {
                        del.setString(1, uuid.toString());
                        del.executeUpdate();
                        if (tagEntries != null) {
                            for (String entry : tagEntries) {
                                // entry may be "ID:VARIANT"
                                String[] parts = entry.split(":", 2);
                                ins.setString(1, uuid.toString());
                                ins.setString(2, parts[0]);
                                ins.setString(3, parts.length > 1 ? parts[1] : "NONE");
                                ins.executeUpdate();
                            }
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] savePlayerTags failed: " + e.getMessage());
            }
        });
    }

    public Set<String> loadPlayerTags(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT tag_id, variant FROM player_tags WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        String id = rs.getString("tag_id");
                        String variant = rs.getString("variant");
                        set.add(id + (variant != null && !variant.isEmpty() && !"NONE".equals(variant) ? ":" + variant : ""));
                    }
                    return set;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadPlayerTags failed: " + e.getMessage());
            return set;
        }
    }

    public String loadActivePlayerTag(UUID uuid) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT tag_id, variant FROM player_active_tag WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String id = rs.getString("tag_id");
                        String variant = rs.getString("variant");
                        return id + (variant != null && !variant.isEmpty() && !"NONE".equals(variant) ? ":" + variant : "");
                    }
                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            return null;
        }
    }

    // Active tag save similar pattern (omitted for brevity, can be added symmetrically)
    public void saveActiveTag(UUID uuid, String tagEntry) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO player_active_tag (uuid, tag_id, variant, updated_at) VALUES (?, ?, ?, ?)")) {
                        String[] parts = (tagEntry != null ? tagEntry : "").split(":", 2);
                        ps.setString(1, uuid.toString());
                        ps.setString(2, parts[0]);
                        ps.setString(3, parts.length > 1 ? parts[1] : "NONE");
                        ps.setLong(4, System.currentTimeMillis());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] saveActiveTag failed: " + e.getMessage());
            }
        });
    }

    // Collection for tags
    public void savePlayerTagCollectionEntry(UUID uuid, String tagId) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO player_tag_collection (uuid, tag_id) VALUES (?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, tagId);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] savePlayerTagCollectionEntry failed: " + e.getMessage());
            }
        });
    }

    public Set<String> loadPlayerTagCollection(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT tag_id FROM player_tag_collection WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadPlayerTagCollection failed: " + e.getMessage());
            return set;
        }
    }

    // ==================== ACTIVE PET (for load/save in pet persistence) ====================

    public void saveActivePet(UUID uuid, com.thenerdcj.pets.CosmeticPet activePet) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO player_active_pet (uuid, pet_type, custom_name, skin) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, uuid.toString());
                        if (activePet != null) {
                            ps.setString(2, activePet.getType().name());
                            ps.setString(3, activePet.getCustomName());
                            ps.setString(4, activePet.getSkin() != null ? activePet.getSkin().name() : "NONE");
                        } else {
                            ps.setString(2, null);
                            ps.setString(3, null);
                            ps.setString(4, "NONE");
                        }
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] saveActivePet failed: " + e.getMessage());
            }
        });
    }

    // Note: load of active is often done together with loadPlayerPets in manager; here for completeness
    public com.thenerdcj.pets.CosmeticPet loadActivePet(UUID uuid) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_active_pet WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String typeStr = rs.getString("pet_type");
                        if (typeStr != null) {
                            try {
                                com.thenerdcj.pets.PetType type = com.thenerdcj.pets.PetType.valueOf(typeStr);
                                String name = rs.getString("custom_name");
                                String varStr = rs.getString("variant");
                                com.thenerdcj.pets.PetVariant variant = com.thenerdcj.pets.PetVariant.NONE;
                                if (varStr != null && !varStr.isEmpty() && !"NONE".equalsIgnoreCase(varStr)) {
                                    try { variant = com.thenerdcj.pets.PetVariant.valueOf(varStr); } catch (Exception ignored) {}
                                }
                                String skinStr = rs.getString("skin");
                                com.thenerdcj.pets.PetSkin skin = com.thenerdcj.pets.PetSkin.NONE;
                                if (skinStr != null && !skinStr.isEmpty() && !"NONE".equalsIgnoreCase(skinStr)) {
                                    try { skin = com.thenerdcj.pets.PetSkin.valueOf(skinStr); } catch (Exception ignored) {}
                                }
                                return new com.thenerdcj.pets.CosmeticPet(type, name, variant, skin);
                            } catch (Exception ignored) {}
                        }
                    }
                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadActivePet failed: " + e.getMessage());
            return null;
        }
    }

    // ==================== ELYTRA WINGS ====================
    public void savePlayerElytraWings(UUID uuid, Set<String> wingIds, String activeWingId) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_elytra_wings WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR REPLACE INTO player_elytra_wings (uuid, wing_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString());
                        del.executeUpdate();
                        for (String id : wingIds) {
                            ins.setString(1, uuid.toString());
                            ins.setString(2, id);
                            ins.executeUpdate();
                        }
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] savePlayerElytraWings failed: " + e.getMessage());
            }
        });
        if (activeWingId != null) {
            runAsync(() -> {
                try {
                    withConnection(conn -> {
                        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_active_elytra_wing (uuid, wing_id) VALUES (?, ?)")) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, activeWingId);
                            ps.executeUpdate();
                        } catch (SQLException ex) {
                            throw new RuntimeException(ex);
                        }
                        return null;
                    });
                } catch (Exception e) {
                    plugin.getLogger().severe("[CosmeticDAO] saveActiveElytraWing failed: " + e.getMessage());
                }
            });
        }
    }

    public Set<String> loadPlayerElytraWings(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT wing_id FROM player_elytra_wings WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) set.add(rs.getString(1));
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                return set;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadPlayerElytraWings failed: " + e.getMessage());
            return set;
        }
    }

    public String loadActiveElytraWing(UUID uuid) {
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT wing_id FROM player_active_elytra_wing WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? rs.getString(1) : null;
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] loadActiveElytraWing failed: " + e.getMessage());
            return null;
        }
    }

    public void saveElytraWingCollectionEntry(UUID uuid, String wingId) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO player_elytra_wing_collection (uuid, wing_id) VALUES (?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, wingId);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] saveElytraWingCollectionEntry failed: " + e.getMessage());
            }
        });
    }

    // ==================== RUNES ====================
    public void savePlayerRunes(UUID uuid, Set<String> runeIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_runes WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR REPLACE INTO player_runes (uuid, rune_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString());
                        del.executeUpdate();
                        for (String id : runeIds) {
                            ins.setString(1, uuid.toString());
                            ins.setString(2, id);
                            ins.executeUpdate();
                        }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerRunes failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerRunes(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT rune_id FROM player_runes WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerRunes failed: " + e.getMessage()); return set; }
    }

    public void saveRuneCollectionEntry(UUID uuid, String runeId) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO player_rune_collection (uuid, rune_id) VALUES (?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, runeId);
                        ps.executeUpdate();
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] saveRuneCollectionEntry failed: " + e.getMessage()); }
        });
    }

    // ==================== HELMET SKINS, DEATH EFFECTS, BACKPACK, POWER ORB, MINION SKINS (simple sets) ====================
    public void savePlayerHelmetSkins(UUID uuid, Set<String> skinIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_helmet_skins WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_helmet_skins (uuid, skin_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : skinIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerHelmetSkins failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerHelmetSkins(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT skin_id FROM player_helmet_skins WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerHelmetSkins failed: " + e.getMessage()); return set; }
    }

    public void savePlayerDeathEffects(UUID uuid, Set<String> effectIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_death_effects WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_death_effects (uuid, effect_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : effectIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerDeathEffects failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerDeathEffects(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT effect_id FROM player_death_effects WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerDeathEffects failed: " + e.getMessage()); return set; }
    }

    public void savePlayerBackpackSkins(UUID uuid, Set<String> skinIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_backpack_skins WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_backpack_skins (uuid, skin_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : skinIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerBackpackSkins failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerBackpackSkins(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT skin_id FROM player_backpack_skins WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerBackpackSkins failed: " + e.getMessage()); return set; }
    }

    public void savePlayerPowerOrbSkins(UUID uuid, Set<String> skinIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_power_orb_skins WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_power_orb_skins (uuid, skin_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : skinIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerPowerOrbSkins failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerPowerOrbSkins(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT skin_id FROM player_power_orb_skins WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerPowerOrbSkins failed: " + e.getMessage()); return set; }
    }

    public void savePlayerMinionSkins(UUID uuid, Set<String> skinIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_minion_skins WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_minion_skins (uuid, skin_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : skinIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerMinionSkins failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerMinionSkins(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT skin_id FROM player_minion_skins WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerMinionSkins failed: " + e.getMessage()); return set; }
    }

    // ==================== ISLAND FURNITURE / STRUCTURES / MUSIC OWNERSHIP (player side) ====================
    public void savePlayerIslandFurniture(UUID uuid, Set<String> furnitureIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_island_furniture WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_island_furniture (uuid, furniture_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : furnitureIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerIslandFurniture failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerIslandFurniture(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT furniture_id FROM player_island_furniture WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerIslandFurniture failed: " + e.getMessage()); return set; }
    }

    public void savePlayerIslandStructures(UUID uuid, Set<String> structureIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_island_structures WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_island_structures (uuid, structure_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : structureIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerIslandStructures failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerIslandStructures(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT structure_id FROM player_island_structures WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerIslandStructures failed: " + e.getMessage()); return set; }
    }

    public void savePlayerIslandMusic(UUID uuid, Set<String> musicIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_island_music WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_island_music (uuid, music_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : musicIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerIslandMusic failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerIslandMusic(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT music_id FROM player_island_music WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerIslandMusic failed: " + e.getMessage()); return set; }
    }

    // ==================== OVERHEAD, EMOTES, CHAT BUBBLES, ACCESSORIES, ISLAND WEATHER ====================
    public void savePlayerOverheadCosmetics(UUID uuid, Set<String> cosmeticIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_overhead_cosmetics WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_overhead_cosmetics (uuid, cosmetic_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : cosmeticIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerOverheadCosmetics failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerOverheadCosmetics(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT cosmetic_id FROM player_overhead_cosmetics WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerOverheadCosmetics failed: " + e.getMessage()); return set; }
    }

    public void savePlayerEmoteCosmetics(UUID uuid, Set<String> emoteIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_emote_cosmetics WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_emote_cosmetics (uuid, emote_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : emoteIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerEmoteCosmetics failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerEmoteCosmetics(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT emote_id FROM player_emote_cosmetics WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerEmoteCosmetics failed: " + e.getMessage()); return set; }
    }

    public void savePlayerChatBubbleCosmetics(UUID uuid, Set<String> bubbleIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_chat_bubble_cosmetics WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_chat_bubble_cosmetics (uuid, bubble_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : bubbleIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerChatBubbleCosmetics failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerChatBubbleCosmetics(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT bubble_id FROM player_chat_bubble_cosmetics WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerChatBubbleCosmetics failed: " + e.getMessage()); return set; }
    }

    public void savePlayerAccessories(UUID uuid, Set<String> accessoryIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_accessories WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_accessories (uuid, accessory_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : accessoryIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerAccessories failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerAccessories(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT accessory_id FROM player_accessories WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerAccessories failed: " + e.getMessage()); return set; }
    }

    public void savePlayerIslandWeather(UUID uuid, Set<String> weatherIds) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_island_weather WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO player_island_weather (uuid, weather_id) VALUES (?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        for (String id : weatherIds) { ins.setString(1, uuid.toString()); ins.setString(2, id); ins.executeUpdate(); }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] savePlayerIslandWeather failed: " + e.getMessage()); }
        });
    }

    public Set<String> loadPlayerIslandWeather(UUID uuid) {
        Set<String> set = new HashSet<>();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT weather_id FROM player_island_weather WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString()); ResultSet rs = ps.executeQuery(); while (rs.next()) set.add(rs.getString(1));
                    return set;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) { plugin.getLogger().severe("[CosmeticDAO] loadPlayerIslandWeather failed: " + e.getMessage()); return set; }
    }

    // ==================== EMOTE TRIGGERS ====================
    public void savePlayerEmoteTriggers(UUID uuid, java.util.Map<String, String> triggers) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_emote_triggers WHERE uuid = ?");
                         PreparedStatement ins = conn.prepareStatement("INSERT OR REPLACE INTO player_emote_triggers (uuid, trigger_key, emote_id) VALUES (?, ?, ?)")) {
                        del.setString(1, uuid.toString()); del.executeUpdate();
                        if (triggers != null) {
                            for (java.util.Map.Entry<String, String> e : triggers.entrySet()) {
                                ins.setString(1, uuid.toString());
                                ins.setString(2, e.getKey());
                                ins.setString(3, e.getValue());
                                ins.executeUpdate();
                            }
                        }
                    } catch (SQLException e) { throw new RuntimeException(e); }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] savePlayerEmoteTriggers failed: " + e.getMessage());
            }
        });
    }

    // ==================== WARDROBE (cosmetic presets) ====================
    // Note: wardrobe is complex with item base64; for now stub or move simple parts. Full move may require ItemSerializer in DAO.
    // For this pass, we delegate calls but leave complex wardrobe logic or move simple load/save if possible.
    // To complete the step, we will add delegation for loadWardrobeForPlayer etc in DM, and stub here or implement basic.

    // ==================== WARDROBE (cosmetic presets - moved here as part of player cosmetic persistence) ====================

    public void saveWardrobeSet(UUID uuid, int slot, String setType, com.thenerdcj.wardrobe.WardrobeSet set) {
        if (set == null) return;
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    String sql = "INSERT OR REPLACE INTO player_wardrobe " +
                            "(uuid, slot, set_type, name, icon, h_base64, c_base64, l_base64, b_base64, e1_base64, e2_base64, e3_base64, e4_base64) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, uuid.toString());
                        ps.setInt(2, slot);
                        ps.setString(3, setType);
                        ps.setString(4, set.getName());
                        ps.setString(5, set.getIcon().name());

                        ItemStack[] armor = set.getArmor();
                        ItemStack[] eq = set.getEquipment();

                        ps.setString(6, com.thenerdcj.util.ItemSerializer.itemToBase64(armor[0]));
                        ps.setString(7, com.thenerdcj.util.ItemSerializer.itemToBase64(armor[1]));
                        ps.setString(8, com.thenerdcj.util.ItemSerializer.itemToBase64(armor[2]));
                        ps.setString(9, com.thenerdcj.util.ItemSerializer.itemToBase64(armor[3]));

                        ps.setString(10, com.thenerdcj.util.ItemSerializer.itemToBase64(eq[0]));
                        ps.setString(11, com.thenerdcj.util.ItemSerializer.itemToBase64(eq[1]));
                        ps.setString(12, com.thenerdcj.util.ItemSerializer.itemToBase64(eq[2]));
                        ps.setString(13, com.thenerdcj.util.ItemSerializer.itemToBase64(eq[3]));

                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] Failed to save wardrobe set: " + e.getMessage());
            }
        });
    }

    public void deleteWardrobeSet(UUID uuid, int slot, String setType) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM player_wardrobe WHERE uuid = ? AND slot = ? AND set_type = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.setInt(2, slot);
                        ps.setString(3, setType);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] Failed to delete wardrobe set: " + e.getMessage());
            }
        });
    }

    public void loadWardrobeForPlayer(UUID uuid, com.thenerdcj.wardrobe.WardrobeManager manager) {
        try {
            withConnection(conn -> {
                String sql = "SELECT * FROM player_wardrobe WHERE uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        int slot = rs.getInt("slot");
                        String setType = rs.getString("set_type");
                        String name = rs.getString("name");
                        String iconName = rs.getString("icon");

                        org.bukkit.Material icon = org.bukkit.Material.matchMaterial(iconName);
                        if (icon == null) icon = org.bukkit.Material.LEATHER_CHESTPLATE;

                        ItemStack[] armor = new ItemStack[4];
                        armor[0] = com.thenerdcj.util.ItemSerializer.itemFromBase64(rs.getString("h_base64"));
                        armor[1] = com.thenerdcj.util.ItemSerializer.itemFromBase64(rs.getString("c_base64"));
                        armor[2] = com.thenerdcj.util.ItemSerializer.itemFromBase64(rs.getString("l_base64"));
                        armor[3] = com.thenerdcj.util.ItemSerializer.itemFromBase64(rs.getString("b_base64"));

                        ItemStack[] equipment = new ItemStack[4];
                        equipment[0] = com.thenerdcj.util.ItemSerializer.itemFromBase64(rs.getString("e1_base64"));
                        equipment[1] = com.thenerdcj.util.ItemSerializer.itemFromBase64(rs.getString("e2_base64"));
                        equipment[2] = com.thenerdcj.util.ItemSerializer.itemFromBase64(rs.getString("e3_base64"));
                        equipment[3] = com.thenerdcj.util.ItemSerializer.itemFromBase64(rs.getString("e4_base64"));

                        com.thenerdcj.wardrobe.WardrobeSet set =
                                new com.thenerdcj.wardrobe.WardrobeSet(name, icon, armor, equipment);

                        if ("ARMOR".equalsIgnoreCase(setType)) {
                            manager.getArmorPresets(uuid).put(slot, set);
                        } else if ("EQUIPMENT".equalsIgnoreCase(setType)) {
                            manager.getEquipmentPresets(uuid).put(slot, set);
                        }
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                return null;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] Failed to load wardrobe for " + uuid + ": " + e.getMessage());
        }
    }

    public void saveWardrobeCollectionEntry(UUID uuid, String materialName) {
        runAsync(() -> {
            try {
                withConnection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO player_wardrobe_collection (uuid, material) VALUES (?, ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, materialName);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().severe("[CosmeticDAO] Failed to save wardrobe collection entry: " + e.getMessage());
            }
        });
    }

    public Set<org.bukkit.Material> loadWardrobeCollection(UUID uuid) {
        Set<org.bukkit.Material> collected = java.util.concurrent.ConcurrentHashMap.newKeySet();
        try {
            return withConnection(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT material FROM player_wardrobe_collection WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        String matName = rs.getString("material");
                        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(matName);
                        if (mat != null) {
                            collected.add(mat);
                        }
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                return collected;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[CosmeticDAO] Failed to load wardrobe collection for " + uuid + ": " + e.getMessage());
            return collected;
        }
    }

    // Bridge complete: all cosmetic player_* moved to withConnection in CosmeticDAO (prior batches).
    // Island-placed (furniture/structures/music) in IslandDAO. No legacy getConnection() left in DAOs.
}