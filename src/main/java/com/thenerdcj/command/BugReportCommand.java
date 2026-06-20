package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.BugReport;
import com.thenerdcj.gui.BugReportListGUI;
import com.thenerdcj.manager.BugReportManager;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Player command for submitting bug reports / feedback.
 * Usage:
 *   /bug <description of the issue>
 *   /bug bug The skyblock generator is not giving cobble...
 *   /bug exploit Duplication with hoppers on island edge...
 *   /bug suggestion Add a way to ...
 *
 * Staff can also do /bug reports or /bug list to open the triage GUI (same as /isadmin reports).
 */
public class BugReportCommand implements CommandExecutor, TabCompleter {

    private final FoliaSkyblock plugin;
    private final BugReportManager reportManager;

    private static final List<String> CATEGORIES = Arrays.asList("bug", "exploit", "suggestion", "other");

    public BugReportCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.reportManager = (plugin.getBugReportManager() != null)
            ? plugin.getBugReportManager()
            : new BugReportManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "§cOnly players can submit bug reports.");
            return true;
        }

        String cmdName = command.getName().toLowerCase();
        boolean isReportsCmd = "reports".equals(cmdName);

        if (isReportsCmd) {
            // Direct /reports command - staff only (respects foliasb.staff, admin, owner rank with *, and custom staff: true ranks)
            boolean canView = player.hasPermission("foliasb.staff") || player.hasPermission("foliasb.admin");
            if (!canView && plugin.getRankManager() != null) {
                String rankId = plugin.getRankManager().getPlayerRankId(player.getUniqueId());
                if (plugin.getRankManager().isStaffRank(rankId)) {
                    canView = true;
                }
            }
            if (!canView) {
                MessageUtil.sendMessage(player, "§cYou do not have permission to view bug reports.");
                return true;
            }
            plugin.getBugReportListGUI().open(player);
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendMessage(player, "§6Bug Report Help:");
            MessageUtil.sendMessage(player, "§e/bug <description> §7- Submit a bug, exploit, or suggestion");
            MessageUtil.sendMessage(player, "§7Example: §f/bug The nether island reset does not clear mobs");
            MessageUtil.sendMessage(player, "§7You can prefix with category: §f/bug exploit There is a dupe with...");
            MessageUtil.sendMessage(player, "§7Staff: §f/bug reports §7or §f/reports §7to view open reports.");
            return true;
        }

        // Staff shortcut inside /bug reports etc.
        String first = args[0].toLowerCase();
        if ((first.equals("reports") || first.equals("list") || first.equals("view"))) {
            boolean canView = player.hasPermission("foliasb.staff") || player.hasPermission("foliasb.admin");
            if (!canView && plugin.getRankManager() != null) {
                String rankId = plugin.getRankManager().getPlayerRankId(player.getUniqueId());
                if (plugin.getRankManager().isStaffRank(rankId)) {
                    canView = true;
                }
            }
            if (canView) {
                plugin.getBugReportListGUI().open(player);
                return true;
            }
        }

        // Staff: attach note to a selected report from the GUI (used with "Add Note" button)
        if (first.equals("note") && (player.hasPermission("foliasb.staff") || player.hasPermission("foliasb.admin"))) {
            if (args.length < 2) {
                MessageUtil.sendMessage(player, "§cUsage: /bug note <your note text>");
                return true;
            }
            String noteText = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

            // Prefer the last report the staff explicitly selected in the GUI (via "Add Note" click or report click)
            Integer targetReport = reportManager.getLastSelectedReport(player.getUniqueId());
            if (targetReport == null || targetReport <= 0) {
                MessageUtil.sendMessage(player, "§cNo report selected. Open the reports GUI, click a report (or the Add Note button), then run this command.");
                return true;
            }

            reportManager.setPendingNote(targetReport, noteText);
            MessageUtil.sendMessage(player, "§aNote captured for report #" + targetReport + ".");
            MessageUtil.sendMessage(player, "§7Now change the status in the reports GUI — the note will be attached.");
            return true;
        }

        // Player self-service: view own recent reports (works for everyone)
        if ((first.equals("list") || first.equals("my") || first.equals("mine")) || args.length == 0) {
            reportManager.getReportsForPlayer(player.getUniqueId(), 10).thenAccept(ownReports -> {
                if (ownReports.isEmpty()) {
                    MessageUtil.sendMessage(player, "§7You have not submitted any bug reports yet. Use §f/bug <description>§7 to submit one.");
                    return;
                }
                MessageUtil.sendMessage(player, "§6Your recent bug reports:");
                for (BugReport r : ownReports) {
                    String shortDesc = r.getShortDescription(60);
                    MessageUtil.sendMessage(player,
                        "§e#" + r.getId() + " §7[" + r.getCategory().getColor() + r.getCategory().getDisplayName() + "§7] " +
                        r.getStatus().getColor() + r.getStatus().getDisplayName() +
                        " §f" + shortDesc);
                }
                MessageUtil.sendMessage(player, "§7Use §f/bug <text>§7 to submit a new one. Staff can view full details via /reports.");
            });
            return true;
        }

        // Determine category from first word if it matches
        BugReport.Category category = BugReport.Category.BUG;
        int descStart = 0;
        if (CATEGORIES.contains(first)) {
            category = switch (first) {
                case "exploit" -> BugReport.Category.EXPLOIT;
                case "suggestion" -> BugReport.Category.SUGGESTION;
                case "other" -> BugReport.Category.OTHER;
                default -> BugReport.Category.BUG;
            };
            descStart = 1;
        }

        if (args.length <= descStart) {
            MessageUtil.sendMessage(player, "§cPlease provide a description after the optional category.");
            return true;
        }

        String description = String.join(" ", Arrays.copyOfRange(args, descStart, args.length));

        // Delegate to manager (handles cooldown, length, async submit + staff notify)
        reportManager.submitReport(player, category, description);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CATEGORIES.stream()
                    .filter(c -> c.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
