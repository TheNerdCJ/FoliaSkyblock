package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Cosmetic Join/Leave Messages.
 * Follows the same patterns as DeathMessageManager and other cosmetics.
 * Purely cosmetic text replacements on join/quit events.
 */
public class JoinLeaveMessageManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<JoinLeaveMessageCosmetic>> ownedMessages = new ConcurrentHashMap<>();
    private final Map<UUID, JoinLeaveMessageCosmetic> activeJoinLeaveMessage = new ConcurrentHashMap<>();

    private static final int[] JOIN_LEAVE_MILESTONES = {3, 5, 8, 12};

    public JoinLeaveMessageManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<JoinLeaveMessageCosmetic> getOwnedMessages(UUID uuid) {
        return ownedMessages.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public JoinLeaveMessageCosmetic getActiveJoinLeaveMessage(UUID uuid) {
        return activeJoinLeaveMessage.getOrDefault(uuid, JoinLeaveMessageCosmetic.NONE);
    }

    public void setActiveJoinLeaveMessage(Player player, JoinLeaveMessageCosmetic message) {
        if (message == null) message = JoinLeaveMessageCosmetic.NONE;

        if (!message.isNone() && !hasMessage(player.getUniqueId(), message)) {
            player.sendMessage("§cYou have not unlocked this join/leave message.");
            return;
        }

        activeJoinLeaveMessage.put(player.getUniqueId(), message);
        // Persist the active choice
        plugin.getDatabaseManager().saveActiveJoinLeaveMessage(player.getUniqueId(), message.name());
        player.sendMessage("§aJoin/Leave Message activated: " + (message.isNone() ? "None" : message.getDisplayName()));
    }

    public void unlockMessage(UUID uuid, JoinLeaveMessageCosmetic message) {
        if (message == null || message.isNone()) return;

        Set<JoinLeaveMessageCosmetic> messages = getOwnedMessages(uuid);
        boolean wasNew = messages.add(message);

        if (wasNew) {
            int xp = message.getRarity().getXpReward() / 2;

            if (plugin.getConfig().getBoolean("join-leave-messages.collection-xp.enabled", true)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getIslandManager().addIslandXp(player, xp);
                    player.sendMessage("§a§lJoin/Leave Message Collection §7» Unlocked " + message.getRarity().getColorCode() + message.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                    int count = messages.size();
                    for (int m : JOIN_LEAVE_MILESTONES) {
                        if (count == m) {
                            player.sendMessage("§6§l★ Socialite Milestone! ★ §e" + m + " unique join/leave messages unlocked!");
                            break;
                        }
                    }
                }
            }

            plugin.getDatabaseManager().savePlayerJoinLeaveMessages(uuid, getMessageIdSet(uuid));
        }
    }

    public boolean hasMessage(UUID uuid, JoinLeaveMessageCosmetic message) {
        if (message == null || message.isNone()) return true;
        return getOwnedMessages(uuid).contains(message);
    }

    public int getMessageCollectionCount(UUID uuid) {
        return getOwnedMessages(uuid).size();
    }

    /**
     * Formats the message using the player's rich display name.
     */
    public String formatMessage(String template, Player player) {
        if (template == null || template.isEmpty()) return null;
        String display = getRichDisplayName(player);
        return template.replace("%player%", display);
    }

    private String getRichDisplayName(Player player) {
        if (plugin.getPlayerTagManager() != null) {
            return plugin.getPlayerTagManager().getComposedDisplayName(
                    player.getUniqueId(), player.getName());
        }
        if (plugin.getRankManager() != null) {
            return plugin.getRankManager().getPlayerDisplayName(player.getUniqueId(), player.getName());
        }
        return player.getName();
    }

    private Set<String> getMessageIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (JoinLeaveMessageCosmetic m : getOwnedMessages(uuid)) {
            ids.add(m.name());
        }
        return ids;
    }

    public void onPlayerQuit(Player player) {
        // optional cleanup
    }

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerJoinLeaveMessages(uuid);
        Set<JoinLeaveMessageCosmetic> messages = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                JoinLeaveMessageCosmetic m = JoinLeaveMessageCosmetic.valueOf(id);
                if (!m.isNone()) messages.add(m);
            } catch (Exception ignored) {}
        }
        ownedMessages.put(uuid, messages);

        // Load the persisted active choice
        String activeId = plugin.getDatabaseManager().loadActiveJoinLeaveMessage(uuid);
        if (activeId != null && !activeId.isEmpty() && !activeId.equals("NONE")) {
            try {
                JoinLeaveMessageCosmetic m = JoinLeaveMessageCosmetic.valueOf(activeId);
                if (!m.isNone()) {
                    activeJoinLeaveMessage.put(uuid, m);
                }
            } catch (Exception ignored) {}
        }
    }

    public void savePlayer(UUID uuid) {
        Set<String> ids = getMessageIdSet(uuid);
        plugin.getDatabaseManager().savePlayerJoinLeaveMessages(uuid, ids);
        // Also persist the currently active choice
        JoinLeaveMessageCosmetic active = activeJoinLeaveMessage.get(uuid);
        if (active != null) {
            plugin.getDatabaseManager().saveActiveJoinLeaveMessage(uuid, active.name());
        }
    }
}