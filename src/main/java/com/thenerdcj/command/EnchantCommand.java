package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /enchant command - Opens the custom enchanting table
 */
public class EnchantCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public EnchantCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            player.sendMessage("§cYou must hold an item to enchant!");
            return true;
        }

        // Open the custom enchanting table
        plugin.getEnchantingTableGUI().open(player, item);

        return true;
    }
}