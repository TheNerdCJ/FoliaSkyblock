package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
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

/**
 * Island Bank GUI - Deposit/Withdraw interface for island economy.
 *
 * Deep modernization pass:
 * - Manual createItem + createGlassPane converted to GUIUtils.createItem.
 * - Title now uses MessageUtil.legacy.
 * - Click handler title check made resilient (startsWith).
 * - Filler modernized to respect dynamic vault size.
 * - Preserved all async bank loading, economy flows, metadata position tracking, and reopen logic.
 */
public class IslandBankGUI implements Listener {

    private final FoliaSkyblock plugin;

    public IslandBankGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandBankGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, Island island) {
        GridPosition pos = island.getGridPosition();

        plugin.getIslandBankManager().getBank(pos).thenAccept(bank -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                // Dynamic size based on VAULT_SLOTS upgrade (Tier A)
                int size = 27;
                if (plugin.getIslandUpgradeManager() != null) {
                    size = plugin.getIslandUpgradeManager().getVaultInventorySize(island);
                }
                Inventory gui = Bukkit.createInventory(null, size, MessageUtil.legacy("§6§lIsland Bank"));

                double balance = bank.getBalance();

                // Balance display
                gui.setItem(4, createItem(Material.GOLD_INGOT, "§6§lIsland Bank Balance",
                        "§7Current Balance: §e$" + String.format("%,.2f", balance)));

                // Deposit buttons
                gui.setItem(10, createItem(Material.EMERALD, "§a§lDeposit $100", "§7Click to deposit $100"));
                gui.setItem(11, createItem(Material.EMERALD, "§a§lDeposit $500", "§7Click to deposit $500"));
                gui.setItem(12, createItem(Material.EMERALD, "§a§lDeposit $1,000", "§7Click to deposit $1,000"));
                gui.setItem(13, createItem(Material.EMERALD, "§a§lDeposit $5,000", "§7Click to deposit $5,000"));
                gui.setItem(14, createItem(Material.EMERALD, "§a§lDeposit $10,000", "§7Click to deposit $10,000"));

                // Withdraw buttons
                gui.setItem(16, createItem(Material.REDSTONE, "§c§lWithdraw $100", "§7Click to withdraw $100"));
                gui.setItem(17, createItem(Material.REDSTONE, "§c§lWithdraw $500", "§7Click to withdraw $500"));
                gui.setItem(18, createItem(Material.REDSTONE, "§c§lWithdraw $1,000", "§7Click to withdraw $1,000"));

                // Info
                int infoSlot = Math.min(22, size - 5);
                gui.setItem(infoSlot, createItem(Material.BOOK, "§e§lHow Bank Works",
                        "§7• Deposit money from your balance",
                        "§7• Withdraw to your personal balance",
                        "§7• Use for island upgrades",
                        "§7• Larger vault with Vault Slots upgrade"));

                // Modernized dynamic filler (respects vault size)
                ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
                for (int i = 0; i < size; i++) {
                    if (gui.getItem(i) == null) gui.setItem(i, glass);
                }

                player.openInventory(gui);
                player.setMetadata("island_bank_pos", new org.bukkit.metadata.FixedMetadataValue(plugin, pos.toString()));
            });
        });
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        return GUIUtils.createItem(material, name, lore);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Resilient title check (modernized)
        if (!event.getView().getTitle().startsWith("§6§lIsland Bank")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata("island_bank_pos")) return;

        String posStr = player.getMetadata("island_bank_pos").get(0).asString();
        GridPosition pos = GridPosition.fromString(posStr);

        String itemName = event.getCurrentItem().getItemMeta().getDisplayName();

        // Handle deposit
        if (itemName.contains("Deposit")) {
            double amount = 0;
            if (itemName.contains("$100")) amount = 100;
            else if (itemName.contains("$500")) amount = 500;
            else if (itemName.contains("$1,000")) amount = 1000;
            else if (itemName.contains("$5,000")) amount = 5000;
            else if (itemName.contains("$10,000")) amount = 10000;

            if (amount == 0) return;

            // Check player has enough money
            final double finalAmount = amount;
            plugin.getEconomyManager().getPlayerBalance(player.getUniqueId()).thenAccept(playerBalance -> {
                if (playerBalance >= finalAmount) {
                    plugin.getEconomyManager().removePlayerBalance(player.getUniqueId(), finalAmount);
                    plugin.getIslandBankManager().deposit(pos, finalAmount);
                    player.sendMessage("§aDeposited $" + String.format("%,.2f", finalAmount) + " to island bank!");
                    plugin.getThreadSafety().runOnMainThread(() -> {
                        player.closeInventory();
                        // Reopen GUI
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        if (island != null) new IslandBankGUI(plugin).open(player, island);
                    });
                } else {
                    player.sendMessage("§cYou don't have enough money!");
                }
            });
        }
        // Handle withdraw
        else if (itemName.contains("Withdraw")) {
            double amount = 0;
            if (itemName.contains("$100")) amount = 100;
            else if (itemName.contains("$500")) amount = 500;
            else if (itemName.contains("$1,000")) amount = 1000;

            if (amount == 0) return;

            final double finalAmount = amount;
            plugin.getIslandBankManager().withdraw(pos, finalAmount).thenAccept(success -> {
                if (success) {
                    plugin.getEconomyManager().addPlayerBalance(player.getUniqueId(), finalAmount);
                    player.sendMessage("§aWithdrew $" + String.format("%,.2f", finalAmount) + " from island bank!");
                    plugin.getThreadSafety().runOnMainThread(() -> {
                        player.closeInventory();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        if (island != null) new IslandBankGUI(plugin).open(player, island);
                    });
                } else {
                    player.sendMessage("§cNot enough money in island bank!");
                }
            });
        }
    }
}