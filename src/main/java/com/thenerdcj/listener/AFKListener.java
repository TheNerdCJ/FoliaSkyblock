package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.AFKManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listens for player movement to reset AFK timer.
 * Folia-safe (event is called appropriately).
 */
public class AFKListener implements Listener {

    private final FoliaSkyblock plugin;
    private final AFKManager afkManager;

    public AFKListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.afkManager = plugin.getAfkManager();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (afkManager == null) return;

        Player player = event.getPlayer();
        // Only reset if actually moved (ignore head look)
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
            event.getFrom().getBlockY() != event.getTo().getBlockY() ||
            event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            afkManager.updateActivity(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (afkManager != null) {
            afkManager.onPlayerJoin(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (afkManager != null) {
            afkManager.onPlayerQuit(event.getPlayer());
        }
    }
}
