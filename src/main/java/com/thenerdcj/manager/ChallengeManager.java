package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.challenge.Challenge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChallengeManager {
    private final FoliaSkyblock plugin;
    private final Map<UUID, List<Challenge>> playerChallenges = new ConcurrentHashMap<>();

    public ChallengeManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<List<Challenge>> getActiveChallenges(UUID playerUuid) {
        return CompletableFuture.completedFuture(
                playerChallenges.getOrDefault(playerUuid, new ArrayList<>())
        );
    }

    public CompletableFuture<List<Challenge>> getActiveChallenges() {
        List<Challenge> allChallenges = new ArrayList<>();
        for (List<Challenge> challenges : playerChallenges.values()) {
            allChallenges.addAll(challenges);
        }
        return CompletableFuture.completedFuture(allChallenges);
    }

    public void updateProgress(UUID playerUuid, String challengeId, int amount) {
        List<Challenge> challenges = playerChallenges.get(playerUuid);
        if (challenges == null) return;

        for (Challenge challenge : challenges) {
            if (challenge.id().equals(challengeId) && !challenge.isCompleted()) {
                challenge.addProgress(amount);

                if (challenge.isCompleted()) {
                    completeChallenge(playerUuid, challenge);
                }
                break;
            }
        }
    }

    private void completeChallenge(UUID playerUuid, Challenge challenge) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage("§a§lChallenge Complete! §e" + challenge.getDescription());
            player.sendMessage("§aEarned §e" + challenge.getRewardXP() + " XP§a!");
        }

        // Award XP to island
        plugin.getIslandManager().getIslandByOwner(playerUuid, org.bukkit.World.Environment.NORMAL)
                .thenAccept(island -> {
                    if (island != null) {
                        island.addXp(challenge.getRewardXP());
                    }
                });
    }

    public void generateDailyChallenge(UUID playerUuid) {
        String[] descriptions = {
                "Mine 100 blocks",
                "Kill 50 mobs",
                "Collect 200 items",
                "Build a 50-block structure",
                "Complete 10 trades"
        };

        int target = 50 + new Random().nextInt(100);
        int rewardXP = target * 2;

        Challenge challenge = new Challenge(
                playerUuid,
                Challenge.Type.DAILY,
                "DAILY",
                descriptions[new Random().nextInt(descriptions.length)],
                target,
                rewardXP
        );

        playerChallenges.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(challenge);
    }
}