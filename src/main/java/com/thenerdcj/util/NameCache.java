package com.thenerdcj.util;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized name caching for players.
 * 
 * Benefits:
 * - Avoids repeated expensive Bukkit.getOfflinePlayer calls.
 * - Keeps names fresh on join.
 * - Can be used by holograms, commands, GUIs, etc.
 * 
 * This is especially useful for dynamic holograms showing top islands/players.
 */
public class NameCache implements Listener {

    private final ConcurrentHashMap<UUID, String> cache = new ConcurrentHashMap<>();

    private final FoliaSkyblock plugin;

    public NameCache(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Gets the player name, using cache if available.
     * Falls back to OfflinePlayer lookup (may be slow on first call).
     */
    public String getName(UUID uuid) {
        if (uuid == null) return "Unknown";

        String cached = cache.get(uuid);
        if (cached != null) return cached;

        // Fallback lookup (synchronous - try to avoid in hot paths)
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName();
            if (name != null) {
                cache.put(uuid, name);
                return name;
            }
        } catch (Exception ignored) {}

        // Last resort
        String fallback = uuid.toString().substring(0, 8);
        cache.put(uuid, fallback);
        return fallback;
    }

    /**
     * Asynchronously ensures the name is cached.
     * Useful for hologram preparation.
     */
    public void ensureCachedAsync(UUID uuid) {
        if (uuid == null || cache.containsKey(uuid)) return;

        plugin.getThreadSafety().runOnMainThread(() -> {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                if (op.getName() != null) {
                    cache.put(uuid, op.getName());
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Updates the cache with the player's current name (called on join).
     */
    public void updateName(Player player) {
        if (player != null && player.getName() != null) {
            cache.put(player.getUniqueId(), player.getName());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateName(event.getPlayer());
    }

    /**
     * Manually preload a name (useful on startup for top players).
     */
    public void preload(UUID uuid, String name) {
        if (uuid != null && name != null) {
            cache.put(uuid, name);
        }
    }

    public void clearCache() {
        cache.clear();
    }
}
