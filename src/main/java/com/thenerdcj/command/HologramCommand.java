package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.hologram.Hologram;
import com.thenerdcj.hologram.HologramData;
import com.thenerdcj.hologram.HologramManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Admin command for managing persistent holograms.
 * Usage: /holo create <name> | /holo addline <name> <text...> | /holo setline <name> <index> <text> etc.
 * Requires staff/admin rank or permission "foliasb.admin.hologram".
 * Integrates with existing RankManager / StaffCommand patterns.
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

        // Permission check (integrate with your RankManager or use hasPermission)
        if (!player.hasPermission("foliasb.admin.hologram") && !player.isOp()) {
            // You can also check plugin.getRankManager().hasStaffPermission(player)
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

            case "createleaderboard":
            case "leaderboard":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /holo createleaderboard <name> topislands");
                    return true;
                }
                createLeaderboard(player, args[1], args[2]);
                break;

            case "setinterval":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /holo setinterval <name> <seconds>");
                    return true;
                }
                try {
                    int seconds = Integer.parseInt(args[2]);
                    setInterval(player, args[1], seconds);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cSeconds must be a number (min 30).");
                }
                break;

            case "gui":
            case "manage":
                // Open GUI (created below)
                new com.thenerdcj.gui.HologramListGUI(plugin).open(player);
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
        player.sendMessage("§e/holo createleaderboard <name> topislands §7- Dynamic auto-updating leaderboard");
        player.sendMessage("§e/holo setinterval <name> <seconds> §7- Change refresh rate (min 30)");
        player.sendMessage("§e/holo gui §7- Open Hologram Manager GUI");
    }

    private void createHologram(Player player, String name) {
        Location loc = player.getLocation();
        HologramData data = new HologramData(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        // No forced default text. Users add their own lines (with & colors) via addline/setline.
        // This prevents unwanted "Welcome to FoliaSkyblock" on every new hologram.

        plugin.getDatabaseManager().saveHologram(data).thenAccept(success -> {
            if (success) {
                hologramManager.spawnHologram(data);
                player.sendMessage("§aHologram '" + name + "' created (empty). Use /holo addline " + name + " <text> to add content (& colors supported).");
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
                .thenAccept(success -> player.sendMessage(success ? "§aLine added." : "§cUpdate failed."));
    }

    private void setLine(Player player, String name, int index, String text) {
        Hologram holo = hologramManager.getHologramByName(name);
        if (holo == null) {
            player.sendMessage("§cHologram not found.");
            return;
        }
        holo.getData().setLine(index, text);
        hologramManager.updateLines(holo.getData().getId(), holo.getData().getLines())
                .thenAccept(success -> player.sendMessage(success ? "§aLine updated." : "§cUpdate failed."));
    }

    private void removeLine(Player player, String name, int index) {
        Hologram holo = hologramManager.getHologramByName(name);
        if (holo == null) {
            player.sendMessage("§cHologram not found.");
            return;
        }
        holo.getData().removeLine(index);
        hologramManager.updateLines(holo.getData().getId(), holo.getData().getLines())
                .thenAccept(success -> player.sendMessage(success ? "§aLine removed." : "§cUpdate failed."));
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
                        player.sendMessage("§cFailed to move hologram (DB update failed?).");
                    }
                });
    }

    private void createLeaderboard(Player player, String name, String type) {
        Location loc = player.getLocation();
        HologramData data = new HologramData(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());

        data.setDynamic(true);
        data.setDynamicType(type.toUpperCase()); // TOP_ISLANDS_LEVEL etc.
        data.setUpdateInterval(300); // 5 minutes

        // Initial header (will be overwritten on first refresh)
        data.addLine("&6&l★ Top Islands Leaderboard ★");
        data.addLine("&7Loading...");

        plugin.getDatabaseManager().saveHologram(data).thenAccept(success -> {
            if (success) {
                hologramManager.spawnHologram(data);
                // Immediate first refresh so players see data right away
                plugin.getThreadSafety().runOnMainThreadLater(() -> {
                    Hologram h = hologramManager.getHologramByName(name);
                    if (h != null && h.getData().isDynamic()) {
                        hologramManager.refreshDynamicContent(h);
                    }
                }, 40L); // 2 seconds delay to let spawn finish

                player.sendMessage("§aDynamic leaderboard hologram '" + name + "' created! Type: " + type);
                player.sendMessage("§7It will auto-update every 5 minutes (first refresh in ~2s).");
            } else {
                player.sendMessage("§cFailed to create leaderboard (name conflict?).");
            }
        });
    }

    private void setInterval(Player player, String name, int seconds) {
        Hologram holo = hologramManager.getHologramByName(name);
        if (holo == null) {
            player.sendMessage("§cHologram not found.");
            return;
        }
        if (!holo.getData().isDynamic()) {
            player.sendMessage("§cThis hologram is not dynamic.");
            return;
        }
        hologramManager.setUpdateInterval(holo.getData().getId(), seconds)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aUpdate interval for '" + name + "' set to " + seconds + " seconds.");
                    } else {
                        player.sendMessage("§cFailed to update interval.");
                    }
                });
    }
}
