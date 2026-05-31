package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.SlayerTier;
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
 * Scrollable Slayer GUI Menu
 *
 * Features:
 * - Paginated display of all slayer tiers
 * - Click to start quest
 * - Shows progress and requirements
 * - Color-coded by availability
 */
public class SlayerGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    public SlayerGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public SlayerGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    /**
     * Open the slayer GUI for a player
     */
    public void open(Player player) {
        openPage(player, 0);
    }

    private void openPage(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);

        // Get all tiers grouped by entity type
        Map<String, List<SlayerTier>> tiersByEntity = new LinkedHashMap<>();

        for (SlayerTier tier : SlayerTier.values()) {
            String entityName = tier.getTargetEntity().name();
            tiersByEntity.computeIfAbsent(entityName, k -> new ArrayList<>()).add(tier);
        }

        // Calculate total pages (4 tiers per page)
        int totalTiers = SlayerTier.values().length;
        int tiersPerPage = 4;
        int totalPages = (int) Math.ceil(totalTiers / (double) tiersPerPage);

        Inventory gui = Bukkit.createInventory(null, 54, "§6§lSlayer Quests §7(Page " + (page + 1) + "/" + totalPages + ")");

        // Header
        gui.setItem(4, createItem(Material.DIAMOND_SWORD, "§6§lSLAYER QUESTS",
                "§7Complete slayer quests to earn rewards!",
                "§7Rewards are AI-balanced based on economy",
                "",
                "§eClick a tier to start the quest"));

        // Navigation
        if (page > 0) {
            gui.setItem(45, createItem(Material.ARROW, "§a§lPrevious Page",
                    "§7Click to go to page " + page));
        }
        if (page < totalPages - 1) {
            gui.setItem(53, createItem(Material.ARROW, "§a§lNext Page",
                    "§7Click to go to page " + (page + 2)));
        }

        // Close button
        gui.setItem(49, createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));

        // Display tiers for this page
        SlayerTier[] allTiers = SlayerTier.values();
        int startIndex = page * tiersPerPage;
        int slot = 10;

        for (int i = 0; i < tiersPerPage && (startIndex + i) < allTiers.length; i++) {
            SlayerTier tier = allTiers[startIndex + i];

            // Check if player can start this tier
            int currentTier = plugin.getBossManager().getCurrentSlayerTier(player, tier.getTargetEntity());
            boolean canStart = tier.getTier() <= currentTier + 1;
            boolean isActive = isActiveQuest(player, tier);

            Material icon = getEntityIcon(tier.getTargetEntity());
            String status = isActive ? "§a§lACTIVE QUEST" :
                    (canStart ? "§e§lClick to Start" : "§c§lLOCKED");

            List<String> lore = new ArrayList<>();
            lore.add("§7" + tier.getDescription());
            lore.add("");
            lore.add("§7Level Required: §e" + tier.getMinLevel());
            lore.add("§7Kills Needed: §e" + (tier.getXpRequired() / 10));
            lore.add("§7XP Reward: §e" + (tier.getXpRequired() / 2));
            lore.add("");
            lore.add("§6Rewards:");

            for (var reward : tier.getRewards()) {
                String color = reward.getRarityColor();
                if (reward.isSpecialReward()) {
                    lore.add("  " + color + reward.getSpecialReward() + " §7(" + reward.getRarityName() + ")");
                } else {
                    lore.add("  " + color + reward.getAmount() + "x " + reward.getMaterial().name() +
                            " §7(" + reward.getRarityName() + ")");
                }
            }

            lore.add("");
            lore.add(status);

            if (!canStart && !isActive) {
                lore.add("§7Complete previous tier first!");
            }

            gui.setItem(slot, createItem(icon, tier.getDisplayName(), lore.toArray(new String[0])));

            slot += 2;

            // Skip to next row
            if ((slot - 10) % 7 == 0) slot += 2;
        }

        // Active quest indicator
        var activeQuest = plugin.getBossManager().getActiveSlayerQuest(player);
        if (activeQuest != null) {
            gui.setItem(40, createItem(Material.CLOCK, "§a§lACTIVE QUEST",
                    "§e" + activeQuest.getTier().getDisplayName(),
                    "§7Progress: §e" + activeQuest.getKills() + "§7/§e" + activeQuest.getKillsRequired(),
                    "§7Time: §e" + (activeQuest.getTimeElapsed() / 60000) + " min",
                    "",
                    "§cClick to abandon quest"));
        }

        player.openInventory(gui);
    }

    private boolean isActiveQuest(Player player, SlayerTier tier) {
        var quest = plugin.getBossManager().getActiveSlayerQuest(player);
        return quest != null && quest.getTier() == tier;
    }

    private Material getEntityIcon(org.bukkit.entity.EntityType entityType) {
        return switch (entityType) {
            case ZOMBIE -> Material.ROTTEN_FLESH;
            case SPIDER -> Material.STRING;
            case ENDERMAN -> Material.ENDER_PEARL;
            case BLAZE -> Material.BLAZE_ROD;
            case SKELETON -> Material.BONE;
            case CREEPER -> Material.TNT;
            case WITHER_SKELETON -> Material.COAL;
            case ENDER_DRAGON -> Material.DRAGON_EGG;
            default -> Material.DIAMOND_SWORD;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().contains("Slayer Quests")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String title = event.getView().getTitle();
        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);

        // Navigation
        if (clicked.getType() == Material.ARROW) {
            if (clicked.getItemMeta().getDisplayName().contains("Previous")) {
                openPage(player, Math.max(0, currentPage - 1));
            } else if (clicked.getItemMeta().getDisplayName().contains("Next")) {
                int totalPages = (int) Math.ceil(SlayerTier.values().length / 4.0);
                openPage(player, Math.min(totalPages - 1, currentPage + 1));
            }
            return;
        }

        // Close
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // Active quest abandonment
        if (clicked.getType() == Material.CLOCK && clicked.getItemMeta().getDisplayName().contains("ACTIVE")) {
            plugin.getBossManager().abandonSlayerQuest(player);
            player.closeInventory();
            player.sendMessage("§cSlayer quest abandoned.");
            return;
        }

        // Tier selection
        String displayName = clicked.getItemMeta().getDisplayName();

        for (SlayerTier tier : SlayerTier.values()) {
            if (displayName.contains(tier.getDisplayName().replace("§", "")) ||
                    displayName.contains(tier.name())) {

                // Check if already active
                var activeQuest = plugin.getBossManager().getActiveSlayerQuest(player);
                if (activeQuest != null && activeQuest.getTier() == tier) {
                    player.sendMessage("§cYou already have this quest active!");
                    return;
                }

                // Try to start quest
                boolean success = plugin.getBossManager().startSlayerQuest(player, tier);

                if (success) {
                    player.closeInventory();
                }

                return;
            }
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