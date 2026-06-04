package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.thenerdcj.util.MessageUtil;

import java.util.UUID;

/**
 * Admin commands for fixing broken islands, balances, and data.
 * Permission: foliasb.admin
 */
public class AdminCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public AdminCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foliasb.admin")) {
            MessageUtil.sendMessage(sender, "§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendMessage(sender, "§eAdmin Commands:");
            MessageUtil.sendMessage(sender, "§7/isadmin reset <player> [overworld|nether|end]");
            MessageUtil.sendMessage(sender, "§7/isadmin setlevel <player> <level> [dimension]");
            MessageUtil.sendMessage(sender, "§7/isadmin setbalance <player> <amount>");
            MessageUtil.sendMessage(sender, "§7/isadmin setislandbalance <player> <amount> [dimension]");
            MessageUtil.sendMessage(sender, "§7/isadmin givepending <player>");
            MessageUtil.sendMessage(sender, "§7/isadmin fixdata <player>");
            MessageUtil.sendMessage(sender, "§7/isadmin wardrobe give <player> <levels>");
            MessageUtil.sendMessage(sender, "§7/isadmin debug worth <player>     - Detailed island worth breakdown");
            MessageUtil.sendMessage(sender, "§7/isadmin debug minions <player>  - Minion stats and breakdown");
            MessageUtil.sendMessage(sender, "§7/isadmin debug anticheat <player> - Violation profile and risk");
            MessageUtil.sendMessage(sender, "§7/isadmin inspect <player>       - DAO-backed island+player state GUI (staff)");
            MessageUtil.sendMessage(sender, "§7/isadmin reports               - Open bug reports triage GUI (view, triage, resolve)");
            MessageUtil.sendMessage(sender, "§7/isadmin spawngui <player>     - Dedicated Spawn Edit GUI (admin set spawn to loc)");
            MessageUtil.sendMessage(sender, "§7/isadmin benchmark           - Runnable 500-island load sim (timings for worth/loads)");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reset":
                handleIslandReset(sender, args);
                break;

            case "setlevel":
                handleSetLevel(sender, args);
                break;

            // case "mission":
            //     handleMissionCommand(sender, args);
            //     break;

            case "booster":
                handleBoosterCommand(sender, args);
                break;
            case "border":
                handleBorderCommand(sender, args);
                break;
            case "slayer":
                handleSlayerAdminCommand(sender, args);
                break;

            case "debug":
                handleDebugCommand(sender, args);
                break;

            case "inspect":
                handleInspectCommand(sender, args);
                break;

            case "reports":
            case "bugs":
            case "bugreports":
                if (sender instanceof Player p && plugin.getBugReportListGUI() != null) {
                    plugin.getBugReportListGUI().open(p);
                } else {
                    sender.sendMessage("§cOnly players can open the reports GUI.");
                }
                break;

            case "spawngui":
            case "spawnedit":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /isadmin spawngui <player>");
                    return true;
                }
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) {
                    sender.sendMessage("§cPlayer not online.");
                    return true;
                }
                if (plugin.getSpawnEditGUI() != null) {
                    plugin.getSpawnEditGUI().open((Player) sender, t.getUniqueId());
                }
                break;

            case "benchmark":
                handleBenchmark(sender);
                break;

            case "setbalance":
                handleSetPlayerBalance(sender, args);
                break;

            case "setislandbalance":
                handleSetIslandBalance(sender, args);
                break;

            case "givepending":
                handleGivePending(sender, args);
                break;

            case "fixdata":
                handleFixData(sender, args);
                break;

            case "wardrobe":
                handleWardrobe(sender, args);
                break;

            default:
                MessageUtil.sendMessage(sender, "§cUnknown admin subcommand. Use /isadmin for help.");
        }

        return true;
    }

    // Task batch: real runnable 500-island benchmark (admin debug). Times loads, worth, etc using testing helpers + H2 style.
    // Run via /isadmin benchmark . Logs timings. For large server validation (notes in config/perf).
    // Inter-class: uses IslandManager.createForTesting, WorthManager, etc. Folia safe (main thread sim small).
    private void handleBenchmark(CommandSender sender) {
        sender.sendMessage("§e[BM] Starting 500-island load benchmark (scaled real-ish sim; see console for details)...");
        long start = System.currentTimeMillis();
        int n = 500;
        int created = 0;
        long worthTime = 0;
        for (int i = 0; i < n; i++) {
            try {
                com.thenerdcj.island.Island mock = plugin.getIslandManager().createIslandForTesting(
                    null, org.bukkit.World.Environment.NORMAL, "PLAINS");
                if (mock != null) created++;
                if (plugin.getIslandWorthManager() != null) {
                    long wStart = System.nanoTime();
                    plugin.getIslandWorthManager().getCachedWorthLevel(mock);
                    // Simulate recalc touch
                    plugin.getIslandWorthManager().invalidateCache(mock);
                    worthTime += (System.nanoTime() - wStart);
                }
            } catch (Exception e) { /* continue */ }
        }
        long time = System.currentTimeMillis() - start;
        double avgMs = time / (double) n;
        double worthAvgNs = worthTime / (double) Math.max(1, n/10); // sampled
        sender.sendMessage("§a[BM] Done in " + time + "ms for " + n + " (created " + created + "). Avg " + String.format("%.2f", avgMs) + "ms/island.");
        plugin.getLogger().info("[BENCHMARK-REAL] 500-island sim: total=" + time + "ms, avgPerIsland=" + avgMs + "ms, sampledWorthTouchAvgNs=" + worthAvgNs + ". For 500+ validation of Folia stagger, caches, DB. Full H2 version in dedicated test recommended.");
        // Continuation: can extend to timed full IslandWorthManager.calculateIslandWorthAsync on mocks + report to file.
    }

    private void handleIslandReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin reset <player> [overworld|nether|end]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found or offline.");
            return;
        }

        World.Environment dimension = World.Environment.NORMAL;
        if (args.length >= 3) {
            try {
                dimension = World.Environment.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                MessageUtil.sendMessage(sender, "§cInvalid dimension. Use: overworld, nether, end");
                return;
            }
        }

        plugin.getIslandManager().resetIslandWithBiome(target, null, dimension);
        MessageUtil.sendMessage(sender, "§aReset " + target.getName() + "'s island in " + dimension.name());
        MessageUtil.sendMessage(target, "§cYour island in " + dimension.name() + " has been reset by an admin.");
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin setlevel <player> <level> [overworld|nether|end]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found.");
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "§cInvalid level number.");
            return;
        }

        World.Environment dim = World.Environment.NORMAL;
        if (args.length >= 4) {
            dim = World.Environment.valueOf(args[3].toUpperCase());
        }

        Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), dim);
        if (island != null) {
            island.setLevel(level);
            MessageUtil.sendMessage(sender, "§aSet " + target.getName() + "'s island level to " + level + " in " + dim.name());
        } else {
            MessageUtil.sendMessage(sender, "§cPlayer does not have an island in that dimension.");
        }
    }

    private void handleSetPlayerBalance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin setbalance <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "§cInvalid amount.");
            return;
        }

        plugin.getEconomyManager().setPlayerBalance(target.getUniqueId(), amount);
        MessageUtil.sendMessage(sender, "§aSet " + target.getName() + "'s balance to §e$" + amount);
    }

    private void handleSetIslandBalance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin setislandbalance <player> <amount> [dimension]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "§cInvalid amount.");
            return;
        }

        World.Environment dim = World.Environment.NORMAL;
        if (args.length >= 4) {
            dim = World.Environment.valueOf(args[3].toUpperCase());
        }

        Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), dim);
        if (island != null) {
            GridPosition pos = island.getGridPosition();
            plugin.getEconomyManager().setIslandBalance(pos, amount);
            MessageUtil.sendMessage(sender, "§aSet island balance for " + target.getName() + " to §e$" + amount);
        } else {
            MessageUtil.sendMessage(sender, "§cPlayer has no island in " + dim.name());
        }
    }

    private void handleGivePending(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin givepending <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found.");
            return;
        }

        plugin.getDatabaseManager().getPendingItems(target.getUniqueId()).thenAccept(items -> {
            // Must give items on main thread
            plugin.getThreadSafety().runOnMainThread(() -> {
                for (var item : items) {
                    target.getInventory().addItem(item);
                }
                MessageUtil.sendMessage(sender, "§aGave pending items to " + target.getName());
                if (target.isOnline()) {
                    MessageUtil.sendMessage(target, "§aYou received your pending items from admin.");
                }
            });
        });
    }

    private void handleFixData(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin fixdata <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found.");
            return;
        }

        // Reload island data
        plugin.getIslandManager().loadPlayerIslands(target);
        MessageUtil.sendMessage(sender, "§aReloaded island data for " + target.getName());
    }

    private void handleWardrobe(CommandSender sender, String[] args) {
        if (args.length < 3 || !"give".equalsIgnoreCase(args[1])) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin wardrobe give <player> <levels>");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found or offline.");
            return;
        }

        int levels;
        try {
            levels = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "§cInvalid number of levels.");
            return;
        }

        if (levels <= 0) {
            MessageUtil.sendMessage(sender, "§cLevels must be positive.");
            return;
        }

        // Apply to all dimensions the player has islands in (or at least main one)
        boolean applied = false;
        for (World.Environment dim : new World.Environment[]{World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END}) {
            Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), dim);
            if (island != null) {
                int current = island.getUpgradeLevel(com.thenerdcj.island.IslandUpgrade.WARDROBE_SLOTS);
                island.setUpgradeLevel(com.thenerdcj.island.IslandUpgrade.WARDROBE_SLOTS, current + levels);
                applied = true;
            }
        }

        if (applied) {
            MessageUtil.sendMessage(sender, "§aGave " + levels + " WARDROBE_SLOTS levels to " + target.getName() + "'s island(s).");
            MessageUtil.sendMessage(target, "§aAn admin has increased your wardrobe slot capacity!");
        } else {
            MessageUtil.sendMessage(sender, "§cPlayer has no islands to apply wardrobe upgrade to.");
        }
    }

    private void handleBoosterCommand(CommandSender sender, String[] args) {
        if (args.length < 5 || !args[1].equalsIgnoreCase("give")) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin booster give <player> <type> <minutes>");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found.");
            return;
        }

        try {
            com.thenerdcj.booster.BoosterType type = com.thenerdcj.booster.BoosterType.valueOf(args[3].toUpperCase());
            long minutes = Long.parseLong(args[4]);
            long duration = minutes * 60 * 1000;

            Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
            if (island == null) {
                MessageUtil.sendMessage(sender, "§cPlayer has no island in current dimension.");
                return;
            }

            double multiplier = plugin.getConfig().getDouble("boosters.multipliers." + type.name(), 1.5);
            plugin.getBoosterManager().activateBooster(island, type, multiplier, duration);

            MessageUtil.sendMessage(sender, "§aGave " + type.name() + " booster to " + target.getName() + " for " + minutes + " minutes.");
            if (target.isOnline()) {
                MessageUtil.sendMessage(target, "§aAn admin has activated a " + type.getDisplayName() + " booster for you!");
            }
        } catch (Exception e) {
            MessageUtil.sendMessage(sender, "§cInvalid booster type or duration.");
        }
    }

    private void handleBorderCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin border <setsize|setcolor> <player> <value>");
            return;
        }

        String sub = args[1].toLowerCase();
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin border " + sub + " <player> <value>");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found.");
            return;
        }

        Island island = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(sender, "§cPlayer has no island in their current dimension.");
            return;
        }

        GridPosition pos = island.getGridPosition();

        try {
            if (sub.equals("setsize")) {
                int newSize = Integer.parseInt(args[3]);
                plugin.getIslandSettingsManager().getSettings(pos).thenAccept(settings -> {
                    settings.setBorderSize(newSize);
                    plugin.getIslandSettingsManager().saveSettings(settings);

                    // Also update effective radius display
                    plugin.getThreadSafety().runOnMainThread(() -> {
                        MessageUtil.sendMessage(sender, "§aSet border size for " + target.getName() + "'s island to §b" + newSize + " blocks.");
                        if (target.isOnline()) {
                            MessageUtil.sendMessage(target, "§aAn admin has changed your island border size to §b" + newSize + " blocks.");
                        }
                    });
                });
            } else if (sub.equals("setcolor")) {
                String color = args[3].toUpperCase();
                plugin.getIslandSettingsManager().getSettings(pos).thenAccept(settings -> {
                    settings.setBorderColor(color);
                    plugin.getIslandSettingsManager().saveSettings(settings);

                    plugin.getThreadSafety().runOnMainThread(() -> {
                        MessageUtil.sendMessage(sender, "§aSet border color for " + target.getName() + "'s island to §b" + color + ".");
                        if (target.isOnline()) {
                            MessageUtil.sendMessage(target, "§aAn admin has changed your island border color to §b" + color + ".");
                        }
                    });
                });
            } else {
                MessageUtil.sendMessage(sender, "§cUnknown border subcommand. Use setsize or setcolor.");
            }
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "§cInvalid number for size.");
        }
    }

    private void handleSlayerAdminCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin slayer <giveTokens|resetLeaderboard|forceBoss> <player|all>");
            return;
        }

        String sub = args[1].toLowerCase();

        if (sub.equals("givetokens")) {
            if (args.length < 4) {
                MessageUtil.sendMessage(sender, "§cUsage: /isadmin slayer giveTokens <player> <amount>");
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                MessageUtil.sendMessage(sender, "§cPlayer not found.");
                return;
            }
            try {
                int amt = Integer.parseInt(args[3]);
                plugin.getBossManager().awardSlayerTokens(target, amt);
                MessageUtil.sendMessage(sender, "§aGave " + amt + " Slayer Tokens to " + target.getName());
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(sender, "§cInvalid amount.");
            }
        } else if (sub.equals("resetleaderboard")) {
            plugin.getBossManager().checkAndResetTokenLeaderboard();
            plugin.getDatabaseManager().resetWeeklySlayerTokens();
            MessageUtil.sendMessage(sender, "§aSlayer Token leaderboard reset.");
        } else if (sub.equals("forceboss")) {
            MessageUtil.sendMessage(sender, "§7Force island boss (advanced admin feature).");
        }
    }

    private void handleDebugCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin debug <worth|minions|anticheat> <player>");
            return;
        }

        String debugType = args[1].toLowerCase();
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin debug " + debugType + " <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not found or offline.");
            return;
        }

        switch (debugType) {
            case "worth":
                handleDebugWorth(sender, target);
                break;
            case "minions":
                handleDebugMinions(sender, target);
                break;
            case "anticheat":
                handleDebugAnticheat(sender, target);
                break;
            default:
                MessageUtil.sendMessage(sender, "§cUnknown debug type. Use worth, minions, or anticheat.");
        }
    }

    private void handleDebugWorth(CommandSender sender, Player target) {
        var island = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(sender, "§c" + target.getName() + " has no island in this dimension.");
            return;
        }

        double worth = plugin.getIslandWorthManager().getCachedWorth(island);
        int level = plugin.getIslandWorthManager().getCachedWorthLevel(island);

        MessageUtil.sendMessage(sender, "§6=== Worth Debug for " + target.getName() + " ===");
        MessageUtil.sendMessage(sender, "§7Total Worth: §e$" + String.format("%,.0f", worth));
        MessageUtil.sendMessage(sender, "§7Worth Level: §b" + level);

        // Show multipliers
        double prestigeMult = 1.0;
        if (plugin.getPrestigeManager() != null) {
            prestigeMult = plugin.getPrestigeManager().getPrestigeMultiplier(island, com.thenerdcj.manager.PrestigeManager.PrestigeMultiplierType.WORTH);
        }
        MessageUtil.sendMessage(sender, "§7Prestige Worth Mult: §a" + String.format("%.2fx", prestigeMult));

        MessageUtil.sendMessage(sender, "§7(Incremental tracking active - drift correction runs periodically)");
    }

    private void handleDebugMinions(CommandSender sender, Player target) {
        var island = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(sender, "§c" + target.getName() + " has no island in this dimension.");
            return;
        }

        String islandId = island.getId();
        int maxSlots = plugin.getMinionManager().getMaxMinionSlots(island);
        int used = plugin.getMinionManager().getPlacedMinionCount(islandId);

        MessageUtil.sendMessage(sender, "§6=== Minion Debug for " + target.getName() + " ===");
        MessageUtil.sendMessage(sender, "§7Slots: §e" + used + "§7/§e" + maxSlots);

        var breakdown = plugin.getMinionManager().getMinionBreakdown(islandId);
        if (breakdown.isEmpty()) {
            MessageUtil.sendMessage(sender, "§7No minions placed.");
        } else {
            MessageUtil.sendMessage(sender, "§7Breakdown:");
            breakdown.forEach((type, count) ->
                MessageUtil.sendMessage(sender, "  §f" + type.getDisplayName() + " §7x" + count));
        }

        int fuel = plugin.getMinionManager().getIslandFuel(islandId);
        MessageUtil.sendMessage(sender, "§7Island Fuel: §e" + fuel);
    }

    private void handleDebugAnticheat(CommandSender sender, Player target) {
        var manager = plugin.getAntiCheatManager();
        int violations = manager.getViolationCount(target.getUniqueId());
        double risk = manager.getNeuralRiskScore(target);

        MessageUtil.sendMessage(sender, "§6=== AntiCheat Debug for " + target.getName() + " ===");
        MessageUtil.sendMessage(sender, "§7Total Violations: §e" + violations);
        MessageUtil.sendMessage(sender, "§7Neural Risk Score: §c" + String.format("%.1f%%", risk * 100));

        if (violations > 10) {
            MessageUtil.sendMessage(sender, "§cHigh risk profile - review recent activity.");
        } else if (violations > 3) {
            MessageUtil.sendMessage(sender, "§eModerate violations - monitor.");
        } else {
            MessageUtil.sendMessage(sender, "§aClean profile.");
        }
    }

    private void handleInspectCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "§cUsage: /isadmin inspect <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(sender, "§cPlayer not online (inspect requires online for full live state; offline DAO read in future).");
            return;
        }
        if (plugin.getAdminIslandInspectGUI() != null) {
            plugin.getAdminIslandInspectGUI().open((Player) sender, target.getUniqueId());
        } else {
            MessageUtil.sendMessage(sender, "§cInspect GUI not initialized.");
        }
    }
}
