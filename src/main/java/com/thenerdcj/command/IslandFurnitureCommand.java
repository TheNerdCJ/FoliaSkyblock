package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.IslandFurnitureManager;
import com.thenerdcj.island.Island;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IslandFurnitureCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public IslandFurnitureCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (plugin.getIslandFurnitureManager() == null || plugin.getIslandFurnitureGUI() == null) {
            player.sendMessage("§cIsland Furniture system not available yet.");
            return true;
        }

        if (args.length >= 1) {
            String sub = args[0].toLowerCase();
            IslandFurnitureManager mgr = plugin.getIslandFurnitureManager();
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            String islandKey = (island != null) ? island.getId() : null;

            if (sub.equals("list") || sub.equals("placed")) {
                if (islandKey == null || mgr == null) {
                    player.sendMessage("§cNo island or furniture system.");
                    return true;
                }
                java.util.List<IslandFurnitureManager.PlacedFurniture> placed = mgr.getPlacedFurnitureForIsland(islandKey);
                if (placed.isEmpty()) {
                    player.sendMessage("§7No furniture placed on this island.");
                } else {
                    player.sendMessage("§6§lFurniture on island:");
                    for (int i = 0; i < placed.size(); i++) {
                        IslandFurnitureManager.PlacedFurniture p = placed.get(i);
                        player.sendMessage("§e" + (i+1) + ". §f" + p.furnitureId() + " §7at " + String.format("%.0f,%.0f,%.0f", p.x(), p.y(), p.z()));
                    }
                    player.sendMessage("§7Use §6/furniture remove <num>§7 to remove by list number (or shift+rightclick tool).");
                }
                return true;
            }

            if (sub.equals("remove") && args.length >= 2) {
                if (islandKey == null || mgr == null) {
                    player.sendMessage("§cNo island.");
                    return true;
                }
                try {
                    int idx = Integer.parseInt(args[1]) - 1;
                    java.util.List<IslandFurnitureManager.PlacedFurniture> placed = mgr.getPlacedFurnitureForIsland(islandKey);
                    if (idx >= 0 && idx < placed.size()) {
                        IslandFurnitureManager.PlacedFurniture p = placed.get(idx);
                        mgr.removeFurniture(islandKey, p, player.getWorld());
                        player.sendMessage("§aRemoved furniture #" + (idx+1));
                    } else {
                        player.sendMessage("§cInvalid number. Use /furniture list first.");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cUsage: /furniture remove <number from list>");
                }
                return true;
            }

            if (sub.equals("remove")) {
                player.sendMessage("§eShift + Right-click placed furniture with tool to remove nearest, or use §6/furniture list§e then §6remove <num>§e.");
                return true;
            }
        }

        plugin.getIslandFurnitureGUI().open(player);
        return true;
    }
}
