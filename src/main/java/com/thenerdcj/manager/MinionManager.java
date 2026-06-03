package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.cosmetic.MinionSkinManager;
import com.thenerdcj.cosmetic.MinionSkin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MinionManager - Implementation for minion system (Hypixel-style).
 */
public class MinionManager {

    private final FoliaSkyblock plugin;

    /**
     * Per-island minion counts by type.
     * Key = islandId (e.g. "x,z")
     * Inner map = MinionType -> count
     */
    private final Map<String, Map<MinionType, Integer>> placedMinionsByType = new HashMap<>();

    private final Map<String, List<ArmorStand>> activeMinions = new HashMap<>();
    private final Map<String, Integer> islandFuels = new HashMap<>();

    // Bounded for large scale CHM/memory compression (many islands with minions/fuel)
    private static final int MAX_ISLANDS_MINIONS = 2000;

    // Real fuel items: Material -> fuel units provided (config-driven)
    private static final Map<Material, Integer> FUEL_VALUES = new EnumMap<>(Material.class);
    static {
        FUEL_VALUES.put(Material.COAL, 50);
        FUEL_VALUES.put(Material.CHARCOAL, 40);
        FUEL_VALUES.put(Material.COAL_BLOCK, 450);
        FUEL_VALUES.put(Material.BLAZE_ROD, 120);
        FUEL_VALUES.put(Material.LAVA_BUCKET, 300);
        FUEL_VALUES.put(Material.WHEAT, 10);
        FUEL_VALUES.put(Material.ENDER_PEARL, 80);
        // Hypixel-aligned more fuels for expanded minions (task 4)
        FUEL_VALUES.put(Material.MAGMA_CREAM, 90);
        FUEL_VALUES.put(Material.NETHERRACK, 5);
        FUEL_VALUES.put(Material.GLOWSTONE_DUST, 25);
        FUEL_VALUES.put(Material.GRAVEL, 8);
        FUEL_VALUES.put(Material.NETHERITE_SCRAP, 500); // rare high fuel
    }

    public MinionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();

        // Periodic fuel save (polish - prevents loss on crash)
        plugin.getThreadSafety().runRepeatingOnMainThread(() -> {
            for (String islandId : islandFuels.keySet()) {
                saveIslandFuel(islandId);
            }
        }, 20L * 60 * 5, 20L * 60 * 5); // Every 5 minutes

        // Periodic bounded CHM eviction for large scale (minions/fuel per island)
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
    }

    private void loadConfig() {
        var config = plugin.getConfig();

        // Load custom fuel values (config-driven nice-to-have)
        if (config.isConfigurationSection("minions.fuel")) {
            FUEL_VALUES.clear();
            for (String key : config.getConfigurationSection("minions.fuel").getKeys(false)) {
                Material mat = Material.matchMaterial(key.toUpperCase());
                if (mat != null) {
                    FUEL_VALUES.put(mat, config.getInt("minions.fuel." + key, 50));
                }
            }
        }

        // Production multipliers could be loaded similarly into a map if needed
        plugin.getLogger().info("§a[Minions] Loaded " + FUEL_VALUES.size() + " fuel values from config.");
    }

    public int getMaxMinionSlots(String islandId) {
        if (plugin.getIslandUpgradeManager() == null) return 5;
        int upgradeLevel = plugin.getIslandUpgradeManager().getUpgradeLevel(islandId, com.thenerdcj.island.IslandUpgrade.MINION_SLOTS);
        return 5 + upgradeLevel;
    }

    public int getMaxMinionSlots(Island island) {
        if (island == null) return 5;
        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
        return getMaxMinionSlots(islandId);
    }

    public boolean canPlaceMinion(Player player, Island island) {
        if (island == null || !island.isMember(player.getUniqueId())) return false;
        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
        int current = getTotalPlacedMinions(islandId);
        return current < getMaxMinionSlots(islandId);
    }

    /** Returns total number of minions placed on an island (across all types) */
    public int getTotalPlacedMinions(String islandId) {
        Map<MinionType, Integer> typeCounts = placedMinionsByType.get(islandId);
        if (typeCounts == null) return 0;
        return typeCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Returns a copy of the per-type breakdown for an island */
    public Map<MinionType, Integer> getMinionBreakdown(String islandId) {
        Map<MinionType, Integer> counts = placedMinionsByType.get(islandId);
        if (counts == null) return Collections.emptyMap();
        return new HashMap<>(counts);
    }

    public boolean placeMinion(Player player, Island island, String minionTypeStr) {
        MinionType type = MinionType.fromString(minionTypeStr);

        if (!canPlaceMinion(player, island)) {
            player.sendMessage("§cYou have reached your minion slot limit! Purchase more §eMinion Slots§c upgrades.");
            return false;
        }

        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();

        // Use per-type tracking
        placedMinionsByType
            .computeIfAbsent(islandId, k -> new HashMap<>())
            .merge(type, 1, Integer::sum);

        saveMinionDataForIsland(islandId);

        // Task batch: AC hook for minion macro (prod log + Neural)
        if (plugin.getAntiCheatManager() != null) {
            int count = placedMinionsByType.getOrDefault(islandId, java.util.Collections.emptyMap()).getOrDefault(type, 0);
            plugin.getAntiCheatManager().isFlaggedForMinionMacro(player, count);
        }

        if (!islandFuels.containsKey(islandId)) {
            islandFuels.put(islandId, 1000);
        }

        int newTotal = getTotalPlacedMinions(islandId);
        spawnMinionEntity(player, island, type, islandId, newTotal);

        player.sendMessage("§aPlaced §e" + type.getDisplayName() + "§a minion! (" + newTotal + "/" + getMaxMinionSlots(islandId) + " slots used).");

        // Early game / onboarding quest hook (FIRST minion quest) - safe, called from GUI/main
        if (plugin.getQuestManager() != null && island != null) {
            // Guard + report via expanded anti-cheat (minion place also feeds spam detection)
            if (plugin.getAntiCheatManager() != null) {
                if (plugin.getAntiCheatManager().isFlaggedForQuestExploit(player) ||
                    plugin.getAntiCheatManager().isFlaggedForMinionSpam(player)) {
                    player.sendMessage("§cAnti-cheat: Quest/minion progress temporarily suppressed.");
                    return true; // still placed, but no progress credit
                }
                plugin.getAntiCheatManager().reportHighQuestProgress(player, "first_minion");
                plugin.getAntiCheatManager().recordMinionPlace(player);
            }
            plugin.getQuestManager().addProgressToIsland(island.getId(), com.thenerdcj.quest.Quest.QuestCategory.CHALLENGE, 1);
        }

        return true;
    }

    /** Overload accepting the enum directly (preferred) */
    public boolean placeMinion(Player player, Island island, MinionType type) {
        return placeMinion(player, island, type.getKey());
    }

    private void spawnMinionEntity(Player player, Island island, MinionType type, String islandId, int minionNumber) {
        World world = (player != null) ? player.getWorld() : Bukkit.getWorlds().get(0);
        Location center = island.getCenter(world);
        if (center == null) {
            center = (player != null) ? player.getLocation().clone() : new Location(world, 0, 100, 0);
        }

        int spread = 3;
        int xOffset = ((minionNumber - 1) % 4) * spread - (spread);
        int zOffset = ((minionNumber - 1) / 4) * spread;
        Location spawnLoc = center.clone().add(xOffset, 1.2, zOffset);

        ArmorStand stand = (ArmorStand) world.spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setCustomNameVisible(true);
        stand.setCustomName("§6" + type.getDisplayName() + " Minion §7#" + minionNumber);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setSmall(true);

        // Apply cosmetic Minion Skin - prefer per-minion assignment, fallback to owner active (Play-to-Win visual only)
        Material helmetMat = type.getIcon();
        ItemStack helmet = new ItemStack(helmetMat);
        UUID islandOwner = (island != null) ? island.getOwnerUuid() : (player != null ? player.getUniqueId() : null);
        MinionSkinManager skinMgr = (plugin != null) ? plugin.getMinionSkinManager() : null;
        if (skinMgr != null && islandOwner != null && island != null) {
            MinionSkin skin = skinMgr.getAssignedOrActiveSkin(island.getId(), minionNumber, islandOwner);
            if (skin != null && !skin.isNone()) {
                helmet = skinMgr.getMinionHelmetForSkin(skin, helmetMat);
                // Light premium particle accents (Folia-safe)
                skinMgr.spawnMinionCosmeticParticles(stand, skin);
            }
        }
        stand.getEquipment().setHelmet(helmet);

        activeMinions.computeIfAbsent(islandId, k -> new ArrayList<>()).add(stand);
        scheduleResourceTask(stand, player, type, islandId);
    }

    private void scheduleResourceTask(ArmorStand minion, Player owner, MinionType type, String islandId) {
        Runnable minionTask = () -> {
            if (minion == null || !minion.isValid() || minion.isDead()) {
                return;
            }
            long start = 0;
            if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();

            int currentFuel = islandFuels.getOrDefault(islandId, 1000);
            // Polish: More efficient minions consume slightly less fuel per cycle
            double consumption = Math.max(0.6, 1.0 / type.getProductionMultiplier());
            int fuelCost = (int) Math.ceil(consumption);

            if (currentFuel >= fuelCost) {
                islandFuels.put(islandId, currentFuel - fuelCost);
            } else {
                if (owner != null && owner.isOnline()) {
                    owner.sendActionBar("§cMinion out of fuel! Feed it real items (Coal, Blaze Rods, etc.)");
                }
                return;
            }

            Material resource = type.getResource();
            double multiplier = type.getProductionMultiplier();
            int baseAmount = 1 + (int) (Math.random() * 2);
            int amount = Math.max(1, (int) Math.round(baseAmount * multiplier));

            ItemStack drop = new ItemStack(resource, amount);
            minion.getWorld().dropItemNaturally(minion.getLocation().add(0, 0.8, 0), drop);

            if (owner != null && owner.isOnline()) {
                String niceName = resource.name().toLowerCase().replace('_', ' ');
                owner.sendActionBar("§7" + type.getDisplayName() + " Minion produced §a+" + amount + " " + niceName + " (fuel: " + islandFuels.getOrDefault(islandId, 0) + ")");
            }
            if (start != 0) {
                long ns = System.nanoTime() - start;
                if (ns > 500_000L) plugin.getLogger().info("[MinionManager] PROFILE: minion produce cycle took " + (ns / 1_000_000.0) + " ms (hot path for large minion farms)");
            }
        };

        // Use Folia EntityScheduler when possible — this is the correct way to tick per-entity on Folia
        if (plugin.getThreadSafety().isFolia() && minion != null && minion.isValid()) {
            plugin.getThreadSafety().runRepeatingForEntity(minion, minionTask, 20L * 5, 20L * 10);
        } else {
            // Fallback for Paper/Spigot — use ThreadSafety
            plugin.getThreadSafety().runRepeatingOnMainThread(minionTask, 20L * 5, 20L * 10);
        }
    }

    // ==================== METHODS REQUIRED BY MinionsGUI ====================

    public int getPlacedMinionCount(String islandId) {
        return getTotalPlacedMinions(islandId);
    }

    public int getIslandFuel(String islandId) {
        return islandFuels.getOrDefault(islandId, 1000);
    }

    /** Admin helper to directly set or add fuel (for /minions admin) */
    public void adminAddFuel(String islandId, int amount) {
        int current = islandFuels.getOrDefault(islandId, 1000);
        islandFuels.put(islandId, Math.max(0, current + amount));
        saveIslandFuel(islandId);
    }

    /** Expose for admin command (temporary direct access) */
    public Map<String, Integer> getIslandFuels() {
        return islandFuels;
    }

    /** Helper for per-minion skin assignment (polish) */
    public MinionSkin getAssignedSkinForMinion(String islandId, int minionNum, UUID owner) {
        if (plugin.getMinionSkinManager() != null) {
            return plugin.getMinionSkinManager().getAssignedOrActiveSkin(islandId, minionNum, owner);
        }
        return null;
    }

    public void assignSkinToMinionSlot(String islandId, int minionNum, MinionSkin skin, UUID owner) {
        if (plugin.getMinionSkinManager() != null) {
            plugin.getMinionSkinManager().assignSkinToMinion(islandId, minionNum, skin, owner);
            saveMinionDataForIsland(islandId); // triggers assignment save
        }
    }

    /** Clear a specific per-minion assignment (polish UX). */
    public void clearSkinAssignmentForMinionSlot(String islandId, int minionNum, UUID owner) {
        if (plugin.getMinionSkinManager() != null) {
            plugin.getMinionSkinManager().clearMinionAssignment(islandId, minionNum);
            // Explicit DB delete for the cleared slot
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().deleteMinionSkinAssignment(islandId, minionNum);
            }
            saveMinionDataForIsland(islandId);
        }
    }

    /**
     * Respawn minion ArmorStands for an island based on current counts.
     * Called on player island load (basic respawn without saved positions).
     */
    public void respawnMinionsForIsland(Island island) {
        if (island == null) return;
        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
        Map<MinionType, Integer> breakdown = getMinionBreakdown(islandId);
        if (breakdown.isEmpty()) return;

        World world = island.getCenter(null) != null ? island.getCenter(null).getWorld() : Bukkit.getWorlds().get(0);
        Location center = island.getCenter(world);
        if (center == null) return;

        // Clear any stale runtime entities for this island
        List<ArmorStand> existing = activeMinions.remove(islandId);
        if (existing != null) {
            for (ArmorStand stand : existing) {
                if (stand != null && stand.isValid()) stand.remove();
            }
        }

        int minionNumber = 1;
        for (Map.Entry<MinionType, Integer> entry : breakdown.entrySet()) {
            MinionType type = entry.getKey();
            int count = entry.getValue();
            for (int i = 0; i < count; i++) {
                // Reuse the grid placement logic
                int spread = 3;
                int xOffset = ((minionNumber - 1) % 4) * spread - spread;
                int zOffset = ((minionNumber - 1) / 4) * spread;
                Location spawnLoc = center.clone().add(xOffset, 1.2, zOffset);

                ArmorStand stand = (ArmorStand) world.spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
                stand.setCustomNameVisible(true);
                stand.setCustomName("§6" + type.getDisplayName() + " Minion §7#" + minionNumber);
                stand.setGravity(false);
                stand.setBasePlate(false);
                stand.setArms(true);
                stand.setSmall(true);

                // Cosmetic Minion Skin application (per-minion assignment or owner's active theme)
                ItemStack helmet = new ItemStack(type.getIcon());
                MinionSkinManager skinMgr = (plugin != null) ? plugin.getMinionSkinManager() : null;
                if (skinMgr != null && island != null) {
                    MinionSkin skin = skinMgr.getAssignedOrActiveSkin(island.getId(), minionNumber, island.getOwnerUuid());
                    if (skin != null && !skin.isNone()) {
                        helmet = skinMgr.getMinionHelmetForSkin(skin, type.getIcon());
                        skinMgr.spawnMinionCosmeticParticles(stand, skin);
                    }
                }
                stand.getEquipment().setHelmet(helmet);

                activeMinions.computeIfAbsent(islandId, k -> new ArrayList<>()).add(stand);
                scheduleResourceTask(stand, null, type, islandId);  // owner null is ok for actionbar

                minionNumber++;
            }
        }
    }

    /**
     * Feed real fuel items to an island's minions.
     * Consumes qualifying items from the player's inventory and adds fuel units.
     */
    public int feedFuel(Player player, Island island) {
        if (player == null || island == null) return 0;

        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
        int totalAdded = 0;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            Integer fuelValue = FUEL_VALUES.get(item.getType());
            if (fuelValue == null || fuelValue <= 0) continue;

            int amountToUse = item.getAmount();
            int added = amountToUse * fuelValue;
            totalAdded += added;

            // Remove the items
            player.getInventory().setItem(i, null);
        }

        if (totalAdded > 0) {
            int current = islandFuels.getOrDefault(islandId, 1000);
            islandFuels.put(islandId, current + totalAdded);
            player.sendMessage("§aAdded §e" + totalAdded + " §afuel units to your island minions!");
            saveIslandFuel(islandId); // Persist fuel (polish)
        } else {
            player.sendMessage("§cNo valid fuel items found in your inventory. Try Coal, Blaze Rods, Lava Buckets, etc.");
        }

        return totalAdded;
    }

    /** Returns a list of accepted fuel materials for display purposes */
    public static List<Material> getAcceptedFuelMaterials() {
        return new ArrayList<>(FUEL_VALUES.keySet());
    }

    public void removeMinion(String islandId) {
        Map<MinionType, Integer> counts = placedMinionsByType.get(islandId);
        if (counts == null || counts.isEmpty()) return;

        MinionType toRemove = counts.keySet().iterator().next();
        int newCount = counts.get(toRemove) - 1;
        if (newCount <= 0) {
            counts.remove(toRemove);
        } else {
            counts.put(toRemove, newCount);
        }
        if (counts.isEmpty()) placedMinionsByType.remove(islandId);

        saveMinionDataForIsland(islandId);
    }

    public void removeMinionType(String islandId, MinionType type) {
        Map<MinionType, Integer> counts = placedMinionsByType.get(islandId);
        if (counts == null) return;

        int current = counts.getOrDefault(type, 0);
        if (current > 0) {
            if (current == 1) {
                counts.remove(type);
            } else {
                counts.put(type, current - 1);
            }
            if (counts.isEmpty()) placedMinionsByType.remove(islandId);
            saveMinionDataForIsland(islandId);
        }
    }

    public void loadMinionDataForIsland(String islandId) {
        // Load per-type data
        plugin.getDatabaseManager().loadMinionData(islandId).thenAccept(data -> {
            Map<MinionType, Integer> typeMap = new HashMap<>();
            if (data != null) {
                for (Map.Entry<Integer, Integer> entry : data.entrySet()) {
                    MinionType t = mapLegacyType(entry.getKey());
                    if (t != null && entry.getValue() > 0) {
                        typeMap.put(t, entry.getValue());
                    }
                }
            }
            if (!typeMap.isEmpty()) {
                placedMinionsByType.put(islandId, typeMap);
            }
        });

        // Load persisted fuel
        plugin.getDatabaseManager().loadIslandFuel(islandId).thenAccept(fuel -> {
            islandFuels.put(islandId, fuel);
        });

        // Load per-minion skin assignments (polish)
        if (plugin.getMinionSkinManager() != null) {
            plugin.getMinionSkinManager().loadMinionAssignmentsForIsland(islandId);
        }
    }

    public void saveMinionDataForIsland(String islandId) {
        Map<MinionType, Integer> counts = placedMinionsByType.get(islandId);
        if (counts == null || counts.isEmpty()) {
            plugin.getDatabaseManager().saveMinionData(islandId, 0, 0);
            return;
        }
        for (Map.Entry<MinionType, Integer> entry : counts.entrySet()) {
            plugin.getDatabaseManager().saveMinionData(islandId, entry.getKey().ordinal(), entry.getValue());
        }
        // Save per-minion skin assignments (polish)
        if (plugin.getMinionSkinManager() != null) {
            plugin.getMinionSkinManager().saveMinionAssignmentsForIsland(islandId);
        }
    }

    /** Saves current fuel for an island (called after significant changes) */
    public void saveIslandFuel(String islandId) {
        if (islandId == null) return;
        int fuel = islandFuels.getOrDefault(islandId, 1000);
        plugin.getDatabaseManager().saveIslandFuel(islandId, fuel);
    }

    private MinionType mapLegacyType(int legacy) {
        return switch (legacy) {
            case 0 -> MinionType.WHEAT;
            case 1 -> MinionType.COBBLESTONE;
            case 2 -> MinionType.IRON;
            case 3 -> MinionType.GOLD;
            case 4 -> MinionType.DIAMOND;
            default -> null;
        };
    }

    /**
     * Folia world unload hook - clears active entity refs for minions in the unloading world to avoid stale refs.
     */
    public void onWorldUnload(org.bukkit.World world) {
        if (world == null) return;
        // Best-effort: scan active entities and remove those in this world; island keys are grid based so we drop matching world
        activeMinions.values().forEach(list -> list.removeIf(e -> e != null && e.isValid() && e.getWorld() != null && e.getWorld().equals(world)));
        // Note: placed maps by islandId (grid) are kept; on re-load of dim they respawn via Island load hooks.
        plugin.getLogger().info("[MinionManager] World unload cleanup done for " + world.getName());
        cleanupCaches();
    }

    /**
     * Bounded eviction for minion/fuel maps (large scale compression for 1000+ islands).
     * Per "Minion/Hologram: ... avoid any global lists iteration without bounds", "more CHM bounds".
     */
    private void cleanupCaches() {
        if (placedMinionsByType.size() > MAX_ISLANDS_MINIONS) {
            java.util.Iterator<String> it = placedMinionsByType.keySet().iterator();
            int toRemove = placedMinionsByType.size() - (MAX_ISLANDS_MINIONS - 200);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
        if (activeMinions.size() > MAX_ISLANDS_MINIONS) {
            java.util.Iterator<String> it = activeMinions.keySet().iterator();
            int toRemove = activeMinions.size() - (MAX_ISLANDS_MINIONS - 200);
            while (it.hasNext() && toRemove > 0) {
                String id = it.next(); it.remove();
                List<ArmorStand> list = activeMinions.remove(id); // already removed key
                if (list != null) for (ArmorStand e : list) if (e != null && e.isValid()) e.remove();
                toRemove--;
            }
        }
        if (islandFuels.size() > MAX_ISLANDS_MINIONS) {
            java.util.Iterator<String> it = islandFuels.keySet().iterator();
            int toRemove = islandFuels.size() - (MAX_ISLANDS_MINIONS - 200);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
    }
}
