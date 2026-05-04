package com.thenerdcj.boss;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

/**
 * Manages natural Minecraft boss spawning, tracking, and progression
 * EXPANDED: Includes Slayer Tier system with AI-powered reward balancing
 */
public class BossManager {

    private final FoliaSkyblock plugin;

    // Track killed bosses per island: IslandID -> Set of killed boss names
    private final Map<String, Set<String>> killedBosses = new ConcurrentHashMap<>();

    // Track active bosses: EntityUUID -> BossType
    private final Map<UUID, String> activeBosses = new ConcurrentHashMap<>();

    // Slayer quest tracking: PlayerUUID -> Active Quest
    private final Map<UUID, SlayerQuest> activeSlayerQuests = new ConcurrentHashMap<>();

    // Slayer progress: PlayerUUID -> EntityType -> Highest Tier Completed
    private final Map<UUID, Map<EntityType, Integer>> slayerProgress = new ConcurrentHashMap<>();

    // AI-Powered Reward Balancer
    private final SlayerRewardBalancer rewardBalancer;

    // Slayer Achievement Manager
    private final SlayerAchievementManager achievementManager;

    public BossManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.rewardBalancer = new SlayerRewardBalancer(plugin);
        this.achievementManager = new SlayerAchievementManager(plugin);
    }

    // ============================================
    // DIMENSION BOSS METHODS (Existing)
    // ============================================

    public boolean canSpawnBoss(Player player, DimensionBoss boss) {
        String islandId = getIslandId(player);
        Set<String> killed = killedBosses.getOrDefault(islandId, Collections.emptySet());
        return !killed.contains(boss.getName());
    }

    public boolean spawnBoss(Player player, DimensionBoss boss) {
        if (!canSpawnBoss(player, boss)) {
            player.sendMessage("§cYou have already defeated " + boss.getName() + "!");
            return false;
        }

        Location spawnLoc = player.getLocation().add(0, 5, 0);
        LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(spawnLoc, boss.getEntityType());

        entity.setCustomName("§c§l" + boss.getName());
        entity.setCustomNameVisible(true);
        entity.setMaxHealth(boss.getHealth());
        entity.setHealth(boss.getHealth());

        activeBosses.put(entity.getUniqueId(), boss.getName());

        player.sendMessage("§c§l⚠ BOSS SPAWNED! ⚠");
        player.sendMessage("§e" + boss.getName() + " §7has appeared!");

        return true;
    }

    public void recordBossKill(Player player, String bossName) {
        String islandId = getIslandId(player);
        killedBosses.computeIfAbsent(islandId, k -> ConcurrentHashMap.newKeySet()).add(bossName);

        player.sendMessage("§a§l✓ " + bossName + " defeated!");
        player.sendMessage("§7This boss will not respawn on your island.");
    }

    private void checkDimensionProgress(Player player, Island island) {
        int level = island.getLevel();

        if (level >= 50 && canSpawnBoss(player, DimensionBoss.ENDER_DRAGON)) {
            player.sendMessage("§5§lThe End awaits! §7Defeat the Ender Dragon to unlock the End dimension!");
        }

        if (level >= 75 && canSpawnBoss(player, DimensionBoss.WITHER)) {
            player.sendMessage("§8§lThe Nether calls! §7Defeat the Wither to unlock advanced Nether features!");
        }
    }

    public boolean canAccessDimension(Player player, String dimension) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return false;

        int level = island.getLevel();

        switch (dimension.toUpperCase()) {
            case "NETHER":
                return level >= 30;
            case "END":
                return level >= 50 && !canSpawnBoss(player, DimensionBoss.ENDER_DRAGON);
            default:
                return true;
        }
    }

    public boolean isBoss(UUID entityId) {
        return activeBosses.containsKey(entityId);
    }

    public void removeBoss(UUID entityId) {
        activeBosses.remove(entityId);
    }

    private String getIslandId(Player player) {
        return player.getUniqueId().toString();
    }

    // ============================================
    // SLAYER TIER METHODS (NEW)
    // ============================================

    public boolean startSlayerQuest(Player player, SlayerTier tier) {
        if (activeSlayerQuests.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou already have an active slayer quest!");
            return false;
        }

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || island.getLevel() < tier.getMinLevel()) {
            player.sendMessage("§cYour island level is too low for " + tier.getDisplayName() + "!");
            return false;
        }

        int currentTier = getCurrentSlayerTier(player, tier.getTargetEntity());
        if (tier.getTier() > currentTier + 1) {
            player.sendMessage("§cYou must complete previous tiers first!");
            return false;
        }

        SlayerQuest quest = new SlayerQuest(player.getUniqueId(), tier);
        activeSlayerQuests.put(player.getUniqueId(), quest);

        player.sendMessage("§6§l══════════════════════════════════════");
        player.sendMessage("§a§lSLAYER QUEST STARTED!");
        player.sendMessage("§e" + tier.getDisplayName());
        player.sendMessage("§6§l══════════════════════════════════════");

        return true;
    }

    public void recordSlayerKill(Player player, EntityType entityType) {
        SlayerQuest quest = activeSlayerQuests.get(player.getUniqueId());

        if (quest == null || quest.getTier().getTargetEntity() != entityType) {
            return;
        }

        quest.addKill();

        plugin.getDatabaseManager().incrementSlayerKills(
                player.getUniqueId(),
                player.getName(),
                entityType.name(),
                quest.getTier().getTier()
        );

        if (quest.isCompleted()) {
            completeSlayerQuest(player, quest);
        } else {
            int remaining = quest.getKillsRequired() - quest.getKills();
            if (remaining % 10 == 0 && remaining > 0) {
                player.sendMessage("§aSlayer Progress: §e" + quest.getKills() + "§7/§e" + quest.getKillsRequired() +
                        " §7kills (§6" + (int)(quest.getProgress() * 100) + "%§7)");
            }
        }
    }

    /**
     * Track slayer achievements after quest completion
     */
    private void trackSlayerAchievements(Player player, SlayerTier tier) {
        UUID uuid = player.getUniqueId();

        achievementManager.incrementProgress(uuid, SlayerAchievement.FIRST_BLOOD, 1);
        achievementManager.incrementProgress(uuid, SlayerAchievement.SLAYER_APPRENTICE, 1);
        achievementManager.incrementProgress(uuid, SlayerAchievement.SLAYER_VETERAN, 1);
        achievementManager.incrementProgress(uuid, SlayerAchievement.SLAYER_LEGEND, 1);

        if (tier.getTargetEntity() == EntityType.ZOMBIE) {
            achievementManager.incrementProgress(uuid, SlayerAchievement.ZOMBIE_SLAYER, 1);
        } else if (tier.getTargetEntity() == EntityType.SPIDER) {
            achievementManager.incrementProgress(uuid, SlayerAchievement.SPIDER_SLAYER, 1);
        } else if (tier.getTargetEntity() == EntityType.ENDERMAN) {
            achievementManager.incrementProgress(uuid, SlayerAchievement.ENDERMAN_SLAYER, 1);
        } else if (tier.getTargetEntity() == EntityType.BLAZE) {
            achievementManager.incrementProgress(uuid, SlayerAchievement.BLAZE_SLAYER, 1);
        }

        int zombieTier = getCurrentSlayerTier(player, EntityType.ZOMBIE);
        int spiderTier = getCurrentSlayerTier(player, EntityType.SPIDER);
        int endermanTier = getCurrentSlayerTier(player, EntityType.ENDERMAN);
        int blazeTier = getCurrentSlayerTier(player, EntityType.BLAZE);

        if (zombieTier >= 5 && spiderTier >= 4 && endermanTier >= 3 && blazeTier >= 3) {
            achievementManager.updateProgress(uuid, SlayerAchievement.SLAYER_MASTER, 15);
        }
    }

    /**
     * Complete a slayer quest and give rewards
     */
    private void completeSlayerQuest(Player player, SlayerQuest quest) {
        SlayerTier tier = quest.getTier();

        player.sendMessage("§6§l══════════════════════════════════════");
        player.sendMessage("§a§lSLAYER QUEST COMPLETE!");
        player.sendMessage("§e" + tier.getDisplayName());
        player.sendMessage("§6§l══════════════════════════════════════");

        int serverPopulation = Bukkit.getOnlinePlayers().size();
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        int playerLevel = island != null ? island.getLevel() : 1;

        List<ItemStack> rewards = new ArrayList<>();

        for (SlayerReward reward : tier.getRewards()) {
            double dynamicChance = rewardBalancer.calculateDynamicDropChance(reward, playerLevel, serverPopulation);

            if (new Random().nextDouble() <= dynamicChance) {
                ItemStack item = new ItemStack(reward.getMaterial(), reward.getAmount());
                rewards.add(item);
                player.getInventory().addItem(item);

                rewardBalancer.recordActualDrop(reward.getMaterial(), true, dynamicChance);

                if (reward.isSpecial()) {
                    player.sendMessage("§6§l★ SPECIAL DROP! ★ §e" + reward.getAmount() + "x " +
                            reward.getMaterial().name() + " §7(" + reward.getRarityName() + ")");
                }
            } else {
                rewardBalancer.recordActualDrop(reward.getMaterial(), false, dynamicChance);
            }
        }

        if (island != null) {
            int xpReward = tier.getXpRequired() / 2;
            island.addXp(xpReward);
            player.sendMessage("§a+" + xpReward + " Island XP!");
        }

        updateSlayerProgress(player, tier);
        trackSlayerAchievements(player, tier);
        activeSlayerQuests.remove(player.getUniqueId());

        SlayerTier nextTier = tier.getNextTier();
        if (nextTier != null) {
            player.sendMessage("§7Next tier available: §e" + nextTier.getDisplayName());
            player.sendMessage("§7Use §a/slayer start " + nextTier.name().toLowerCase() + " §7to begin!");
        } else {
            player.sendMessage("§6§l★ MAX TIER REACHED! ★");
            player.sendMessage("§aYou have mastered this slayer!");
        }
    } // End of completeSlayerQuest

    /**
     * Update player's slayer progress
     */
    private void updateSlayerProgress(Player player, SlayerTier tier) {
        slayerProgress.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(tier.getTargetEntity(), Math.max(
                        slayerProgress.get(player.getUniqueId()).getOrDefault(tier.getTargetEntity(), 0),
                        tier.getTier()
                ));
    }

    /**
     * Get current slayer tier for entity type
     */
    public int getCurrentSlayerTier(Player player, EntityType entityType) {
        return slayerProgress.getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(entityType, 0);
    }

    /**
     * Get active slayer quest for player
     */
    public SlayerQuest getActiveSlayerQuest(Player player) {
        return activeSlayerQuests.get(player.getUniqueId());
    }

    /**
     * Abandon current slayer quest
     */
    public boolean abandonSlayerQuest(Player player) {
        SlayerQuest quest = activeSlayerQuests.remove(player.getUniqueId());
        if (quest != null) {
            player.sendMessage("§cSlayer quest abandoned: " + quest.getTier().getDisplayName());
            return true;
        }
        return false;
    }

    /**
     * Get AI reward balancer for external access
     */
    public SlayerRewardBalancer getRewardBalancer() {
        return rewardBalancer;
    }

    /**
     * Get achievement manager for external access
     */
    public SlayerAchievementManager getAchievementManager() {
        return achievementManager;
    }
}