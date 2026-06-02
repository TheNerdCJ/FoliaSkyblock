package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Cosmetic Kill/Death Messages.
 * Follows the same patterns as DeathEffectManager.
 * Purely cosmetic text replacements on death/kill events.
 */
public class DeathMessageManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<DeathMessageCosmetic>> ownedMessages = new ConcurrentHashMap<>();
    private final Map<UUID, DeathMessageCosmetic> activeDeathMessage = new ConcurrentHashMap<>();

    private static final int[] DEATH_MESSAGE_MILESTONES = {3, 5, 8, 12};

    public DeathMessageManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<DeathMessageCosmetic> getOwnedMessages(UUID uuid) {
        return ownedMessages.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public DeathMessageCosmetic getActiveDeathMessage(UUID uuid) {
        return activeDeathMessage.getOrDefault(uuid, DeathMessageCosmetic.NONE);
    }

    public void setActiveDeathMessage(Player player, DeathMessageCosmetic message) {
        if (message == null) message = DeathMessageCosmetic.NONE;

        if (!message.isNone() && !hasMessage(player.getUniqueId(), message)) {
            player.sendMessage("§cYou have not unlocked this death message.");
            return;
        }

        activeDeathMessage.put(player.getUniqueId(), message);
        player.sendMessage("§aDeath Message activated: " + (message.isNone() ? "None" : message.getDisplayName()));
    }

    public void unlockMessage(UUID uuid, DeathMessageCosmetic message) {
        if (message == null || message.isNone()) return;

        Set<DeathMessageCosmetic> messages = getOwnedMessages(uuid);
        boolean wasNew = messages.add(message);

        if (wasNew) {
            int xp = message.getRarity().getXpReward() / 2;

            if (plugin.getConfig().getBoolean("death-messages.collection-xp.enabled", true)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getIslandManager().addIslandXp(player, xp);
                    player.sendMessage("§a§lDeath Message Collection §7» Unlocked " + message.getRarity().getColorCode() + message.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                    int count = messages.size();
                    for (int m : DEATH_MESSAGE_MILESTONES) {
                        if (count == m) {
                            player.sendMessage("§6§l★ Chronicler Milestone! ★ §e" + m + " unique death messages unlocked!");
                            break;
                        }
                    }
                }
            }

            plugin.getDatabaseManager().savePlayerDeathMessages(uuid, getMessageIdSet(uuid));
        }
    }

    public boolean hasMessage(UUID uuid, DeathMessageCosmetic message) {
        if (message == null || message.isNone()) return true;
        return getOwnedMessages(uuid).contains(message);
    }

    public int getMessageCollectionCount(UUID uuid) {
        return getOwnedMessages(uuid).size();
    }

    /**
     * Formats and broadcasts the message if active for killer or victim.
     * Folia-safe: uses main thread for chat broadcast.
     */
    public void triggerMessage(Player killer, Player victim, String defaultMessage) {
        if (killer == null && victim == null) return;

        // Check for killer's message (on kill)
        if (killer != null) {
            DeathMessageCosmetic killMsg = getActiveDeathMessage(killer.getUniqueId());
            if (!killMsg.isNone()) {
                String formatted = formatMessage(killMsg.getDescription(), killer, victim);
                broadcastMessage(formatted);
                return; // killer's takes precedence?
            }
        }

        // Check for victim's message (on death)
        if (victim != null) {
            DeathMessageCosmetic deathMsg = getActiveDeathMessage(victim.getUniqueId());
            if (!deathMsg.isNone()) {
                String formatted = formatMessage(deathMsg.getDescription(), killer, victim);
                broadcastMessage(formatted);
                return;
            }
        }
    }

    private String formatMessage(String template, Player killer, Player victim) {
        String msg = template;
        if (killer != null) {
            msg = msg.replace("%killer%", killer.getName());
        } else {
            msg = msg.replace("%killer%", "the environment");
        }
        if (victim != null) {
            msg = msg.replace("%victim%", victim.getName());
        }
        return msg;
    }

    private void broadcastMessage(String message) {
        threadSafety.runOnMainThread(() -> {
            Bukkit.broadcastMessage("§c" + message); // or use adventure if project uses it
        });
    }

    // ==================== PERSISTENCE ====================

    private Set<String> getMessageIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (DeathMessageCosmetic m : getOwnedMessages(uuid)) {
            ids.add(m.name());
        }
        return ids;
    }

    public void onPlayerQuit(Player player) {
        // optional cleanup
    }

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerDeathMessages(uuid);
        Set<DeathMessageCosmetic> messages = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                DeathMessageCosmetic m = DeathMessageCosmetic.valueOf(id);
                if (!m.isNone()) messages.add(m);
            } catch (Exception ignored) {}
        }
        ownedMessages.put(uuid, messages);
    }

    public void savePlayer(UUID uuid) {
        Set<String> ids = getMessageIdSet(uuid);
        plugin.getDatabaseManager().savePlayerDeathMessages(uuid, ids);
    }
}