package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.quest.Quest;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quest Manager - Structured quest system with AI integration
 */
public class QuestManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, List<Quest>> questCache = new ConcurrentHashMap<>();

    public QuestManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        createQuestTable();
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupCache, 36000L, 36000L);
    }

    private void createQuestTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS quests (
                id TEXT PRIMARY KEY,
                island_id TEXT,
                type TEXT,
                category TEXT,
                title TEXT,
                description TEXT,
                target INTEGER,
                progress INTEGER DEFAULT 0,
                reward_xp INTEGER,
                reward_money INTEGER,
                completed BOOLEAN DEFAULT 0,
                created_at INTEGER,
                expires_at INTEGER
            )
            """;
        databaseManager.executeUpdate(sql);
    }

    public CompletableFuture<List<Quest>> getQuestsForIsland(String islandId) {
        List<Quest> cached = questCache.get(islandId);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return CompletableFuture.supplyAsync(() -> {
            List<Quest> quests = new ArrayList<>();
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM quests WHERE island_id = ?")) {
                stmt.setString(1, islandId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    Quest quest = new Quest(
                            rs.getString("id"),
                            rs.getString("island_id"),
                            Quest.QuestType.valueOf(rs.getString("type")),
                            Quest.QuestCategory.valueOf(rs.getString("category")),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getInt("target"),
                            rs.getInt("reward_xp"),
                            rs.getInt("reward_money")
                    );
                    quest.setProgress(rs.getInt("progress"));
                    quest.setCompleted(rs.getBoolean("completed"));
                    quests.add(quest);
                }

                questCache.put(islandId, quests);
                return quests;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load quests: " + e.getMessage());
                return new ArrayList<>();
            }
        });
    }

    public CompletableFuture<Void> saveQuest(Quest quest) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                     INSERT OR REPLACE INTO quests 
                     (id, island_id, type, category, title, description, target, progress, 
                      reward_xp, reward_money, completed, created_at, expires_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
                stmt.setString(1, quest.getId());
                stmt.setString(2, quest.getIslandId());
                stmt.setString(3, quest.getType().name());
                stmt.setString(4, quest.getCategory().name());
                stmt.setString(5, quest.getTitle());
                stmt.setString(6, quest.getDescription());
                stmt.setInt(7, quest.getTarget());
                stmt.setInt(8, quest.getProgress());
                stmt.setInt(9, quest.getRewardXp());
                stmt.setInt(10, quest.getRewardMoney());
                stmt.setBoolean(11, quest.isCompleted());
                stmt.setLong(12, quest.getCreatedAt());
                stmt.setLong(13, quest.getExpiresAt());
                stmt.executeUpdate();

                // Update cache
                questCache.computeIfAbsent(quest.getIslandId(), k -> new ArrayList<>()).add(quest);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save quest: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Boolean> completeQuest(Quest quest) {
        quest.setCompleted(true);
        return saveQuest(quest).thenApply(v -> true);
    }

    public void generateDailyQuests(String islandId) {
        // Generate 3 daily quests
        List<Quest> dailyQuests = Arrays.asList(
                new Quest(UUID.randomUUID().toString(), islandId, Quest.QuestType.DAILY,
                        Quest.QuestCategory.MINING, "Mine 100 Blocks", "Mine any 100 blocks", 100, 50, 100),
                new Quest(UUID.randomUUID().toString(), islandId, Quest.QuestType.DAILY,
                        Quest.QuestCategory.FARMING, "Harvest 50 Crops", "Harvest wheat, carrots, or potatoes", 50, 50, 100),
                new Quest(UUID.randomUUID().toString(), islandId, Quest.QuestType.DAILY,
                        Quest.QuestCategory.COMBAT, "Defeat 20 Mobs", "Defeat hostile mobs", 20, 75, 150)
        );

        for (Quest quest : dailyQuests) {
            saveQuest(quest);
        }
    }

    public void generateWeeklyQuests(String islandId) {
        List<Quest> weeklyQuests = Arrays.asList(
                new Quest(UUID.randomUUID().toString(), islandId, Quest.QuestType.WEEKLY,
                        Quest.QuestCategory.BUILDING, "Build 500 Blocks", "Place 500 blocks", 500, 200, 500),
                new Quest(UUID.randomUUID().toString(), islandId, Quest.QuestType.WEEKLY,
                        Quest.QuestCategory.TRADING, "Complete 10 Trades", "Trade with the island shop", 10, 150, 300)
        );

        for (Quest quest : weeklyQuests) {
            saveQuest(quest);
        }
    }

    private void cleanupCache() {
        if (questCache.size() > 500) questCache.clear();
    }
}
