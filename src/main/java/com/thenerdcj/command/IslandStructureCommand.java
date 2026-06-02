package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.IslandStructureManager;
import com.thenerdcj.island.Island;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IslandStructureCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public IslandStructureCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (plugin.getIslandStructureManager() == null || plugin.getIslandStructureGUI() == null) {
            player.sendMessage("§cIsland Structures system not available yet.");
            return true;
        }

        IslandStructureManager mgr = plugin.getIslandStructureManager();
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        String islandKey = (island != null) ? island.getId() : null;

        if (args.length >= 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("list") || sub.equals("placed")) {
                if (islandKey == null) {
                    player.sendMessage("§cNo island.");
                    return true;
                }
                java.util.List<IslandStructureManager.PlacedStructure> placed = mgr.getPlacedStructuresForIsland(islandKey);
                if (placed.isEmpty()) {
                    player.sendMessage("§7No structures placed on this island.");
                } else {
                    player.sendMessage("§6§lStructures on island:");
                    for (int i = 0; i < placed.size(); i++) {
                        IslandStructureManager.PlacedStructure p = placed.get(i);
                        player.sendMessage("§e" + (i+1) + ". §f" + p.structureId() + " §7at " + String.format("%.0f,%.0f,%.0f", p.x(), p.y(), p.z()));
                    }
                    player.sendMessage("§7Use §6/structure remove <num>§7 to remove by list number (or shift+rightclick tool).");
                }
                return true;
            }
            if (sub.equals("remove") && args.length >= 2) {
                if (islandKey == null) {
                    player.sendMessage("§cNo island.");
                    return true;
                }
                try {
                    int idx = Integer.parseInt(args[1]) - 1;
                    java.util.List<IslandStructureManager.PlacedStructure> placed = mgr.getPlacedStructuresForIsland(islandKey);
                    if (idx >= 0 && idx < placed.size()) {
                        IslandStructureManager.PlacedStructure p = placed.get(idx);
                        mgr.removeStructure(islandKey, p, player.getWorld());
                        player.sendMessage("§aRemoved structure #" + (idx+1));
                    } else {
                        player.sendMessage("§cInvalid number. Use /structure list first.");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cUsage: /structure remove <number from list>");
                }
                return true;
            }
            if (sub.equals("remove")) {
                player.sendMessage("§eUse §6/structure list§e then §6remove <num>§e, or shift+rightclick tool.");
                return true;
            }
        }

        plugin.getIslandStructureGUI().open(player);
        return true;
    }
}
