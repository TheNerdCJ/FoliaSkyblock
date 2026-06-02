package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /skills - Opens player skill GUI. MCMMO inspired.
 */
public class SkillCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public SkillCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (plugin.getPlayerSkillManager() == null || plugin.getSkillGUI() == null) {
            player.sendMessage("§cSkill system unavailable.");
            return true;
        }
        plugin.getSkillGUI().open(player);
        return true;
    }
}