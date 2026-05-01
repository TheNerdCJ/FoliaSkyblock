package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class ChallengeProgressListener implements Listener {

    private final FoliaSkyblock plugin;

    public ChallengeProgressListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getChallengeManager() != null) {
            plugin.getChallengeManager().updateProgress(event.getPlayer().getUniqueId(), "BUILDING", 1);
        }
    }
}