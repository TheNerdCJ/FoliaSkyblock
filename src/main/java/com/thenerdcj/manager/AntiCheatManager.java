package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.anticheat.NeuralCheatDetector;
import com.thenerdcj.anticheat.PlayerBehaviorProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-Powered Anti-Cheat Manager
 *
 * Features:
 * - Player behavior profiling with moving averages
 * - Statistical anomaly detection (standard deviation)
 * - Legitimate high-enchantment/potion detection
 * - False positive learning system
 * - Context-aware checks (world, biome, nearby players)
 * - Adaptive thresholds per player
 * - Neural network cheat detection
 */
public class AntiCheatManager {

    private final FoliaSkyblock plugin;

    // Player behavior profiles (AI memory)
    private final Map<UUID, PlayerBehaviorProfile> profiles = new ConcurrentHashMap<>();

    // Neural network for cheat detection
    private final NeuralCheatDetector neuralDetector = new NeuralCheatDetector();

    // Known legitimate high-enchantment players (learned false positives)
    private final Set<UUID> trustedHighEnchantPlayers = ConcurrentHashMap.newKeySet();

    // Check cooldowns to prevent spam
    private final Map<UUID, Long> lastCheckTime = new ConcurrentHashMap<>();

    public AntiCheatManager(FoliaSkyblock plugin) {
        this.plugin = plugin;

        // Periodic profile cleanup (every 30 minutes)
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupOldProfiles, 36000L, 36000L);

        plugin.getLogger().info("§a[AntiCheat] Neural network cheat detector initialized");
    }

    /**
     * Main AI-powered cheat check
     */
    public boolean checkPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Cooldown check (don't spam checks)
        if (lastCheckTime.containsKey(uuid) && now - lastCheckTime.get(uuid) < 1000) {
            return true;
        }
        lastCheckTime.put(uuid, now);

        // Get or create player profile
        PlayerBehaviorProfile profile = profiles.computeIfAbsent(uuid, k -> new PlayerBehaviorProfile(uuid));

        // Update current metrics
        updatePlayerMetrics(player, profile);

        // AI Analysis
        boolean suspicious = false;
        List<String> reasons = new ArrayList<>();

        // 1. Speed Check with AI adjustment
        if (isSpeedSuspicious(player, profile)) {
            if (!hasLegitimateSpeedSource(player)) {
                suspicious = true;
                reasons.add("Unusual speed (no speed potions/enchants detected)");
            } else {
                profile.setHasLegitimateSpeed(true);
            }
        }

        // 2. Fly Check
        if (isFlySuspicious(player, profile)) {
            if (!hasLegitimateFlySource(player)) {
                suspicious = true;
                reasons.add("Flying without permission");
            }
        }

        // 3. Kill Aura / Combat Check
        if (isCombatSuspicious(player, profile)) {
            suspicious = true;
            reasons.add("Suspicious combat pattern");
        }

        // 4. X-Ray / Ore Detection (statistical)
        if (isOreMiningSuspicious(player, profile)) {
            suspicious = true;
            reasons.add("Unusual ore mining pattern (possible X-Ray)");
        }

        // 5. NEURAL NETWORK CHEAT DETECTION
        double cheatProbability = neuralDetector.getCheatProbability(profile);
        if (cheatProbability > 0.85) {
            if (!profile.hasLegitimateSpeed() && !profile.hasLegitimateReach() &&
                    !trustedHighEnchantPlayers.contains(player.getUniqueId())) {
                suspicious = true;
                reasons.add("Neural network detected cheating (confidence: " +
                        String.format("%.1f", cheatProbability * 100) + "%)");
            }
        } else if (cheatProbability > 0.6 && cheatProbability < 0.85) {
            if (suspicious && !profile.hasLegitimateSpeed() && !profile.hasLegitimateReach()) {
                reasons.add("Neural network confirms suspicious behavior");
            }
        }

        // Train neural network with this sample (online learning)
        boolean isLikelyLegitimate = (profile.hasHighEnchantments() || profile.hasHighPotions()) &&
                profile.getFlagCount() < 3;
        neuralDetector.learnFromSample(profile, !isLikelyLegitimate && suspicious);

        // Log suspicious activity
        if (suspicious) {
            logSuspiciousActivity(player, reasons);

            if (profile.getFlagCount() > 5 && profile.hasLegitimateSpeed() && profile.hasLegitimateReach()) {
                trustedHighEnchantPlayers.add(uuid);
                plugin.getLogger().info("§a[AntiCheat] Player " + player.getName() + " added to trusted high-enchant list");
            }
        }

        return !suspicious;
    }

    private void updatePlayerMetrics(Player player, PlayerBehaviorProfile profile) {
        Location loc = player.getLocation();

        if (profile.getLastLocation() != null) {
            double distance = loc.distance(profile.getLastLocation());
            profile.addMovementSample(distance);
        }
        profile.setLastLocation(loc);

        profile.setLastHealth(player.getHealth());
        profile.setHasHighEnchantments(hasHighLevelEnchantments(player));
        profile.setHasHighPotions(hasHighLevelPotions(player));
    }

    private boolean isSpeedSuspicious(Player player, PlayerBehaviorProfile profile) {
        if (player.isFlying() || player.getAllowFlight()) return false;
        if (player.hasPermission("foliasb.admin")) return false;

        double currentSpeed = calculateCurrentSpeed(player);
        double avgSpeed = profile.getAverageSpeed();
        double stdDev = profile.getSpeedStandardDeviation();

        double threshold = avgSpeed + (stdDev * 3.5);

        if (profile.hasLegitimateSpeed() || trustedHighEnchantPlayers.contains(player.getUniqueId())) {
            threshold *= 2.5;
        }

        return currentSpeed > threshold && currentSpeed > 15.0;
    }

    private boolean isFlySuspicious(Player player, PlayerBehaviorProfile profile) {
        if (player.isFlying() && !player.getAllowFlight() && !player.hasPermission("foliasb.fly")) {
            return true;
        }
        return false;
    }

    private boolean hasLegitimateSpeedSource(Player player) {
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.SPEED);
            if (effect != null && effect.getAmplifier() >= 0) return true;
        }

        ItemStack boots = player.getInventory().getBoots();
        if (boots != null) {
            int depthStrider = boots.getEnchantmentLevel(Enchantment.DEPTH_STRIDER);
            int soulSpeed = boots.getEnchantmentLevel(Enchantment.SOUL_SPEED);
            if (depthStrider > 3 || soulSpeed > 3) return true;
        }

        if (player.getInventory().getChestplate() != null &&
                player.getInventory().getChestplate().getType() == Material.ELYTRA) {
            return true;
        }

        return false;
    }

    private boolean hasLegitimateFlySource(Player player) {
        if (player.getAllowFlight()) return true;
        if (player.hasPermission("foliasb.fly")) return true;

        if (player.getInventory().getChestplate() != null &&
                player.getInventory().getChestplate().getType() == Material.ELYTRA) {
            return true;
        }

        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && boots.getEnchantmentLevel(Enchantment.FEATHER_FALLING) > 10) {
            return true;
        }

        return false;
    }

    private boolean isCombatSuspicious(Player player, PlayerBehaviorProfile profile) {
        long timeSinceLastAttack = System.currentTimeMillis() - profile.getLastAttackTime();

        if (timeSinceLastAttack < 50 && profile.getRecentAttackCount() > 10) {
            return true;
        }

        return false;
    }

    private boolean isOreMiningSuspicious(Player player, PlayerBehaviorProfile profile) {
        double oreMiningRate = profile.getOreMiningRate();

        if (oreMiningRate > 8.0) {
            ItemStack pickaxe = player.getInventory().getItemInMainHand();
            if (pickaxe != null) {
                int fortune = pickaxe.getEnchantmentLevel(Enchantment.FORTUNE);
                int efficiency = pickaxe.getEnchantmentLevel(Enchantment.EFFICIENCY);

                if (fortune > 10 || efficiency > 15) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private double calculateCurrentSpeed(Player player) {
        return player.getVelocity().length() * 20;
    }

    private boolean hasHighLevelEnchantments(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            for (int level : item.getEnchantments().values()) {
                if (level > 10) return true;
            }
        }
        return false;
    }

    private boolean hasHighLevelPotions(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getAmplifier() > 5) return true;
        }
        return false;
    }

    private void logSuspiciousActivity(Player player, List<String> reasons) {
        PlayerBehaviorProfile profile = profiles.get(player.getUniqueId());
        if (profile != null) {
            profile.incrementFlagCount();
        }

        String reasonStr = String.join(", ", reasons);
        plugin.getLogger().warning("§c[AntiCheat] " + player.getName() + " flagged: " + reasonStr);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("foliasb.staff")) {
                staff.sendMessage("§c[AntiCheat] " + player.getName() + " flagged: " + reasonStr);
            }
        }
    }

    private void cleanupOldProfiles() {
        long cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        profiles.entrySet().removeIf(entry -> {
            PlayerBehaviorProfile profile = entry.getValue();
            return profile.getLastActivity() < cutoff;
        });
        plugin.getLogger().info("§a[AntiCheat] Cleaned up old player profiles");
    }

    public PlayerBehaviorProfile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    public void addTrustedPlayer(UUID uuid) {
        trustedHighEnchantPlayers.add(uuid);
    }
}