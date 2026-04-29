package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandParty;
import com.thenerdcj.island.IslandRank;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class IslandCommand implements CommandExecutor, TabCompleter, Listener {

    private final FoliaSkyblock plugin;
    private final IslandManager islandManager;

    // Trade GUI pagination
    private final Map<UUID, Integer> playerTradePage = new HashMap<>();

    // Cooldown tracking
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public IslandCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (isOnCooldown(player, sub)) return true;

        switch (sub) {
            case "create" -> handleCreate(player, args.length > 1 ? args[1] : null);
            case "home", "h" -> handleHome(player);
            case "reset" -> handleReset(player, args.length > 1 ? args[1] : null);
            case "delete" -> handleDelete(player);
            case "party" -> handleParty(player, args);
            case "trade" -> openTradeGUI(player);
            case "top" -> handleTop(player);
            case "help" -> sendHelp(player);
            default -> sendHelp(player);
        }
        return true;
    }

    // ====================== CREATE ======================
    private void handleCreate(Player player, String biomeName) {
        islandManager.createIsland(player, biomeName, World.Environment.NORMAL)
                .thenAccept(success -> {
                    if (success) player.sendMessage("§aYour island has been created!");
                    else player.sendMessage("§cYou already have an island!");
                });
    }

    // ====================== HOME ======================
    private void handleHome(Player player) {
        Island island = islandManager.getIsland(player.getUniqueId(), World.Environment.NORMAL);
        if (island == null) {
            player.sendMessage("§cYou don't have an island!");
            return;
        }
        player.teleport(islandManager.getIslandHome(player));
        player.sendMessage("§aTeleported to your island.");
    }

    // ====================== RESET ======================
    private void handleReset(Player player, String biomeName) {
        islandManager.resetIsland(player, biomeName != null ? biomeName : "PLAINS")
                .thenAccept(success -> {
                    if (success) player.sendMessage("§aIsland reset successfully!");
                    else player.sendMessage("§cFailed to reset island.");
                });
    }

    // ====================== DELETE ======================
    private void handleDelete(Player player) {
        islandManager.deleteIsland(player.getUniqueId(), World.Environment.NORMAL)
                .thenAccept(success -> {
                    if (success) player.sendMessage("§aIsland deleted.");
                    else player.sendMessage("§cYou don't have an island to delete.");
                });
    }

    // ====================== PARTY ======================
    private void handleParty(Player player, String[] args) {
        if (args.length < 2) {
            sendPartyHelp(player);
            return;
        }
        String action = args[1].toLowerCase();

        switch (action) {
            case "invite" -> islandManager.inviteToParty(player, args.length > 2 ? args[2] : null);
            case "accept" -> islandManager.acceptPartyInvite(player);
            case "kick" -> islandManager.removeMemberFromIsland(player.getUniqueId(), args.length > 2 ? Bukkit.getOfflinePlayer(args[2]).getUniqueId() : null);
            case "rank" -> {
                if (args.length > 3) {
                    IslandRank newRank = IslandRank.valueOf(args[3].toUpperCase());
                    islandManager.setMemberRank(player.getUniqueId(), Bukkit.getOfflinePlayer(args[2]).getUniqueId(), newRank);
                }
            }
            default -> sendPartyHelp(player);
        }
    }

    private void sendPartyHelp(Player player) {
        player.sendMessage("§6=== Party Commands ===");
        player.sendMessage("§e/is party invite <player>");
        player.sendMessage("§e/is party accept");
        player.sendMessage("§e/is party kick <player>");
        player.sendMessage("§e/is party rank <player> <OWNER|MODERATOR|HELPER|GUEST>");
    }

    // ====================== TRADE GUI ======================
    public void openTradeGUI(Player player) {
        Island island = islandManager.getIsland(player.getUniqueId(), World.Environment.NORMAL);
        if (island == null) {
            player.sendMessage("§cYou need an island to trade!");
            return;
        }

        int page = playerTradePage.getOrDefault(player.getUniqueId(), 0);
        Inventory gui = Bukkit.createInventory(null, 54, "§6Island Trades §7(Page " + (page + 1) + ")");

        File tradesFile = new File(plugin.getDataFolder(), "trades.yml");
        YamlConfiguration tradesConfig = YamlConfiguration.loadConfiguration(tradesFile);
        List<Map<?, ?>> tradeList = tradesConfig.getMapList("trades");

        for (int i = 0; i < 45 && (page * 45 + i) < tradeList.size(); i++) {
            Map<?, ?> trade = tradeList.get(page * 45 + i);
            Object levelObj = trade.get("level");
            int requiredLevel = levelObj instanceof Number ? ((Number) levelObj).intValue() : 1;

            String outputStr = (String) trade.get("output");
            Material material = Material.valueOf(outputStr.split(":")[0]);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§eTrade #" + (page * 45 + i + 1));

            List<String> lore = new ArrayList<>();
            lore.add("§7Input: " + trade.get("input"));
            lore.add("§7Output: " + trade.get("output"));
            lore.add("");

            if (island.getLevel() >= requiredLevel) {
                lore.add("§a✔ Click to trade!");
                item.setType(Material.GREEN_STAINED_GLASS_PANE);
            } else {
                lore.add("§c✖ Locked - Island Level " + requiredLevel + " required");
                item.setType(Material.RED_STAINED_GLASS_PANE);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        // Navigation
        gui.setItem(45, createNavItem(Material.ARROW, "§ePrevious Page"));
        gui.setItem(53, createNavItem(Material.ARROW, "§eNext Page"));

        player.openInventory(gui);
        playerTradePage.put(player.getUniqueId(), page);
    }

    private ItemStack createNavItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onTradeClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith("§6Island Trades")) return;
        e.setCancelled(true);

        Player player = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot >= 45) return; // navigation

        int page = playerTradePage.getOrDefault(player.getUniqueId(), 0);
        int tradeIndex = page * 45 + slot;

        File tradesFile = new File(plugin.getDataFolder(), "trades.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(tradesFile);
        List<Map<?, ?>> tradeList = config.getMapList("trades");

        if (tradeIndex >= tradeList.size()) return;

        Map<?, ?> trade = tradeList.get(tradeIndex);
        Object levelObj = trade.get("level");
        int requiredLevel = levelObj instanceof Number ? ((Number) levelObj).intValue() : 1;

        Island island = islandManager.getIsland(player.getUniqueId(), World.Environment.NORMAL);
        if (island == null || island.getLevel() < requiredLevel) {
            player.sendMessage("§cYou need island level §e" + requiredLevel + " §cto unlock this trade!");
            return;
        }

        islandManager.performTrade(player, tradeIndex);
    }

    // ====================== TOP ======================
    private void handleTop(Player player) {
        plugin.getDatabaseManager().getTopIslands(10).thenAccept(topList -> {
            player.sendMessage("§6=== Top 10 Richest Islands ===");
            for (int i = 0; i < topList.size(); i++) {
                var entry = topList.get(i);
                String name = Bukkit.getOfflinePlayer(entry.ownerUuid()).getName() != null ?
                        Bukkit.getOfflinePlayer(entry.ownerUuid()).getName() : "Unknown";
                player.sendMessage("§e#" + (i + 1) + " §f" + name + " §7- §e" + entry.balance());
            }
        });
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== Island Commands ===");
        player.sendMessage("§e/is create [biome] §7- Create island");
        player.sendMessage("§e/is home §7- Teleport home");
        player.sendMessage("§e/is reset [biome] §7- Reset island");
        player.sendMessage("§e/is delete §7- Delete island");
        player.sendMessage("§e/is party ... §7- Party commands");
        player.sendMessage("§e/is trade §7- Open trade menu");
        player.sendMessage("§e/is top §7- Top islands");
        player.sendMessage("§e/is help §7- This menu");
    }

    // ====================== COOLDOWN ======================
    private boolean isOnCooldown(Player player, String action) {
        int cooldown = plugin.getConfig().getInt("cooldowns.island-" + action, 3);
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - last < cooldown * 1000L) {
            player.sendMessage("§cWait §e" + ((cooldown * 1000L - (now - last)) / 1000) + "§c seconds.");
            return true;
        }
        cooldowns.put(player.getUniqueId(), now);
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        if (args.length == 1) {
            return List.of("create", "home", "reset", "delete", "party", "trade", "top", "help");
        }
        return Collections.emptyList();
    }
}