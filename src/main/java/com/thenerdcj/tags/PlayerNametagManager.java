package com.thenerdcj.tags;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;import net.kyori.adventure.text.Component;import org.bukkit.Bukkit;
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
 * On Folia, scoreboard creation is region-bound — work runs on the player's entity scheduler,
 * with a display-name fallback when custom scoreboards are unavailable.
 */
public class PlayerNametagManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private Scoreboard cosmeticBoard;
    private boolean scoreboardUnsupported;

    private final ConcurrentHashMap<UUID, String> playerTeams = new ConcurrentHashMap<>();
    private final Set<UUID> nametagDisabled = ConcurrentHashMap.newKeySet();

    private static final int MAX_TEAM_NAME = 16;

    public PlayerNametagManager(FoliaSkyblock plugin){this.plugin=plugin;this.threadSafety=plugin.getThreadSafety();threadSafety.runOnMainThread(this::ensureCosmeticBoard);}

    private void ensureCosmeticBoard() {
        if (cosmeticBoard != null || scoreboardUnsupported) {
            return;
        }
        try {
            cosmeticBoard = Bukkit.getScoreboardManager().getNewScoreboard();
        } catch (UnsupportedOperationException ex) {
            scoreboardUnsupported = true;
        }
    }

    private void runNametagTask(Player player, Runnable task){if(player==null||!player.isOnline())return;player.getScheduler().run(plugin,t->task.run(),null);}

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

        String rankShort = "";
        if (plugin.getRankManager() != null) {
            var rankData = plugin.getRankManager().getPlayerRankData(uuid);
            if (rankData != null) {
                String prefix = rankData.getPrefix(); if (prefix != null) { rankShort = LegacyComponentSerializer.legacyAmpersand().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix)); if (rankShort.length() > 10) rankShort = rankShort.substring(0, 10); }
            }
        }

        final String finalPrefix = (rankShort + " " + tagText).trim();
        final String finalSuffix = "";

        runNametagTask(player, () -> applyNametagSync(player, finalPrefix, finalSuffix));
    }

    private void applyNametagSync(Player player, String finalPrefix, String finalSuffix) {
        ensureCosmeticBoard();
        if (cosmeticBoard == null) {
            applyNametagDisplayNameFallback(player, finalPrefix);
            return;
        }

        UUID uuid = player.getUniqueId();
        String teamName = "tag_" + uuid.toString().substring(0, 10).replace("-", "");
        if (teamName.length() > MAX_TEAM_NAME) {
            teamName = teamName.substring(0, MAX_TEAM_NAME);
        }

        Team team = cosmeticBoard.getTeam(teamName);
        if (team == null) {
            team = cosmeticBoard.registerNewTeam(teamName);
        }

        team.prefix(LegacyComponentSerializer.legacyAmpersand().deserialize(finalPrefix + " ")); team.suffix(LegacyComponentSerializer.legacyAmpersand().deserialize(finalSuffix));

        if (!team.hasEntry(player.getName())) {
            for (Team t : cosmeticBoard.getTeams()) {
                if (t.hasEntry(player.getName())) t.removeEntry(player.getName());
            }
            team.addEntry(player.getName());
        }

        player.setScoreboard(cosmeticBoard);
        playerTeams.put(uuid, teamName);
    }

    private void applyNametagDisplayNameFallback(Player player, String text) {
        if (text == null || text.isBlank()) {
            player.customName(null);
            player.setCustomNameVisible(false);
            return;
        }
        player.customName(LegacyComponentSerializer.legacySection().deserialize(text));
        player.setCustomNameVisible(true);
    }

    public void removeNametag(Player player) {
        if (player == null) return;

        runNametagTask(player, () -> removeNametagSync(player));
    }

    private void removeNametagSync(Player player) {
        String teamName = playerTeams.remove(player.getUniqueId());
        if (teamName != null && cosmeticBoard != null) {
            Team team = cosmeticBoard.getTeam(teamName);
            if (team != null) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        }

        if (cosmeticBoard != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        } else {
            player.customName(null);
            player.setCustomNameVisible(false);
        }
    }

    public void onPlayerJoin(Player player){if(player==null||!player.isOnline())return;player.getScheduler().runDelayed(plugin,t->applyNametag(player),null,10L);}
    public void onPlayerQuit(Player player){removeNametag(player);}
    public void refreshNametag(Player player){removeNametag(player);if(player!=null&&player.isOnline())player.getScheduler().runDelayed(plugin,t->applyNametag(player),null,2L);}

    public boolean toggleNametagVisibility(Player player) {
        UUID uuid = player.getUniqueId();
        boolean nowDisabled = nametagDisabled.contains(uuid);

        if (nowDisabled) {
            nametagDisabled.remove(uuid);
            applyNametag(player);
            return true;
        } else {
            nametagDisabled.add(uuid);
            removeNametag(player);
            return false;
        }
    }

    public boolean isNametagVisible(UUID uuid) {
        return !nametagDisabled.contains(uuid);
    }
}