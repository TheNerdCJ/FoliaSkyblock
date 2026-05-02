package com.thenerdcj.manager;
import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat Manager - Global chat with mute functionality + Island chat integration
 */
public class ChatManager {
    private final FoliaSkyblock plugin;
    private final Set<UUID> muted = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Boolean> islandChatMode = new ConcurrentHashMap<>();

    public ChatManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    public boolean isMuted(UUID uuid) {
        return muted.contains(uuid);
    }

    public void mute(UUID uuid) {
        muted.add(uuid);
    }

    public void unmute(UUID uuid) {
        muted.remove(uuid);
    }

    /**
     * Broadcast a message to all online players (global chat)
     */
    public void broadcastMessage(Player sender, String message) {
        if (isMuted(sender.getUniqueId())) {
            sender.sendMessage("§cYou are muted and cannot speak.");
            return;
        }

        String formattedMessage = "§7[Global] §e" + sender.getName() + "§7: §f" + message;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(formattedMessage);
        }
    }

    /**
     * Toggle island chat mode for a player
     */
    public void toggleIslandChat(Player player) {
        UUID uuid = player.getUniqueId();
        boolean currentMode = islandChatMode.getOrDefault(uuid, false);
        islandChatMode.put(uuid, !currentMode);

        if (!currentMode) {
            player.sendMessage("§aIsland chat enabled! All messages will go to your island members.");
        } else {
            player.sendMessage("§cIsland chat disabled. Messages will go to global chat.");
        }
    }

    /**
     * Check if player is in island chat mode
     */
    public boolean isInIslandChat(UUID uuid) {
        return islandChatMode.getOrDefault(uuid, false);
    }

    /**
     * Send a message to all island members
     */
    public void sendIslandMessage(Player sender, String message) {
        com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(
                sender.getUniqueId(), sender.getWorld().getEnvironment());

        if (island == null) {
            sender.sendMessage("§cYou don't have an island! Use §b/island create§c first.");
            return;
        }

        String formattedMessage = "§b[Island] §e" + sender.getName() + "§7: §f" + message;

        // Send to all online island members
        for (UUID memberId : island.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(formattedMessage);
            }
        }

        // Also send to sender
        sender.sendMessage(formattedMessage);
    }

    /**
     * Handle chat based on player's current mode
     */
    public void handleChat(Player player, String message) {
        if (isInIslandChat(player.getUniqueId())) {
            sendIslandMessage(player, message);
        } else {
            broadcastMessage(player, message);
        }
    }
}