package com.thenerdcj.rank;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * RankManager - FULLY DYNAMIC Rank System (LuckPerms-style)
 * All ranks are loaded from ranks.yml - completely configurable!
 *
 * Features:
 * - Unlimited custom ranks
 * - Dynamic permissions per rank
 * - Community voting for staff promotions
 * - Prefix/suffix support
 * - Priority-based rank ordering
 */
public class RankManager {

    private final FoliaSkyblock plugin;
    private final Map<UUID, String> playerRankIds = new HashMap<>();
    private final Map<String, RankData> rankDataMap = new LinkedHashMap<>();
    private File rankFile;
    private FileConfiguration rankConfig;

    // Community voting thresholds (configurable)
    private int helperVoteThreshold = 50;
    private int moderatorVoteThreshold = 150;
    private long voteCooldownMs = 30L * 24 * 60 * 60 * 1000; // 30 days

    public RankManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadRankConfig();
        loadPlayerRanks();
    }

    private void loadRankConfig() {
        rankFile = new File(plugin.getDataFolder(), "ranks.yml");
        if (!rankFile.exists()) {
            plugin.saveResource("ranks.yml", false);
        }
        rankConfig = YamlConfiguration.loadConfiguration(rankFile);
        loadRankData();
        loadVotingConfig();
    }

    private void loadVotingConfig() {
        helperVoteThreshold = rankConfig.getInt("voting.helper-threshold", 50);
        moderatorVoteThreshold = rankConfig.getInt("voting.moderator-threshold", 150);
        voteCooldownMs = rankConfig.getLong("voting.cooldown-days", 30) * 24 * 60 * 60 * 1000;
    }

    private void loadRankData() {
        rankDataMap.clear();

        if (rankConfig.contains("ranks")) {
            for (String rankId : rankConfig.getConfigurationSection("ranks").getKeys(false)) {
                String path = "ranks." + rankId;

                String displayName = rankConfig.getString(path + ".display-name", rankId);
                String prefix = rankConfig.getString(path + ".prefix", "&7[" + rankId + "]");
                String suffix = rankConfig.getString(path + ".suffix", "");
                List<String> permissions = rankConfig.getStringList(path + ".permissions");
                int priority = rankConfig.getInt(path + ".priority", 0);
                boolean isStaff = rankConfig.getBoolean(path + ".staff", false);
                boolean isDonor = rankConfig.getBoolean(path + ".donor", false);
                String parent = rankConfig.getString(path + ".parent", null);
                boolean isDefault = rankConfig.getBoolean(path + ".default", false);

                RankData data = new RankData(rankId, displayName, prefix, suffix,
                        permissions, priority, isStaff, isDonor, parent, isDefault);
                rankDataMap.put(rankId.toLowerCase(), data);
            }
        }

        plugin.getLogger().info("§aLoaded " + rankDataMap.size() + " dynamic ranks from ranks.yml");
    }

    private void loadPlayerRanks() {
        playerRankIds.clear();
        // Player ranks are loaded from database on-demand
    }

    // ====================== DYNAMIC RANK GETTERS ======================


    public String getPlayerRankId(UUID uuid) {
        return playerRankIds.getOrDefault(uuid, getDefaultRankId());
    }

    public RankData getPlayerRankData(UUID uuid) {
        String rankId = getPlayerRankId(uuid);
        return rankDataMap.get(rankId.toLowerCase());
    }

    public String getDefaultRankId() {
        // First, look for a rank explicitly marked as default
        for (RankData data : rankDataMap.values()) {
            if (data.isDefault()) {
                return data.getRankId();
            }
        }

        // Fallback: Return the rank with lowest priority
        return rankDataMap.values().stream()
                .min(Comparator.comparingInt(RankData::getPriority))
                .map(RankData::getRankId)
                .orElse("member");
    }

    public List<RankData> getAllRanksSorted() {
        return rankDataMap.values().stream()
                .sorted(Comparator.comparingInt(RankData::getPriority))
                .toList();
    }

    public RankData getRankData(String rankId) {
        return rankDataMap.get(rankId.toLowerCase());
    }

    public boolean rankExists(String rankId) {
        return rankDataMap.containsKey(rankId.toLowerCase());
    }

    // ====================== DATABASE INTEGRATION ======================

    public CompletableFuture<String> getCurrentRankId(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String rankId = plugin.getDatabaseManager().getCurrentRankId(uuid).join();
                return rankExists(rankId) ? rankId : getDefaultRankId();
            } catch (Exception e) {
                return getDefaultRankId();
            }
        });
    }

    public CompletableFuture<Integer> getUpvoteCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return plugin.getDatabaseManager().getUpvoteCount(uuid).join();
            } catch (Exception e) {
                return 0;
            }
        });
    }

    public void checkForAutoPromotion(UUID uuid) {
        getUpvoteCount(uuid).thenAccept(votes -> {
            String currentRankId = getPlayerRankId(uuid);
            RankData currentRank = getRankData(currentRankId);

            if (currentRank == null) return;

            // Check for moderator promotion
            if (votes >= moderatorVoteThreshold && currentRank.isStaff() &&
                    currentRankId.equalsIgnoreCase("helper")) {
                setPlayerRank(uuid, "moderator");
                notifyPromotion(uuid, "Moderator");
            }
            // Check for helper promotion
            else if (votes >= helperVoteThreshold && !currentRank.isStaff() &&
                    currentRankId.equalsIgnoreCase("member")) {
                setPlayerRank(uuid, "helper");
                notifyPromotion(uuid, "Helper");
            }
        });
    }

    private void notifyPromotion(UUID uuid, String newRankName) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage("§a§lCongratulations! §7You have been promoted to §e" + newRankName + "§7!");
            applyRankPrefix(player);
        }
    }

    // ====================== RANK SETTING ======================

    public void setPlayerRank(UUID uuid, String rankId) {
        if (!rankExists(rankId)) {
            plugin.getLogger().warning("Attempted to set non-existent rank: " + rankId);
            return;
        }

        playerRankIds.put(uuid, rankId.toLowerCase());

        // Save to database
        plugin.getDatabaseManager().setRank(uuid, rankId.toLowerCase());

        // Apply prefix if player is online
        org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applyRankPrefix(player);
        }
    }

    public void setPlayerRank(UUID uuid, RankData rankData) {
        setPlayerRank(uuid, rankData.getRankId());
    }

    // ====================== PERMISSION CHECKING ======================

    public boolean hasPermission(UUID uuid, String permission) {
        String rankId = getPlayerRankId(uuid);
        RankData data = rankDataMap.get(rankId.toLowerCase());

        if (data != null) {
            return data.hasPermission(permission);
        }
        return false;
    }

    public List<String> getPlayerPermissions(UUID uuid) {
        String rankId = getPlayerRankId(uuid);
        RankData data = rankDataMap.get(rankId.toLowerCase());

        if (data != null) {
            return data.getPermissions();
        }
        return Collections.emptyList();
    }

    // ====================== PREFIX/SUFFIX APPLICATION ======================

    public void applyRankPrefix(org.bukkit.entity.Player player) {
        String rankId = getPlayerRankId(player.getUniqueId());
        RankData data = rankDataMap.get(rankId.toLowerCase());

        if (data != null && player.isOnline()) {
            String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', data.getPrefix());
            String suffix = org.bukkit.ChatColor.translateAlternateColorCodes('&', data.getSuffix());

            player.setDisplayName(prefix + " " + player.getName() + suffix);
            player.setPlayerListName(prefix + " " + player.getName() + suffix);
        }
    }

    public String getPlayerDisplayName(UUID uuid, String playerName) {
        String rankId = getPlayerRankId(uuid);
        RankData data = rankDataMap.get(rankId.toLowerCase());

        if (data != null) {
            String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', data.getPrefix());
            String suffix = org.bukkit.ChatColor.translateAlternateColorCodes('&', data.getSuffix());
            return prefix + " " + playerName + suffix;
        }
        return playerName;
    }

    // ====================== VOTING SYSTEM ======================

    public CompletableFuture<Boolean> addVote(UUID voterUuid, UUID targetUuid) {
        return plugin.getDatabaseManager().addVote(voterUuid, targetUuid).thenApply(success -> {
            if (success) {
                checkForAutoPromotion(targetUuid);
            }
            return success;
        });
    }

    // ====================== CONFIG MANAGEMENT ======================

    public void reloadRanks() {
        loadRankConfig();
        playerRankIds.clear();
        plugin.getLogger().info("§aAll ranks reloaded from ranks.yml (LuckPerms-style dynamic system)");
    }

    public void saveRankConfig() {
        try {
            rankConfig.save(rankFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save ranks.yml: " + e.getMessage());
        }
    }

    // ====================== UTILITY METHODS ======================

    public int getRankCount() {
        return rankDataMap.size();
    }

    public boolean isStaffRank(String rankId) {
        RankData data = rankDataMap.get(rankId.toLowerCase());
        return data != null && data.isStaff();
    }

    public boolean isDonorRank(String rankId) {
        RankData data = rankDataMap.get(rankId.toLowerCase());
        return data != null && data.isDonor();
    }
    /**
     * Remove cached rank data for a player when they quit.
     */
    public void removePlayer(UUID uuid) {
        playerRankIds.remove(uuid);
    }

    // Optional alias (for compatibility with earlier suggestions)
    public void removeCachedPlayer(UUID uuid) {
        removePlayer(uuid);
    }
}