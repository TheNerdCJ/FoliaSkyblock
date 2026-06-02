package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.DeathEffect;
import com.thenerdcj.cosmetic.DeathEffectManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Triggers cosmetic Death/Kill Effects.
 * Folia-safe: delegates spawning to DeathEffectManager which uses ThreadSafety.
 */
public class DeathEffectListener implements Listener {

    private final FoliaSkyblock plugin;

    public DeathEffectListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;

        Player killer = event.getEntity().getKiller();
        DeathEffectManager manager = plugin.getDeathEffectManager();
        if (manager == null) return;

        // Trigger kill effect for the killer (on killing a mob/player)
        DeathEffect killEffect = manager.getActiveDeathEffect(killer.getUniqueId());
        if (!killEffect.isNone()) {
            manager.triggerEffect(event.getEntity().getLocation(), killEffect, killer);
        }

        // If the dead entity is a player, also trigger their death effect
        if (event.getEntity() instanceof Player victim) {
            DeathEffect deathEffect = manager.getActiveDeathEffect(victim.getUniqueId());
            if (!deathEffect.isNone()) {
                manager.triggerEffect(victim.getLocation(), deathEffect, victim);
            }

            // NEW: Trigger cosmetic death message (text) for victim or killer
            if (plugin.getDeathMessageManager() != null) {
                plugin.getDeathMessageManager().triggerMessage(killer, victim, null);
            }
        }
    }
}