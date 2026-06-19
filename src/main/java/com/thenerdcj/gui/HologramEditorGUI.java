package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.hologram.Hologram;
import com.thenerdcj.hologram.HologramData;
import com.thenerdcj.hologram.HologramManager;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI editor for hologram lines.
 * Layout: "two wide"
 * - Left vertical column (slots 0,9,18,...): one item per visible line. Unused slots are fillers.
 * - Right vertical column (col 8): scroll/add/info/close controls.
 * Left click line = prompt chat edit.
 * Right click line = remove + respawn (auto re-stacks spacing from base Y).
 * Scroll when >6 lines.
 * All scheduling uses Folia API via ThreadSafety (GlobalRegionScheduler / RegionScheduler).
 */
public class HologramEditorGUI implements Listener, InventoryHolder {

    private final FoliaSkyblock plugin;
    private final HologramManager hologramManager;
    private final NamespacedKey actionKey;
    private final NamespacedKey lineIndexKey;

    private Inventory inventory; // last opened (holder reference only)

    // Per-player state (singleton GUI instance)
    private final Map<UUID, String> editingHologram = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> scrollOffsets = new ConcurrentHashMap<>();
    private final Map<UUID, LineEditContext> pendingEdits = new ConcurrentHashMap<>();

    private static final int VISIBLE_LINES = 6;
    private static final int[] LINE_SLOTS = {0, 9, 18, 27, 36, 45};
    private static final int ADD_SLOT = 8;
    private static final int UP_SLOT = 17;
    private static final int INFO_SLOT = 26;
    private static final int DOWN_SLOT = 35;
    private static final int CLOSE_SLOT = 53;

    public HologramEditorGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.hologramManager = plugin.getHologramManager();
        this.actionKey = new NamespacedKey(plugin, "holo_editor_action");
        this.lineIndexKey = new NamespacedKey(plugin, "holo_line_index");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, String holoName) {
        if (player == null || holoName == null) return;

        Hologram holo = hologramManager.getHologramByName(holoName);
        if (holo == null) {
            player.sendMessage("§cHologram '" + holoName + "' not found or not loaded.");
            return;
        }

        UUID uuid = player.getUniqueId();
        editingHologram.put(uuid, holoName);

        HologramData data = holo.getData();
        List<String> lines = data.getLines();

        int offset = scrollOffsets.getOrDefault(uuid, 0);
        int maxOffset = Math.max(0, lines.size() - VISIBLE_LINES);
        if (offset > maxOffset) offset = maxOffset;
        if (offset < 0) offset = 0;
        scrollOffsets.put(uuid, offset);

        String title = "§6§lEdit Hologram: §e" + holoName;
        inventory = Bukkit.createInventory(this, 54, MessageUtil.legacy(title));

        // Fill background with filler
        ItemStack filler = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Left vertical: lines or blanks/fillers
        for (int i = 0; i < VISIBLE_LINES; i++) {
            int slot = LINE_SLOTS[i];
            int lineIdx = offset + i;

            if (lineIdx < lines.size()) {
                String raw = lines.get(lineIdx);
                String preview = raw;
                if (preview.length() > 48) preview = preview.substring(0, 45) + "...";
                preview = preview.replace('&', '§'); // allow colors to render in lore

                List<String> lore = new ArrayList<>();
                lore.add(preview);
                lore.add("");
                lore.add("§aLeft-click §7→ Edit text (type in chat)");
                lore.add("§cRight-click §7→ Remove line (auto re-stack)");

                ItemStack lineItem = GUIUtils.createItem(Material.PAPER, "§eLine " + (lineIdx + 1), lore);
                ItemMeta meta = lineItem.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(lineIndexKey, PersistentDataType.INTEGER, lineIdx);
                    lineItem.setItemMeta(meta);
                }
                inventory.setItem(slot, lineItem);
            } else {
                // Blank/filler for rest of left vertical row
                ItemStack blank = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, "§7(empty)");
                inventory.setItem(slot, blank);
            }
        }

        // Right vertical controls
        // Add line
        ItemStack addBtn = GUIUtils.createItem(Material.WRITABLE_BOOK, "§a§l+ Add Line",
                "§7Appends at the end", "§7You will type the text in chat", "§7Hologram will re-stack automatically");
        setAction(addBtn, "ADD");
        inventory.setItem(ADD_SLOT, addBtn);

        // Scroll up
        if (offset > 0) {
            ItemStack up = GUIUtils.createItem(Material.ARROW, "§a§l▲ Scroll Up", "§7Show earlier lines");
            setAction(up, "UP");
            inventory.setItem(UP_SLOT, up);
        }

        // Info
        int shownEnd = Math.min(offset + VISIBLE_LINES, lines.size());
        ItemStack info = GUIUtils.createItem(Material.BOOK, "§6§lLines §f" + (offset + 1) + "-" + shownEnd + "§6/§f" + lines.size(),
                "§7Name: §e" + holoName,
                "§7Scale: §f" + String.format("%.2f", data.getScale()),
                data.isDynamic() ? "§bDynamic" : "§7Static");
        inventory.setItem(INFO_SLOT, info);

        // Scroll down
        if (shownEnd < lines.size()) {
            ItemStack down = GUIUtils.createItem(Material.ARROW, "§c§l▼ Scroll Down", "§7Show more lines");
            setAction(down, "DOWN");
            inventory.setItem(DOWN_SLOT, down);
        }

        // Close
        ItemStack close = GUIUtils.createItem(Material.BARRIER, "§cClose");
        setAction(close, "CLOSE");
        inventory.setItem(CLOSE_SLOT, close);

        player.openInventory(inventory);
    }

    private void setAction(ItemStack item, String value) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof HologramEditorGUI)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        UUID uuid = player.getUniqueId();
        String holoName = editingHologram.get(uuid);
        if (holoName == null) {
            player.closeInventory();
            return;
        }

        Hologram holo = hologramManager.getHologramByName(holoName);
        if (holo == null) {
            player.sendMessage("§cHologram no longer exists.");
            editingHologram.remove(uuid);
            scrollOffsets.remove(uuid);
            player.closeInventory();
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Line item?
        Integer lineIdx = pdc.get(lineIndexKey, PersistentDataType.INTEGER);
        if (lineIdx != null) {
            int idx = lineIdx;
            if (event.isLeftClick()) {
                startChatEdit(player, holoName, idx);
            } else if (event.isRightClick()) {
                removeLineAndRespawn(player, holoName, idx);
            }
            return;
        }

        // Action button?
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "ADD" -> startChatAdd(player, holoName);
            case "UP" -> {
                int off = scrollOffsets.getOrDefault(uuid, 0) - 1;
                scrollOffsets.put(uuid, Math.max(0, off));
                open(player, holoName);
            }
            case "DOWN" -> {
                int off = scrollOffsets.getOrDefault(uuid, 0) + 1;
                scrollOffsets.put(uuid, off);
                open(player, holoName);
            }
            case "CLOSE" -> player.closeInventory();
        }
    }

    private void startChatEdit(Player player, String holoName, int lineIndex) {
        player.closeInventory();
        player.sendMessage("§eType the replacement text for §6line " + (lineIndex + 1) + "§e.");
        player.sendMessage("§7Use & codes for colors (e.g. &aHello). Type §ccancel §7to abort.");
        pendingEdits.put(player.getUniqueId(), new LineEditContext(holoName, lineIndex));
    }

    private void startChatAdd(Player player, String holoName) {
        player.closeInventory();
        player.sendMessage("§eType the text for the §anew line§e to append.");
        player.sendMessage("§7& for colors supported. Type §ccancel §7to abort.");
        pendingEdits.put(player.getUniqueId(), new LineEditContext(holoName, -1));
    }

    private void removeLineAndRespawn(Player player, String holoName, int index) {
        Hologram holo = hologramManager.getHologramByName(holoName);
        if (holo == null) return;

        HologramData data = holo.getData();
        if (index < 0 || index >= data.getLines().size()) return;

        List<String> newLines = new ArrayList<>(data.getLines());
        newLines.remove(index);

        // Update + re-spawn (this re-creates TextDisplays with correct y stacking from base)
        hologramManager.updateLines(data.getId(), newLines).thenAccept(success -> {
            if (success) {
                hologramManager.spawnHologram(data); // Folia region scheduled inside
                player.sendMessage("§aLine removed. Spacing auto-adjusted.");

                // Clamp scroll
                UUID u = player.getUniqueId();
                int off = scrollOffsets.getOrDefault(u, 0);
                if (off > 0 && off >= newLines.size()) {
                    off = Math.max(0, newLines.size() - VISIBLE_LINES);
                    scrollOffsets.put(u, off);
                }

                plugin.getThreadSafety().runOnMainThread(() -> open(player, holoName));
            } else {
                player.sendMessage("§cFailed to save changes.");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        LineEditContext ctx = pendingEdits.remove(player.getUniqueId());
        if (ctx == null) return;

        event.setCancelled(true);

        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7Cancelled.");
            plugin.getThreadSafety().runOnMainThread(() -> open(player, ctx.holoName));
            return;
        }

        // Perform actual update on main thread (Folia GlobalRegionScheduler)
        plugin.getThreadSafety().runOnMainThread(() -> {
            Hologram holo = hologramManager.getHologramByName(ctx.holoName);
            if (holo == null) {
                player.sendMessage("§cHologram no longer exists.");
                return;
            }

            HologramData data = holo.getData();
            List<String> newLines = new ArrayList<>(data.getLines());

            if (ctx.lineIndex < 0) {
                // append
                newLines.add(message);
                player.sendMessage("§aLine added at end.");
            } else if (ctx.lineIndex < newLines.size()) {
                newLines.set(ctx.lineIndex, message);
                player.sendMessage("§aLine " + (ctx.lineIndex + 1) + " updated.");
            } else {
                player.sendMessage("§cLine index out of range.");
                open(player, ctx.holoName);
                return;
            }

            hologramManager.updateLines(data.getId(), newLines).thenAccept(success -> {
                if (success) {
                    hologramManager.spawnHologram(data);
                    plugin.getThreadSafety().runOnMainThread(() -> open(player, ctx.holoName));
                } else {
                    player.sendMessage("§cFailed to persist line change.");
                }
            });
        });
    }

    private static class LineEditContext {
        final String holoName;
        final int lineIndex; // -1 = add/append

        LineEditContext(String holoName, int lineIndex) {
            this.holoName = holoName;
            this.lineIndex = lineIndex;
        }
    }

    /** Cleanup for a player (called externally if needed). */
    public void cleanupPlayer(UUID uuid) {
        editingHologram.remove(uuid);
        scrollOffsets.remove(uuid);
        pendingEdits.remove(uuid);
    }
}
