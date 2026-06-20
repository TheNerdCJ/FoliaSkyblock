package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AFK Manager - Manual /afk toggle + auto AFK after 15 minutes of no movement.
 * Updates tab list automatically when AFK state changes.
 * Uses Folia-safe ThreadSafety for repeating checks and main thread updates.
 */
public class AFKManager {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    private static final long AFK_TIMEOUT_MS = 15 * 60 * 1000L; // 15 minutes

    public AFKManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Check for auto AFK every 30 seconds on main thread (Folia safe)
        plugin.getThreadSafety().runRepeatingOnMainThread(this::checkAutoAFK, 20L * 30, 20L * 30);
    }

    /**
     * Called on move (and potentially other activity) to reset timer and exit AFK if needed.
     */
    public void updateActivity(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        lastActivity.put(uuid, now);

        if (isAFK(uuid)) {
            setAFK(player, false);
        }
    }

    public boolean isAFK(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    public boolean isAFK(Player player) {
        return player != null && isAFK(player.getUniqueId());
    }

    /**
     * Toggle AFK for manual /afk command.
     */
    public void toggleAFK(Player player) {
        if (player == null) return;
        boolean newState = !isAFK(player.getUniqueId());
        setAFK(player, newState);
    }

    /**
     * Set AFK state, notify, and refresh tab list.
     */
    public void setAFK(Player player, boolean afk) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        boolean wasAFK = afkPlayers.contains(uuid);

        if (afk) {
            afkPlayers.add(uuid);
            if (!wasAFK) {
                player.sendMessage("§7You are now §eAFK§7.");
            }
        } else {
            afkPlayers.remove(uuid);
            if (wasAFK) {
                player.sendMessage("§aYou are no longer AFK.");
            }
        }

        // Update tab menu (next to name)
        if (plugin.getIslandWorthManager() != null) {
            plugin.getIslandWorthManager().updatePlayerTabList(player);
        }
    }

    private void checkAutoAFK() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            UUID uuid = player.getUniqueId();
            Long last = lastActivity.get(uuid);
            if (last == null) {
                lastActivity.put(uuid, now);
                continue;
            }

            if (!isAFK(uuid) && (now - last > AFK_TIMEOUT_MS)) {
                setAFK(player, true);
                player.sendMessage("§7You have been automatically set to §eAFK §7due to 15 minutes of inactivity.");
            }
        }

        // Cleanup old entries occasionally
        if (lastActivity.size() > 1000) {
            lastActivity.entrySet().removeIf(e -> {
                Player p = Bukkit.getPlayer(e.getKey());
                return p == null || !p.isOnline();
            });
        }
    }

    public void onPlayerJoin(Player player) {
        if (player == null) return;
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        // ensure not afk on join
        afkPlayers.remove(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        if (player == null) return;
        lastActivity.remove(player.getUniqueId());
        afkPlayers.remove(player.getUniqueId());
    }

    /**
     * Used by tab list to insert indicator next to name.
     */
    public String getAFKIndicator() {
        return " §7[AFK]";
    }
}
