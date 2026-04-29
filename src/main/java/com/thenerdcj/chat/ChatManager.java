package com.thenerdcj.chat;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatManager {

    private final FoliaSkyblock plugin;
    private final RankManager rankManager;

    // Reply system: Receiver UUID → Sender UUID
    private final Map<UUID, UUID> lastReplyTarget = new ConcurrentHashMap<>();

    // Spy system
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

    // Muted players (in-memory cache loaded from database)
    private final Set<UUID> mutedPlayers = ConcurrentHashMap.newKeySet();

    public ChatManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();

        loadMutedPlayersFromDatabase();
    }

    private void loadMutedPlayersFromDatabase() {
        plugin.getDatabaseManager().loadMutedPlayers().thenAccept(mutedList -> {
            mutedPlayers.clear();
            mutedPlayers.addAll(mutedList);
            plugin.getLogger().info("§aLoaded " + mutedPlayers.size() + " muted players from database.");
        });
    }

    /**
     * Send a private message between two players
     */
    public void sendPrivateMessage(Player sender, Player target, String message) {
        if (sender == null || target == null) return;

        // Check if sender is muted
        if (mutedPlayers.contains(sender.getUniqueId())) {
            sender.sendMessage("§cYou are muted and cannot send messages.");
            return;
        }

        String senderRank = rankManager.getRankPrefix(sender);
        String targetRank = rankManager.getRankPrefix(target);

        String senderDimTag = getDimensionTag(sender);
        String targetDimTag = getDimensionTag(target);

        // Message to receiver
        target.sendMessage("§7[From " + senderRank + sender.getName() + senderDimTag + "§7] §f" + message);

        // Message to sender
        sender.sendMessage("§7[To " + targetRank + target.getName() + targetDimTag + "§7] §f" + message);

        // Store for /reply
        lastReplyTarget.put(target.getUniqueId(), sender.getUniqueId());
        lastReplyTarget.put(sender.getUniqueId(), target.getUniqueId());

        // Spy system
        for (UUID spyUuid : spies) {
            Player spy = Bukkit.getPlayer(spyUuid);
            if (spy != null && spy != sender && spy != target) {
                spy.sendMessage("§8[SPY] " + senderRank + sender.getName() + senderDimTag +
                               " §7→ " + targetRank + target.getName() + targetDimTag + "§7: §f" + message);
            }
        }
    }

    /**
     * Handle /reply command
     */
    public void reply(Player player, String message) {
        UUID targetUuid = lastReplyTarget.get(player.getUniqueId());
        if (targetUuid == null) {
            player.sendMessage("§cYou have no one to reply to.");
            return;
        }

        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cThat player is no longer online.");
            lastReplyTarget.remove(player.getUniqueId());
            return;
        }

        sendPrivateMessage(player, target, message);
    }

    public boolean isMuted(Player player) {
        return mutedPlayers.contains(player.getUniqueId());
    }

    /**
     * Mute or unmute a player (with duration support)
     */
    public void setMuted(Player player, boolean muted, UUID mutedBy, String reason, long durationSeconds) {
        if (muted) {
            mutedPlayers.add(player.getUniqueId());
            String durationText = (durationSeconds > 0) ? " for " + formatDuration(durationSeconds) : " permanently";
            player.sendMessage("§cYou have been muted" + durationText + ".");
        } else {
            mutedPlayers.remove(player.getUniqueId());
            player.sendMessage("§aYou have been unmuted.");
        }

        // Save to database
        plugin.getDatabaseManager().setMuted(player.getUniqueId(), muted, mutedBy, reason, durationSeconds);
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " seconds";
        if (seconds < 3600) return (seconds / 60) + " minutes";
        if (seconds < 86400) return (seconds / 3600) + " hours";
        return (seconds / 86400) + " days";
    }

    private String getDimensionTag(Player player) {
        Environment env = player.getWorld().getEnvironment();
        return switch (env) {
            case NETHER -> " §7[§cNether§7]";
            case THE_END -> " §7[§5End§7]";
            default -> "";
        };
    }

    public void toggleSpy(Player player) {
        if (spies.contains(player.getUniqueId())) {
            spies.remove(player.getUniqueId());
            player.sendMessage("§cYou are no longer spying on private messages.");
        } else {
            spies.add(player.getUniqueId());
            player.sendMessage("§aYou are now spying on private messages.");
        }
    }
}