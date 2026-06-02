package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Slayer Token Leaderboard GUI.
 *
 * Deep modernization pass:
 * - Manual skull (PLAYER_HEAD + SkullMeta) and createItem patterns converted to GUIUtils + createSkullItem helper.
 * - Titles now use MessageUtil.legacy.
 * - Title checks made more resilient.
 * - Preserved pagination and click logic.
 */
public class SlayerTokenLeaderboardGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    public SlayerTokenLeaderboardGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public SlayerTokenLeaderboardGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player) {
        openPage(player, 0);
    }

    private void openPage(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);

        String title = "§6§lSlayer Token Leaderboards §7(Page " + (page + 1) + ")";
        Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy(title));

        gui.setItem(4, createItem(Material.NETHER_STAR, "§6§lSLAYER TOKEN LEADERBOARDS",
                "§7Top earners of Slayer Tokens!",
                "§7Earned from Slayer Quests & Island Boss Events"));

        if (page > 0) {
            gui.setItem(45, createItem(Material.ARROW, "§a§lPrevious Page", "§7Click to go back"));
        }
        gui.setItem(49, createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));
        if (page < 4) {
            gui.setItem(53, createItem(Material.ARROW, "§a§lNext Page", "§7Click to go forward"));
        }

        var topEarners = plugin.getBossManager().getTopSlayerTokenEarners(20);

        int start = page * 9;
        int end = Math.min(start + 9, topEarners.size());
        int slot = 10;
        int rank = start + 1;

        for (int i = start; i < end; i++) {
            var entry = topEarners.get(i);
            UUID uuid = entry.getKey();
            int tokens = entry.getValue();

            String playerName = Bukkit.getOfflinePlayer(uuid).getName();
            if (playerName == null) playerName = "Unknown";

            List<String> lore = new ArrayList<>();
            lore.add("§7Total Slayer Tokens Earned: §6" + tokens);

            ItemStack skull = createSkullItem(uuid, "§6#" + rank + " §e" + playerName, lore);
            gui.setItem(slot, skull);
            slot++;
            rank++;
        }

        int playerTokens = 0;
        try {
            playerTokens = plugin.getBossManager().getTotalSlayerTokens(player);
        } catch (Exception ignored) {}

        gui.setItem(40, createItem(Material.PAPER, "§eYour Tokens",
                "§7Current: §6" + playerTokens));

        player.openInventory(gui);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        return GUIUtils.createItem(mat, name, lore);
    }

    private ItemStack createSkullItem(UUID uuid, String displayName, List<String> lore) {
        ItemStack skull = GUIUtils.createItem(Material.PLAYER_HEAD, displayName, lore.toArray(new String[0]));
        ItemMeta meta = skull.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            skull.setItemMeta(skullMeta);
        }
        return skull;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Resilient title check (modernized)
        if (!event.getView().getTitle().startsWith("§6§lSlayer Token Leaderboards")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);

        if (clicked.getType() == Material.ARROW) {
            String display = clicked.getItemMeta().getDisplayName();
            if (display.contains("Previous")) {
                openPage(player, Math.max(0, currentPage - 1));
            } else if (display.contains("Next")) {
                openPage(player, currentPage + 1);
            }
        } else if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
        }
    }
}