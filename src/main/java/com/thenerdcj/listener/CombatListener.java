package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CombatListener - Fully optimized for Folia 1.21+
 * Uses ConcurrentHashMap for thread-safe combat tagging.
 */
public class CombatListener implements Listener {

    private final FoliaSkyblock plugin;

    // Thread-safe combat tag storage: UUID -> expiry time (ms)
    private final ConcurrentHashMap<UUID, Long> combatTags = new ConcurrentHashMap<>();

    // Combat tag duration in milliseconds (default 15 seconds)
    private final long combatTagDuration;

    public CombatListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.combatTagDuration = plugin.getConfig().getLong("combat.tag-duration", 15000);
    }

    /**
     * Tag a player in combat (thread-safe)
     */
    private void tagPlayer(Player player) {
        combatTags.put(player.getUniqueId(), System.currentTimeMillis() + combatTagDuration);
    }

    /**
     * Check if player is in combat
     */
    public boolean isInCombat(Player player) {
        Long expiry = combatTags.get(player.getUniqueId());
        if (expiry == null) return false;

        if (System.currentTimeMillis() > expiry) {
            combatTags.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    // ====================== EVENT HANDLERS ======================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player damager)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        // Tag both players
        tagPlayer(damager);
        tagPlayer(victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (isInCombat(player)) {
            // Use EntityScheduler for Folia-safe player killing
            if (plugin.isFolia()) {
                player.getScheduler().run(plugin, scheduledTask -> {
                    if (player.isOnline()) {
                        player.setHealth(0);
                        plugin.getLogger().info("§c" + player.getName() + " combat logged and was killed.");
                    }
                }, null);
            } else {
                // Fallback for Paper/Spigot
                plugin.getThreadSafety().runOnMainThread(() -> {
                    if (player.isOnline()) {
                        player.setHealth(0);
                        plugin.getLogger().info("§c" + player.getName() + " combat logged and was killed.");
                    }
                });
            }
        }

        // Clean up
        combatTags.remove(uuid);
    }

    /**
     * Clean up expired tags (call this periodically if needed)
     */
    public void cleanupExpiredTags() {
        long now = System.currentTimeMillis();
        combatTags.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}