package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final FoliaSkyblock plugin;

    public PlayerQuitListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getChatManager() != null) {
            plugin.getChatManager().onPlayerQuit(player);
        }

        if (plugin.getTeleportRequestManager() != null) {
            plugin.getTeleportRequestManager().removePlayer(uuid);
        }

        if (plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().removePlayer(uuid);
        }

        if (plugin.getRankManager() != null) {
            plugin.getRankManager().removePlayer(uuid);
        }

        if (plugin.getParticleTrailManager() != null) {
            plugin.getParticleTrailManager().onPlayerQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getIslandWorthManager() != null) {
            plugin.getIslandWorthManager().updatePlayerTabList(player);
        }

        if (plugin.getParticleTrailManager() != null) {
            plugin.getParticleTrailManager().onPlayerJoin(player);
        }
    }
}