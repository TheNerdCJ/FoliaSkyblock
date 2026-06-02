package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ResetConfirmationGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final String GUI_TITLE = "§c§lConfirm Island Reset";

    public ResetConfirmationGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public ResetConfirmationGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, World.Environment dimension) {
        int cost = plugin.getConfig().getInt("island.reset.cost", 0);

        boolean inCombat = plugin.getIslandManager().isPlayerInCombat(player);
        boolean hasActiveBoss = plugin.getIslandManager().hasActiveBossOnDimension(player.getUniqueId(), dimension);

        int remainingHours = plugin.getIslandManager().getResetCooldownRemainingHours(player);
        int remainingMinutes = plugin.getIslandManager().getResetCooldownRemainingMinutes(player);

        CompletableFuture<Double> balanceFuture = plugin.getEconomyManager().getBalance(player.getUniqueId());

        balanceFuture.thenAccept(balance -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                Inventory gui = Bukkit.createInventory(null, 36, MessageUtil.legacy(GUI_TITLE));

                // Warning Item - modernized
                List<String> warningLore = new ArrayList<>();
                warningLore.add("§7This will permanently reset your island in §e" + dimension.name() + "§7 only!");
                warningLore.add("§7All progress in this dimension will be lost.");
                warningLore.add("");
                if (inCombat) warningLore.add("§c§l⚠ You are currently in combat!");
                if (hasActiveBoss) warningLore.add("§c§l⚠ Active boss detected on this dimension!");
                warningLore.add("");
                warningLore.add(cost > 0 ? "§6Cost: §e" + cost : "§aFree reset");

                ItemStack warning = GUIUtils.createItem(Material.TNT, "§c§lWARNING - RESET " + dimension.name(), warningLore.toArray(new String[0]));
                gui.setItem(4, warning);

                // === COOLDOWN TIMER (Smart: shows minutes when low) ===
                List<String> cooldownLore = new ArrayList<>();
                if (remainingMinutes > 0) {
                    cooldownLore.add("§cOn Cooldown");
                    if (remainingHours >= 2) {
                        cooldownLore.add("§7Time remaining: §e" + remainingHours + " hour(s)");
                    } else {
                        cooldownLore.add("§7Time remaining: §e" + remainingMinutes + " minute(s)");
                    }
                    cooldownLore.add("§7You cannot reset yet.");
                } else {
                    cooldownLore.add("§aReady to Reset");
                    cooldownLore.add("§7No active cooldown.");
                }

                ItemStack cooldownItem = GUIUtils.createItem(Material.CLOCK, "§e§lReset Cooldown", cooldownLore.toArray(new String[0]));
                gui.setItem(13, cooldownItem);

                // Safety Status
                List<String> statusLore = new ArrayList<>();
                statusLore.add(inCombat ? "§c✖ In Combat" : "§a✔ Not in combat");
                statusLore.add(hasActiveBoss ? "§c✖ Active boss on dimension" : "§a✔ No active boss");

                ItemStack status = GUIUtils.createItem(Material.REDSTONE_TORCH, "§e§lSafety Status", statusLore.toArray(new String[0]));
                gui.setItem(22, status);

                // Balance
                ItemStack balanceItem = GUIUtils.createItem(Material.GOLD_INGOT, "§6§lYour Balance", "§e" + String.format("%,.2f", balance));
                gui.setItem(31, balanceItem);

                // Confirm / Disabled Button
                boolean isBlocked = inCombat || hasActiveBoss || remainingMinutes > 0;
                NamespacedKey dimKey = new NamespacedKey(plugin, "target_dimension");

                ItemStack confirmItem;
                if (!isBlocked) {
                    List<String> confirmLore = new ArrayList<>();
                    confirmLore.add("§7Click to reset your " + dimension.name() + " island.");
                    if (cost > 0) confirmLore.add("§7Cost: §e" + cost);
                    confirmLore.add("§cThis cannot be undone!");

                    confirmItem = GUIUtils.createItem(Material.EMERALD_BLOCK, "§a§lCONFIRM RESET", confirmLore.toArray(new String[0]));
                } else {
                    List<String> blockedLore = new ArrayList<>();
                    if (inCombat) blockedLore.add("§cYou are currently in combat.");
                    if (hasActiveBoss) blockedLore.add("§cThere is an active boss on this dimension.");
                    if (remainingMinutes > 0) {
                        if (remainingHours >= 2) {
                            blockedLore.add("§cOn cooldown for §e" + remainingHours + " hour(s).");
                        } else {
                            blockedLore.add("§cOn cooldown for §e" + remainingMinutes + " minute(s).");
                        }
                    }
                    blockedLore.add("§7Resolve the issues above to enable reset.");

                    confirmItem = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, "§c§lRESET BLOCKED", blockedLore.toArray(new String[0]));
                }

                ItemMeta confirmMeta = confirmItem.getItemMeta();
                if (confirmMeta != null) {
                    confirmMeta.getPersistentDataContainer().set(dimKey, PersistentDataType.STRING, dimension.name());
                    confirmItem.setItemMeta(confirmMeta);
                }
                gui.setItem(11, confirmItem);

                // Cancel
                ItemStack cancel = GUIUtils.createItem(Material.REDSTONE_BLOCK, "§c§lCANCEL", "§7Click to go back");
                gui.setItem(15, cancel);

                // Fill empty using GUIUtils
                ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
                for (int i = 0; i < 36; i++) {
                    if (gui.getItem(i) == null) gui.setItem(i, glass);
                }

                player.openInventory(gui);
            });
        });
    }

    public void open(Player player) {
        open(player, player.getWorld().getEnvironment());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§c§lConfirm Island Reset")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        if (clicked.getType() == Material.EMERALD_BLOCK) {
            player.closeInventory();

            // === Read dimension FIRST (must be effectively final for lambda) ===
            World.Environment targetDimension = player.getWorld().getEnvironment();

            ItemMeta meta = clicked.getItemMeta();
            if (meta != null) {
                String dimStr = meta.getPersistentDataContainer().get(
                        new NamespacedKey(plugin, "target_dimension"), PersistentDataType.STRING);
                if (dimStr != null) {
                    try {
                        targetDimension = World.Environment.valueOf(dimStr);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            // Final reference for use inside lambdas
            final World.Environment dimension = targetDimension;

            // Re-check safety
            if (plugin.getIslandManager().isPlayerInCombat(player)) {
                player.sendMessage("§cYou cannot reset while in combat!");
                return;
            }

            if (plugin.getIslandManager().hasActiveBossOnDimension(player.getUniqueId(), dimension)) {
                player.sendMessage("§cYou cannot reset while a boss is active on this dimension!");
                return;
            }

            if (!plugin.getIslandManager().canReset(player)) {
                long remaining = plugin.getIslandManager().getResetCooldownRemainingHours(player);
                player.sendMessage("§cYou must wait §e" + remaining + " more hours§c before resetting again.");
                return;
            }

            int cost = plugin.getConfig().getInt("island.reset.cost", 0);

            if (cost > 0) {
                plugin.getEconomyManager().getBalance(player.getUniqueId()).thenAccept(currentBalance -> {
                    if (currentBalance < cost) {
                        plugin.getThreadSafety().sendMessageSafely(player, "§cYou need at least §e" + cost + "§c to reset.");
                        return;
                    }

                    plugin.getEconomyManager().removeBalance(player.getUniqueId(), cost).thenRun(() -> {
                        plugin.getThreadSafety().sendMessageSafely(player, "§a§l" + cost + "§a has been deducted.");
                        plugin.getThreadSafety().runOnMainThread(() -> {
                            plugin.getBiomeSelectionGUI().open(player, true, dimension);
                        });
                    });
                });
                return;
            }

            // No cost → go directly to biome selection
            plugin.getThreadSafety().runOnMainThread(() -> {
                plugin.getBiomeSelectionGUI().open(player, true, dimension);
            });

        } else if (clicked.getType() == Material.REDSTONE_BLOCK) {
            player.closeInventory();
            player.sendMessage("§7Island reset cancelled.");
        }
    }
}