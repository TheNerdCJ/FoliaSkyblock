package com.thenerdcj.rank;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {
    private final FoliaSkyblock plugin;

    private final Map<String, RankData> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerRankIds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastVoteTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> monthlyVotes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> staffRankGrantedTime = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> permissionAttachments = new ConcurrentHashMap<>();

    private File rankFile;
    private YamlConfiguration rankConfig;

    private int maxVotesPerMonth = 10;
    private long voteResetInterval = 30L * 24 * 60 * 60 * 1000;
    private long staffRankGracePeriod = 60L * 24 * 60 * 60 * 1000;

    public RankManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::resetMonthlyVotes,
                20L * 60 * 60 * 24 * 30, 20L * 60 * 60 * 24 * 30);
    }

    private void loadConfig() {
        rankFile = new File(plugin.getDataFolder(), "rank.yml");
        if (!rankFile.exists()) {
            plugin.saveResource("rank.yml", false);
        }
        rankConfig = YamlConfiguration.loadConfiguration(rankFile);

        ranks.clear();
        loadRankSection("default-ranks", "default");
        loadRankSection("donor-ranks", "donor");
        loadRankSection("staff-ranks", "staff");

        maxVotesPerMonth = rankConfig.getInt("voting.max-votes-per-month", 10);

        plugin.getLogger().info("§aLoaded " + ranks.size() + " ranks from rank.yml");
    }

    private void loadRankSection(String sectionName, String category) {
        ConfigurationSection section = rankConfig.getConfigurationSection(sectionName);
        if (section == null) return;

        for (String rankId : section.getKeys(false)) {
            ConfigurationSection rankSection = section.getConfigurationSection(rankId);
            if (rankSection == null) continue;

            String displayName = rankSection.getString("display-name", rankId);
            int level = rankSection.getInt("level", ranks.size());
            int voteRequirement = rankSection.getInt("votes-required", 0);
            double price = rankSection.getDouble("price", 0.0);
            String chatPrefix = rankSection.getString("chat-prefix", "[" + displayName + "]");
            String permission = rankSection.getString("permission", "foliasb.rank." + rankId);

            List<String> perks = rankSection.getStringList("perks");
            List<String> inherits = rankSection.getStringList("inherits");
            List<String> permissions = rankSection.getStringList("permissions");

            RankData rankData = new RankData(
                    rankId.toUpperCase(), displayName, level, category,
                    voteRequirement, price, chatPrefix, permission,
                    perks, inherits, permissions
            );

            ranks.put(rankId.toUpperCase(), rankData);
        }
    }

    public RankData getRank(UUID uuid) {
        String rankId = playerRankIds.getOrDefault(uuid, "MEMBER");
        return ranks.getOrDefault(rankId, ranks.get("MEMBER"));
    }

    public RankData getRankById(String rankId) {
        return ranks.get(rankId.toUpperCase());
    }

    public void setRank(UUID uuid, String rankId) {
        RankData rank = ranks.get(rankId.toUpperCase());
        if (rank == null) {
            plugin.getLogger().warning("Unknown rank: " + rankId);
            return;
        }

        playerRankIds.put(uuid, rankId.toUpperCase());

        if (rank.isStaff()) {
            staffRankGrantedTime.put(uuid, System.currentTimeMillis());
        }

        plugin.getDatabaseManager().setRank(uuid, rankId);
        applyRankPermissions(uuid, rank);
    }

    private void applyRankPermissions(UUID uuid, RankData rank) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        PermissionAttachment oldAttachment = permissionAttachments.remove(uuid);
        if (oldAttachment != null) {
            oldAttachment.remove();
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        permissionAttachments.put(uuid, attachment);

        attachment.setPermission(rank.getPermission(), true);
        attachment.setPermission("foliasb.category." + rank.getCategory(), true);

        for (String inheritId : rank.getInherits()) {
            RankData inherited = ranks.get(inheritId.toUpperCase());
            if (inherited != null) {
                for (String perm : inherited.getPermissions()) {
                    attachment.setPermission(perm, true);
                }
            }
        }

        for (String perm : rank.getPermissions()) {
            attachment.setPermission(perm, true);
        }
    }

    public CompletableFuture<Boolean> canVote(UUID voterUuid, UUID targetUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (voterUuid.equals(targetUuid)) return false;

            Long lastVote = lastVoteTime.get(voterUuid);
            if (lastVote != null) {
                long timeSinceLastVote = System.currentTimeMillis() - lastVote;
                if (timeSinceLastVote < voteResetInterval) return false;
            }

            int votesThisMonth = monthlyVotes.getOrDefault(voterUuid, 0);
            if (votesThisMonth >= maxVotesPerMonth) return false;

            return true;
        });
    }

    public CompletableFuture<Boolean> castVote(UUID voterUuid, UUID targetUuid) {
        return canVote(voterUuid, targetUuid).thenApply(canVote -> {
            if (!canVote) return false;

            lastVoteTime.put(voterUuid, System.currentTimeMillis());
            monthlyVotes.merge(voterUuid, 1, Integer::sum);

            int currentVotes = getUpvoteCount(targetUuid).join();
            int newVotes = currentVotes + 1;

            plugin.getDatabaseManager().addVote(targetUuid, voterUuid);
            checkForAutoPromotion(targetUuid, newVotes);

            return true;
        });
    }

    public void checkForAutoPromotion(UUID uuid, int currentVotes) {
        RankData currentRank = getRank(uuid);
        if (currentRank == null || !currentRank.isDefault()) return;

        RankData newRank = null;
        int highestVotes = 0;

        for (RankData rank : ranks.values()) {
            if (rank.isStaff() && currentVotes >= rank.getVoteRequirement()) {
                if (rank.getVoteRequirement() > highestVotes) {
                    highestVotes = rank.getVoteRequirement();
                    newRank = rank;
                }
            }
        }

        if (newRank != null && !newRank.getId().equals(currentRank.getId())) {
            setRank(uuid, newRank.getId());

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage("§a§lCongratulations! §eYou've been promoted to " + newRank.getDisplayName() + "!");
                player.sendMessage("§7You received §e" + currentVotes + "§7 votes from the community!");
            }

            Bukkit.broadcastMessage("§6§l[PROMOTION] §e" +
                    Bukkit.getOfflinePlayer(uuid).getName() + " §7has been promoted to §b" + newRank.getDisplayName() + "§7!");
        }
    }

    public void checkForAutoPromotion(UUID uuid) {
        int votes = getUpvoteCount(uuid).join();
        checkForAutoPromotion(uuid, votes);
    }

    public CompletableFuture<Integer> getUpvoteCount(UUID uuid) {
        return CompletableFuture.completedFuture(
                plugin.getDatabaseManager().getUpvoteCount(uuid)
        );
    }

    public Collection<RankData> getAllRanks() {
        return ranks.values();
    }

    public void applyRankPrefix(Player player) {
        RankData rank = getRank(player.getUniqueId());
        if (rank != null) {
            // Prefix handled by chat format
        }
    }

    public String getPlayerRankId(UUID uuid) {
        RankData rank = getRank(uuid);
        return rank != null ? rank.getId() : "MEMBER";
    }

    public void reloadRanks() {
        loadConfig();
        playerRankIds.clear();
    }

    private void resetMonthlyVotes() {
        for (Map.Entry<UUID, String> entry : playerRankIds.entrySet()) {
            UUID uuid = entry.getKey();
            RankData rank = ranks.get(entry.getValue());

            if (rank != null && rank.isStaff()) {
                Long grantedTime = staffRankGrantedTime.get(uuid);
                if (grantedTime != null) {
                    long timeSinceGranted = System.currentTimeMillis() - grantedTime;
                    int currentVotes = getUpvoteCount(uuid).join();

                    if (timeSinceGranted > staffRankGracePeriod && currentVotes < rank.getVoteRequirement()) {
                        RankData demoteTo = getDemotionRank(rank);
                        setRank(uuid, demoteTo.getId());

                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.sendMessage("§c§lYour " + rank.getDisplayName() + " rank has been removed!");
                            player.sendMessage("§7You didn't meet the minimum vote requirement of §e" +
                                    rank.getVoteRequirement() + "§7 votes.");
                        }
                    }
                }
            }
        }

        monthlyVotes.clear();
        lastVoteTime.clear();
        plugin.getLogger().info("§aMonthly vote reset completed!");
    }

    private RankData getDemotionRank(RankData currentRank) {
        RankData highestLower = ranks.get("MEMBER");

        for (RankData rank : ranks.values()) {
            if (rank.isStaff() && rank.getLevel() < currentRank.getLevel()) {
                if (highestLower == null || rank.getLevel() > highestLower.getLevel()) {
                    highestLower = rank;
                }
            }
        }

        return highestLower != null ? highestLower : ranks.get("MEMBER");
    }
}