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
        World.Environment oldDimension = event.getFrom().getEnvironment();
        World.Environment newDimension = player.getWorld().getEnvironment();

        // Polish: call leave hooks for previous island's cosmetics (music/weather particle loops)
        if (!oldDimension.equals(newDimension)) {
            Island oldIsland = plugin.getIslandManager().getIsland(player.getUniqueId(), oldDimension);
            if (oldIsland != null) {
                if (plugin.getIslandWeatherCosmeticManager() != null) {
                    plugin.getIslandWeatherCosmeticManager().onPlayerLeaveIsland(player, oldIsland);
                }
                if (plugin.getIslandMusicManager() != null) {
                    plugin.getIslandMusicManager().onPlayerLeaveIsland(player, oldIsland);
                }
            }
        }

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

            // Polish: trigger island cosmetic enter hooks for weather/music (Folia-safe delegation)
            if (plugin.getIslandWeatherCosmeticManager() != null) {
                plugin.getIslandWeatherCosmeticManager().onPlayerEnterIsland(player, island);
            }
            if (plugin.getIslandMusicManager() != null) {
                plugin.getIslandMusicManager().onPlayerEnterIsland(player, island);
            }

            // Housing decor (furniture/structures) - respawn visuals on enter for coverage (e.g. after dim switch or reload edge cases)
            if (plugin.getIslandFurnitureManager() != null) {
                org.bukkit.World w = (island.getCenter(null) != null) ? island.getCenter(null).getWorld() : player.getWorld();
                plugin.getIslandFurnitureManager().respawnAllFurnitureForIsland(island.getId(), w);
            }
            if (plugin.getIslandStructureManager() != null) {
                org.bukkit.World w = (island.getCenter(null) != null) ? island.getCenter(null).getWorld() : player.getWorld();
                plugin.getIslandStructureManager().respawnAllStructuresForIsland(island.getId(), w);
            }

            // Housing set bonus visuals on enter (active placed sets "shine" for the player and visitors)
            if (plugin.getIslandFurnitureManager() != null) {
                plugin.getIslandFurnitureManager().onPlayerEnterIsland(player, island);
            }

            // Broader integration: re-apply player-based cosmetic visuals (accessories, overhead) on island enter/dim change
            // Ensures displays persist/re-spawn across world changes (Folia-safe via setActive which triggers spawn)
            if (plugin.getAccessoryCosmeticManager() != null) {
                com.thenerdcj.cosmetic.AccessoryCosmetic acc = plugin.getAccessoryCosmeticManager().getActive(player.getUniqueId());
                if (!acc.isNone()) {
                    plugin.getAccessoryCosmeticManager().setActive(player, acc);
                }
            }
            if (plugin.getOverheadCosmeticManager() != null) {
                com.thenerdcj.cosmetic.OverheadCosmetic ov = plugin.getOverheadCosmeticManager().getActive(player.getUniqueId());
                if (!ov.isNone()) {
                    plugin.getOverheadCosmeticManager().setActive(player, ov);
                }
            }
        }
    }
}