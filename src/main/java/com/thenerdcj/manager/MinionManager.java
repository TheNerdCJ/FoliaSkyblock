package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandUpgrade;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MinionManager - Implementation for minion system (Hypixel-style).
 * 
 * Minions are automated workers that farm resources on islands.
 * Placement limited by MINION_SLOTS island upgrade.
 * Spawns visual ArmorStand minions that periodically generate and drop resources.
 * Counts persisted to DB (island_minions table).
 */
public class MinionManager {

    private final FoliaSkyblock plugin;
    // In-memory tracking of placed minions per island (islandId -> count)
    private final Map<String, Integer> placedMinions = new HashMap<>();
    // Track spawned ArmorStand entities for cleanup on remove
    private final Map<String, List<ArmorStand>> activeMinions = new HashMap<>();
    // Fuel level per island (aggregate for simplicity, can be expanded to per-minion)
    private final Map<String, Integer> islandFuels = new HashMap<>();

    public MinionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Table creation now centralized in DatabaseManager.createTables() for cleaner architecture
    }

    /**
     * Get the max number of minions allowed for an island based on purchased upgrade.
     */
    public int getMaxMinionSlots(String islandId) {
        if (plugin.getIslandUpgradeManager() == null) return 5; // default base
        int upgradeLevel = plugin.getIslandUpgradeManager().getUpgradeLevel(islandId, IslandUpgrade.MINION_SLOTS);
        return 5 + upgradeLevel; // base 5 + purchased slots
    }

    public int getMaxMinionSlots(Island island) {
        if (island == null) return 5;
        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
        return getMaxMinionSlots(islandId);
    }

    /**
     * Check if player can place another minion on their island.
     */
    public boolean canPlaceMinion(Player player, Island island) {
        if (island == null || !island.isMember(player.getUniqueId())) return false;
        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
        int current = placedMinions.getOrDefault(islandId, 0);
        return current < getMaxMinionSlots(islandId);
    }

    /**
     * Place a minion: increments count, persists to DB, spawns ArmorStand entity,
     * schedules periodic resource generation task.
     */
    public boolean placeMinion(Player player, Island island, String minionType) {
        if (!canPlaceMinion(player, island)) {
            player.sendMessage("§cYou have reached your minion slot limit! Purchase more §eMinion Slots§c upgrades.");
            return false;
        }
        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
        int newCount = placedMinions.getOrDefault(islandId, 0) + 1;
        placedMinions.put(islandId, newCount);

        // Persist to DB
        // Initialize starting fuel on first minion or add a bit
        if (!islandFuels.containsKey(islandId)) {
            islandFuels.put(islandId, 1000);
        }
        saveMinionCount(islandId, newCount);

        // Spawn visual minion entity + start generation task
        spawnMinionEntity(player, island, minionType, islandId, newCount);

        player.sendMessage("§aPlaced §e" + minionType + "§a minion! (" + newCount + "/" + getMaxMinionSlots(islandId) + " slots used). Starting fuel: 1000 units.");
        return true;
    }

    private void spawnMinionEntity(Player player, Island island, String minionType, String islandId, int minionNumber) {
        World world = (player != null) ? player.getWorld() : Bukkit.getWorlds().get(0); // fallback to first world if no player (e.g. on load)
        Location center = island.getCenter(world);
        if (center == null) {
            if (player != null) {
                center = player.getLocation().clone();
            } else {
                center = new Location(world, 0, 100, 0); // default spawn area
            }
        }

        // Spread minions around island center in a small grid
        int spread = 3;
        int xOffset = ((minionNumber - 1) % 4) * spread - (spread);
        int zOffset = ((minionNumber - 1) / 4) * spread;
        Location spawnLoc = center.clone().add(xOffset, 1.2, zOffset);

        ArmorStand stand = (ArmorStand) world.spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setCustomNameVisible(true);
        stand.setCustomName("§6" + minionType + " Minion §7#" + minionNumber);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setSmall(true);

        // Visual helmet based on minion type
        Material helmetMat = getHelmetMaterial(minionType);
        if (helmetMat != null) {
            stand.getEquipment().setHelmet(new ItemStack(helmetMat));
        }

        // Track for possible removal
        activeMinions.computeIfAbsent(islandId, k -> new ArrayList<>()).add(stand);

        // Start resource generation scheduler (every 10s after initial 5s delay)
        scheduleResourceTask(stand, player, minionType, islandId);
    }

    private Material getHelmetMaterial(String minionType) {
        if (minionType == null) return Material.HAY_BLOCK;
        return switch (minionType.toUpperCase()) {
            case "WHEAT", "CROP", "FARM" -> Material.WHEAT;
            case "COBBLE", "COBBLESTONE", "STONE" -> Material.COBBLESTONE;
            case "IRON" -> Material.IRON_INGOT;
            case "GOLD" -> Material.GOLD_INGOT;
            case "DIAMOND" -> Material.DIAMOND;
            case "EMERALD" -> Material.EMERALD;
            default -> Material.HAY_BLOCK;
        };
    }

    private void scheduleResourceTask(ArmorStand minion, Player owner, String type, String islandId) {
        new BukkitRunnable() {
            private int cycles = 0;

            @Override
            public void run() {
                if (minion == null || !minion.isValid() || minion.isDead()) {
                    cancel();
                    return;
                }
                cycles++;

                // Fuel mechanics: consume fuel to operate. If no fuel, pause production (play to win: fuel earned via gameplay/trading, not pay-to-win)
                int currentFuel = islandFuels.getOrDefault(islandId, 1000);
                if (currentFuel > 0) {
                    islandFuels.put(islandId, currentFuel - 1);
                    if ((cycles % 10) == 0) saveMinionCount(islandId, placedMinions.getOrDefault(islandId, 0)); // periodic persist
                } else {
                    // No fuel: pause production (fair play-to-win design)
                    if (cycles % 10 == 0 && owner != null && owner.isOnline()) {
                        owner.sendActionBar("§cMinion out of fuel! Refuel using crafted/traded resources to continue.");
                    }
                    return; // skip production
                }

                Material resource = getResourceMaterial(type);
                int amount = 1 + (int) (Math.random() * 2);
                ItemStack drop = new ItemStack(resource, amount);

                // Drop resource near the minion (visible "work" effect)
                minion.getWorld().dropItemNaturally(minion.getLocation().add(0, 0.8, 0), drop);

                // Occasional feedback to owner
                if (cycles % 5 == 0 && owner != null && owner.isOnline()) {
                    String niceName = resource.name().toLowerCase().replace('_', ' ');
                    owner.sendActionBar("§7" + type + " Minion produced §a+" + amount + " " + niceName + " (fuel: " + islandFuels.getOrDefault(islandId, 0) + ")");
                }
            }
        }.runTaskTimer(plugin, 20L * 5, 20L * 10); // 5s delay, repeat every 10s
    }

    private Material getResourceMaterial(String minionType) {
        if (minionType == null) return Material.DIRT;
        return switch (minionType.toUpperCase()) {
            case "WHEAT", "CROP", "FARM" -> Material.WHEAT;
            case "COBBLE", "COBBLESTONE", "STONE" -> Material.COBBLESTONE;
            case "IRON" -> Material.IRON_INGOT;
            case "GOLD" -> Material.GOLD_INGOT;
            case "DIAMOND" -> Material.DIAMOND;
            case "EMERALD" -> Material.EMERALD;
            default -> Material.DIRT;
        };
    }

    private void saveMinionCount(String islandId, int count) {
        int fuel = islandFuels.getOrDefault(islandId, 1000);
        // Use centralized DB method - no raw SQL here
        plugin.getDatabaseManager().saveMinionData(islandId, count, fuel).thenAccept(success -> {
            if (!Boolean.TRUE.equals(success)) {
                plugin.getLogger().warning("[MinionManager] Failed to persist minion data for island " + islandId);
            }
        });
    }

    /**
     * Remove a minion (decrements count, persists, removes one visual entity if tracked).
     */
    public void removeMinion(String islandId) {
        int current = placedMinions.getOrDefault(islandId, 0);
        if (current > 0) {
            int newCount = current - 1;
            placedMinions.put(islandId, newCount);
            saveMinionCount(islandId, newCount);

            // Remove last tracked entity
            List<ArmorStand> list = activeMinions.get(islandId);
            if (list != null && !list.isEmpty()) {
                ArmorStand toRemove = list.remove(list.size() - 1);
                if (toRemove.isValid()) {
                    toRemove.remove();
                }
            }
        }
    }

    public int getPlacedMinionCount(String islandId) {
        return placedMinions.getOrDefault(islandId, 0);
    }

    /**
     * Refuel an island's minions (fuel mechanics for play-to-win: fuel obtained via gameplay, trading, or island resources).
     * In full version, support per-minion refuel via GUI/right-click with fuel items (coal, lava bucket, etc.).
     */
    public void refuelIsland(String islandId, int amount) {
        int current = islandFuels.getOrDefault(islandId, 1000);
        int maxFuel = 10000; // cap
        int newFuel = Math.min(maxFuel, current + amount);
        islandFuels.put(islandId, newFuel);
        saveMinionCount(islandId, placedMinions.getOrDefault(islandId, 0));
        // Could notify players on island
    }

    public int getIslandFuel(String islandId) {
        return islandFuels.getOrDefault(islandId, 1000);
    }

    /**
     * Load minion count and fuel from DB for the island (async), and spawn entities for full persistence on load/restart.
     * This ensures minions (entities + tasks) persist across server restarts, as intended for island progression system.
     * Delegates to DatabaseManager.loadMinionData() for clean separation - no raw SQL or duplicate query logic in MinionManager.
     */
    public CompletableFuture<Void> loadMinionsForIsland(Island island) {
        if (island == null) return CompletableFuture.completedFuture(null);
        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
        return plugin.getDatabaseManager().loadMinionData(islandId).thenApply(data -> {
            int count = data.getOrDefault("count", 0);
            int fuel = data.getOrDefault("fuel", 1000);
            placedMinions.put(islandId, count);
            islandFuels.put(islandId, fuel);
            // Spawn entities to restore physical minions and tasks on load (entity persistence)
            if (count > 0) {
                spawnMinionsOnLoad(island, count, "WHEAT"); // default type for loaded; full type persistence can use extended table in future
            }
            return null;
        }).exceptionally(ex -> {
            // Should rarely happen as loadMinionData handles SQL errors gracefully with defaults
            plugin.getLogger().warning("[MinionManager] Unexpected error loading minions for " + islandId + ": " + ex.getMessage());
            placedMinions.putIfAbsent(islandId, 0);
            islandFuels.putIfAbsent(islandId, 1000);
            return null;
        });
    }

    /**
     * Spawn multiple minion entities on load for persistence. Uses calculated positions based on count.
     */
    private void spawnMinionsOnLoad(Island island, int count, String defaultType) {
        for (int i = 1; i <= count; i++) {
            // Use a null player fallback since no specific player; spawn at island center + offset
            spawnMinionEntity(null, island, defaultType, 
                island.getGridPosition().getX() + "," + island.getGridPosition().getZ(), i);
        }
    }
}
