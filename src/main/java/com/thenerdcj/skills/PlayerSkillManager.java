package com.thenerdcj.skills;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player skill system modeled after MCMMO.
 * Skills: Mining, Woodcutting, Farming, Fishing, Combat, etc.
 * XP from actions (block break, kill, fish).
 * Levels provide passive bonuses and abilities.
 * Folia-safe: uses ThreadSafety, player schedulers for abilities.
 * Anti-cheat integrated: checks isFlaggedForXPExploit before XP.
 * Does not replace island skills; player skills are personal progression (can contribute to island if desired).
 * No conflicts: separate from island XP, worth, minion, etc.
 */
public class PlayerSkillManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    // UUID -> skill -> [xp, level]
    private final Map<UUID, Map<SkillType, double[]>> playerSkills = new ConcurrentHashMap<>();

    // Active abilities: player -> skill -> end time
    private final Map<UUID, Map<SkillType, Long>> activeAbilities = new ConcurrentHashMap<>();

    // Cooldowns: player -> skill -> next available time
    private final Map<UUID, Map<SkillType, Long>> abilityCooldowns = new ConcurrentHashMap<>();

    private static final int MAX_LEVEL = 100;
    private static final double XP_PER_LEVEL_BASE = 100.0; // MCMMO style scaling

    // Ability durations in ticks (20 = 1s)
    private static final long SUPER_BREAKER_DURATION = 20 * 30; // 30s
    private static final long TREE_FELLER_DURATION = 20 * 20;

    // Bounded for large scale CHM compression (many players with skills data)
    private static final int MAX_PLAYERS_SKILLS = 5000;

    public PlayerSkillManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
        threadSafety.runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
    }

    public void loadPlayer(UUID uuid) {
        Map<String, Object[]> data = plugin.getDatabaseManager().loadPlayerSkills(uuid);
        Map<SkillType, double[]> skills = new EnumMap<>(SkillType.class);
        for (Map.Entry<String, Object[]> entry : data.entrySet()) {
            try {
                SkillType skill = SkillType.valueOf(entry.getKey());
                Object[] vals = entry.getValue();
                double xp = (Double) vals[0];
                int level = (Integer) vals[1];
                skills.put(skill, new double[]{xp, level});
            } catch (Exception ignored) {}
        }
        // Init missing
        for (SkillType s : SkillType.values()) {
            skills.putIfAbsent(s, new double[]{0.0, 1});
        }
        playerSkills.put(uuid, skills);
    }

    public void savePlayer(UUID uuid) {
        Map<SkillType, double[]> skills = playerSkills.get(uuid);
        if (skills == null) return;
        Map<String, Object[]> data = new HashMap<>();
        for (Map.Entry<SkillType, double[]> e : skills.entrySet()) {
            data.put(e.getKey().name(), new Object[]{e.getValue()[0], (int) e.getValue()[1]});
        }
        plugin.getDatabaseManager().savePlayerSkills(uuid, data);
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        savePlayer(uuid);
        playerSkills.remove(uuid);
        activeAbilities.remove(uuid);
        abilityCooldowns.remove(uuid);
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        threadSafety.runAsync(() -> loadPlayer(uuid));
    }

    public int getSkillLevel(UUID uuid, SkillType skill) {
        Map<SkillType, double[]> skills = playerSkills.get(uuid);
        if (skills == null) return 1;
        double[] data = skills.get(skill);
        return data != null ? (int) data[1] : 1;
    }

    public double getSkillXp(UUID uuid, SkillType skill) {
        Map<SkillType, double[]> skills = playerSkills.get(uuid);
        if (skills == null) return 0;
        double[] data = skills.get(skill);
        return data != null ? data[0] : 0;
    }

    /**
     * Award XP to player skill. Checks anti-cheat first.
     * Returns true if awarded.
     */
    public boolean addSkillXp(Player player, SkillType skill, double amount) {
        if (player == null || skill == null || amount <= 0) return false;

        // Anti-cheat guard - critical to not conflict
        if (plugin.getAntiCheatManager() != null && 
            plugin.getAntiCheatManager().isFlaggedForXPExploit(player, guessMaterialForSkill(skill))) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        Map<SkillType, double[]> skills = playerSkills.computeIfAbsent(uuid, k -> new EnumMap<>(SkillType.class));
        double[] data = skills.computeIfAbsent(skill, k -> new double[]{0.0, 1});

        double currentXp = data[0];
        int currentLevel = (int) data[1];

        double newXp = currentXp + amount;
        int newLevel = currentLevel;

        while (newXp >= getRequiredXpForLevel(newLevel + 1) && newLevel < MAX_LEVEL) {
            newXp -= getRequiredXpForLevel(newLevel + 1);
            newLevel++;
            // Level up bonus, e.g. notify
            player.sendMessage("§a§l" + skill.getDisplayName() + " Level Up! §e" + newLevel);
            // Grant small island XP if on island (synergy, no bug)
            awardIslandBonus(player, newLevel);
        }

        data[0] = newXp;
        data[1] = newLevel;

        // Report to anti-cheat if high gain (like island)
        if (plugin.getAntiCheatManager() != null && amount > 50) {
            plugin.getAntiCheatManager().reportHighXPGain(player, amount, "player_skill_" + skill.name());
        }

        // Chance for ability activation or passive
        checkAbilityActivation(player, skill, newLevel);

        return true;
    }

    private Material guessMaterialForSkill(SkillType skill) {
        // For anti-cheat flag, provide a representative material
        switch (skill) {
            case MINING: return Material.DIAMOND_ORE;
            case WOODCUTTING: return Material.OAK_LOG;
            case FARMING: return Material.WHEAT;
            case FISHING: return Material.COD;
            default: return Material.STONE;
        }
    }

    private double getRequiredXpForLevel(int level) {
        return XP_PER_LEVEL_BASE * level * 1.2; // MCMMO-like scaling
    }

    private void awardIslandBonus(Player player, int newLevel) {
        // Non-conflicting: bonus to island if player has one, small contribution
        if (plugin.getIslandManager() != null) {
            var island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island != null) {
                island.addXp(5.0 * (newLevel / 10.0)); // small
            }
        }
    }

    /**
     * Check/activate abilities. Simple MCMMO inspired: super breaker for mining on high level + sneak break chance.
     * Uses Folia player scheduler for duration.
     */
    private void checkAbilityActivation(Player player, SkillType skill, int level) {
        if (level < 10) return; // Unlock at 10+

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Map<SkillType, Long> cooldowns = abilityCooldowns.computeIfAbsent(uuid, k -> new EnumMap<>(SkillType.class));
        Long next = cooldowns.get(skill);
        if (next != null && now < next) return;

        boolean activated = false;
        long cooldownMs = 60 * 1000; // 1 min base

        if (skill == SkillType.MINING && level >= 10 && player.isSneaking()) {
            // Super Breaker: haste + double drop chance for duration
            activateSuperBreaker(player, level);
            activated = true;
            cooldownMs = 90 * 1000;
        } else if (skill == SkillType.WOODCUTTING && level >= 15 && player.isSneaking()) {
            // Tree Feller: haste + extra drops (handled in listener)
            activateTreeFeller(player, level);
            activated = true;
            cooldownMs = 60 * 1000;
        }

        if (activated) {
            cooldowns.put(skill, now + cooldownMs);
            // Schedule cooldown end message Folia safe
            threadSafety.runForEntityLater(player, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§7" + skill.getDisplayName() + " ability cooldown ended.");
                }
            }, cooldownMs / 50);
        }
    }

    private void activateSuperBreaker(Player player, int level) {
        int durationTicks = (int) (SUPER_BREAKER_DURATION + (level * 5));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, durationTicks, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks / 2, 0, false, false));

        UUID uuid = player.getUniqueId();
        Map<SkillType, Long> actives = activeAbilities.computeIfAbsent(uuid, k -> new EnumMap<>(SkillType.class));
        actives.put(SkillType.MINING, System.currentTimeMillis() + (durationTicks * 50L));

        player.sendMessage("§6§lSuper Breaker activated! §eMining boosted for " + (durationTicks / 20) + "s");

        // Schedule end
        threadSafety.runForEntityLater(player, () -> {
            if (player.isOnline()) {
                player.sendMessage("§7Super Breaker ended.");
                Map<SkillType, Long> a = activeAbilities.get(uuid);
                if (a != null) a.remove(SkillType.MINING);
            }
        }, durationTicks);
    }

    private void activateTreeFeller(Player player, int level) {
        int durationTicks = 200 + (level * 5);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, durationTicks, 1, false, true));

        UUID uuid = player.getUniqueId();
        Map<SkillType, Long> actives = activeAbilities.computeIfAbsent(uuid, k -> new EnumMap<>(SkillType.class));
        actives.put(SkillType.WOODCUTTING, System.currentTimeMillis() + (durationTicks * 50L));

        player.sendMessage("§a§lTree Feller activated! §eExtra logs for " + (durationTicks / 20) + "s");

        threadSafety.runForEntityLater(player, () -> {
            if (player.isOnline()) {
                player.sendMessage("§7Tree Feller ended.");
                Map<SkillType, Long> a = activeAbilities.get(uuid);
                if (a != null) a.remove(SkillType.WOODCUTTING);
            }
        }, durationTicks);
    }

    public boolean isAbilityActive(UUID uuid, SkillType skill) {
        Map<SkillType, Long> actives = activeAbilities.get(uuid);
        if (actives == null) return false;
        Long end = actives.get(skill);
        return end != null && System.currentTimeMillis() < end;
    }

    /**
     * Called from listeners for block breaks to award mining/wood/excavation.
     * Also handles double drops if ability active.
     */
    public void processBlockBreak(Player player, Material material, boolean wasNatural) {
        SkillType skill = SkillType.fromBlockBreak(material);
        if (skill == null) return;

        double xp = 1.0 + (wasNatural ? 2.0 : 0.5); // base + bonus for natural
        if (isAbilityActive(player.getUniqueId(), skill)) {
            xp *= 1.5; // bonus during ability
        }
        addSkillXp(player, skill, xp);

        // Passive double drop / extra during abilities for mining/wood - flag for listener
        if ((skill == SkillType.MINING && isAbilityActive(player.getUniqueId(), SkillType.MINING)) ||
            (skill == SkillType.WOODCUTTING && isAbilityActive(player.getUniqueId(), SkillType.WOODCUTTING))) {
            if (Math.random() < 0.5) {
                MessageUtil.sendActionBar(player, "§6" + (skill == SkillType.MINING ? "Double drop" : "Feller") + " chance!");
            }
        }
    }

    public void processMobKill(Player player, EntityType type) {
        SkillType skill = SkillType.fromMobKill(type);
        if (skill != null) {
            double xp = 5.0 + (type == EntityType.ENDER_DRAGON ? 50 : 0);
            int level = getSkillLevel(player.getUniqueId(), SkillType.COMBAT);
            if (level >= 15 && Math.random() < 0.1 + (level * 0.002)) {
                xp *= 1.5; // crit bonus
                MessageUtil.sendActionBar(player, "§cCombat crit! Bonus XP");
            }
            addSkillXp(player, skill, xp);
        }
    }

    public void processFish(Player player) {
        addSkillXp(player, SkillType.FISHING, 3.0);

        // Fishing ability: at high level, chance for bonus XP or "treasure" message (can hook for extra item later)
        if (getSkillLevel(player.getUniqueId(), SkillType.FISHING) >= 20 && Math.random() < 0.15) {
            addSkillXp(player, SkillType.FISHING, 5.0); // bonus
            MessageUtil.sendActionBar(player, "§bLucky catch! Bonus fishing XP");
        }
    }

    // For GUI/command
    public Map<SkillType, double[]> getPlayerSkills(UUID uuid) {
        return playerSkills.getOrDefault(uuid, Collections.emptyMap());
    }

    public void removePlayer(UUID uuid) {
        playerSkills.remove(uuid);
        activeAbilities.remove(uuid);
        abilityCooldowns.remove(uuid);
    }

    /**
     * Helper for listeners: spawn extra drops for active abilities (Super Breaker for mining, Feller for wood) safely.
     * Called from SkillListener after vanilla break. Does not affect worth (no extra break event),
     * anti-cheat (drops not XP), or other systems. Folia safe as event is region-threaded.
     */
    public void spawnExtraDropsForAbility(Player player, org.bukkit.block.Block block, SkillType skill) {
        if (player == null || block == null || skill == null) return;
        if (!isAbilityActive(player.getUniqueId(), skill)) return;

        try {
            java.util.Collection<org.bukkit.inventory.ItemStack> drops = block.getDrops(
                player.getInventory().getItemInMainHand() != null ? player.getInventory().getItemInMainHand() : new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR)
            );
            for (org.bukkit.inventory.ItemStack drop : drops) {
                if (drop == null || drop.getType().isAir()) continue;
                int level = getSkillLevel(player.getUniqueId(), skill);
                double chance = 0.3 + (level * 0.005);
                if (Math.random() < Math.min(chance, 0.8)) {
                    org.bukkit.inventory.ItemStack extra = drop.clone();
                    org.bukkit.Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                    block.getWorld().dropItemNaturally(loc, extra);
                    if (loc.getWorld() != null) {
                        loc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 3, 0.2, 0.2, 0.2, 0.01);
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().fine("[Skills] Extra drop spawn failed: " + ex.getMessage());
        }
    }

    /**
     * Bounded eviction for player skills/abilities/cooldowns maps (large scale memory compression).
     */
    private void cleanupCaches() {
        if (playerSkills.size() > MAX_PLAYERS_SKILLS) {
            java.util.Iterator<UUID> it = playerSkills.keySet().iterator();
            int toRemove = playerSkills.size() - (MAX_PLAYERS_SKILLS - 500);
            while (it.hasNext() && toRemove > 0) {
                UUID u = it.next(); it.remove();
                activeAbilities.remove(u);
                abilityCooldowns.remove(u);
                toRemove--;
            }
        }
        if (activeAbilities.size() > MAX_PLAYERS_SKILLS) {
            java.util.Iterator<UUID> it = activeAbilities.keySet().iterator();
            int toRemove = activeAbilities.size() - (MAX_PLAYERS_SKILLS - 500);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
        if (abilityCooldowns.size() > MAX_PLAYERS_SKILLS) {
            java.util.Iterator<UUID> it = abilityCooldowns.keySet().iterator();
            int toRemove = abilityCooldowns.size() - (MAX_PLAYERS_SKILLS - 500);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
    }
}