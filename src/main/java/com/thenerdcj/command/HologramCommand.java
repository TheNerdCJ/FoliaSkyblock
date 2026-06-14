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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                    player.sendMessage("§cUsage: /holo create <name> [timer-seconds]  (timer makes it auto-remove after N seconds; otherwise always re-spawns on chunk load)");
                    return true;
                }
                String timerArg = args.length >= 3 ? args[2] : null;
                createHologram(player, args[1], timerArg);
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
        player.sendMessage("§e/holo create <name> [timer-seconds] §7- Create at your location (optional timer for auto-remove; default = persistent, re-spawns on chunk load)");
        player.sendMessage("§e/holo addline <name> <text> §7- Add a line (use & for colors)");
        player.sendMessage("§e/holo setline <name> <index> <text>");
        player.sendMessage("§e/holo remline <name> <index>");
        player.sendMessage("§e/holo delete <name>");
        player.sendMessage("§e/holo list");
        player.sendMessage("§e/holo movehere <name> §7- Move to your location");
        player.sendMessage("§e/holo reload §7- Respawn all from DB");
    }

    private void createHologram(Player player, String name, String timerArg) {
        Location loc = player.getLocation();
        HologramData data = new HologramData(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        plugin.getLogger().info("[HOLO-DEBUG-COMMAND] CREATE: name='" + name + "' world=" + loc.getWorld().getName() + " pos=(" + loc.getX() + "," + loc.getY() + "," + loc.getZ() + ") by " + player.getName());

        final int timerSeconds = (timerArg != null) ? parseTimer(timerArg) : 0;
        if (timerSeconds > 0) {
            plugin.getLogger().info("[HOLO-DEBUG-COMMAND] CREATE with timer=" + timerSeconds + "s (will auto-remove)");
        }

        plugin.getDatabaseManager().saveHologram(data).thenAccept(success -> {
            plugin.getLogger().info("[HOLO-DEBUG-COMMAND] CREATE DB save for '" + name + "' success=" + success + " assignedId=" + data.getId());
            if (success) {
                hologramManager.spawnHologram(data);
                if (timerSeconds > 0) {
                    long delayTicks = timerSeconds * 20L;
                    plugin.getThreadSafety().runOnMainThreadLater(() -> {
                        hologramManager.deleteHologram(data.getId());
                        if (player.isOnline()) {
                            player.sendMessage("§eHologram '" + name + "' auto-removed after " + timerSeconds + "s.");
                        }
                    }, delayTicks);
                    player.sendMessage("§aHologram '" + name + "' created (timer " + timerSeconds + "s). It will auto-remove.");
                } else {
                    player.sendMessage("§aHologram '" + name + "' created (persistent - will re-spawn on chunk load/return). Use /holo addline " + name + " <text> to add content.");
                }
                // Debug verify for create
                plugin.getThreadSafety().runOnMainThreadLater(() -> {
                    Hologram after = hologramManager.getActiveHolograms().get(data.getId());
                    int disp = (after != null && after.getDisplays() != null) ? after.getDisplays().size() : 0;
                    int logical = (after != null && after.getData() != null) ? after.getData().getLines().size() : 0;
                    plugin.getLogger().info("[HOLO-DEBUG-COMMAND] CREATE 40tick CHECK: id=" + data.getId() + " displays=" + disp + " logicalLines=" + logical);
                    if (disp == 0 && logical > 0) {
                        plugin.getLogger().warning("[HOLO-DEBUG-COMMAND] CREATE: 0 displays but had lines - forcing re-spawn");
                        if (after != null) hologramManager.spawnHologram(after.getData());
                    }
                }, 40L);
            } else {
                player.sendMessage("§cFailed to save hologram (name may already exist).");
            }
        });
    }

    private int parseTimer(String arg) {
        try {
            int t = Integer.parseInt(arg);
            return Math.max(5, Math.min(3600, t)); // min 5s, max 1h
        } catch (Exception e) {
            return 0;
        }
    }

    private void addLine(Player player, String name, String text) {
        plugin.getLogger().info("[HOLO-DEBUG-COMMAND] ADDLINE start: name='" + name + "' text='" + text + "' by " + player.getName());
        Hologram holo = hologramManager.getHologramByName(name);
        if (holo == null) {
            plugin.getLogger().warning("[HOLO-DEBUG-COMMAND] ADDLINE: holo not found by name='" + name + "' - will retry in 1 tick");
            player.sendMessage("§cHologram not found: " + name);
            return;
        }
        plugin.getLogger().info("[HOLO-DEBUG-COMMAND] ADDLINE: found holo id=" + holo.getData().getId() + " currentLinesBefore=" + holo.getData().getLines().size());
        holo.getData().addLine(text);
        // Pass a copy to avoid list aliasing bug with setLines (clear + addAll on same list object)
        List<String> linesCopy = new ArrayList<>(holo.getData().getLines());
        hologramManager.updateLines(holo.getData().getId(), linesCopy)
                .thenAccept(success -> {
                    plugin.getLogger().info("[HOLO-DEBUG-COMMAND] ADDLINE DB update success=" + success + " for id=" + holo.getData().getId() + " linesNow=" + holo.getData().getLines().size());
                    if (success) {
                        hologramManager.spawnHologram(holo.getData());
                        player.sendMessage("§aLine added.");
                        // Extra post-spawn check with more debug (ALWAYS logs for isolation)
                        plugin.getThreadSafety().runOnMainThreadLater(() -> {
                            Hologram after = hologramManager.getActiveHolograms().get(holo.getData().getId());
                            int disp = (after != null && after.getDisplays() != null) ? after.getDisplays().size() : 0;
                            int logical = (after != null && after.getData() != null) ? after.getData().getLines().size() : -1;
                            plugin.getLogger().info("[HOLO-DEBUG-COMMAND] ADDLINE 40tick CHECK: id=" + holo.getData().getId() + " displays=" + disp + " logicalLines=" + logical + " (full debug dump)");
                            if (disp == 0) {
                                plugin.getLogger().warning("[HOLO-DEBUG-COMMAND] ADDLINE: STILL 0 DISPLAYS after spawn! Will force re-spawn.");
                                if (after != null) hologramManager.spawnHologram(after.getData());
                            } else {
                                plugin.getLogger().info("[HOLO-DEBUG-COMMAND] ADDLINE SUCCESS: " + disp + " displays are live for id=" + holo.getData().getId());
                            }
                        }, 40L);
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
        // Pass a copy to avoid list aliasing bug with setLines
        List<String> linesCopy = new ArrayList<>(holo.getData().getLines());
        hologramManager.updateLines(holo.getData().getId(), linesCopy)
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
        // Pass a copy to avoid list aliasing bug with setLines
        List<String> linesCopy = new ArrayList<>(holo.getData().getLines());
        hologramManager.updateLines(holo.getData().getId(), linesCopy)
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