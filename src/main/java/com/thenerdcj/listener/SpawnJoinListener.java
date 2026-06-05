package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Blocks player login until the hub spawn island has finished generating.
 */
public class SpawnJoinListener implements Listener {

    private final FoliaSkyblock plugin;

    public SpawnJoinListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (plugin.getWorldManager() == null) {
            return;
        }
        if (!plugin.getWorldManager().shouldBlockJoinUntilHubSpawnReady()) {
            return;
        }
        if (plugin.getWorldManager().isHubSpawnReady()) {
            return;
        }
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                "Spawn hub is still generating. Please reconnect in a moment.");
    }
}