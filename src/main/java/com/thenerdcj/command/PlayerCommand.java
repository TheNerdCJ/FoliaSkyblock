package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.TPAListGUI;
import com.thenerdcj.manager.TeleportRequestManager;
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
        // In real init, pass or get from plugin
        this.tpaManager = new TeleportRequestManager(plugin); // Ideally plugin.getTpaManager()
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
                // Uses IslandManager
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
                // Accept most recent or specific
                List<UUID> pending = tpaManager.getPendingRequestersFor(player);
                if (pending.isEmpty()) {
                    player.sendMessage("§cNo pending TPA requests.");
                    return true;
                }
                // For simplicity accept first; expand with arg later
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

            // ========== PRIVATE MESSAGES /msg /r with ChatManager spy ==========
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
                StringBuilder msg = new StringBuilder();
                for (int i = 1; i < args.length; i++) msg.append(args[i]).append(" ");
                String message = msg.toString().trim();

                // Send private
                player.sendMessage("§d[You -> " + msgTarget.getName() + "] §f" + message);
                msgTarget.sendMessage("§d[" + player.getName() + " -> You] §f" + message);

                // Staff spy if enabled (integrate with ChatManager or Rank)
                spyOnPrivateMessage(player, msgTarget, message);
                break;

            case "r", "reply":
                // Simple last message reply - expand with map of last conversed
                player.sendMessage("§eReply feature: Use /msg <player> or implement last conversed tracking.");
                // Placeholder - in full impl store last PM partner per player
                break;

            // ========== /list and /online ==========
            case "list", "online", "who":
                player.sendMessage("§6§l=== Online Players (" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + ") ===");
                StringBuilder onlineList = new StringBuilder();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String rankPrefix = plugin.getRankManager() != null ?
                            plugin.getRankManager().getPrefix(p.getUniqueId()) : "§7";
                    onlineList.append(rankPrefix).append(p.getName()).append("§r ");
                }
                player.sendMessage(onlineList.toString());
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

    private void spyOnPrivateMessage(Player sender, Player receiver, String message) {
        // Integrate with ChatManager or add staff spy toggle
        // For now, broadcast to staff with perm (example)
        String spyMsg = "§8[SPY] §7" + sender.getName() + " -> " + receiver.getName() + ": §f" + message;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("folia.staff.spy") || staff.hasPermission("folia.moderator")) {
                // Could check toggle in ChatManager later
                if (staff != sender && staff != receiver) {
                    staff.sendMessage(spyMsg);
                }
            }
        }
    }

    private void sendHelpMenu(Player player) {
        player.sendMessage("§6§l=== FoliaSkyblock Help & Commands ===");
        player.sendMessage("§e/island §7or §e/is §7- Main island commands (create, home, party, upgrades, etc.)");
        player.sendMessage("§e/tpa <player> §7- Request to teleport to player (cooldown applies)");
        player.sendMessage("§e/tpaccept §7/ §ctpdeny §7/ §6/tplist §7- Manage incoming TPA requests (GUI supported)");
        player.sendMessage("§e/tpignore <player> §7- Toggle ignoring TPA from a player");
        player.sendMessage("§e/msg <player> <msg> §7- Private message (staff can spy if enabled)");
        player.sendMessage("§e/r §7- Quick reply to last PM");
        player.sendMessage("§e/list §7or §e/online §7- List online players with ranks");
        player.sendMessage("§e/ah §7or §e/auction §7- Auction House (create/bid/list auctions)");
        player.sendMessage("§e/bal §7- Check your player balance (for chest shops)");
        player.sendMessage("§e/spawn §7- Go to default spawn (0,0 unclaimable)");
        player.sendMessage("§e/home §7- Teleport to your island home");
        player.sendMessage("§e/challenge §7- View challenges & progression");
        player.sendMessage("§e/slayer §7- Slayer quests and bosses");
        player.sendMessage("§e/trade §7- Open trade GUI with other players");
        player.sendMessage("§e/help §7- This menu");
        player.sendMessage("§7Play to Win: Progress through islands, levels, dimensions, and bosses!");
        player.sendMessage("§7Use §b/is help §7for full island subcommands.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (alias.equalsIgnoreCase("tpa") || alias.equalsIgnoreCase("tpignore") ||
                    alias.equalsIgnoreCase("msg") || alias.equalsIgnoreCase("tell")) {
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
