package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for Join/Leave Messages cosmetic.
 */
public class JoinLeaveMessageCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public JoinLeaveMessageCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (plugin.getJoinLeaveMessageManager() == null || plugin.getJoinLeaveMessageGUI() == null) {
            player.sendMessage("§cJoin/Leave Messages system not loaded.");
            return true;
        }

        plugin.getJoinLeaveMessageGUI().open(player);
        return true;
    }
}