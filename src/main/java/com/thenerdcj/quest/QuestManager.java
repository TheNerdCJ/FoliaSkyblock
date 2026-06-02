package com.thenerdcj.quest;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.ParticleTrail;
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

        // Clean up old/expired dailies (never touch FIRST/onboarding quests)
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

        // Clean up old weeklies (never touch FIRST/onboarding quests)
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
     * Generate one-time early-game / onboarding "FIRST" quests for a brand new island.
     * These act as the tutorial / balance for the heavy late-game systems (skills, collections, housing, cosmetics).
     * Called on island creation (including resets for fresh start feel). Never removed by daily/weekly gens.
     */
    public void generateOnboardingQuests(String islandId) {
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());

        long firstCount = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.FIRST)
            .count();

        if (firstCount > 0) return; // Already seeded for this island life

        // Fixed, friendly first-island quests (target low for new players, categories map to actions)
        current.add(createFirstQuest(
            Quest.QuestCategory.FARMING,
            "First Harvest",
            "Harvest your first crops (break fully-grown wheat, carrots, potatoes, etc.)",
            1, 35, 40
        ));
        current.add(createFirstQuest(
            Quest.QuestCategory.MINING,
            "First Dig",
            "Break your first stone, ore, or dirt block on the island",
            1, 25, 30
        ));
        current.add(createFirstQuest(
            Quest.QuestCategory.COMBAT,
            "First Foe",
            "Defeat your first hostile mob (zombie, skeleton, etc.)",
            1, 50, 45
        ));
        current.add(createFirstQuest(
            Quest.QuestCategory.BUILDING,
            "First Steps",
            "Place blocks to expand or customize your island (5 total)",
            5, 20, 25
        ));
        current.add(createFirstQuest(
            Quest.QuestCategory.CHALLENGE,
            "First Minion",
            "Deploy your first minion to help automate tasks",
            1, 60, 50
        ));

        // Note: progress is fed by EarlyGameListener (safe, anti-cheat guarded) + MinionManager hook
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
        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
        List<Quest> quests = questsByIsland.get(islandId);
        if (quests == null) {
            if (start != 0) { /* no log if no quests */ }
            return;
        }

        for (Quest quest : quests) {
            if (quest.getCategory() == category && !quest.isCompleted() && !quest.isExpired() && !quest.isClaimed()) {
                quest.addProgress(amount);
            }
        }
        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[QuestManager] PROFILE: addProgressToIsland took " + (ns / 1_000_000.0) + " ms (early game/quest hot path for large scale)");
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
            case FIRST -> 1; // onboarding handled by dedicated creator (not reached via random)
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
}
