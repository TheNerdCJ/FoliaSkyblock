package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.SlayerTier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

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
public class SlayerGUI extends BaseGUI {

    public SlayerGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public SlayerGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lSlayer Quests";
    }

    @Override
    protected String getActionKeyName() {
        return "slayer_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 4;
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        // Header
        gui.setItem(4, GUIUtils.createItem(Material.DIAMOND_SWORD, "§6§lSLAYER QUESTS",
                "§7Complete slayer quests to earn rewards!",
                "§7Rewards are AI-balanced based on economy",
                "",
                "§eClick a tier to start the quest"));

        // Display tiers for this page (4 per page, custom layout)
        SlayerTier[] allTiers = SlayerTier.values();
        int startIndex = page * getItemsPerPage();
        int slot = 10;

        for (int i = 0; i < getItemsPerPage() && (startIndex + i) < allTiers.length; i++) {
            SlayerTier tier = allTiers[startIndex + i];

            int currentTier = plugin.getBossManager().getCurrentSlayerTier(player, tier.getTargetEntity());
            boolean canStart = tier.getTier() <= currentTier + 1;
            boolean isActive = isActiveQuest(player, tier);

            Material icon = getEntityIcon(tier.getTargetEntity());
            String status = isActive ? "§a§lACTIVE QUEST" :
                    (canStart ? "§e§lClick to Start" : "§c§lLOCKED");

            java.util.List<String> lore = new java.util.ArrayList<>();
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

            ItemStack tierItem = GUIUtils.createItem(icon, tier.getDisplayName(), lore.toArray(new String[0]));

            // Store tier name in PDC for reliable click handling
            ItemMeta meta = tierItem.getItemMeta();
            if (meta != null) {
                GUIUtils.setPDCString(meta, ACTION_KEY, "TIER:" + tier.name());
                tierItem.setItemMeta(meta);
            }

            gui.setItem(slot, tierItem);
            slot += 2;
            if ((slot - 10) % 7 == 0) slot += 2;
        }

        // Active quest indicator
        var activeQuest = plugin.getBossManager().getActiveSlayerQuest(player);
        if (activeQuest != null) {
            ItemStack activeItem = GUIUtils.createItem(Material.CLOCK, "§a§lACTIVE QUEST",
                    "§e" + activeQuest.getTier().getDisplayName(),
                    "§7Progress: §e" + activeQuest.getKills() + "§7/§e" + activeQuest.getKillsRequired(),
                    "§7Time: §e" + (activeQuest.getTimeElapsed() / 60000) + " min",
                    "",
                    "§cClick to abandon quest");

            ItemMeta meta = activeItem.getItemMeta();
            if (meta != null) {
                GUIUtils.setPDCString(meta, ACTION_KEY, "ABANDON");
                activeItem.setItemMeta(meta);
            }
            gui.setItem(40, activeItem);
        }
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        if (action.startsWith("TIER:")) {
            String tierName = action.substring(5);
            try {
                SlayerTier tier = SlayerTier.valueOf(tierName);

                var activeQuest = plugin.getBossManager().getActiveSlayerQuest(player);
                if (activeQuest != null && activeQuest.getTier() == tier) {
                    player.sendMessage("§cYou already have this quest active!");
                    return;
                }

                boolean success = plugin.getBossManager().startSlayerQuest(player, tier);
                if (success) {
                    player.closeInventory();
                }
            } catch (Exception ignored) {}
            return;
        }

        if ("ABANDON".equals(action)) {
            plugin.getBossManager().abandonSlayerQuest(player);
            player.closeInventory();
            player.sendMessage("§cSlayer quest abandoned.");
            return;
        }
    }

    @Override
    protected int getTotalPages(Player player) {
        return (int) Math.ceil(SlayerTier.values().length / (double) getItemsPerPage());
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

    /**
     * Open the slayer GUI for a player
     */
    public void open(Player player) {
        open(player, 0);
    }
}
