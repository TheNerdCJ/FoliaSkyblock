package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.challenge.Challenge;
import com.thenerdcj.manager.ChallengeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * GUI for Daily and Weekly Challenges
 */
public class ChallengeGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final ChallengeManager challengeManager;

    public ChallengeGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.challengeManager = plugin.getChallengeManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6§lDaily & Weekly Challenges");

        // Title
        gui.setItem(4, createTitleItem());

        // Daily Challenges (Slots 10-16)
        List<Challenge> daily = challengeManager.getActiveChallenges(player.getUniqueId()).join();
        int slot = 10;
        for (Challenge c : daily) {
            if (c.getType() == Challenge.Type.DAILY) {
                gui.setItem(slot++, createChallengeItem(c, true));
            }
        }

        // Weekly Challenges (Slots 28-34)
        List<Challenge> weekly = challengeManager.getActiveChallenges(player.getUniqueId()).join();
        slot = 28;
        for (Challenge c : weekly) {
            if (c.getType() == Challenge.Type.WEEKLY) {
                gui.setItem(slot++, createChallengeItem(c, false));
            }
        }

        // Info
        gui.setItem(49, createInfoItem());

        player.openInventory(gui);
    }

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§l★ CHALLENGES ★");
        meta.setLore(java.util.Arrays.asList(
                "§7Complete challenges for bonus XP!",
                "§7Daily challenges reset every day",
                "§7Weekly challenges reset every Sunday"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createChallengeItem(Challenge challenge, boolean isDaily) {
        Material material = switch (challenge.getCategory()) {
            case "MINING" -> Material.DIAMOND_PICKAXE;
            case "FARMING" -> Material.WHEAT;
            case "COMBAT" -> Material.IRON_SWORD;
            case "BUILDING" -> Material.BRICKS;
            case "EXPLORATION" -> Material.COMPASS;
            default -> Material.PAPER;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String status = challenge.isCompleted() ? "§a§lCOMPLETED" : "§e§lIN PROGRESS";
        meta.setDisplayName("§f" + challenge.getDescription() + " " + status);

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Progress: §e" + challenge.getProgress() + "§7/§e" + challenge.getTarget());
        lore.add("§7Reward: §a+" + challenge.getRewardXP() + " XP");
        lore.add("");
        if (challenge.isCompleted()) {
            lore.add("§a§lClick to claim reward!");
        } else {
            lore.add("§7" + String.format("%.1f", challenge.getProgressPercent()) + "% complete");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lHow Challenges Work");
        meta.setLore(java.util.Arrays.asList(
                "§7• Complete tasks to earn bonus XP",
                "§7• Challenges scale with your island level",
                "§7• Daily = Easy, Weekly = Hard",
                "§7• Complete all for special rewards!"
        ));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().contains("Challenges")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // TODO: Handle claim clicks
        if (clicked.getItemMeta() != null && clicked.getItemMeta().getDisplayName().contains("COMPLETED")) {
            player.sendMessage("§aReward claimed! (Full implementation coming soon)");
        }
    }
}