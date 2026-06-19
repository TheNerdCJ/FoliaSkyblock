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
            if (q.getQuestLine() == Quest.QuestLine.MAIN_STORY && q.getChapter() >= 71 && q.getChapter() <= 80 && !q.isCompleted()) {
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
     * This creates a guided 100-chapter story arc ("The Fractured Veil") from early game through Nether/End prep,
     * the Ender Dragon, and into Prestige loops. Dailies & Weeklies run simultaneously (different categories or tabs).
     * Called on level up, claim of previous chapter, and initially.
     */
    public void generateStoryQuests(String islandId, int islandLevel) {
        List<Quest> current = questsByIsland.computeIfAbsent(islandId, k -> new ArrayList<>());

        // Ensure persisted active (incl MAIN_STORY) loaded for restart persistence
        ensureActiveQuestsLoaded(islandId);

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
            plugin.getDatabaseManager().saveActiveQuest(islandId, chapterQuest);
        }
    }

    private int getMinLevelForChapter(int chapter) {
        // Scaled for 100 chapters: gradual to encourage long-term play. Max ~150 for chapter 100.
        return Math.min(150, 1 + (int)(chapter * 1.4));
    }

    private Quest createStoryQuestForChapter(int chapter) {
        // FULL STORY: "The Fractured Veil" - 100 chapters for deep, continuous gameplay to Prestige.
        //
        // Core design (no overwhelm, maximum engagement):
        // - STRICT LINEAR: only the NEXT chapter after your last completed one is active. Others are hidden until you claim.
        // - Runs PARALLEL to onboarding (FIRST), dailies, weeklies, challenges, slayers, housing, collections. Play everything together.
        // - Rich, immersive Elder lore in EVERY description: the Sky Elder tells an ongoing tale of the fraying Veil, corruption from the void, the Skyweavers who came before, the Dragon as final lock, and Prestige as eternal re-weaving.
        // - Granular + evocative steps: each chapter focuses on 1-2 concrete skyblock actions with flavor (build shelter walls, mine the "bones of old worlds", deploy specific minions, trade at Bazaar, craft Eyes of Ender, shatter crystals with cover, raise prestige monuments, etc.).
        // - Encourages continuous play: targets are meaningful (not trivial), scale gradually, rewards (XP + island bank money + unlocks) feel significant. Late chapters push advanced automation, grand building, and repeat mastery.
        // - Interaction: direct hints for /is upgrades, Minions menu, Bazaar, skill menus, island furniture/housing, Slayer quests, Dimension bosses. The Elder Codex (in Quest Log) is a beautiful interactive journal.
        // - 0-100 journey: Chapter 1 feels like the arrival after a mysterious "call". Chapter 100 is a true capstone that explicitly opens infinite prestige loops with escalating power and legend.
        //
        // 10 PHASES (10 chapters each) with distinct identity and rising stakes:
        // Phase 1 (1-10): The First Tear — Awakening on the speck. Shelter, first resources, minion, basic skills.
        // Phase 2 (11-20): Heart of the Sky — Overworld Empire. Combat hordes, collections, trade, real housing, first bosses.
        // Phase 3 (21-30): The Burning Gate — Portal to Nether. Obsidian, first steps in fire, blaze rods.
        // Phase 4 (31-40): Fortress of Ash — Nether depths. Wither skeletons, fortresses, nether bases, economy.
        // Phase 5 (41-50): Lords of Flame — Nether mastery. Advanced mobs, Eyes of Ender preparation.
        // Phase 6 (51-60): Gaze into the Void — End prep. Eyes crafted, End dimension unlocked.
        // Phase 7 (61-70): Chorus of the Beyond — Outer islands. Shulkers, chorus, edge bases.
        // Phase 8 (71-80): The Last Lock — Dragon's Fall. Crystals, the great battle, egg, Veil mended.
        // Phase 9 (81-90): Threads Rewoven — Prestige dawn. Rebirth, multipliers, monuments.
        // Phase 10 (91-100): Eternal Weaver — Become legend. Massive projects, max power, infinite cycles.
        //
        // Major beats: Nether ~30, End ~60, Dragon ~80, full Prestige mastery at 100.
        // Special handling for Ender Dragon kill. Prestige rebirth clears active story so you can re-experience with power.
        if (chapter < 1 || chapter > 100) {
            return createStoryQuest(Quest.QuestCategory.CHALLENGE, "Legendary Trial",
                "The Elder: 'Your legend continues beyond the known chapters. Master skyblock and ascend.'", 10 + chapter, 100 + chapter * 10, 200 + chapter * 20, chapter);
        }

        int phase = (chapter - 1) / 10 + 1;
        int sub = (chapter - 1) % 10 + 1; // 1-10 within phase

        String title = "Veil Eternal " + chapter;
        String desc = "Elder: 'Beyond the chapters, your skyblock story continues. Prestige, build, conquer.'";
        Quest.QuestCategory cat = Quest.QuestCategory.CHALLENGE;
        int target = 8 + chapter * 6;
        int xp = 120 + chapter * 18;
        int money = 250 + chapter * 30;
        String mobType = null;

        // ========== PHASE 1: AWAKENING (1-10) ==========
        if (phase == 1) {
            if (sub <= 3) {
                cat = Quest.QuestCategory.BUILDING;
                title = "The First Tear " + sub + ": Anchor in the Void";
                desc = "Elder: 'Chapter " + chapter + ". Before you arrived there was only the call — a whisper across the fractured sky. You stand upon a speck above the endless void. The Veil between worlds frays — the first tear calls your name. Place blocks to shape a humble shelter (walls, floor, roof). This will be the seed of your sky empire. Visit /is later for upgrades and housing. Every structure you raise mends one thread of the Veil. Tend your dailies and deploy that first minion early — the small continuous acts of a weaver build the habits that will make every prestige run legendary. In your future prestiges, you'll look back on these humble beginnings with fondness and vastly greater power.'";
                target = 8 + sub * 6;
            } else if (sub <= 6) {
                cat = Quest.QuestCategory.MINING;
                title = "The First Tear " + sub + ": Bones of the Old World";
                desc = "Elder: 'Chapter " + chapter + ". The stone remembers what the sky has forgotten. Mine stone, ore and the gravel of ages. Level your Mining skill — it will grant power and speed for all future labors. The fragments you gather will become tools, furnaces, and the backbone of your collections. Do not neglect the daily weave; even while mining, a quick daily or bazaar visit keeps the Veil strong and builds prestige power for later chapters.'";
                target = 24 + sub * 8;
                if (sub == 6) target = 64;
            } else {
                cat = Quest.QuestCategory.FARMING;
                title = "The First Tear " + sub + ": Roots of the Dream";
                desc = "Elder: 'Chapter " + chapter + ". Life must take hold or the island starves. Till soil, plant seeds, harvest wheat, carrots and potatoes. Place a simple water source and build a small farm plot. Deploy your first minion soon — it will tend while you grow stronger. Collections will sing your name. The daily rhythm of harvest and care is the quiet mending that prepares you for the grandeur of prestige cycles, where these early farms will bloom in minutes thanks to your past dedication.'";
                target = 10 + sub * 4;
            }
        }
        // ========== PHASE 2: OVERWORLD EMPIRE (11-20) ==========
        else if (phase == 2) {
            if (sub <= 4) {
                cat = Quest.QuestCategory.COMBAT;
                mobType = switch (sub) {
                    case 1 -> "ZOMBIE";
                    case 2 -> "SKELETON";
                    case 3 -> "CREEPER";
                    default -> "SPIDER";
                };
                title = "Heart of the Sky " + sub + ": Purge the Leaking Shadows";
                desc = "Elder: 'Chapter " + chapter + ". The first tear widens. Corrupted shades pour into our realm. Defeat " + (mobType != null ? mobType.toLowerCase() + "s" : "hordes") + " that wander your island at night. Use a sword, gain combat practice, and consider your first Slayer quest for greater challenges. Your skill in battle protects everything you build. Do not neglect your daily quests and minion checks during these fights — they are the quiet mending that builds the power and habits for prestige returns where combat becomes second nature and you can focus on grander works faster.'";
                target = 12 + sub * 5;
            } else if (sub <= 7) {
                cat = Quest.QuestCategory.BUILDING;
                title = "Heart of the Sky " + sub + ": Raise the Empire";
                desc = "Elder: 'Chapter " + chapter + ". A lone hut is not enough. Expand your platform, craft a proper house with storage, a crafting hall, and a farm shelter. Place furniture and decorations from your island menu. Begin using the Island Upgrade menu. Every block laid increases your island's worth and unlocks new /is upgrades. Make this place worthy of legend. These early investments in housing and upgrades will multiply in prestige, letting your empire rise in hours instead of days while you enjoy the creative side sooner.'";
                target = 25 + sub * 6;
            } else {
                cat = Quest.QuestCategory.CHALLENGE;
                title = "Heart of the Sky " + sub + ": First Minions & Trade";
                desc = "Elder: 'Chapter " + chapter + ". Hands alone cannot build an empire. Deploy minions from the Minions menu to automate farming and mining. Sell excess crops and cobble at the Bazaar to earn your first real wealth for the island bank. Begin your collection log — every new item you gather strengthens the Veil. Keep the daily and weekly quests going alongside this — the rhythm of small consistent actions is what turns these early foundations into unstoppable prestige power and allows you to reach the beautiful late-game content faster in every cycle.'";
                target = 3 + sub;
            }
        }
        // ========== PHASE 3: NETHER PORTAL (21-30) ==========
        else if (phase == 3) {
            if (sub <= 5) {
                cat = Quest.QuestCategory.BUILDING;
                title = "The Burning Gate " + sub + ": Frame the Portal";
                desc = "Elder: 'Chapter " + chapter + ". The sky alone will not save us. You must reach the realm of fire. Gather obsidian (through careful mining or by trading for it at the Bazaar). Build a 4x5 obsidian frame. Light it with flint and steel. Step through — the Nether awaits. Prepare armor and food; the flames test the unprepared.'";
                target = 1;
            } else {
                cat = Quest.QuestCategory.COMBAT;
                mobType = "BLAZE";
                title = "The Burning Gate " + sub + ": Rods of the First Flame";
                desc = "Elder: 'Chapter " + chapter + ". Within the fortress of netherrack and soul sand, blazes guard the rods needed for eyes of ender. Hunt blazes carefully — their fireballs are deadly. Use minions back home to farm rods while you explore. These rods are the key that will one day open the final gate to the Dragon.'";
                target = 6 + sub;
            }
            if (chapter == 30) {
                desc += " The Nether dimension is now fully unlocked for your island. The Elder smiles.";
            }
        }
        // ========== PHASE 4: NETHER DEPTHS (31-40) ==========
        else if (phase == 4) {
            if (sub % 3 == 1) {
                cat = Quest.QuestCategory.COMBAT;
                mobType = "WITHER_SKELETON";
                title = "Fortress of Ash " + sub + ": Skulls in the Dark";
                desc = "Elder: 'Chapter " + chapter + ". Deeper in the nether fortresses stalk wither skeletons. Their skulls are required to summon a wither and to complete the End portal frame. Fight them with care and good gear. Slayer quests practiced in the Overworld will serve you here. Bring back skulls and bones for the great work.'";
                target = 8 + sub;
            } else if (sub % 3 == 2) {
                cat = Quest.QuestCategory.BUILDING;
                title = "Fortress of Ash " + sub + ": Outpost in Hell";
                desc = "Elder: 'Chapter " + chapter + ". A safe haven in the flames. Using netherrack, basalt, blackstone and your growing resources, construct a small fortified base with chests, a portal room, and even a small farm of nether wart. Your island housing skills now extend across dimensions. Survive the heat by preparing potions and fire protection.'";
                target = 30 + sub * 2;
            } else {
                cat = Quest.QuestCategory.TRADING;
                title = "Fortress of Ash " + sub + ": Tears & Markets of Fire";
                desc = "Elder: 'Chapter " + chapter + ". The ghasts that sail the red sky weep tears of value. Hunt them or trade for their tears at the Bazaar. Quartz, glowstone, and magma blocks flow through the markets. Use the economy of the Nether to fund your growing legend. Every coin in the island bank is another thread mended.'";
                target = 5 + sub;
            }
        }
        // ========== PHASE 5: NETHER MASTERY (41-50) ==========
        else if (phase == 5) {
            if (sub <= 4) {
                cat = Quest.QuestCategory.COMBAT;
                mobType = (sub == 1 || sub == 2) ? "GHAST" : "MAGMA_CUBE";
                title = "Lords of Flame " + sub + ": Masters of the Crimson";
                desc = "Elder: 'Chapter " + chapter + ". The true lords reveal themselves. Ghasts and magma cubes rule these lands. Master their patterns. Collect more tears and magma cream. These resources let you craft powerful items and eyes of ender in bulk. The Veil trembles as your power grows.'";
                target = 6 + sub * 2;
            } else if (sub <= 7) {
                cat = Quest.QuestCategory.CHALLENGE;
                title = "Lords of Flame " + sub + ": Eye of the Storm";
                desc = "Elder: 'Chapter " + chapter + ". Combine blaze rods from your fortress farms with ender pearls won from the Overworld. Craft Eyes of Ender. Each one is a small star that will guide you to the final confrontation. Stockpile at least a dozen. The End draws near. Use your island bank wealth to buy any missing pieces at the Bazaar.'";
                target = 4 + sub;
            } else {
                cat = Quest.QuestCategory.BUILDING;
                title = "Lords of Flame " + sub + ": The Bridge to Eternity";
                desc = "Elder: 'Chapter " + chapter + ". Build a safe bridge or platform in the nether for future expeditions. Fortify your portal room. Add lighting, storage, and even a small villager trading hall if you can lure them. Your mastery of building now serves you in every dimension. Prepare your best gear for the journey ahead.'";
                target = 20 + sub * 3;
            }
            if (chapter == 50) desc += " All nether threats bow before the one who will face the Dragon.";
        }
        // ========== PHASE 6: END PREP (51-60) ==========
        else if (phase == 6) {
            if (sub <= 5) {
                cat = Quest.QuestCategory.CHALLENGE;
                title = "Gaze into the Void " + sub + ": Stars in Your Hands";
                desc = "Elder: 'Chapter " + chapter + ". You now hold the means to open the End portal. Craft and collect enough Eyes of Ender. Throw them in the Overworld to locate the stronghold, or use them directly if you have the coordinates from your explorations. The final gate will demand twelve eyes to activate. This is the culmination of everything you have built.'";
                target = 6 + sub;
            } else {
                cat = Quest.QuestCategory.COMBAT;
                mobType = "ENDERMAN";
                title = "Gaze into the Void " + sub + ": Pearls from the Void";
                desc = "Elder: 'Chapter " + chapter + ". Ender pearls are the final component. Hunt endermen at night or in the warped forests of the nether. Be patient and precise — they teleport. Your combat skill from earlier chapters will keep you alive. Gather many; some will be needed for the portal itself and for later shulker hunts.'";
                target = 10 + sub * 2;
            }
            if (chapter == 60) {
                desc += " The End dimension is now unlocked. The Elder grows quiet with anticipation.";
            }
        }
        // ========== PHASE 7: OUTER END (61-70) ==========
        else if (phase == 7) {
            if (sub % 3 == 1) {
                cat = Quest.QuestCategory.COMBAT;
                mobType = "SHULKER";
                title = "Chorus of the Beyond " + sub + ": Shells of the Void";
                desc = "Elder: 'Chapter " + chapter + ". The outer islands float in silence. Shulkers guard them. Defeat shulkers to claim their shells — these become shulker boxes, the greatest storage a skyblock master can own. Move carefully on the islands; one misstep sends you into the void forever. Bring slow falling or careful bridging.'";
                target = 5 + sub;
            } else if (sub % 3 == 2) {
                cat = Quest.QuestCategory.BUILDING;
                title = "Chorus of the Beyond " + sub + ": A Home at the Edge";
                desc = "Elder: 'Chapter " + chapter + ". Establish a small outpost on an outer end island. Use end stone, purpur, and chorus plants for building. Create a safe return portal and storage. Your housing and building expertise now reaches the very edge of existence. This base will serve future dragon fights and exploration.'";
                target = 15 + sub * 3;
            } else {
                cat = Quest.QuestCategory.CHALLENGE;
                title = "Chorus of the Beyond " + sub + ": The Final Preparations";
                desc = "Elder: 'Chapter " + chapter + ". Harvest chorus fruit and craft the last gear you will need. Upgrade your tools, enchantments, and armor using everything the dimensions have given you. Visit the island upgrades menu one last time. Speak with the Codex. The Dragon awaits those who are truly ready.'";
                target = 8 + sub;
            }
        }
        // ========== PHASE 8: DRAGON (71-80) ==========
        else if (phase == 8) {
            cat = Quest.QuestCategory.CHALLENGE;
            if (sub == 1) {
                title = "The Last Lock 1: The Gateway Opens";
                desc = "Elder: 'Chapter " + chapter + ". You have arrived at the end of all beginnings. Throw the twelve Eyes of Ender into the portal frame hidden in the stronghold. The air itself screams as the gate opens. Step into the End. The central island of obsidian and black pillars awaits like a throne room for a god that should never have existed. Build a small safe platform if you must — the void is patient. This is the moment every block you ever placed, every minion you ever deployed, every trade you ever made has been preparing you for. The Dragon is not just a boss. It is the heart of the last great tear in the Veil. When it falls, the first true mending begins. Remember your dailies even here — they are the constant that made this possible.'";
                target = 1;
            } else if (sub <= 5) {
                title = "The Last Lock " + sub + ": Shatter the Crystals";
                desc = "Elder: 'Chapter " + chapter + ". The Dragon draws power from the crystals atop obsidian pillars. Destroy them with arrows, snowballs, or your best ranged attacks. Watch for the dragon's breath attack — it creates lingering clouds of death. Build cover with end stone blocks. Use water carefully. This is a true test of all your skyblock mastery — the minions you built, the gear from nether and end, the skills you leveled, the trades that funded it all. When the last crystal shatters, the beast will know fear. In future prestiges, this moment will come faster, but the triumph will feel just as deep. The continuous play of previous chapters has given you the power and wisdom to succeed.'";
                target = 4;
            } else {
                title = "The Last Lock " + sub + ": Strike the Heart";
                desc = "Elder: 'Chapter " + chapter + ". The crystals are gone. Now face the beast itself. Dodge, strike the head when it dives low, use beds for burst damage if you dare, or simply wear it down with skill and enchanted weapons. When it falls, the Veil will sing. Claim the egg. The story reaches its first great climax. Prestige will let you relive this glory with multipliers — the ultimate reward for the weaver who never stopped mending.'";
                target = (sub >= 9) ? 1 : 3;
            }
            if (chapter == 80) {
                desc += " The Dragon has fallen. The Elder weeps with pride. Prestige now opens the path to eternity. Return stronger each time; the tale grows with you.";
            }
        }
        // ========== PHASE 9: PRESTIGE DAWN (81-90) ==========
        else if (phase == 9) {
            if (sub % 2 == 0) {
                cat = Quest.QuestCategory.CHALLENGE;
                title = "Threads Rewoven " + sub + ": The First Rebirth";
                desc = "Elder: 'Chapter " + chapter + ". You have slain the Dragon and mended the greatest tear. But the Veil is vast. Open the Prestige menu. Rebirth your island. You will start anew, yet with powerful multipliers on all gains. Your previous empire's knowledge remains in your heart. Rebuild faster. Conquer the story again at greater scale. This is how legends are forged across cycles. Do your dailies immediately upon return — they are the habits that make the multipliers sing from day one and let you reach the beautiful housing, museum, and automation projects much quicker each time.'";
                target = 1 + (sub / 3);
            } else {
                cat = Quest.QuestCategory.BUILDING;
                title = "Threads Rewoven " + sub + ": Monument to the Eternal";
                desc = "Elder: 'Chapter " + chapter + ". In your new prestige life, construct a monument that honors the journey — a tower, a dragon memorial, or a grand hall using blocks from every dimension. Place rare furniture and perhaps even display items in a museum wing. Let future visitors (and your own future runs) see the story written in blocks. Housing and building now carry the weight of legend. These monuments and museum pieces become your personal history books — in future prestiges you will stand before them and feel the full weight of the 0-100 tale, motivating even grander continuous creations.'";
                target = 40 + sub * 4;
            }
            xp += 400;
            money += 600;
        }
        // ========== PHASE 10: ETERNAL (91-100) ==========
        else if (phase == 10) {
            if (sub <= 4) {
                cat = Quest.QuestCategory.CHALLENGE;
                title = "Eternal Weaver " + sub + ": The Cycle Deepens";
                desc = "Elder: 'Chapter " + chapter + ". Each prestige makes you stronger. Repeat the great works at new heights: command dozens of specialized minions, fill massive farms, complete every collection milestone, push skills and island upgrades to their limit. Defeat prestige-enhanced dimension bosses and higher slayer tiers. The multipliers compound. Your name echoes across islands. The Veil recognizes its champion. Never forget the small continuous stitches — dailies, minion checks, bazaar visits — they are what let the machine run while you dream bigger in each new life.'";
                target = 6 + sub;
            } else if (sub <= 7) {
                cat = Quest.QuestCategory.BUILDING;
                title = "Eternal Weaver " + sub + ": Cities in the Sky";
                desc = "Elder: 'Chapter " + chapter + ". Build on a scale you once thought impossible. Create automated factories with dozens of minions across multiple islands. Design beautiful districts, redstone wonders, and public works that future players will admire. Every island upgrade tier reached, every furniture set unlocked, every collection maxed — all of it weaves new strength into the Veil. You are no longer a visitor. You are its guardian. Housing and museum pieces become the living record that makes prestige runs feel like coming home to a story you already wrote.'";
                target = 80 + sub * 10;
            } else {
                cat = Quest.QuestCategory.CHALLENGE;
                title = "Eternal Weaver " + sub + ": The Veil Remembers";
                desc = "Elder: 'Chapter " + chapter + ". The final chapters. This is where the story becomes legend instead of memory. Complete the most difficult challenges the skyblock worlds can offer. Max your island level and worth until the top islands speak your name. Raise one last grand monument using blocks, furniture, and memories from every dimension you conquered. Do your dailies and weeklies without fail — they are the daily mending that builds the power for prestige. Use specialized minions, level every skill, trade at the bazaar, decorate your housing, curate the museum. When you claim chapter 100, the story does not end — it becomes eternal. You may prestige again and again. Each time the journey is deeper, faster, and more glorious because you carry the wisdom of every previous cycle and the multipliers that reward continuous play. You are no longer the one who mends the Veil. You are the reason the Veil still exists. The sky itself will remember you.'";
                target = 3 + (sub - 7);
            }
            xp += 800;
            money += 1200;
            if (chapter == 100) {
                desc += " The Fractured Veil is whole because of you. Prestige without end, legend without limit. Every future cycle will remember the weaver who refused to let the sky fall. The daily weave, the grand builds, the slayers — all of it lives on in the eternal loop.";
                cat = Quest.QuestCategory.CHALLENGE;
            }
        }

        // Additional new depth for more chapters to encourage continuous play
        if (chapter == 15) {
            desc += " The corrupted are pushed back. 'Your blade sings with the first true power. Collections and trades fuel the empire. Keep the dailies alive — they are the steady mending that makes prestige runs soar. In future lives this phase becomes a joyful warm-up for the grander tale.'";
        }
        if (chapter == 35) {
            desc += " A true nether foothold. 'Build where others fear. The flames forge endurance. Your slayer practice pays here. Trade and survive; continuous economy in hell prepares you for the void. Prestige will let you turn this trial into a profitable, quick base for later conquests.'";
        }
        if (chapter == 55) {
            desc += " The End fully opens. 'Shulkers, chorus, the edge of existence. Build your outpost proudly with furniture and storage. These gifts from the void will transform your entire playstyle. In prestige you will rush here to set up the advanced storage that lets you automate even faster.'";
        }
        if (chapter == 85) {
            desc += " The first monuments of rebirth. 'Use blocks from every dimension to honor what was. The museum wing grows with relics. Continuous play (dailies, housing, museum curation) now feels like the natural heartbeat of the legend. Prestige rewards those who never let a day go un-mended.'";
        }
        if (chapter == 4) {
            desc += " Your first real tools emerge. 'The crafting table and furnace turn raw stone into purpose. Mining skill levels grant speed and fortune that compound across every prestige. These foundational choices echo in faster builds and richer yields every cycle.'";
        }
        if (chapter == 7) {
            desc += " Automation begins its song. 'The first minion toils while you dream of empires. Deploy specialized ones early — they free you for skills, exploration, and the grand story. In prestige these loyal workers multiply your output from hour one.'";
        }
        if (chapter == 13) {
            desc += " Shadows test your growing light. 'Combat and slayers forge the blade needed for dimensions ahead. Pair every fight with daily mends and minion checks — the rhythm of continuous play turns early battles into the power that makes late-game prestige runs feel godlike.'";
        }
        if (chapter == 17) {
            desc += " Empire takes root. 'Collections and trades weave wealth and purpose. Bazaar visits and upgrades become ritual. These small consistent acts build the foundation that prestige multipliers will explode into massive early advantages and beautiful late-game creations.'";
        }
        if (chapter == 22) {
            desc += " Obsidian's promise. 'The portal frame rises from careful gathering or wise trades. The Nether's fire will temper you. Keep home systems running — dailies and minions are your anchor while you brave the flames and return wiser each prestige cycle.'";
        }
        if (chapter == 27) {
            desc += " Blaze rods crackle with destiny. 'Harvested from fire, these keys open the End. Minions farm while you push. Balance adventure with daily mending — this harmony is the weaver's art, rewarded with faster conquests and more time for legacy in every rebirth.'";
        }
        if (chapter == 32) {
            desc += " Fortress depths yield grim keys. 'Wither skulls for the End, lessons in power and patience. Slayer experience pays dividends. In prestige these trials become efficient steps, letting you focus on the beautiful outposts and monuments that define your legend.'";
        }
        if (chapter == 38) {
            desc += " Nether outpost thrives. 'A bastion of storage and trade in the flames. Your building skills now span dimensions. Continuous economy here funds the final push. Prestige turns this endurance into a quick, profitable base for grander works.'";
        }
        if (chapter == 43) {
            desc += " Eyes of Ender take form. 'Blaze and pearl become stars that pierce the final veil. Stockpile with bank wealth and minion help. Every prior chapter converges here — prestige will let you craft these keys faster and savor the drama of the End.'";
        }
        if (chapter == 48) {
            desc += " Stronghold found. 'The portal frame awaits in darkness. The final dimension cracks open. Prepare with full arsenal from 48 chapters. Continuous play has built this moment; rebirths make it come sooner for even greater legacies.'";
        }
        if (chapter == 53) {
            desc += " Shulkers fall, shells claimed. 'Storage transcendent, mobility divine. Build your void outpost with furniture and chorus farms. These gifts from the edge change how you carry the world. Prestige makes these advanced setups your early-game edge.'";
        }
        if (chapter == 58) {
            desc += " Chorus mastery at the edge. 'The void's fruit harvested, outposts rise where few dare. Place a bed even here — the edge of existence can feel like home. Mastery here echoes in every cycle, freeing time for the Dragon and eternal prestige glory.'";
        }
        if (chapter == 63) {
            desc += " End outpost rises. 'A true home among the stars. Shulker boxes revolutionize storage. Place furniture, farm chorus, defend against the void. Continuous exploration here builds the courage and infrastructure that make prestige End runs breathtakingly fast.'";
        }
        if (chapter == 68) {
            desc += " Final preparations before the lock. 'Upgrade, enchant, brew, gather the last pearls. Minions toil faithfully at home. Every system touched across 68 chapters has led to this moment of truth. Prestige will let you arrive here with godlike power.'";
        }
        if (chapter == 73) {
            desc += " Crystals shatter under will. 'The beast weakens with each ranged strike. Build cover, respect the breath. This victory frees the egg and opens prestige's door. Your continuous daily play has prepared the multipliers for glory in every retelling.'";
        }
        if (chapter == 78) {
            desc += " The Dragon falls. 'The egg is yours, the veil mended. Prestige now — return stronger with the power of this triumph. The story is eternal only because weavers like you choose to re-weave it with ever-greater wisdom and might.'";
        }
        if (chapter == 83) {
            desc += " Monuments rise in the new life. 'Blocks from every dimension tell the full journey. Add rare furniture and museum wings so the tale lives in stone. Future cycles will stand before them and feel the weight — inspiring even grander continuous creations.'";
        }
        if (chapter == 88) {
            desc += " Your second life strengthens. 'Automation and beauty in harmony. While minions labor you chase slayers, complete collections, design districts. Continuous play (dailies to grand builds) is now your nature. Prestige lets you conduct this symphony from the first hour.'";
        }
        if (chapter == 92) {
            desc += " The weaver commands legions. 'Dozens of specialized minions, capped skills, thriving bank. Focus on the beautiful and the eternal. Never skip the daily mending — it is the secret that multiplies everything in prestige and turns the 0-100 tale into living legend.'";
        }
        if (chapter == 98) {
            desc += " The legend is sealed. 'All systems in harmony. Dailies, minions, housing, museum, bazaar, slayers, upgrades — the island itself sings your story. Claim the end and rebirth. Prestige is the reward for the continuous weaver who never let the sky fall.'";
        }

        if (chapter == 64) {
            desc += " Daily rituals become sacred. 'The small acts between chapters — harvests, trades, minion checks — are the power source. Prestige turns the consistent weaver into a force of nature from the first day.'";
        }
        if (chapter == 69) {
            desc += " Void mastery. 'Shulker boxes let you carry empires. Build outposts that feel home. Continuous exploration builds courage for the Dragon. Prestige makes these tools available early for godlike automation.'";
        }
        if (chapter == 72) {
            desc += " The final test begins. 'Pillars and breath teach respect. All prior chapters converge. Continuous play prepared this moment — prestige will make the victory a celebration of the full tale.'";
        }
        if (chapter == 76) {
            desc += " Legacy monuments. 'Build for those who follow and your future self. Housing and museum preserve the story. These choices compound in prestige, letting you start with wisdom and beauty from day one.'";
        }
        if (chapter == 79) {
            desc += " Power peaks. 'Skills and systems sing. The island is ready. Continuous daily mending has built the foundation for eternal prestige glory.'";
        }

        // Return built chapter quest. (All depth added via phase descriptions and ~25 unique milestone if(chapter) blocks above.)
        return createStoryQuest(cat, title, desc, target, xp, money, chapter, mobType);
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
        if (chapter > 1) {
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

                // Elder flavor for story chapters - adds depth & encourages next steps without overwhelming
                if (quest.getQuestLine() == Quest.QuestLine.MAIN_STORY) {
                    switch (quest.getChapter()) {
                        case 1 -> {
                            player.sendMessage("§5The Elder: 'The first tear opens. Build your shelter and listen to the sky. You are the one who will mend the Veil.'");
                            player.sendMessage("§7§oBefore the call there was only silence. Now the story begins with you.");
                        }
                        case 2 -> player.sendMessage("§5Elder: 'The first blocks feel solid. The void notices. Keep building — every wall is a prayer against the fracture. In prestige, this memory returns with new power.'");
                        case 5 -> player.sendMessage("§5The Elder whispers: 'Good. Minions free your hands for greater works — skills, trade, the Nether awaits. Daily harvests now feel like part of the eternal weave.'");
                        case 6 -> player.sendMessage("§5Elder: 'The first minion awakens. Automation is the key to continuous legend. Let it work while you push the story forward.'");
                        case 8 -> player.sendMessage("§5Elder: 'A home takes shape. These comforts will guide you in every future cycle. Build with heart.'");
                        case 9 -> player.sendMessage("§5Elder: 'The island gains a heartbeat. Upgrades are investments in eternity. Each level strengthens your future prestiges.'");
                        case 10 -> player.sendMessage("§5Elder: 'Empire's first breath. Collections and trades weave the story. Continuous play mends the Veil in quiet rhythm.'");
                        case 13 -> player.sendMessage("§5Elder: 'Shadows test your resolve. Slayer paths open — they prepare you for what the dimensions will throw at you.'");
                        case 16 -> player.sendMessage("§5Elder: 'Trade fuels the empire. The Bazaar is your ally. Small consistent efforts build the legend.'");
                        case 21 -> player.sendMessage("§5§lThe Burning Gate opens. §7Elder: 'Obsidian and courage. The Nether will forge you or break you. Return with rods and wisdom.'");
                        case 22 -> player.sendMessage("§5Elder: 'The gate is lit. Step with purpose. The flames will test and teach endurance.'");
                        case 26 -> player.sendMessage("§5Elder: 'First steps in fire. The heat forges more than tools — it forges the will needed for the stars.'");
                        case 27 -> player.sendMessage("§5Elder: 'Rods secured. Minions farm while you push. Layer the tale with survival.'");
                        case 29 -> player.sendMessage("§5Elder: 'Blaze rods in hand. The path to the End is clearer. Balance toil and adventure.'");
                        case 15 -> player.sendMessage("§5Elder: 'You have tamed the land. The corrupted now fear your blade. Soon the gate of fire will be yours to open.'");
                        case 20 -> player.sendMessage("§5The Elder nods: 'Collections bloom. The Bazaar sings your name. The first true step toward other worlds is near.'");








                        case 25 -> player.sendMessage("§5§lThe Gate draws close. §7Elder: 'Gather obsidian and courage. The Nether will burn away weakness.'");
                        case 30 -> player.sendMessage("§5§lNether is yours! §7Elder: 'Conquer its bosses and prepare for the End. The Veil thins.'");
                        case 31 -> player.sendMessage("§5Elder: 'The fortress looms. Skulls for the End. Slayer practice pays off.'");
                        case 35 -> player.sendMessage("§5Elder: 'The fortresses whisper your name in fear. Wither skulls are the currency of the final gate.'");
                        case 36 -> player.sendMessage("§5Elder: 'A bastion in the blaze. Trade and survive; the flames forge the will.'");
                        case 39 -> player.sendMessage("§5Elder: 'Ghast tears fuel the story's progress. The nether's gifts are many.'");
                        case 40 -> player.sendMessage("§5Elder: 'Fortresses have fallen before you. Skulls and tears fill your vaults. You are becoming legend.'");
                        case 45 -> player.sendMessage("§5The Elder: 'The stars of the End are nearly in your grasp. Use the economy and your minions to finish the eyes.'");
                        case 50 -> player.sendMessage("§5The Elder: 'The Lords of Flame bow. Eyes of Ender wait for your hand. The final veil parts soon.'");
                        case 55 -> player.sendMessage("§5§lEnder eyes complete. §7Elder: 'Throw them. Find the portal. The Dragon sleeps, but not for long.'");
                        case 60 -> player.sendMessage("§5§lThe End opens! §7Elder: 'Gather eyes, defeat shulkers, face the Dragon. This is the heart of it all.'");
                        case 65 -> player.sendMessage("§5Elder: 'The silence of the outer islands is broken by your footsteps. Shulker boxes will let you carry the world.'");
                        case 70 -> player.sendMessage("§5Elder: 'The outer islands are tamed. Shulker shells and chorus fruit mark your mastery. The Last Lock remains.'");
                        case 75 -> player.sendMessage("§6§lThe pillars await. §eElder: 'Shatter every crystal. Leave the beast no power but its own breath.'");
                        case 80 -> player.sendMessage("§6§lThe Dragon falls! §eElder: 'Now Prestige to continue the eternal legend. The Veil sings your name.'");
                        case 82 -> player.sendMessage("§6Elder: 'The egg is yours. The first rebirth calls. Open the Prestige menu and begin the cycle anew, stronger.'");
                        case 85 -> player.sendMessage("§6Elder: 'The first rebirth is complete. Multipliers flow through you. Rebuild the empire swifter and grander.'");
                        case 88 -> player.sendMessage("§6The Elder: 'Your monument rises. Future runs will look upon it and know a legend walked here.'");
                        case 90 -> player.sendMessage("§6Elder: 'Monuments rise from your first rebirth. The dailies and small systems you tended now bear fruit with power. Build even greater — the museum and housing will carry your legacy into the next cycle.'");
                        case 93 -> player.sendMessage("§6Elder: 'Dozens of minions serve you now. The sky itself bends to your will. Prepare the final chapters. Level your skills and build grander than before.'");
                        case 95 -> player.sendMessage("§6§lNearly eternal. §eElder: 'One final weave. Max your power. The Veil will remember this forever. Use the museum to display your journey.'");
                        case 97 -> player.sendMessage("§6Elder: 'The final weaves tighten. Your name is woven into the Veil. Ch.100 is the crown, but prestige lets you wear it again with new glory.'");
                        case 98 -> player.sendMessage("§6Elder: 'Legends are forged in the last steps. Your monuments stand tall. The story is yours. Claim the end and rebirth. All systems harmony from daily mends to grand builds.'");
                        case 99 -> player.sendMessage("§6The Elder’s voice is proud: 'One chapter remains. When it is claimed, you will have lived the full tale. Then you will live it again — better. The story encourages you to use every system the server offers. Continuous play leads to eternal prestige glory.'");
                        case 100 -> player.sendMessage("§6§lThe Veil is eternal! §eYou are the master of skyblock. Every daily, minion, trade, and build wove this. Prestige repeatedly — each telling deeper because of the wisdom and power you carry. Thank you for answering the call. The sky remembers you forever.'");
                        case 0 -> player.sendMessage("§5Elder: 'The call echoes still. This is where it all begins — the quiet before the first block. In prestige you will hear it again, and know the full weight of the journey ahead.'");
                        case 64 -> player.sendMessage("§5Elder: 'The daily weave deepens. Quick dailies and minion checks are the power source. Prestige turns consistency into godlike force from day one.'");
                        case 69 -> player.sendMessage("§5Elder: 'Void mastery. Shulker boxes and outposts change everything. Continuous exploration builds courage. Prestige makes these tools early advantages.'");
                        case 72 -> player.sendMessage("§6Elder: 'The final test. Pillars teach respect. Continuous play prepared this. Prestige makes victory a celebration of the tale.'");
                        case 76 -> player.sendMessage("§6Elder: 'Legacy monuments. Build for future self. Housing preserves the story. These compound in prestige for wisdom from the start.'");
                        case 79 -> player.sendMessage("§6Elder: 'Power peaks. Skills aligned. Continuous mending built the foundation for eternal prestige glory.'");
                        // Additional unique Elder messages for depth (see quest descriptions)
                    }
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
                if (quest.getQuestLine() == Quest.QuestLine.MAIN_STORY && quest.getChapter() >= 91) {
                    player.sendMessage("§6§lCONGRATULATIONS! §eYou have completed the full 100-chapter Fractured Veil story and slain the Ender Dragon!");
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

                    // Story-based dimension gates: claiming key chapters unlocks dimensions.
                    // This enforces the narrative: master Overworld before Nether, Nether before End.
                    if (isl != null) {
                        if (quest.getChapter() == 30) { // After nether entry phase
                            isl.completeMilestone("nether_access_milestone", 800);
                            isl.unlockDimension("nether");
                            player.sendMessage("§a§lNether Unlocked! §7Your island may now access the Nether dimension.");
                        }
                        if (quest.getChapter() == 60) { // After end prep phase
                            isl.completeMilestone("end_access_milestone", 1200);
                            isl.unlockDimension("end");
                            player.sendMessage("§a§lThe End Unlocked! §7Your island may now access the End (prepare for the Dragon).");
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
                    plugin.getDatabaseManager().saveActiveQuest(islandId, quest);  // persist full story quest for restarts
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
