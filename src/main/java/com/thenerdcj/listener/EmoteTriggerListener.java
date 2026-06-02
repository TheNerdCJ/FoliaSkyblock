package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.EmoteCosmeticManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Triggers cosmetic emotes on events like kills, deaths, joins, respawns, levelups.
 * Uses the emote triggers API from EmoteCosmeticManager (set via /emote trigger or GUI).
 * Folia-safe: delegates to manager which uses ThreadSafety for effects.
 * Advanced triggers polish for more event depth.
 */
public class EmoteTriggerListener implements Listener {

    private final FoliaSkyblock plugin;

    public EmoteTriggerListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;

        Player killer = event.getEntity().getKiller();
        EmoteCosmeticManager manager = plugin.getEmoteCosmeticManager();
        if (manager == null) return;

        // Trigger "kill" emote for the killer (on killing a mob/player) if set and owned
        manager.triggerEmote(killer, "kill");

        // If the dead entity is a player, trigger their "death" emote if set
        if (event.getEntity() instanceof Player victim) {
            manager.triggerEmote(victim, "death");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        EmoteCosmeticManager manager = plugin.getEmoteCosmeticManager();
        if (manager == null) return;

        // Centralized "join" trigger support (more events polish). Manager onJoin demo removed to avoid double.
        // Respects per-player ownership + persisted triggers.
        manager.triggerEmote(player, "join");
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        EmoteCosmeticManager manager = plugin.getEmoteCosmeticManager();
        if (manager == null) return;

        // Advanced trigger: 'respawn' for cosmetic flair on respawn (Folia-safe via delegation)
        manager.triggerEmote(player, "respawn");
    }

    @EventHandler
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        EmoteCosmeticManager manager = plugin.getEmoteCosmeticManager();
        if (manager == null) return;

        // Advanced trigger: 'levelup' for celebratory emote on level gain
        if (event.getNewLevel() > event.getOldLevel()) {
            manager.triggerEmote(player, "levelup");
        }
    }
}