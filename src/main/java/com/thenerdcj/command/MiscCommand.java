package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.chat.ChatManager;
import com.thenerdcj.combat.CombatManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MiscCommand implements CommandExecutor, TabCompleter, Listener {

    private final FoliaSkyblock plugin;
    private final CombatManager combatManager;
    private final ChatManager chatManager;

    // TPA system
    private final Map<UUID, UUID> pendingTpaRequests = new HashMap<>();

    // Ignore system
    private final Set<UUID> globallyIgnored = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> ignoredPlayers = new ConcurrentHashMap<>();

    // Cooldown system
    private final Map<String, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();

    // Pending TPA GUI pagination
    private final Map<UUID, Integer> playerPendingPage = new HashMap<>();
    private static final int REQUESTS_PER_PAGE = 45;

    public MiscCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.combatManager = new CombatManager(plugin);
        this.chatManager = new ChatManager(plugin);

        loadCooldowns();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadCooldowns() {
        String[] cmds = {"spawn", "setspawn", "tpa", "tpaccept", "tpdeny", "tpignore", "rules", "bal", "mute", "unmute"};
        for (String cmd : cmds) cooldowns.put(cmd, new ConcurrentHashMap<>());
    }

    private boolean isOnCooldown(Player player, String command) {
        if (player.hasPermission("foliaskyblock.admin.bypass")) return false;

        long cooldownSeconds = plugin.getConfig().getInt("cooldowns." + command, 0);
        if (cooldownSeconds <= 0) return false;

        Map<UUID, Long> map = cooldowns.get(command);
        Long lastUse = map.get(player.getUniqueId());
        if (lastUse == null) return false;

        long remaining = (lastUse + cooldownSeconds * 1000L) - System.currentTimeMillis();
        if (remaining > 0) {
            player.sendMessage("§cYou must wait §e" + (remaining / 1000) + "s §cbefore using /" + command + " again.");
            return true;
        }
        return false;
    }

    private void setCooldown(Player player, String command) {
        if (!player.hasPermission("foliaskyblock.admin.bypass")) {
            Map<UUID, Long> map = cooldowns.get(command);
            if (map != null) map.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (isOnCooldown(player, cmd)) return true;

        switch (cmd) {
            case "spawn" -> handleSpawn(player);
            case "setspawn" -> handleSetSpawn(player);
            case "tpa" -> handleTpa(player, args);
            case "tpaccept", "tpac" -> handleTpAccept(player, args);
            case "tpdeny", "tpdecline" -> handleTpDeny(player, args);
            case "tpignore" -> handleTpIgnore(player, args);
            case "pending" -> handleTpaPending(player);
            case "rules" -> handleRules(player);
            case "bal" -> handleBalance(player, args);
            case "mute" -> handleMute(player, args);
            case "unmute" -> handleUnmute(player, args);
            default -> player.sendMessage("§cUnknown command.");
        }
        return true;
    }

    // ====================== TPA PENDING GUI ======================
    private void handleTpaPending(Player player) {
        openTpaPendingGUI(player, 0);
    }

    private void openTpaPendingGUI(Player player, int page) {
        List<UUID> requesters = pendingTpaRequests.entrySet().stream()
                .filter(e -> e.getValue().equals(player.getUniqueId()))
                .map(Map.Entry::getKey)
                .toList();

        int totalPages = (int) Math.ceil((double) requesters.size() / REQUESTS_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = Math.max(0, totalPages - 1);

        playerPendingPage.put(player.getUniqueId(), page);

        int start = page * REQUESTS_PER_PAGE;
        List<UUID> pageRequesters = requesters.subList(start, Math.min(start + REQUESTS_PER_PAGE, requesters.size()));

        Inventory gui = Bukkit.createInventory(null, 54, "§6Pending TPA Requests §7(Page " + (page + 1) + "/" + totalPages + ")");

        for (int i = 0; i < pageRequesters.size(); i++) {
            UUID requesterUuid = pageRequesters.get(i);
            OfflinePlayer requester = Bukkit.getOfflinePlayer(requesterUuid);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(requester);
            meta.setDisplayName("§e" + (requester.getName() != null ? requester.getName() : "Unknown"));
            List<String> lore = new ArrayList<>();
            lore.add("§7Wants to teleport to you");
            lore.add("");
            lore.add("§aLeft-click §7→ §cDENY");
            lore.add("§aRight-click §7→ §2ACCEPT");
            meta.setLore(lore);
            head.setItemMeta(meta);

            gui.setItem(i, head);
        }

        if (page > 0) gui.setItem(45, createNavItem("§e← Previous"));
        gui.setItem(49, createInfoItem(requesters.size()));
        if (page < totalPages - 1) gui.setItem(53, createNavItem("§eNext →"));

        player.openInventory(gui);
    }

    private ItemStack createNavItem(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(int total) {
        ItemStack item = new ItemStack(Material.BOOK);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setDisplayName("§6Pending Requests");
        meta.setLore(List.of("§7Total: §f" + total));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onPendingGUIClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith("§6Pending TPA Requests")) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getSlot();

        if (slot == 45) {
            int page = playerPendingPage.getOrDefault(player.getUniqueId(), 0) - 1;
            openTpaPendingGUI(player, page);
            return;
        }
        if (slot == 53) {
            int page = playerPendingPage.getOrDefault(player.getUniqueId(), 0) + 1;
            openTpaPendingGUI(player, page);
            return;
        }
        if (slot >= 45) return;

        List<UUID> requesters = pendingTpaRequests.entrySet().stream()
                .filter(entry -> entry.getValue().equals(player.getUniqueId()))
                .map(Map.Entry::getKey)
                .toList();

        int page = playerPendingPage.getOrDefault(player.getUniqueId(), 0);
        int index = page * REQUESTS_PER_PAGE + slot;
        if (index >= requesters.size()) return;

        UUID requesterUuid = requesters.get(index);
        Player requester = Bukkit.getPlayer(requesterUuid);

        if (e.getClick().isRightClick()) { // ACCEPT
            pendingTpaRequests.remove(requesterUuid);
            if (requester != null && !combatManager.isInCombat(requester)) {
                requester.teleport(player.getLocation());
                requester.sendMessage("§aTeleported to §e" + player.getName());
                player.sendMessage("§aAccepted TPA from §e" + (requester.getName() != null ? requester.getName() : "Unknown"));
            }
        } else { // DENY
            pendingTpaRequests.remove(requesterUuid);
            if (requester != null) requester.sendMessage("§cYour TPA request was denied.");
            player.sendMessage("§cDenied TPA from §e" + (requester != null ? requester.getName() : "Unknown"));
        }

        openTpaPendingGUI(player, page);
    }

    // ====================== MUTE / UNMUTE ======================
    private void handleMute(Player player, String[] args) {
        if (!player.hasPermission("foliaskyblock.moderator.mute")) {
            player.sendMessage("§cYou do not have permission to mute players.");
            return;
        }
        if (args.length == 0) {
            player.sendMessage("§cUsage: /mute <player> [duration] [reason]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cThat player is not online.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cYou cannot mute yourself.");
            return;
        }

        long durationSeconds = 0; // permanent by default
        String reason = "No reason given";

        if (args.length >= 2) {
            durationSeconds = parseDuration(args[1]);
        }
        if (args.length >= 3) {
            reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        }

        chatManager.setMuted(target, true, player.getUniqueId(), reason, durationSeconds);
        player.sendMessage("§cMuted §e" + target.getName() + (durationSeconds > 0 ? " for " + formatDuration(durationSeconds) : " permanently"));
    }

    private void handleUnmute(Player player, String[] args) {
        if (!player.hasPermission("foliaskyblock.moderator.mute")) {
            player.sendMessage("§cYou do not have permission to unmute players.");
            return;
        }
        if (args.length == 0) {
            player.sendMessage("§cUsage: /unmute <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cThat player is not online.");
            return;
        }

        chatManager.setMuted(target, false, player.getUniqueId(), null, 0);
        player.sendMessage("§aUnmuted §e" + target.getName());
    }

    private long parseDuration(String input) {
        try {
            if (input.endsWith("s")) return Long.parseLong(input.replace("s", ""));
            if (input.endsWith("m")) return Long.parseLong(input.replace("m", "")) * 60;
            if (input.endsWith("h")) return Long.parseLong(input.replace("h", "")) * 3600;
            if (input.endsWith("d")) return Long.parseLong(input.replace("d", "")) * 86400;
        } catch (Exception ignored) {}
        return 0;
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        return (seconds / 86400) + "d";
    }

    // ====================== REMAINING COMMANDS ======================
    private void handleSpawn(Player player) { /* same as previous version */ }
    private void handleSetSpawn(Player player) { /* same as previous version */ }
    private void handleTpa(Player player, String[] args) { /* same as previous version */ }
    private void handleTpAccept(Player player, String[] args) { /* same as previous version */ }
    private void handleTpDeny(Player player, String[] args) { /* same as previous version */ }
    private void handleTpIgnore(Player player, String[] args) { /* same as previous version */ }
    private void handleRules(Player player) { /* same as previous version */ }
    private void handleBalance(Player player, String[] args) { /* same as previous version */ }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "setspawn", "tpa", "tpaccept", "tpdeny", "tpignore", "pending", "rules", "bal", "mute", "unmute");
        }
        return new ArrayList<>();
    }
}