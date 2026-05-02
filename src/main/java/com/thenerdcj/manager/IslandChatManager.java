package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island Chat Manager - Per-island chat channels
 */
public class IslandChatManager {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Boolean> islandChatMode = new ConcurrentHashMap<>();

    public IslandChatManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

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

    public boolean isInIslandChat(UUID uuid) {
        return islandChatMode.getOrDefault(uuid, false);
    }

    public void sendIslandMessage(Player sender, String message) {
        Island island = plugin.getIslandManager().getIsland(sender.getUniqueId(), sender.getWorld().getEnvironment());

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

    public void handleChat(Player player, String message) {
        if (isInIslandChat(player.getUniqueId())) {
            sendIslandMessage(player, message);
        } else {
            // Let the normal chat system handle it
            plugin.getChatManager().broadcastMessage(player, message);
        }
    }
}