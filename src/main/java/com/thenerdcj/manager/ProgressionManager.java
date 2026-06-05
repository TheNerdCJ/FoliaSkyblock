package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.Island.Skill;
import com.thenerdcj.island.IslandMilestoneCompleteEvent;
import com.thenerdcj.island.IslandSkillLevelUpEvent;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProgressionManager
 * 
 * Handles deep island progression:
 * - Per-island Skills (Mining, Farming, Combat, etc.) with XP and levels
 * - Milestones (achievements that grant bonus XP or unlocks)
 * 
 * TODOs addressed:
 * 1. Full skill XP tracking, leveling, and persistence
 * 2. Milestone tracking, completion detection, and event firing
 * 
 * Integrates with:
 * - Island (via islandKey or direct reference)
 * - DatabaseManager (existing save/load methods for skills & milestones)
 * - IslandManager
 * - IslandMilestoneCompleteEvent (and can fire skill level up events)
 */
public class ProgressionManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;

    // In-memory cache: islandKey -> (Island.Skill -> progress)
    private final Map<String, Map<Skill, SkillProgress>> skillCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> milestoneCache = new ConcurrentHashMap<>();

    public ProgressionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    // Skill enum is now reused from Island.Skill for consistency with DatabaseManager

    public static class SkillProgress {
        public double xp;
        public int level;

        public SkillProgress(double xp, int level) {
            this.xp = xp;
            this.level = level;
        }
    }

    // ==================== SKILL METHODS ====================

    public CompletableFuture<Void> loadSkillsForIsland(String islandKey) {
        return databaseManager.loadIslandSkills(islandKey).thenAccept(data -> {
            Map<Skill, SkillProgress> progressMap = new EnumMap<>(Skill.class);
            for (Map.Entry<Skill, Object[]> entry : data.entrySet()) {
                Object[] arr = entry.getValue();
                progressMap.put(entry.getKey(), new SkillProgress((double) arr[0], (int) arr[1]));
            }
            skillCache.put(islandKey, progressMap);
        });
    }

    public void addSkillXp(Island island, Skill skill, double amount) {
        if (island == null || skill == null || amount <= 0) return;

        String islandKey = island.getId(); // Uses owner + dimension

        skillCache.computeIfAbsent(islandKey, k -> new EnumMap<>(Skill.class));

        Map<Skill, SkillProgress> skills = skillCache.get(islandKey);
        SkillProgress progress = skills.computeIfAbsent(skill, k -> new SkillProgress(0, 1));

        progress.xp += amount;

        // Check for level up
        while (progress.xp >= getRequiredXpForSkillLevel(progress.level + 1)) {
            progress.xp -= getRequiredXpForSkillLevel(progress.level + 1);
            progress.level++;

            // Optional: Fire skill level up event or give rewards
            Bukkit.getPluginManager().callEvent(new IslandSkillLevelUpEvent(island, skill, progress.level));
            MessageUtil.info(plugin.getLogger(), "§a[Progression] Island skill level up: " + skill + " → Lvl " + progress.level);
        }

        // Persist asynchronously
        saveSkills(islandKey);
    }

    private double getRequiredXpForSkillLevel(int level) {
        return 100.0 * level * level; // Quadratic scaling, adjust as needed
    }

    public int getSkillLevel(Island island, Skill skill) {
        if (island == null) return 1;
        String key = island.getId();
        Map<Skill, SkillProgress> skills = skillCache.get(key);
        if (skills == null || !skills.containsKey(skill)) return 1;
        return skills.get(skill).level;
    }

    public double getSkillXp(Island island, Skill skill) {
        if (island == null) return 0;
        String key = island.getId();
        Map<Skill, SkillProgress> skills = skillCache.get(key);
        if (skills == null || !skills.containsKey(skill)) return 0;
        return skills.get(skill).xp;
    }

    private void saveSkills(String islandKey) {
        Map<Skill, SkillProgress> skills = skillCache.get(islandKey);
        if (skills == null) return;

        Map<Skill, Double> xpMap = new EnumMap<>(Skill.class);
        Map<Skill, Integer> levelMap = new EnumMap<>(Skill.class);

        for (Map.Entry<Skill, SkillProgress> entry : skills.entrySet()) {
            xpMap.put(entry.getKey(), entry.getValue().xp);
            levelMap.put(entry.getKey(), entry.getValue().level);
        }

        databaseManager.saveIslandSkills(islandKey, xpMap, levelMap);
    }

    // ==================== MILESTONE METHODS ====================

    public CompletableFuture<Void> loadMilestonesForIsland(String islandKey) {
        return databaseManager.loadIslandMilestones(islandKey).thenAccept(set -> {
            milestoneCache.put(islandKey, new HashSet<>(set));
        });
    }

    public boolean hasCompletedMilestone(Island island, String milestoneId) {
        if (island == null) return false;
        Set<String> completed = milestoneCache.getOrDefault(island.getId(), Collections.emptySet());
        return completed.contains(milestoneId);
    }

    public void completeMilestone(Island island, String milestoneId, double bonusXp) {
        if (island == null || hasCompletedMilestone(island, milestoneId)) return;

        String islandKey = island.getId();
        milestoneCache.computeIfAbsent(islandKey, k -> new HashSet<>()).add(milestoneId);

        // Persist
        databaseManager.saveIslandMilestones(islandKey, milestoneCache.get(islandKey));

        // Fire event
        IslandMilestoneCompleteEvent event = new IslandMilestoneCompleteEvent(island, milestoneId, bonusXp);
        Bukkit.getPluginManager().callEvent(event);

        // Apply bonus XP to main island level if provided
        if (bonusXp > 0) {
            island.addXp(bonusXp);
        }

        MessageUtil.info(plugin.getLogger(), "§a[Progression] Milestone completed: " + milestoneId + " on island " + islandKey);
    }

    // Example predefined milestones (expand in config or enum later)
    public void checkAndCompleteMilestones(Island island) {
        if (island == null) return;

        // Example automatic checks
        if (island.getLevel() >= 10 && !hasCompletedMilestone(island, "reach_level_10")) {
            completeMilestone(island, "reach_level_10", 50);
        }

        if (getSkillLevel(island, Skill.MINING) >= 5 && !hasCompletedMilestone(island, "mining_level_5")) {
            completeMilestone(island, "mining_level_5", 30);
        }

        // Add more milestone checks based on your progression design
    }

    // ==================== UTILITY ====================

    public void loadAllProgressionForIsland(Island island) {
        if (island == null) return;
        String key = island.getId();
        loadSkillsForIsland(key);
        loadMilestonesForIsland(key);
    }

    public void saveAllProgression() {
        for (String key : skillCache.keySet()) {
            saveSkills(key);
        }
        // Milestones are saved on completion
    }
}
