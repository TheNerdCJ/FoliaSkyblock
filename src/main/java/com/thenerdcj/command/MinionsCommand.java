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

        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("foliasb.admin")) {
                player.sendMessage("§cYou do not have permission to use admin minion commands.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUsage: /minions admin <givefuel|list|remove> [player] ...");
                return true;
            }

            String sub = args[1].toLowerCase();
            switch (sub) {
                case "givefuel":
                    if (args.length < 4) {
                        player.sendMessage("§cUsage: /minions admin givefuel <player> <amount>");
                        return true;
                    }
                    // Simple admin fuel give (targets online player for demo)
                    org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage("§cPlayer not found or not online.");
                        return true;
                    }
                    try {
                        int amount = Integer.parseInt(args[3]);
                        com.thenerdcj.island.Island tIsland = plugin.getIslandManager().getIslandByOwner(target.getUniqueId(), target.getWorld().getEnvironment());
                        if (tIsland != null) {
                            String tid = tIsland.getGridPosition().getX() + "," + tIsland.getGridPosition().getZ();
                            plugin.getMinionManager().adminAddFuel(tid, amount);
                            player.sendMessage("§aGave " + amount + " fuel to " + target.getName() + "'s island minions.");
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid amount.");
                    }
                    break;

                case "list":
                    if (args.length < 3) {
                        player.sendMessage("§cUsage: /minions admin list <player>");
                        return true;
                    }
                    org.bukkit.entity.Player listTarget = org.bukkit.Bukkit.getPlayer(args[2]);
                    if (listTarget == null) {
                        player.sendMessage("§cPlayer not online.");
                        return true;
                    }
                    com.thenerdcj.island.Island lIsland = plugin.getIslandManager().getIslandByOwner(listTarget.getUniqueId(), listTarget.getWorld().getEnvironment());
                    if (lIsland == null) {
                        player.sendMessage("§cTarget has no island in this dimension.");
                        return true;
                    }
                    String lid = lIsland.getGridPosition().getX() + "," + lIsland.getGridPosition().getZ();
                    player.sendMessage("§6=== Minions for " + listTarget.getName() + " ===");
                    player.sendMessage("§7Fuel: §e" + plugin.getMinionManager().getIslandFuel(lid));
                    var breakdown = plugin.getMinionManager().getMinionBreakdown(lid);
                    if (breakdown.isEmpty()) {
                        player.sendMessage("§7No minions placed.");
                    } else {
                        breakdown.forEach((type, count) -> player.sendMessage("  §f" + type.getDisplayName() + " §7x" + count));
                    }
                    break;

                default:
                    player.sendMessage("§cUnknown admin subcommand. Try givefuel or list.");
            }
            return true;
        }

        // Default: Open the minions GUI
        minionsGUI.openMinionsGUI(player);
        return true;
    }
}
