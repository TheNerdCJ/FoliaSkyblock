package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.hologram.Hologram;
import com.thenerdcj.hologram.HologramData;
import com.thenerdcj.hologram.HologramManager;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

/**
 * Admin command for managing persistent holograms.
 * Modern Folia-safe implementation.
 */
public class HologramCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;
    private final HologramManager hologramManager;

    public HologramCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.hologramManager = plugin.getHologramManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("foliasb.staff") && !player.isOp()) {
            player.sendMessage("§cYou do not have permission to manage holograms.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /holo create <name>");
                    return true;
                }
                createHologram(player, args[1]);
                break;

            case "addline":
            case "add":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /holo addline <name> <text...>");
                    return true;
                }
                addLine(player, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                break;

            case "setline":
                if (args.length < 4) {
                    player.sendMessage("§cUsage: /holo setline <name> <index> <text...>");
                    return true;
                }
                try {
                    int index = Integer.parseInt(args[2]);
                    setLine(player, args[1], index, String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                } catch (NumberFormatException e) {
                    player.sendMessage("§cIndex must be a number.");
                }
                break;

            case "remline":
            case "removeline":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /holo remline <name> <index>");
                    return true;
                }
                try {
                    removeLine(player, args[1], Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    player.sendMessage("§cIndex must be a number.");
                }
                break;

            case "delete":
            case "remove":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /holo delete <name>");
                    return true;
                }
                deleteHologram(player, args[1]);
                break;

            case "list":
                listHolograms(player);
                break;

            case "movehere":
            case "move":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /holo movehere <name>");
                    return true;
                }
                moveHere(player, args[1]);
                break;

            case "reload":
                player.sendMessage("§eReloading all holograms...");
                hologramManager.cleanup();
                hologramManager.loadAndSpawnAll();
                player.sendMessage("§aHolograms reloaded.");
                break;

            default:
                sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== FoliaSkyblock Hologram Admin ===");
        player.sendMessage("§e/holo create <name> §7- Create at your location");
        player.sendMessage("§e/holo addline <name> <text> §7- Add a line (use & for colors)");
        player.sendMessage("§e/holo setline <name> <index> <text>");
        player.sendMessage("§e/holo remline <name> <index>");
        player.sendMessage("§e/holo delete <name>");
        player.sendMessage("§e/holo list");
        player.sendMessage("§e/holo movehere <name> §7- Move to your location");
        player.sendMessage("§e/holo reload §7- Respawn all from DB");
    }

    private void createHologram(Player player, String name) {
        Location loc = player.getLocation();
        HologramData data = new HologramData(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());

        plugin.getDatabaseManager().saveHologram(data).thenAccept(success -> {
            if (success) {
                hologramManager.spawnHologram(data);
                player.sendMessage("§aHologram '" + name + "' created (empty). Use /holo addline " + name + " <text> to add content.");
            } else {
                player.sendMessage("§cFailed to save hologram (name may already exist).");
            }
        });
    }

    private void addLine(Player player, String name, String text) {
        Hologram holo = hologramManager.getHologramByName(name);
        if (holo == null) {
            player.sendMessage("§cHologram not found: " + name);
            return;
        }
        holo.getData().addLine(text);
        hologramManager.updateLines(holo.getData().getId(), holo.getData().getLines())
                .thenAccept(success -> {
                    if (success) {
                        hologramManager.spawnHologram(holo.getData());
                        player.sendMessage("§aLine added.");
                    } else {
                        player.sendMessage("§cUpdate failed.");
                    }
                });
    }

    private void setLine(Player player, String name, int index, String text) {
        Hologram holo = hologramManager.getHologramByName(name);
        if (holo == null) {
            player.sendMessage("§cHologram not found.");
            return;
        }
        holo.getData().setLine(index, text);
        hologramManager.updateLines(holo.getData().getId(), holo.getData().getLines())
                .thenAccept(success -> {
                    if (success) {
                        hologramManager.spawnHologram(holo.getData());
                        player.sendMessage("§aLine updated.");
                    } else {
                        player.sendMessage("§cUpdate failed.");
                    }
                });
    }

    private void removeLine(Player player, String name, int index) {
        Hologram holo = hologramManager.getHologramByName(name);
        if (holo == null) {
            player.sendMessage("§cHologram not found.");
            return;
        }
        holo.getData().removeLine(index);
        hologramManager.updateLines(holo.getData().getId(), holo.getData().getLines())
                .thenAccept(success -> {
                    if (success) {
                        hologramManager.spawnHologram(holo.getData());
                        player.sendMessage("§aLine removed.");
                    } else {
                        player.sendMessage("§cUpdate failed.");
                    }
                });
    }

    private void deleteHologram(Player player, String name) {
        Hologram holo = hologramManager.getHologramByName(name);
        if (holo == null) {
            player.sendMessage("§cHologram not found.");
            return;
        }
        hologramManager.deleteHologram(holo.getData().getId())
                .thenAccept(success -> player.sendMessage(success ? "§aHologram deleted." : "§cDelete failed."));
    }

    private void listHolograms(Player player) {
        player.sendMessage("§6Active Holograms:");
        hologramManager.getActiveHolograms().values().forEach(h -> {
            HologramData d = h.getData();
            player.sendMessage("§e- " + d.getName() + " §7(" + d.getWorldName() + " @ " + 
                String.format("%.1f,%.1f,%.1f", d.getX(), d.getY(), d.getZ()) + ") Lines: " + d.getLines().size());
        });
    }

    private void moveHere(Player player, String name) {
        Hologram holo = hologramManager.getHologramByName(name);
        if (holo == null) {
            player.sendMessage("§cHologram not found.");
            return;
        }
        Location newLoc = player.getLocation();
        hologramManager.moveHologram(holo.getData().getId(), newLoc)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aHologram moved to your location.");
                    } else {
                        player.sendMessage("§cFailed to move hologram.");
                    }
                });
    }
}