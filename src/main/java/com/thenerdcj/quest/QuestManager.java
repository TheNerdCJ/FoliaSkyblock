package com.thenerdcj.quest;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class QuestManager {

    private final FoliaSkyblock plugin;

    private final Map<String, List<Quest>> islandQuests = new ConcurrentHashMap<>();
    private final Map<String, List<Quest.QuestCategory>> recentQuestHistory = new ConcurrentHashMap<>();

    public QuestManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    // ==================== GET QUESTS ====================

    public CompletableFuture<List<Quest>> getQuestsForIsland(String islandId) {
        return CompletableFuture.supplyAsync(() ->
                islandQuests.getOrDefault(islandId, new ArrayList<>()));
    }

    // ==================== QUEST GENERATION ====================

    public void generateDailyQuests(String islandId) {
        List<Quest> newQuests = new ArrayList<>();
        int islandLevel = getIslandLevel(islandId);
        int playerCount = getPlayerCountOnIsland(islandId);

        for (int i = 0; i < 3; i++) {
            Quest quest = generateSmartQuest(islandId, islandLevel, playerCount, Quest.QuestType.DAILY);
            if (quest != null && isQuestFeasible(quest, islandId)) {
                newQuests.add(quest);
            }
        }

        islandQuests.put(islandId, newQuests);
        Bukkit.getLogger().info("[QuestManager] Generated daily quests for island: " + islandId);
    }

    public void generateWeeklyQuests(String islandId) {
        List<Quest> newQuests = new ArrayList<>();
        int islandLevel = getIslandLevel(islandId);
        int playerCount = getPlayerCountOnIsland(islandId);

        for (int i = 0; i < 2; i++) {
            Quest quest = generateSmartQuest(islandId, islandLevel, playerCount, Quest.QuestType.WEEKLY);
            if (quest != null && isQuestFeasible(quest, islandId)) {
                newQuests.add(quest);
            }
        }

        List<Quest> existing = islandQuests.getOrDefault(islandId, new ArrayList<>());
        existing.addAll(newQuests);
        islandQuests.put(islandId, existing);

        Bukkit.getLogger().info("[QuestManager] Generated weekly quests for island: " + islandId);
    }

    // ==================== SMART / WEIGHTED QUEST GENERATION ====================

    private Quest generateSmartQuest(String islandId, int islandLevel, int playerCount, Quest.QuestType type) {
        List<QuestTemplate> pool = getWeightedQuestPool(islandLevel, playerCount, islandId);
        if (pool.isEmpty()) return null;

        QuestTemplate selected = weightedRandomSelection(pool);

        int target = calculateTarget(selected, islandLevel, playerCount, type);
        int rewardXp = calculateRewardXp(selected, target, type);
        int rewardMoney = calculateRewardMoney(selected, target, type);

        long expiry = (type == Quest.QuestType.DAILY)
                ? System.currentTimeMillis() + (1000L * 60 * 60 * 24)
                : System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 7);

        return new Quest(
                UUID.randomUUID().toString(),
                selected.title,
                selected.description,
                selected.category,
                type,
                0,
                target,
                rewardXp,
                rewardMoney,
                false,
                expiry
        );
    }

    private List<QuestTemplate> getWeightedQuestPool(int islandLevel, int playerCount, String islandId) {
        List<QuestTemplate> pool = new ArrayList<>();
        List<Quest.QuestCategory> recent = recentQuestHistory.getOrDefault(islandId, new ArrayList<>());

        addTemplate(pool, Quest.QuestCategory.MINING, "Daily Mining", "Mine blocks", 30, islandLevel >= 5);
        addTemplate(pool, Quest.QuestCategory.FARMING, "Daily Farming", "Harvest crops", 25, islandLevel >= 3);
        addTemplate(pool, Quest.QuestCategory.COMBAT, "Daily Combat", "Defeat mobs", 20, islandLevel >= 8);
        addTemplate(pool, Quest.QuestCategory.BUILDING, "Daily Building", "Build structures", 15, islandLevel >= 10);
        addTemplate(pool, Quest.QuestCategory.EXPLORATION, "Daily Exploration", "Explore areas", 10, islandLevel >= 15);

        for (QuestTemplate t : pool) {
            if (recent.contains(t.category)) {
                t.weight = Math.max(5, t.weight / 2);
            }
        }

        return pool;
    }

    private void addTemplate(List<QuestTemplate> pool, Quest.QuestCategory category,
                             String title, String description, int weight, boolean condition) {
        if (condition) {
            pool.add(new QuestTemplate(category, title, description, weight));
        }
    }

    private QuestTemplate weightedRandomSelection(List<QuestTemplate> pool) {
        int total = pool.stream().mapToInt(t -> t.weight).sum();
        int rand = new Random().nextInt(total);
        for (QuestTemplate t : pool) {
            rand -= t.weight;
            if (rand <= 0) return t;
        }
        return pool.get(0);
    }

    // ==================== FEASIBILITY VALIDATION ====================

    private boolean isQuestFeasible(Quest quest, String islandId) {
        int level = getIslandLevel(islandId);
        if (quest.getCategory() == Quest.QuestCategory.EXPLORATION) return level >= 15;
        if (quest.getCategory() == Quest.QuestCategory.COMBAT) return level >= 5;
        return true;
    }

    // ==================== CALCULATIONS ====================

    private int calculateTarget(QuestTemplate template, int level, int players, Quest.QuestType type) {
        int base = 30;
        int mult = type == Quest.QuestType.WEEKLY ? 4 : 1;
        return Math.max(5, (base * mult) + (level / 2) + (players * 3));
    }

    private int calculateRewardXp(QuestTemplate template, int target, Quest.QuestType type) {
        int base = target * 3;
        return type == Quest.QuestType.WEEKLY ? base * 4 : base;
    }

    private int calculateRewardMoney(QuestTemplate template, int target, Quest.QuestType type) {
        int base = target * 8;
        return type == Quest.QuestType.WEEKLY ? base * 5 : base;
    }

    // ==================== TODOs COMPLETED ====================

    private int getIslandLevel(String islandId) {
        try {
            UUID owner = UUID.fromString(islandId);
            Island island = plugin.getIslandManager().getIsland(owner, World.Environment.NORMAL);
            return (island != null) ? island.getLevel() : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private int getPlayerCountOnIsland(String islandId) {
        try {
            UUID owner = UUID.fromString(islandId);
            Island island = plugin.getIslandManager().getIsland(owner, World.Environment.NORMAL);
            return (island != null) ? island.getMemberCount() : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    // ==================== CLAIM QUEST (WITH PLAYER ECONOMY + ISLAND XP) ====================

    public boolean claimQuest(String islandId, String questId, Player player) {
        if (player == null) return false;

        List<Quest> quests = islandQuests.get(islandId);
        if (quests == null || quests.isEmpty()) {
            player.sendMessage("§cNo quests found for this island.");
            return false;
        }

        for (Quest quest : quests) {
            if (quest.getId().equals(questId) && quest.isCompleted() && !quest.isClaimed()) {

                quest.setClaimed(true);

                int moneyReward = quest.getRewardMoney();
                int xpReward = quest.getRewardXp();

                // 1. Reward Player's Personal Economy
                if (moneyReward > 0) {
                    plugin.getEconomyManager().addBalance(player.getUniqueId(), moneyReward);
                }

                // 2. Reward Island XP
                if (xpReward > 0) {
                    plugin.getIslandManager().addIslandXp(player, xpReward);
                }

                // Feedback
                player.sendMessage("§a§lQuest Completed! §e" + quest.getTitle());
                if (moneyReward > 0) {
                    player.sendMessage("§a+ §e$" + moneyReward);
                }
                if (xpReward > 0) {
                    player.sendMessage("§b+ " + xpReward + " Island XP");
                }

                return true;
            }
        }

        player.sendMessage("§cThis quest has already been claimed or is not completed.");
        return false;
    }

    // ==================== QUEST TEMPLATE ====================

    private static class QuestTemplate {
        Quest.QuestCategory category;
        String title;
        String description;
        int weight;

        QuestTemplate(Quest.QuestCategory category, String title, String description, int weight) {
            this.category = category;
            this.title = title;
            this.description = description;
            this.weight = weight;
        }
    }
}