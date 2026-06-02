package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MinionSkinCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public MinionSkinCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (plugin.getMinionSkinManager() == null || plugin.getMinionSkinGUI() == null) {
            player.sendMessage("§cMinion Skins system not available.");
            return true;
        }

        plugin.getMinionSkinGUI().open(player);
        return true;
    }
}
