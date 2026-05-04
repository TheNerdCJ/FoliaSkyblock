package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.SlayerLeaderboard;
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
 * Slayer Leaderboard GUI - Scrollable top slayers display
 */
public class SlayerLeaderboardGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    public SlayerLeaderboardGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        openPage(player, 0);
    }

    private void openPage(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);

        Inventory gui = Bukkit.createInventory(null, 54, "§6§lSlayer Leaderboards §7(Page " + (page + 1) + ")");

        // Header
        gui.setItem(4, createItem(Material.DIAMOND_SWORD, "§6§lSLAYER LEADERBOARDS",
                "§7Top slayers across all dimensions!",
                "§7Points = Kills × Tier × 10",
                "",
                "§eClick to view entity-specific leaderboards"));

        // Navigation
        if (page > 0) {
            gui.setItem(45, createItem(Material.ARROW, "§a§lPrevious Page", "§7Click to go back"));
        }
        gui.setItem(49, createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));
        gui.setItem(53, createItem(Material.ARROW, "§a§lNext Page", "§7Click to go forward"));

        // Global leaderboard (top 10)
        plugin.getDatabaseManager().getGlobalTopSlayers(10).thenAccept(globalLeaders -> {
            int slot = 10;
            int rank = 1;

            for (Object obj : globalLeaders) {
                SlayerLeaderboard entry;
                if (obj instanceof SlayerLeaderboard) {
                    entry = (SlayerLeaderboard) obj;
                } else if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    entry = new SlayerLeaderboard(
                            UUID.fromString((String) map.get("player_uuid")),
                            (String) map.get("player_name"),
                            (String) map.getOrDefault("entity_type", "ALL"),
                            ((Number) map.getOrDefault("tier", 0)).intValue(),
                            ((Number) map.getOrDefault("total_kills", 0)).intValue(),
                            ((Number) map.getOrDefault("last_kill", 0)).longValue()
                    );
                } else {
                    continue;
                }
                if (slot > 43) break;

                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getPlayerUuid()));
                    meta.setDisplayName("§6#" + rank + " §e" + entry.getPlayerName());

                    List<String> lore = new ArrayList<>();
                    lore.add("§7Total Kills: §e" + entry.getTotalKills());
                    lore.add("§7Highest Tier: §e" + entry.getTier());
                    lore.add("§7Points: §6" + entry.getPoints());
                    lore.add("");
                    lore.add("§7Last Kill: §e" + new Date(entry.getLastKillTime()).toString().substring(0, 16));

                    meta.setLore(lore);
                    skull.setItemMeta(meta);
                }

                gui.setItem(slot, skull);
                slot += 2;
                rank++;
            }

            // Entity-specific quick links
            gui.setItem(38, createItem(Material.ROTTEN_FLESH, "§c§lZombie Leaderboard",
                    "§7Click to view top Zombie slayers"));
            gui.setItem(40, createItem(Material.STRING, "§8§lSpider Leaderboard",
                    "§7Click to view top Spider slayers"));
            gui.setItem(42, createItem(Material.ENDER_PEARL, "§5§lEnderman Leaderboard",
                    "§7Click to view top Enderman slayers"));

            player.openInventory(gui);
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().contains("Slayer Leaderboards")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);

        // Navigation
        if (clicked.getType() == Material.ARROW) {
            if (clicked.getItemMeta().getDisplayName().contains("Previous")) {
                openPage(player, Math.max(0, currentPage - 1));
            } else {
                openPage(player, currentPage + 1);
            }
            return;
        }

        // Close
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // Entity-specific leaderboards
        if (clicked.getType() == Material.ROTTEN_FLESH) {
            showEntityLeaderboard(player, "ZOMBIE");
        } else if (clicked.getType() == Material.STRING) {
            showEntityLeaderboard(player, "SPIDER");
        } else if (clicked.getType() == Material.ENDER_PEARL) {
            showEntityLeaderboard(player, "ENDERMAN");
        }
    }

    private void showEntityLeaderboard(Player player, String entityType) {
        plugin.getDatabaseManager().getTopSlayers(entityType, 10).thenAccept(leaders -> {
            Inventory gui = Bukkit.createInventory(null, 54, "§6§l" + entityType + " Leaderboard");

            gui.setItem(4, createItem(getEntityIcon(entityType), "§6§lTOP " + entityType + " SLAYERS",
                    "§7Ranked by total kills and tier"));

            int slot = 10;
            int rank = 1;

            for (Object obj : leaders) {
                SlayerLeaderboard entry;
                if (obj instanceof SlayerLeaderboard) {
                    entry = (SlayerLeaderboard) obj;
                } else if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    entry = new SlayerLeaderboard(
                            UUID.fromString((String) map.get("player_uuid")),
                            (String) map.get("player_name"),
                            (String) map.getOrDefault("entity_type", entityType),
                            ((Number) map.getOrDefault("tier", 0)).intValue(),
                            ((Number) map.getOrDefault("total_kills", 0)).intValue(),
                            ((Number) map.getOrDefault("last_kill", 0)).longValue()
                    );
                } else {
                    continue;
                }
                if (slot > 43) break;

                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getPlayerUuid()));
                    meta.setDisplayName("§6#" + rank + " §e" + entry.getPlayerName());

                    List<String> lore = new ArrayList<>();
                    lore.add("§7Kills: §e" + entry.getTotalKills());
                    lore.add("§7Tier: §e" + entry.getTier());
                    lore.add("§7Points: §6" + entry.getPoints());

                    meta.setLore(lore);
                    skull.setItemMeta(meta);
                }

                gui.setItem(slot, skull);
                slot += 2;
                rank++;
            }

            gui.setItem(49, createItem(Material.BARRIER, "§c§lBack", "§7Return to main leaderboard"));

            player.openInventory(gui);
        });
    }

    private Material getEntityIcon(String entityType) {
        return switch (entityType) {
            case "ZOMBIE" -> Material.ROTTEN_FLESH;
            case "SPIDER" -> Material.STRING;
            case "ENDERMAN" -> Material.ENDER_PEARL;
            case "BLAZE" -> Material.BLAZE_ROD;
            default -> Material.DIAMOND_SWORD;
        };
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(java.util.Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}