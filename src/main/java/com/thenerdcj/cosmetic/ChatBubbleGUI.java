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

public class ChatBubbleGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public ChatBubbleGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "chat_bubble_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        ChatBubbleCosmeticManager manager = plugin.getChatBubbleCosmeticManager();
        if (manager == null) {
            player.sendMessage("§cChat Bubbles system not available.");
            return;
        }

        String title = "§d§lChat Bubbles";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<ChatBubbleCosmetic> owned = manager.getOwned(player.getUniqueId());
        ChatBubbleCosmetic current = manager.getActive(player.getUniqueId());
        int total = ChatBubbleCosmetic.values().length - 1;

        inv.setItem(4, GUIUtils.createItem(Material.PAPER, "§d§lChat Bubbles",
                "§7Floating message effects on chat",
                "§7Collection: §a" + owned.size() + " / " + total));

        int slot = 19;
        for (ChatBubbleCosmetic b : ChatBubbleCosmetic.values()) {
            if (slot > 44) break;
            if (b.isNone()) continue;

            boolean has = owned.contains(b);
            boolean isActive = b == current;

            ItemStack item = GUIUtils.createItem(Material.PAPER,
                    (isActive ? "§a§l★ " : has ? "§e" : "§7") + b.getRarity().getColorCode() + b.getDisplayName(),
                    "§7" + b.getDescription(),
                    has ? (isActive ? "§aCurrently Active - Click to re-preview" : "§eClick to equip") : "§cLocked - Unlock via prestige or Slayer Shop");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "CHATBUBBLE_" + b.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        // None / remove button
        ItemStack none = GUIUtils.createItem(Material.BARRIER, "§cRemove Bubble");
        ItemMeta noneMeta = none.getItemMeta();
        if (noneMeta != null) {
            noneMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "CHATBUBBLE_NONE");
            none.setItemMeta(noneMeta);
        }
        inv.setItem(18, none);

        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§d§lChat Bubbles")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        ChatBubbleCosmeticManager manager = plugin.getChatBubbleCosmeticManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        if (action.equals("CHATBUBBLE_NONE")) {
            manager.setActive(player, ChatBubbleCosmetic.NONE);
            open(player);
            return;
        }

        if (action.startsWith("CHATBUBBLE_")) {
            try {
                ChatBubbleCosmetic b = ChatBubbleCosmetic.valueOf(action.substring(11));
                if (!manager.has(player.getUniqueId(), b) && !b.isNone()) {
                    player.sendMessage("§cYou have not unlocked this chat bubble.");
                    return;
                }
                manager.setActive(player, b);
                open(player); // refresh to show active state
            } catch (Exception ignored) {}
        }
    }
}
