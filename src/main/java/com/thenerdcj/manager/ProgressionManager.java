package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.Island.Skill;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Complete ProgressionManager for deep progression (Skills + Milestones).
 */
public class ProgressionManager {

    private final FoliaSkyblock plugin;

    public static final Map<String, MilestoneDefinition> MILESTONES = new LinkedHashMap<>();

    static {
        MILESTONES.put("first_mine_1000", new MilestoneDefinition(
                "Mine 1,000 blocks", Skill.MINING, 1000, 75.0,
                "Unlocks early mining-related upgrades"));

        MILESTONES.put("nether_access_milestone", new MilestoneDefinition(
                "Reach Mining Skill Level 5", Skill.MINING, 5, 200.0,
                "Helps unlock Nether dimension access"));

        MILESTONES.put("end_access_milestone", new MilestoneDefinition(
                "Reach Combat Skill Level 8 + Island Level 20", Skill.COMBAT, 8, 500.0,
                "Helps unlock The End dimension"));

        MILESTONES.put("build_spawner_farm", new MilestoneDefinition(
                "Place significant spawner value or complete related challenges", null, 0, 150.0,
                "Unlocks advanced island upgrade options"));
    }

    public static class MilestoneDefinition {
        public final String description;
        public final Skill requiredSkill;
        public final int requiredValue;
        public final double bonusXp;
        public final String unlockDescription;

        public MilestoneDefinition(String description, Skill requiredSkill, int requiredValue,
                                   double bonusXp, String unlockDescription) {
            this.description = description;
            this.requiredSkill = requiredSkill;
            this.requiredValue = requiredValue;
            this.bonusXp = bonusXp;
            this.unlockDescription = unlockDescription;
        }
    }

    public ProgressionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public void checkAndAwardMilestone(Island island, String milestoneId, Player actor) {
        if (island == null || island.hasCompletedMilestone(milestoneId)) return;

        MilestoneDefinition def = MILESTONES.get(milestoneId);
        if (def == null) return;

        boolean conditionsMet = (def.requiredSkill != null)
                ? island.getSkillLevel(def.requiredSkill) >= def.requiredValue
                : true; // Extend with ChallengeManager / custom logic

        if (conditionsMet) {
            island.completeMilestone(milestoneId, def.bonusXp);

            if (actor != null && actor.isOnline()) {
                actor.sendMessage("§a§l[MILESTONE COMPLETE] §e" + def.description);
            }

            // Save asynchronously
            String islandKey = island.getId();
            Set<String> completed = new HashSet<>(island.getCompletedMilestones());

            plugin.getDatabaseManager()
                    .saveIslandMilestones(islandKey, completed);
        }
    }

    public void awardSkillXpFromAction(Player player, Skill skill, double amount) {
        if (player == null || skill == null || amount <= 0) return;

        Island island = plugin.getIslandManager().getIsland(
                player.getUniqueId(), player.getWorld().getEnvironment());

        if (island != null) {
            island.addSkillXp(skill, amount);
            if (skill == Skill.MINING) {
                checkAndAwardMilestone(island, "first_mine_1000", player);
            }
        }
    }

    public void loadIslandProgression(Island island) {
        // TODO: Load from DatabaseManager and call island.loadProgressionData(...)
    }

    public CompletableFuture<Void> saveIslandProgression(Island island) {
        // TODO: Save using island.getSkillXpMap(), getSkillLevelsMap(), getCompletedMilestones()
        return CompletableFuture.completedFuture(null);
    }

    public Map<String, MilestoneDefinition> getAllMilestones() {
        return Collections.unmodifiableMap(MILESTONES);
    }
}