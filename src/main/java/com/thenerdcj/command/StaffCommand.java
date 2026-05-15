package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.Punishment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;
    private final ConcurrentHashMap<UUID, Boolean> vanishedPlayers = new ConcurrentHashMap<>();

    public StaffCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use staff commands.");
            return true;
        }

        if (!player.hasPermission("foliasb.staff")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            // === Moderation Tools ===
            case "vanish":
                toggleVanish(player);
                break;

            case "fly":
                player.setAllowFlight(!player.getAllowFlight());
                player.sendMessage("§aFlight " + (player.getAllowFlight() ? "§2enabled" : "§cdisabled") + "§a.");
                break;

            case "god":
                boolean god = !player.isInvulnerable();
                player.setInvulnerable(god);
                player.sendMessage("§aGod mode " + (god ? "§2enabled" : "§cdisabled") + "§a.");
                break;

            case "heal":
                if (args.length == 0) {
                    healPlayer(player, player);
                } else {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target != null) healPlayer(player, target);
                    else player.sendMessage("§cPlayer not found.");
                }
                break;

            case "speed":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /speed <1-10>");
                    return true;
                }
                try {
                    float speed = Math.max(0.1f, Math.min(1f, Float.parseFloat(args[0]) / 10f));
                    player.setFlySpeed(speed);
                    player.setWalkSpeed(speed);
                    player.sendMessage("§aSpeed set to §e" + (int)(speed * 10));
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid speed value.");
                }
                break;

            case "gm", "gamemode":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /gm <0|1|2|3>");
                    return true;
                }
                GameMode mode = switch (args[0].toLowerCase()) {
                    case "0", "s", "survival" -> GameMode.SURVIVAL;
                    case "1", "c", "creative" -> GameMode.CREATIVE;
                    case "2", "a", "adventure" -> GameMode.ADVENTURE;
                    case "3", "sp", "spectator" -> GameMode.SPECTATOR;
                    default -> null;
                };
                if (mode != null) {
                    player.setGameMode(mode);
                    player.sendMessage("§aGamemode set to §e" + mode.name());
                } else {
                    player.sendMessage("§cInvalid gamemode.");
                }
                break;

            // === Teleport Tools ===
            case "tp":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /tp <player>");
                    return true;
                }
                Player tpTarget = Bukkit.getPlayer(args[0]);
                if (tpTarget != null) {
                    player.teleport(tpTarget);
                    player.sendMessage("§aTeleported to §e" + tpTarget.getName());
                } else player.sendMessage("§cPlayer not found.");
                break;

            case "tphere":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /tphere <player>");
                    return true;
                }
                Player hereTarget = Bukkit.getPlayer(args[0]);
                if (hereTarget != null) {
                    hereTarget.teleport(player);
                    player.sendMessage("§aTeleported §e" + hereTarget.getName() + " §ato you.");
                } else player.sendMessage("§cPlayer not found.");
                break;

            case "tppos":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /tppos <x> <y> <z>");
                    return true;
                }
                try {
                    double x = Double.parseDouble(args[0]);
                    double y = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    player.teleport(player.getWorld().getBlockAt((int)x, (int)y, (int)z).getLocation().add(0.5, 0, 0.5));
                    player.sendMessage("§aTeleported to coordinates.");
                } catch (Exception e) {
                    player.sendMessage("§cInvalid coordinates.");
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
                player.getInventory().clear();
                player.sendMessage("§aYour inventory has been cleared.");
                break;

            case "repair":
                handleRepair(player);
                break;

            default:
                player.sendMessage("§cUnknown staff command.");
        }
        return true;
    }

    // ==================== HELPER METHODS ====================

    private void toggleVanish(Player player) {
        boolean isVanished = vanishedPlayers.getOrDefault(player.getUniqueId(), false);
        if (!isVanished) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("foliasb.staff")) p.hidePlayer(plugin, player);
            }
            vanishedPlayers.put(player.getUniqueId(), true);
            plugin.getAntiCheatManager().setStaffBypass(player.getUniqueId(), true);
            player.sendMessage("§aYou are now vanished.");
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, player);
            }
            vanishedPlayers.remove(player.getUniqueId());
            plugin.getAntiCheatManager().setStaffBypass(player.getUniqueId(), false);
            player.sendMessage("§cYou are no longer vanished.");
        }
    }

    private void healPlayer(Player staff, Player target) {
        target.setHealth(20);
        target.setFoodLevel(20);
        target.setSaturation(20);
        target.sendMessage("§aYou have been healed by staff.");
        staff.sendMessage("§aHealed §e" + target.getName());
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
        target.kick(Component.text(kickMsg));

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
        target.kick(Component.text("§c" + reason));
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

    private void handleRepair(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage("§cYou must hold an item to repair.");
            return;
        }
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(0);
            item.setItemMeta(damageable);
            player.sendMessage("§aItem repaired.");
        } else {
            player.sendMessage("§cThis item cannot be repaired.");
        }
    }
}
