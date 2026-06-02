package com.thenerdcj.tags;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles overhead nametags (the floating name above the player's head).
 *
 * Implementation: Dedicated cosmetic scoreboard + teams with prefix/suffix.
 * This is the classic, widely compatible approach used by NameTagEdit, TAB, etc.
 *
 * Folia-safe: All scoreboard mutations are scheduled on the main thread.
 *
 * The cosmetic tag (from PlayerTagManager) is combined with a short rank indicator.
 */
public class PlayerNametagManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private Scoreboard cosmeticBoard;

    // Cache of teams we manage (player uuid -> team name)
    private final ConcurrentHashMap<UUID, String> playerTeams = new ConcurrentHashMap<>();

    // Players who have chosen to hide their overhead nametag
    private final Set<UUID> nametagDisabled = ConcurrentHashMap.newKeySet();

    private static final String BOARD_NAME = "cosmetic_tags";
    private static final int MAX_TEAM_NAME = 16; // legacy team name limit (safe)

    public PlayerNametagManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
        initScoreboard();
    }

    private void initScoreboard() {
        threadSafety.runOnMainThread(() -> {
            cosmeticBoard = Bukkit.getScoreboardManager().getNewScoreboard();
            // We don't register it globally to avoid interfering with other plugins.
            // Each player will have this board set when we want custom nametags.
        });
    }

    /**
     * Applies (or refreshes) the overhead nametag for a player.
     * Combines rank short form + active cosmetic tag.
     */
    public void applyNametag(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();

        if (nametagDisabled.contains(uuid)) {
            removeNametag(player);
            return;
        }

        String tagText = "";
        if (plugin.getPlayerTagManager() != null) {
            var active = plugin.getPlayerTagManager().getActiveTag(uuid);
            if (active != null && !active.isNone()) {
                tagText = active.getTagText();
            }
        }

        // Better short rank logic: use the actual rank prefix from RankData (clean, no player name)
        String rankShort = "";
        if (plugin.getRankManager() != null) {
            var rankData = plugin.getRankManager().getPlayerRankData(uuid);
            if (rankData != null) {
                String prefix = rankData.getPrefix();
                if (prefix != null) {
                    // Take a safe short version for nametag (teams have limits)
                    rankShort = org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix);
                    if (rankShort.length() > 10) rankShort = rankShort.substring(0, 10);
                }
            }
        }

        final String finalPrefix = (rankShort + " " + tagText).trim();
        final String finalSuffix = "";

        threadSafety.runOnMainThread(() -> {
            String teamName = "tag_" + uuid.toString().substring(0, 10).replace("-", "");
            if (teamName.length() > MAX_TEAM_NAME) {
                teamName = teamName.substring(0, MAX_TEAM_NAME);
            }

            Team team = cosmeticBoard.getTeam(teamName);
            if (team == null) {
                team = cosmeticBoard.registerNewTeam(teamName);
            }

            // Important: prefix/suffix on team controls what shows above head
            team.setPrefix(org.bukkit.ChatColor.translateAlternateColorCodes('&', finalPrefix + " "));
            team.setSuffix(org.bukkit.ChatColor.translateAlternateColorCodes('&', finalSuffix));

            // Add the player to the team (removes from previous if needed)
            if (!team.hasEntry(player.getName())) {
                // Remove from any old team we managed
                for (Team t : cosmeticBoard.getTeams()) {
                    if (t.hasEntry(player.getName())) t.removeEntry(player.getName());
                }
                team.addEntry(player.getName());
            }

            // Give the player our cosmetic scoreboard so the team is visible to everyone
            // (This is the common pattern; it can be improved with per-viewer boards later)
            player.setScoreboard(cosmeticBoard);

            playerTeams.put(uuid, teamName);
        });
    }

    public void removeNametag(Player player) {
        if (player == null) return;

        threadSafety.runOnMainThread(() -> {
            String teamName = playerTeams.remove(player.getUniqueId());
            if (teamName != null) {
                Team team = cosmeticBoard.getTeam(teamName);
                if (team != null) {
                    team.removeEntry(player.getName());
                    if (team.getEntries().isEmpty()) {
                        team.unregister();
                    }
                }
            }

            // Reset to main scoreboard (vanilla names)
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        });
    }

    public void onPlayerJoin(Player player) {
        // Slight delay so rank data is loaded
        threadSafety.runOnMainThreadLater(() -> applyNametag(player), 10L);
    }

    public void onPlayerQuit(Player player) {
        removeNametag(player);
    }

    /**
     * Call this whenever a player's tag or rank changes.
     */
    public void refreshNametag(Player player) {
        removeNametag(player);
        threadSafety.runOnMainThreadLater(() -> applyNametag(player), 2L);
    }

    /**
     * Toggles whether this player's overhead nametag (cosmetic tag above head) is visible to others.
     */
    public boolean toggleNametagVisibility(Player player) {
        UUID uuid = player.getUniqueId();
        boolean nowDisabled = nametagDisabled.contains(uuid);

        if (nowDisabled) {
            nametagDisabled.remove(uuid);
            applyNametag(player);
            return true; // now visible
        } else {
            nametagDisabled.add(uuid);
            removeNametag(player);
            return false; // now hidden
        }
    }

    public boolean isNametagVisible(UUID uuid) {
        return !nametagDisabled.contains(uuid);
    }
}
