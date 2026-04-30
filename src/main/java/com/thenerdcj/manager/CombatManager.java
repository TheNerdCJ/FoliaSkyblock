package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Long> combatEndTime = new HashMap<>(); // Player UUID -> Time when combat ends

    private final long COMBAT_DURATION_MS = 5000; // 5 seconds

    public CombatManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Tags a player as in combat
     */
    public void tagCombat(Player player) {
        long endTime = System.currentTimeMillis() + COMBAT_DURATION_MS;
        combatEndTime.put(player.getUniqueId(), endTime);
    }

    /**
     * Checks if player is currently in combat
     */
    public boolean isInCombat(Player player) {
        Long endTime = combatEndTime.get(player.getUniqueId());
        if (endTime == null) return false;
        return System.currentTimeMillis() < endTime;
    }

    /**
     * Returns remaining combat time in seconds (for messages)
     */
    public int getRemainingCombatSeconds(Player player) {
        Long endTime = combatEndTime.get(player.getUniqueId());
        if (endTime == null) return 0;
        long remaining = endTime - System.currentTimeMillis();
        return (int) Math.ceil(remaining / 1000.0);
    }

    /**
     * Called when a player damages or is damaged
     */
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager) {
            tagCombat(damager);
        }
        if (event.getEntity() instanceof Player victim) {
            tagCombat(victim);
        }
    }

    /**
     * Removes combat tag when player quits (prevents combat logging)
     */
    public void onPlayerQuit(Player player) {
        combatEndTime.remove(player.getUniqueId());
    }
}