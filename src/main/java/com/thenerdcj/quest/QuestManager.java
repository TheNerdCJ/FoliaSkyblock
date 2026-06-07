package com.thenerdcj.quest;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.ParticleTrail;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

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

    // islandId -> list of quests (daily/weekly per-island for unique progression; FIRST per-player)
    private final Map<String, List<Quest>> questsByIsland = new ConcurrentHashMap<>();

    // Per-island recent categories for down-weighting repeats in adaptive generation (ring buffer)
    private final Map<String, Deque<Quest.QuestCategory>> recentCategories = new ConcurrentHashMap<>();

    // Per-island last completion time per category for adaptive target scaling
    private final Map<String, Map<Quest.QuestCategory, Long>> lastQuestCompletion = new ConcurrentHashMap<>();

    // Per-island last reroll time for player agency (1 reroll per day per island)
    private final Map<String, Long> lastRerollTime = new ConcurrentHashMap<>();

    // Simple preferences per key (player or island) for agency: preferred categories bias generation
    private final Map<String, Set<Quest.QuestCategory>> preferredCategories = new ConcurrentHashMap<>();

    // Streak tracking per island for daily habit forming and user-friendly "continuous progression" feel (inspired by recurring dailies on popular skyblock servers)
    private final Map<String, Integer> dailyStreaks = new ConcurrentHashMap<>();
    private final Map<String, Long> lastDailyClaimDay = new ConcurrentHashMap<>(); // epoch day (millis / 86400000)

    // Reputation cache (island -> category -> rep) for Step 1 light faction/rep layer (biases generation, future unlocks)
    private final Map<String, Map<Quest.QuestCategory, Integer>> islandReputation = new ConcurrentHashMap<>();

    // Step 6: Streak freezes cache (island -> available freezes to protect streak on miss)
    private final Map<String, Integer> streakFreezes = new ConcurrentHashMap<>();

    // Step 6: Weekly theme for bias (rotates to give variety/themes, computed from epoch week)
    private String currentWeeklyTheme = "Balanced";
    private long lastThemeCheck = 0;

    // Step 6: Player stats caches for Quest Master surface (total completed, best streak, cat breakdown)
    private final Map<String, Integer> playerTotalCompleted = new ConcurrentHashMap<>();
    private final Map<String, Integer> playerBestStreak = new ConcurrentHashMap<>();
    private final Map<String, Map<Quest.QuestCategory, Integer>> playerCatCounts = new ConcurrentHashMap<>();

    // Large scale compression/optim: bound quest data per island/global to prevent mem growth on 1000+ islands.
    // Quests per island are small but map of islands can grow; trim expired/old islands periodically.
    private static final int MAX_QUEST_ISLANDS = 2000;
    private static final int MAX_QUESTS_PER_ISLAND = 20; // safety cap

    public QuestManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Future: load quests from database on startup
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
    }

    /**
     * Get all quests for a specific owner (player UUID string as key - per-player for independent/parallel quests).
     * Async to match GUI expectation. Onboarding FIRST quests are now persistent and parallel (no prior quest gate).
     */
    public CompletableFuture<List<Quest>> getQuestsForIsland(String ownerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Quest> quests = questsByIsland.get(ownerId);
            if (quests == null || quests.isEmpty()) {
                List<com.thenerdcj.quest.Quest> loaded = new ArrayList<>();

                // Load island-scoped (dailies, weeklies, and any island story)
                loaded.addAll(plugin.getDatabaseManager().loadIslandQuests(ownerId));

                // For player keys (UUID), also load per-player story/main quests for chains
                if (ownerId != null && ownerId.contains("-")) {
                    loaded.addAll(plugin.getDatabaseManager().loadPlayerQuests(ownerId));
                }

                if (!loaded.isEmpty()) {
                    quests = new ArrayList<>(loaded);
                    questsByIsland.put(ownerId, quests);
                } else {
                    quests = Collections.emptyList();
                }
            }
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

        // Load persisted daily/weekly from DB for persistence (unique per island)
        if (current.stream().noneMatch(q -> q.getType() == Quest.QuestType.DAILY)) {
            List<com.thenerdcj.quest.Quest> persisted = plugin.getDatabaseManager().loadIslandQuests(islandId);
            for (com.thenerdcj.quest.Quest p : persisted) {
                if (p.getType() == Quest.QuestType.DAILY && !current.stream().anyMatch(c -> c.getId().equals(p.getId()))) {
                    current.add(p);
                }
            }
        }

        // Clean up old/expired dailies (never touch FIRST/onboarding quests)
        current.removeIf(q -> 
            q.getType() == Quest.QuestType.DAILY && 
            (q.isCompleted() || q.isExpired() || q.isClaimed())
        );

        // Ensure we have at least 3 daily quests with unique categories for better player experience
        long dailyCount = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.DAILY)
            .count();

        Set<Quest.QuestCategory> usedDailyCategories = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.DAILY)
            .map(Quest::getCategory)
            .collect(java.util.stream.Collectors.toSet());

        for (long i = dailyCount; i < 3; i++) {
            Quest newQuest;
            int attempts = 0;
            do {
                newQuest = createRandomQuest(Quest.QuestType.DAILY, islandId);
                attempts++;
            } while (usedDailyCategories.contains(newQuest.getCategory()) && attempts < 20);
            current.add(newQuest);
            usedDailyCategories.add(newQuest.getCategory());
            recordRecentCategory(islandId, newQuest.getCategory());
        }

        // Save current daily/weekly to DB for persistence
        saveDailyWeeklyToDB(islandId, current);
    }

    /**
     * Generate (or refresh) weekly quests for the island.
     */
    public void generateWeeklyQuests(String islandId) {
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());

        // Load persisted daily/weekly from DB for persistence (unique per island)
        if (current.stream().noneMatch(q -> q.getType() == Quest.QuestType.WEEKLY)) {
            List<com.thenerdcj.quest.Quest> persisted = plugin.getDatabaseManager().loadIslandQuests(islandId);
            for (com.thenerdcj.quest.Quest p : persisted) {
                if (p.getType() == Quest.QuestType.WEEKLY && !current.stream().anyMatch(c -> c.getId().equals(p.getId()))) {
                    current.add(p);
                }
            }
        }

        // Clean up old weeklies (never touch FIRST/onboarding quests)
        current.removeIf(q -> 
            q.getType() == Quest.QuestType.WEEKLY && 
            (q.isCompleted() || q.isExpired() || q.isClaimed())
        );

        long weeklyCount = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.WEEKLY)
            .count();

        // Ensure unique categories for weeklies too
        Set<Quest.QuestCategory> usedWeeklyCategories = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.WEEKLY)
            .map(Quest::getCategory)
            .collect(java.util.stream.Collectors.toSet());

        for (long i = weeklyCount; i < 2; i++) {
            Quest newQuest;
            int attempts = 0;
            do {
                newQuest = createRandomQuest(Quest.QuestType.WEEKLY, islandId);
                attempts++;
            } while (usedWeeklyCategories.contains(newQuest.getCategory()) && attempts < 20);
            current.add(newQuest);
            usedWeeklyCategories.add(newQuest.getCategory());
            recordRecentCategory(islandId, newQuest.getCategory());
        }

        // Save current daily/weekly to DB for persistence
        saveDailyWeeklyToDB(islandId, current);
    }

    /**
     * Generate one-time early-game / onboarding "FIRST" quests.
     * Now per-player (keyed by player UUID string) so each player achieves independently and in parallel.
     * Persistent: loads saved progress/claimed from DB on generate (so survives restarts/logins).
     * No "prior quest" requirement - all 5 are available immediately and progress independently.
     * These act as the tutorial / balance for the heavy late-game systems.
     * Called on first island creation or when opening quest log for a player.
     */
    public void generateOnboardingQuests(String playerKey) {
        List<Quest> current = questsByIsland.computeIfAbsent(playerKey, k -> new ArrayList<>());

        long firstCount = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.FIRST)
            .count();

        if (firstCount > 0) {
            // Already in memory - but ensure DB state is applied (in case of partial load)
            applyPersistedProgress(playerKey, current);
            return;
        }

        // Fixed, friendly first quests - all generated at once for parallel achievement (no sequential gating)
        List<Quest> onboarding = new ArrayList<>();
        onboarding.add(createFirstQuest(
            Quest.QuestCategory.FARMING,
            "First Harvest",
            "Harvest your first crops (break fully-grown wheat, carrots, potatoes, etc.)",
            1, 35, 40
        ));
        onboarding.add(createFirstQuest(
            Quest.QuestCategory.MINING,
            "First Dig",
            "Break your first stone, ore, or dirt block on the island",
            1, 25, 30
        ));
        onboarding.add(createFirstQuest(
            Quest.QuestCategory.COMBAT,
            "First Foe",
            "Defeat your first hostile mob (zombie, skeleton, etc.)",
            1, 50, 45
        ));
        onboarding.add(createFirstQuest(
            Quest.QuestCategory.BUILDING,
            "First Steps",
            "Place blocks to expand or customize your island (5 total)",
            5, 20, 25
        ));
        onboarding.add(createFirstQuest(
            Quest.QuestCategory.CHALLENGE,
            "First Minion",
            "Deploy your first minion to help automate tasks",
            1, 60, 50
        ));

        // Load any persisted progress/claimed from DB (makes onboarding persistent across restarts)
        Map<Quest.QuestCategory, com.thenerdcj.database.DatabaseManager.QuestProgress> persisted = plugin.getDatabaseManager().loadPlayerQuestProgress(UUID.fromString(playerKey));
        for (Quest q : onboarding) {
            com.thenerdcj.database.DatabaseManager.QuestProgress p = persisted.get(q.getCategory());
            if (p != null) {
                q.setProgress(p.progress);
                q.setCompleted(p.completed);
                q.setClaimed(p.claimed);
            }
        }

        current.addAll(onboarding);

        // Note: progress is fed by EarlyGameListener (safe, anti-cheat guarded) + MinionManager hook
        // All quests are active from the start - player can work on any/all simultaneously.

        // Step 1: Also generate the guided Main Story chain (with prereqs for "I did X, now Y is available" dopamine)
        generateMainStoryQuests(playerKey);
    }

    /**
     * Step 1: Main Story guided path.
     * A light chain of quests that teach core features in sequence (minions -> farming -> combat -> trading -> bosses/challenge).
     * Uses prerequisites so later quests only become available (and visible) after earlier ones are claimed.
     * Stored per-player for individual progression (like FIRST).
     */
    public void generateMainStoryQuests(String playerKey) {
        List<Quest> current = questsByIsland.computeIfAbsent(playerKey, k -> new ArrayList<>());

        long storyCount = current.stream()
            .filter(q -> q.getQuestLine() == Quest.QuestLine.MAIN_STORY)
            .count();

        if (storyCount > 0) {
            // Already seeded; just ensure persisted claimed state is applied
            applyPersistedStoryProgress(playerKey, current);
            return;
        }

        List<Quest> story = new ArrayList<>();
        long farFuture = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);

        // Chapter 1: Minion automation (teaches minions, builds on "First Minion" onboarding)
        Quest q1 = new Quest(UUID.randomUUID().toString(),
            "§bMain Story: Automate Your Island",
            "Place and upgrade 2 minions to start passive resource generation.",
            Quest.QuestCategory.CHALLENGE, Quest.QuestType.FIRST,
            0, 2, 80, 120, false, farFuture,
            java.util.Collections.singletonList(new Quest.QuestObjective("Place or upgrade 2 minions", 2, 0)),
            Quest.QuestLine.MAIN_STORY, 1, null, false);
        q1.addExtraReward(new Quest.QuestReward(Quest.QuestReward.Type.COSMETIC_UNLOCK, "HAPPY_VILLAGER", 1));
        story.add(q1);

        // Chapter 2: Farming expansion (prereq q1)
        Quest q2 = new Quest(UUID.randomUUID().toString(),
            "§bMain Story: Expand Your Farm",
            "Harvest a larger amount of crops and sell some to build your economy.",
            Quest.QuestCategory.FARMING, Quest.QuestType.FIRST,
            0, 48, 60, 90, false, farFuture,
            java.util.Arrays.asList(
                new Quest.QuestObjective("Harvest 48 crops", 48, 0),
                new Quest.QuestObjective("Complete 3 trades/sales of crops", 3, 0)
            ),
            Quest.QuestLine.MAIN_STORY, 2, java.util.Collections.singletonList(q1.getId()), false);
        story.add(q2);

        // Chapter 3: Combat readiness
        Quest q3 = new Quest(UUID.randomUUID().toString(),
            "§bMain Story: Defend Your Island",
            "Defeat hostile mobs and improve your combat gear through practice.",
            Quest.QuestCategory.COMBAT, Quest.QuestType.FIRST,
            0, 25, 70, 100, false, farFuture,
            java.util.Collections.singletonList(new Quest.QuestObjective("Defeat 25 hostile mobs", 25, 0)),
            Quest.QuestLine.MAIN_STORY, 3, java.util.Collections.singletonList(q2.getId()), false);
        story.add(q3);

        // Chapter 4: Trading & economy
        Quest q4 = new Quest(UUID.randomUUID().toString(),
            "§bMain Story: Master Trading",
            "Engage in meaningful trades and build island wealth.",
            Quest.QuestCategory.TRADING, Quest.QuestType.FIRST,
            0, 12, 65, 110, false, farFuture,
            java.util.Arrays.asList(
                new Quest.QuestObjective("Complete 12 trades or sales", 12, 0),
                new Quest.QuestObjective("Contribute to island bank or worth", 1, 0)
            ),
            Quest.QuestLine.MAIN_STORY, 4, java.util.Collections.singletonList(q3.getId()), false);
        story.add(q4);

        // Chapter 5: Building & expansion
        Quest q5 = new Quest(UUID.randomUUID().toString(),
            "§bMain Story: Build Your Legacy",
            "Expand your island significantly with strategic building.",
            Quest.QuestCategory.BUILDING, Quest.QuestType.FIRST,
            0, 64, 55, 85, false, farFuture,
            java.util.Collections.singletonList(new Quest.QuestObjective("Place 64 blocks for expansion", 64, 0)),
            Quest.QuestLine.MAIN_STORY, 5, java.util.Collections.singletonList(q4.getId()), false);
        story.add(q5);

        // Chapter 6: Challenge / Boss intro (ties to endgame loops)
        Quest q6 = new Quest(UUID.randomUUID().toString(),
            "§bMain Story: Face Greater Challenges",
            "Take on tougher challenges and contribute to island milestones.",
            Quest.QuestCategory.CHALLENGE, Quest.QuestType.FIRST,
            0, 5, 100, 150, false, farFuture,
            java.util.Arrays.asList(
                new Quest.QuestObjective("Complete 5 challenge actions (minions, bosses, or special)", 5, 0),
                new Quest.QuestObjective("Reach a new island worth or level milestone", 1, 0)
            ),
            Quest.QuestLine.MAIN_STORY, 6, java.util.Collections.singletonList(q5.getId()), false);
        story.add(q6);

        // Load any persisted claimed state for these story quests (use player_quests table)
        List<com.thenerdcj.quest.Quest> persisted = plugin.getDatabaseManager().loadPlayerQuests(playerKey);
        java.util.Map<String, com.thenerdcj.quest.Quest> persistedById = new java.util.HashMap<>();
        for (com.thenerdcj.quest.Quest p : persisted) {
            if (p.getQuestLine() == Quest.QuestLine.MAIN_STORY) persistedById.put(p.getId(), p);
        }
        for (Quest q : story) {
            com.thenerdcj.quest.Quest p = persistedById.get(q.getId());
            if (p != null) {
                q.setProgress(p.getProgress());
                q.setCompleted(p.isCompleted());
                q.setClaimed(p.isClaimed());
            }
        }

        current.addAll(story);
        savePlayerStoryToDB(playerKey, current);

        // Step 5: Add more side quests for volume (dozens of flavorful one-time or long-cooldown side quests)
        generateSideQuests(playerKey);
    }

    /**
     * Step 5: Side quests for volume and flavor.
     * Static flavorful side quests (teach more features, optional, with prereqs from main story).
     * Stored as MAIN_STORY or SIDE line, one-time per player.
     */
    public void generateSideQuests(String playerKey) {
        List<Quest> current = questsByIsland.computeIfAbsent(playerKey, k -> new ArrayList<>());

        long sideCount = current.stream().filter(q -> q.getQuestLine() == Quest.QuestLine.SIDE).count();
        if (sideCount > 0) return; // already generated

        List<Quest> sides = new ArrayList<>();
        long farFuture = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);

        // Example side quests (expandable pool)
        sides.add(new Quest(UUID.randomUUID().toString(), "§dSide: Friendly Neighbors",
            "Place 10 unique blocks to make your island welcoming.",
            Quest.QuestCategory.BUILDING, Quest.QuestType.FIRST, 0, 10, 40, 60, false, farFuture,
            java.util.Collections.singletonList(new Quest.QuestObjective("Place 10 different block types", 10, 0)),
            Quest.QuestLine.SIDE, 10, null, false)); // no prereq for accessibility

        sides.add(new Quest(UUID.randomUUID().toString(), "§dSide: Pet Lover",
            "Interact with or level a pet companion.",
            Quest.QuestCategory.EXPLORATION, Quest.QuestType.FIRST, 0, 5, 50, 70, false, farFuture,
            java.util.Collections.singletonList(new Quest.QuestObjective("Pet interactions or levels", 5, 0)),
            Quest.QuestLine.SIDE, 11, null, false));

        sides.add(new Quest(UUID.randomUUID().toString(), "§dSide: Trade Baron",
            "Complete trades across different categories.",
            Quest.QuestCategory.TRADING, Quest.QuestType.FIRST, 0, 8, 45, 65, false, farFuture,
            java.util.Collections.singletonList(new Quest.QuestObjective("Diverse trades", 8, 0)),
            Quest.QuestLine.SIDE, 12, null, false));

        current.addAll(sides);
        savePlayerStoryToDB(playerKey, current);
    }

    private void applyPersistedStoryProgress(String playerKey, List<Quest> quests) {
        try {
            List<com.thenerdcj.quest.Quest> persisted = plugin.getDatabaseManager().loadPlayerQuests(playerKey);
            java.util.Map<String, com.thenerdcj.quest.Quest> byId = new java.util.HashMap<>();
            for (com.thenerdcj.quest.Quest p : persisted) byId.put(p.getId(), p);
            for (Quest q : quests) {
                if (q.getQuestLine() != Quest.QuestLine.MAIN_STORY) continue;
                com.thenerdcj.quest.Quest p = byId.get(q.getId());
                if (p != null) {
                    q.setProgress(p.getProgress());
                    q.setCompleted(p.isCompleted());
                    q.setClaimed(p.isClaimed());
                }
            }
        } catch (Exception ignored) {}
    }

    private void savePlayerStoryToDB(String playerKey, List<Quest> current) {
        if (plugin.getDatabaseManager() != null && current != null) {
            List<Quest> toSave = new ArrayList<>();
            for (Quest q : current) {
                if (q.getQuestLine() == Quest.QuestLine.MAIN_STORY || q.getQuestLine() == Quest.QuestLine.SIDE) {
                    toSave.add(q);
                }
            }
            plugin.getDatabaseManager().savePlayerQuests(playerKey, toSave);
        }
    }

    private void applyPersistedProgress(String playerKey, List<Quest> quests) {
        try {
            Map<Quest.QuestCategory, com.thenerdcj.database.DatabaseManager.QuestProgress> persisted = plugin.getDatabaseManager().loadPlayerQuestProgress(UUID.fromString(playerKey));
            for (Quest q : quests) {
                if (q.getType() != Quest.QuestType.FIRST) continue;
                com.thenerdcj.database.DatabaseManager.QuestProgress p = persisted.get(q.getCategory());
                if (p != null) {
                    q.setProgress(p.progress);
                    q.setCompleted(p.completed);
                    q.setClaimed(p.claimed);
                }
            }
        } catch (Exception ignored) {}
    }

    private Quest createFirstQuest(Quest.QuestCategory category, String title, String description,
                                   int target, int rewardXp, int rewardMoney) {
        long farFuture = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000); // never expires
        return new Quest(
            UUID.randomUUID().toString(),
            "§aOnboarding: " + title,
            description,
            category,
            Quest.QuestType.FIRST,
            0,
            target,
            rewardXp,
            rewardMoney,
            false,
            farFuture
        );
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
                if (quest.getType() != Quest.QuestType.FIRST) {
                    plugin.getLogger().info("[QuestAnalytics] Claimed " + quest.getType() + "/" + quest.getCategory() + " for key=" + islandId);
                }

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

                // Step 3: Grant extra typed rewards (cosmetics, rep already handled for story, pending items, etc.)
                for (Quest.QuestReward rw : quest.getExtraRewards()) {
                    try {
                        switch (rw.type) {
                            case COSMETIC_UNLOCK:
                                if (rw.data != null && plugin.getParticleTrailManager() != null) {
                                    // Example: map simple names to trails
                                    if (rw.data.contains("VILLAGER") || rw.data.contains("HAPPY")) {
                                        plugin.getParticleTrailManager().unlockTrail(player, ParticleTrail.HAPPY_VILLAGER);
                                        player.sendMessage("§d§lReward: §fHappy Villager trail unlocked!");
                                    }
                                }
                                break;
                            case REPUTATION:
                                if (island != null && rw.data != null) {
                                    try {
                                        Quest.QuestCategory cat = Quest.QuestCategory.valueOf(rw.data);
                                        plugin.getDatabaseManager().addIslandReputation(island.getId(), cat, rw.amount);
                                        player.sendMessage("§b+" + rw.amount + " " + rw.data + " reputation!");
                                    } catch (Exception ignored) {}
                                }
                                break;
                            case PENDING_ITEM:
                                // Future: push to pending items system. For now message.
                                player.sendMessage("§ePending reward: " + rw.getDescription() + " (claimable later)");
                                break;
                            default:
                                break;
                        }
                    } catch (Exception ignored) {}
                }

                // Early game / onboarding special rewards for FIRST quests (light Play-to-Win onboarding)
                if (quest.getType() == Quest.QuestType.FIRST) {
                    player.sendMessage("§d§lOnboarding Milestone! §7Thank you for taking your first steps.");
                    // Grant a free low-tier cosmetic trail (starter reward, normally prestige/slayer gated)
                    if (plugin.getParticleTrailManager() != null) {
                        boolean granted = plugin.getParticleTrailManager().unlockTrail(player, ParticleTrail.HAPPY_VILLAGER);
                        if (granted) {
                            player.sendMessage("§a§lWelcome Reward: §7Happy Villager trail unlocked (free for new players)!");
                        }
                    }
                    // Small extra island XP synergy (via manager for party balance)
                    plugin.getIslandManager().addIslandXp(player, 25);
                    // Light personal economy nudge (new player starter balance via small grant - Play-to-Win onboarding)
                    plugin.getEconomyManager().addPlayerBalance(player.getUniqueId(), 75.0);

                    // Persist claimed state for FIRST quest
                    try {
                        plugin.getDatabaseManager().savePlayerQuestProgress(
                            player.getUniqueId(), quest.getCategory(), quest.getProgress(), true, true);
                    } catch (Exception ignored) {}
                }

                // Play sound is handled in GUI

                // Save for daily/weekly persistence per island
                if (quest.getType() == Quest.QuestType.DAILY || quest.getType() == Quest.QuestType.WEEKLY) {
                    List<Quest> list = questsByIsland.get(islandId);
                    if (list != null) {
                        saveDailyWeeklyToDB(islandId, list);
                    }
                    recordQuestCompletion(islandId, quest.getCategory());
                }

                // Streak + richer rewards / notifications for daily (user friendly habit + continuous island leveling)
                if (quest.getType() == Quest.QuestType.DAILY) {
                    updateAndAwardDailyStreak(islandId, player);
                }

                if (quest.getType() == Quest.QuestType.WEEKLY) {
                    // Island-wide milestone reward for continuous progression (inspired by weekly chains)
                    plugin.getIslandManager().addIslandXp(player, 50);
                    player.sendMessage("§6§lWeekly Progression Boost! §7+50 Island XP helping level the island further.");
                    // Light cosmetic nudge (PtW safe)
                    if (plugin.getParticleTrailManager() != null && ThreadLocalRandom.current().nextInt(3) == 0) {
                        plugin.getParticleTrailManager().unlockTrail(player, com.thenerdcj.cosmetic.ParticleTrail.HAPPY_VILLAGER);
                    }
                }

                // Send per-objective summary on claim for complex quests (UX win)
                if (quest.hasMultipleObjectives()) {
                    player.sendMessage("§7Objectives completed: §a" + quest.getCompletedObjectiveCount() + "§7/" + quest.getObjectives().size());
                }

                // Step 1: Award light reputation for Main Story claims (island scoped) + persist story state
                if (quest.getQuestLine() == Quest.QuestLine.MAIN_STORY) {
                    Island isl = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                    if (isl != null) {
                        plugin.getDatabaseManager().addIslandReputation(isl.getId(), quest.getCategory(), 5);
                        // Refresh cache
                        islandReputation.remove(isl.getId());
                    }
                    // Persist the story quest claimed state
                    List<Quest> list = questsByIsland.get(islandId); // may be playerKey
                    if (list != null) savePlayerStoryToDB(islandId, list);
                }

                // Step 4: Record to player quest history for "Quest Master" log and discovery feel (per-player)
                String histPlayer = player.getUniqueId().toString();
                long now = System.currentTimeMillis();
                plugin.getDatabaseManager().savePlayerQuestHistory(
                    histPlayer,
                    quest.getId(),
                    quest.getTitle(),
                    quest.getCategory().name(),
                    quest.getQuestLine() != null ? quest.getQuestLine().name() : "ONBOARDING",
                    now
                );

                // Step 6: Update player-facing Quest Master stats + analytics
                if (quest.getType() == Quest.QuestType.FIRST || quest.getType() == Quest.QuestType.DAILY || quest.getType() == Quest.QuestType.WEEKLY) {
                    loadPlayerStatsIfNeeded(histPlayer);
                    int newTotal = playerTotalCompleted.getOrDefault(histPlayer, 0) + 1;
                    playerTotalCompleted.put(histPlayer, newTotal);
                    int streakForBest = (quest.getType() == Quest.QuestType.DAILY) ? dailyStreaks.getOrDefault(islandId != null ? islandId : histPlayer, 0) : 0;
                    int newBest = Math.max(playerBestStreak.getOrDefault(histPlayer, 0), streakForBest);
                    playerBestStreak.put(histPlayer, newBest);
                    Map<Quest.QuestCategory, Integer> cats = playerCatCounts.computeIfAbsent(histPlayer, k -> new EnumMap<>(Quest.QuestCategory.class));
                    cats.put(quest.getCategory(), cats.getOrDefault(quest.getCategory(), 0) + 1);
                    // Persist
                    String catStr = "";
                    for (Map.Entry<Quest.QuestCategory, Integer> e : cats.entrySet()) {
                        if (!catStr.isEmpty()) catStr += ";";
                        catStr += e.getKey().name() + ":" + e.getValue();
                    }
                    plugin.getDatabaseManager().savePlayerQuestStats(histPlayer, newTotal, newBest, catStr);
                    plugin.getDatabaseManager().updatePlayerQuestStatsOnClaim(histPlayer, quest.getCategory(), streakForBest); // legacy compat
                }

                // Step 6: Extended analytics for tuning
                if (quest.getType() != Quest.QuestType.FIRST) {
                    plugin.getLogger().info("[QuestAnalytics] Claimed " + quest.getType() + "/" + quest.getCategory() + " line=" + quest.getQuestLine() + " streak=" + getDailyStreak(islandId) + " for key=" + islandId);
                }

                return true;
            }
        }
        return false;
    }

    /**
     * Optional helper: Add progress to matching quests (call this from your listeners)
     * Supports per-player keys (UUID for FIRST/onboarding parallel) + per-island for d/w.
     * ENHANCED: when called with a player UUID, we ALSO credit the player's current island's quest list
     * so daily/weekly per-island quests (continuous leveling) actually receive progress from normal play.
     * Objectives inside quests are driven for the new multi-objective complexity.
     */
    public void addProgressToIsland(String ownerId, Quest.QuestCategory category, int amount) {
        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();

        // Apply to the explicitly passed key's quests (works for islandId or playerKey)
        applyProgressToKey(ownerId, category, amount);

        // Dual-credit: if this looks like a player (has dashes), also resolve their island and credit d/w quests
        if (ownerId != null && ownerId.contains("-")) {
            try {
                UUID pu = UUID.fromString(ownerId);
                // Resolve island on main (listeners are main thread)
                if (plugin.getIslandManager() != null) {
                    // Try multiple envs? Use primary (normal is overworld for islands)
                    Island island = plugin.getIslandManager().getIsland(pu, org.bukkit.World.Environment.NORMAL);
                    if (island == null) {
                        island = plugin.getIslandManager().getIsland(pu, org.bukkit.World.Environment.THE_END);
                    }
                    if (island != null && island.getId() != null && !island.getId().equals(ownerId)) {
                        applyProgressToKey(island.getId(), category, amount);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[QuestManager] PROFILE: addProgressToIsland took " + (ns / 1_000_000.0) + " ms (early game/quest hot path for large scale)");
        }
    }

    private void applyProgressToKey(String key, Quest.QuestCategory category, int amount) {
        List<Quest> quests = questsByIsland.get(key);
        if (quests == null) return;

        boolean savedFirst = false;
        boolean anyAdvanced = false;
        for (Quest quest : quests) {
            if (quest.getCategory() == category && !quest.isCompleted() && !quest.isExpired() && !quest.isClaimed()) {
                // Use objective-aware advance for complex quests
                boolean hadObjComplete = false;
                if (quest.hasMultipleObjectives()) {
                    for (Quest.QuestObjective obj : quest.getObjectives()) {
                        if (!obj.isCompleted()) {
                            int before = obj.getProgress();
                            quest.addObjectiveProgress(Math.max(1, amount));
                            if (obj.isCompleted() && before < obj.getTarget()) {
                                hadObjComplete = true;
                                // Step 4 feedback
                                notifyObjectiveComplete(key, quest, obj);
                            }
                            break;
                        }
                    }
                } else {
                    quest.addProgress(amount);
                }
                anyAdvanced = true;

                // For persistent onboarding FIRST quests, save progress immediately (lightweight)
                if (quest.getType() == Quest.QuestType.FIRST && !savedFirst) {
                    try {
                        plugin.getDatabaseManager().savePlayerQuestProgress(
                            UUID.fromString(key), category, quest.getProgress(), quest.isCompleted(), quest.isClaimed());
                        savedFirst = true;
                    } catch (Exception ignored) {}
                }

                // Per-objective analytics (more granular for tuning)
                if (quest.isCompleted() && quest.getType() != Quest.QuestType.FIRST) {
                    plugin.getLogger().info("[QuestAnalytics] Completed " + quest.getType() + "/" + category + " (multi-obj=" + quest.hasMultipleObjectives() + ") for " + key);
                } else if (quest.getType() != Quest.QuestType.FIRST) {
                    // Light log of objective level progress occasionally
                    if (quest.hasMultipleObjectives() && ThreadLocalRandom.current().nextInt(5) == 0) {
                        plugin.getLogger().info("[QuestAnalytics] Progress " + quest.getType() + "/" + category + " obj " + quest.getCompletedObjectiveCount() + "/" + quest.getObjectives().size() + " for " + key);
                    }
                }
            }
        }

        // Save daily/weekly progress to DB for per-island persistence (only if changed)
        if (anyAdvanced) {
            List<Quest> list = questsByIsland.get(key);
            if (list != null) {
                boolean hasDailyWeekly = list.stream().anyMatch(q -> q.getType() == Quest.QuestType.DAILY || q.getType() == Quest.QuestType.WEEKLY);
                if (hasDailyWeekly) {
                    saveDailyWeeklyToDB(key, list);
                }
            }
        }
    }

    // ==================== PRIVATE HELPERS ====================

    private Quest createRandomQuest(Quest.QuestType type, String islandId) {
        Random random = ThreadLocalRandom.current();
        Quest.QuestCategory category;
        if (type == Quest.QuestType.FIRST) {
            // Onboarding uses dedicated fixed list for parallel availability
            Quest.QuestCategory[] categories = Quest.QuestCategory.values();
            category = categories[random.nextInt(categories.length)];
        } else {
            category = selectCategoryForIsland(islandId, type);
        }

        int baseTarget = switch (type) {
            case DAILY -> 8 + random.nextInt(25);
            case WEEKLY -> 40 + random.nextInt(80);
            case FIRST -> 1; // onboarding handled by dedicated creator (not reached via random)
        };

        // Adjust target per category for balance, with intelligent scaling
        int target = adjustTargetForCategory(category, baseTarget, type, islandId);

        int rewardXp = Math.max(20, target * (type == Quest.QuestType.DAILY ? 3 : 2));
        int rewardMoney = Math.max(50, target * (type == Quest.QuestType.DAILY ? 8 : 6));

        long duration = type == Quest.QuestType.DAILY 
            ? 24L * 60 * 60 * 1000 
            : 7L * 24 * 60 * 60 * 1000;
        long expiryTime = System.currentTimeMillis() + duration;

        String title = generateTitle(category, type);

        // COMPLEX QUESTS: generate 1-3 related objectives for this category (multi-objective like popular skyblock daily/weekly systems)
        List<Quest.QuestObjective> objectives = generateObjectivesForCategory(category, type, target, islandId, random);

        // Summary description for header (GUI will show per-obj details)
        String description = generateSummaryDescription(category, type, objectives);

        // For legacy top-level progress we still store a representative target (sum of obj targets or original)
        int summaryTarget = objectives.isEmpty() ? target : Math.max(1, objectives.stream().mapToInt(Quest.QuestObjective::getTarget).sum() / Math.max(1, objectives.size()));

        Quest q = new Quest(
            UUID.randomUUID().toString(),
            title,
            description,
            category,
            type,
            0,
            summaryTarget,
            rewardXp,
            rewardMoney,
            false,
            expiryTime,
            objectives
        );
        return q;
    }

    /**
     * Generate 1-3 related objectives for a category (core of "more complex" quests).
     * References real skyblock patterns: multiple sub-tasks per daily (gather + craft + deliver style), visual progress per line.
     * Keeps them in the same broad category for simple listener integration while giving depth.
     */
    private List<Quest.QuestObjective> generateObjectivesForCategory(Quest.QuestCategory cat, Quest.QuestType type, int base, String islandId, Random random) {
        List<Quest.QuestObjective> objs = new ArrayList<>();
        int scale = Math.max(3, base);

        switch (cat) {
            case FARMING -> {
                objs.add(new Quest.QuestObjective("Harvest " + scale + " crops (wheat, carrots, etc.)", scale, 0));
                if (type == Quest.QuestType.WEEKLY || random.nextBoolean()) {
                    int plantT = Math.max(4, scale / 2);
                    objs.add(new Quest.QuestObjective("Plant or tend " + plantT + " crop blocks", plantT, 0));
                }
                if (type == Quest.QuestType.DAILY && random.nextFloat() < 0.6f) {
                    int sellT = Math.max(3, scale / 3);
                    objs.add(new Quest.QuestObjective("Sell or trade " + sellT + " farming goods (bazaar/NPC)", sellT, 0));
                }
            }
            case MINING -> {
                objs.add(new Quest.QuestObjective("Mine " + scale + " stone, ore or deepslate blocks", scale, 0));
                if (type != Quest.QuestType.DAILY || random.nextBoolean()) {
                    int placeT = Math.max(3, scale / 3);
                    objs.add(new Quest.QuestObjective("Place " + placeT + " blocks (building/mining support)", placeT, 0));
                }
            }
            case COMBAT -> {
                objs.add(new Quest.QuestObjective("Defeat " + Math.max(3, scale / 2) + " hostile mobs", Math.max(3, scale / 2), 0));
                if (type == Quest.QuestType.WEEKLY) {
                    objs.add(new Quest.QuestObjective("Survive or complete " + Math.max(1, scale / 8) + " higher risk fights", Math.max(1, scale / 8), 0));
                }
            }
            case BUILDING -> {
                objs.add(new Quest.QuestObjective("Place " + scale + " blocks to expand your island", scale, 0));
                if (random.nextBoolean()) {
                    objs.add(new Quest.QuestObjective("Craft or arrange structures (place in patterns)", Math.max(2, scale / 4), 0));
                }
            }
            case EXPLORATION -> {
                objs.add(new Quest.QuestObjective("Travel/explore " + Math.max(5, scale / 2) + " distance on or around island", Math.max(5, scale / 2), 0));
                objs.add(new Quest.QuestObjective("Visit or interact with " + Math.max(1, scale / 6) + " new areas/structures", Math.max(1, scale / 6), 0));
            }
            case TRADING -> {
                objs.add(new Quest.QuestObjective("Complete " + Math.max(2, scale / 3) + " trades or shop/villager interactions", Math.max(2, scale / 3), 0));
                if (type == Quest.QuestType.WEEKLY) {
                    objs.add(new Quest.QuestObjective("Deal in higher value trades (bazaar or NPC)", Math.max(1, scale / 5), 0));
                }
            }
            case CHALLENGE -> {
                // Step 2: more specific commission-style for minions, worth, bosses
                objs.add(new Quest.QuestObjective("Complete " + Math.max(1, scale / 2) + " special actions (minions, events, bosses)", Math.max(1, scale / 2), 0));
                objs.add(new Quest.QuestObjective("Produce resources from " + Math.max(1, scale / 4) + " active minions", Math.max(1, scale / 4), 0));
                if (type == Quest.QuestType.WEEKLY || random.nextBoolean()) {
                    objs.add(new Quest.QuestObjective("Contribute to island worth or level milestone", 1, 0));
                }
            }
            default -> {
                objs.add(new Quest.QuestObjective("Complete " + scale + " actions in this category", scale, 0));
            }
        }
        // Cap at 3 for UX (don't overwhelm log)
        while (objs.size() > 3) objs.remove(objs.size() - 1);
        if (objs.isEmpty()) {
            objs.add(new Quest.QuestObjective("Complete " + scale + " actions", scale, 0));
        }
        return objs;
    }

    private String generateSummaryDescription(Quest.QuestCategory category, Quest.QuestType type, List<Quest.QuestObjective> objectives) {
        if (objectives.size() <= 1) {
            return generateDescription(category, objectives.isEmpty() ? 10 : objectives.get(0).getTarget());
        }
        return "Complete all " + objectives.size() + " objectives for this " + (type == Quest.QuestType.DAILY ? "daily" : "weekly") + " " + category.name().toLowerCase() + " challenge";
    }

    private int adjustTargetForCategory(Quest.QuestCategory category, int baseTarget, Quest.QuestType type, String islandId) {
        int target = switch (category) {
            case MINING, FARMING -> baseTarget;
            case COMBAT -> Math.max(5, baseTarget / 2);
            case BUILDING -> baseTarget + 10;
            case EXPLORATION -> Math.max(5, baseTarget / 3);
            case TRADING -> Math.max(3, baseTarget / 4);
            case CHALLENGE -> baseTarget;
            default -> baseTarget;
        };

        // Intelligent scaling based on island level / prestige / effective radius (base on island data)
        // and previous completion speed for this category (faster previous -> slightly harder next)
        if (islandId != null) {
            try {
                // Use island level from worth or manager if available; fallback to 1
                int islandLevel = 1;
                if (plugin.getIslandWorthManager() != null) {
                    // For island, try to get a representative level (e.g. owner's or average)
                    // Simplified: use a base scale; enhance with actual island.getLevel() if exposed
                    islandLevel = Math.max(1, 1 + (int)(Math.log10(Math.max(1, plugin.getIslandWorthManager().getCachedWorthLevel(null))) )); // placeholder
                }
                target = (int) (target * (1.0 + (islandLevel - 1) * 0.08)); // ~8% harder per level band

                // Scale by previous completion time for this category (adaptive difficulty)
                Map<Quest.QuestCategory, Long> times = lastQuestCompletion.get(islandId);
                if (times != null && times.containsKey(category)) {
                    long lastComplete = times.get(category);
                    long timeSince = System.currentTimeMillis() - lastComplete;
                    if (type == Quest.QuestType.DAILY && timeSince < 12L * 60 * 60 * 1000) { // fast <12h
                        target = (int) (target * 1.25);
                    } else if (type == Quest.QuestType.WEEKLY && timeSince < 3L * 24 * 60 * 60 * 1000) {
                        target = (int) (target * 1.2);
                    }
                }
            } catch (Exception ignored) {}
        }
        return Math.max(1, target);
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

    /**
     * Bounded cleanup for questsByIsland CHM (large scale server compression for 100s-1000+ islands).
     * Trims map size, per-island quest lists to MAX.
     * Periodic Folia task + can be called on island delete/reset.
     * Per IMPROVEMENTS "review/bound all CHM", "more CHM bounds in all managers", "compression/optimization suggestions for large scale servers".
     */
    private void cleanupCaches() {
        if (questsByIsland.size() > MAX_QUEST_ISLANDS) {
            java.util.Iterator<String> it = questsByIsland.keySet().iterator();
            int toRemove = questsByIsland.size() - (MAX_QUEST_ISLANDS - 100);
            while (it.hasNext() && toRemove > 0) {
                it.next();
                it.remove();
                toRemove--;
            }
        }
        for (java.util.List<Quest> list : questsByIsland.values()) {
            if (list.size() > MAX_QUESTS_PER_ISLAND) {
                // trim excess (keep recent)
                while (list.size() > MAX_QUESTS_PER_ISLAND) {
                    list.remove(0);
                }
            }
        }
    }

    private void saveDailyWeeklyToDB(String islandId, List<Quest> current) {
        if (plugin.getDatabaseManager() != null && current != null) {
            List<Quest> toSave = new ArrayList<>();
            for (Quest q : current) {
                if (q.getType() == Quest.QuestType.DAILY || q.getType() == Quest.QuestType.WEEKLY) {
                    toSave.add(q);
                }
            }
            plugin.getDatabaseManager().saveIslandQuests(islandId, toSave);
        }
    }

    private void loadReputationIfNeeded(String islandId) {
        if (islandId == null || islandReputation.containsKey(islandId)) return;
        try {
            java.util.Map<Quest.QuestCategory, Integer> reps = plugin.getDatabaseManager().loadIslandReputation(islandId);
            islandReputation.put(islandId, reps);
        } catch (Exception ignored) {
            islandReputation.put(islandId, new EnumMap<>(Quest.QuestCategory.class));
        }
    }

    public int getReputation(String islandId, Quest.QuestCategory cat) {
        if (islandId == null || cat == null) return 0;
        loadReputationIfNeeded(islandId);
        Map<Quest.QuestCategory, Integer> reps = islandReputation.getOrDefault(islandId, Collections.emptyMap());
        return reps.getOrDefault(cat, 0);
    }

    private void loadStreakFreezesIfNeeded(String islandId) {
        if (islandId == null || streakFreezes.containsKey(islandId)) return;
        try {
            int f = plugin.getDatabaseManager().loadIslandStreakFreezes(islandId);
            streakFreezes.put(islandId, f);
        } catch (Exception ignored) {
            streakFreezes.put(islandId, 0);
        }
    }

    public int getStreakFreezes(String islandId) {
        if (islandId == null) return 0;
        loadStreakFreezesIfNeeded(islandId);
        return streakFreezes.getOrDefault(islandId, 0);
    }

    private void loadPlayerStatsIfNeeded(String playerKey) {
        if (playerKey == null || playerTotalCompleted.containsKey(playerKey)) return;
        try {
            int[] stats = plugin.getDatabaseManager().loadPlayerQuestStats(playerKey);
            playerTotalCompleted.put(playerKey, stats[0]);
            playerBestStreak.put(playerKey, stats[1]);
            String cats = plugin.getDatabaseManager().loadPlayerCatCounts(playerKey);
            Map<Quest.QuestCategory, Integer> catMap = new EnumMap<>(Quest.QuestCategory.class);
            if (cats != null && !cats.isEmpty()) {
                for (String p : cats.split(";")) {
                    String[] kv = p.split(":");
                    if (kv.length == 2) {
                        try { catMap.put(Quest.QuestCategory.valueOf(kv[0]), Integer.parseInt(kv[1])); } catch (Exception ignored) {}
                    }
                }
            }
            playerCatCounts.put(playerKey, catMap);
        } catch (Exception ignored) {
            playerTotalCompleted.put(playerKey, 0);
            playerBestStreak.put(playerKey, 0);
            playerCatCounts.put(playerKey, new EnumMap<>(Quest.QuestCategory.class));
        }
    }

    public int getTotalQuestsCompleted(String playerKey) {
        if (playerKey == null) return 0;
        loadPlayerStatsIfNeeded(playerKey);
        return playerTotalCompleted.getOrDefault(playerKey, 0);
    }

    public int getBestStreak(String playerKey) {
        if (playerKey == null) return 0;
        loadPlayerStatsIfNeeded(playerKey);
        return playerBestStreak.getOrDefault(playerKey, 0);
    }

    public Map<Quest.QuestCategory, Integer> getCategoryBreakdown(String playerKey) {
        if (playerKey == null) return Collections.emptyMap();
        loadPlayerStatsIfNeeded(playerKey);
        return new EnumMap<>(playerCatCounts.getOrDefault(playerKey, new EnumMap<>(Quest.QuestCategory.class)));
    }

    public String getQuestMasterStats(String playerKey) {
        int total = getTotalQuestsCompleted(playerKey);
        int best = getBestStreak(playerKey);
        Map<Quest.QuestCategory, Integer> cats = getCategoryBreakdown(playerKey);
        StringBuilder sb = new StringBuilder("§6Quest Master: §f" + total + " completed §7| Best streak §e" + best);
        if (!cats.isEmpty()) {
            sb.append(" §7| Cats: ");
            for (Map.Entry<Quest.QuestCategory, Integer> e : cats.entrySet()) {
                sb.append(e.getKey().name().substring(0,3)).append(":").append(e.getValue()).append(" ");
            }
        }
        return sb.toString();
    }

    /**
     * Lightweight adaptive category selection for daily/weekly.
     * - Down-weights recently used categories (last 5-10) using ring buffer.
     * - Base weights + recent history for variety.
     * - TODO: integrate IslandWorthManager per-category worth, PlayerSkillManager levels,
     *   recent EarlyGame/Skill/Minion activity for activity bias (e.g. boost low farming).
     */
    private Quest.QuestCategory selectCategoryForIsland(String islandId, Quest.QuestType type) {
        if (islandId == null) {
            Quest.QuestCategory[] cats = Quest.QuestCategory.values();
            return cats[ThreadLocalRandom.current().nextInt(cats.length)];
        }
        // Step 6: Compute/apply weekly theme (rotates for variety and "theme weeks")
        updateWeeklyTheme();
        Deque<Quest.QuestCategory> recent = recentCategories.computeIfAbsent(islandId, k -> new LinkedList<>());
        Map<Quest.QuestCategory, Integer> weights = new EnumMap<>(Quest.QuestCategory.class);
        for (Quest.QuestCategory c : Quest.QuestCategory.values()) {
            weights.put(c, 12); // base
        }
        // down-weight repeats
        for (Quest.QuestCategory c : recent) {
            weights.put(c, Math.max(2, weights.get(c) - 3));
        }
        // Boost preferred categories (player or island prefs for agency)
        Set<Quest.QuestCategory> prefs = preferredCategories.getOrDefault(islandId, Collections.emptySet());
        for (Quest.QuestCategory c : prefs) {
            weights.put(c, weights.get(c) + 5);
        }

        // Step 1: Reputation bias (higher rep in a category makes quests in that cat more likely / "better" dailies feel)
        loadReputationIfNeeded(islandId);
        Map<Quest.QuestCategory, Integer> reps = islandReputation.getOrDefault(islandId, Collections.emptyMap());
        for (Quest.QuestCategory c : Quest.QuestCategory.values()) {
            int r = reps.getOrDefault(c, 0);
            if (r > 0) {
                weights.put(c, weights.get(c) + (r / 4)); // light bias
            }
        }

        // Step 6: Weekly theme bias (e.g. "Farming" week boosts FARMING)
        Quest.QuestCategory themeCat = getThemeCategory();
        if (themeCat != null) {
            weights.put(themeCat, weights.get(themeCat) + 8);
        }

        // Step 6: Pity system - if a cat has been heavily used recently (flood), bias away strongly
        Map<Quest.QuestCategory, Integer> recentCount = new EnumMap<>(Quest.QuestCategory.class);
        for (Quest.QuestCategory c : recent) {
            recentCount.put(c, recentCount.getOrDefault(c, 0) + 1);
        }
        for (Map.Entry<Quest.QuestCategory, Integer> e : recentCount.entrySet()) {
            if (e.getValue() >= 4) { // pity threshold
                weights.put(e.getKey(), Math.max(1, weights.get(e.getKey()) / 3));
            }
        }

        // TODO: activity bias from island data / skills
        int total = 0;
        for (int w : weights.values()) total += w;
        if (total <= 0) total = 1;
        int r = ThreadLocalRandom.current().nextInt(total);
        int sum = 0;
        for (Map.Entry<Quest.QuestCategory, Integer> e : weights.entrySet()) {
            sum += e.getValue();
            if (r < sum) return e.getKey();
        }
        return Quest.QuestCategory.values()[ThreadLocalRandom.current().nextInt(Quest.QuestCategory.values().length)];
    }

    private void updateWeeklyTheme() {
        long now = System.currentTimeMillis();
        if (now - lastThemeCheck < 24 * 60 * 60 * 1000) return; // check daily max
        lastThemeCheck = now;
        // Simple week-based rotation (7 themes)
        String[] themes = {"Farming", "Mining", "Combat", "Building", "Trading", "Challenge", "Exploration", "Balanced"};
        int week = (int) ((now / (7L * 24 * 60 * 60 * 1000)) % themes.length);
        currentWeeklyTheme = themes[week];
        plugin.getLogger().info("[QuestAnalytics] Weekly theme set to: " + currentWeeklyTheme);
    }

    private Quest.QuestCategory getThemeCategory() {
        if ("Balanced".equals(currentWeeklyTheme)) return null;
        try {
            return Quest.QuestCategory.valueOf(currentWeeklyTheme.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private void recordRecentCategory(String islandId, Quest.QuestCategory cat) {
        if (islandId == null) return;
        Deque<Quest.QuestCategory> recent = recentCategories.computeIfAbsent(islandId, k -> new LinkedList<>());
        recent.addLast(cat);
        if (recent.size() > 10) recent.removeFirst();
    }

    private void recordQuestCompletion(String islandId, Quest.QuestCategory cat) {
        if (islandId == null) return;
        lastQuestCompletion.computeIfAbsent(islandId, k -> new EnumMap<>(Quest.QuestCategory.class))
            .put(cat, System.currentTimeMillis());
    }

    /**
     * Player agency: set preferred categories (bias generation for daily/weekly).
     * Stored per key (player uuid or island id). Server-side and auditable.
     * Example: /quest prefs FARMING true
     */
    public void setCategoryPreference(String key, Quest.QuestCategory cat, boolean prefer) {
        Set<Quest.QuestCategory> prefs = preferredCategories.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        if (prefer) {
            prefs.add(cat);
        } else {
            prefs.remove(cat);
        }
    }

    public Set<Quest.QuestCategory> getCategoryPreferences(String key) {
        return preferredCategories.getOrDefault(key, Collections.emptySet());
    }

    /**
     * Player agency: reroll one daily/weekly per day per island (small cost, cooldown).
     * Fully server-side and auditable.
     */
    public boolean rerollDailyWeeklyQuest(String islandId, String questId, Player player) {
        List<Quest> current = questsByIsland.get(islandId);
        if (current == null) return false;

        Quest toReroll = null;
        for (Quest q : current) {
            if (q.getId().equals(questId) && (q.getType() == Quest.QuestType.DAILY || q.getType() == Quest.QuestType.WEEKLY) && !q.isCompleted() && !q.isClaimed()) {
                toReroll = q;
                break;
            }
        }
        if (toReroll == null) return false;

        // Cooldown: 1 reroll per day per island
        long last = lastRerollTime.getOrDefault(islandId, 0L);
        if (System.currentTimeMillis() - last < 24L * 60 * 60 * 1000) {
            player.sendMessage("§cYou can reroll one quest per island per day.");
            return false;
        }

        // Small cost demo: 100 from island bank (in real, use tryRemoveIslandBalance)
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null) {
            // For demo, assume success; in prod integrate with EconomyManager.tryRemoveIslandBalance
            player.sendMessage("§eReroll cost: §6100§e from island bank (demo mode).");
        }

        current.remove(toReroll);
        Quest newQuest = createRandomQuest(toReroll.getType(), islandId);
        current.add(newQuest);
        recordRecentCategory(islandId, newQuest.getCategory());

        lastRerollTime.put(islandId, System.currentTimeMillis());
        saveDailyWeeklyToDB(islandId, current);

        player.sendMessage("§aQuest rerolled! New: §f" + newQuest.getTitle());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        // Step 6: Analytics for reroll frequency/tuning
        plugin.getLogger().info("[QuestAnalytics] Reroll used for " + questId + " type=" + toReroll.getType() + " island=" + islandId + " (cooldown respected)");

        return true;
    }

    // ==================== STREAKS (user-friendly daily habit + visible progression) ====================

    private void loadStreakIfNeeded(String islandId) {
        if (islandId == null || dailyStreaks.containsKey(islandId)) return;
        try {
            int[] data = plugin.getDatabaseManager().loadIslandDailyStreak(islandId);
            dailyStreaks.put(islandId, data[0]);
            lastDailyClaimDay.put(islandId, (long) data[1]);
        } catch (Exception ignored) {
            dailyStreaks.put(islandId, 0);
            lastDailyClaimDay.put(islandId, 0L);
        }
    }

    public int getDailyStreak(String islandId) {
        if (islandId == null) return 0;
        loadStreakIfNeeded(islandId);
        return dailyStreaks.getOrDefault(islandId, 0);
    }

    private void updateAndAwardDailyStreak(String islandId, Player player) {
        if (islandId == null) return;
        loadStreakIfNeeded(islandId);
        loadStreakFreezesIfNeeded(islandId);

        long nowDay = System.currentTimeMillis() / (24L * 60 * 60 * 1000);
        long last = lastDailyClaimDay.getOrDefault(islandId, 0L);
        int current = dailyStreaks.getOrDefault(islandId, 0);
        int freezes = streakFreezes.getOrDefault(islandId, 0);

        boolean usedFreeze = false;
        if (last == 0) {
            current = 1;
        } else if (nowDay == last + 1) {
            current = current + 1;
        } else if (nowDay > last + 1) {
            // Step 6: Streak freeze option - if available, use one to protect streak instead of reset
            if (freezes > 0) {
                freezes--;
                streakFreezes.put(islandId, freezes);
                plugin.getDatabaseManager().saveIslandStreakFreezes(islandId, freezes);
                usedFreeze = true;
                player.sendMessage("§e§lStreak Freeze used! §7Your streak is protected.");
                // do not reset current
            } else {
                current = 1; // reset streak
            }
        } // same day: keep current (already claimed one today)

        dailyStreaks.put(islandId, current);
        lastDailyClaimDay.put(islandId, nowDay);

        // Persist
        plugin.getDatabaseManager().saveIslandDailyStreak(islandId, current, nowDay);

        // Step 6: Escalating rewards for streaks (more generous on long streaks, PtW safe)
        int bonusXp = 0;
        if (current >= 3) {
            bonusXp = 10 + (current / 3 * 5); // escalating
            if (current % 7 == 0) {
                bonusXp += 25; // weekly milestone extra
                // Chance at cosmetic or freeze as escalating agency reward
                if (ThreadLocalRandom.current().nextInt(3) == 0) {
                    freezes = Math.min(5, freezes + 1);
                    streakFreezes.put(islandId, freezes);
                    plugin.getDatabaseManager().saveIslandStreakFreezes(islandId, freezes);
                    player.sendMessage("§d§lStreak Milestone! §7+1 Streak Freeze awarded.");
                }
            }
        }

        // UX feedback
        if (current >= 3) {
            player.sendMessage("§6§lDaily Streak: §e" + current + " days! §7Keep it up for bonus island rewards." + (usedFreeze ? " (protected by freeze)" : ""));
            if (bonusXp > 0) {
                plugin.getIslandManager().addIslandXp(player, bonusXp);
                player.sendMessage("§a+ " + bonusXp + " Island XP from streak!");
            }
        } else {
            player.sendMessage("§7Daily streak: §f" + current + "§7 (claim daily quests consistently for bonuses)");
        }

        if (usedFreeze) {
            plugin.getLogger().info("[QuestAnalytics] Used streak freeze for " + islandId);
        }
        plugin.getLogger().info("[QuestAnalytics] Daily streak for " + islandId + " now " + current);
    }

    /** Expose for GUI header / recommended logic. */
    public String getStreakInfo(String islandId) {
        int s = getDailyStreak(islandId);
        int freezes = getStreakFreezes(islandId);
        String base = (s >= 2) ? ("§6Streak: §e" + s + "d 🔥") : "";
        if (freezes > 0) base += " §7(Freezes: §a" + freezes + "§7)";
        if (!"Balanced".equals(currentWeeklyTheme)) base += " §dTheme: " + currentWeeklyTheme;
        return base;
    }

    /**
     * Clear all in-memory quest caches for new season (called from SeasonManager after DB wipe).
     * The next generate/open for any island/player will repopulate from (now empty) DB.
     * Safe to call during reset.
     */
    public void clearForNewSeason() {
        try {
            questsByIsland.clear();
            recentCategories.clear();
            lastQuestCompletion.clear();
            lastRerollTime.clear();
            preferredCategories.clear();
            dailyStreaks.clear();
            lastDailyClaimDay.clear();
            islandReputation.clear();
            streakFreezes.clear();
            playerTotalCompleted.clear();
            playerBestStreak.clear();
            playerCatCounts.clear();
            // weekly theme can stay or reset; keep for continuity of "new season starts mid-theme" or reset
            // lastThemeCheck = 0; // optional
            plugin.getLogger().info("[QuestManager] Cleared all caches for new season.");
        } catch (Exception e) {
            plugin.getLogger().warning("[QuestManager] Some cache clear failed during seasonal reset: " + e.getMessage());
        }
    }

    // Step 4: Feedback for objective completion (actionable, user friendly)
    private void notifyObjectiveComplete(String key, Quest quest, Quest.QuestObjective obj) {
        if (key == null || !key.contains("-")) return; // only notify for per-player keys easily
        try {
            java.util.UUID uuid = java.util.UUID.fromString(key);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("§a[Quest] §fObjective complete: §e" + obj.getDescription() + " §7(" + quest.getTitle() + ")");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            }
        } catch (Exception ignored) {}
    }
}
