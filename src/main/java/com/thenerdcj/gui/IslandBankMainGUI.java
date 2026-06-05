package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island bank (variable-size BaseGUI + PDC). Opened via {@link IslandBankGUI#open(Player, Island)}.
 */
public class IslandBankMainGUI extends BaseGUI {

    private final Map<UUID, GridPosition> sessionGrid = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> vaultSizes = new ConcurrentHashMap<>();

    public IslandBankMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    public IslandBankMainGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lIsland Bank";
    }

    @Override
    protected String formatTitle(Player player, int page) {
        return getTitlePrefix();
    }

    @Override
    protected String getActionKeyName() {
        return "island_bank_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 0;
    }

    @Override
    protected int getInventorySize(Player player) {
        return vaultSizes.getOrDefault(player.getUniqueId(), 27);
    }

    public void open(Player player, Island island) {
        if (island == null) {
            player.sendMessage("§cYou must be on an island to use the bank.");
            return;
        }
        GridPosition pos = island.getGridPosition();
        sessionGrid.put(player.getUniqueId(), pos);

        int size = 27;
        if (plugin.getIslandUpgradeManager() != null) {
            size = plugin.getIslandUpgradeManager().getVaultInventorySize(island);
        }
        vaultSizes.put(player.getUniqueId(), size);
        final int vaultSize = size;

        plugin.getIslandBankManager().getBank(pos).thenAccept(bank ->
                plugin.getThreadSafety().runOnMainThread(() -> {
                    Inventory gui = Bukkit.createInventory(null, vaultSize, MessageUtil.legacy(getTitlePrefix()));
                    double balance = bank.getBalance();

                    gui.setItem(4, GUIUtils.createItem(Material.GOLD_INGOT, "§6§lIsland Bank Balance",
                            "§7Current Balance: §e$" + String.format("%,.2f", balance)));

                    gui.setItem(10, nav(Material.EMERALD, "§a§lDeposit $100", "DEPOSIT_100"));
                    gui.setItem(11, nav(Material.EMERALD, "§a§lDeposit $500", "DEPOSIT_500"));
                    gui.setItem(12, nav(Material.EMERALD, "§a§lDeposit $1,000", "DEPOSIT_1000"));
                    gui.setItem(13, nav(Material.EMERALD, "§a§lDeposit $5,000", "DEPOSIT_5000"));
                    gui.setItem(14, nav(Material.EMERALD, "§a§lDeposit $10,000", "DEPOSIT_10000"));

                    gui.setItem(16, nav(Material.REDSTONE, "§c§lWithdraw $100", "WITHDRAW_100"));
                    gui.setItem(17, nav(Material.REDSTONE, "§c§lWithdraw $500", "WITHDRAW_500"));
                    gui.setItem(18, nav(Material.REDSTONE, "§c§lWithdraw $1,000", "WITHDRAW_1000"));

                    int infoSlot = Math.min(22, vaultSize - 5);
                    gui.setItem(infoSlot, GUIUtils.createItem(Material.BOOK, "§e§lHow Bank Works",
                            "§7• Deposit money from your balance",
                            "§7• Withdraw to your personal balance",
                            "§7• Use for island upgrades",
                            "§7• Larger vault with Vault Slots upgrade"));

                    ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
                    for (int i = 0; i < vaultSize; i++) {
                        if (gui.getItem(i) == null) {
                            gui.setItem(i, glass);
                        }
                    }
                    player.openInventory(gui);
                }));
    }

    @Override
    public void open(Player player, int page) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        open(player, island);
    }

    private ItemStack nav(Material material, String name, String action) {
        return GUIUtils.createNavButton(material, name, ACTION_KEY, action);
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        GridPosition pos = sessionGrid.get(player.getUniqueId());
        if (pos == null) {
            return;
        }

        if (action.startsWith("DEPOSIT_")) {
            double amount = parseAmount(action.substring(8));
            if (amount <= 0) {
                return;
            }
            final double finalAmount = amount;
            plugin.getEconomyManager().getPlayerBalance(player.getUniqueId()).thenAccept(playerBalance -> {
                if (playerBalance >= finalAmount) {
                    plugin.getEconomyManager().removePlayerBalance(player.getUniqueId(), finalAmount);
                    plugin.getIslandBankManager().deposit(pos, finalAmount);
                    player.sendMessage("§aDeposited $" + String.format("%,.2f", finalAmount) + " to island bank!");
                    reopen(player);
                } else {
                    player.sendMessage("§cYou don't have enough money!");
                }
            });
            return;
        }

        if (action.startsWith("WITHDRAW_")) {
            double amount = parseAmount(action.substring(9));
            if (amount <= 0) {
                return;
            }
            final double finalAmount = amount;
            plugin.getIslandBankManager().withdraw(pos, finalAmount).thenAccept(success -> {
                if (success) {
                    plugin.getEconomyManager().addPlayerBalance(player.getUniqueId(), finalAmount);
                    player.sendMessage("§aWithdrew $" + String.format("%,.2f", finalAmount) + " from island bank!");
                    reopen(player);
                } else {
                    player.sendMessage("§cNot enough money in island bank!");
                }
            });
        }
    }

    private static double parseAmount(String suffix) {
        return switch (suffix) {
            case "100" -> 100;
            case "500" -> 500;
            case "1000" -> 1_000;
            case "5000" -> 5_000;
            case "10000" -> 10_000;
            default -> 0;
        };
    }

    private void reopen(Player player) {
        plugin.getThreadSafety().runOnMainThread(() -> {
            player.closeInventory();
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island != null) {
                open(player, island);
            }
        });
    }
}