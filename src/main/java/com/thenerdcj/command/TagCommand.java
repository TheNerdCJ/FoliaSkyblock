package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.tags.PlayerTag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Dedicated /tags command.
 * Opens the interactive Tag GUI for managing cosmetic player tags.
 * Consistent with /pets command pattern.
 */
public class TagCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public TagCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("folia.skyblock.tags")) {
            player.sendMessage("§cYou do not have permission to use tags.");
            return true;
        }

        // Open the dedicated GUI (will create TagGUI next)
        if (plugin.getTagGUI() != null) {
            plugin.getTagGUI().open(player);
        } else {
            player.sendMessage("§cTags system is not fully initialized yet.");
        }

        return true;
    }
}
