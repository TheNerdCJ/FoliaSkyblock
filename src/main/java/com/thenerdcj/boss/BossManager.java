package com.thenerdcj.boss;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

/**
 * Manages natural Minecraft boss spawning, tracking, and progression
 * EXPANDED: Includes Slayer Tier system with AI-powered reward balancing
 */
public class BossManager {

    private final FoliaSkyblock plugin;

    // Track killed bosses per island: IslandID -> Set of killed boss names
    private final Map<String, Set<String>> killedBosses = new ConcurrentHashMap<>();

    // Track active bosses: EntityUUID -> BossType
    private final Map<UUID, String> activeBosses = new ConcurrentHashMap<>();

    // NEW: Island-wide Boss Events (Public / Island events)
    // islandId -> Active Island Boss Event
    private final Map<String, IslandBossEvent> activeIslandBossEvents = new ConcurrentHashMap<>();

    // Per-island boss summon cooldowns
    private final Map<String, Long> islandBossSummonCooldowns = new ConcurrentHashMap<>();

    // Simple Slayer Token leaderboard (player UUID -> lifetime tokens earned)
    private final Map<UUID, Integer> slayerTokenLeaderboard = new ConcurrentHashMap<>();
    private long lastTokenLeaderboardReset = System.currentTimeMillis();

    // Slayer quest tracking: PlayerUUID -> Active Quest
    private final Map<UUID, SlayerQuest> activeSlayerQuests = new ConcurrentHashMap<>();

    // Slayer progress: PlayerUUID -> EntityType -> Highest Tier Completed
    private final Map<UUID, Map<EntityType, Integer>> slayerProgress = new ConcurrentHashMap<>();

    // AI-Powered Reward Balancer
    private final SlayerRewardBalancer rewardBalancer;

    // Slayer Achievement Manager
    private final SlayerAchievementManager achievementManager;

    public BossManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.SLAYER_TOKEN_KEY = new NamespacedKey(plugin, "slayer_token_amount");
        this.rewardBalancer = new SlayerRewardBalancer(plugin);
        this.achievementManager = new SlayerAchievementManager(plugin);
        loadSlayerConfig();
    }

    private double bossHealthMultiplier = 1.5;
    private int minParticipantsForBonus = 2;

    private void loadSlayerConfig() {
        if (plugin.getConfig().contains("slayer.boss-event.health-multiplier")) {
            bossHealthMultiplier = plugin.getConfig().getDouble("slayer.boss-event.health-multiplier", 1.5);
        }
        if (plugin.getConfig().contains("slayer.boss-event.min-participants-for-bonus")) {
            minParticipantsForBonus = plugin.getConfig().getInt("slayer.boss-event.min-participants-for-bonus", 2);
        }
    }

    // ============================================
    // DIMENSION BOSS METHODS (Existing)
    // ============================================

    public boolean canSpawnBoss(Player player, DimensionBoss boss) {
        String islandId = getIslandId(player);
        Set<String> killed = killedBosses.getOrDefault(islandId, Collections.emptySet());
        return !killed.contains(boss.getName());
    }

    public boolean spawnBoss(Player player, DimensionBoss boss) {
        if (!canSpawnBoss(player, boss)) {
            player.sendMessage("§cYou have already defeated " + boss.getName() + "!");
            return false;
        }

        Location spawnLoc = player.getLocation().add(0, 5, 0);
        LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(spawnLoc, boss.getEntityType());

        entity.setCustomName("§c§l" + boss.getName());
        entity.setCustomNameVisible(true);
        entity.setMaxHealth(boss.getHealth());
        entity.setHealth(boss.getHealth());

        activeBosses.put(entity.getUniqueId(), boss.getName());

        player.sendMessage("§c§l⚠ BOSS SPAWNED! ⚠");
        player.sendMessage("§e" + boss.getName() + " §7has appeared!");

        return true;
    }

    public void recordBossKill(Player player, String bossName) {
        String islandId = getIslandId(player);
        killedBosses.computeIfAbsent(islandId, k -> ConcurrentHashMap.newKeySet()).add(bossName);

        // Check if this was an island-wide event
        IslandBossEvent event = activeIslandBossEvents.get(islandId);
        if (event != null && event.bossName.contains(bossName)) {
            onIslandBossDefeated(event.bossEntityId, player);
        } else {
            player.sendMessage("§a§l✓ " + bossName + " defeated!");
            player.sendMessage("§7This boss will not respawn on your island.");
        }
    }

    private void checkDimensionProgress(Player player, Island island) {
        int level = island.getLevel();

        if (level >= 50 && canSpawnBoss(player, DimensionBoss.ENDER_DRAGON)) {
            player.sendMessage("§5§lThe End awaits! §7Defeat the Ender Dragon to unlock the End dimension!");
        }

        if (level >= 75 && canSpawnBoss(player, DimensionBoss.WITHER)) {
            player.sendMessage("§8§lThe Nether calls! §7Defeat the Wither to unlock advanced Nether features!");
        }
    }

    public boolean canAccessDimension(Player player, String dimension) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return false;

        int level = island.getLevel();

        switch (dimension.toUpperCase()) {
            case "NETHER":
                return level >= 30;
            case "END":
                return level >= 50 && !canSpawnBoss(player, DimensionBoss.ENDER_DRAGON);
            default:
                return true;
        }
    }

    public boolean isBoss(UUID entityId) {
        return activeBosses.containsKey(entityId);
    }

    public void removeBoss(UUID entityId) {
        activeBosses.remove(entityId);
    }

    private String getIslandId(Player player) {
        return player.getUniqueId().toString();
    }

    // ============================================
    // SLAYER TIER METHODS (NEW)
    // ============================================

    public boolean startSlayerQuest(Player player, SlayerTier tier) {
        if (activeSlayerQuests.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou already have an active slayer quest!");
            return false;
        }

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || island.getLevel() < tier.getMinLevel()) {
            player.sendMessage("§cYour island level is too low for " + tier.getDisplayName() + "!");
            return false;
        }

        int currentTier = getCurrentSlayerTier(player, tier.getTargetEntity());
        if (tier.getTier() > currentTier + 1) {
            player.sendMessage("§cYou must complete previous tiers first!");
            return false;
        }

        SlayerQuest quest = new SlayerQuest(player.getUniqueId(), tier);
        activeSlayerQuests.put(player.getUniqueId(), quest);

        player.sendMessage("§6§l══════════════════════════════════════");
        player.sendMessage("§a§lSLAYER QUEST STARTED!");
        player.sendMessage("§e" + tier.getDisplayName());
        player.sendMessage("§6§l══════════════════════════════════════");

        return true;
    }

    public void recordSlayerKill(Player player, EntityType entityType) {
        SlayerQuest quest = activeSlayerQuests.get(player.getUniqueId());

        if (quest == null || quest.getTier().getTargetEntity() != entityType) {
            return;
        }

        quest.addKill();

        // Integration with Mission system
        if (plugin.getMissionManager() != null) {
            plugin.getMissionManager().addProgress(player.getUniqueId(), 
                com.thenerdcj.mission.Mission.ObjectiveType.COMPLETE_SLAYERS, "ANY", 1);
        }

        plugin.getDatabaseManager().incrementSlayerKills(
                player.getUniqueId(),
                player.getName(),
                entityType.name(),
                quest.getTier().getTier()
        );

        if (quest.isCompleted()) {
            completeSlayerQuest(player, quest);
        } else {
            int remaining = quest.getKillsRequired() - quest.getKills();
            if (remaining % 10 == 0 && remaining > 0) {
                player.sendMessage("§aSlayer Progress: §e" + quest.getKills() + "§7/§e" + quest.getKillsRequired() +
                        " §7kills (§6" + (int)(quest.getProgress() * 100) + "%§7)");
            }
        }
    }

    /**
     * Track slayer achievements after quest completion
     */
    private void trackSlayerAchievements(Player player, SlayerTier tier) {
        UUID uuid = player.getUniqueId();

        achievementManager.incrementProgress(uuid, SlayerAchievement.FIRST_BLOOD, 1);
        achievementManager.incrementProgress(uuid, SlayerAchievement.SLAYER_APPRENTICE, 1);
        achievementManager.incrementProgress(uuid, SlayerAchievement.SLAYER_VETERAN, 1);
        achievementManager.incrementProgress(uuid, SlayerAchievement.SLAYER_LEGEND, 1);

        if (tier.getTargetEntity() == EntityType.ZOMBIE) {
            achievementManager.incrementProgress(uuid, SlayerAchievement.ZOMBIE_SLAYER, 1);
        } else if (tier.getTargetEntity() == EntityType.SPIDER) {
            achievementManager.incrementProgress(uuid, SlayerAchievement.SPIDER_SLAYER, 1);
        } else if (tier.getTargetEntity() == EntityType.ENDERMAN) {
            achievementManager.incrementProgress(uuid, SlayerAchievement.ENDERMAN_SLAYER, 1);
        } else if (tier.getTargetEntity() == EntityType.BLAZE) {
            achievementManager.incrementProgress(uuid, SlayerAchievement.BLAZE_SLAYER, 1);
        }

        int zombieTier = getCurrentSlayerTier(player, EntityType.ZOMBIE);
        int spiderTier = getCurrentSlayerTier(player, EntityType.SPIDER);
        int endermanTier = getCurrentSlayerTier(player, EntityType.ENDERMAN);
        int blazeTier = getCurrentSlayerTier(player, EntityType.BLAZE);

        if (zombieTier >= 5 && spiderTier >= 4 && endermanTier >= 3 && blazeTier >= 3) {
            achievementManager.updateProgress(uuid, SlayerAchievement.SLAYER_MASTER, 15);
        }
    }

    /**
     * Complete a slayer quest and give rewards
     */
    private void completeSlayerQuest(Player player, SlayerQuest quest) {
        SlayerTier tier = quest.getTier();

        player.sendMessage("§6§l══════════════════════════════════════");
        player.sendMessage("§a§lSLAYER QUEST COMPLETE!");
        player.sendMessage("§e" + tier.getDisplayName());
        player.sendMessage("§6§l══════════════════════════════════════");

        int serverPopulation = Bukkit.getOnlinePlayers().size();
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        int playerLevel = island != null ? island.getLevel() : 1;

        List<ItemStack> rewards = new ArrayList<>();

        for (SlayerReward reward : tier.getRewards()) {
            double dynamicChance = rewardBalancer.calculateDynamicDropChance(reward, playerLevel, serverPopulation);

            if (new Random().nextDouble() <= dynamicChance) {
                if (reward.isSpecialReward()) {
                    // Handle modern rewards: Crates, Boosters, Prestige
                    grantSpecialSlayerReward(player, island, reward);
                } else {
                    ItemStack item = new ItemStack(reward.getMaterial(), reward.getAmount());
                    rewards.add(item);
                    player.getInventory().addItem(item);
                    rewardBalancer.recordActualDrop(reward.getMaterial(), true, dynamicChance);

                    if (reward.isSpecial()) {
                        player.sendMessage("§6§l★ SPECIAL DROP! ★ §e" + reward.getAmount() + "x " +
                                reward.getMaterial().name() + " §7(" + reward.getRarityName() + ")");
                    }
                }
            } else {
                if (!reward.isSpecialReward()) {
                    rewardBalancer.recordActualDrop(reward.getMaterial(), false, dynamicChance);
                }
            }
        }

        if (island != null) {
            int xpReward = tier.getXpRequired() / 2;
            island.addXp(xpReward);
            player.sendMessage("§a+" + xpReward + " Island XP!");
        }

        updateSlayerProgress(player, tier);
        trackSlayerAchievements(player, tier);
        activeSlayerQuests.remove(player.getUniqueId());

        // NEW: High-tier slayer quests trigger island-wide boss events
        if (isBossSlayerTier(tier) && island != null) {
            DimensionBoss bossToSummon = mapSlayerTierToDimensionBoss(tier);
            if (bossToSummon != null) {
                player.sendMessage("§6§lYour slayer mastery has summoned a powerful island boss!");
                summonIslandBossEvent(island, bossToSummon);
            }
        }

        SlayerTier nextTier = tier.getNextTier();
        if (nextTier != null) {
            player.sendMessage("§7Next tier available: §e" + nextTier.getDisplayName());
            player.sendMessage("§7Use §a/slayer start " + nextTier.name().toLowerCase() + " §7to begin!");
        } else {
            player.sendMessage("§6§l★ MAX TIER REACHED! ★");
            player.sendMessage("§aYou have mastered this slayer!");
        }
    } // End of completeSlayerQuest

    /**
     * Grants modern slayer rewards (Crates, Boosters, Prestige XP, etc.)
     */
    private void grantSpecialSlayerReward(Player player, Island island, SlayerReward reward) {
        String rewardId = reward.getSpecialReward();

        if (rewardId == null) return;

        if (rewardId.startsWith("CRATE_")) {
            // Give crate key via the new Crate system
            if (plugin.getCrateManager() != null) {
                try {
                    com.thenerdcj.crate.CrateType crateType = com.thenerdcj.crate.CrateType.valueOf(rewardId.replace("CRATE_", ""));
                    ItemStack key = plugin.getCrateManager().createKeyItem(crateType);
                    player.getInventory().addItem(key);
                    player.sendMessage("§6§l★ SPECIAL! ★ §e" + crateType.getDisplayName() + " Key");
                } catch (Exception ignored) {}
            }
        } else if (rewardId.startsWith("BOOSTER_")) {
            // Example: BOOSTER_CROP_60
            if (plugin.getBoosterManager() != null && island != null) {
                String[] parts = rewardId.split("_");
                if (parts.length >= 3) {
                    try {
                        com.thenerdcj.booster.BoosterType bType = com.thenerdcj.booster.BoosterType.valueOf(parts[1]);
                        int minutes = Integer.parseInt(parts[2]);
                        double mult = plugin.getConfig().getDouble("boosters.multipliers." + bType.name(), 2.0);
                        plugin.getBoosterManager().activateBooster(island, bType, mult, minutes * 60L * 1000);
                        player.sendMessage("§6§l★ SPECIAL! ★ §e" + bType.getDisplayName() + " Booster (" + minutes + "m)");
                    } catch (Exception ignored) {}
                }
            }
        } else if (rewardId.startsWith("PRESTIGE_")) {
            if (island != null) {
                int prestigeXp = Integer.parseInt(rewardId.replace("PRESTIGE_", ""));
                island.addXp(prestigeXp);
                player.sendMessage("§6§l★ SPECIAL! ★ §d+" + prestigeXp + " Island XP (Slayer Prestige)");
            }
        }
    }

    /**
     * Update player's slayer progress
     */
    private void updateSlayerProgress(Player player, SlayerTier tier) {
        slayerProgress.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(tier.getTargetEntity(), Math.max(
                        slayerProgress.get(player.getUniqueId()).getOrDefault(tier.getTargetEntity(), 0),
                        tier.getTier()
                ));
    }

    /**
     * Get current slayer tier for entity type
     */
    public int getCurrentSlayerTier(Player player, EntityType entityType) {
        return slayerProgress.getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(entityType, 0);
    }

    /**
     * Get active slayer quest for player
     */
    public SlayerQuest getActiveSlayerQuest(Player player) {
        return activeSlayerQuests.get(player.getUniqueId());
    }

    /**
     * Abandon current slayer quest
     */
    public boolean abandonSlayerQuest(Player player) {
        SlayerQuest quest = activeSlayerQuests.remove(player.getUniqueId());
        if (quest != null) {
            player.sendMessage("§cSlayer quest abandoned: " + quest.getTier().getDisplayName());
            return true;
        }
        return false;
    }

    /**
     * Get AI reward balancer for external access
     */
    public SlayerRewardBalancer getRewardBalancer() {
        return rewardBalancer;
    }

    /**
     * Get achievement manager for external access
     */
    public SlayerAchievementManager getAchievementManager() {
        return achievementManager;
    }

    // Slayer Tokens - New dedicated currency for Slayer content
    private NamespacedKey SLAYER_TOKEN_KEY;

    /** Creates a stackable Slayer Token item (used as currency for Slayer Shop) */
    public ItemStack createSlayerToken(int amount) {
        ItemStack token = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = token.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lSlayer Token");
            meta.setLore(Arrays.asList(
                "§7Special currency earned from Slayer quests",
                "§7and Island Boss Events.",
                "",
                "§eRight-click in Slayer Shop to spend"
            ));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(SLAYER_TOKEN_KEY, PersistentDataType.INTEGER, amount);
            token.setItemMeta(meta);
        }
        return token;
    }

    public int getSlayerTokenAmount(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer amt = meta.getPersistentDataContainer().get(SLAYER_TOKEN_KEY, PersistentDataType.INTEGER);
        return amt != null ? amt : 0;
    }

    public void awardSlayerTokens(Player player, int amount) {
        if (amount <= 0) return;
        ItemStack token = createSlayerToken(amount);
        player.getInventory().addItem(token);
        player.sendMessage("§6§l+" + amount + " Slayer Tokens");

        // Track for leaderboard (in-memory + DB)
        slayerTokenLeaderboard.merge(player.getUniqueId(), amount, Integer::sum);
        plugin.getDatabaseManager().incrementSlayerTokens(player.getUniqueId(), amount);
    }

    /** Returns total Slayer Tokens the player currently holds (across all stacks) */
    public int getTotalSlayerTokens(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            total += getSlayerTokenAmount(item);
        }
        return total;
    }

    /** Consumes the specified amount of Slayer Tokens from the player's inventory (handles stacks) */
    public boolean consumeSlayerTokens(Player player, int amount) {
        if (getTotalSlayerTokens(player) < amount) return false;

        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;

            int current = getSlayerTokenAmount(item);
            if (current <= 0) continue;

            if (current >= remaining) {
                int newAmt = current - remaining;
                if (newAmt > 0) {
                    ItemStack newToken = createSlayerToken(newAmt);
                    player.getInventory().setItem(i, newToken);
                } else {
                    player.getInventory().setItem(i, null);
                }
                remaining = 0;
            } else {
                player.getInventory().setItem(i, null);
                remaining -= current;
            }
        }
        player.updateInventory();
        return true;
    }

    /** Example Slayer-specific gear (lore + abilities handled in SlayerGearListener) */
    public ItemStack createSlayerGear(String type) {
        if (type.equalsIgnoreCase("scythe")) {
            ItemStack scythe = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta meta = scythe.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§c§lRevenant Scythe");
                meta.setLore(Arrays.asList(
                    "§7+25% damage to Zombies & Undead",
                    "§7Chance to drop extra flesh on kill",
                    "§6Slayer Exclusive"
                ));
                scythe.setItemMeta(meta);
            }
            return scythe;
        }

        if (type.equalsIgnoreCase("helmet")) {
            ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
            ItemMeta meta = helmet.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§5§lTarantula Helmet");
                meta.setLore(Arrays.asList(
                    "§7+15% spider damage resistance",
                    "§7Chance to avoid poison",
                    "§6Slayer Exclusive"
                ));
                helmet.setItemMeta(meta);
            }
            return helmet;
        }

        return new ItemStack(Material.DIAMOND_SWORD);
    }

    /**
     * Returns a damage multiplier for slayer content based on progress.
     * Can be used by CombatListener for slayer-specific damage bonuses.
     */
    public double getSlayerDamageMultiplier(Player player, EntityType target) {
        Map<EntityType, Integer> progress = slayerProgress.get(player.getUniqueId());
        if (progress == null) return 1.0;

        int tier = progress.getOrDefault(target, 0);
        return 1.0 + (tier * 0.08); // +8% damage per completed tier
    }

    /**
     * Returns all unlocked Slayer Perks for a player based on their progress.
     */
    public java.util.List<SlayerPerk> getUnlockedSlayerPerks(Player player) {
        java.util.List<SlayerPerk> unlocked = new java.util.ArrayList<>();
        Map<EntityType, Integer> progress = slayerProgress.get(player.getUniqueId());
        if (progress == null) return unlocked;

        for (SlayerPerk perk : SlayerPerk.values()) {
            int tierProgress = progress.getOrDefault(perk.getTargetEntity(), 0);
            if (perk.isUnlocked(tierProgress)) {
                unlocked.add(perk);
            }
        }
        return unlocked;
    }

    // ============================================
    // ISLAND-WIDE BOSS EVENTS (Public / Group Slayer Content)
    // ============================================

    /**
     * Simple data holder for an active island-wide boss event.
     */
    public static class IslandBossEvent {
        public final String islandId;
        public final String bossName;
        public final UUID bossEntityId;
        public final long startTime;
        public final java.util.Map<UUID, Double> damageContributions = new ConcurrentHashMap<>();
        public boolean defeated = false;

        public IslandBossEvent(String islandId, String bossName, UUID bossEntityId) {
            this.islandId = islandId;
            this.bossName = bossName;
            this.bossEntityId = bossEntityId;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * Summons a powerful boss as an island-wide event.
     * All online island members are notified and can participate.
     * High-tier slayer quests should route through this for group content.
     */
    public boolean summonIslandBossEvent(Island island, DimensionBoss bossType) {
        if (island == null || bossType == null) return false;

        String islandId = island.getId();

        if (activeIslandBossEvents.containsKey(islandId)) {
            // Prevent multiple simultaneous events on same island
            return false;
        }

        if (!canSummonIslandBoss(island)) {
            return false;
        }

        // Spawn at island center or safe location
        Location spawnLoc = island.getCenter(null);
        if (spawnLoc == null) spawnLoc = island.getSpawnLocation();
        if (spawnLoc == null) return false;

        spawnLoc = spawnLoc.clone().add(0, 5, 0);

        LivingEntity entity = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, bossType.getEntityType());
        entity.setCustomName("§c§l" + bossType.getDisplayName() + " §7[Island Event]");
        entity.setCustomNameVisible(true);
        entity.setMaxHealth(bossType.getHealth() * bossHealthMultiplier);
        entity.setHealth(entity.getMaxHealth());

        UUID bossId = entity.getUniqueId();
        activeBosses.put(bossId, bossType.getName());

        IslandBossEvent event = new IslandBossEvent(islandId, bossType.getDisplayName(), bossId);
        activeIslandBossEvents.put(islandId, event);
        setIslandBossSummonCooldown(island);

        // Visual flair - spawn particles around the boss
        for (int i = 0; i < 30; i++) {
            double offsetX = (Math.random() - 0.5) * 4;
            double offsetZ = (Math.random() - 0.5) * 4;
            Location particleLoc = spawnLoc.clone().add(offsetX, 1 + Math.random() * 2, offsetZ);
            spawnLoc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, particleLoc, 1);
            spawnLoc.getWorld().spawnParticle(org.bukkit.Particle.FLAME, particleLoc, 3, 0.3, 0.5, 0.3, 0.01);
        }

        // Announce to all online island members (Folia-safe)
        for (Player member : island.getOnlineMembers()) {
            member.sendMessage("§c§l⚠ ISLAND BOSS EVENT STARTED! ⚠");
            member.sendMessage("§e" + bossType.getDisplayName() + " §7has been summoned on your island!");
            member.sendMessage("§7All participants will receive rewards based on contribution.");
            member.sendMessage("§7Time limit: 15 minutes");
        }

        // Schedule auto-fail after 15 minutes (Folia-safe)
        plugin.getThreadSafety().runOnMainThreadLater(() -> {
            if (activeIslandBossEvents.containsKey(islandId) && !event.defeated) {
                endIslandBossEvent(islandId, false);
            }
        }, 15 * 60 * 20L); // 15 minutes

        return true;
    }

    /**
     * Track damage contribution during an island boss fight.
     * Call this from a damage listener or EntityDamageByEntityEvent.
     */
    public void recordBossDamage(UUID bossId, Player player, double damage) {
        IslandBossEvent event = getActiveIslandEventForBoss(bossId);
        if (event == null) return;

        event.damageContributions.merge(player.getUniqueId(), damage, Double::sum);
    }

    private IslandBossEvent getActiveIslandEventForBoss(UUID bossId) {
        for (IslandBossEvent event : activeIslandBossEvents.values()) {
            if (event.bossEntityId.equals(bossId)) return event;
        }
        return null;
    }

    /**
     * Called when an island boss is defeated.
     */
    public void onIslandBossDefeated(UUID bossId, Player killer) {
        IslandBossEvent event = getActiveIslandEventForBoss(bossId);
        if (event == null) return;

        event.defeated = true;
        String islandId = event.islandId;

        // Distribute rewards based on contribution
        distributeIslandBossRewards(event);

        // Deeper Mission integration - "defeat X island boss" objectives
        if (plugin.getMissionManager() != null && !event.damageContributions.isEmpty()) {
            UUID sample = event.damageContributions.keySet().iterator().next();
            plugin.getMissionManager().addProgress(sample, com.thenerdcj.mission.Mission.ObjectiveType.COMPLETE_SLAYERS, "ISLAND_BOSS", 1);
        }

        // Cleanup
        activeIslandBossEvents.remove(islandId);
        activeBosses.remove(bossId);

        // Notify island members (best effort)
        if (!event.damageContributions.isEmpty()) {
            UUID sampleId = event.damageContributions.keySet().iterator().next();
            Player sample = Bukkit.getPlayer(sampleId);
            if (sample != null) {
                Island island = plugin.getIslandManager().getIsland(sampleId, sample.getWorld().getEnvironment());
                if (island != null) {
                    for (Player p : island.getOnlineMembers()) {
                        p.sendMessage("§a§l✓ Island Boss Defeated! §e" + event.bossName);
                        p.sendMessage("§7Rewards have been distributed based on contribution.");

                        // Visual flair - defeat fireworks
                        Location loc = p.getLocation();
                        loc.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, loc, 50, 2, 2, 2, 0.1);
                        p.playSound(loc, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 1.0f);
                    }
                }
            }
        }
    }

    private void distributeIslandBossRewards(IslandBossEvent event) {
        // Simple contribution-based distribution
        double totalDamage = event.damageContributions.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalDamage <= 0) return;

        for (java.util.Map.Entry<UUID, Double> entry : event.damageContributions.entrySet()) {
            Player participant = Bukkit.getPlayer(entry.getKey());
            if (participant == null || !participant.isOnline()) continue;

            double contribution = entry.getValue() / totalDamage;

            // Base reward + contribution bonus
            int baseCoins = 5000;
            int bonus = (int) (baseCoins * contribution * 2);

            // Give via island bank or direct (using existing economy patterns)
            participant.sendMessage("§6§lBoss Reward: §e+" + (baseCoins + bonus) + " coins (contribution: " + (int)(contribution * 100) + "%)");

            // Award Slayer Tokens (new currency)
            int tokenAmount = (int) (10 + (contribution * 30));
            awardSlayerTokens(participant, tokenAmount);

            // Example modern reward
            if (plugin.getCrateManager() != null && new Random().nextDouble() < 0.4) {
                ItemStack key = plugin.getCrateManager().createKeyItem(com.thenerdcj.crate.CrateType.RARE);
                participant.getInventory().addItem(key);
                participant.sendMessage("§6§lBonus: §eRare Crate Key");
            }
        }
    }

    private void endIslandBossEvent(String islandId, boolean success) {
        IslandBossEvent event = activeIslandBossEvents.remove(islandId);
        if (event == null) return;

        // Best effort notification using any online player from contributions
        if (!event.damageContributions.isEmpty()) {
            UUID anyParticipant = event.damageContributions.keySet().iterator().next();
            Player sample = Bukkit.getPlayer(anyParticipant);
            if (sample != null) {
                Island island = plugin.getIslandManager().getIsland(anyParticipant, sample.getWorld().getEnvironment());
                if (island != null) {
                    String msg = success ? "§aIsland boss event ended successfully!" : "§cIsland boss event timed out.";
                    for (Player p : island.getOnlineMembers()) {
                        p.sendMessage(msg);
                    }
                }
            }
        }
    }

    // Helper - you may need to add getIslandById in IslandManager if not present
    // For now we assume it or use owner lookup.

    private boolean isBossSlayerTier(SlayerTier tier) {
        return tier.name().contains("BOSS") || tier.getTier() >= 5;
    }

    /** Returns true if the island can summon a boss right now (respects cooldown + prestige scaling) */
    public boolean canSummonIslandBoss(Island island) {
        if (island == null) return false;
        String id = island.getId();
        long last = islandBossSummonCooldowns.getOrDefault(id, 0L);

        int prestige = 0;
        if (plugin.getPrestigeManager() != null) {
            prestige = plugin.getPrestigeManager().getPrestigeLevel(island);
        }

        long baseCooldown = plugin.getConfig().getLong("slayer.boss-summon-cooldown-hours", 24) * 60 * 60 * 1000L;
        long reduction = prestige * 30L * 60 * 1000L; // 30 min per prestige level
        long effectiveCooldown = Math.max(baseCooldown - reduction, 2 * 60 * 60 * 1000L); // min 2 hours

        return (System.currentTimeMillis() - last) >= effectiveCooldown;
    }

    public long getIslandBossSummonCooldownRemaining(Island island) {
        if (island == null) return 0;
        if (canSummonIslandBoss(island)) return 0;

        String id = island.getId();
        long last = islandBossSummonCooldowns.getOrDefault(id, 0L);

        int prestige = 0;
        if (plugin.getPrestigeManager() != null) {
            prestige = plugin.getPrestigeManager().getPrestigeLevel(island);
        }

        long baseCooldown = plugin.getConfig().getLong("slayer.boss-summon-cooldown-hours", 24) * 60 * 60 * 1000L;
        long reduction = prestige * 30L * 60 * 1000L;
        long effective = Math.max(baseCooldown - reduction, 2 * 60 * 60 * 1000L);

        return Math.max(0, effective - (System.currentTimeMillis() - last));
    }

    private void setIslandBossSummonCooldown(Island island) {
        if (island != null) {
            islandBossSummonCooldowns.put(island.getId(), System.currentTimeMillis());
        }
    }

    /** Returns top Slayer Token earners (DB-backed for global accuracy) — Folia-safe (never blocks) */
    public java.util.List<java.util.Map.Entry<UUID, Integer>> getTopSlayerTokenEarners(int limit) {
        // Always return a snapshot from the in-memory leaderboard (populated by awards + periodic DB refresh)
        // Fire a non-blocking async refresh from DB for freshness (GUI will see it on next open)
        plugin.getDatabaseManager().getTopSlayerTokenEarners(limit).thenAccept(dbResults -> {
            if (dbResults != null && !dbResults.isEmpty()) {
                java.util.Map<UUID, Integer> fresh = new java.util.HashMap<>();
                for (Object[] row : dbResults) {
                    fresh.put((UUID) row[0], (Integer) row[1]);
                }
                slayerTokenLeaderboard.clear();
                slayerTokenLeaderboard.putAll(fresh);
            }
        });

        return slayerTokenLeaderboard.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Basic weekly reset for token leaderboard (call periodically) */
    public void checkAndResetTokenLeaderboard() {
        long week = 7L * 24 * 60 * 60 * 1000;
        if (System.currentTimeMillis() - lastTokenLeaderboardReset > week) {
            slayerTokenLeaderboard.clear();
            lastTokenLeaderboardReset = System.currentTimeMillis();
            plugin.getLogger().info("[Slayer] Weekly Slayer Token leaderboard has been reset.");
        }
    }

    private DimensionBoss mapSlayerTierToDimensionBoss(SlayerTier tier) {
        // Simple mapping for the new high-tier content
        if (tier.name().contains("ZOMBIE_BOSS")) return DimensionBoss.REVENANT_HORROR_T5;
        if (tier.name().contains("DRAGON")) return DimensionBoss.ENDER_DRAGON;
        if (tier.name().contains("WITHER")) return DimensionBoss.WITHER;
        return null;
    }
}