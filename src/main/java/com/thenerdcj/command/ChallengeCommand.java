package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.ChallengeGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChallengeCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;
    private final ChallengeGUI challengeGUI;

    public ChallengeCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.challengeGUI = new ChallengeGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        challengeGUI.open(player);
        return true;
    }
}