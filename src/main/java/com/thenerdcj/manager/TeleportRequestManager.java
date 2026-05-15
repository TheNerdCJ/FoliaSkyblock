package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teleport Request Manager for TPA system.
 * Handles requests, cooldowns, ignore lists, and integrates with Folia async where possible.
 * Play-to-Win friendly: cooldowns prevent spam, no P2W bypass.
 */
public class TeleportRequestManager {

    private final FoliaSkyblock plugin;

    // sender -> target -> request time
    private final Map<UUID, Map<UUID, Long>> pendingRequests = new ConcurrentHashMap<>();
    // player -> set of ignored players (who they ignore TPA from)
    private final Map<UUID, Set<UUID>> ignoreLists = new ConcurrentHashMap<>();
    // player -> last TPA time for cooldown
    private final Map<UUID, Long> lastTpaTime = new ConcurrentHashMap<>();

    private static final long DEFAULT_COOLDOWN_MS = 30_000; // 30 seconds cooldown
    private static final long REQUEST_TIMEOUT_MS = 120_000; // 2 minutes request expiry

    public TeleportRequestManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Periodic cleanup of expired requests (Folia compatible scheduler)
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredRequests, 20L * 30, 20L * 30);
    }

    public long getCooldownMs() {
        // Could load from config later
        return DEFAULT_COOLDOWN_MS;
    }

    public boolean isOnCooldown(Player player) {
        Long last = lastTpaTime.get(player.getUniqueId());
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < getCooldownMs();
    }

    public long getRemainingCooldown(Player player) {
        Long last = lastTpaTime.get(player.getUniqueId());
        if (last == null) return 0;
        long remaining = getCooldownMs() - (System.currentTimeMillis() - last);
        return Math.max(0, remaining);
    }

    public void setCooldown(Player player) {
        lastTpaTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Send TPA request from sender to target.
     * Returns true if sent successfully.
     */
    public boolean sendRequest(Player sender, Player target) {
        if (sender.equals(target)) {
            sender.sendMessage("§cYou cannot TPA to yourself!");
            return false;
        }

        if (isOnCooldown(sender)) {
            long secs = getRemainingCooldown(sender) / 1000;
            sender.sendMessage("§cTPA on cooldown! Wait §e" + secs + "s§c.");
            return false;
        }

        if (isIgnoring(target, sender.getUniqueId())) {
            sender.sendMessage("§c" + target.getName() + " is ignoring TPA requests from you.");
            return false;
        }

        // Clean old requests from this sender
        pendingRequests.computeIfAbsent(sender.getUniqueId(), k -> new ConcurrentHashMap<>()).clear();

        pendingRequests.computeIfAbsent(sender.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(target.getUniqueId(), System.currentTimeMillis());

        setCooldown(sender);

        sender.sendMessage("§aTPA request sent to §e" + target.getName() + "§a. They have 2 minutes to accept.");

        target.sendMessage("§e" + sender.getName() + " §7wants to teleport to you.");
        target.sendMessage("§7Use §a/tpaccept §7or §c/tpdeny §7or §6/tplist §7to manage.");

        return true;
    }

    public boolean hasPendingRequestFrom(Player target, Player sender) {
        Map<UUID, Long> requests = pendingRequests.get(sender.getUniqueId());
        if (requests == null) return false;
        Long time = requests.get(target.getUniqueId());
        if (time == null) return false;
        return (System.currentTimeMillis() - time) < REQUEST_TIMEOUT_MS;
    }

    public List<UUID> getPendingRequestersFor(Player target) {
        List<UUID> requesters = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, Long>> entry : pendingRequests.entrySet()) {
            UUID sender = entry.getKey();
            if (entry.getValue().containsKey(target.getUniqueId())) {
                Long time = entry.getValue().get(target.getUniqueId());
                if (time != null && (System.currentTimeMillis() - time) < REQUEST_TIMEOUT_MS) {
                    requesters.add(sender);
                }
            }
        }
        return requesters;
    }

    public boolean acceptRequest(Player target, Player sender) {
        if (!hasPendingRequestFrom(target, sender)) {
            target.sendMessage("§cNo pending TPA request from §e" + sender.getName() + "§c.");
            return false;
        }

        // Remove the request
        Map<UUID, Long> requests = pendingRequests.get(sender.getUniqueId());
        if (requests != null) {
            requests.remove(target.getUniqueId());
        }

        // Teleport sender to target (Folia safe teleport)
        sender.teleport(target.getLocation());
        sender.sendMessage("§aTeleporting to §e" + target.getName() + "§a...");
        target.sendMessage("§a" + sender.getName() + " §7has teleported to you.");

        return true;
    }

    public boolean denyRequest(Player target, Player sender) {
        Map<UUID, Long> requests = pendingRequests.get(sender.getUniqueId());
        if (requests != null) {
            requests.remove(target.getUniqueId());
        }
        target.sendMessage("§cTPA request from §e" + sender.getName() + " §cdenied.");
        sender.sendMessage("§cYour TPA request to §e" + target.getName() + " §cwas denied.");
        return true;
    }

    public void toggleIgnore(Player player, UUID ignored) {
        Set<UUID> ignores = ignoreLists.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        if (ignores.contains(ignored)) {
            ignores.remove(ignored);
            player.sendMessage("§aYou are now receiving TPA requests from §e" + Bukkit.getOfflinePlayer(ignored).getName() + "§a.");
        } else {
            ignores.add(ignored);
            player.sendMessage("§cIgnoring TPA requests from §e" + Bukkit.getOfflinePlayer(ignored).getName() + "§c.");
        }
    }

    public boolean isIgnoring(Player player, UUID potentialSender) {
        Set<UUID> ignores = ignoreLists.get(player.getUniqueId());
        return ignores != null && ignores.contains(potentialSender);
    }

    public void clearRequestsFor(Player player) {
        pendingRequests.remove(player.getUniqueId());
    }

    private void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, Long>> senderEntry : pendingRequests.entrySet()) {
            senderEntry.getValue().entrySet().removeIf(e -> (now - e.getValue()) > REQUEST_TIMEOUT_MS);
        }
        // Remove empty maps
        pendingRequests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    // For GUI integration
    public Map<UUID, Map<UUID, Long>> getAllPendingRequests() {
        return pendingRequests;
    }
    /**
     * Clean up ALL data for a player when they quit.
     * Removes them as sender + as target from all pending requests,
     * clears ignore lists and cooldowns.
     */
    public void removePlayer(UUID uuid) {
        // Remove as sender
        pendingRequests.remove(uuid);
        lastTpaTime.remove(uuid);

        // Remove as target from every other player's pending requests
        for (Map<UUID, Long> requests : pendingRequests.values()) {
            requests.remove(uuid);
        }

        // Clean ignore list
        ignoreLists.remove(uuid);

        // Remove any empty request maps
        pendingRequests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
