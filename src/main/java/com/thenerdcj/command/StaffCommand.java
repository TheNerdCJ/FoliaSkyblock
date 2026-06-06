package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.Punishment;
import com.thenerdcj.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffCommand implements CommandExecutor, TabCompleter {

    private final FoliaSkyblock plugin;
    private final ConcurrentHashMap<UUID, Boolean> vanishedPlayers = new ConcurrentHashMap<>();

    public StaffCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "§cOnly players can use staff commands.");
            return true;
        }

        if (!player.hasPermission("foliasb.staff")) {
            MessageUtil.sendMessage(player, "§cYou don't have permission to use this command.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "staff":
                if (plugin.getStaffGUI() != null) {
                    plugin.getStaffGUI().open(player);
                } else {
                    sendStaffHelp(player);
                }
                break;

            // === Moderation Tools ===
            case "vanish":
                toggleVanish(player);
                break;

            case "fly":
                Player flyTarget = resolveOptionalTarget(player, args, 0);
                if (flyTarget == null) return true;
                boolean newFly = !flyTarget.getAllowFlight();
                flyTarget.setAllowFlight(newFly);
                String flyMsg = "§aFlight " + (newFly ? "§2enabled" : "§cdisabled") + "§a.";
                if (flyTarget.equals(player)) {
                    MessageUtil.sendMessage(player, flyMsg);
                } else {
                    MessageUtil.sendMessage(player, "§aSet flight " + (newFly ? "§2on" : "§coff") + " for §e" + flyTarget.getName());
                    MessageUtil.sendMessage(flyTarget, flyMsg + " §7(by " + player.getName() + ")");
                }
                break;

            case "god":
                Player godTarget = resolveOptionalTarget(player, args, 0);
                if (godTarget == null) return true;
                boolean newGod = !godTarget.isInvulnerable();
                godTarget.setInvulnerable(newGod);
                String godMsg = "§aGod mode " + (newGod ? "§2enabled" : "§cdisabled") + "§a.";
                if (godTarget.equals(player)) {
                    MessageUtil.sendMessage(player, godMsg);
                } else {
                    MessageUtil.sendMessage(player, "§aSet god mode " + (newGod ? "§2on" : "§coff") + " for §e" + godTarget.getName());
                    MessageUtil.sendMessage(godTarget, godMsg + " §7(by " + player.getName() + ")");
                }
                break;

            case "heal":
                if (args.length == 0) {
                    healPlayer(player, player);
                } else {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target != null) healPlayer(player, target);
                    else MessageUtil.sendMessage(player, "§cPlayer not found.");
                }
                break;

            case "speed":
                if (args.length == 0) {
                    MessageUtil.sendMessage(player, "§cUsage: /speed <1-10> [player]");
                    return true;
                }
                Player speedTarget = resolveOptionalTarget(player, args, 1);
                if (speedTarget == null) return true;
                try {
                    float speed = Math.max(0.1f, Math.min(1f, Float.parseFloat(args[0]) / 10f));
                    speedTarget.setFlySpeed(speed);
                    speedTarget.setWalkSpeed(speed);
                    String speedMsg = "§aSpeed set to §e" + (int)(speed * 10);
                    if (speedTarget.equals(player)) {
                        MessageUtil.sendMessage(player, speedMsg);
                    } else {
                        MessageUtil.sendMessage(player, "§aSet speed §e" + (int)(speed * 10) + " §afor §e" + speedTarget.getName());
                        MessageUtil.sendMessage(speedTarget, speedMsg + " §7(by " + player.getName() + ")");
                    }
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(player, "§cInvalid speed value.");
                }
                break;

            case "gm", "gamemode":
                if (args.length == 0) {
                    MessageUtil.sendMessage(player, "§cUsage: /gm <mode> [player]  (modes: 0/s/survival, 1/c/creative, 2/a/adventure, 3/sp/spectator)");
                    return true;
                }
                GameMode mode = switch (args[0].toLowerCase()) {
                    case "0", "s", "survival" -> GameMode.SURVIVAL;
                    case "1", "c", "creative" -> GameMode.CREATIVE;
                    case "2", "a", "adventure" -> GameMode.ADVENTURE;
                    case "3", "sp", "spectator" -> GameMode.SPECTATOR;
                    default -> null;
                };
                if (mode == null) {
                    MessageUtil.sendMessage(player, "§cInvalid gamemode. Use 0/s/survival, 1/c/creative, 2/a/adventure, 3/sp/spectator.");
                    return true;
                }
                Player gmTarget = resolveOptionalTarget(player, args, 1);
                if (gmTarget == null) return true;
                setGamemode(player, gmTarget, mode);
                break;

            // EssentialsX-style direct shortcuts (support optional [player] target like Essentials)
            case "gmc":
                Player tC = resolveOptionalTarget(player, args, 0);
                if (tC == null) return true;
                setGamemode(player, tC, GameMode.CREATIVE);
                break;
            case "gms":
                Player tS = resolveOptionalTarget(player, args, 0);
                if (tS == null) return true;
                setGamemode(player, tS, GameMode.SURVIVAL);
                break;
            case "gma":
                Player tA = resolveOptionalTarget(player, args, 0);
                if (tA == null) return true;
                setGamemode(player, tA, GameMode.ADVENTURE);
                break;
            case "gmsp":
                Player tSp = resolveOptionalTarget(player, args, 0);
                if (tSp == null) return true;
                setGamemode(player, tSp, GameMode.SPECTATOR);
                break;

            // === Teleport Tools ===
            case "tp":
                if (args.length == 0) {
                    MessageUtil.sendMessage(player, "§cUsage: /tp <player>");
                    return true;
                }
                Player tpTarget = Bukkit.getPlayer(args[0]);
                if (tpTarget != null) {
                    plugin.recordLastLocation(player);
                    player.teleport(tpTarget);
                    MessageUtil.sendMessage(player, "§aTeleported to §e" + tpTarget.getName());
                } else MessageUtil.sendMessage(player, "§cPlayer not found.");
                break;

            case "tphere":
                if (args.length == 0) {
                    MessageUtil.sendMessage(player, "§cUsage: /tphere <player>");
                    return true;
                }
                Player hereTarget = Bukkit.getPlayer(args[0]);
                if (hereTarget != null) {
                    plugin.recordLastLocation(hereTarget);
                    hereTarget.teleport(player);
                    MessageUtil.sendMessage(player, "§aTeleported §e" + hereTarget.getName() + " §ato you.");
                } else MessageUtil.sendMessage(player, "§cPlayer not found.");
                break;

            case "tppos":
                if (args.length < 3) {
                    MessageUtil.sendMessage(player, "§cUsage: /tppos <x> <y> <z>");
                    return true;
                }
                try {
                    double x = Double.parseDouble(args[0]);
                    double y = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    Location dest = player.getWorld().getBlockAt((int)x, (int)y, (int)z).getLocation().add(0.5, 0, 0.5);
                    plugin.recordLastLocation(player);
                    player.teleport(dest);
                    MessageUtil.sendMessage(player, "§aTeleported to coordinates.");
                } catch (Exception e) {
                    MessageUtil.sendMessage(player, "§cInvalid coordinates.");
                }
                break;

            // === Punishments (with logging) ===
            case "ban", "tempban":
                handleBan(player, args, cmd.equals("tempban"));
                break;

            case "kick":
                handleKick(player, args);
                break;

            case "mute":
                handleMute(player, args);
                break;

            case "unmute":
                handleUnmute(player, args);
                break;

            case "warn":
                handleWarn(player, args);
                break;

            // === Inventory Tools ===
            case "invsee":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /invsee <player>");
                    return true;
                }
                Player invTarget = Bukkit.getPlayer(args[0]);
                if (invTarget != null) {
                    player.openInventory(invTarget.getInventory());
                    player.sendMessage("§aOpened inventory of §e" + invTarget.getName());
                }
                break;

            case "endersee":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /endersee <player>");
                    return true;
                }
                Player enderTarget = Bukkit.getPlayer(args[0]);
                if (enderTarget != null) {
                    player.openInventory(enderTarget.getEnderChest());
                    player.sendMessage("§aOpened ender chest of §e" + enderTarget.getName());
                }
                break;

            case "freeze":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /freeze <player>");
                    return true;
                }
                Player freezeTarget = Bukkit.getPlayer(args[0]);
                if (freezeTarget != null) {
                    freezeTarget.setWalkSpeed(0f);
                    freezeTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 255, false, false));
                    player.sendMessage("§cFrozen §e" + freezeTarget.getName());
                }
                break;

            // === Staff Chat & Announce ===
            case "sc", "staffchat":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /sc <message>");
                    return true;
                }
                String scMsg = "§c[Staff] §e" + player.getName() + "§7: §f" + String.join(" ", args);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("foliasb.staff")) p.sendMessage(scMsg);
                }
                break;

            case "broadcast", "announce":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /broadcast <message>");
                    return true;
                }
                String message = String.join(" ", args);
                Component announcement = Component.text()
                        .append(Component.text("[ANNOUNCEMENT] ", NamedTextColor.GOLD))
                        .append(Component.text(message, NamedTextColor.WHITE))
                        .build();
                Bukkit.broadcast(announcement);
                break;

            case "clear":
                Player clearTarget = resolveOptionalTarget(player, args, 0);
                if (clearTarget == null) return true;
                clearTarget.getInventory().clear();
                if (clearTarget.equals(player)) {
                    MessageUtil.sendMessage(player, "§aYour inventory has been cleared.");
                } else {
                    MessageUtil.sendMessage(player, "§aCleared inventory of §e" + clearTarget.getName());
                    MessageUtil.sendMessage(clearTarget, "§cYour inventory was cleared by §e" + player.getName());
                }
                break;

            case "repair":
                Player repairTarget = resolveOptionalTarget(player, args, 0);
                if (repairTarget == null) return true;
                handleRepair(player, repairTarget);
                break;

            case "setspawn":
                // Supports foliasb.admin.setspawn (declared in plugin.yml) in addition to staff
                if (!player.hasPermission("foliasb.admin.setspawn") && !player.hasPermission("foliasb.admin")) {
                    MessageUtil.sendMessage(player, "§cYou don't have permission to set the global spawn.");
                    return true;
                }
                if (plugin.getWorldManager() != null) {
                    plugin.getWorldManager().setHubSpawnLocation(player.getLocation());
                    MessageUtil.sendMessage(player, "§aGlobal server spawn location set to your current position.");
                    MessageUtil.sendMessage(player, "§7(Updated world spawn + spawn-platform config. Players will land here on /spawn and joins.)");
                } else {
                    MessageUtil.sendMessage(player, "§cWorldManager not available; cannot set spawn.");
                }
                break;

            // === EssentialsX-style movement tools (record last loc for /back) ===
            case "top":
                handleTop(player, args);
                break;
            case "jump":
                handleJump(player, args);
                break;
            case "back":
                handleBack(player, args);
                break;

            // === Sudo + Social Spy ===
            case "sudo":
                handleSudo(player, args);
                break;
            case "socialspy":
                if (plugin.getChatManager() != null) {
                    plugin.getChatManager().toggleStaffSpy(player);
                } else {
                    MessageUtil.sendMessage(player, "§cChatManager not available.");
                }
                break;

            // === Time / Weather (world level, affects the dimension/island the target is in) ===
            case "time":
                handleTime(player, args);
                break;
            case "weather":
                handleWeather(player, args);
                break;

            default:
                MessageUtil.sendMessage(player, "§cUnknown staff command. Use /staff for a list.");
        }
        return true;
    }

    // ==================== HELPER METHODS ====================

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.getOrDefault(uuid, false);
    }

    public void toggleVanish(Player player) {
        boolean isVanished = vanishedPlayers.getOrDefault(player.getUniqueId(), false);
        if (!isVanished) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("foliasb.staff")) p.hidePlayer(plugin, player);
            }
            vanishedPlayers.put(player.getUniqueId(), true);
            plugin.getAntiCheatManager().setStaffBypass(player.getUniqueId(), true);
            MessageUtil.sendMessage(player, "§aYou are now vanished.");
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, player);
            }
            vanishedPlayers.remove(player.getUniqueId());
            plugin.getAntiCheatManager().setStaffBypass(player.getUniqueId(), false);
            MessageUtil.sendMessage(player, "§cYou are no longer vanished.");
        }
    }

    private void healPlayer(Player staff, Player target) {
        target.setHealth(20);
        target.setFoodLevel(20);
        target.setSaturation(20);
        MessageUtil.sendMessage(target, "§aYou have been healed by staff.");
        MessageUtil.sendMessage(staff, "§aHealed §e" + target.getName());
    }

    private void handleBan(Player staff, String[] args, boolean isTemp) {
        if (args.length == 0) {
            staff.sendMessage("§cUsage: /ban <player> [reason]   or   /tempban <player> <minutes> [reason]");
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            staff.sendMessage("§cPlayer not found or offline.");
            return;
        }

        String reason = args.length > (isTemp ? 2 : 1)
                ? String.join(" ", java.util.Arrays.copyOfRange(args, isTemp ? 2 : 1, args.length))
                : "Banned by staff";

        long duration = 0;
        if (isTemp) {
            try {
                int minutes = Integer.parseInt(args[1]);
                duration = minutes * 60L * 1000L;
            } catch (NumberFormatException e) {
                staff.sendMessage("§cInvalid duration. Use number of minutes.");
                return;
            }
        }

        // Log punishment
        plugin.getPunishmentManager().logPunishment(
                target.getUniqueId(),
                staff.getUniqueId(),
                isTemp ? Punishment.Type.TEMPBAN : Punishment.Type.BAN,
                reason,
                duration
        );

        // Kick with modern API
        String kickMsg = "§cYou have been " + (isTemp ? "temporarily " : "") + "banned.\n§eReason: §f" + reason;
        target.kick(MessageUtil.legacy(kickMsg));

        staff.sendMessage("§c" + (isTemp ? "Tempbanned" : "Banned") + " §e" + target.getName());
    }

    private void handleKick(Player staff, String[] args) {
        if (args.length == 0) {
            staff.sendMessage("§cUsage: /kick <player> [reason]");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            staff.sendMessage("§cPlayer not found.");
            return;
        }
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kicked by staff";
        target.kick(MessageUtil.legacy("§c" + reason));
        staff.sendMessage("§aKicked §e" + target.getName());
    }

    private void handleMute(Player staff, String[] args) {
        if (args.length == 0) {
            staff.sendMessage("§cUsage: /mute <player> [reason]");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            staff.sendMessage("§cPlayer not found.");
            return;
        }
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Muted by staff";
        plugin.getChatManager().mute(target.getUniqueId());
        plugin.getPunishmentManager().logPunishment(target.getUniqueId(), staff.getUniqueId(), Punishment.Type.MUTE, reason, 0);
        target.sendMessage("§cYou have been muted. Reason: §f" + reason);
        staff.sendMessage("§cMuted §e" + target.getName());
    }

    private void handleUnmute(Player staff, String[] args) {
        if (args.length == 0) {
            staff.sendMessage("§cUsage: /unmute <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target != null) {
            plugin.getChatManager().unmute(target.getUniqueId());
            staff.sendMessage("§aUnmuted §e" + target.getName());
        }
    }

    private void handleWarn(Player staff, String[] args) {
        if (args.length < 2) {
            staff.sendMessage("§cUsage: /warn <player> <reason>");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            staff.sendMessage("§cPlayer not found.");
            return;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        plugin.getPunishmentManager().logPunishment(target.getUniqueId(), staff.getUniqueId(), Punishment.Type.WARN, reason, 0);
        target.sendMessage("§6§l[WARNING] §e" + reason);
        staff.sendMessage("§aWarned §e" + target.getName());
    }

    private void handleRepair(Player staff, Player target) {
        ItemStack item = target.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            MessageUtil.sendMessage(staff, "§c" + (target.equals(staff) ? "You" : target.getName()) + " must hold an item to repair.");
            return;
        }
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(0);
            item.setItemMeta(damageable);
            if (target.equals(staff)) {
                MessageUtil.sendMessage(staff, "§aItem repaired.");
            } else {
                MessageUtil.sendMessage(staff, "§aRepaired held item for §e" + target.getName());
                MessageUtil.sendMessage(target, "§aYour held item was repaired by §e" + staff.getName());
            }
        } else {
            MessageUtil.sendMessage(staff, "§cThis item cannot be repaired.");
        }
    }

    // ==================== NEW ESSENTIALSX MOVEMENT + TOOLS ====================

    private void handleTop(Player staff, String[] args) {
        Player target = resolveOptionalTarget(staff, args, 0);
        if (target == null) return;
        plugin.recordLastLocation(target);
        Location loc = target.getLocation();
        World w = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int y = w.getHighestBlockYAt(x, z);
        // Ensure we land on top, not inside
        Location top = new Location(w, x + 0.5, y + 1, z + 0.5, loc.getYaw(), loc.getPitch());
        target.teleport(top);
        if (target.equals(staff)) {
            MessageUtil.sendMessage(staff, "§aTeleported to the top.");
        } else {
            MessageUtil.sendMessage(staff, "§aTeleported §e" + target.getName() + " §ato the top.");
            MessageUtil.sendMessage(target, "§aYou were teleported to the top by §e" + staff.getName());
        }
    }

    private void handleJump(Player staff, String[] args) {
        // /jump is usually self only (jump to where you're looking)
        Player target = (args.length > 0) ? resolveOptionalTarget(staff, args, 0) : staff;
        if (target == null) return;
        plugin.recordLastLocation(target);
        // Ray trace to block in line of sight (up to 200 blocks)
        org.bukkit.block.Block targetBlock = target.getTargetBlockExact(200);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            MessageUtil.sendMessage(staff, "§cNo block in sight to jump to.");
            return;
        }
        Location dest = targetBlock.getLocation().add(0.5, 1.0, 0.5);
        dest.setYaw(target.getLocation().getYaw());
        dest.setPitch(target.getLocation().getPitch());
        target.teleport(dest);
        if (target.equals(staff)) {
            MessageUtil.sendMessage(staff, "§aJumped to block.");
        } else {
            MessageUtil.sendMessage(staff, "§aJumped §e" + target.getName() + "§a to looked-at block.");
            MessageUtil.sendMessage(target, "§aYou were jumped to a location by §e" + staff.getName());
        }
    }

    private void handleBack(Player staff, String[] args) {
        Player target = resolveOptionalTarget(staff, args, 0);
        if (target == null) return;
        Location last = plugin.getLastLocation(target.getUniqueId());
        if (last == null) {
            MessageUtil.sendMessage(staff, "§cNo previous location recorded for " + (target.equals(staff) ? "you" : target.getName()) + ".");
            return;
        }
        // Record current so double /back can return
        plugin.recordLastLocation(target);
        target.teleport(last);
        if (target.equals(staff)) {
            MessageUtil.sendMessage(staff, "§aReturned to previous location.");
        } else {
            MessageUtil.sendMessage(staff, "§aSent §e" + target.getName() + " §aback to their previous location.");
            MessageUtil.sendMessage(target, "§aYou were sent back by §e" + staff.getName());
        }
    }

    private void handleSudo(Player staff, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(staff, "§cUsage: /sudo <player> <command...>   or   /sudo <player> c:<chat message>");
            return;
        }
        Player sudoTarget = Bukkit.getPlayer(args[0]);
        if (sudoTarget == null) {
            MessageUtil.sendMessage(staff, "§cPlayer not found or offline.");
            return;
        }
        String full = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        plugin.getLogger().info("[SUDO] " + staff.getName() + " executed as " + sudoTarget.getName() + ": " + full);

        if (full.toLowerCase().startsWith("c:") || full.toLowerCase().startsWith("chat:")) {
            int idx = full.indexOf(':');
            String chatMsg = (idx >= 0) ? full.substring(idx + 1).trim() : full;
            sudoTarget.chat(chatMsg);
            MessageUtil.sendMessage(staff, "§aMade §e" + sudoTarget.getName() + " §achat: §f" + chatMsg);
            return;
        }

        String cmd = full;
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        boolean success = sudoTarget.performCommand(cmd);
        MessageUtil.sendMessage(staff, "§aSudo executed §e/" + cmd + " §aas §e" + sudoTarget.getName() +
                (success ? "" : " §7(possible failure or no output)"));
    }

    private void handleTime(Player staff, String[] args) {
        if (args.length == 0) {
            MessageUtil.sendMessage(staff, "§cUsage: /time <day|night|dawn|dusk|ticks> [player]");
            return;
        }
        String spec = args[0].toLowerCase();
        long ticks;
        switch (spec) {
            case "day", "dawn" -> ticks = 1000L;
            case "noon" -> ticks = 6000L;
            case "night", "dusk" -> ticks = 13000L;
            case "midnight" -> ticks = 18000L;
            default -> {
                try {
                    ticks = Long.parseLong(spec);
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(staff, "§cInvalid time. Use day/night/dawn/dusk or tick number (0-24000).");
                    return;
                }
            }
        }
        Player timeTarget = resolveOptionalTarget(staff, args, 1);
        if (timeTarget == null && args.length > 1) return;
        World w = (timeTarget != null ? timeTarget.getWorld() : staff.getWorld());
        final World targetWorld = w;
        final long finalTicks = ticks;
        final String who = (timeTarget != null && !timeTarget.equals(staff)) ? " for §e" + timeTarget.getName() : "";

        plugin.getThreadSafety().runOnMainThread(() -> {
            targetWorld.setTime(finalTicks);
            MessageUtil.sendMessage(staff, "§aSet time to §e" + finalTicks + who + " §7(in " + targetWorld.getName() + ")");
        });
    }

    private void handleWeather(Player staff, String[] args) {
        if (args.length == 0) {
            MessageUtil.sendMessage(staff, "§cUsage: /weather <clear|sun|rain|storm> [player]");
            return;
        }
        String spec = args[0].toLowerCase();
        boolean storm;
        boolean thunder = false;
        switch (spec) {
            case "clear", "sun" -> storm = false;
            case "rain" -> { storm = true; thunder = false; }
            case "storm", "thunder" -> { storm = true; thunder = true; }
            default -> {
                MessageUtil.sendMessage(staff, "§cInvalid weather. Use clear/sun/rain/storm.");
                return;
            }
        }
        Player weatherTarget = resolveOptionalTarget(staff, args, 1);
        if (weatherTarget == null && args.length > 1) return;
        World w = (weatherTarget != null ? weatherTarget.getWorld() : staff.getWorld());
        final World targetWorld = w;
        final boolean finalStorm = storm;
        final boolean finalThunder = thunder;
        final String who = (weatherTarget != null && !weatherTarget.equals(staff)) ? " for §e" + weatherTarget.getName() : "";
        final String state = storm ? (thunder ? "storm" : "rain") : "clear";

        plugin.getThreadSafety().runOnMainThread(() -> {
            targetWorld.setStorm(finalStorm);
            targetWorld.setThundering(finalThunder);
            MessageUtil.sendMessage(staff, "§aSet weather to §e" + state + who + " §7(in " + targetWorld.getName() + ")");
        });
    }

    // ==================== STAFF PANEL / HELP (EssentialsX-style "staff command panel") ====================

    private void sendStaffHelp(Player player) {
        MessageUtil.sendMessage(player, "§6§l=== Staff Commands (EssentialsX-style) ===");
        MessageUtil.sendMessage(player, "§eMovement: §7/top [p] /jump [p] /back [p] /tp <p> /tphere <p> /tppos");
        MessageUtil.sendMessage(player, "§eModeration: §7/vanish /fly [p] /god [p] /heal [p] /speed <1-10> [p]");
        MessageUtil.sendMessage(player, "§eGamemode: §7/gm <mode> [p] /gmc [p] /gms [p] /gma [p] /gmsp [p]");
        MessageUtil.sendMessage(player, "§ePunish: §7/ban <p> [r] /tempban <p> <min> [r] /kick <p> [r] /mute <p> [r] /unmute <p> /warn <p> <r>");
        MessageUtil.sendMessage(player, "§eInv Tools: §7/invsee <p> /endersee <p> /clear [p] /repair [p] /freeze <p>");
        MessageUtil.sendMessage(player, "§eTools: §7/sudo <p> <cmd|c:msg> /socialspy /time <..> [p] /weather <..> [p] /setspawn");
        MessageUtil.sendMessage(player, "§eComms: §7/sc <msg> /broadcast <msg> /announce <msg>");
        MessageUtil.sendMessage(player, "§ePanel: §7/staff §7(opens GUI)   Full admin: §e/isadmin");
        MessageUtil.sendMessage(player, "§7Tip: /staff opens interactive GUI panel. Most support [player] targets.");
    }

    // ==================== GAMEMODE + TARGET HELPERS (EssentialsX-style) ====================

    private void setGamemode(Player staff, Player target, GameMode mode) {
        if (target == null) {
            MessageUtil.sendMessage(staff, "§cTarget player not found or offline.");
            return;
        }
        target.setGameMode(mode);
        String modeName = mode.name();
        if (target.equals(staff)) {
            MessageUtil.sendMessage(staff, "§aGamemode set to §e" + modeName);
        } else {
            MessageUtil.sendMessage(staff, "§aSet §e" + target.getName() + "§a's gamemode to §e" + modeName);
            MessageUtil.sendMessage(target, "§aYour gamemode was set to §e" + modeName + " §aby " + staff.getName());
        }
    }

    /**
     * Resolves an optional player target from args[argIndex].
     * If no such arg, returns the staff (self).
     * If arg present but player not online/found, sends error and returns null.
     */
    private Player resolveOptionalTarget(Player staff, String[] args, int argIndex) {
        if (args.length > argIndex) {
            Player t = Bukkit.getPlayer(args[argIndex]);
            if (t == null) {
                MessageUtil.sendMessage(staff, "§cPlayer '" + args[argIndex] + "' not found or offline.");
                return null;
            }
            return t;
        }
        return staff;
    }

    // ==================== TAB COMPLETER ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("foliasb.staff")) {
            return List.of();
        }
        String cmd = command.getName().toLowerCase();
        List<String> completions = new ArrayList<>();

        // Gamemode modes for /gm and /gamemode (first arg)
        if ((cmd.equals("gm") || cmd.equals("gamemode")) && args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> modes = List.of("survival", "creative", "adventure", "spectator", "s", "c", "a", "sp", "0", "1", "2", "3");
            for (String m : modes) {
                if (m.startsWith(prefix)) completions.add(m);
            }
            return completions;
        }

        // Numeric hints for /speed
        if (cmd.equals("speed") && args.length == 1) {
            List<String> speeds = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
            for (String s : speeds) {
                if (s.startsWith(args[0])) completions.add(s);
            }
            return completions;
        }

        // Player name suggestions for commands that accept optional [player] target
        // gm/gamemode player is arg index 1; most others index 0
        boolean suggestPlayer = switch (cmd) {
            case "gm", "gamemode", "gmc", "gms", "gma", "gmsp",
                 "fly", "god", "speed", "heal", "clear", "repair",
                 "tp", "tphere", "invsee", "endersee", "freeze",
                 "ban", "tempban", "kick", "mute", "unmute", "warn" -> true;
            default -> false;
        };
        if (suggestPlayer) {
            int playerIdx = (cmd.equals("gm") || cmd.equals("gamemode") || cmd.equals("speed")) ? 1 : 0;
            if (args.length == playerIdx + 1) {
                String prefix = args[playerIdx].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }
        }

        return completions;
    }
}
