package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.SuggestionManager;
import com.thenerdcj.suggest.Suggestion;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /suggest command - Player feature / alteration poll system.
 *
 * Full setup:
 * - submit ideas
 * - prevent duplicate spam via normalized similarity check
 * - voting / poll mechanics
 * - data persisted to suggestions.json (Grok readable) + suggestions.md log
 * - Configurable cooldown + length limits
 * - Folia native scheduling (GlobalRegionScheduler via ThreadSafety for UI/feedback)
 */
public class SuggestCommand implements CommandExecutor, TabCompleter {

    private final FoliaSkyblock plugin;
    private final SuggestionManager manager;

    public SuggestCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.manager = plugin.getSuggestionManager() != null
                ? plugin.getSuggestionManager()
                : new SuggestionManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "§cOnly players can use /suggest.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list":
            case "top":
            case "view":
                showList(player, args.length > 1 ? args[1] : "1");
                break;

            case "vote":
            case "upvote":
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "§cUsage: /suggest vote <id>");
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    manager.vote(player, id);
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(player, "§cID must be a number. Example: /suggest vote 3");
                }
                break;

            case "info":
            case "show":
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "§cUsage: /suggest info <id>");
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    showInfo(player, id);
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(player, "§cInvalid ID.");
                }
                break;

            default:
                // Treat everything as suggestion text
                String text = String.join(" ", args);
                submit(player, text);
                break;
        }
        return true;
    }

    private void submit(Player player, String text) {
        if (!manager.canSubmit(player)) {
            long remaining = manager.getRemainingCooldownMillis(player);
            long minutes = (remaining / 1000) / 60;
            MessageUtil.sendMessage(player, "§cYou must wait " + Math.max(1, minutes) + " more minute(s) before suggesting again.");
            return;
        }

        manager.submitSuggestion(player, text).thenAccept(id -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                if (id > 0) {
                    Suggestion existing = manager.getById(id);
                    if (existing != null && !existing.submitterUuid.equals(player.getUniqueId())) {
                        // It was a duplicate
                        MessageUtil.sendMessage(player, "§eA very similar suggestion already exists:");
                        MessageUtil.sendMessage(player, "§6#" + id + " §f(" + existing.votes + " votes) §7" + truncate(existing.text, 70));
                        MessageUtil.sendMessage(player, "§aVote for it using §f/suggest vote " + id);
                    }
                    // Otherwise the manager already sent the "thank you" message
                }
            });
        });
    }

    private void showList(Player player, String pageStr) {
        int page;
        try {
            page = Math.max(1, Integer.parseInt(pageStr));
        } catch (Exception e) {
            page = 1;
        }

        List<Suggestion> top = manager.getTopSuggestions(50);
        if (top.isEmpty()) {
            MessageUtil.sendMessage(player, "§7No suggestions yet! Be the first with §f/suggest <your idea>");
            return;
        }

        int perPage = 8;
        int totalPages = (int) Math.ceil(top.size() / (double) perPage);
        page = Math.min(page, totalPages);

        MessageUtil.sendMessage(player, "§6§lPlayer Suggestions §7(Poll) §8- Page " + page + "/" + totalPages);
        MessageUtil.sendMessage(player, "§7Vote with §f/suggest vote <id> §7| Submit with §f/suggest <text>");

        int start = (page - 1) * perPage;
        for (int i = start; i < Math.min(start + perPage, top.size()); i++) {
            Suggestion s = top.get(i);
            String shortText = truncate(s.text, 55);
            MessageUtil.sendMessage(player,
                "§e#" + s.id + " §f(" + s.votes + " votes) §7- " + shortText + " §8by " + s.submitterName);
        }

        if (page < totalPages) {
            MessageUtil.sendMessage(player, "§7Next page: §f/suggest list " + (page + 1));
        }
    }

    private void showInfo(Player player, int id) {
        Suggestion s = manager.getById(id);
        if (s == null) {
            MessageUtil.sendMessage(player, "§cSuggestion #" + id + " does not exist.");
            return;
        }

        MessageUtil.sendMessage(player, "§6Suggestion #" + s.id);
        MessageUtil.sendMessage(player, "§f" + s.text);
        MessageUtil.sendMessage(player, "§7Submitted by: §e" + s.submitterName + " §8on " + java.time.Instant.ofEpochMilli(s.timestamp));
        MessageUtil.sendMessage(player, "§7Votes: §a" + s.votes);
        MessageUtil.sendMessage(player, "§7Use §f/suggest vote " + id + " §7to support this idea.");
    }

    private void sendHelp(Player player) {
        MessageUtil.sendMessage(player, "§6§l/suggest §7- Community Feature Poll");
        MessageUtil.sendMessage(player, "§e/suggest <your idea here> §7- Submit a new feature or change suggestion");
        MessageUtil.sendMessage(player, "§e/suggest list §7- View top suggestions by votes");
        MessageUtil.sendMessage(player, "§e/suggest vote <id> §7- Vote for an idea you like");
        MessageUtil.sendMessage(player, "§e/suggest info <id> §7- See full details of a suggestion");
        MessageUtil.sendMessage(player, "§7Ideas with the most votes will be noticed by the team!");
    }

    private String truncate(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = Arrays.asList("list", "vote", "info", "top");
            return options.stream()
                    .filter(o -> o.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("vote") || args[0].equalsIgnoreCase("info"))) {
            // Could return recent IDs but expensive without caching. Empty for now.
            return List.of();
        }
        return List.of();
    }
}
