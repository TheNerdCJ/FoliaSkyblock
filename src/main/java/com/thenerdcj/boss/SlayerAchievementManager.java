package com.thenerdcj.boss;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SlayerAchievementManager {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Map<SlayerAchievement, Integer>> playerProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Set<SlayerAchievement>> unlockedAchievements = new ConcurrentHashMap<>();

    public SlayerAchievementManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        MessageUtil.info(plugin.getLogger(), "§a[SlayerAchievements] Achievement system initialized");
    }

    public int getProgress(UUID playerUuid, SlayerAchievement achievement) {
        return playerProgress.getOrDefault(playerUuid, Collections.emptyMap())
                .getOrDefault(achievement, 0);
    }

    public void updateProgress(UUID playerUuid, SlayerAchievement achievement, int newProgress) {
        playerProgress.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(achievement, newProgress);
        checkAndUnlock(playerUuid, achievement, newProgress);
    }

    public void incrementProgress(UUID playerUuid, SlayerAchievement achievement, int amount) {
        int current = getProgress(playerUuid, achievement);
        updateProgress(playerUuid, achievement, current + amount);
    }

    private void checkAndUnlock(UUID playerUuid, SlayerAchievement achievement, int progress) {
        if (achievement.isUnlocked(progress) && !isUnlocked(playerUuid, achievement)) {
            unlockAchievement(playerUuid, achievement);
        }
    }

    public boolean isUnlocked(UUID playerUuid, SlayerAchievement achievement) {
        return unlockedAchievements.getOrDefault(playerUuid, Collections.emptySet())
                .contains(achievement);
    }

    private void unlockAchievement(UUID playerUuid, SlayerAchievement achievement) {
        unlockedAchievements.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet())
                .add(achievement);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage("§6§l══════════════════════════════════════");
            player.sendMessage("§a§l★ ACHIEVEMENT UNLOCKED! ★");
            player.sendMessage("§e" + achievement.getDisplayName());
            player.sendMessage("§7" + achievement.getDescription());
            player.sendMessage("§6§l══════════════════════════════════════");

            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            if (plugin.getIslandManager().getIsland(playerUuid, player.getWorld().getEnvironment()) != null) {
                plugin.getIslandManager().getIsland(playerUuid, player.getWorld().getEnvironment())
                        .addXp(achievement.getXpReward());
                player.sendMessage("§a+" + achievement.getXpReward() + " Island XP!");
            }

            for (AchievementReward reward : achievement.getItemRewards()) {
                ItemStack item = new ItemStack(reward.getMaterial(), reward.getAmount());
                player.getInventory().addItem(item);
                player.sendMessage("§a+" + reward.getAmount() + "x " + reward.getMaterial().name());
            }

            if (achievement != SlayerAchievement.ULTIMATE_SLAYER) {
                checkUltimateSlayer(playerUuid);
            }
        }
    }

    private void checkUltimateSlayer(UUID playerUuid) {
        Set<SlayerAchievement> unlocked = unlockedAchievements.getOrDefault(playerUuid, Collections.emptySet());
        long totalAchievements = Arrays.stream(SlayerAchievement.values())
                .filter(a -> a != SlayerAchievement.ULTIMATE_SLAYER)
                .count();

        if (unlocked.size() >= totalAchievements) {
            unlockAchievement(playerUuid, SlayerAchievement.ULTIMATE_SLAYER);
        }
    }

    public Set<SlayerAchievement> getUnlockedAchievements(UUID playerUuid) {
        return unlockedAchievements.getOrDefault(playerUuid, Collections.emptySet());
    }

    public double getCompletionPercentage(UUID playerUuid) {
        Set<SlayerAchievement> unlocked = getUnlockedAchievements(playerUuid);
        return (double) unlocked.size() / SlayerAchievement.values().length;
    }

    private void saveToDatabase(UUID playerUuid, SlayerAchievement achievement) {
        // Database persistence ready
    }

    public void loadPlayerAchievements(UUID playerUuid) {
        // Load from database
    }
}