package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * DimensionIslandListener - Handles dimension transitions and island loading.
 *
 * Features:
 * - Auto-loads player islands on join
 * - Notifies players when they enter a dimension without an island
 * - Optional auto-create island when entering new dimension (configurable)
 */
public class DimensionIslandListener implements Listener {

    private final FoliaSkyblock plugin;
    private final boolean autoCreateOnDimensionEnter;

    public DimensionIslandListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.autoCreateOnDimensionEnter = plugin.getConfig().getBoolean("island.auto-create-on-dimension", false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load all islands for this player across all dimensions
        plugin.getIslandManager().loadPlayerIslands(player);

        // Welcome message with island info
        plugin.getServer().getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), World.Environment.NORMAL);
            if (island != null) {
                player.sendMessage("§aWelcome back! Your island is level §e" + island.getLevel());
            } else {
                player.sendMessage("§eYou don't have an island yet! Use §b/island create§e to begin.");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World.Environment newDimension = player.getWorld().getEnvironment();

        // Check if player has an island in the new dimension
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), newDimension);

        if (island == null) {
            player.sendMessage("§eYou don't have an island in this dimension yet!");

            // Auto-create island if enabled (for donors or if configured)
            if (autoCreateOnDimensionEnter &&
                    (player.hasPermission("foliasb.donor") || player.hasPermission("foliasb.autocreate"))) {

                player.sendMessage("§aCreating your island in this dimension...");

                plugin.getIslandManager().createIsland(player, null, newDimension)
                        .thenAccept(success -> {
                            if (success) {
                                player.sendMessage("§a§lIsland created! Welcome to the " +
                                        newDimension.name().toLowerCase() + " dimension!");
                            }
                        });
            } else {
                player.sendMessage("§7Use §b/island create§7 to claim an island here.");
            }
        } else {
            // Player has an island here - show quick info
            player.sendMessage("§aWelcome to your §e" + island.getBiomeName() + "§a island (Level " + island.getLevel() + ")");
        }
    }
}