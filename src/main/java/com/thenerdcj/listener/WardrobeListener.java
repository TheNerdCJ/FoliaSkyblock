package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles loading and saving of wardrobe data on player join/quit.
 * This completes the persistence layer for the Wardrobe system.
 */
public class WardrobeListener implements Listener {

    private final FoliaSkyblock plugin;

    public WardrobeListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var uuid = event.getPlayer().getUniqueId();

        // Load wardrobe data asynchronously into the manager's cache
        plugin.getThreadSafety().runAsync(() -> {
            plugin.getWardrobeManager().loadPlayer(uuid);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();

        // Optional: force any pending saves (current implementation saves on every change)
        plugin.getWardrobeManager().savePlayer(uuid);
    }
}