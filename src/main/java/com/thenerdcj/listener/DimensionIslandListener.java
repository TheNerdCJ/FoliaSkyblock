package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerPortalEvent;

public class DimensionIslandListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandManager islandManager;

    public DimensionIslandListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
    }

    // ====================== NETHER ISLAND (Portal) ======================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNetherPortal(PlayerPortalEvent e) {
        // New reliable check - no PortalType needed
        if (e.getTo() == null || e.getTo().getWorld().getEnvironment() != World.Environment.NETHER) {
            return;
        }

        Player player = e.getPlayer();
        World.Environment dimension = World.Environment.NETHER;

        if (islandManager.hasIslandInDimension(player.getUniqueId(), dimension)) {
            return; // Already has one
        }

        e.setCancelled(true); // Stop default teleport until island is ready

        player.sendMessage("§eCreating your Nether island...");

        islandManager.createIsland(player, null, dimension)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a§lYour Nether island has been generated!");
                        Island island = islandManager.getIsland(player.getUniqueId(), dimension);
                        if (island != null) {
                            player.teleport(islandManager.getIslandHome(player));
                        }
                    } else {
                        player.sendMessage("§cFailed to create Nether island.");
                    }
                });
    }

    // ====================== END ISLAND (Void Fall) ======================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndVoidFall(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) return;

        World.Environment dimension = World.Environment.THE_END;

        if (islandManager.hasIslandInDimension(player.getUniqueId(), dimension)) {
            return; // Already has one
        }

        e.setCancelled(true); // Prevent death
        player.setFallDistance(0);

        player.sendMessage("§eCreating your End island...");

        islandManager.createIsland(player, null, dimension)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a§lYour End island has been generated!");
                        Island island = islandManager.getIsland(player.getUniqueId(), dimension);
                        if (island != null) {
                            player.teleport(islandManager.getIslandHome(player));
                        }
                    } else {
                        player.sendMessage("§cFailed to create End island.");
                    }
                });
    }
}