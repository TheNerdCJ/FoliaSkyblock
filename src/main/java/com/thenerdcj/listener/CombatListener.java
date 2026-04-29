package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.combat.CombatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Combat Listener - Prevents combat logging and blocks dangerous actions during combat
 */
public class CombatListener implements Listener {

    private final FoliaSkyblock plugin;
    private final CombatManager combatManager;

    public CombatListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.combatManager = new CombatManager(plugin);
    }

    /**
     * Tag players when they deal or receive damage
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        // Player damages another player
        if (e.getDamager() instanceof Player damager) {
            combatManager.tagCombat(damager);
        }

        // Player gets damaged by another player
        if (e.getEntity() instanceof Player victim) {
            combatManager.tagCombat(victim);
        }
    }

    /**
     * Prevent combat logging (player quitting during combat)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();

        if (combatManager.isInCombat(player)) {
            // Kill the player and drop items (anti-combat log)
            player.setHealth(0);
            plugin.getLogger().info("§c" + player.getName() + " combat logged and was killed.");
        }

        combatManager.onPlayerQuit(player);
    }
}