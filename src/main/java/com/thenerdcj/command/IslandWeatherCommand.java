package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.IslandWeatherCosmeticManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IslandWeatherCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public IslandWeatherCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        IslandWeatherCosmeticManager manager = plugin.getIslandWeatherCosmeticManager();
        if (manager == null) {
            player.sendMessage("§cIsland Weather system not available.");
            return true;
        }

        if (args.length == 0) {
            plugin.getIslandWeatherGUI().open(player);
            return true;
        }

        // For now, direct set requires being on island (handled in GUI/manager)
        player.sendMessage("§7Use /islandweather or /weathereffects with no args to open the catalog (set active while on your island).");
        plugin.getIslandWeatherGUI().open(player);
        return true;
    }
}
