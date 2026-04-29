package com.thenerdcj.rank;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RankManager {

    private final FoliaSkyblock plugin;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();

    public RankManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadRanks();
    }

    public void loadRanks() {
        ranks.clear();

        File file = new File(plugin.getDataFolder(), "ranks.yml");
        if (!file.exists()) {
            plugin.saveResource("ranks.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            String id = key.toLowerCase();
            String prefix = config.getString(key + ".prefix", "&7[Member]");
            int priority = config.getInt(key + ".priority", 0);
            int minVotes = config.getInt(key + ".min-votes", 0);

            ranks.put(id, new Rank(id, prefix, priority, minVotes));
        }

        plugin.getLogger().info("§aLoaded " + ranks.size() + " ranks from ranks.yml");
    }

    public void reloadRanks() {
        loadRanks();
    }

    public Rank getRank(String id) {
        return ranks.get(id.toLowerCase());
    }

    public Map<String, Rank> getAllRanks() {
        return Collections.unmodifiableMap(ranks);
    }

    /**
     * Returns the formatted prefix for a player (used by ChatManager)
     */
    public String getRankPrefix(Player player) {
        return getPlayerRankId(player.getUniqueId())
                .thenApply(rankId -> {
                    Rank rank = getRank(rankId);
                    return (rank != null) ? ChatColor.translateAlternateColorCodes('&', rank.getPrefix()) : "§7";
                })
                .join(); // Safe here because this is called on main thread only
    }

    public CompletableFuture<String> getPlayerRankId(UUID uuid) {
        return plugin.getDatabaseManager().getCurrentRankId(uuid);
    }

    public void applyRankPrefix(Player player) {
        getPlayerRankId(player.getUniqueId())
                .thenAccept(rankId -> {
                    Rank rank = getRank(rankId);
                    if (rank != null) {
                        String prefix = ChatColor.translateAlternateColorCodes('&', rank.getPrefix());
                        player.setDisplayName(prefix + player.getName());
                        player.setPlayerListName(prefix + player.getName());
                    }
                });
    }

    public void checkForAutoPromotion(UUID uuid) {
        plugin.getDatabaseManager().getUpvoteCount(uuid)
                .thenAccept(votes -> {
                    for (Rank rank : ranks.values()) {
                        if (votes >= rank.getMinVotes()) {
                            plugin.getDatabaseManager().setRank(uuid, rank.getId())
                                    .thenAccept(success -> {
                                        if (success) {
                                            Player p = plugin.getServer().getPlayer(uuid);
                                            if (p != null) {
                                                applyRankPrefix(p);
                                                p.sendMessage("§aYou have been automatically promoted to §e" + rank.getId().toUpperCase() + "§a!");
                                            }
                                        }
                                    });
                            break;
                        }
                    }
                });
    }

    // ====================== INNER RANK CLASS ======================
    public static class Rank {
        private final String id;
        private final String prefix;
        private final int priority;
        private final int minVotes;

        public Rank(String id, String prefix, int priority, int minVotes) {
            this.id = id;
            this.prefix = prefix;
            this.priority = priority;
            this.minVotes = minVotes;
        }

        public String getId() {
            return id;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getFormattedPrefix() {
            return ChatColor.translateAlternateColorCodes('&', prefix);
        }

        public int getPriority() {
            return priority;
        }

        public int getMinVotes() {
            return minVotes;
        }
    }
}