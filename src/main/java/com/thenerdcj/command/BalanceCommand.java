package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.manager.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final FoliaSkyblock plugin;
    private final EconomyManager economyManager;

    // Cooldown tracking
    private final Map<UUID, Long> lastBalanceUse = new HashMap<>();
    private final Map<UUID, Long> lastPayUse = new HashMap<>();
    private final Map<UUID, Long> lastRequestUse = new HashMap<>();

    public BalanceCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            if (hasCooldown(player, "balance", lastBalanceUse, "balance")) return true;
            showBalance(player, player.getUniqueId(), player.getName());
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "pay" -> {
                if (hasCooldown(player, "pay", lastPayUse, "pay")) return true;
                handlePay(player, args.length > 1 ? args[1] : null, args.length > 2 ? args[2] : null);
            }
            case "request" -> {
                if (hasCooldown(player, "request", lastRequestUse, "request")) return true;
                handleRequest(player, args.length > 1 ? args[1] : null, args.length > 2 ? args[2] : null);
            }
            case "top" -> {
                if (hasCooldown(player, "balance", lastBalanceUse, "balance")) return true;
                showTopBalances(player);
            }
            case "help" -> sendHelp(player);
            default -> {
                // /bal <player>
                if (hasCooldown(player, "balance", lastBalanceUse, "balance")) return true;
                showBalance(player, null, args[0]);
            }
        }
        return true;
    }

    // ====================== SHOW BALANCE ======================
    private void showBalance(Player viewer, UUID targetUuid, String targetName) {
        UUID uuidToCheck = (targetUuid != null) ? targetUuid : getUuidFromName(targetName);

        if (uuidToCheck == null) {
            viewer.sendMessage("§cPlayer not found!");
            return;
        }

        boolean isSelf = uuidToCheck.equals(viewer.getUniqueId());

        economyManager.getBalance(uuidToCheck).thenAccept(balance -> {
            String displayName = isSelf ? "Your" : (targetName + "'s");
            String formatted = economyManager.getCurrencySymbol() + String.format("%,.2f", balance);
            viewer.sendMessage("§6" + displayName + " balance: §e" + formatted);
        });
    }

    // ====================== TOP BALANCES ======================
    private void showTopBalances(Player player) {
        player.sendMessage("§6=== Top 10 Richest Players ===");

        plugin.getDatabaseManager().getTopBalances(10).thenAccept(topList -> {
            if (topList.isEmpty()) {
                player.sendMessage("§cNo players found.");
                return;
            }

            for (int i = 0; i < topList.size(); i++) {
                DatabaseManager.TopBalanceEntry entry = topList.get(i);
                OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.uuid());
                String name = offline.getName() != null ? offline.getName() : "Unknown Player";
                String formatted = economyManager.getCurrencySymbol() + String.format("%,.2f", entry.balance());

                player.sendMessage("§e#" + (i + 1) + " §f" + name + " §7- " + formatted);
            }
        });
    }

    // ====================== PAY ======================
    private void handlePay(Player player, String targetName, String amountStr) {
        if (targetName == null || amountStr == null) {
            player.sendMessage("§cUsage: /bal pay <player> <amount>");
            return;
        }

        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage("§cYou cannot pay yourself!");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§cPlayer must be online to receive payment.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return;
        }

        if (amount <= 0) {
            player.sendMessage("§cAmount must be positive.");
            return;
        }

        economyManager.removePlayerBalance(player.getUniqueId(), amount).thenAccept(success -> {
            if (success) {
                economyManager.addPlayerBalance(target.getUniqueId(), amount);
                String currency = economyManager.getCurrencySymbol();
                player.sendMessage("§aYou paid §e" + amount + " " + currency + " §ato §e" + target.getName());
                target.sendMessage("§a" + player.getName() + " paid you §e" + amount + " " + currency);
            } else {
                player.sendMessage("§cYou don't have enough " + economyManager.getCurrencySymbol() + "!");
            }
        });
    }

    // ====================== REQUEST ======================
    private void handleRequest(Player player, String targetName, String amountStr) {
        if (targetName == null || amountStr == null) {
            player.sendMessage("§cUsage: /bal request <player> <amount>");
            return;
        }

        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage("§cYou cannot request from yourself!");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§cPlayer must be online.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return;
        }

        String currency = economyManager.getCurrencySymbol();
        target.sendMessage("§e" + player.getName() + " is requesting §6" + amount + " " + currency + "§e from you.");
        target.sendMessage("§eUse §a/bal pay " + player.getName() + " " + amount + " §eto accept.");
        player.sendMessage("§aRequest sent to §e" + target.getName());
    }

    // ====================== COOLDOWN ======================
    private boolean hasCooldown(Player player, String action, Map<UUID, Long> cooldownMap, String configKey) {
        int cooldownSeconds = plugin.getConfig().getInt("cooldowns." + configKey, 5);
        long currentTime = System.currentTimeMillis();
        long lastUse = cooldownMap.getOrDefault(player.getUniqueId(), 0L);

        if (currentTime - lastUse < cooldownSeconds * 1000L) {
            long remaining = (cooldownSeconds * 1000L - (currentTime - lastUse)) / 1000;
            player.sendMessage("§cYou must wait §e" + remaining + "§c seconds before using this again.");
            return true;
        }

        cooldownMap.put(player.getUniqueId(), currentTime);
        return false;
    }

    private UUID getUuidFromName(String name) {
        if (name == null) return null;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() || Bukkit.getPlayerExact(name) != null ? offline.getUniqueId() : null;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== Balance Commands ===");
        player.sendMessage("§e/bal §7- Check your balance");
        player.sendMessage("§e/bal <player> §7- Check another player's balance");
        player.sendMessage("§e/bal top §7- Show top 10 richest players");
        player.sendMessage("§e/bal pay <player> <amount> §7- Pay someone");
        player.sendMessage("§e/bal request <player> <amount> §7- Request money");
        player.sendMessage("§e/bal help §7- Show this help menu");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("pay", "request", "top", "help"));
        }
        return completions;
    }
}