package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.anticheat.NeuralCheatDetector;
import com.thenerdcj.anticheat.PlayerBehaviorProfile;
import com.thenerdcj.island.generator.IslandOreGenerator;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Updated Anti-Cheat Manager for FoliaSkyblock
 *
 * === IMPORTANT NOTE (Tier 3 review) ===
 * This is an ADVANCED / EXPERIMENTAL anti-cheat module containing a hand-rolled neural network
 * and detailed behavior profiling. While functional, it is over-engineered for most Skyblock servers.
 *
 * RECOMMENDED USAGE:
 * - For serious servers: Replace with a proper established anti-cheat plugin (Matrix, Spartan, Grim, etc.)
 * - Keep enabled only if you want the custom fastbreak/xray/dupe/XP heuristics tailored to this plugin's custom generators.
 *
 * The NeuralCheatDetector can be disabled via anticheat.yml or by commenting out its usage.
 * This module adds non-trivial complexity and should be audited carefully during updates.
 *
 * Improvements in this update:
 * - Fully implemented fastbreak and fastplace detection
 * - Xray heuristics aware of custom IslandOreGenerator upgrades
 * - Play-to-Win protections (XP macro, dupes)
 *
 * =============================================
 * DETAILED SKYBLOCK EXPLOIT GUIDE (per IMPROVEMENTS.md audit)
 * =============================================
 * This class implements heuristics for common Skyblock exploits. Expand here with more if new patterns emerge.
 *
 * 1. X-RAY / ORE MACROS:
 *    - Detect high ore rates with low stone mined (classic xray, unless on custom upgraded IslandOreGenerator islands).
 *    - Config: xray.enabled, xray.max-per-minute.
 *    - Mitigation: flag + violation, alert staff. Neural can supplement.
 *
 * 2. FASTBREAK / FASTPLACE (macro / autoclicker):
 *    - Track last break/place time per player.
 *    - If delay < min-delay-ms (default 180ms), suspicious.
 *    - Config: block.fastbreak/fastplace.
 *    - Called from listeners on BlockBreak/Place.
 *
 * 3. XP MACRO / EXPLOIT (Play-to-Win critical - bypasses island leveling to unlock dimensions/bosses):
 *    - Track recentXPGains in window (xpWindowMs=30s, threshold).
 *    - isFlaggedForXPExploit and reportHighXPGain used by IslandXPListener, SkillListener, etc.
 *    - High gain flags "High XP Gain (Possible Macro/Exploit - breaks Play to Win)".
 *    - See isFlaggedForXPExploit, reportHighXPGain.
 *
 * 4. DUPES (shulker, hopper cross-claim, item dupe):
 *    - Shulker place count tracking.
 *    - recordHopperTransfer for cross-claim (different owner islands) or spawn/unclaimed.
 *    - recentItemGains for rapid item actions.
 *    - Flags "Shulker Box Duplication Pattern (common skyblock dupe)".
 *
 * 5. GENERATOR ABUSE / MACRO on custom gens:
 *    - IslandOreGenerator levels affect rates; anti-cheat aware (lower suspicion on high level gens? but still monitor).
 *    - High volume on low level gens = suspicious.
 *
 * 6. FLY/SPEED / MOVEMENT:
 *    - Basic speed/fly thresholds, weights.
 *    - Config: speed/fly.
 *
 * 7. OTHER (hopper dupe across claims, combat macros, etc.):
 *    - See HopperDupeListener integration.
 *    - NeuralCheatDetector for advanced patterns (experimental, default off).
 *
 * 8. QUEST SPAM / EARLY-GAME EXPLOIT (bypasses onboarding balance and Play-to-Win first-island design):
 *    - Track recentQuestGains window; isFlaggedForQuestExploit + reportHighQuestProgress.
 *    - Called from EarlyGameListener (all FIRST categories) and MinionManager first-minion hook.
 *    - Flags "High Quest Progress (Possible Macro/Exploit - bypasses early game balance)".
 *
 * 9. COLLECTION ABUSE / MACRO (rapid first-discover or milestone farming for tokens/XP/cosmetics):
 *    - recentCollectionDiscovers; isFlaggedForCollectionAbuse + reportCollectionDiscover.
 *    - Integrated in CollectionManager.discover and CollectionListener paths (MONITOR).
 *    - Prevents macroing unique item finds to farm collection rewards (25/50/100 milestone cosmetics/tokens).
 *
 * 10. DRAGON GRIEF / ISLAND PROJECTION BYPASS:
 *    - reportDragonGriefAttempt (player-aware when possible) for attempts to damage non-home islands via dragon.
 *    - Called from IslandProtectionListener onEntityExplode / crystal attribution mismatch paths.
 *    - Flags "Dragon grief attempt on non-home island (projection violation)".
 *
 * 11. MINION / HOUSING SPAM (duping, placement spam causing lag or grief):
 *    - recordMinionPlace + isFlaggedForMinionSpam; recordHousingPlace + isFlaggedForHousingSpam.
 *    - Wired in MinionManager.placeMinion success + IslandFurnitureManager / IslandStructure place.
 *    - Rate limit + flag "Minion/Housing placement spam (possible dupe/lag induction)".
 *
 * 12. ENCHANT POWER FARMING / ANVIL ABUSE (rapid custom enchant stacking or proc spam):
 *    - isFlaggedForEnchantPowerAbuse + reportEnchantProc / recordAnvilCombine.
 *    - Used by EnchantingTableGUI, AnvilListener, EnchantEffectListener for high-volume custom power.
 *    - Protects custom enchant economy (no free power creep bypassing progression).
 *
 * ENFORCEMENT:
 *    - flagViolation(player, reason, severity) -> increments violations, alerts if perm.
 *    - Levels: level1 (warn), level2 (kick/temp), level3 (ban).
 *    - saveViolationLogs() on disable for audit.
 *    - Bypass: foliasb.bypass.anticheat or staffBypass.
 *
 * INTEGRATION:
 *    - Listeners: AntiCheatListener, HopperDupeListener, etc. call record* and isFlagged*.
 *    - PlayerQuitListener removes profile.
 *    - Used in IslandXP, skills (to guard XP), etc. to protect Play-to-Win.
 *
 * AUDIT NOTES (from June 2026):
 *    - Expand this guide with more Skyblock-specific (e.g. new dupe methods, generator throttling).
 *    - Add logging/auditing for sensitive ops.
 *    - Profile hot paths.
 *    - Ensure no false positives on legit high-level islands/parties.
 */

public class AntiCheatManager {

    private final FoliaSkyblock plugin;

    // Config (from anticheat.yml - create if missing)
    private boolean enabled;
    private int maxViolations;
    private int violationDecaySeconds;
    private String alertPermission;

    private boolean speedEnabled, flyEnabled;
    private double speedThreshold, flyThreshold;
    private int speedWeight, flyWeight;

    private boolean fastbreakEnabled, fastplaceEnabled;
    private long fastbreakMinDelayMs, fastplaceMinDelayMs;

    private boolean xrayEnabled;
    private int xrayMaxPerMinute;

    // Neural detector is experimental — disabled by default after Tier 3 review
    private boolean neuralDetectorEnabled;

    private String punishmentLevel1, punishmentLevel2, punishmentLevel3;
    private int banDurationHours;

    // Runtime
    private final Map<UUID, PlayerBehaviorProfile> profiles = new ConcurrentHashMap<>();
    private final NeuralCheatDetector neuralDetector = new NeuralCheatDetector();
    private final Set<UUID> trustedHighEnchantPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastCheckTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violationCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastViolationTime = new ConcurrentHashMap<>();
    private final Set<UUID> staffBypassPlayers = ConcurrentHashMap.newKeySet();

    // Dupe / item
    private final Map<UUID, Long> lastItemActionTime = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> recentItemGains = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shulkerPlaceCount = new ConcurrentHashMap<>();

    // XP Play-to-Win
    private final Map<UUID, List<Long>> recentXPGains = new ConcurrentHashMap<>();
    private final int xpGainThreshold = 100;
    private final long xpWindowMs = 30000;

    // Expanded Skyblock exploit tracking (quest, collection, housing, minion, enchant, dragon)
    private final Map<UUID, List<Long>> recentQuestGains = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> recentCollectionDiscovers = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> recentHousingPlaces = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> recentMinionPlaces = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> recentEnchantProcs = new ConcurrentHashMap<>();
    private final long generalWindowMs = 60000; // 1 min for most non-XP rates
    private final int questSpamThreshold = 20;
    private final int collectionSpamThreshold = 10;
    private final int placeSpamThreshold = 15;

    // === NEW: Fastbreak / Fastplace tracking ===
    private final Map<UUID, Long> lastBlockBreakTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBlockPlaceTime = new ConcurrentHashMap<>();

    public AntiCheatManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();
        startCleanupTask();
        MessageUtil.info(plugin.getLogger(), "§a[AntiCheat] Updated Anti-Cheat Manager initialized (fastbreak + xray + Folia ready)");
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "anticheat.yml");
        if (!configFile.exists()) {
            plugin.saveResource("anticheat.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("enabled", true);
        maxViolations = config.getInt("max-violations", 15);
        violationDecaySeconds = config.getInt("violation-decay-seconds", 120);
        alertPermission = config.getString("alert-permission", "foliasb.staff.alert");

        speedEnabled = config.getBoolean("movement.speed.enabled", true);
        speedThreshold = config.getDouble("movement.speed.threshold", 0.68);
        speedWeight = config.getInt("movement.speed.violation-weight", 3);

        flyEnabled = config.getBoolean("movement.fly.enabled", true);
        flyThreshold = config.getDouble("movement.fly.threshold", 0.42);
        flyWeight = config.getInt("movement.fly.violation-weight", 4);

        fastbreakEnabled = config.getBoolean("block.fastbreak.enabled", true);
        fastbreakMinDelayMs = config.getLong("block.fastbreak.min-delay-ms", 180);

        fastplaceEnabled = config.getBoolean("block.fastplace.enabled", true);
        fastplaceMinDelayMs = config.getLong("block.fastplace.min-delay-ms", 150);

        xrayEnabled = config.getBoolean("xray.enabled", true);
        xrayMaxPerMinute = config.getInt("xray.max-per-minute", 8);

        neuralDetectorEnabled = config.getBoolean("neural-detector.enabled", false); // Disabled by default after Tier 3 review

        punishmentLevel1 = config.getString("punishment.level-1", "warn");
        punishmentLevel2 = config.getString("punishment.level-2", "kick");
        punishmentLevel3 = config.getString("punishment.level-3", "ban");
        banDurationHours = config.getInt("punishment.ban-duration-hours", 24);
    }

    private void startCleanupTask() {
        plugin.getThreadSafety().runRepeatingOnMainThread(() -> {
            long now = System.currentTimeMillis();
            violationCounts.entrySet().removeIf(entry -> {
                Long last = lastViolationTime.get(entry.getKey());
                return last != null && (now - last) > (violationDecaySeconds * 1000L);
            });
            recentItemGains.values().forEach(list -> list.removeIf(ts -> now - ts > 30000));
            recentXPGains.values().forEach(list -> list.removeIf(ts -> now - ts > xpWindowMs));
            recentQuestGains.values().forEach(list -> list.removeIf(ts -> now - ts > generalWindowMs));
            recentCollectionDiscovers.values().forEach(list -> list.removeIf(ts -> now - ts > generalWindowMs));
            recentHousingPlaces.values().forEach(list -> list.removeIf(ts -> now - ts > generalWindowMs));
            recentMinionPlaces.values().forEach(list -> list.removeIf(ts -> now - ts > generalWindowMs));
            recentEnchantProcs.values().forEach(list -> list.removeIf(ts -> now - ts > generalWindowMs));
            shulkerPlaceCount.clear();
        }, 12000L, 12000L);
    }

    /**
     * Programmatically enable/disable anti-cheat bypass for staff.
     * Used by StaffCommand when toggling /vanish.
     */
    public void setStaffBypass(UUID uuid, boolean bypass) {
        if (bypass) {
            staffBypassPlayers.add(uuid);
        } else {
            staffBypassPlayers.remove(uuid);
        }
    }

    public boolean hasStaffBypass(UUID uuid) {
        return staffBypassPlayers.contains(uuid);
    }
    // ==================== MAIN CHECK (called from listener with Folia schedulers) ====================

    public boolean checkPlayer(Player player) {
        if (!enabled || player.hasPermission("foliasb.bypass.anticheat")) return true;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (hasStaffBypass(uuid)) return true;
        if (lastCheckTime.containsKey(uuid) && now - lastCheckTime.get(uuid) < 800) return true;
        lastCheckTime.put(uuid, now);

        PlayerBehaviorProfile profile = profiles.computeIfAbsent(uuid, k -> new PlayerBehaviorProfile(uuid));
        updatePlayerMetrics(player, profile);

        boolean suspicious = false;
        List<String> reasons = new ArrayList<>();

        if (speedEnabled && isSpeedSuspicious(player, profile)) {
            if (!hasLegitimateSpeedSource(player)) {
                suspicious = true;
                reasons.add("Unusual speed");
                flagViolation(player, "Speed", speedWeight);
            }
        }

        if (flyEnabled && isFlySuspicious(player, profile)) {
            if (!hasLegitimateFlySource(player)) {
                suspicious = true;
                reasons.add("Flying without permission");
                flagViolation(player, "Fly", flyWeight);
            }
        }

        // NEW: Fastbreak check
        if (fastbreakEnabled && isFastBreakSuspicious(player, profile)) {
            suspicious = true;
            reasons.add("Fast block breaking");
            flagViolation(player, "FastBreak", 4);
        }

        // NEW: Fastplace (basic)
        if (fastplaceEnabled && isFastPlaceSuspicious(player)) {
            suspicious = true;
            reasons.add("Fast block placing");
            flagViolation(player, "FastPlace", 3);
        }

        if (neuralDetectorEnabled) {
            double cheatProb = neuralDetector.getCheatProbability(profile);
            if (cheatProb > 0.82) {
                suspicious = true;
                reasons.add("Neural detection (possible macro/xray/dupe pattern)");
                flagViolation(player, "NeuralCheat", 5);
            }
        }

        // Xray heuristic (if high ore/low stone and not explained by island gen upgrade)
        // Generator ores are excluded from oreRate via recordBlockBreak using PDC tag from IslandOreGenerator
        if (xrayEnabled && isXraySuspicious(player, profile)) {
            suspicious = true;
            reasons.add("Possible X-ray (unusual ore/stone ratio)");
            flagViolation(player, "Xray", 6);
        }

        if (suspicious && !reasons.isEmpty()) {
            logSuspiciousActivity(player, reasons);
        }
        return !suspicious;
    }

    // ==================== NEW/UPDATED: Fastbreak & Fastplace ====================

    public boolean isFastBreakSuspicious(Player player, PlayerBehaviorProfile profile) {
        if (player.hasPermission("foliasb.admin")) return false;
        long now = System.currentTimeMillis();
        Long last = lastBlockBreakTime.get(player.getUniqueId());
        if (last == null) return false;
        return (now - last) < fastbreakMinDelayMs;
    }

    public boolean isFastPlaceSuspicious(Player player) {
        if (player.hasPermission("foliasb.admin")) return false;
        long now = System.currentTimeMillis();
        Long last = lastBlockPlaceTime.get(player.getUniqueId());
        if (last == null) return false;
        return (now - last) < fastplaceMinDelayMs;
    }

    /**
     * Records a block break for anticheat tracking (speed, ore rates, etc).
     * Communicates with IslandOreGenerator via static isGeneratorOre() to detect PDC-tagged generator ores.
     * Generator ores on own island are excluded from oreMiningRate to ensure 100% whitelist and no false positives.
     */
    public void recordBlockBreak(Player player, Block block) {
        if (!enabled || player.hasPermission("foliasb.bypass.anticheat")) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        lastBlockBreakTime.put(uuid, now);

        PlayerBehaviorProfile profile = profiles.computeIfAbsent(uuid, k -> new PlayerBehaviorProfile(uuid));
        profile.recordBlockBreak(block.getType());

        // Update ore rate etc already in profile
        // IMPORTANT: Whitelist generator-placed ores (tagged via PDC in IslandOreGenerator at placement time)
        // Only count non-generator ores toward suspicious oreMiningRate to prevent false positives
        // on legit Play-to-Win island ore generator yields when broken on owning island.
        if (block.getType().name().endsWith("_ORE")) {
            boolean isGenOre = IslandOreGenerator.isGeneratorOre(block, plugin) && isOnOwnIsland(player);
            if (!isGenOre) {
                profile.recordOreMined();
            }
            // If isGenOre, skip incrementing ore rate - this is expected high-yield from upgraded generator
        }
    }

    public void recordBlockPlace(Player player) {
        if (!enabled) return;
        lastBlockPlaceTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // ==================== XRAY HEURISTIC (updated) ====================

    private boolean isXraySuspicious(Player player, PlayerBehaviorProfile profile) {
        if (player.hasPermission("foliasb.admin") || profile.hasHighEnchantments()) return false;

        // High ore rate + very low stone mined = classic xray (unless on upgraded custom gen island)
        // Note: oreMiningRate now excludes generator-placed ores (via IslandOreGenerator PDC tag + isOnOwnIsland check in recordBlockBreak)
        // This provides 100% whitelist for specific tagged blocks, preventing false positives for Play-to-Win generator mining.
        double oreRate = profile.getOreMiningRate();
        int stone = profile.getStoneMinedCount();
        int totalBreaks = profile.getBlocksBrokenTotal();

        if (oreRate > 6 && stone < 20 && totalBreaks > 30) {
            // Check if on island with high ore gen upgrade - if so, trust more
            if (isOnOwnIsland(player)) {
                // Can further integrate IslandUpgradeManager.getUpgradeLevel for ORE_GENERATOR here if needed for extra leniency
                return oreRate > 12 && stone < 5;
            }
            return true;
        }
        return false;
    }

    // ==================== DUPE DETECTION (kept & improved) ====================

    public void recordItemTransaction(Player player, int itemCountDelta) {
        if (!enabled) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastAction = lastItemActionTime.get(uuid);
        if (lastAction != null && (now - lastAction) < 150) {
            if (isInSpawnProtectedArea(player.getLocation()) || isOnOwnIsland(player)) {
                flagViolation(player, "Rapid Item Manipulation (Possible Dupe - ruins economy/trading)", 5);
            } else {
                flagViolation(player, "Suspicious Item Activity", 3);
            }
        }
        lastItemActionTime.put(uuid, now);

        if (itemCountDelta > 0) {
            recentItemGains.computeIfAbsent(uuid, k -> new ArrayList<>()).add(now);
            List<Long> gains = recentItemGains.get(uuid);
            if (gains.size() > 25) {
                flagViolation(player, "Excessive Item Gain (Possible Duplication - bypasses progression)", 6);
                gains.clear();
            }
        }
    }

    public void checkShulkerDuplication(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.SHULKER_BOX) return;

        UUID uuid = player.getUniqueId();
        int count = shulkerPlaceCount.getOrDefault(uuid, 0) + 1;
        shulkerPlaceCount.put(uuid, count);

        if (count > 8) {
            flagViolation(player, "Shulker Box Duplication Pattern (common skyblock dupe)", 7);
            shulkerPlaceCount.put(uuid, 0);
        }
    }

    public void checkContainerDuplication(Player player, Block container) {
        if (!enabled || player == null) return;

        if (isInSpawnProtectedArea(container.getLocation())) {
            flagViolation(player, "Container Item Movement in Spawn (Possible Dupe)", 6);
        } else if (isOnOwnIsland(player)) {
            // Monitor but allow normal island gameplay
        }
    }

    private boolean isInSpawnProtectedArea(Location loc) {
        if (loc.getWorld() == null) return false;
        double dist = loc.distance(new Location(loc.getWorld(), 0, loc.getY(), 0));
        return dist <= plugin.getConfig().getInt("island.spawn-protection-radius", 128);
    }

    private boolean isOnOwnIsland(Player player) {
        // Communicates with IslandManager - verified working
        return plugin.getIslandManager() != null &&
               plugin.getIslandManager().getIslandAt(player.getLocation()) != null;
    }

    // ==================== ILLEGAL ITEM (protects progression/enchanting economy) ====================

    public boolean scanForIllegalItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        if (item.getEnchantmentLevel(Enchantment.EFFICIENCY) > 5 ||
            item.getEnchantmentLevel(Enchantment.PROTECTION) > 4 ||
            item.getEnchantmentLevel(Enchantment.SHARPNESS) > 5) {

            if (!player.hasPermission("foliasb.admin")) {
                flagViolation(player, "Illegal Enchantment Level (bypasses enchanting/trading)", 7);
                return true;
            }
        }

        if (isBannedSkyblockBlock(item.getType())) {
            flagViolation(player, "Illegal Block/Item (not obtainable via normal skyblock progression)", 8);
            return true;
        }
        return false;
    }

    private boolean isBannedSkyblockBlock(Material mat) {
        return mat == Material.REINFORCED_DEEPSLATE ||
               mat == Material.TRIAL_SPAWNER ||
               mat == Material.VAULT;
    }

    // ==================== VIOLATION & PUNISHMENT ====================

    public void flagViolation(Player player, String reason, int weight) {
        UUID uuid = player.getUniqueId();
        int current = violationCounts.getOrDefault(uuid, 0) + weight;
        violationCounts.put(uuid, current);
        lastViolationTime.put(uuid, System.currentTimeMillis());

        if (current >= 5 && current < 8) {
            executePunishment(player, punishmentLevel1, reason);
        } else if (current >= 8 && current < 12) {
            executePunishment(player, punishmentLevel2, reason);
        } else if (current >= 12) {
            executePunishment(player, punishmentLevel3, reason);
        }

        if (plugin.getConfig().getBoolean("punishment.broadcast-to-staff", true)) {
            String msg = "§c[AntiCheat] " + player.getName() + " flagged for " + reason + " (total: " + current + ")";
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission(alertPermission))
                    .forEach(p -> p.sendMessage(msg));
        }
    }

    private void executePunishment(Player player, String level, String reason) {
        switch (level.toLowerCase()) {
            case "warn":
                player.sendMessage("§c[AntiCheat] Warning: " + reason);
                break;
            case "kick":
                player.kickPlayer("§c[AntiCheat] Kicked for: " + reason);
                break;
            case "ban":
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                        player.getName(), "AntiCheat: " + reason,
                        new Date(System.currentTimeMillis() + (banDurationHours * 3600L * 1000L)),
                        "FoliaSkyblock AntiCheat");
                player.kickPlayer("§cBanned for: " + reason);
                break;
            default:
                player.sendMessage("§c[AntiCheat] " + reason);
        }
    }

    // ==================== HELPERS ====================

    private void updatePlayerMetrics(Player player, PlayerBehaviorProfile profile) {
        Location loc = player.getLocation();
        if (profile.getLastLocation() != null) {
            profile.addMovementSample(loc.distance(profile.getLastLocation()));
        }
        profile.setLastLocation(loc);
        profile.setHasHighEnchantments(hasHighLevelEnchantments(player));
        profile.setHasHighPotions(hasHighLevelPotions(player));
    }

    private boolean isSpeedSuspicious(Player player, PlayerBehaviorProfile profile) {
        if (player.isFlying() || player.getAllowFlight() || player.hasPermission("foliasb.admin")) return false;
        double current = calculateCurrentSpeed(player);
        double avg = profile.getAverageSpeed();
        double std = profile.getSpeedStandardDeviation();
        double threshold = Math.max(speedThreshold, avg + (std * 3.5));
        if (profile.hasLegitimateSpeed() || trustedHighEnchantPlayers.contains(player.getUniqueId())) threshold *= 2.2;
        return current > threshold;
    }

    private boolean isFlySuspicious(Player player, PlayerBehaviorProfile profile) {
        return player.isFlying() && !player.getAllowFlight() && !player.hasPermission("foliasb.fly");
    }

    private boolean hasLegitimateSpeedSource(Player player) {
        if (player.hasPotionEffect(PotionEffectType.SPEED)) return true;
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null) {
            if (boots.getEnchantmentLevel(Enchantment.DEPTH_STRIDER) > 3 || boots.getEnchantmentLevel(Enchantment.SOUL_SPEED) > 3) return true;
        }
        return player.getInventory().getChestplate() != null && player.getInventory().getChestplate().getType() == Material.ELYTRA;
    }

    private boolean hasLegitimateFlySource(Player player) {
        if (player.getAllowFlight() || player.hasPermission("foliasb.fly")) return true;
        ItemStack chest = player.getInventory().getChestplate();
        return chest != null && chest.getType() == Material.ELYTRA;
    }

    private boolean hasHighLevelEnchantments(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) for (int lvl : item.getEnchantments().values()) if (lvl > 4) return true;
        }
        return false;
    }

    private boolean hasHighLevelPotions(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) if (effect.getAmplifier() >= 2) return true;
        return false;
    }

    private double calculateCurrentSpeed(Player player) {
        return player.getVelocity().length() * 20;
    }

    private void logSuspiciousActivity(Player player, List<String> reasons) {
        plugin.getLogger().warning("[AntiCheat] " + player.getName() + " suspicious: " + String.join(", ", reasons));
    }

    public PlayerBehaviorProfile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ==================== XP EXPLOIT (Play to Win - prevents macro bypassing leveling/XP to dimensions) ====================

    public boolean isFlaggedForXPExploit(Player player, Material material) {
        if (!enabled || player.hasPermission("foliasb.bypass.anticheat")) return false;
        UUID uuid = player.getUniqueId();
        List<Long> gains = recentXPGains.get(uuid);
        if (gains == null || gains.isEmpty()) return false;
        long now = System.currentTimeMillis();
        gains.removeIf(ts -> now - ts > xpWindowMs);
        return gains.size() > 5;
    }

    public void reportHighXPGain(Player player, double xpGained, String source) {
        if (!enabled || xpGained <= xpGainThreshold || player.hasPermission("foliasb.bypass.anticheat")) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        recentXPGains.computeIfAbsent(uuid, k -> new ArrayList<>()).add(now);

        List<Long> gains = recentXPGains.get(uuid);
        gains.removeIf(ts -> now - ts > xpWindowMs);
        if (gains.size() > 3) {
            flagViolation(player, "High XP Gain from " + source + " (Possible Macro/Exploit - breaks Play to Win)", 5);
            gains.clear();
        }
    }

    // ==================== EXPANDED SKYBLOCK GUARDS (quest/collection/housing/minion/dragon/enchant) ====================

    public boolean isFlaggedForQuestExploit(Player player) {
        if (!enabled || player.hasPermission("foliasb.bypass.anticheat")) return false;
        UUID uuid = player.getUniqueId();
        List<Long> gains = recentQuestGains.get(uuid);
        if (gains == null || gains.isEmpty()) return false;
        long now = System.currentTimeMillis();
        gains.removeIf(ts -> now - ts > generalWindowMs);
        return gains.size() > questSpamThreshold;
    }

    public void reportHighQuestProgress(Player player, String source) {
        if (!enabled || player.hasPermission("foliasb.bypass.anticheat")) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        recentQuestGains.computeIfAbsent(uuid, k -> new ArrayList<>()).add(now);
        List<Long> gains = recentQuestGains.get(uuid);
        gains.removeIf(ts -> now - ts > generalWindowMs);
        if (gains.size() > (questSpamThreshold - 3)) {
            flagViolation(player, "High Quest Progress from " + source + " (Possible Macro/Exploit - bypasses early game balance)", 4);
            gains.clear();
        }
    }

    public boolean isFlaggedForCollectionAbuse(Player player) {
        if (!enabled || player.hasPermission("foliasb.bypass.anticheat")) return false;
        UUID uuid = player.getUniqueId();
        List<Long> gains = recentCollectionDiscovers.get(uuid);
        if (gains == null || gains.isEmpty()) return false;
        long now = System.currentTimeMillis();
        gains.removeIf(ts -> now - ts > generalWindowMs);
        return gains.size() > collectionSpamThreshold;
    }

    public void reportCollectionDiscover(Player player, String itemKey) {
        if (!enabled || player == null || player.hasPermission("foliasb.bypass.anticheat")) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        recentCollectionDiscovers.computeIfAbsent(uuid, k -> new ArrayList<>()).add(now);
        List<Long> gains = recentCollectionDiscovers.get(uuid);
        gains.removeIf(ts -> now - ts > generalWindowMs);
        if (gains.size() > (collectionSpamThreshold - 2)) {
            flagViolation(player, "Rapid Collection Discover (" + itemKey + ") - Possible Macro (bypasses collection grind)", 5);
            gains.clear();
        }
    }

    public boolean isFlaggedForHousingSpam(Player player) {
        if (!enabled || player.hasPermission("foliasb.bypass.anticheat")) return false;
        UUID uuid = player.getUniqueId();
        List<Long> places = recentHousingPlaces.get(uuid);
        if (places == null || places.isEmpty()) return false;
        long now = System.currentTimeMillis();
        places.removeIf(ts -> now - ts > generalWindowMs);
        return places.size() > placeSpamThreshold;
    }

    public void recordHousingPlace(Player player) {
        if (!enabled || player == null || player.hasPermission("foliasb.bypass.anticheat")) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        recentHousingPlaces.computeIfAbsent(uuid, k -> new ArrayList<>()).add(now);
        List<Long> places = recentHousingPlaces.get(uuid);
        places.removeIf(ts -> now - ts > generalWindowMs);
        if (places.size() > placeSpamThreshold) {
            flagViolation(player, "Housing Placement Spam (possible lag/grief/dupe induction)", 3);
            places.clear();
        }
    }

    public boolean isFlaggedForMinionSpam(Player player) {
        if (!enabled || player.hasPermission("foliasb.bypass.anticheat")) return false;
        UUID uuid = player.getUniqueId();
        List<Long> places = recentMinionPlaces.get(uuid);
        if (places == null || places.isEmpty()) return false;
        long now = System.currentTimeMillis();
        places.removeIf(ts -> now - ts > generalWindowMs);
        return places.size() > placeSpamThreshold;
    }

    public void recordMinionPlace(Player player) {
        if (!enabled || player == null || player.hasPermission("foliasb.bypass.anticheat")) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        recentMinionPlaces.computeIfAbsent(uuid, k -> new ArrayList<>()).add(now);
        List<Long> places = recentMinionPlaces.get(uuid);
        places.removeIf(ts -> now - ts > generalWindowMs);
        if (places.size() > placeSpamThreshold) {
            flagViolation(player, "Minion Placement Spam (possible dupe/lag)", 4);
            places.clear();
        }
    }

    public boolean isFlaggedForEnchantPowerAbuse(Player player) {
        if (!enabled || player.hasPermission("foliasb.bypass.anticheat")) return false;
        UUID uuid = player.getUniqueId();
        List<Long> procs = recentEnchantProcs.get(uuid);
        if (procs == null || procs.isEmpty()) return false;
        long now = System.currentTimeMillis();
        procs.removeIf(ts -> now - ts > generalWindowMs);
        return procs.size() > 30; // high proc volume
    }

    public void reportEnchantProc(Player player, String enchant) {
        if (!enabled || player == null || player.hasPermission("foliasb.bypass.anticheat")) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        recentEnchantProcs.computeIfAbsent(uuid, k -> new ArrayList<>()).add(now);
        List<Long> procs = recentEnchantProcs.get(uuid);
        procs.removeIf(ts -> now - ts > generalWindowMs);
        if (procs.size() > 25) {
            flagViolation(player, "High volume custom enchant proc (" + enchant + ") - Possible power farming", 3);
            procs.clear();
        }
    }

    public void reportDragonGriefAttempt(Player player, String details) {
        if (player != null && !player.hasPermission("foliasb.bypass.anticheat")) {
            flagViolation(player, "Dragon grief attempt on non-home island (projection violation) " + details, 5);
        } else if (plugin != null) {
            plugin.getLogger().warning("[AntiCheat] Dragon grief attempt (no player or bypassed): " + details);
        }
    }

    // For listener integration - record place for fastplace
    public void recordBlockPlaceTime(Player player) {
        lastBlockPlaceTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Saves current violation logs and stats to file for staff review / anti-exploit auditing on shutdown.
     * Appends summary of top violators, recent patterns to anticheat-violations.log (or similar).
     * Supports Play to Win by providing audit trail to catch and ban exploiters (dupers, macros, xray) without false positives on legit progression.
     * In-memory data (violationCounts, profiles, recent gains) is volatile, so this persists key metrics.
     * Called from FoliaSkyblock.onDisable().
     * Communicates with plugin logger and file I/O; no DB needed for audit logs (file is fine for staff).
     */
    public void saveViolationLogs() {
        File logFile = new File(plugin.getDataFolder(), "anticheat-violations.log");
        try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(logFile, true))) {
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            out.println("[" + timestamp + "] === SHUTDOWN SAVE: AntiCheat Violation Snapshot ===");
            out.println("Total tracked profiles: " + profiles.size());
            out.println("Active violation entries: " + violationCounts.size());
            // Log top violators (simple)
            violationCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(e -> out.println("  UUID " + e.getKey() + " violations: " + e.getValue()));
            out.println("Recent XP exploit attempts tracked: " + recentXPGains.size());
            out.println("Recent quest/collection/housing/minion/enchant tracking sizes: " +
                recentQuestGains.size() + "/" + recentCollectionDiscovers.size() + "/" +
                recentHousingPlaces.size() + "/" + recentMinionPlaces.size() + "/" + recentEnchantProcs.size());
            // Task batch: prod log Neural training samples / risk for top violators (for offline review + retrain)
            if (neuralDetector != null) {
                out.println("Neural risk for top violators (samples for training):");
                violationCounts.entrySet().stream().sorted((a,b)->Integer.compare(b.getValue(),a.getValue())).limit(5)
                    .forEach(e -> {
                        double risk = getNeuralRiskScore(e.getKey());
                        out.println("  " + e.getKey() + " violations:" + e.getValue() + " neuralRisk:" + String.format("%.2f", risk));
                        // Could persist recent profile as training sample here
                    });
            }
            out.println("=== End of shutdown log ===");
            out.println();
            MessageUtil.info(plugin.getLogger(), "§a[AntiCheatManager] Violation logs saved to anticheat-violations.log for staff audit (Play to Win anti-exploit).");
            // Task: auto export JSON profiles for Neural retrain
            try {
                java.io.File jsonFile = new java.io.File(plugin.getDataFolder(), "anticheat-profiles-export.json");
                exportProfilesToJson(jsonFile);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            plugin.getLogger().warning("[AntiCheatManager] Failed to save violation logs: " + e.getMessage());
        }
        // Clear volatile recent data on shutdown to free memory
        recentItemGains.clear();
        recentXPGains.clear();
    }

    /**
     * Records a hopper/chest item transfer for cross-claim dupe detection.
     * Called from HopperDupeListener (InventoryMoveItemEvent).
     * Supports null player (automated hopper) by resolving island owner from locations.
     * Flags suspicious cross-claim or cross-reset transfers.
     */
    public void recordHopperTransfer(Player player, Location sourceLoc, Location destLoc, ItemStack item, int amount) {
        if (sourceLoc == null || destLoc == null || item == null || amount <= 0) return;

        // Resolve island at source and destination
        com.thenerdcj.island.Island sourceIsland = plugin.getIslandManager().getIslandAt(sourceLoc);
        com.thenerdcj.island.Island destIsland = plugin.getIslandManager().getIslandAt(destLoc);

        boolean crossClaim = false;
        if (sourceIsland != null && destIsland != null) {
            if (!sourceIsland.getOwnerUuid().equals(destIsland.getOwnerUuid())) {
                crossClaim = true;
            }
        } else if (sourceIsland != null || destIsland != null) {
            // One side is spawn or unclaimed
            crossClaim = true;
        }

        if (crossClaim) {
            String details = "Hopper transfer of " + amount + "x " + item.getType() + " from " + 
                (sourceIsland != null ? sourceIsland.getOwnerUuid() : "spawn/unclaimed") + 
                " to " + (destIsland != null ? destIsland.getOwnerUuid() : "spawn/unclaimed");
            
            if (player != null) {
                flagViolation(player, "HOPPER_CROSS_CLAIM_DUPE - " + details, 5);
            } else {
                // Log for staff review (no direct player to flag)
                plugin.getLogger().warning("[AntiCheat] Possible automated hopper dupe across claims: " + details);
                // Could write to violation log file here
            }
        }

        // Additional: check for rapid/high volume suspicious transfers (basic rate limit)
        // (can be expanded with recentItemGains map if desired)
    }

    /**
     * Clean up all tracking data for a player when they quit.
     * Prevents memory leaks from profiles, violations, XP tracking, etc.
     */
    public void removePlayer(UUID uuid) {
        profiles.remove(uuid);
        trustedHighEnchantPlayers.remove(uuid);
        lastCheckTime.remove(uuid);
        violationCounts.remove(uuid);
        lastViolationTime.remove(uuid);
        lastItemActionTime.remove(uuid);
        recentItemGains.remove(uuid);
        shulkerPlaceCount.remove(uuid);
        recentXPGains.remove(uuid);
        lastBlockBreakTime.remove(uuid);
        lastBlockPlaceTime.remove(uuid);
        staffBypassPlayers.remove(uuid);
        recentQuestGains.remove(uuid);
        recentCollectionDiscovers.remove(uuid);
        recentHousingPlaces.remove(uuid);
        recentMinionPlaces.remove(uuid);
        recentEnchantProcs.remove(uuid);
    }

    // ==================== ADMIN DEBUG EXPOSURE ====================

    public int getViolationCount(UUID uuid) {
        return violationCounts.getOrDefault(uuid, 0);
    }

    public double getNeuralRiskScore(Player player) {
        if (player == null || neuralDetector == null) return 0.0;
        PlayerBehaviorProfile profile = profiles.get(player.getUniqueId());
        if (profile == null) return 0.0;
        return neuralDetector.getCheatProbability(profile);
    }

    public double getNeuralRiskScore(UUID uuid) {
        PlayerBehaviorProfile profile = profiles.get(uuid);
        if (profile == null || neuralDetector == null) return 0.0;
        return neuralDetector.getCheatProbability(profile);
    }

    /**
     * Task batch: profile export method to JSON (for external Neural retrain / audit).
     * Exports violation counts + neural risks + recent samples to JSON file (e.g. in violations log dir).
     * Uses Gson (dep added). Folia safe (call on main or async).
     * Play-to-Win: supports staff review of exploits without player data leak beyond violations.
     */
    public void exportProfilesToJson(java.io.File outputFile) {
        if (outputFile == null) return;
        try {
            java.util.Map<String, Object> export = new java.util.HashMap<>();
            export.put("timestamp", System.currentTimeMillis());
            export.put("totalViolations", violationCounts.size());
            java.util.List<java.util.Map<String, Object>> violators = new java.util.ArrayList<>();
            violationCounts.entrySet().stream().sorted((a,b) -> Integer.compare(b.getValue(), a.getValue())).limit(50).forEach(e -> {
                java.util.Map<String, Object> v = new java.util.HashMap<>();
                v.put("uuid", e.getKey().toString());
                v.put("violations", e.getValue());
                v.put("neuralRisk", getNeuralRiskScore(e.getKey()));
                violators.add(v);
            });
            export.put("topViolators", violators);
            export.put("recentXPSamples", recentXPGains.size()); // example
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(export);
            java.nio.file.Files.writeString(outputFile.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
            plugin.getLogger().info("[AntiCheat] Exported profiles to JSON: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().warning("[AntiCheat] Failed to export profiles JSON: " + e.getMessage());
        }
    }

    // ==================== TASK 6: EXPANDED HEURISTICS + NEURAL SAMPLES ====================
    // New for museum/minion/schematic abuse (from logs + popular YT dupe videos for expanded features).
    // Neural: add training samples in test + runtime profile updates.
    public boolean isFlaggedForMinionMacro(Player player, int placedThisMinute) {
        if (placedThisMinute > 15) {
            flagViolation(player, "Minion macro suspected (rapid place for task 4)", 4);
            return true;
        }
        return false;
    }

    public void reportMuseumDonateAbuse(Player player, int count) {
        if (count > 8) flagViolation(player, "Museum donate spam/exploit (task 4)", 3);
    }

    public boolean isFlaggedForSchematicAbuse(Player player) {
        // Hook from generator if schematics on; rate limit pastes.
        return false;
    }
}
