package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.TPAListGUI;
import com.thenerdcj.manager.TeleportRequestManager;
import com.thenerdcj.rank.RankData; // Important import
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerCommand implements CommandExecutor, TabCompleter {

    private final FoliaSkyblock plugin;
    private final TeleportRequestManager tpaManager;
    private final TPAListGUI tpaListGUI;

    public PlayerCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.tpaManager = plugin.getTeleportRequestManager(); // Better: get from plugin (add getter if needed)
        this.tpaListGUI = new TPAListGUI(plugin, tpaManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "spawn":
                player.teleport(Bukkit.getWorld("world").getSpawnLocation());
                player.sendMessage("§aTeleported to spawn (unclaimable default 0,0).");
                break;

            case "home":
                player.teleport(plugin.getIslandManager().getIslandHome(player));
                player.sendMessage("§aTeleported to your island home.");
                break;

            // ========== EXPANDED TPA SYSTEM ==========
            case "tpa":
                if (args.length < 1) {
                    player.sendMessage("§cUsage: /tpa <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage("§cPlayer not found or offline.");
                    return true;
                }
                tpaManager.sendRequest(player, target);
                break;

            case "tpaccept", "tpac":
                List<UUID> pending = tpaManager.getPendingRequestersFor(player);
                if (pending.isEmpty()) {
                    player.sendMessage("§cNo pending TPA requests.");
                    return true;
                }
                Player firstRequester = Bukkit.getPlayer(pending.get(0));
                if (firstRequester != null) {
                    tpaManager.acceptRequest(player, firstRequester);
                }
                break;

            case "tpdeny", "tpdecline":
                List<UUID> pendingDeny = tpaManager.getPendingRequestersFor(player);
                if (pendingDeny.isEmpty()) {
                    player.sendMessage("§cNo pending requests to deny.");
                    return true;
                }
                Player toDeny = Bukkit.getPlayer(pendingDeny.get(0));
                if (toDeny != null) {
                    tpaManager.denyRequest(player, toDeny);
                }
                break;

            case "tpignore":
                if (args.length < 1) {
                    player.sendMessage("§cUsage: /tpignore <player>");
                    return true;
                }
                Player ignoreTarget = Bukkit.getPlayer(args[0]);
                if (ignoreTarget != null) {
                    tpaManager.toggleIgnore(player, ignoreTarget.getUniqueId());
                } else {
                    player.sendMessage("§cPlayer not found.");
                }
                break;

            case "tplist":
                tpaListGUI.open(player);
                player.sendMessage("§aOpened TPA requests GUI. §7Left-click deny, Right-click accept.");
                break;

            case "pending":
                List<UUID> myPending = tpaManager.getPendingRequestersFor(player);
                player.sendMessage("§6You have §e" + myPending.size() + " §6pending TPA requests. Use §b/tplist§6.");
                break;

            // ========== PRIVATE MESSAGES ==========
            case "msg", "tell", "whisper":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /msg <player> <message>");
                    return true;
                }
                Player msgTarget = Bukkit.getPlayer(args[0]);
                if (msgTarget == null || !msgTarget.isOnline()) {
                    player.sendMessage("§cPlayer not online.");
                    return true;
                }
                StringBuilder msgBuilder = new StringBuilder();
                for (int i = 1; i < args.length; i++) msgBuilder.append(args[i]).append(" ");
                String message = msgBuilder.toString().trim();

                plugin.getChatManager().sendPrivateMessage(player, msgTarget, message);
                break;

            case "r", "reply":
                UUID last = plugin.getChatManager().getLastMessaged(player.getUniqueId());
                if (last == null) {
                    player.sendMessage("§cNo one to reply to. Use /msg first.");
                    return true;
                }
                Player replyTarget = Bukkit.getPlayer(last);
                if (replyTarget == null || !replyTarget.isOnline()) {
                    player.sendMessage("§cThe player you replied to is offline.");
                    return true;
                }
                if (args.length < 1) {
                    player.sendMessage("§cUsage: /r <message>");
                    return true;
                }
                StringBuilder replyMsg = new StringBuilder();
                for (String s : args) replyMsg.append(s).append(" ");
                plugin.getChatManager().sendPrivateMessage(player, replyTarget, replyMsg.toString().trim());
                break;

            // ========== /list and /online (FIXED PREFIX) ==========
            case "list", "online", "who":
                showOnlinePlayers(player);
                break;

            // ========== /help ==========
            case "help", "commands":
                sendHelpMenu(player);
                break;

            case "rules":
                player.sendMessage("§6=== Server Rules ===");
                player.sendMessage("§e1. No griefing on islands");
                player.sendMessage("§e2. Be respectful");
                player.sendMessage("§e3. No cheating (anti-cheat active)");
                player.sendMessage("§e4. Play to Win - progression through gameplay");
                break;

            case "bal":
                plugin.getDatabaseManager().getPlayerBalance(player.getUniqueId())
                        .thenAccept(balance -> player.sendMessage("§aYour balance: §e$" + String.format("%,.2f", balance)));
                break;

            default:
                player.sendMessage("§cUnknown command. Use §b/help §cfor list.");
        }
        return true;
    }

    /** FIXED: Uses existing RankManager methods properly */
    private void showOnlinePlayers(Player viewer) {
        viewer.sendMessage("§6§l=== Online Players (" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + ") ===");

        StringBuilder sb = new StringBuilder();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String prefix = getPlayerPrefix(p.getUniqueId());
            sb.append(prefix).append(p.getName()).append("§r ");
        }
        viewer.sendMessage(sb.toString());
    }

    /** Helper that uses your existing RankManager / RankData */
    private String getPlayerPrefix(UUID uuid) {
        if (plugin.getRankManager() == null) return "§7";

        // Preferred way - full display name
        String name = Bukkit.getPlayer(uuid) != null ? Bukkit.getPlayer(uuid).getName() : "Unknown";
        return plugin.getRankManager().getPlayerDisplayName(uuid, name) + " ";

        // Alternative (just prefix):
        // RankData data = plugin.getRankManager().getPlayerRankData(uuid);
        // return data != null ? org.bukkit.ChatColor.translateAlternateColorCodes('&', data.getPrefix()) : "§7";
    }

    private void sendHelpMenu(Player player) {
        player.sendMessage("§6§l=== FoliaSkyblock Help & Commands ===");
        player.sendMessage("§e/island §7or §e/is §7- Main island commands");
        player.sendMessage("§e/tpa <player> §7- Request teleport");
        player.sendMessage("§e/tpaccept §7/ §ctpdeny §7/ §6/tplist §7- Manage TPA (GUI)");
        player.sendMessage("§e/msg <player> <msg> §7- Private message");
        player.sendMessage("§e/r <msg> §7- Reply to last message");
        player.sendMessage("§e/list §7or §e/online §7- Online players with ranks");
        player.sendMessage("§e/ah §7- Auction House");
        player.sendMessage("§e/bal §7- Player balance");
        player.sendMessage("§e/help §7- This menu");
        player.sendMessage("§7Play to Win: Progress through islands, dimensions & bosses!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (List.of("tpa", "tpignore", "msg", "tell").contains(alias.toLowerCase())) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return names;
            }
        }
        return new ArrayList<>();
    }
}