package com.thenerdcj.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    /**
     * QuestLine for structure and chains (Step 1).
     * ONBOARDING = the original 5 parallel FIRST.
     * MAIN_STORY = guided chain teaching features (minions -> farming -> combat -> trading -> bosses) with prerequisites.
     * SIDE = optional flavorful one-time quests.
     */
    public enum QuestLine {
        ONBOARDING,
        MAIN_STORY,
        SIDE,
        EVENT
    }

    /**
     * Simple typed reward descriptor for Step 3 (Rewards & Meaning).
     * Allows quests to grant cosmetics, pending items, rep, boosts, etc. in addition to base XP/money.
     * PtW safe: cosmetic / temporary / progression unlocks only.
     */
    public static class QuestReward {
        public enum Type {
            ISLAND_XP,          // extra island XP (already balanced in IslandManager)
            BANK_MONEY,         // extra to island bank
            COSMETIC_UNLOCK,    // data = cosmetic id (e.g. trail or join message)
            PENDING_ITEM,       // data = material name or key, amount = count (goes to pending/claim later)
            REPUTATION,         // data = category name, amount = rep delta
            TEMP_BOOST          // e.g. temporary sell multiplier or xp boost (future)
        }

        public final Type type;
        public final String data;
        public final int amount;

        public QuestReward(Type type, String data, int amount) {
            this.type = type;
            this.data = data;
            this.amount = Math.max(0, amount);
        }

        public String getDescription() {
            switch (type) {
                case COSMETIC_UNLOCK: return "§dCosmetic: " + (data != null ? data : "special item");
                case PENDING_ITEM: return "§eItem x" + amount + (data != null ? " (" + data + ")" : "");
                case REPUTATION: return "§b+" + amount + " " + (data != null ? data : "") + " Rep";
                case TEMP_BOOST: return "§6Temporary boost";
                default: return type.name() + " x" + amount;
            }
        }
    }

    /**
     * QuestObjective: supports multi-objective quests for complexity (inspired by Hypixel Skyblock quest log + community suggestions for multi-task dailies).
     * Each objective has its own description, target and live progress.
     * A quest is complete only when ALL its objectives are done (or falls back to legacy single progress if no objectives).
     * Serialized to simple pipe/semi format in DB for zero-dep persistence.
     */
    public static class QuestObjective {
        private final String description;
        private int progress;
        private final int target;

        public QuestObjective(String description, int target, int progress) {
            this.description = (description == null || description.isEmpty()) ? "Complete task" : description;
            this.target = Math.max(1, target);
            this.progress = Math.max(0, Math.min(progress, this.target));
        }

        public String getDescription() { return description; }
        public int getProgress() { return progress; }
        public int getTarget() { return target; }

        public void addProgress(int amount) {
            this.progress = Math.min(target, this.progress + Math.max(0, amount));
        }

        public void setProgress(int p) {
            this.progress = Math.max(0, Math.min(target, p));
        }

        public boolean isCompleted() {
            return progress >= target;
        }

        public String getProgressBar() {
            int bars = 8;
            int filled = (int) ((double) progress / target * bars);
            StringBuilder sb = new StringBuilder("§a");
            for (int i = 0; i < bars; i++) sb.append(i < filled ? "█" : "░");
            return sb.toString();
        }

        // Simple serialization for one objective: desc|target|prog   (our generated descs have no | )
        String serialize() {
            return description.replace("|", "/") + "|" + target + "|" + progress;
        }

        static QuestObjective deserialize(String s) {
            if (s == null || s.isEmpty()) return null;
            String[] parts = s.split("\\|", 3);
            if (parts.length < 3) return null;
            try {
                String desc = parts[0];
                int tgt = Integer.parseInt(parts[1]);
                int prg = Integer.parseInt(parts[2]);
                return new QuestObjective(desc, tgt, prg);
            } catch (Exception e) {
                return null;
            }
        }
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

    /** Multi-objective support (complex quests). Empty list = legacy single-progress mode for compat. */
    private final List<QuestObjective> objectives = new ArrayList<>();

    // ==================== CHAINS / GUIDANCE (Step 1: Structure & Main Story) ====================
    /** Quest line for organization and filtering (Main Story vs Side vs Onboarding). */
    private QuestLine questLine = QuestLine.ONBOARDING;
    /** Chapter / step within the line for guided progression (0 = none). */
    private int chapter = 0;
    /** List of quest IDs that must be claimed before this quest is available ("Unlocked by"). */
    private final List<String> prerequisites = new ArrayList<>();
    /** Hidden quests show teaser rewards but hide objectives/desc until prereqs or partial progress (discovery like CubeCraft). */
    private boolean hidden = false;

    /** Additional typed rewards (Step 3) beyond base rewardXp / rewardMoney. */
    private final List<QuestReward> extraRewards = new ArrayList<>(); // populated via add or in extended flows

    // ==================== CONSTRUCTOR ====================

    public Quest(String id, String title, String description,
                 QuestCategory category, QuestType type,
                 int progress, int target,
                 int rewardXp, int rewardMoney,
                 boolean completed, long expiryTime) {
        this(id, title, description, category, type, progress, target, rewardXp, rewardMoney, completed, expiryTime, null);
    }

    /**
     * Full constructor supporting objectives (multi-objective complex quests).
     * If provided objectives list is non-null and non-empty, completion is driven by objectives.
     * Legacy single-progress path remains for DB compat and simple FIRST quests.
     */
    public Quest(String id, String title, String description,
                 QuestCategory category, QuestType type,
                 int progress, int target,
                 int rewardXp, int rewardMoney,
                 boolean completed, long expiryTime,
                 List<QuestObjective> initialObjectives) {
        this(id, title, description, category, type, progress, target, rewardXp, rewardMoney, completed, expiryTime, initialObjectives, QuestLine.ONBOARDING, 0, null, false);
    }

    /**
     * Extended constructor for chains/guidance (prerequisites, chapter, line, hidden).
     * Supports the full "Structure, Chains & Guidance" features.
     */
    public Quest(String id, String title, String description,
                 QuestCategory category, QuestType type,
                 int progress, int target,
                 int rewardXp, int rewardMoney,
                 boolean completed, long expiryTime,
                 List<QuestObjective> initialObjectives,
                 QuestLine questLine,
                 int chapter,
                 List<String> prereqs,
                 boolean hidden) {

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

        if (initialObjectives != null) {
            for (QuestObjective obj : initialObjectives) {
                if (obj != null) this.objectives.add(obj);
            }
        }
        // Legacy compat: if no objectives but we have a target/desc, synthesize one objective so GUIs can always show rich bars
        if (this.objectives.isEmpty() && target > 0) {
            String objDesc = (description != null && !description.isEmpty()) ? description : ("Complete " + target + " actions");
            this.objectives.add(new QuestObjective(objDesc, target, progress));
        }

        this.questLine = (questLine != null) ? questLine : QuestLine.ONBOARDING;
        this.chapter = Math.max(0, chapter);
        this.hidden = hidden;
        if (prereqs != null) {
            for (String p : prereqs) {
                if (p != null && !p.isEmpty()) this.prerequisites.add(p);
            }
        }
        if (initialObjectives != null) { /* already handled */ }
        // extraRewards left empty for most; story quests can populate after construction or via dedicated ctor
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

    public boolean isCompleted() {
        if (!objectives.isEmpty()) {
            // Complex multi-objective mode (new): all sub-objectives must be done
            for (QuestObjective o : objectives) {
                if (!o.isCompleted()) return false;
            }
            return true;
        }
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
        // Drive objectives too (for complex quests). Any category progress on the quest nudges incomplete objectives.
        // This gives the "multi-step" feel without requiring per-obj action types in v1.
        if (!objectives.isEmpty()) {
            for (QuestObjective obj : objectives) {
                if (!obj.isCompleted()) {
                    obj.addProgress(amount);
                    break; // one action primarily fills one step; repeated actions will walk through them
                }
            }
        }
        checkCompletion();
    }

    /** Add progress specifically targeting objectives (used by enhanced generators/listeners for finer control). */
    public void addObjectiveProgress(int amount) {
        if (objectives.isEmpty()) {
            addProgress(amount);
            return;
        }
        for (QuestObjective obj : objectives) {
            if (!obj.isCompleted()) {
                obj.addProgress(amount);
                break;
            }
        }
        checkCompletion();
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public void checkCompletion() {
        if (!objectives.isEmpty()) {
            boolean allDone = true;
            for (QuestObjective o : objectives) {
                if (!o.isCompleted()) { allDone = false; break; }
            }
            if (allDone) this.completed = true;
            return;
        }
        if (progress >= target) {
            this.completed = true;
        }
    }

    // ==================== OBJECTIVES (COMPLEX QUESTS) ====================

    public List<QuestObjective> getObjectives() {
        return new ArrayList<>(objectives); // defensive copy
    }

    public void setObjectives(List<QuestObjective> newObjectives) {
        objectives.clear();
        if (newObjectives != null) {
            for (QuestObjective o : newObjectives) if (o != null) objectives.add(o);
        }
    }

    /** Returns true if this quest was generated with (or carries) multiple distinct objectives. */
    public boolean hasMultipleObjectives() {
        return objectives.size() > 1;
    }

    /** Overall % complete across objectives (or legacy progress). Useful for summary bars. */
    public double getOverallCompletionPercent() {
        if (objectives.isEmpty()) {
            if (target <= 0) return 1.0;
            return Math.min(1.0, (double) progress / target);
        }
        int done = 0;
        for (QuestObjective o : objectives) if (o.isCompleted()) done++;
        return (double) done / objectives.size();
    }

    /**
     * Serialize objectives for DB (zero extra deps). Format: obj1|obj2  where each= desc|target|prog ; separated by ;
     */
    public String serializeObjectives() {
        if (objectives.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < objectives.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(objectives.get(i).serialize());
        }
        return sb.toString();
    }

    public static List<QuestObjective> parseObjectives(String data) {
        List<QuestObjective> list = new ArrayList<>();
        if (data == null || data.isEmpty()) return list;
        String[] parts = data.split(";");
        for (String p : parts) {
            QuestObjective o = QuestObjective.deserialize(p);
            if (o != null) list.add(o);
        }
        return list;
    }

    /** Convenience: number of objectives completed (for lore). */
    public int getCompletedObjectiveCount() {
        int c = 0;
        for (QuestObjective o : objectives) if (o.isCompleted()) c++;
        return c;
    }

    // ==================== CHAINS / GUIDANCE / STORY (Step 1) ====================

    public QuestLine getQuestLine() { return questLine; }
    public void setQuestLine(QuestLine line) { this.questLine = (line != null ? line : QuestLine.ONBOARDING); }

    public int getChapter() { return chapter; }
    public void setChapter(int ch) { this.chapter = Math.max(0, ch); }

    public List<String> getPrerequisites() { return new ArrayList<>(prerequisites); }
    public void setPrerequisites(List<String> prereqs) {
        prerequisites.clear();
        if (prereqs != null) {
            for (String p : prereqs) if (p != null && !p.isEmpty()) prerequisites.add(p);
        }
    }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean h) { this.hidden = h; }

    /** Returns true if all prerequisites are satisfied (pass a collection of claimed quest IDs). */
    public boolean isAvailable(java.util.Collection<String> claimedQuestIds) {
        if (prerequisites.isEmpty()) return true;
        if (claimedQuestIds == null) return false;
        for (String req : prerequisites) {
            if (!claimedQuestIds.contains(req)) return false;
        }
        return true;
    }

    /**
     * Serialize prereqs for DB (simple ; separated IDs).
     */
    public String serializePrerequisites() {
        if (prerequisites.isEmpty()) return "";
        return String.join(";", prerequisites);
    }

    public static List<String> parsePrerequisites(String data) {
        List<String> list = new ArrayList<>();
        if (data == null || data.isEmpty()) return list;
        for (String p : data.split(";")) {
            if (p != null && !p.trim().isEmpty()) list.add(p.trim());
        }
        return list;
    }

    /** Display friendly line name for GUI. */
    public String getQuestLineDisplay() {
        return switch (questLine) {
            case MAIN_STORY -> "Main Story";
            case SIDE -> "Side Quest";
            case EVENT -> "Event";
            default -> "Onboarding";
        };
    }

    // Step 3 reward accessors
    public List<QuestReward> getExtraRewards() { return new ArrayList<>(extraRewards); }
    public void addExtraReward(QuestReward r) {
        if (r != null) extraRewards.add(r);
    }
}