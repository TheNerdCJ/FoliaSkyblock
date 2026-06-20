package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.AFKManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /afk command - usable by everyone.
 * Toggles AFK status manually.
 * AFK also shows in tab list and auto-triggers after 15 min no move.
 */
public class AFKCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;
    private final AFKManager afkManager;

    public AFKCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.afkManager = plugin.getAfkManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /afk.");
            return true;
        }

        if (afkManager == null) {
            player.sendMessage("§cAFK system not available.");
            return true;
        }

        afkManager.toggleAFK(player);
        return true;
    }
}
