package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.AccessoryCosmetic;
import com.thenerdcj.cosmetic.AccessoryCosmeticManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AccessoryCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public AccessoryCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        AccessoryCosmeticManager manager = plugin.getAccessoryCosmeticManager();
        if (manager == null) {
            player.sendMessage("§cAccessories system not available.");
            return true;
        }

        if (args.length == 0) {
            plugin.getAccessoryCosmeticGUI().open(player);
            return true;
        }

        String accName = args[0].toUpperCase();
        try {
            AccessoryCosmetic a = AccessoryCosmetic.valueOf(accName);
            manager.setActive(player, a);
        } catch (Exception e) {
            player.sendMessage("§cUnknown accessory. Use /accessories to open the menu.");
        }
        return true;
    }
}
