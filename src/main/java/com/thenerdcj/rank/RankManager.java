package com.thenerdcj.rank;

import com.thenerdcj.FoliaSkyblock;import com.thenerdcj.util.MessageUtil;import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;import org.bukkit.Bukkit;
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

        MessageUtil.info(plugin.getLogger(), "§aLoaded " + rankDataMap.size() + " dynamic ranks from ranks.yml");
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
        return plugin.getDatabaseManager().getCurrentRankId(uuid)
                .thenApply(rankId -> rankExists(rankId) ? rankId : getDefaultRankId())
                .exceptionally(ex -> getDefaultRankId());
    }

    public CompletableFuture<Integer> getUpvoteCount(UUID uuid) {
        return plugin.getDatabaseManager().getUpvoteCount(uuid)
                .exceptionally(ex -> 0);
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
            if (plugin.getPlayerTagManager() != null) {
                // Use full composed for displayName (includes prestige, rank, tag, name color) similar to other fixes
                String composed = plugin.getPlayerTagManager().getComposedDisplayName(player.getUniqueId(), player.getName());
                player.setDisplayName(composed);
            } else {
                var ampSer = LegacyComponentSerializer.legacyAmpersand();
                var secSer = LegacyComponentSerializer.legacySection();
                var p = ampSer.deserialize(data.getPrefix());
                var s = ampSer.deserialize(data.getSuffix());
                // Force player name to white (cosmetic can override later); rank config controls only [Rank] portion
                var namePart = net.kyori.adventure.text.Component.text(player.getName(), net.kyori.adventure.text.format.NamedTextColor.WHITE);
                var full = p.append(net.kyori.adventure.text.Component.text(" ")).append(namePart).append(s);
                player.displayName(full);
                player.playerListName(full);
            }
            // Re-apply tab list to include full cosmetics + worth (similar tab fix)
            if (plugin.getIslandWorthManager() != null) {
                plugin.getIslandWorthManager().updatePlayerTabList(player);
            }
        }
    }

    public String getPlayerDisplayName(UUID uuid, String playerName) {
        String rankId = getPlayerRankId(uuid);
        RankData data = rankDataMap.get(rankId.toLowerCase());
        if (data != null) {
            var ampSer = LegacyComponentSerializer.legacyAmpersand();
            var secSer = LegacyComponentSerializer.legacySection();
            // Convert & codes (from ranks.yml) to § codes for proper legacy string use in chat/display
            String prefix = secSer.serialize(ampSer.deserialize(data.getPrefix()));
            String suffix = secSer.serialize(ampSer.deserialize(data.getSuffix()));
            // Rank config controls only the rank portion colors. Player name is always white by default (wardrobe cosmetic can override)
            return prefix + "§r§f " + playerName + suffix;
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
        MessageUtil.info(plugin.getLogger(), "§aAll ranks reloaded from ranks.yml (LuckPerms-style dynamic system)");
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

    /**
     * Loads the player's rank from DB into cache synchronously (for join messages etc).
     * Falls back to default on failure.
     */
    public String loadPlayerRankSync(UUID uuid) {
        try {
            String rankId = getCurrentRankId(uuid).get(3, java.util.concurrent.TimeUnit.SECONDS);
            if (rankExists(rankId)) {
                playerRankIds.put(uuid, rankId.toLowerCase());
                return rankId;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Rank] Failed to load rank sync for " + uuid + ": " + e.getMessage());
        }
        String def = getDefaultRankId();
        playerRankIds.put(uuid, def.toLowerCase());
        return def;
    }

    // Optional alias (for compatibility with earlier suggestions)
    public void removeCachedPlayer(UUID uuid) {
        removePlayer(uuid);
    }
}