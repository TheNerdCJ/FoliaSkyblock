package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IslandFurnitureManager - Foundation for Play-to-Win island housing cosmetics.
 *
 * Manages:
 * - Per-player furniture unlocks (collection + XP)
 * - Per-island placed furniture (future: actual entity spawning)
 * - Folia-safe placement API (currently stubbed with logging + hooks for real implementation)
 *
 * Follows the exact pattern established by PetSkins, MinionSkins, PowerOrbSkins, etc.
 */
public class IslandFurnitureManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<IslandFurnitureType>> ownedFurniture = new ConcurrentHashMap<>();
    private final Map<String, List<PlacedFurniture>> placedByIsland = new ConcurrentHashMap<>();
    private final Map<String, List<Entity>> activeFurnitureEntities = new ConcurrentHashMap<>();

    // Active themed set bonuses for placed furniture (computed from placed list)
    // When a full "Celestial"/"Slayer"/"Mystic" set is physically placed on the island, the island "activates" the set for cosmetic effects.
    private final Map<String, Set<String>> activeIslandSets = new ConcurrentHashMap<>();

    // Bounded cache constants for large-scale server compression (review/bound all CHM per IMPROVEMENTS optimization)
    private static final int MAX_OWNED_PER_MANAGER = 5000; // cap total players with furniture data in mem
    private static final int MAX_ISLANDS_PLACED = 2000; // cap islands with placed data

    private static final int[] COLLECTION_MILESTONES = {3, 5, 8, 12, 20};

    public IslandFurnitureManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
        // Periodic bounded cache eviction for large scale (100s-1000+ players/islands); per-manager CHM compression
        threadSafety.runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
    }

    public Set<IslandFurnitureType> getOwnedFurniture(UUID uuid) {
        return ownedFurniture.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public int getCollectionCount(UUID uuid) {
        return getOwnedFurniture(uuid).size();
    }

    /** Returns the themed sets (e.g. "Celestial") that are fully placed and active as bonuses on this island. */
    public Set<String> getActiveSetBonuses(String islandKey) {
        return activeIslandSets.getOrDefault(islandKey, Collections.emptySet());
    }

    public void unlockFurniture(UUID uuid, IslandFurnitureType furniture) {
        if (furniture == null || furniture.isNone()) return;

        Set<IslandFurnitureType> owned = getOwnedFurniture(uuid);
        boolean wasNew = owned.add(furniture);

        if (wasNew) {
            int xp = Math.max(15, furniture.getRarity().getXpReward() / 3);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && plugin.getConfig().getBoolean("island.furniture.collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(player, xp);
                player.sendMessage("§a§lIsland Furniture Collection §7» Unlocked " + furniture.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = owned.size();
                for (int m : COLLECTION_MILESTONES) {
                    if (count == m) {
                        player.sendMessage("§6§l★ Island Decorator Milestone! ★ §e" + count + " unique furniture pieces unlocked!");
                        break;
                    }
                }
            }
            plugin.getDatabaseManager().savePlayerIslandFurniture(uuid, getFurnitureIdSet(uuid));

            // Deeper housing: check for completed themed sets and award bonus
            checkAndAwardSetBonuses(player, furniture, owned);
        }
    }

    private void checkAndAwardSetBonuses(Player player, IslandFurnitureType justUnlocked, Set<IslandFurnitureType> owned) {
        if (player == null) return;

        String set = justUnlocked.getFurnitureSet();
        if (set == null || "Basic".equals(set)) return;

        // Define sets
        java.util.List<IslandFurnitureType> setPieces;
        switch (set) {
            case "Celestial":
                setPieces = java.util.Arrays.asList(IslandFurnitureType.CELESTIAL_ORB, IslandFurnitureType.CELESTIAL_BENCH);
                break;
            case "Slayer":
                setPieces = java.util.Arrays.asList(IslandFurnitureType.SLAYER_TROPHY, IslandFurnitureType.SLAYER_TROPHY_RACK);
                break;
            case "Mystic":
                setPieces = java.util.Arrays.asList(IslandFurnitureType.MYSTIC_FOUNTAIN);
                break;
            default:
                return;
        }

        boolean setComplete = true;
        for (IslandFurnitureType piece : setPieces) {
            if (!owned.contains(piece)) {
                setComplete = false;
                break;
            }
        }

        if (setComplete) {
            // Award bonus XP for completing the set
            int bonusXp = 50;
            plugin.getIslandManager().addIslandXp(player, bonusXp);
            player.sendMessage("§6§l★ Furniture Set Complete! ★ §e" + set + " set unlocked! §a+" + bonusXp + " Island XP");

            // Unlock the set bonus furniture automatically (if not already)
            IslandFurnitureType bonus = null;
            switch (set) {
                case "Celestial":
                    bonus = IslandFurnitureType.CELESTIAL_THRONE;
                    break;
                case "Slayer":
                    bonus = IslandFurnitureType.SLAYER_ALTAR;
                    break;
                case "Mystic":
                    bonus = IslandFurnitureType.MYSTIC_ORB;
                    break;
            }
            if (bonus != null && !owned.contains(bonus)) {
                // Directly add without recursion
                owned.add(bonus);
                int setBonusXp = 25;
                plugin.getIslandManager().addIslandXp(player, setBonusXp);
                player.sendMessage("§6§l★ Set Bonus Unlocked! ★ §e" + bonus.getDisplayName() + " §a+" + setBonusXp + " Island XP");
                UUID playerUuid = player.getUniqueId();
                plugin.getDatabaseManager().savePlayerIslandFurniture(playerUuid, getFurnitureIdSet(playerUuid));
            }
        }
    }

    /**
     * Recomputes which themed furniture sets are fully represented in the *placed* furniture on this island.
     * Called after place, remove, load, and respawn so activeIslandSets is always fresh.
     */
    private void updateActiveSetBonuses(String islandKey) {
        List<PlacedFurniture> placed = getPlacedFurnitureForIsland(islandKey);
        Map<String, Set<String>> bySet = new HashMap<>();
        for (PlacedFurniture p : placed) {
            try {
                IslandFurnitureType t = IslandFurnitureType.valueOf(p.furnitureId());
                String s = t.getFurnitureSet();
                if (s != null && !"Basic".equals(s)) {
                    bySet.computeIfAbsent(s, k -> ConcurrentHashMap.newKeySet()).add(t.name());
                }
            } catch (Exception ignored) {}
        }

        Set<String> complete = ConcurrentHashMap.newKeySet();
        for (Map.Entry<String, Set<String>> entry : bySet.entrySet()) {
            String set = entry.getKey();
            // Require the core pieces for the set (new pieces like CELESTIAL_LAMP contribute; thrones/altars are bonus)
            int required = switch (set) {
                case "Celestial" -> 3; // orb, bench, lamp, throne (any 3+ of available)
                case "Slayer" -> 3;   // trophy, rack, altar (banner contributes)
                case "Mystic" -> 2;   // fountain + orb (altar table contributes)
                default -> 2;
            };
            if (entry.getValue().size() >= required) {
                complete.add(set);
            }
        }
        activeIslandSets.put(islandKey, complete);
    }

    /**
     * Awards a one-time "placed set complete" bonus when a placement causes a themed set to become fully built on the *island*.
     * Separate from the ownership unlock bonus (the throne etc.).
     */
    private void checkAndAwardPlacedSetBonus(Island island, IslandFurnitureType justPlaced, String islandKey) {
        if (island == null) return;
        Set<String> active = activeIslandSets.getOrDefault(islandKey, Collections.emptySet());
        String set = justPlaced.getFurnitureSet();
        if (set == null || "Basic".equals(set) || active.contains(set)) return;

        // Recompute to see if this placement tipped it over
        updateActiveSetBonuses(islandKey);
        Set<String> nowActive = activeIslandSets.getOrDefault(islandKey, Collections.emptySet());
        if (nowActive.contains(set)) {
            int bonusXp = 40;
            island.addXp(bonusXp);
            // Notify online members (best effort, Folia safe)
            for (Player p : island.getOnlineMembers()) {
                p.sendMessage("§6§l★ Island Housing Set Complete! ★ §e" + set + " set is now proudly displayed on your island! §a+" + bonusXp + " Island XP");
            }
            // One-time visual flair at a central location (or the just placed loc)
            Location center = island.getCenter(null);
            if (center != null) {
                threadSafety.runAtLocation(center, () -> {
                    if (center.getWorld() != null) {
                        center.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, center.clone().add(0, 2, 0), 30, 1.5, 1, 1.5, 0.05);
                        center.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, center.clone().add(0, 1, 0), 20, 0.8, 0.8, 0.8, 0.02);
                    }
                });
            }
        }
    }

    public boolean hasFurniture(UUID uuid, IslandFurnitureType furniture) {
        if (furniture == null || furniture.isNone()) return true;
        return getOwnedFurniture(uuid).contains(furniture);
    }

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerIslandFurniture(uuid);
        Set<IslandFurnitureType> furniture = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                IslandFurnitureType f = IslandFurnitureType.valueOf(id);
                if (!f.isNone()) furniture.add(f);
            } catch (Exception ignored) {}
        }
        ownedFurniture.put(uuid, furniture);
    }

    public void savePlayer(UUID uuid) {
        Set<String> ids = getFurnitureIdSet(uuid);
        plugin.getDatabaseManager().savePlayerIslandFurniture(uuid, ids);
    }

    private Set<String> getFurnitureIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (IslandFurnitureType f : getOwnedFurniture(uuid)) ids.add(f.name());
        return ids;
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        ownedFurniture.remove(player.getUniqueId());
    }

    // ==================== PLACEMENT + PREVIEW (Full Folia-safe + persisted) ====================

    public record PlacedFurniture(String furnitureId, double x, double y, double z, double yaw, String extraData) {}

    /**
     * Spawns the actual visual entity for furniture using modern ItemDisplay (preferred) with fallback.
     * Fully Folia-safe via ThreadSafety.
     */
    private void spawnFurnitureEntity(String islandKey, PlacedFurniture placed, IslandFurnitureType type, org.bukkit.World world) {
        if (world == null || type == null || type.isNone()) return;

        Location loc = new Location(world, placed.x(), placed.y(), placed.z(), (float) placed.yaw(), 0f);

        threadSafety.runAtLocation(loc, () -> {
            try {
                // Prefer ItemDisplay for clean furniture (1.19.4+)
                ItemDisplay display = (ItemDisplay) world.spawnEntity(loc, EntityType.ITEM_DISPLAY);

                ItemStack item = new ItemStack(org.bukkit.Material.STONE); // base material; CMD provides the look
                ItemMeta meta = item.getItemMeta();
                if (meta != null && type.getCustomModelData() > 0) {
                    meta.setCustomModelData(type.getCustomModelData());
                    item.setItemMeta(meta);
                }

                display.setItemStack(item);
                display.setBillboard(ItemDisplay.Billboard.FIXED);
                display.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(1f, 1f, 1f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));

                // Store metadata for later removal/identification
                display.setCustomNameVisible(false);
                display.setCustomName("§6Furniture:" + type.name());

                // Active set bonus visual: if this piece is part of a fully placed themed set on the island, give it extra pride (glow + themed particles)
                Set<String> activeSets = activeIslandSets.getOrDefault(islandKey, Collections.emptySet());
                if (activeSets.contains(type.getFurnitureSet())) {
                    display.setGlowing(true);
                    // Extra themed particles on spawn for active sets (Folia already on region thread here)
                    if (loc.getWorld() != null) {
                        org.bukkit.Particle extra = switch (type.getFurnitureSet()) {
                            case "Celestial" -> org.bukkit.Particle.END_ROD;
                            case "Slayer" -> org.bukkit.Particle.FLAME;
                            case "Mystic" -> org.bukkit.Particle.PORTAL;
                            default -> org.bukkit.Particle.HAPPY_VILLAGER;
                        };
                        loc.getWorld().spawnParticle(extra, loc.clone().add(0, 1.1, 0), 6, 0.25, 0.3, 0.25, 0.01);
                    }
                }

                activeFurnitureEntities.computeIfAbsent(islandKey, k -> new ArrayList<>()).add(display);

            } catch (Exception e) {
                // Fallback to ArmorStand if ItemDisplay fails (older compatibility)
                try {
                    org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
                    stand.setVisible(false);
                    stand.setGravity(false);
                    stand.setBasePlate(false);
                    stand.setSmall(true);

                    ItemStack helmet = new ItemStack(org.bukkit.Material.STONE);
                    ItemMeta hMeta = helmet.getItemMeta();
                    if (hMeta != null && type.getCustomModelData() > 0) {
                        hMeta.setCustomModelData(type.getCustomModelData());
                        helmet.setItemMeta(hMeta);
                    }
                    stand.getEquipment().setHelmet(helmet);

                    activeFurnitureEntities.computeIfAbsent(islandKey, k -> new ArrayList<>()).add(stand);
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Main Folia-safe placement method with real visual spawning.
     */
    public boolean placeFurniture(Player player, Island island, IslandFurnitureType type, Location location, float yaw) {
        if (island == null || type == null || type.isNone()) return false;
        if (!hasFurniture(player.getUniqueId(), type)) {
            player.sendMessage("§cYou have not unlocked this furniture piece.");
            return false;
        }

        // Anti-cheat: housing spam / lag induction guard (Play-to-Win visual only but rate limited)
        if (plugin.getAntiCheatManager() != null) {
            if (plugin.getAntiCheatManager().isFlaggedForHousingSpam(player)) {
                player.sendMessage("§cAnti-cheat: Furniture placement rate limited temporarily.");
                return false;
            }
            plugin.getAntiCheatManager().recordHousingPlace(player);
        }

        String islandKey = island.getId();
        PlacedFurniture placed = new PlacedFurniture(type.name(), location.getX(), location.getY(), location.getZ(), yaw, player.getUniqueId().toString());

        placedByIsland.computeIfAbsent(islandKey, k -> new ArrayList<>()).add(placed);

        // Persist to DB
        plugin.getDatabaseManager().savePlacedFurniture(islandKey, type.name(),
                location.getX(), location.getY(), location.getZ(), yaw, placed.extraData());

        // Spawn the visual entity immediately (Folia-safe)
        spawnFurnitureEntity(islandKey, placed, type, location.getWorld());

        player.sendMessage("§aPlaced " + type.getRarity().getColorCode() + type.getDisplayName());

        // Nice placement particles
        threadSafety.runAtLocation(location, () -> {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, location.clone().add(0, 0.8, 0), 12, 0.4, 0.4, 0.4, 0.02);
            }
        });

        // Update active set bonuses (for placed-set pride effects) and award if this placement completed a themed set on the island
        updateActiveSetBonuses(islandKey);
        checkAndAwardPlacedSetBonus(island, type, islandKey);

        return true;
    }

    /**
     * Removes a specific piece of furniture (by record).
     * Cleans only matching entity (by name prefix), removes from mem list, deletes from DB.
     * Folia-safe particle on remove.
     */
    public boolean removeFurniture(String islandKey, PlacedFurniture placed, org.bukkit.World world) {
        List<PlacedFurniture> list = placedByIsland.get(islandKey);
        if (list != null) list.remove(placed);

        // Remove only matching entities for this furniture (do not nuke island list)
        List<Entity> entities = activeFurnitureEntities.get(islandKey);
        if (entities != null) {
            String targetName = "§6Furniture:" + placed.furnitureId();
            for (Entity e : new ArrayList<>(entities)) {
                if (e.isValid() && (e.getCustomName() != null && e.getCustomName().startsWith(targetName))) {
                    e.remove();
                    entities.remove(e);
                }
            }
            if (entities.isEmpty()) {
                activeFurnitureEntities.remove(islandKey);
            }
        }

        // DB delete for persistence
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().deletePlacedFurniture(islandKey, placed.furnitureId(), placed.x(), placed.y(), placed.z());
        }

        if (world != null) {
            Location loc = new Location(world, placed.x(), placed.y(), placed.z());
            threadSafety.runAtLocation(loc, () -> {
                if (world != null) {
                    world.spawnParticle(org.bukkit.Particle.SMOKE, loc.clone().add(0, 0.5, 0), 6, 0.3, 0.3, 0.3, 0.01);
                }
            });
        }

        updateActiveSetBonuses(islandKey);
        return true;
    }

    public List<PlacedFurniture> getPlacedFurnitureForIsland(String islandKey) {
        return placedByIsland.getOrDefault(islandKey, Collections.emptyList());
    }

    /**
     * Loads placed furniture data from DB into memory (populates placedByIsland) and spawns visuals.
     * Full persistence: now survives restarts. Called on island load/respawn.
     */
    public void loadPlacedForIsland(String islandKey, org.bukkit.World world) {
        List<PlacedFurniture> fromDb = loadPlacedFurnitureFromDB(islandKey);
        placedByIsland.put(islandKey, new ArrayList<>(fromDb));
        for (PlacedFurniture p : fromDb) {
            try {
                IslandFurnitureType type = IslandFurnitureType.valueOf(p.furnitureId());
                spawnFurnitureEntity(islandKey, p, type, world);
            } catch (Exception ignored) {}
        }
        updateActiveSetBonuses(islandKey);
    }

    private List<PlacedFurniture> loadPlacedFurnitureFromDB(String islandKey) {
        List<PlacedFurniture> list = new ArrayList<>();
        if (plugin.getDatabaseManager() == null) return list;
        for (DatabaseManager.PlacedFurnitureData d : plugin.getDatabaseManager().loadPlacedFurniture(islandKey)) {
            list.add(new PlacedFurniture(d.furnitureId(), d.x(), d.y(), d.z(), d.yaw(), d.data() != null ? d.data() : ""));
        }
        return list;
    }

    public void respawnAllFurnitureForIsland(String islandKey, org.bukkit.World world) {
        // Clear old entities first
        List<Entity> old = activeFurnitureEntities.remove(islandKey);
        if (old != null) {
            for (Entity e : old) {
                if (e.isValid()) e.remove();
            }
        }
        loadPlacedForIsland(islandKey, world);
        // updateActiveSetBonuses is called inside loadPlacedForIsland
    }

    /**
     * Spawns a temporary preview hologram (ItemDisplay) of the furniture ~1.8 blocks in front of player.
     * Folia-safe via ThreadSafety (runAtLocation + runForEntityLater). Auto-removes after 15s.
     * Includes scale transform, glow, name tag, and particles. Purely cosmetic UX aid (no persistence).
     */
    public void previewFurniture(Player player, IslandFurnitureType type) {
        if (player == null || type == null || type.isNone() || !hasFurniture(player.getUniqueId(), type)) return;

        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().clone().setY(0).normalize();
        Location previewLoc = eye.clone().add(dir.multiply(1.8)).add(0, -0.25, 0);

        threadSafety.runAtLocation(previewLoc, () -> {
            try {
                ItemDisplay display = (ItemDisplay) player.getWorld().spawnEntity(previewLoc, EntityType.ITEM_DISPLAY);

                ItemStack item = new ItemStack(org.bukkit.Material.STONE);
                ItemMeta meta = item.getItemMeta();
                if (meta != null && type.getCustomModelData() > 0) {
                    meta.setCustomModelData(type.getCustomModelData());
                    item.setItemMeta(meta);
                }
                display.setItemStack(item);
                display.setBillboard(ItemDisplay.Billboard.FIXED);
                display.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.65f, 0.65f, 0.65f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                display.setCustomName("§ePreview §f" + type.getDisplayName() + " §7(15s)");
                display.setCustomNameVisible(true);
                display.setGlowing(true);

                if (previewLoc.getWorld() != null) {
                    previewLoc.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, previewLoc.clone().add(0, 0.9, 0), 10, 0.25, 0.35, 0.25, 0.01);
                }

                // Folia-safe delayed self-clean via entity scheduler
                threadSafety.runForEntityLater(display, () -> {
                    if (display != null && display.isValid()) {
                        if (display.getWorld() != null) {
                            display.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, display.getLocation().add(0, 0.6, 0), 5, 0.15, 0.15, 0.15, 0.01);
                        }
                        display.remove();
                    }
                }, 15 * 20L);

            } catch (Exception e) {
                if (player != null && player.isOnline()) {
                    player.sendMessage("§cPreview temporarily unavailable.");
                }
            }
        });

        player.sendMessage("§e§lPreview: §f" + type.getDisplayName() + " §7spawned in front of you (15s).");
    }

    /**
     * Called on player enter (via DimensionIslandListener) for active set bonus visuals.
     * Purely cosmetic: if the island has one or more complete placed themed sets, do a welcoming pride burst
     * around the placed pieces of those sets. Folia-safe.
     */
    public void onPlayerEnterIsland(Player player, Island island) {
        if (player == null || island == null) return;
        String islandKey = island.getId();
        Set<String> active = getActiveSetBonuses(islandKey);
        if (active.isEmpty()) return;

        List<PlacedFurniture> placed = getPlacedFurnitureForIsland(islandKey);
        if (placed.isEmpty()) return;

        Location ref = (island.getCenter(null) != null) ? island.getCenter(null) : player.getLocation();
        threadSafety.runAtLocation(ref, () -> {
            org.bukkit.World w = ref.getWorld();
            if (w == null) return;

            // Central pride burst for the sets (once)
            for (String set : active) {
                org.bukkit.Particle centerPt = switch (set) {
                    case "Celestial" -> org.bukkit.Particle.END_ROD;
                    case "Slayer" -> org.bukkit.Particle.FLAME;
                    case "Mystic" -> org.bukkit.Particle.PORTAL;
                    default -> org.bukkit.Particle.HAPPY_VILLAGER;
                };
                w.spawnParticle(centerPt, ref.clone().add(0, 1.5, 0), 15, 1.2, 0.8, 1.2, 0.03);
                w.spawnParticle(org.bukkit.Particle.FIREWORK, ref.clone().add(0, 2.2, 0), 4, 0.6, 0.4, 0.6, 0.02);
            }

            for (PlacedFurniture p : placed) {
                try {
                    IslandFurnitureType t = IslandFurnitureType.valueOf(p.furnitureId());
                    if (active.contains(t.getFurnitureSet())) {
                        Location loc = new Location(w, p.x(), p.y() + 1.0, p.z());
                        // Themed burst
                        org.bukkit.Particle pt = switch (t.getFurnitureSet()) {
                            case "Celestial" -> org.bukkit.Particle.END_ROD;
                            case "Slayer" -> org.bukkit.Particle.FLAME;
                            case "Mystic" -> org.bukkit.Particle.PORTAL;
                            default -> org.bukkit.Particle.HAPPY_VILLAGER;
                        };
                        w.spawnParticle(pt, loc, 10, 0.4, 0.7, 0.4, 0.02);
                        if (Math.random() < 0.3) {
                            w.spawnParticle(org.bukkit.Particle.FIREWORK, loc.clone().add(0, 0.6, 0), 2, 0.2, 0.2, 0.2, 0.01);
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Folia world unload hook for active furniture entities.
     */
    public void onWorldUnload(org.bukkit.World world) {
        if (world == null) return;
        activeFurnitureEntities.values().forEach(list -> list.removeIf(e -> e != null && e.isValid() && e.getWorld() != null && e.getWorld().equals(world)));
        plugin.getLogger().info("[IslandFurnitureManager] Unload cleanup for " + world.getName());
        // Also trim maps for compression on unload (large server safety)
        cleanupCaches();
    }

    /**
     * Bounded eviction for owned/placed/active maps (LRU-ish simple size cap remove).
     * Addresses "compression of ConcurrentHashMaps (review all for eviction)", "per-manager bounded caches" for large scale.
     * Keeps memory compressed when 1000s of players unlock furniture or many islands have placed decor.
     */
    private void cleanupCaches() {
        if (ownedFurniture.size() > MAX_OWNED_PER_MANAGER) {
            java.util.Iterator<UUID> it = ownedFurniture.keySet().iterator();
            int toRemove = ownedFurniture.size() - (MAX_OWNED_PER_MANAGER - 500);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
        if (placedByIsland.size() > MAX_ISLANDS_PLACED) {
            java.util.Iterator<String> it = placedByIsland.keySet().iterator();
            int toRemove = placedByIsland.size() - (MAX_ISLANDS_PLACED - 200);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
            // also trim active/entities to match
            java.util.Iterator<String> ait = activeFurnitureEntities.keySet().iterator();
            int aRemove = activeFurnitureEntities.size() - (MAX_ISLANDS_PLACED - 200);
            while (ait.hasNext() && aRemove > 0) { ait.next(); ait.remove(); aRemove--; }
        }
        if (activeIslandSets.size() > MAX_ISLANDS_PLACED) {
            java.util.Iterator<String> it = activeIslandSets.keySet().iterator();
            int toRemove = activeIslandSets.size() - (MAX_ISLANDS_PLACED - 200);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
    }
}
