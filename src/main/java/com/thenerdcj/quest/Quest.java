package com.thenerdcj.quest;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class Quest {

    public enum QuestCategory {
        MINING,
        FARMING,
        COMBAT,
        BUILDING,
        EXPLORATION,
        TRADING,
        CHALLENGE
    }

    public enum QuestType {
        DAILY,
        WEEKLY,
        FIRST   // Onboarding / early-game first-island quests (one-time per island life, never expire, special rewards)
    }

    public enum QuestLine {
        ONBOARDING,
        MAIN_STORY
    }

    private final String id;
    private final String title;
    private final String description;
    private final QuestCategory category;
    private final QuestType type;

    private int progress;
    private final int target;

    private final int rewardXp;
    private final int rewardMoney;

    private boolean completed;
    private boolean claimed;
    private final long expiryTime;

    private QuestLine questLine = QuestLine.ONBOARDING;
    private int chapter = 0;

    // Stored prerequisites for chains / "Unlocked by" display (new GUI + story linear)
    private final List<String> prerequisites = new ArrayList<>();
    // Extra typed rewards granted on claim (in addition to base xp/money)
    private final List<QuestReward> extraRewards = new ArrayList<>();

    // For COMBAT story chapters: only this specific mob type (e.g. "ZOMBIE", "BLAZE", "SHULKER") counts toward progress.
    // null = any hostile in the COMBAT category.
    private String requiredMobType;

    // ==================== CONSTRUCTOR ====================

    public Quest(String id, String title, String description,
                 QuestCategory category, QuestType type,
                 int progress, int target,
                 int rewardXp, int rewardMoney,
                 boolean completed, long expiryTime,
                 QuestLine questLine, int chapter) {
        this(id, title, description, category, type, progress, target, rewardXp, rewardMoney,
             completed, expiryTime, questLine, chapter, null, null, null);
    }

    public Quest(String id, String title, String description,
                 QuestCategory category, QuestType type,
                 int progress, int target,
                 int rewardXp, int rewardMoney,
                 boolean completed, long expiryTime,
                 QuestLine questLine, int chapter,
                 List<String> prerequisites, List<QuestReward> extraRewards) {
        this(id, title, description, category, type, progress, target, rewardXp, rewardMoney,
             completed, expiryTime, questLine, chapter, prerequisites, extraRewards, null);
    }

    public Quest(String id, String title, String description,
                 QuestCategory category, QuestType type,
                 int progress, int target,
                 int rewardXp, int rewardMoney,
                 boolean completed, long expiryTime,
                 QuestLine questLine, int chapter,
                 List<String> prerequisites, List<QuestReward> extraRewards,
                 String requiredMobType) {

        this.id = (id != null && !id.isEmpty()) ? id : UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.category = category;
        this.type = type;
        this.progress = progress;
        this.target = target;
        this.rewardXp = rewardXp;
        this.rewardMoney = rewardMoney;
        this.completed = completed;
        this.claimed = false;
        this.expiryTime = expiryTime;
        this.questLine = questLine != null ? questLine : QuestLine.ONBOARDING;
        this.chapter = chapter;
        if (prerequisites != null) this.prerequisites.addAll(prerequisites);
        if (extraRewards != null) this.extraRewards.addAll(extraRewards);
        this.requiredMobType = requiredMobType;
    }

    // ==================== GETTERS ====================

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public QuestCategory getCategory() {
        return category;
    }

    public QuestType getType() {
        return type;
    }

    public int getProgress() {
        return progress;
    }

    public int getTarget() {
        return target;
    }

    public int getRewardXp() {
        return rewardXp;
    }

    public int getRewardMoney() {
        return rewardMoney;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public QuestLine getQuestLine() { return questLine; }

    public int getChapter() { return chapter; }

    public boolean isCompleted() {
        return completed || progress >= target;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public boolean isExpired() {
        if (type == QuestType.FIRST) return false; // Onboarding first quests never expire
        return System.currentTimeMillis() > expiryTime;
    }

    // ==================== SETTERS / MODIFIERS ====================

    public void setProgress(int progress) {
        this.progress = Math.max(0, progress);
        checkCompletion();
    }

    public void addProgress(int amount) {
        this.progress += amount;
        checkCompletion();
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public void checkCompletion() {
        if (progress >= target) {
            this.completed = true;
        }
    }

    // ==================== ADVANCED FEATURES (for QuestDetailGUI, prereqs, streaks, rerolls) ====================

    public String getQuestLineDisplay() {
        if (questLine == QuestLine.MAIN_STORY) return "Main Story";
        if (questLine == QuestLine.ONBOARDING) return "Onboarding";
        return "General";
    }

    public List<String> getPrerequisites() {
        if (!prerequisites.isEmpty()) {
            return new ArrayList<>(prerequisites);
        }
        // Fallback computed for legacy story chapters
        List<String> prereqs = new ArrayList<>();
        if (questLine == QuestLine.MAIN_STORY && chapter > 1) {
            prereqs.add("Complete Chapter " + (chapter - 1));
        }
        return prereqs;
    }

    public boolean hasMultipleObjectives() {
        return false; // basic single objective for now
    }

    public List<QuestObjective> getObjectives() {
        return new ArrayList<>(); // empty for basic quests
    }

    public List<QuestReward> getExtraRewards() {
        return new ArrayList<>(extraRewards);
    }

    public void addExtraReward(QuestReward reward) {
        if (reward != null) extraRewards.add(reward);
    }

    public String getRequiredMobType() {
        return requiredMobType;
    }

    // Inner classes for advanced quest features
    public static class QuestObjective {
        private final String description;
        private final int progress;
        private final int target;

        public QuestObjective(String description, int progress, int target) {
            this.description = description;
            this.progress = progress;
            this.target = target;
        }

        public String getDescription() { return description; }
        public String getProgressBar() {
            int bars = 10;
            int filled = (int) ((double) progress / target * bars);
            StringBuilder sb = new StringBuilder("§a");
            for (int i = 0; i < bars; i++) sb.append(i < filled ? "█" : "░");
            return sb.toString();
        }
        public boolean isCompleted() { return progress >= target; }
        public int getProgress() { return progress; }
        public int getTarget() { return target; }
    }

    public static class QuestReward {
        private final String description;

        public QuestReward(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }
}