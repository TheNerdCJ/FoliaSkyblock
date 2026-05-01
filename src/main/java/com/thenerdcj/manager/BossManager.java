package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.DimensionBoss;
import com.thenerdcj.island.Island;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages natural Minecraft boss spawning, tracking, and progression
 */
public class BossManager {

    private final FoliaSkyblock plugin;

    // Track killed bosses per island: IslandID -> Set of killed boss names
    private final Map<String, Set<String>> killedBosses = new ConcurrentHashMap<>();

    // Track active bosses: EntityUUID -> BossType
    private final Map<UUID, String> activeBosses = new ConcurrentHashMap<>();

    public BossManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if player can spawn a boss based on island level
     */
    public boolean canSpawnBoss(Player player, DimensionBoss boss) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return false;

        return boss.isAvailableAtLevel(island.getLevel());
    }

    /**
     * Spawn a natural boss at player's location (Folia-safe)
     */
    public boolean spawnBoss(Player player, DimensionBoss boss) {
        if (!canSpawnBoss(player, boss)) {
            player.sendMessage("§cYour island level is too low to spawn " + boss.getDisplayName() + "!");
            player.sendMessage("§7Required level: " + boss.getMinLevel());
            return false;
        }

        Location spawnLoc = player.getLocation().add(0, 5, 0);

        // Spawn on correct region thread for Folia
        plugin.getServer().getRegionScheduler().execute(plugin, spawnLoc, () -> {
            Entity entity = player.getWorld().spawnEntity(spawnLoc, boss.getEntityType());

            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                living.setCustomName("§c§l" + boss.getDisplayName());
                living.setCustomNameVisible(true);

                // Scale boss health based on dimension
                if (boss.getDimension().equals("Nether")) {
                    living.setMaxHealth(living.getMaxHealth() * 1.5);
                    living.setHealth(living.getMaxHealth());
                } else if (boss.getDimension().equals("End")) {
                    living.setMaxHealth(living.getMaxHealth() * 2);
                    living.setHealth(living.getMaxHealth());
                }
            }

            activeBosses.put(entity.getUniqueId(), boss.name());
            player.sendMessage("§a§lBoss spawned: §e" + boss.getDisplayName() + "!");
            player.sendMessage("§7" + boss.getSpawnInfo());
        });

        return true;
    }

    /**
     * Record a boss kill (works for both natural and spawned bosses)
     */
    public void recordBossKill(Player player, String bossName) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;

        String islandId = island.getGridPosition().toString();
        killedBosses.computeIfAbsent(islandId, k -> ConcurrentHashMap.newKeySet()).add(bossName);

        player.sendMessage("§a§lBoss defeated: §e" + bossName + "!");

        // Check if all bosses in dimension are killed
        checkDimensionProgress(player, island);
    }

    /**
     * Check if player has killed all bosses in current dimension
     */
    private void checkDimensionProgress(Player player, Island island) {
        String dimension = player.getWorld().getEnvironment().name();
        DimensionBoss[] bosses = DimensionBoss.getBossesForDimension(dimension);

        String islandId = island.getGridPosition().toString();
        Set<String> killed = killedBosses.getOrDefault(islandId, Collections.emptySet());

        long killedCount = Arrays.stream(bosses)
                .map(DimensionBoss::name)
                .filter(killed::contains)
                .count();

        if (killedCount == bosses.length) {
            player.sendMessage("§6§l★ DIMENSION COMPLETE! ★");
            player.sendMessage("§aYou have defeated all bosses in " + dimension + "!");

            if (dimension.equals("Overworld")) {
                player.sendMessage("§eYou can now access the Nether dimension!");
            } else if (dimension.equals("Nether")) {
                player.sendMessage("§eYou can now access The End dimension!");
            } else if (dimension.equals("THE_END")) {
                player.sendMessage("§6§l★ CONGRATULATIONS! ★");
                player.sendMessage("§aYou have conquered all dimensions!");
            }

            // Grant XP bonus for completing dimension
            island.addXp(1000);
        }
    }

    /**
     * Check if player can access a dimension
     */
    public boolean canAccessDimension(Player player, String dimension) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return false;

        String islandId = island.getGridPosition().toString();
        Set<String> killed = killedBosses.getOrDefault(islandId, Collections.emptySet());

        // Check if all bosses of previous dimension are killed
        if (dimension.equals("NETHER")) {
            DimensionBoss[] overworldBosses = DimensionBoss.getBossesForDimension("Overworld");
            return Arrays.stream(overworldBosses)
                    .map(DimensionBoss::name)
                    .allMatch(killed::contains);
        } else if (dimension.equals("THE_END")) {
            DimensionBoss[] netherBosses = DimensionBoss.getBossesForDimension("Nether");
            return Arrays.stream(netherBosses)
                    .map(DimensionBoss::name)
                    .allMatch(killed::contains);
        }

        return true; // Overworld always accessible
    }

    public boolean isBoss(UUID entityId) {
        return activeBosses.containsKey(entityId);
    }

    public String getBossType(UUID entityId) {
        return activeBosses.get(entityId);
    }

    public void removeBoss(UUID entityId) {
        activeBosses.remove(entityId);
    }
}