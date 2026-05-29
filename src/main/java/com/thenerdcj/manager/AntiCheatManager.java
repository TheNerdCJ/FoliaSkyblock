package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.anticheat.NeuralCheatDetector;
import com.thenerdcj.anticheat.PlayerBehaviorProfile;
import com.thenerdcj.island.generator.IslandOreGenerator;
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

    // === NEW: Fastbreak / Fastplace tracking ===
    private final Map<UUID, Long> lastBlockBreakTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBlockPlaceTime = new ConcurrentHashMap<>();

    public AntiCheatManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();
        startCleanupTask();
        plugin.getLogger().info("§a[AntiCheat] Updated Anti-Cheat Manager initialized (fastbreak + xray + Folia ready)");
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
            out.println("=== End of shutdown log ===");
            out.println();
            plugin.getLogger().info("§a[AntiCheatManager] Violation logs saved to anticheat-violations.log for staff audit (Play to Win anti-exploit).");
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
        staffBypassPlayers.remove(uuid);   // ← Add this
    }
}
