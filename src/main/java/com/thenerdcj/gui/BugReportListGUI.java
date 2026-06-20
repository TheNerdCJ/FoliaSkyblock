package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.BugReport;
import com.thenerdcj.manager.BugReportManager;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff GUI for viewing and triaging bug reports.
 *
 * Features:
 * - Async load of open reports (via BugReportManager/DAO).
 * - Category-colored items with reporter + preview.
 * - Click report to "select" it (shows full description in info slot).
 * - Status action buttons (bottom bar) that carry reportId + desired status via PDC.
 * - Refresh + Close controls.
 * - Modernized: GUIUtils + MessageUtil.legacy + PDC (report_id, action).
 *
 * Opened via /isadmin reports (or staff /reports).
 * Folia-safe: loads are async, updates use ThreadSafety where player mutation occurs.
 */
public class BugReportListGUI implements InventoryHolder, Listener {

    private final FoliaSkyblock plugin;
    private final BugReportManager reportManager;
    private Inventory inventory;

    private final NamespacedKey REPORT_ID_KEY;
    private final NamespacedKey ACTION_KEY;

    // Per-staff selection (matching AdminIslandInspectGUI / modern GUI patterns)
    private final Map<UUID, Integer> selectedReport = new ConcurrentHashMap<>();
    // Simple pending note state (staff clicks "Add Note" then types in chat or uses a follow-up command)
    private final Map<UUID, Integer> pendingNoteReport = new ConcurrentHashMap<>();
    // History mode: when true we load *all* reports (incl. FIXED/DUPLICATE/WONTFIX) for persistence/history view
    private final Map<UUID, Boolean> showAllReports = new ConcurrentHashMap<>();

    // Scroll offset for continuous scrolling instead of pure page jumps (items to skip)
    private final Map<UUID, Integer> scrollOffsets = new ConcurrentHashMap<>();

    // View filter modes for the reports list
    private enum ViewMode { OPEN_ONLY, ALL, CLOSED_ONLY }

    private final Map<UUID, ViewMode> viewModes = new ConcurrentHashMap<>();

    public BugReportListGUI(FoliaSkyblock plugin) {
        this(plugin, false);  // Do not auto-register. Registration happens once for the shared instance in FoliaSkyblock.
    }

    public BugReportListGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.reportManager = plugin.getBugReportManager() != null ? plugin.getBugReportManager() : new BugReportManager(plugin);
        this.REPORT_ID_KEY = new NamespacedKey(plugin, "report_id");
        this.ACTION_KEY = new NamespacedKey(plugin, "action");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player staff) {
        boolean canView = staff.hasPermission("foliasb.staff") || staff.hasPermission("foliasb.admin");
        if (!canView && plugin.getRankManager() != null) {
            String rankId = plugin.getRankManager().getPlayerRankId(staff.getUniqueId());
            if (plugin.getRankManager().isStaffRank(rankId)) {
                canView = true;
            }
        }
        if (!canView) {
            MessageUtil.sendMessage(staff, "§cYou do not have permission to view bug reports.");
            return;
        }

        inventory = Bukkit.createInventory(this, 54, MessageUtil.legacy("§c§lBug Reports §7(Loading...)"));

        // Placeholder while loading
        ItemStack loading = GUIUtils.createItem(Material.CLOCK, "§eLoading reports...");
        inventory.setItem(22, loading);

        staff.openInventory(inventory);

        // Async load based on current view filter (supports scrollable history + closed only)
        ViewMode mode = getViewMode(staff);
        CompletableFuture<List<BugReport>> loadFut;
        if (mode == ViewMode.CLOSED_ONLY) {
            loadFut = reportManager.getClosedReports(200);
        } else if (mode == ViewMode.ALL) {
            loadFut = reportManager.getAllReports(200);
        } else {
            loadFut = reportManager.getOpenReports(200);
        }
        loadFut.thenAccept(reports -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                if (staff.getOpenInventory().getTopInventory().getHolder() != this) return;
                populate(staff, reports);
            });
        });
    }

    private void populate(Player staff, List<BugReport> reports) {
        if (inventory == null) return;

        // Use scroll offset for smooth scrolling (step of 9 items = 1 row for nice feel)
        int itemsPerPage = 45;
        int scrollStep = 9;
        int offset = scrollOffsets.getOrDefault(staff.getUniqueId(), 0);
        int total = reports.size();
        if (offset > total - 1) offset = Math.max(0, total - itemsPerPage);
        if (offset < 0) offset = 0;
        scrollOffsets.put(staff.getUniqueId(), offset);

        inventory.clear();

        int count = reports.size();
        ViewMode mode = getViewMode(staff);
        String viewLabel;
        switch (mode) {
            case CLOSED_ONLY -> viewLabel = "Closed Only";
            case ALL -> viewLabel = "All / History";
            default -> viewLabel = count + " open";
        }
        int first = Math.min(offset + 1, Math.max(1, total));
        int last = Math.min(offset + itemsPerPage, total);
        String scrollInfo = total > 0 ? " §8(" + first + "-" + last + "/" + total + ")" : "";
        inventory = Bukkit.createInventory(this, 54, MessageUtil.legacy("§c§lBug Reports §7(" + viewLabel + ")" + scrollInfo));

        int start = offset;
        int end = Math.min(start + itemsPerPage, total);

        int slot = 0;
        for (int i = start; i < end; i++) {
            BugReport r = reports.get(i);

            Material icon = switch (r.getCategory()) {
                case EXPLOIT -> Material.TNT;
                case SUGGESTION -> Material.BOOK;
                case BUG -> Material.REDSTONE;
                default -> Material.PAPER;
            };

            String title = r.getCategory().getColor() + "#" + r.getId() + " §f" + r.getReporterName();
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Status: " + r.getStatus().getColor() + r.getStatus().getDisplayName());
            lore.add("§7When: §f" + r.getFormattedCreated());
            lore.add("§7Category: " + r.getCategory().getColor() + r.getCategory().getDisplayName());
            lore.add("");
            lore.add("§f" + r.getShortDescription(40));
            lore.add("");
            lore.add("§eLeft-click §7→ Select / View details");
            lore.add("§cRight-click §7→ Quick mark FIXED");

            ItemStack item = GUIUtils.createItem(icon, title, lore);
            attachReportPDC(item, r.getId());
            inventory.setItem(slot++, item);
        }

        if (total == 0) {
            ItemStack empty = GUIUtils.createItem(Material.BARRIER, "§7No reports in this view",
                    "§7Try the filter on the bottom row to switch between Open / All / Closed.");
            inventory.setItem(22, empty);
        }

        // Close button first (bottom row)
        ItemStack close = GUIUtils.createItem(Material.BARRIER, "§cClose");
        inventory.setItem(49, close);

        // Info / selected slot (detail overwrites close when a report is selected)
        int selectedId = selectedReport.getOrDefault(staff.getUniqueId(), -1);
        if (selectedId > 0) {
            for (BugReport r : reports) {
                if (r.getId() == selectedId) {
                    ItemStack detail = buildDetailItem(r);
                    inventory.setItem(49, detail);
                    break;
                }
            }
        }

        // Scrollable navigation (bottom row)
        // Scroll up / refresh at 45 (left edge of controls)
        if (offset > 0) {
            ItemStack scrollUp = GUIUtils.createItem(Material.ARROW, "§e▲ Scroll Up",
                    "§7Scroll up " + scrollStep + " items");
            attachActionPDC(scrollUp, "scroll:up");
            inventory.setItem(45, scrollUp);
        } else {
            ItemStack refresh = GUIUtils.createItem(Material.EMERALD, "§aRefresh",
                    "§7Reload current view");
            inventory.setItem(45, refresh);
        }

        // Filter toggle on bottom row (slot 53). 
        // Scroll down (if possible) will take slot 53 to show scrolling capability.
        ViewMode currentMode = getViewMode(staff);
        String filterName;
        Material filterMat;
        String filterLore;
        switch (currentMode) {
            case CLOSED_ONLY -> {
                filterName = "§cClosed Only";
                filterMat = Material.REDSTONE_BLOCK;
                filterLore = "§7Showing only FIXED / DUPLICATE / WONTFIX";
            }
            case ALL -> {
                filterName = "§bAll Reports (History)";
                filterMat = Material.WRITABLE_BOOK;
                filterLore = "§7Showing everything (open + closed)";
            }
            default -> {
                filterName = "§aOpen / Active";
                filterMat = Material.BOOK;
                filterLore = "§7Showing OPEN and INVESTIGATING only";
            }
        }
        ItemStack filterToggle = GUIUtils.createItem(filterMat, filterName,
                filterLore,
                "§7Click to cycle filter (Open → All → Closed)"
        );
        attachActionPDC(filterToggle, "filter:cycle");
        inventory.setItem(53, filterToggle);

        // Scroll down (overrides filter at 53 when there is more to scroll — shows scrolling capability)
        if (offset + itemsPerPage < total) {
            ItemStack scrollDown = GUIUtils.createItem(Material.ARROW, "§e▼ Scroll Down",
                    "§7Scroll down " + scrollStep + " items");
            attachActionPDC(scrollDown, "scroll:down");
            inventory.setItem(53, scrollDown);
        }

        // Status action buttons (bottom row)
        addStatusButton(46, Material.LIME_WOOL, "§aMark FIXED", BugReport.Status.FIXED);
        addStatusButton(47, Material.YELLOW_WOOL, "§eMark INVESTIGATING", BugReport.Status.INVESTIGATING);
        addStatusButton(48, Material.ORANGE_WOOL, "§6Mark DUPLICATE", BugReport.Status.DUPLICATE);
        addStatusButton(50, Material.RED_WOOL, "§cMark WONTFIX", BugReport.Status.WONTFIX);
        addStatusButton(51, Material.LIGHT_BLUE_WOOL, "§bRe-OPEN", BugReport.Status.OPEN);

        // Small "add note" helper on the status row
        ItemStack noteBtn = GUIUtils.createItem(Material.WRITABLE_BOOK, "§6Add Note",
                "§7Click then type a note in chat", "§7(for the currently selected report)");
        attachActionPDC(noteBtn, "action:add_note");
        inventory.setItem(52, noteBtn);

        staff.openInventory(inventory);
    }

    private void addStatusButton(int slot, Material mat, String name, BugReport.Status status) {
        if (inventory == null) return;
        ItemStack btn = GUIUtils.createItem(mat, name, "§7Applies to selected report");
        attachActionPDC(btn, "status:" + status.name());
        inventory.setItem(slot, btn);
    }

    private ItemStack buildDetailItem(BugReport r) {
        Material mat = switch (r.getCategory()) {
            case EXPLOIT -> Material.TNT;
            case SUGGESTION -> Material.WRITABLE_BOOK;
            case BUG -> Material.REDSTONE_TORCH;
            default -> Material.PAPER;
        };
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Reporter: §f" + r.getReporterName() + " §8(" + r.getReporterUuid().toString().substring(0, 8) + ")");
        lore.add("§7Status: " + r.getStatus().getColor() + r.getStatus().getDisplayName());
        lore.add("§7Submitted: §f" + new java.util.Date(r.getCreatedAt()));
        lore.add("");
        // Split description into lines for lore
        String desc = r.getDescription();
        for (String line : desc.split("\n")) {
            if (line.length() > 45) {
                // naive wrap
                for (int i = 0; i < line.length(); i += 45) {
                    lore.add("§f" + line.substring(i, Math.min(i + 45, line.length())));
                }
            } else {
                lore.add("§f" + line);
            }
        }
        if (r.getStaffNotes() != null && !r.getStaffNotes().isEmpty()) {
            lore.add("");
            lore.add("§6Staff notes: §f" + r.getStaffNotes());
        }
        lore.add("");
        lore.add("§7Use status buttons below to triage.");

        return GUIUtils.createItem(mat, "§6Report #" + r.getId() + " Details", lore);
    }

    private void attachReportPDC(ItemStack item, int reportId) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(REPORT_ID_KEY, PersistentDataType.INTEGER, reportId);
            item.setItemMeta(meta);
        }
    }

    private void attachActionPDC(ItemStack item, String actionValue) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, actionValue);
            item.setItemMeta(meta);
        }
    }

    private Integer getReportIdFromItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(REPORT_ID_KEY, PersistentDataType.INTEGER);
    }

    private String getActionFromItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
    }

    private ViewMode getViewMode(Player staff) {
        return viewModes.getOrDefault(staff.getUniqueId(), ViewMode.OPEN_ONLY);
    }

    private void cycleViewMode(Player staff) {
        ViewMode current = getViewMode(staff);
        ViewMode next = switch (current) {
            case OPEN_ONLY -> ViewMode.ALL;
            case ALL -> ViewMode.CLOSED_ONLY;
            case CLOSED_ONLY -> ViewMode.OPEN_ONLY;
        };
        viewModes.put(staff.getUniqueId(), next);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BugReportListGUI)) return;

        event.setCancelled(true);

        Player staff = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Integer reportId = getReportIdFromItem(clicked);
        String action = getActionFromItem(clicked);

        if (clicked.getType() == Material.BARRIER) {
            pendingNoteReport.remove(staff.getUniqueId());
            // keep scroll + view mode so user returns to same place/filter
            staff.closeInventory();
            return;
        }

        // Scroll actions (smooth scrolling over the report list)
        if (action != null && action.equals("scroll:up")) {
            int currentOffset = scrollOffsets.getOrDefault(staff.getUniqueId(), 0);
            int newOffset = Math.max(0, currentOffset - 9); // scroll one row
            scrollOffsets.put(staff.getUniqueId(), newOffset);
            staff.closeInventory();
            plugin.getBugReportListGUI().open(staff);
            return;
        }
        if (action != null && action.equals("scroll:down")) {
            int currentOffset = scrollOffsets.getOrDefault(staff.getUniqueId(), 0);
            scrollOffsets.put(staff.getUniqueId(), currentOffset + 9);
            staff.closeInventory();
            plugin.getBugReportListGUI().open(staff);
            return;
        }

        // Cycle filter: Open Only → All (History) → Closed Only
        if (action != null && action.equals("filter:cycle")) {
            cycleViewMode(staff);
            scrollOffsets.remove(staff.getUniqueId()); // reset scroll when changing filter
            selectedReport.remove(staff.getUniqueId());
            pendingNoteReport.remove(staff.getUniqueId());
            staff.closeInventory();
            plugin.getBugReportListGUI().open(staff);
            return;
        }

        if (clicked.getType() == Material.EMERALD) {
            // Refresh (keeps current filter + scroll position is reset for fresh view)
            scrollOffsets.remove(staff.getUniqueId());
            pendingNoteReport.remove(staff.getUniqueId());
            staff.closeInventory();
            plugin.getBugReportListGUI().open(staff);
            return;
        }

        // Add note (selects report and tells staff to use chat or /bug note)
        if (action != null && action.equals("action:add_note")) {
            int target = selectedReport.getOrDefault(staff.getUniqueId(), reportId != null ? reportId : -1);
            if (target <= 0) {
                MessageUtil.sendMessage(staff, "§cSelect a report first (click it), then click Add Note.");
                return;
            }
            pendingNoteReport.put(staff.getUniqueId(), target);
            MessageUtil.sendMessage(staff, "§6Note mode active for report #" + target + ". Type your note in chat or use §f/bug note <text>§6. It will be attached on next status change.");
            staff.closeInventory();
            return;
        }

        // Status action button — supports pending note from "Add Note"
        if (action != null && action.startsWith("status:")) {
            String statusName = action.substring("status:".length());
            BugReport.Status targetStatus = BugReport.Status.fromString(statusName);

            int targetReport = selectedReport.getOrDefault(staff.getUniqueId(), reportId != null ? reportId : -1);
            if (targetReport <= 0) {
                MessageUtil.sendMessage(staff, "§cNo report selected. Click a report first to select it.");
                return;
            }

            // If staff previously clicked "Add Note" and then used /bug note <text>, the manager will have it.
            String note = reportManager.consumePendingNote(targetReport);
            pendingNoteReport.remove(staff.getUniqueId()); // clear any selection marker

            if (note != null && !note.isEmpty()) {
                MessageUtil.sendMessage(staff, "§aNote attached to report #" + targetReport + ".");
            }

            reportManager.resolveReport(targetReport, targetStatus, staff, note)
                .thenAccept(success -> {
                    if (success) {
                        plugin.getThreadSafety().runOnMainThread(() -> {
                            staff.closeInventory();
                            plugin.getBugReportListGUI().open(staff);
                        });
                    }
                });
            return;
        }

        // Report item clicked:
        //   - Right-click: Quick mark FIXED (as advertised in the item lore)
        //   - Left-click:  Select for details + status button actions (and /bug note)
        if (reportId != null && reportId > 0) {
            if (event.isRightClick()) {
                // Quick mark as FIXED (no need to pre-select)
                String note = reportManager.consumePendingNote(reportId);
                reportManager.resolveReport(reportId, BugReport.Status.FIXED, staff, note)
                    .thenAccept(success -> {
                        if (success) {
                            plugin.getThreadSafety().runOnMainThread(() -> {
                                staff.closeInventory();
                                plugin.getBugReportListGUI().open(staff);
                            });
                        }
                    });
                return;
            }

            // Left click = select
            selectedReport.put(staff.getUniqueId(), reportId);
            reportManager.recordStaffReportSelection(staff.getUniqueId(), reportId); // for /bug note convenience
            staff.closeInventory();
            plugin.getBugReportListGUI().open(staff);
        }
    }
}
