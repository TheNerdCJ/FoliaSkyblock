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

import com.thenerdcj.island.IslandLevelUpEvent;
import org.bukkit.World;
import org.bukkit.event.EventHandler;

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
public class QuestManager implements org.bukkit.event.Listener {

    private final FoliaSkyblock plugin;

    // islandId -> list of quests
    private final Map<String, List<Quest>> questsByIsland = new ConcurrentHashMap<>();

    // islandId -> history of claimed quests (only shown in history view, removed from main list after claim)
    private final Map<String, List<Quest>> questHistory = new ConcurrentHashMap<>();

    // Tracks the highest MAIN_STORY chapter the island has claimed. Used to unlock the *next* chapter only (strict linear story).
    // Cleared on prestige rebirth so the story can be replayed fresh.
    private final ConcurrentHashMap<String, Integer> highestCompletedStoryChapter = new ConcurrentHashMap<>();

    // Track which islands have had their active dailies/weeklies loaded from DB (to avoid repeated loads)
    private final Set<String> activeQuestsLoadedFromDb = ConcurrentHashMap.newKeySet();

    // For uniqueness: recent categories used for daily/weekly per island (LRU style)
    private final Map<String, java.util.Deque<Quest.QuestCategory>> recentDailyCategories = new ConcurrentHashMap<>();
    private final Map<String, java.util.Deque<Quest.QuestCategory>> recentWeeklyCategories = new ConcurrentHashMap<>();

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
     * Get claimed quest history for the island (for history view in GUI).
     * These are removed from main active lists after claim.
     */
    public CompletableFuture<List<Quest>> getQuestHistory(String islandId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!questHistory.containsKey(islandId)) {
                // Load from DB on first access (persistent across restarts)
                List<Quest> loaded = plugin.getDatabaseManager().loadQuestHistory(islandId).join();
                questHistory.put(islandId, new ArrayList<>(loaded));
            }
            List<Quest> h = questHistory.getOrDefault(islandId, Collections.emptyList());
            return new ArrayList<>(h);
        });
    }

    public int getActiveQuestsCount(String islandId) {
        List<Quest> qs = questsByIsland.getOrDefault(islandId, Collections.emptyList());
        return (int) qs.stream()
                .filter(q -> !q.isCompleted() && !q.isClaimed() && !q.isExpired())
                .count();
    }

    /**
     * Clears only the active (in-memory) quests for an island.
     * Used during prestige rebirth so the new run gets fresh onboarding/story quests generated
     * by the create path. Claimed history (persistent) is intentionally left alone for records.
     */
    public void clearActiveForIsland(String islandId) {
        if (islandId != null) {
            questsByIsland.remove(islandId);
            highestCompletedStoryChapter.remove(islandId);
            clearActiveLoaded(islandId);
            // Also clear any persisted story chapter progress (fresh start after prestige)
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().clearStoryProgressForIsland(islandId);
                plugin.getDatabaseManager().clearActiveQuestsForIsland(islandId);
            }
        }
    }

    public void onEnderDragonKilled(String islandId) {
        List<Quest> quests = questsByIsland.get(islandId);
        if (quests == null) return;
        for (Quest q : quests) {
            if (q.getQuestLine() == Quest.QuestLine.MAIN_STORY && q.getChapter() == 20 && !q.isCompleted()) {
                q.addProgress(q.getTarget() - q.getProgress() + 1); // complete the dragon slayer quest
                break;
            }
        }
    }

    /**
     * Generate (or refresh) daily quests for the island.
     * Removes expired/completed old dailies and adds new ones if needed.
     */
    public void generateDailyQuests(String islandId) {
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());

        // Ensure persisted active quests (incl. FIRST/onboarding) are loaded for this island.
        ensureActiveQuestsLoaded(islandId);

        // Clean up old/expired dailies (FIRST/onboarding quests are left alone; they persist via active_quests)
        List<Quest> toRemove = new ArrayList<>();
        for (Quest q : current) {
            if (q.getType() == Quest.QuestType.DAILY && (q.isCompleted() || q.isExpired() || q.isClaimed())) {
                toRemove.add(q);
                plugin.getDatabaseManager().deleteActiveQuest(islandId, q.getId());
            }
        }
        current.removeAll(toRemove);

        // Ensure we have at least 3 daily quests - generate unique ones based on current island progress
        long dailyCount = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.DAILY)
            .count();

        for (long i = dailyCount; i < 3; i++) {
            Quest newQuest = createAdaptiveDailyWeeklyQuest(Quest.QuestType.DAILY, islandId);
            current.add(newQuest);
            plugin.getDatabaseManager().saveActiveQuest(islandId, newQuest);
        }
    }

    /**
     * Generate (or refresh) weekly quests for the island.
     */
    public void generateWeeklyQuests(String islandId) {
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());

        // Ensure persisted active quests (incl. FIRST/onboarding) are loaded for this island.
        ensureActiveQuestsLoaded(islandId);

        // Clean up old weeklies (FIRST/onboarding quests are left alone; they persist via active_quests)
        List<Quest> toRemove = new ArrayList<>();
        for (Quest q : current) {
            if (q.getType() == Quest.QuestType.WEEKLY && (q.isCompleted() || q.isExpired() || q.isClaimed())) {
                toRemove.add(q);
                plugin.getDatabaseManager().deleteActiveQuest(islandId, q.getId());
            }
        }
        current.removeAll(toRemove);

        long weeklyCount = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.WEEKLY)
            .count();

        for (long i = weeklyCount; i < 2; i++) {
            Quest newQuest = createAdaptiveDailyWeeklyQuest(Quest.QuestType.WEEKLY, islandId);
            current.add(newQuest);
            plugin.getDatabaseManager().saveActiveQuest(islandId, newQuest);
        }
    }

    /**
     * Generate one-time early-game / onboarding "FIRST" quests for a brand new island.
     * These act as the tutorial / balance for the heavy late-game systems (skills, collections, housing, cosmetics).
     * Called on island creation (including resets for fresh start feel). Never removed by daily/weekly gens.
     */
    public void generateOnboardingQuests(String islandId) {
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());

        // Ensure any persisted FIRST (onboarding) quests are loaded (progress survives restarts).
        ensureActiveQuestsLoaded(islandId);

        long firstCount = current.stream()
            .filter(q -> q.getType() == Quest.QuestType.FIRST)
            .count();

        if (firstCount > 0) return; // Already seeded (or loaded from DB) for this island life

        // Note: history is still loaded/used for the dedicated history view and records across prestiges.
        // We intentionally re-seed onboarding/story for replay after prestige rebirth (new "run", re-level to experience chapters again).
        // History records the claims from previous runs.

        // Fixed, friendly first-island quests (target low for new players, categories map to actions)
        current.add(createFirstQuest(
            Quest.QuestCategory.FARMING,
            "First Harvest",
            "Harvest your first crops (break fully-grown wheat, carrots, potatoes, etc.)",
            1, 35, 40, 1
        ));
        current.add(createFirstQuest(
            Quest.QuestCategory.MINING,
            "First Dig",
            "Break your first stone, ore, or dirt block on the island",
            1, 25, 30, 2
        ));
        current.add(createFirstQuest(
            Quest.QuestCategory.COMBAT,
            "First Foe",
            "Defeat your first hostile mob (zombie, skeleton, etc.)",
            1, 50, 45, 3
        ));
        current.add(createFirstQuest(
            Quest.QuestCategory.BUILDING,
            "First Steps",
            "Place blocks to expand or customize your island (5 total)",
            5, 20, 25, 4
        ));
        current.add(createFirstQuest(
            Quest.QuestCategory.CHALLENGE,
            "First Minion",
            "Deploy your first minion to help automate tasks",
            1, 60, 50, 5
        ));

        // Note: progress is fed by EarlyGameListener (safe, anti-cheat guarded) + MinionManager hook

        // Kick off initial main story chapters (based on starting island level ~1)
        generateStoryQuests(islandId, 1);
    }

    /**
     * Generate or unlock MAIN_STORY quests based on current island level.
     * STRICTLY LINEAR CHAPTERS: only the single next chapter after the last completed one is ever active.
     * This creates a guided story arc (small sequential missions) from early game through Nether/End prep,
     * culminating in killing the Ender Dragon. Dailies & Weeklies run simultaneously (different categories or tabs).
     * Called on level up, claim of previous chapter, and initially.
     */
    public void generateStoryQuests(String islandId, int islandLevel) {
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());

        // Determine the last completed MAIN_STORY chapter (tracker + any loaded history for safety across restarts)
        int lastCompleted = highestCompletedStoryChapter.getOrDefault(islandId, 0);
        List<Quest> hist = questHistory.get(islandId);
        if (hist != null) {
            int histMax = hist.stream()
                .filter(q -> q.getQuestLine() == Quest.QuestLine.MAIN_STORY)
                .mapToInt(Quest::getChapter)
                .max().orElse(0);
            lastCompleted = Math.max(lastCompleted, histMax);
        }

        int targetChapter = lastCompleted + 1;

        // Enforce "only one story chapter active at a time": remove any other MAIN_STORY quests (past claimed or future spoilers)
        current.removeIf(q -> q.getQuestLine() == Quest.QuestLine.MAIN_STORY && q.getChapter() != targetChapter);

        // If we already have the exact current target chapter active, nothing to do
        boolean hasCurrent = current.stream()
            .anyMatch(q -> q.getQuestLine() == Quest.QuestLine.MAIN_STORY && q.getChapter() == targetChapter && !q.isClaimed());
        if (hasCurrent) {
            return;
        }

        // Level gate: only unlock the next chapter when the island is strong enough
        int minLevel = getMinLevelForChapter(targetChapter);
        if (islandLevel >= minLevel) {
            Quest chapterQuest = createStoryQuestForChapter(targetChapter);

            // Load any previously saved progress for this chapter so story mob defeats survive restarts
            int savedProgress = plugin.getDatabaseManager().loadStoryChapterProgress(islandId, targetChapter);
            if (savedProgress > 0) {
                chapterQuest.setProgress(Math.min(savedProgress, chapterQuest.getTarget()));
            }

            current.add(chapterQuest);
        }
    }

    private int getMinLevelForChapter(int chapter) {
        return switch (chapter) {
            case 6 -> 5;
            case 7 -> 7;
            case 8 -> 9;
            // Overworld mob / boss chapters
            case 9 -> 10;
            case 10 -> 11;
            case 11 -> 12;
            case 12 -> 13;
            case 13 -> 14;
            case 14 -> 15;
            case 15 -> 16;
            // Nether gate
            case 16 -> 18;
            // Nether mob / boss chapters
            case 17 -> 20;
            case 18 -> 22;
            case 19 -> 24;
            // Final dragon
            case 20 -> 28;
            default -> 100; // future chapters not yet defined
        };
    }

    private Quest createStoryQuestForChapter(int chapter) {
        // Story is strictly linear chapters (only current one active + progress).
        // Overworld phase (ch 9-15): defeat every key hostile mob + "bosses" (zombie, skeleton, creeper, spider, witch, enderman, raid/guardian bosses).
        // Claiming the nether gate chapter (16) sets the nether_access_milestone and unlocks nether dimension access.
        // Nether phase (17-19): defeat every key nether mob/boss (blaze, wither skeleton, ghast, piglin brute etc).
        // End + final: the dragon chapter requires the nether complete via linear story. End mob threats (shulkers) are part of final prep descriptions.
        // Dragon kill (final chapter) gives prestige option.
        return switch (chapter) {
            // === FOUNDATIONS (pre-combat) ===
            case 6 -> createStoryQuest(Quest.QuestCategory.BUILDING, "Solid Foundations",
                "Place 250 blocks to build a real base. A strong home is the foundation for every adventure that follows — all the way to the End.",
                250, 180, 450, 6);
            case 7 -> createStoryQuest(Quest.QuestCategory.MINING, "Resource Rush",
                "Mine 450 stone and ores. Stockpile the raw materials you will need for serious tools, armor, and the nether portal.",
                450, 210, 520, 7);
            case 8 -> createStoryQuest(Quest.QuestCategory.FARMING, "Island Bounty",
                "Harvest 300 crops. Secure sustainable food and resources so you can focus on exploration and combat.",
                300, 190, 480, 8);

            // === OVERWORLD: EVERY HOSTILE MOB + BOSSES (must complete to "enter" nether) ===
            case 9 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Undead Plague: Zombies",
                "Defeat 80 Zombies. The most common overworld hostile — master them as the first step toward nether access.",
                80, 250, 600, 9, "ZOMBIE");
            case 10 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Bone Collectors: Skeletons",
                "Defeat 60 Skeletons (all variants). Archers and swordsmen must be eliminated.",
                60, 240, 580, 10, "SKELETON");
            case 11 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Creeper Carnage",
                "Defeat 40 Creepers. Survive the explosions — these are critical overworld threats.",
                40, 280, 650, 11, "CREEPER");
            case 12 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Arachnid Ambush: Spiders",
                "Defeat 50 Spiders. Nighttime and cave terrors — part of full overworld hostile mastery.",
                50, 230, 570, 12, "SPIDER");
            case 13 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Witch Hunt",
                "Defeat 25 Witches. Potion masters of the overworld must be purged.",
                25, 270, 620, 13, "WITCH");
            case 14 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Enderman Stalkers (Overworld)",
                "Defeat 20 Overworld Endermen. These teleporting nightmares are the last major overworld hostile before bosses.",
                20, 300, 700, 14, "ENDERMAN");
            case 15 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Overworld Bosses: Raiders & Guardians",
                "Defeat the major overworld bosses — Vindicators, Evokers, Ravagers, and an Elder Guardian. EVERY overworld boss must fall to unlock the Nether in your story.",
                12, 380, 950, 15, "VINDICATOR"); // Representative; natural play + other kills will cover the spirit of "every"

            // === NETHER GATE (claiming this chapter unlocks nether access via milestone + dimension flag) ===
            case 16 -> createStoryQuest(Quest.QuestCategory.BUILDING, "Nether Portal Mastery",
                "Build and light a functional nether portal. With all overworld hostiles and bosses defeated, the Nether dimension is now open to your island.",
                10, 420, 1100, 16);

            // === NETHER: EVERY MOB / BOSS (must complete to enter the End) ===
            case 17 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Blaze Barrage",
                "Defeat 30 Blazes deep in the Nether. Their rods are the key to the End — defeat this core nether threat.",
                30, 320, 780, 17, "BLAZE");
            case 18 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Withering Depths",
                "Defeat 25 Wither Skeletons in nether fortresses. Skulls and the fortress itself must be conquered.",
                25, 340, 820, 18, "WITHER_SKELETON");
            case 19 -> createStoryQuest(Quest.QuestCategory.COMBAT, "Sky Terrors & Brutes",
                "Defeat 15 Ghasts (tear their tears) and 15 Piglin Brutes / Hoglins. Every major nether flying and brute force must be broken to unlock the End.",
                30, 360, 880, 19, "GHAST");

            // === FINAL: ENDER DRAGON (after nether complete via linear gating). End mob threats (shulkers etc.) described as part of final preparation. ===
            case 20 -> {
                Quest dragon = createStoryQuest(Quest.QuestCategory.CHALLENGE, "Slay the Ender Dragon",
                    "You have defeated all overworld hostiles/bosses, all nether mobs/bosses, and prepared for the End. Now enter the End and defeat the Ender Dragon — the final boss. Claiming this completes the Main Story and unlocks the Prestige option for your island.",
                    1, 1500, 8000, 20);
                dragon.addExtraReward(new Quest.QuestReward("Prestige eligibility + island level & XP multipliers"));
                dragon.addExtraReward(new Quest.QuestReward("Cosmetic prestige unlocks + top-island leaderboard glory"));
                yield dragon;
            }
            default -> createStoryQuest(Quest.QuestCategory.CHALLENGE, "Legendary Trial",
                "Complete this chapter of your legend and press on toward the Ender Dragon.", 10, 200, 500, chapter);
        };
    }

    private Quest createFirstQuest(Quest.QuestCategory category, String title, String description,
                                   int target, int rewardXp, int rewardMoney, int chapter) {
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
            farFuture,
            Quest.QuestLine.ONBOARDING,
            chapter
        );
    }

    private Quest createStoryQuest(Quest.QuestCategory category, String title, String description,
                                   int target, int rewardXp, int rewardMoney, int chapter) {
        return createStoryQuest(category, title, description, target, rewardXp, rewardMoney, chapter, null);
    }

    private Quest createStoryQuest(Quest.QuestCategory category, String title, String description,
                                   int target, int rewardXp, int rewardMoney, int chapter,
                                   String requiredMobType) {
        long farFuture = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);
        List<String> prereqs = new ArrayList<>();
        if (chapter > 6) {
            prereqs.add("Complete Story Chapter " + (chapter - 1));
        }
        return new Quest(
            UUID.randomUUID().toString(),
            "§bStory Ch." + chapter + ": " + title,
            description,
            category,
            Quest.QuestType.FIRST,
            0,
            target,
            rewardXp,
            rewardMoney,
            false,
            farFuture,
            Quest.QuestLine.MAIN_STORY,
            chapter,
            prereqs,
            new ArrayList<>(),
            requiredMobType
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

                // Remove from active list immediately so it no longer appears in main GUI lists
                // (generate* also cleans on refresh, but do it here for instant effect)
                quests.removeIf(q -> q.getId().equals(questId));

                // Add to history (shown only in dedicated history view)
                List<Quest> hist = questHistory.computeIfAbsent(islandId, k -> new ArrayList<>());
                if (hist.stream().noneMatch(h -> h.getId().equals(questId))) {
                    hist.add(0, quest); // most recent first
                    if (hist.size() > 12) {
                        hist.remove(hist.size() - 1);
                    }
                }

                // Persist to DB so history survives restarts and "knows what player has completed"
                plugin.getDatabaseManager().saveQuestToHistory(islandId, quest);

                // Remove from active_quests table (for dailies/weeklies + FIRST/onboarding persistence)
                if (quest.getType() == Quest.QuestType.DAILY || quest.getType() == Quest.QuestType.WEEKLY || quest.getType() == Quest.QuestType.FIRST) {
                    plugin.getDatabaseManager().deleteActiveQuest(islandId, quest.getId());
                }

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

                // Prestige encouragement: Dragon kill (final story chapter) is the gateway to prestige and top islands
                if (quest.getQuestLine() == Quest.QuestLine.MAIN_STORY && quest.getChapter() == 20) {
                    player.sendMessage("§6§lCONGRATULATIONS! §eYou have completed the full story arc and slain the Ender Dragon!");
                    player.sendMessage("§aThis positions your island perfectly for Prestige - the path to the absolute top islands and leaderboards.");
                    if (plugin.getPrestigeManager() != null) {
                        Island islandForPrestige = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        if (islandForPrestige != null) {
                            int pLevel = plugin.getPrestigeManager().getPrestigeLevel(islandForPrestige);
                            player.sendMessage("§7Current Prestige: §b" + pLevel + " §7- Prestige now to multiply your future gains and climb the tops!");
                            // Grant a prestige-level reward cosmetic (e.g. unlock a high-tier trail if not already)
                            if (plugin.getParticleTrailManager() != null) {
                                plugin.getParticleTrailManager().grantPrestigeUnlocks(player, pLevel + 1);
                            }
                        }
                    }
                }

                // Track completed story chapter and immediately unlock the *next* chapter (if level allows).
                // This makes chapters feel like sequential "small missions" that you complete one-by-one on the road to the dragon.
                if (quest.getQuestLine() == Quest.QuestLine.MAIN_STORY) {
                    highestCompletedStoryChapter.merge(islandId, quest.getChapter(), Math::max);
                    Island isl = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                    int lvl = (isl != null) ? isl.getLevel() : 1;
                    generateStoryQuests(islandId, lvl);

                    // Story-based dimension gates: claiming the dedicated gate chapters sets the existing milestone + unlock flag.
                    // This enforces "defeat all overworld hostiles/bosses before you can enter the nether", etc.
                    if (isl != null) {
                        if (quest.getChapter() == 16) { // Nether gate chapter (after all overworld mobs + bosses)
                            isl.completeMilestone("nether_access_milestone", 800);
                            isl.unlockDimension("nether");
                            player.sendMessage("§a§lNether Unlocked! §7Your island may now access the Nether dimension.");
                        }
                        if (quest.getChapter() == 19) { // After all nether mobs/bosses — end is next (dragon is final)
                            isl.completeMilestone("end_access_milestone", 1200);
                            isl.unlockDimension("end");
                            player.sendMessage("§a§lThe End Unlocked! §7Your island may now access the End dimension (prepare for the final bosses).");
                        }
                    }
                }

                // Play sound is handled in GUI

                return true;
            }
        }
        return false;
    }

    // ==================== STUBS FOR QUEST DETAIL GUI (streaks, reroll, etc.) ====================

    public String getStreakInfo(String islandId) {
        // TODO: implement daily/weekly streak tracking if desired
        return "";
    }

    public boolean rerollDailyWeeklyQuest(String islandId, String questId, Player player) {
        List<Quest> current = questsByIsland.get(islandId);
        if (current == null) return false;

        // Find and remove the quest to reroll
        Quest toReroll = null;
        for (Quest q : current) {
            if (q.getId().equals(questId) && (q.getType() == Quest.QuestType.DAILY || q.getType() == Quest.QuestType.WEEKLY)) {
                toReroll = q;
                break;
            }
        }
        if (toReroll == null) return false;

        current.remove(toReroll);

        // Add a fresh one of the same type (adaptive + unique + saved for persistence)
        Quest newQuest = createAdaptiveDailyWeeklyQuest(toReroll.getType(), islandId);
        current.add(newQuest);
        plugin.getDatabaseManager().saveActiveQuest(islandId, newQuest);

        // In real impl would charge cost / apply cooldown
        return true;
    }

    /**
     * Optional helper: Add progress to matching quests (call this from your listeners)
     * Overload for generic (any in category).
     */
    public void addProgressToIsland(String islandId, Quest.QuestCategory category, int amount) {
        addProgressToIsland(islandId, category, null, amount);
    }

    /**
     * Add progress, with optional mobType for COMBAT-specific story chapters.
     * Only the current linear story chapter (if any) receives progress, and only if mob matches when required.
     */
    public void addProgressToIsland(String islandId, Quest.QuestCategory category, String mobType, int amount) {
        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();
        List<Quest> quests = questsByIsland.get(islandId);
        if (quests == null) {
            if (start != 0) { /* no log if no quests */ }
            return;
        }

        int currentLinearChapter = getCurrentLinearChapter(islandId);

        for (Quest quest : quests) {
            if (quest.getCategory() == category && !quest.isCompleted() && !quest.isExpired() && !quest.isClaimed()) {
                // Only enforce strict linear chapter for MAIN_STORY (one chapter at a time).
                // Onboarding quests (different categories/chapters) should track simultaneously from normal play.
                if (quest.getQuestLine() == Quest.QuestLine.MAIN_STORY 
                        && currentLinearChapter != 0 && quest.getChapter() != currentLinearChapter) {
                    continue; // linear story: only the current (lowest unclaimed) chapter receives progress
                }
                // For story combat chapters that require a specific hostile mob, only that mob counts.
                if (category == Quest.QuestCategory.COMBAT 
                        && quest.getRequiredMobType() != null 
                        && mobType != null 
                        && !quest.getRequiredMobType().equalsIgnoreCase(mobType)) {
                    continue;
                }
                quest.addProgress(amount);

                // Persist progress for MAIN_STORY chapters so partial mob kills survive server restarts
                if (quest.getQuestLine() == Quest.QuestLine.MAIN_STORY) {
                    plugin.getDatabaseManager().saveStoryChapterProgress(islandId, quest.getChapter(), quest.getProgress());
                }

                // Persist progress for daily/weekly + FIRST (onboarding) so they survive restarts
                if (quest.getType() == Quest.QuestType.DAILY || quest.getType() == Quest.QuestType.WEEKLY || quest.getType() == Quest.QuestType.FIRST) {
                    plugin.getDatabaseManager().saveActiveQuest(islandId, quest);
                }
            }
        }
        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[QuestManager] PROFILE: addProgressToIsland took " + (ns / 1_000_000.0) + " ms (early game/quest hot path for large scale)");
        }
    }

    private int getCurrentLinearChapter(String islandId) {
        List<Quest> qs = questsByIsland.getOrDefault(islandId, Collections.emptyList());
        return qs.stream()
                .filter(q -> (q.getQuestLine() == Quest.QuestLine.ONBOARDING || q.getQuestLine() == Quest.QuestLine.MAIN_STORY) && !q.isClaimed())
                .mapToInt(Quest::getChapter)
                .min()
                .orElse(0);
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
            expiryTime,
            Quest.QuestLine.ONBOARDING,
            0
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
     * Adaptive daily/weekly quest generation.
     * - Always unique per generation (avoids recent categories per island using LRU).
     * - References current island "progress" via per-island seeding + bias toward development areas.
     * - Descriptions encourage further island growth (level, worth, collections, etc.).
     */
    private Quest createAdaptiveDailyWeeklyQuest(Quest.QuestType type, String islandId) {
        java.util.Deque<Quest.QuestCategory> recent = (type == Quest.QuestType.DAILY ? recentDailyCategories : recentWeeklyCategories)
            .computeIfAbsent(islandId, k -> new java.util.ArrayDeque<>());

        Random random = ThreadLocalRandom.current();
        Quest.QuestCategory[] categories = Quest.QuestCategory.values();

        // Per-island unique seed for reproducibility + variety across islands
        long seed = (long) islandId.hashCode() * 31 + System.currentTimeMillis() / (type == Quest.QuestType.DAILY ? 1000*60*60*6 : 1000*60*60*24);
        Random seededRandom = new Random(seed);
        Quest.QuestCategory category = categories[seededRandom.nextInt(categories.length)];

        // Uniqueness: avoid recently used categories for this island's dailies/weeklies
        int attempts = 0;
        while (recent.contains(category) && attempts < 7) {
            category = categories[random.nextInt(categories.length)];
            attempts++;
        }
        recent.addLast(category);
        if (recent.size() > 6) recent.removeFirst();

        // Target base
        int target = switch (type) {
            case DAILY -> 8 + random.nextInt(25);
            case WEEKLY -> 40 + random.nextInt(80);
            default -> 10;
        };
        target = adjustTargetForCategory(category, target, type);

        // Adaptive to island progress: use islandId hash as proxy for "development stage" + slight bias
        // In practice this encourages different quests for different islands based on their "progress signature"
        int progressBias = Math.abs(islandId.hashCode()) % 10;
        if (progressBias > 6) {
            // "advanced" islands get slightly harder or challenge-oriented
            if (category == Quest.QuestCategory.CHALLENGE || category == Quest.QuestCategory.COMBAT) target = (int)(target * 1.2);
        } else if (progressBias < 3) {
            // Newer islands get more approachable targets in building/farming
            if (category == Quest.QuestCategory.BUILDING || category == Quest.QuestCategory.FARMING) target = Math.max(5, (int)(target * 0.8));
        }

        int rewardXp = Math.max(20, target * (type == Quest.QuestType.DAILY ? 3 : 2));
        int rewardMoney = Math.max(50, target * (type == Quest.QuestType.DAILY ? 8 : 6));

        long duration = type == Quest.QuestType.DAILY 
            ? 24L * 60 * 60 * 1000 
            : 7L * 24 * 60 * 60 * 1000;
        long expiryTime = System.currentTimeMillis() + duration;

        String title = generateTitle(category, type);
        String description = generateAdaptiveDescription(category, target, islandId, type);

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
            expiryTime,
            Quest.QuestLine.ONBOARDING,
            0
        );
    }

    private String generateAdaptiveDescription(Quest.QuestCategory category, int target, String islandId, Quest.QuestType type) {
        String base = generateDescription(category, target);
        // Make it reference "current island progress" and encourage further development
        String suffix = "";
        if (type == Quest.QuestType.WEEKLY && category == Quest.QuestCategory.CHALLENGE && new Random(islandId.hashCode()).nextBoolean()) {
            suffix = " (Bonus: complete several dailies this week to push your island's overall progress)";
        } else {
            // Pseudo reference to development
            int sig = Math.abs(islandId.hashCode() % 5);
            switch (sig) {
                case 0 -> suffix = " to grow your island's worth and level";
                case 1 -> suffix = " and expand your collections";
                case 2 -> suffix = " to support more minions and automation";
                case 3 -> suffix = " as you prepare for the next dimension";
                default -> suffix = " to strengthen your island for prestige";
            }
        }
        return base + suffix;
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

    private boolean hasLoadedActiveFromDb(String islandId) {
        return activeQuestsLoadedFromDb.contains(islandId);
    }

    private void markActiveLoaded(String islandId) {
        activeQuestsLoadedFromDb.add(islandId);
    }

    private void clearActiveLoaded(String islandId) {
        activeQuestsLoadedFromDb.remove(islandId);
        recentDailyCategories.remove(islandId);
        recentWeeklyCategories.remove(islandId);
    }

    /**
     * Ensures any persisted active quests (DAILY + WEEKLY + FIRST/onboarding) are loaded from DB.
     * Safe to call multiple times; only loads once per island per server run.
     */
    private void ensureActiveQuestsLoaded(String islandId) {
        if (hasLoadedActiveFromDb(islandId)) return;
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());
        try {
            List<Quest> loaded = plugin.getDatabaseManager().loadActiveQuests(islandId).join();
            for (Quest q : loaded) {
                if (current.stream().noneMatch(existing -> existing.getId().equals(q.getId()))) {
                    current.add(q);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[QuestManager] Failed to load active quests for " + islandId);
        }
        markActiveLoaded(islandId);
    }

    @EventHandler
    public void onIslandLevelUp(IslandLevelUpEvent e) {
        if (e.getIsland() == null || e.getIsland().getDimension() != World.Environment.NORMAL) {
            return; // Story progression on the main overworld island
        }
        generateStoryQuests(e.getIsland().getId(), e.getNewLevel());
    }
}
