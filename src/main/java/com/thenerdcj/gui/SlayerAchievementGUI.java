package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.SlayerAchievement;
import com.thenerdcj.boss.AchievementCategory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Slayer Achievements GUI - Scrollable achievement display
 */
public class SlayerAchievementGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    public SlayerAchievementGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        openPage(player, 0);
    }

    private void openPage(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);

        SlayerAchievement[] allAchievements = SlayerAchievement.values();
        int achievementsPerPage = 6;
        int totalPages = (int) Math.ceil(allAchievements.length / (double) achievementsPerPage);

        Inventory gui = Bukkit.createInventory(null, 54,
                "§6§lSlayer Achievements §7(Page " + (page + 1) + "/" + totalPages + ")");

        // Header
        double completion = plugin.getBossManager().getAchievementManager()
                .getCompletionPercentage(player.getUniqueId());

        gui.setItem(4, createItem(Material.DIAMOND, "§6§lSLAYER ACHIEVEMENTS",
                "§7Track your slayer accomplishments!",
                "§7Completion: §e" + (int)(completion * 100) + "%",
                "",
                "§eClick to view details"));

        // Navigation
        if (page > 0) {
            gui.setItem(45, createItem(Material.ARROW, "§a§lPrevious Page", "§7Click to go back"));
        }

        gui.setItem(49, createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));

        if (page < totalPages - 1) {
            gui.setItem(53, createItem(Material.ARROW, "§a§lNext Page", "§7Click to go forward"));
        }

        // Display achievements
        int startIndex = page * achievementsPerPage;
        int slot = 10;

        for (int i = 0; i < achievementsPerPage && (startIndex + i) < allAchievements.length; i++) {
            SlayerAchievement achievement = allAchievements[startIndex + i];

            boolean unlocked = plugin.getBossManager().getAchievementManager()
                    .isUnlocked(player.getUniqueId(), achievement);

            int progress = plugin.getBossManager().getAchievementManager()
                    .getProgress(player.getUniqueId(), achievement);

            Material icon = unlocked ? Material.LIME_DYE : Material.GRAY_DYE;
            String status = unlocked ? "§a§lUNLOCKED ✓" : "§7§lLOCKED";

            List<String> lore = new ArrayList<>();
            lore.add("§7" + achievement.getDescription());
            lore.add("");
            lore.add("§7Category: " + achievement.getCategory().getDisplayName());  // ✅ FIXED
            lore.add("§7Requirement: §e" + achievement.getRequirement());
            lore.add("§7XP Reward: §e" + achievement.getXpReward());
            lore.add("");
            lore.add("§6Rewards:");

            for (var reward : achievement.getItemRewards()) {
                lore.add(" §f" + reward.getAmount() + "x " + reward.getMaterial().name());
            }

            lore.add("");
            lore.add(status);

            if (!unlocked) {
                double pct = achievement.getProgress(progress);
                lore.add("§7Progress: §e" + progress + "§7/§e" + achievement.getRequirement() +
                        " §7(" + (int)(pct * 100) + "%)");
                lore.add("");
                lore.add("§7" + achievement.getUnlockCondition());
            }

            gui.setItem(slot, createItem(icon, achievement.getDisplayName(),
                    lore.toArray(new String[0])));

            slot += 2;
            if ((slot - 10) % 7 == 0) slot += 2;
        }

        // Category legend
        gui.setItem(38, createItem(Material.LIME_DYE, "§a§lBEGINNER", "§7Easy achievements"));
        gui.setItem(40, createItem(Material.YELLOW_DYE, "§e§lINTERMEDIATE", "§7Medium difficulty"));
        gui.setItem(42, createItem(Material.BLUE_DYE, "§9§lADVANCED", "§7Hard achievements"));

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!event.getView().getTitle().contains("Slayer Achievements")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);

        // Navigation
        if (clicked.getType() == Material.ARROW) {
            if (clicked.getItemMeta().getDisplayName().contains("Previous")) {
                openPage(player, Math.max(0, currentPage - 1));
            } else {
                int totalPages = (int) Math.ceil(SlayerAchievement.values().length / 6.0);
                openPage(player, Math.min(totalPages - 1, currentPage + 1));
            }
            return;
        }

        // Close
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }
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