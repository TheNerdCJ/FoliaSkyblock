package com.thenerdcj.quest;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * QuestManager - Handles Daily & Weekly Quests for islands.
 * 
 * Provides:
 * - Async quest retrieval for GUI
 * - Generation of daily/weekly quests
 * - Claiming rewards
 * 
 * Currently in-memory (like ChallengeManager). Can be extended with Database persistence.
 */
public class QuestManager {

    private final FoliaSkyblock plugin;

    // islandId -> list of quests
    private final Map<String, List<Quest>> questsByIsland = new ConcurrentHashMap<>();

    public QuestManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Future: load quests from database on startup
    }

    /**
     * Get all quests for a specific island (async to match GUI expectation)
     */
    public CompletableFuture<List<Quest>> getQuestsForIsland(String islandId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Quest> quests = questsByIsland.getOrDefault(islandId, Collections.emptyList());
            // Return a copy to avoid concurrent modification issues
            return new ArrayList<>(quests);
        });
    }

    /**
     * Generate (or refresh) daily quests for the island.
     * Removes expired/completed old dailies and adds new ones if needed.
     */
    public void generateDailyQuests(String islandId) {
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());

        // Clean up old/expired dailies
        current.removeIf(q -> 
            q.getType() == Quest.QuestType.DAILY && 
            (q.isCompleted() || q.isExpired() || q.isClaimed())
        );

        // Ensure we have at least 3 daily quests
        long dailyCount = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.DAILY)
            .count();

        for (long i = dailyCount; i < 3; i++) {
            Quest newQuest = createRandomQuest(Quest.QuestType.DAILY);
            current.add(newQuest);
        }
    }

    /**
     * Generate (or refresh) weekly quests for the island.
     */
    public void generateWeeklyQuests(String islandId) {
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());

        // Clean up old weeklies
        current.removeIf(q -> 
            q.getType() == Quest.QuestType.WEEKLY && 
            (q.isCompleted() || q.isExpired() || q.isClaimed())
        );

        long weeklyCount = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.WEEKLY)
            .count();

        for (long i = weeklyCount; i < 2; i++) {
            Quest newQuest = createRandomQuest(Quest.QuestType.WEEKLY);
            current.add(newQuest);
        }
    }

    /**
     * Claim a specific quest reward.
     */
    public boolean claimQuest(String islandId, String questId, Player player) {
        List<Quest> quests = questsByIsland.get(islandId);
        if (quests == null) return false;

        for (Quest quest : quests) {
            if (quest.getId().equals(questId) && quest.isCompleted() && !quest.isClaimed()) {
                quest.setClaimed(true);

                // Deliver rewards (basic implementation - enhance with your Economy/XP system)
                int xp = quest.getRewardXp();
                int money = quest.getRewardMoney();

                player.sendMessage("§a§lQuest Completed! §r§a+" + xp + " XP  §e+$" + money);
                player.sendMessage("§7Thank you for completing: §f" + quest.getTitle());

                // Award Island XP (uses IslandManager which applies party-size balancing automatically)
                plugin.getIslandManager().addIslandXp(player, xp);

                // Award money to the ISLAND BANK (not personal player balance)
                // Uses IslandBankManager.deposit() which persists to DB and cache
                Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                if (island != null) {
                    plugin.getIslandBankManager().deposit(island.getGridPosition(), money)
                            .thenAccept(success -> {
                                if (!success) {
                                    player.sendMessage("§cWarning: Could not add money reward to your island bank.");
                                }
                            });
                } else {
                    player.sendMessage("§cWarning: No island found to deposit money into.");
                }

                // Play sound is handled in GUI

                return true;
            }
        }
        return false;
    }

    /**
     * Optional helper: Add progress to matching quests (call this from your listeners)
     */
    public void addProgressToIsland(String islandId, Quest.QuestCategory category, int amount) {
        List<Quest> quests = questsByIsland.get(islandId);
        if (quests == null) return;

        for (Quest quest : quests) {
            if (quest.getCategory() == category && !quest.isCompleted() && !quest.isExpired() && !quest.isClaimed()) {
                quest.addProgress(amount);
            }
        }
    }

    // ==================== PRIVATE HELPERS ====================

    private Quest createRandomQuest(Quest.QuestType type) {
        Random random = ThreadLocalRandom.current();
        Quest.QuestCategory[] categories = Quest.QuestCategory.values();
        Quest.QuestCategory category = categories[random.nextInt(categories.length)];

        int target = switch (type) {
            case DAILY -> 8 + random.nextInt(25);
            case WEEKLY -> 40 + random.nextInt(80);
        };

        // Adjust target per category for balance
        target = adjustTargetForCategory(category, target, type);

        int rewardXp = Math.max(20, target * (type == Quest.QuestType.DAILY ? 3 : 2));
        int rewardMoney = Math.max(50, target * (type == Quest.QuestType.DAILY ? 8 : 6));

        long duration = type == Quest.QuestType.DAILY 
            ? 24L * 60 * 60 * 1000 
            : 7L * 24 * 60 * 60 * 1000;
        long expiryTime = System.currentTimeMillis() + duration;

        String title = generateTitle(category, type);
        String description = generateDescription(category, target);

        return new Quest(
            UUID.randomUUID().toString(),
            title,
            description,
            category,
            type,
            0,
            target,
            rewardXp,
            rewardMoney,
            false,
            expiryTime
        );
    }

    private int adjustTargetForCategory(Quest.QuestCategory category, int baseTarget, Quest.QuestType type) {
        return switch (category) {
            case MINING, FARMING -> baseTarget;
            case COMBAT -> Math.max(5, baseTarget / 2);
            case BUILDING -> baseTarget + 10;
            case EXPLORATION -> Math.max(5, baseTarget / 3);
            case TRADING -> Math.max(3, baseTarget / 4);
            case CHALLENGE -> baseTarget;
            default -> baseTarget;
        };
    }

    private String generateTitle(Quest.QuestCategory category, Quest.QuestType type) {
        String prefix = type == Quest.QuestType.DAILY ? "Daily " : "Weekly ";
        return prefix + category.name().charAt(0) + category.name().substring(1).toLowerCase() + " Task";
    }

    private String generateDescription(Quest.QuestCategory category, int target) {
        return switch (category) {
            case MINING -> "Mine " + target + " ore blocks on your island";
            case FARMING -> "Harvest " + target + " crops";
            case COMBAT -> "Defeat " + target + " hostile mobs";
            case BUILDING -> "Place " + target + " blocks to expand your island";
            case EXPLORATION -> "Explore and travel " + target + " blocks";
            case TRADING -> "Complete " + target + " trades or villager interactions";
            case CHALLENGE -> "Complete " + target + " special actions";
            default -> "Complete " + target + " actions";
        };
    }
}
