package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.MinionsGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /minions command - Opens the interactive Minion Management GUI.
 */
public class MinionsCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;
    private final MinionsGUI minionsGUI;

    public MinionsCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Create GUI instance (it self-registers listener)
        this.minionsGUI = new MinionsGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        // Open the minions GUI
        minionsGUI.openMinionsGUI(player);
        return true;
    }
}
