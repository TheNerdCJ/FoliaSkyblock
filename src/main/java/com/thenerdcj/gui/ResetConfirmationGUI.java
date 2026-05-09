package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class ResetConfirmationGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final String GUI_TITLE = "§c§lConfirm Island Reset";

    public ResetConfirmationGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        int cost = plugin.getConfig().getInt("island.reset.cost", 0);
        long cooldownHours = plugin.getConfig().getLong("island.reset.cooldown-hours", 24);

        CompletableFuture<Double> balanceFuture = plugin.getEconomyManager().getBalance(player.getUniqueId());

        balanceFuture.thenAccept(balance -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

                // Warning
                ItemStack warning = new ItemStack(Material.TNT);
                ItemMeta warningMeta = warning.getItemMeta();
                warningMeta.setDisplayName("§c§lWARNING");
                warningMeta.setLore(Arrays.asList(
                        "§7This will permanently reset your island!",
                        "§7All blocks and items will be lost.",
                        "",
                        cost > 0 ? "§6Cost: §e" + cost + " §7from your balance" : "§aFree reset",
                        cooldownHours > 0 ? "§7Cooldown: §e" + cooldownHours + " hours" : ""
                ));
                warning.setItemMeta(warningMeta);
                gui.setItem(4, warning);

                // Live Balance
                ItemStack balanceItem = new ItemStack(Material.GOLD_INGOT);
                ItemMeta balanceMeta = balanceItem.getItemMeta();
                balanceMeta.setDisplayName("§6§lYour Current Balance");
                balanceMeta.setLore(Arrays.asList(
                        "§e" + String.format("%,.2f", balance),
                        cost > 0 ? (balance >= cost ? "§aYou can afford this reset" : "§cYou cannot afford this reset") : ""
                ));
                balanceItem.setItemMeta(balanceMeta);
                gui.setItem(13, balanceItem);

                // Confirm
                ItemStack confirm = new ItemStack(Material.EMERALD_BLOCK);
                ItemMeta confirmMeta = confirm.getItemMeta();
                confirmMeta.setDisplayName("§a§lCONFIRM RESET");
                if (cost > 0) {
                    confirmMeta.setLore(Arrays.asList(
                            "§7This will deduct §e" + cost + "§7 from your balance",
                            balance >= cost ? "§aYou have enough balance" : "§cInsufficient balance"
                    ));
                } else {
                    confirmMeta.setLore(Arrays.asList("§7Click to choose new biome"));
                }
                confirm.setItemMeta(confirmMeta);
                gui.setItem(11, confirm);

                // Cancel
                ItemStack cancel = new ItemStack(Material.REDSTONE_BLOCK);
                ItemMeta cancelMeta = cancel.getItemMeta();
                cancelMeta.setDisplayName("§c§lCANCEL");
                cancelMeta.setLore(Arrays.asList("§7Click to cancel"));
                cancel.setItemMeta(cancelMeta);
                gui.setItem(15, cancel);

                // Fill with glass
                ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta glassMeta = glass.getItemMeta();
                glassMeta.setDisplayName(" ");
                glass.setItemMeta(glassMeta);
                for (int i = 0; i < 27; i++) {
                    if (gui.getItem(i) == null) gui.setItem(i, glass);
                }

                player.openInventory(gui);
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        if (event.getCurrentItem() == null) return;

        if (event.getCurrentItem().getType() == Material.EMERALD_BLOCK) {
            player.closeInventory();

            int cost = plugin.getConfig().getInt("island.reset.cost", 0);

            if (!plugin.getIslandManager().canReset(player)) {
                long remaining = plugin.getIslandManager().getResetCooldownRemainingHours(player);
                player.sendMessage("§cYou must wait §e" + remaining + " more hours§c before resetting again.");
                return;
            }

            if (cost > 0) {
                double currentBalance = plugin.getEconomyManager().getBalance(player.getUniqueId()).join();
                if (currentBalance < cost) {
                    player.sendMessage("§cYou need at least §e" + cost + "§c to reset your island.");
                    return;
                }
                plugin.getEconomyManager().removeBalance(player.getUniqueId(), cost);
                player.sendMessage("§a§l" + cost + "§a has been deducted from your balance.");
            }

            plugin.getBiomeSelectionGUI().open(player, true);
        }
        else if (event.getCurrentItem().getType() == Material.REDSTONE_BLOCK) {
            player.closeInventory();
            player.sendMessage("§7Island reset cancelled.");
        }
    }
}