package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
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
 */
public class MinionManager {

    private final FoliaSkyblock plugin;
    private final Map<String, Integer> placedMinions = new HashMap<>();
    private final Map<String, List<ArmorStand>> activeMinions = new HashMap<>();
    private final Map<String, Integer> islandFuels = new HashMap<>();

    public MinionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
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
        int current = placedMinions.getOrDefault(islandId, 0);
        return current < getMaxMinionSlots(islandId);
    }

    public boolean placeMinion(Player player, Island island, String minionType) {
        if (!canPlaceMinion(player, island)) {
            player.sendMessage("§cYou have reached your minion slot limit! Purchase more §eMinion Slots§c upgrades.");
            return false;
        }
        String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
        int newCount = placedMinions.getOrDefault(islandId, 0) + 1;
        placedMinions.put(islandId, newCount);

        if (!islandFuels.containsKey(islandId)) {
            islandFuels.put(islandId, 1000);
        }
        plugin.getDatabaseManager().saveMinionData(islandId, 0, newCount).join();

        spawnMinionEntity(player, island, minionType, islandId, newCount);

        player.sendMessage("§aPlaced §e" + minionType + "§a minion! (" + newCount + "/" + getMaxMinionSlots(islandId) + " slots used). Starting fuel: 1000 units.");
        return true;
    }

    private void spawnMinionEntity(Player player, Island island, String minionType, String islandId, int minionNumber) {
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
        stand.setCustomName("§6" + minionType + " Minion §7#" + minionNumber);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setSmall(true);

        Material helmetMat = getHelmetMaterial(minionType);
        if (helmetMat != null) {
            stand.getEquipment().setHelmet(new ItemStack(helmetMat));
        }

        activeMinions.computeIfAbsent(islandId, k -> new ArrayList<>()).add(stand);
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

                int currentFuel = islandFuels.getOrDefault(islandId, 1000);
                if (currentFuel > 0) {
                    islandFuels.put(islandId, currentFuel - 1);
                    if ((cycles % 10) == 0) {
                        plugin.getDatabaseManager().saveMinionData(islandId, 0, placedMinions.getOrDefault(islandId, 0)).join();
                    }
                } else {
                    if (cycles % 10 == 0 && owner != null && owner.isOnline()) {
                        owner.sendActionBar("§cMinion out of fuel! Refuel using crafted/traded resources to continue.");
                    }
                    return;
                }

                Material resource = getResourceMaterial(type);
                int amount = 1 + (int) (Math.random() * 2);
                ItemStack drop = new ItemStack(resource, amount);
                minion.getWorld().dropItemNaturally(minion.getLocation().add(0, 0.8, 0), drop);

                if (cycles % 5 == 0 && owner != null && owner.isOnline()) {
                    String niceName = resource.name().toLowerCase().replace('_', ' ');
                    owner.sendActionBar("§7" + type + " Minion produced §a+" + amount + " " + niceName + " (fuel: " + islandFuels.getOrDefault(islandId, 0) + ")");
                }
            }
        }.runTaskTimer(plugin, 20L * 5, 20L * 10);
    }

    private Material getResourceMaterial(String type) {
        if (type == null) return Material.DIRT;
        return switch (type.toUpperCase()) {
            case "WHEAT", "CROP", "FARM" -> Material.WHEAT;
            case "COBBLE", "COBBLESTONE", "STONE" -> Material.COBBLESTONE;
            case "IRON" -> Material.IRON_INGOT;
            case "GOLD" -> Material.GOLD_INGOT;
            case "DIAMOND" -> Material.DIAMOND;
            case "EMERALD" -> Material.EMERALD;
            default -> Material.DIRT;
        };
    }

    // ==================== METHODS REQUIRED BY MinionsGUI ====================
    public int getPlacedMinionCount(String islandId) {
        return placedMinions.getOrDefault(islandId, 0);
    }

    public int getIslandFuel(String islandId) {
        return islandFuels.getOrDefault(islandId, 1000);
    }

    public void removeMinion(String islandId) {
        placedMinions.put(islandId, Math.max(0, placedMinions.getOrDefault(islandId, 0) - 1));
    }

    public void loadMinionDataForIsland(String islandId) {
        plugin.getDatabaseManager().loadMinionData(islandId).thenAccept(data -> {
            // Placeholder
        });
    }
}
