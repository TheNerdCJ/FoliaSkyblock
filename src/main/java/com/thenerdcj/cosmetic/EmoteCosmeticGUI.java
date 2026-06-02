package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmoteCosmeticGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    // Per-player pending state for GUI-driven trigger assignment (join/kill/death etc.)
    // Cleared on use / re-open / quit; lightweight in-memory UI helper.
    private final ConcurrentHashMap<UUID, String> pendingTriggerSelection = new ConcurrentHashMap<>();

    public EmoteCosmeticGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "emote_cosmetic_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        EmoteCosmeticManager manager = plugin.getEmoteCosmeticManager();
        if (manager == null) {
            player.sendMessage("§cEmote system not available.");
            return;
        }

        String title = "§d§lCosmetic Emotes";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<EmoteCosmetic> owned = manager.getOwned(player.getUniqueId());
        int total = EmoteCosmetic.values().length - 1;

        inv.setItem(4, GUIUtils.createItem(Material.TOTEM_OF_UNDYING, "§d§lCosmetic Emotes",
                "§7Fun visual and chat emotes",
                "§7Use /emote <name> or /emote list; configure triggers here or /emote trigger",
                "§7Collection: §a" + owned.size() + " / " + total));

        int slot = 19;
        for (EmoteCosmetic e : EmoteCosmetic.values()) {
            if (slot > 44) break;
            if (e.isNone()) continue;

            boolean has = owned.contains(e);

            ItemStack item = GUIUtils.createItem(Material.TOTEM_OF_UNDYING,
                    (has ? "§e" : "§7") + e.getRarity().getColorCode() + e.getDisplayName(),
                    "§7" + e.getDescription(),
                    has ? "§aOwned - Click to use (/emote " + e.name().toLowerCase() + ")" : "§cLocked");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "EMOTE_" + e.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        // ==================== AUTO-TRIGGERS CONFIG (per-player, persisted via manager) ====================
        // "Emote triggers via GUI" polish: players configure in the same catalog without /emote trigger.
        // Click a trigger slot to select mode, then click an owned emote to assign. Shift-click trigger to clear.
        ItemStack triggersHeader = GUIUtils.createItem(Material.REDSTONE_TORCH, "§d§lAuto-Triggers (Events)",
                "§7Configure emotes that fire automatically",
                "§7on events like join/kill/death/respawn/levelup.",
                "§7Click a slot to assign, Shift+Click to clear.");
        ItemMeta thMeta = triggersHeader.getItemMeta();
        if (thMeta != null) {
            thMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "TRIGGERS_HEADER");
            triggersHeader.setItemMeta(thMeta);
        }
        inv.setItem(46, triggersHeader);

        // Helper to build a trigger config item showing current assignment
        String[] triggerKeys = {"join", "kill", "death"};
        int[] triggerSlots = {47, 48, 49};
        for (int i = 0; i < triggerKeys.length; i++) {
            String key = triggerKeys[i];
            EmoteCosmetic assigned = manager.getTrigger(player.getUniqueId(), key);
            String currentName = (assigned != null && !assigned.isNone()) ? assigned.getDisplayName() : "§7None";
            ItemStack trigItem = GUIUtils.createItem(Material.PAPER,
                    "§d" + key.toUpperCase() + " Trigger",
                    "§7Current: " + currentName,
                    "§eClick to assign selected emote",
                    "§7Shift+Click to clear this trigger");
            ItemMeta tMeta = trigItem.getItemMeta();
            if (tMeta != null) {
                tMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "SET_TRIGGER_" + key);
                trigItem.setItemMeta(tMeta);
            }
            inv.setItem(triggerSlots[i], trigItem);
        }

        // Quick help item
        ItemStack help = GUIUtils.createItem(Material.BOOK, "§bTrigger Help",
                "§7Triggers fire your chosen emote",
                "§7automatically on game events.",
                "§7Use /emote trigger <key> <name> too.",
                "§7Keys: join, kill, death, respawn, levelup");
        inv.setItem(50, help);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§d§lCosmetic Emotes")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        EmoteCosmeticManager manager = plugin.getEmoteCosmeticManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            pendingTriggerSelection.remove(player.getUniqueId());
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        // Handle trigger config items (GUI-driven trigger assignment - "via GUI" polish)
        if (action.startsWith("SET_TRIGGER_")) {
            String key = action.substring("SET_TRIGGER_".length());
            if (player.isSneaking()) {
                // Shift+Click clears the trigger
                manager.setTrigger(player.getUniqueId(), key, EmoteCosmetic.NONE);
                pendingTriggerSelection.remove(player.getUniqueId());
                player.sendMessage("§eCleared auto-trigger for '" + key + "'.");
                open(player); // refresh current assignments
                return;
            }
            // Enter assignment mode
            pendingTriggerSelection.put(player.getUniqueId(), key);
            player.sendMessage("§e§lTrigger Assignment: §fClick any §aowned§f emote in the list to set it as your '" + key + "' trigger.");
            player.sendMessage("§7(Shift-click the trigger slot again to cancel or clear.)");
            // Keep inventory open so user can immediately click an emote below
            return;
        }

        if (action.equals("TRIGGERS_HEADER")) {
            player.sendMessage("§dAuto-Triggers: §7Click a trigger slot (join/kill/death), then click an emote to assign. Shift to clear.");
            return;
        }

        if (action.startsWith("EMOTE_")) {
            try {
                EmoteCosmetic e = EmoteCosmetic.valueOf(action.substring(6));

                // If in pending trigger assignment mode, assign instead of performing
                if (pendingTriggerSelection.containsKey(player.getUniqueId())) {
                    String key = pendingTriggerSelection.remove(player.getUniqueId());
                    if (manager.has(player.getUniqueId(), e)) {
                        manager.setTrigger(player.getUniqueId(), key, e);
                        player.sendMessage("§a§lTrigger assigned! §f" + e.getDisplayName() + " §7will auto-fire for '" + key + "' events.");
                    } else {
                        player.sendMessage("§cYou have not unlocked this emote.");
                    }
                    open(player); // re-open to show the updated trigger current values
                    return;
                }

                // Normal behavior: perform the emote
                if (manager.has(player.getUniqueId(), e)) {
                    manager.performEmote(player, e);
                    player.closeInventory();
                } else {
                    player.sendMessage("§cYou have not unlocked this emote.");
                }
            } catch (Exception ignored) {}
        }
    }
}
