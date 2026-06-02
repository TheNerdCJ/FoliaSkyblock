package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * Manager for Island Structure Decorations (larger cosmetic clusters).
 * Extends housing theme.
 */
public class IslandStructureManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<IslandStructureCosmetic>> ownedStructures = new ConcurrentHashMap<>();
    private final Map<String, List<PlacedStructure>> placedByIsland = new ConcurrentHashMap<>();
    private final Map<String, List<Entity>> activeStructureEntities = new ConcurrentHashMap<>();

    // Bounded cache constants for large-scale server compression (CHM review/eviction per IMPROVEMENTS)
    private static final int MAX_OWNED_STRUCT = 5000;
    private static final int MAX_ISLANDS_STRUCT = 2000;

    private static final int[] COLLECTION_MILESTONES = {2, 4, 6};

    public IslandStructureManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
        // Periodic bounded CHM eviction for large scale compression/optim
        threadSafety.runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
    }

    public Set<IslandStructureCosmetic> getOwnedStructures(UUID uuid) {
        return ownedStructures.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public int getCollectionCount(UUID uuid) {
        return getOwnedStructures(uuid).size();
    }

    public void unlockStructure(UUID uuid, IslandStructureCosmetic structure) {
        if (structure == null || structure.isNone()) return;
        Set<IslandStructureCosmetic> owned = getOwnedStructures(uuid);
        boolean wasNew = owned.add(structure);
        if (wasNew) {
            int xp = Math.max(20, structure.getRarity().getXpReward() / 2);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                plugin.getIslandManager().addIslandXp(player, xp);
                player.sendMessage("§a§lIsland Structure Collection §7» Unlocked " + structure.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = owned.size();
                for (int m : COLLECTION_MILESTONES) {
                    if (count == m) {
                        player.sendMessage("§6§l★ Island Architect Milestone! ★ §e" + count + " unique structures unlocked!");
                        break;
                    }
                }
            }
            plugin.getDatabaseManager().savePlayerIslandStructures(uuid, getStructureIdSet(uuid));
        }
    }

    public boolean hasStructure(UUID uuid, IslandStructureCosmetic structure) {
        if (structure == null || structure.isNone()) return true;
        return getOwnedStructures(uuid).contains(structure);
    }

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerIslandStructures(uuid);
        Set<IslandStructureCosmetic> structures = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                IslandStructureCosmetic s = IslandStructureCosmetic.valueOf(id);
                if (!s.isNone()) structures.add(s);
            } catch (Exception ignored) {}
        }
        ownedStructures.put(uuid, structures);
    }

    public void savePlayer(UUID uuid) {
        Set<String> ids = getStructureIdSet(uuid);
        plugin.getDatabaseManager().savePlayerIslandStructures(uuid, ids);
    }

    private Set<String> getStructureIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (IslandStructureCosmetic s : getOwnedStructures(uuid)) ids.add(s.name());
        return ids;
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        ownedStructures.remove(player.getUniqueId());
    }

    public record PlacedStructure(String structureId, double x, double y, double z, double yaw, String extraData) {}

    // Spawn a 'structure' as cluster of 2-3 ItemDisplays with offsets and CMD
    private void spawnStructureEntity(String islandKey, PlacedStructure placed, IslandStructureCosmetic type, org.bukkit.World world) {
        if (world == null || type == null || type.isNone()) return;

        Location baseLoc = new Location(world, placed.x(), placed.y(), placed.z(), (float) placed.yaw(), 0f);

        threadSafety.runAtLocation(baseLoc, () -> {
            try {
                // Main piece
                ItemDisplay main = (ItemDisplay) world.spawnEntity(baseLoc, EntityType.ITEM_DISPLAY);
                ItemStack item = new ItemStack(Material.STONE);
                ItemMeta meta = item.getItemMeta();
                if (meta != null && type.getCustomModelData() > 0) {
                    meta.setCustomModelData(type.getCustomModelData());
                    item.setItemMeta(meta);
                }
                main.setItemStack(item);
                main.setBillboard(ItemDisplay.Billboard.FIXED);
                main.setCustomName("§6Structure:" + type.name());
                activeStructureEntities.computeIfAbsent(islandKey, k -> new ArrayList<>()).add(main);

                // Add 1-2 offset pieces for 'cluster' look (use same or +1 CMD if available)
                Location offset1 = baseLoc.clone().add(0.5, 0.5, 0);
                ItemDisplay part1 = (ItemDisplay) world.spawnEntity(offset1, EntityType.ITEM_DISPLAY);
                part1.setItemStack(item); // reuse or adjust
                part1.setBillboard(ItemDisplay.Billboard.FIXED);
                part1.setCustomName("§6StructurePart:" + type.name());
                activeStructureEntities.computeIfAbsent(islandKey, k -> new ArrayList<>()).add(part1);

                Location offset2 = baseLoc.clone().add(-0.5, 0.2, 0.5);
                ItemDisplay part2 = (ItemDisplay) world.spawnEntity(offset2, EntityType.ITEM_DISPLAY);
                part2.setItemStack(item);
                part2.setBillboard(ItemDisplay.Billboard.FIXED);
                part2.setCustomName("§6StructurePart:" + type.name());
                activeStructureEntities.computeIfAbsent(islandKey, k -> new ArrayList<>()).add(part2);

            } catch (Exception e) {
                // fallback simple
                try {
                    org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) world.spawnEntity(baseLoc, EntityType.ARMOR_STAND);
                    stand.setVisible(false);
                    stand.setGravity(false);
                    ItemStack helmet = new ItemStack(Material.STONE);
                    ItemMeta hMeta = helmet.getItemMeta();
                    if (hMeta != null && type.getCustomModelData() > 0) {
                        hMeta.setCustomModelData(type.getCustomModelData());
                        helmet.setItemMeta(hMeta);
                    }
                    stand.getEquipment().setHelmet(helmet);
                    stand.setCustomName("§6Structure:" + type.name());
                    activeStructureEntities.computeIfAbsent(islandKey, k -> new ArrayList<>()).add(stand);
                } catch (Exception ignored) {}
            }
        });
    }

    public boolean placeStructure(Player player, Island island, IslandStructureCosmetic type, Location location, float yaw) {
        if (island == null || type == null || type.isNone()) return false;
        if (!hasStructure(player.getUniqueId(), type)) {
            player.sendMessage("§cYou have not unlocked this structure.");
            return false;
        }

        // Anti-cheat housing spam guard (structures count toward placement rate)
        if (plugin.getAntiCheatManager() != null) {
            if (plugin.getAntiCheatManager().isFlaggedForHousingSpam(player)) {
                player.sendMessage("§cAnti-cheat: Structure placement rate limited temporarily.");
                return false;
            }
            plugin.getAntiCheatManager().recordHousingPlace(player);
        }

        String islandKey = island.getId();
        PlacedStructure placed = new PlacedStructure(type.name(), location.getX(), location.getY(), location.getZ(), yaw, player.getUniqueId().toString());

        placedByIsland.computeIfAbsent(islandKey, k -> new ArrayList<>()).add(placed);

        plugin.getDatabaseManager().savePlacedStructure(islandKey, type.name(),
                location.getX(), location.getY(), location.getZ(), yaw, placed.extraData());

        spawnStructureEntity(islandKey, placed, type, location.getWorld());

        player.sendMessage("§aPlaced structure: " + type.getDisplayName());

        threadSafety.runAtLocation(location, () -> {
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, location.clone().add(0, 1, 0), 15, 0.6, 0.6, 0.6, 0.02);
            }
        });

        return true;
    }

    public List<PlacedStructure> getPlacedStructuresForIsland(String islandKey) {
        return placedByIsland.getOrDefault(islandKey, Collections.emptyList());
    }

    public void loadPlacedStructuresForIsland(String islandKey, org.bukkit.World world) {
        // Full DB-backed: populates placedByIsland from persisted rows then spawns (survives restarts)
        List<PlacedStructure> fromDb = loadPlacedStructuresFromDB(islandKey);
        placedByIsland.put(islandKey, new ArrayList<>(fromDb));
        for (PlacedStructure p : fromDb) {
            try {
                IslandStructureCosmetic type = IslandStructureCosmetic.valueOf(p.structureId());
                spawnStructureEntity(islandKey, p, type, world);
            } catch (Exception ignored) {}
        }
    }

    private List<PlacedStructure> loadPlacedStructuresFromDB(String islandKey) {
        List<PlacedStructure> list = new ArrayList<>();
        if (plugin.getDatabaseManager() == null) return list;
        for (DatabaseManager.PlacedStructureData d : plugin.getDatabaseManager().loadPlacedStructures(islandKey)) {
            list.add(new PlacedStructure(d.structureId(), d.x(), d.y(), d.z(), d.yaw(), d.data() != null ? d.data() : ""));
        }
        return list;
    }

    public void respawnAllStructuresForIsland(String islandKey, org.bukkit.World world) {
        List<Entity> old = activeStructureEntities.remove(islandKey);
        if (old != null) {
            for (Entity e : old) {
                if (e.isValid()) e.remove();
            }
        }
        loadPlacedStructuresForIsland(islandKey, world);
    }

    /**
     * Removes a specific structure (by record). Cleans only matching entities.
     */
    public boolean removeStructure(String islandKey, PlacedStructure placed, org.bukkit.World world) {
        List<PlacedStructure> list = placedByIsland.get(islandKey);
        if (list != null) list.remove(placed);

        // Remove only matching entities for this structure
        List<Entity> entities = activeStructureEntities.get(islandKey);
        if (entities != null) {
            String targetName = "§6Structure:" + placed.structureId();
            for (Entity e : new ArrayList<>(entities)) {
                if (e.isValid() && (e.getCustomName() != null && e.getCustomName().startsWith(targetName))) {
                    e.remove();
                    entities.remove(e);
                }
            }
            if (entities.isEmpty()) {
                activeStructureEntities.remove(islandKey);
            }
        }

        // DB delete for full persistence across restarts
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().deletePlacedStructure(islandKey, placed.structureId(), placed.x(), placed.y(), placed.z());
        }

        if (world != null) {
            Location loc = new Location(world, placed.x(), placed.y(), placed.z());
            threadSafety.runAtLocation(loc, () -> {
                if (world != null) {
                    world.spawnParticle(org.bukkit.Particle.SMOKE, loc.clone().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.01);
                }
            });
        }
        return true;
    }

    /**
     * Spawns a temporary preview hologram for a structure (main piece + label for UX).
     * Folia-safe, 15s auto cleanup, particles, scaled. Click in GUI with shift for this.
     */
    public void previewStructure(Player player, IslandStructureCosmetic type) {
        if (player == null || type == null || type.isNone() || !hasStructure(player.getUniqueId(), type)) return;

        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().clone().setY(0).normalize();
        Location previewLoc = eye.clone().add(dir.multiply(2.2)).add(0, -0.1, 0);

        threadSafety.runAtLocation(previewLoc, () -> {
            try {
                ItemDisplay display = (ItemDisplay) player.getWorld().spawnEntity(previewLoc, EntityType.ITEM_DISPLAY);

                ItemStack item = new ItemStack(Material.STONE);
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
                        new Vector3f(0.9f, 0.9f, 0.9f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                display.setCustomName("§ePreview §f" + type.getDisplayName() + " §7(15s)");
                display.setCustomNameVisible(true);
                display.setGlowing(true);

                if (previewLoc.getWorld() != null) {
                    previewLoc.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, previewLoc.clone().add(0, 1.2, 0), 12, 0.4, 0.5, 0.4, 0.02);
                }

                threadSafety.runForEntityLater(display, () -> {
                    if (display != null && display.isValid()) {
                        if (display.getWorld() != null) {
                            display.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, display.getLocation().add(0, 0.8, 0), 6, 0.2, 0.2, 0.2, 0.01);
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
     * Folia world unload hook for active structure entities.
     */
    public void onWorldUnload(org.bukkit.World world) {
        if (world == null) return;
        activeStructureEntities.values().forEach(list -> list.removeIf(e -> e != null && e.isValid() && e.getWorld() != null && e.getWorld().equals(world)));
        plugin.getLogger().info("[IslandStructureManager] Unload cleanup for " + world.getName());
        cleanupCaches();
    }

    /**
     * Bounded eviction for owned/placed/active structure maps (size cap eviction).
     * Part of large scale CHM compression review/bound all for 100s-1000+ islands/players.
     */
    private void cleanupCaches() {
        if (ownedStructures.size() > MAX_OWNED_STRUCT) {
            java.util.Iterator<UUID> it = ownedStructures.keySet().iterator();
            int toRemove = ownedStructures.size() - (MAX_OWNED_STRUCT - 500);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
        if (placedByIsland.size() > MAX_ISLANDS_STRUCT) {
            java.util.Iterator<String> it = placedByIsland.keySet().iterator();
            int toRemove = placedByIsland.size() - (MAX_ISLANDS_STRUCT - 200);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
            java.util.Iterator<String> ait = activeStructureEntities.keySet().iterator();
            int aRemove = activeStructureEntities.size() - (MAX_ISLANDS_STRUCT - 200);
            while (ait.hasNext() && aRemove > 0) { ait.next(); ait.remove(); aRemove--; }
        }
    }
}
