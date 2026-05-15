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

    // Original features
    private final Set<UUID> muted = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Boolean> islandChatMode = new ConcurrentHashMap<>();

    // Expanded Private Messaging features
    private final Map<UUID, UUID> lastMessaged = new ConcurrentHashMap<>(); // Player -> Last person they messaged
    private final Set<UUID> staffSpyEnabled = Collections.newSetFromMap(new ConcurrentHashMap<>()); // Staff with spy toggled on

    public ChatManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    // ====================== ORIGINAL METHODS ======================

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

    // ====================== NEW EXPANDED PRIVATE MESSAGING ======================

    /**
     * Send a private message from sender to target
     */
    public void sendPrivateMessage(Player sender, Player target, String message) {
        if (isMuted(sender.getUniqueId())) {
            sender.sendMessage("§cYou are muted and cannot send messages.");
            return;
        }

        if (!target.isOnline()) {
            sender.sendMessage("§cThat player is no longer online.");
            return;
        }

        // Format messages
        String senderMsg = "§7[§eTo §f" + target.getName() + "§7] §f" + message;
        String targetMsg = "§7[§eFrom §f" + sender.getName() + "§7] §f" + message;

        sender.sendMessage(senderMsg);
        target.sendMessage(targetMsg);

        // Update last messaged for /r
        lastMessaged.put(sender.getUniqueId(), target.getUniqueId());
        lastMessaged.put(target.getUniqueId(), sender.getUniqueId());

        // Staff Spy
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staffSpyEnabled.contains(staff.getUniqueId())
                    && staff.hasPermission("folia.staff.spy")
                    && !staff.getUniqueId().equals(sender.getUniqueId())
                    && !staff.getUniqueId().equals(target.getUniqueId())) {

                String spyMsg = "§8[§7Spy§8] §e" + sender.getName() + " §7→ §f" + target.getName() + "§7: §f" + message;
                staff.sendMessage(spyMsg);
            }
        }
    }

    /**
     * Toggle staff spy mode for moderators
     */
    public void toggleStaffSpy(Player player) {
        UUID uuid = player.getUniqueId();
        if (staffSpyEnabled.contains(uuid)) {
            staffSpyEnabled.remove(uuid);
            player.sendMessage("§cStaff chat spy disabled.");
        } else {
            if (!player.hasPermission("folia.staff.spy")) {
                player.sendMessage("§cYou don't have permission to use staff spy.");
                return;
            }
            staffSpyEnabled.add(uuid);
            player.sendMessage("§aStaff chat spy enabled. You will now see all private messages.");
        }
    }

    /**
     * Get the last player this person messaged (for /r command)
     */
    public UUID getLastMessaged(UUID uuid) {
        return lastMessaged.get(uuid);
    }

    /**
     * Remove player from tracking when they quit
     */
    public void removePlayer(UUID uuid) {
        muted.remove(uuid);
        islandChatMode.remove(uuid);
        lastMessaged.remove(uuid);
        staffSpyEnabled.remove(uuid);
    }

    // ====================== CLEANUP ======================

    /**
     * Called when a player quits (add this to your PlayerQuitListener)
     */
    public void onPlayerQuit(Player player) {
        removePlayer(player.getUniqueId());
    }
}