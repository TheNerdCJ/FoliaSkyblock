package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.wings.ElytraWingManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Listens for elytra gliding to apply cosmetic wing effects + custom elytra items.
 * Uses Folia EntityScheduler where possible for safety.
 */
public class ElytraWingListener implements Listener {

    private final FoliaSkyblock plugin;

    public ElytraWingListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ElytraWingManager manager = plugin.getElytraWingManager();
        if (manager == null) return;

        if (event.isGliding()) {
            // Apply cosmetic elytra item if applicable
            manager.applyCosmeticElytra(player);

            // Schedule repeating wing + trail effect while gliding
            player.getScheduler().runAtFixedRate(plugin, task -> {
                if (!player.isOnline() || !player.isGliding()) {
                    task.cancel();
                    return;
                }
                manager.applyGlidingEffect(player);
            }, null, 5L, 5L);
        }
    }

    /**
     * Optional: Allow swapping hands or other actions to refresh cosmetic elytra.
     */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ElytraWingManager manager = plugin.getElytraWingManager();
        if (manager != null && player.isGliding()) {
            manager.applyCosmeticElytra(player);
        }
    }
}
