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

    // Per-staff pagination + selection (matching AdminIslandInspectGUI / modern GUI patterns)
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> selectedReport = new ConcurrentHashMap<>();
    // Simple pending note state (staff clicks "Add Note" then types in chat or uses a follow-up command)
    private final Map<UUID, Integer> pendingNoteReport = new ConcurrentHashMap<>();

    public BugReportListGUI(FoliaSkyblock plugin) {
        this(plugin, true);
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
        if (!staff.hasPermission("foliasb.staff") && !staff.hasPermission("foliasb.admin")) {
            MessageUtil.sendMessage(staff, "§cYou do not have permission to view bug reports.");
            return;
        }

        inventory = Bukkit.createInventory(this, 54, MessageUtil.legacy("§c§lBug Reports §7(Loading...)"));

        // Placeholder while loading
        ItemStack loading = GUIUtils.createItem(Material.CLOCK, "§eLoading reports...");
        inventory.setItem(22, loading);

        staff.openInventory(inventory);

        // Async load a healthy number for pagination (large servers can have many reports)
        reportManager.getOpenReports(200).thenAccept(reports -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                if (staff.getOpenInventory().getTopInventory().getHolder() != this) return;
                populate(staff, reports);
            });
        });
    }

    private void populate(Player staff, List<BugReport> reports) {
        if (inventory == null) return;

        int page = playerPages.getOrDefault(staff.getUniqueId(), 0);
        int itemsPerPage = 45;
        int total = reports.size();
        int totalPages = Math.max(1, (total + itemsPerPage - 1) / itemsPerPage);
        if (page >= totalPages) page = totalPages - 1;
        playerPages.put(staff.getUniqueId(), page);

        inventory.clear();

        int count = reports.size();
        inventory = Bukkit.createInventory(this, 54, MessageUtil.legacy("§c§lBug Reports §7(" + count + " open) §8p" + (page + 1) + "/" + totalPages));

        int start = page * itemsPerPage;
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

        // Info / selected slot (slot 49 is standard close area, we use 49 for detail like other GUIs)
        int selectedId = selectedReport.getOrDefault(staff.getUniqueId(), -1);
        if (selectedId > 0) {
            for (BugReport r : reports) {
                if (r.getId() == selectedId) {
                    ItemStack detail = buildDetailItem(r);
                    inventory.setItem(49, detail);
                    break;
                }
            }
        } else {
            ItemStack info = GUIUtils.createItem(Material.BOOK, "§6Select a report",
                    "§7Click a report above to view full details", "§7and use the status buttons below.");
            inventory.setItem(49, info);
        }

        // Standard navigation (45/49/53 pattern used across modern GUIs)
        if (page > 0) {
            ItemStack prev = GUIUtils.createItem(Material.ARROW, "§e« Previous Page",
                    "§7Page " + page + " of " + totalPages);
            attachActionPDC(prev, "page:prev");
            inventory.setItem(45, prev);
        } else {
            ItemStack refresh = GUIUtils.createItem(Material.EMERALD, "§aRefresh",
                    "§7Reload open reports list");
            inventory.setItem(45, refresh);
        }

        ItemStack close = GUIUtils.createItem(Material.BARRIER, "§cClose");
        inventory.setItem(49, close);  // note: we may overwrite with detail above; close is still reachable via action

        if (page < totalPages - 1) {
            ItemStack next = GUIUtils.createItem(Material.ARROW, "§eNext Page »",
                    "§7Page " + (page + 2) + " of " + totalPages);
            attachActionPDC(next, "page:next");
            inventory.setItem(53, next);
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
            staff.closeInventory();
            return;
        }

        // Pagination nav
        if (action != null && action.equals("page:prev")) {
            int p = playerPages.getOrDefault(staff.getUniqueId(), 0);
            playerPages.put(staff.getUniqueId(), Math.max(0, p - 1));
            staff.closeInventory();
            new BugReportListGUI(plugin).open(staff);
            return;
        }
        if (action != null && action.equals("page:next")) {
            int p = playerPages.getOrDefault(staff.getUniqueId(), 0);
            playerPages.put(staff.getUniqueId(), p + 1);
            staff.closeInventory();
            new BugReportListGUI(plugin).open(staff);
            return;
        }

        if (clicked.getType() == Material.EMERALD) {
            // Refresh
            playerPages.remove(staff.getUniqueId());
            pendingNoteReport.remove(staff.getUniqueId());
            staff.closeInventory();
            new BugReportListGUI(plugin).open(staff);
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
                            new BugReportListGUI(plugin).open(staff);
                        });
                    }
                });
            return;
        }

        // Report item clicked -> select
        if (reportId != null && reportId > 0) {
            selectedReport.put(staff.getUniqueId(), reportId);
            reportManager.recordStaffReportSelection(staff.getUniqueId(), reportId); // for /bug note convenience
            staff.closeInventory();
            new BugReportListGUI(plugin).open(staff);
        }
    }
}
