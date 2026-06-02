package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.IslandFurnitureManager;
import com.thenerdcj.cosmetic.IslandFurnitureType;
import com.thenerdcj.cosmetic.IslandStructureCosmetic;
import com.thenerdcj.cosmetic.IslandStructureManager;
import com.thenerdcj.island.Island;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles placement tool usage and basic furniture removal for the Island Furniture system.
 */
public class IslandFurnitureListener implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public IslandFurnitureListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "island_furniture_action");
    }

    @EventHandler
    public void onFurnitureToolUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null || !action.startsWith("PLACE_")) return;

        // Removal mode: Shift + Right-click with tool to remove nearby furniture
        if (player.isSneaking() && event.getAction().name().contains("RIGHT")) {
            IslandFurnitureManager manager = plugin.getIslandFurnitureManager();
            if (manager == null) return;

            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island == null) {
                player.sendMessage("§cMust be on your island to remove furniture.");
                event.setCancelled(true);
                return;
            }

            // Find nearby furniture or structure entities (ItemDisplay or ArmorStand with custom name)
            org.bukkit.Location eyeLoc = player.getEyeLocation();
            org.bukkit.util.Vector dir = eyeLoc.getDirection();
            double maxDist = 8.0;

            Entity closest = null;
            double closestDist = Double.MAX_VALUE;

            for (Entity e : player.getWorld().getNearbyEntities(eyeLoc, maxDist, maxDist, maxDist)) {
                if (e instanceof org.bukkit.entity.ItemDisplay || e instanceof org.bukkit.entity.ArmorStand) {
                    if (e.getCustomName() != null && (e.getCustomName().startsWith("§6Furniture:") || e.getCustomName().startsWith("§6Structure:"))) {
                        double dist = e.getLocation().distance(eyeLoc);
                        if (dist < closestDist && dist < maxDist) {
                            closestDist = dist;
                            closest = e;
                        }
                    }
                }
            }

            if (closest != null) {
                String name = closest.getCustomName();
                if (name.startsWith("§6Furniture:")) {
                    String furnId = name.substring("§6Furniture:".length());
                    try {
                        IslandFurnitureType type = IslandFurnitureType.valueOf(furnId);
                        String islandKey = island.getId();
                        manager.removeFurniture(islandKey, new IslandFurnitureManager.PlacedFurniture(furnId, closest.getLocation().getX(), closest.getLocation().getY(), closest.getLocation().getZ(), closest.getLocation().getYaw(), ""), closest.getWorld());
                        closest.remove();
                        player.sendMessage("§aRemoved " + type.getDisplayName());
                    } catch (Exception ex) {
                        closest.remove();
                        player.sendMessage("§aRemoved furniture.");
                    }
                } else if (name.startsWith("§6Structure:")) {
                    String structId = name.substring("§6Structure:".length());
                    try {
                        IslandStructureCosmetic type = IslandStructureCosmetic.valueOf(structId);
                        String islandKey = island.getId();
                        IslandStructureManager structManager = plugin.getIslandStructureManager();
                        if (structManager != null) {
                            structManager.removeStructure(islandKey, new IslandStructureManager.PlacedStructure(structId, closest.getLocation().getX(), closest.getLocation().getY(), closest.getLocation().getZ(), closest.getLocation().getYaw(), ""), closest.getWorld());
                        }
                        closest.remove();
                        player.sendMessage("§aRemoved " + type.getDisplayName());
                    } catch (Exception ex) {
                        closest.remove();
                        player.sendMessage("§aRemoved structure.");
                    }
                }
            } else {
                player.sendMessage("§eNo furniture or structure found nearby to remove.");
            }

            event.setCancelled(true);
            return;
        }

        if (action.startsWith("PLACE_STRUCT_")) {
            IslandStructureManager structManager = plugin.getIslandStructureManager();
            if (structManager == null) return;

            try {
                IslandStructureCosmetic type = IslandStructureCosmetic.valueOf(action.substring(13));
                Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());

                if (island == null) {
                    player.sendMessage("§cYou must be on your island to place structures.");
                    event.setCancelled(true);
                    return;
                }

                if (event.getClickedBlock() != null) {
                    org.bukkit.Location placeLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);

                    boolean success = structManager.placeStructure(player, island, type, placeLoc, player.getLocation().getYaw());
                    if (success) {
                        if (item.getAmount() > 1) {
                            item.setAmount(item.getAmount() - 1);
                        } else {
                            player.getInventory().setItemInMainHand(null);
                        }
                    }
                } else {
                    player.sendMessage("§eRight-click a block to place " + type.getDisplayName());
                }

                event.setCancelled(true);
            } catch (Exception ignored) {
                player.sendMessage("§cInvalid structure tool.");
            }
            return;
        }

        IslandFurnitureManager manager = plugin.getIslandFurnitureManager();
        if (manager == null) return;

        try {
            IslandFurnitureType type = IslandFurnitureType.valueOf(action.substring(6));
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());

            if (island == null) {
                player.sendMessage("§cYou must be on your island to place furniture.");
                event.setCancelled(true);
                return;
            }

            if (event.getClickedBlock() != null) {
                org.bukkit.Location placeLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);

                boolean success = manager.placeFurniture(player, island, type, placeLoc, player.getLocation().getYaw());
                if (success) {
                    // Consume the tool
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }
                }
            } else {
                player.sendMessage("§eRight-click a block to place " + type.getDisplayName());
            }

            event.setCancelled(true);
        } catch (Exception ignored) {
            player.sendMessage("§cInvalid furniture tool.");
        }
    }
}
