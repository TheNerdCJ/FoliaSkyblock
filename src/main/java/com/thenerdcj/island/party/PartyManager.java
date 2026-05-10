package com.thenerdcj.island.party;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandRank;
import com.thenerdcj.island.IslandUpgrade;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PartyManager - Handles dynamic party configuration and "smart" party features.
 * 
 * UPDATED: Now integrates with IslandUpgrade.MEMBER_LIMIT to make member limits
 * purchasable upgrades using island balance.
 */
public class PartyManager {

    private final FoliaSkyblock plugin;
    private FileConfiguration config;
    private File configFile;

    // Runtime data
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();

    // Smart scoring cache (simple in-memory, can be persisted later)
    private final Map<UUID, Double> contributionScores = new ConcurrentHashMap<>();

    public PartyManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "parties.yml");
        if (!configFile.exists()) {
            plugin.saveResource("parties.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save parties.yml: " + e.getMessage());
        }
    }

    // ==================== CONFIG GETTERS ====================

    public int getMaxPartySize() {
        return config.getInt("party.max-size", 8);
    }

    public int getInviteTimeoutSeconds() {
        return config.getInt("party.invite-timeout-seconds", 300);
    }

    public boolean isXpBalancingEnabled() {
        return config.getBoolean("party.xp-balancing.enabled", true);
    }

    public double getXpMultiplierForSize(int partySize) {
        if (!isXpBalancingEnabled() || partySize <= 1) return 1.0;

        return switch (partySize) {
            case 2 -> config.getDouble("party.xp-balancing.size-2", 0.85);
            case 3 -> config.getDouble("party.xp-balancing.size-3", 0.75);
            default -> config.getDouble("party.xp-balancing.size-4-plus", 0.55);
        };
    }

    public IslandRank getDefaultInviteRank() {
        String rankName = config.getString("party.permissions.default-invite-rank", "GUEST");
        try {
            return IslandRank.valueOf(rankName.toUpperCase());
        } catch (Exception e) {
            return IslandRank.GUEST;
        }
    }

    public boolean areSmartFeaturesEnabled() {
        return config.getBoolean("party.smart-features.enable-suggestions", true);
    }

    // ==================== NEW: Upgrade-integrated member limit ====================

    /**
     * Returns the effective max party size for a specific island,
     * including purchased MEMBER_LIMIT upgrades.
     * Base from config + extra levels from upgrade.
     */
    public int getEffectiveMaxPartySize(Island island) {
        int base = getMaxPartySize();
        if (island == null) return base;

        try {
            String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
            int extraLevels = 0;
            if (plugin.getIslandUpgradeManager() != null) {
                extraLevels = plugin.getIslandUpgradeManager().getUpgradeLevel(islandId, IslandUpgrade.MEMBER_LIMIT);
            }
            return Math.min(base + extraLevels, 20); // hard cap at 20 for balance
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load MEMBER_LIMIT upgrade for island: " + e.getMessage());
            return base;
        }
    }

    /**
     * Backward compatible: uses global max if no island context.
     */
    public int getMaxPartySize(Island island) {
        if (island != null) {
            return getEffectiveMaxPartySize(island);
        }
        return getMaxPartySize();
    }

    // ==================== PENDING INVITES ====================

    public void addPendingInvite(UUID inviter, UUID target, Island island) {
        PendingInvite invite = new PendingInvite(inviter, target, island.getCenter(Bukkit.getWorlds().get(0)).getWorld().getEnvironment(), System.currentTimeMillis());
        pendingInvites.put(target, invite);

        // Auto-expire after timeout
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingInvites.containsKey(target) && pendingInvites.get(target).getTimestamp() == invite.getTimestamp()) {
                pendingInvites.remove(target);
                Player p = Bukkit.getPlayer(target);
                if (p != null) p.sendMessage("§7Your island invite from §e" + Bukkit.getOfflinePlayer(inviter).getName() + " §7has expired.");
            }
        }, getInviteTimeoutSeconds() * 20L);
    }

    public PendingInvite getPendingInvite(UUID player) {
        return pendingInvites.get(player);
    }

    public void removePendingInvite(UUID player) {
        pendingInvites.remove(player);
    }

    public boolean hasPendingInvite(UUID player) {
        return pendingInvites.containsKey(player);
    }

    // ==================== SMART / "AI-LIKE" PARTY FEATURES ====================

    /**
     * Calculates a simple contribution score for a player on an island.
     * This is a lightweight heuristic (can be expanded with more data sources later).
     * Higher score = better candidate for party / promotion.
     */
    public double calculateContributionScore(UUID playerUuid, Island island) {
        // Simple weighted score (can pull real data from IslandXPListener or DB later)
        double score = contributionScores.getOrDefault(playerUuid, 50.0); // base score

        // Bonus for being online and active
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            score += 15;
        }

        // Future: Add real metrics like blocks placed, mobs killed on this island, playtime on island, etc.
        // For now this gives "smart" behavior without ML dependencies.

        contributionScores.put(playerUuid, Math.min(100.0, score));
        return score;
    }

    /**
     * Suggests good players to invite based on recent activity / contribution.
     * This is the "AI-assisted" part — rule-based smart recommendations.
     */
    public List<UUID> suggestPotentialMembers(Island island, int maxSuggestions) {
        if (!areSmartFeaturesEnabled()) return Collections.emptyList();

        List<UUID> suggestions = new ArrayList<>();

        // Simple heuristic: Look at recent online players who have been near the island or have high contribution
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (island.isMember(online.getUniqueId())) continue;
            if (suggestions.size() >= maxSuggestions) break;

            double score = calculateContributionScore(online.getUniqueId(), island);
            if (score > 60) { // threshold for "good" candidate
                suggestions.add(online.getUniqueId());
            }
        }

        // Sort by score descending (highest contributors first)
        suggestions.sort((a, b) -> Double.compare(
            calculateContributionScore(b, island),
            calculateContributionScore(a, island)
        ));

        return suggestions;
    }

    /**
     * Decides if a member should be auto-promote (simple rule-based "AI").
     */
    public boolean shouldAutoPromote(UUID playerUuid, Island island) {
        if (!areSmartFeaturesEnabled()) return false;

        double score = calculateContributionScore(playerUuid, island);
        IslandRank currentRank = island.getRank(playerUuid);

        return currentRank == IslandRank.GUEST && score > 75;
    }

    public void recordContribution(UUID playerUuid, double amount) {
        double current = contributionScores.getOrDefault(playerUuid, 50.0);
        contributionScores.put(playerUuid, Math.min(100.0, current + amount));
    }

    // ==================== INNER CLASS ====================

    public static class PendingInvite {
        private final UUID inviter;
        private final UUID target;
        private final org.bukkit.World.Environment dimension;
        private final long timestamp;

        public PendingInvite(UUID inviter, UUID target, org.bukkit.World.Environment dimension, long timestamp) {
            this.inviter = inviter;
            this.target = target;
            this.dimension = dimension;
            this.timestamp = timestamp;
        }

        public UUID getInviter() { return inviter; }
        public UUID getTarget() { return target; }
        public org.bukkit.World.Environment getDimension() { return dimension; }
        public long getTimestamp() { return timestamp; }
    }
}
