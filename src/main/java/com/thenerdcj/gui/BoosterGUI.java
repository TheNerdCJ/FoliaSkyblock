package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.booster.BoosterType;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Simple Booster GUI for purchasing and viewing active boosters.
 */
public class BoosterGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey BOOSTER_TYPE_KEY;

    public BoosterGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public BoosterGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.BOOSTER_TYPE_KEY = new NamespacedKey(plugin, "booster_type");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, Island island) {
        GridPosition pos = island.getGridPosition();

        plugin.getIslandBankManager().getBank(pos).thenAccept(bank -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                Inventory gui = Bukkit.createInventory(null, 54, "§6§lIsland Boosters §7(Shop)");

                double balance = bank.getBalance();

                // Balance header
                ItemStack balanceItem = new ItemStack(Material.GOLD_INGOT);
                ItemMeta bMeta = balanceItem.getItemMeta();
                if (bMeta != null) {
                    bMeta.setDisplayName("§e§lIsland Balance");
                    bMeta.setLore(Arrays.asList(
                        "§6$" + String.format("%,.0f", balance),
                        "§7Click boosters below to purchase"
                    ));
                    balanceItem.setItemMeta(bMeta);
                }
                gui.setItem(4, balanceItem);

                // Info book
                ItemStack info = new ItemStack(Material.BOOK);
                ItemMeta iMeta = info.getItemMeta();
                if (iMeta != null) {
                    iMeta.setDisplayName("§a§lBooster Info");
                    iMeta.setLore(Arrays.asList(
                        "§7Boosters provide temporary multipliers",
                        "§7that stack on top of island upgrades.",
                        "§7Active boosters apply automatically."
                    ));
                    info.setItemMeta(iMeta);
                }
                gui.setItem(0, info);

                // Booster shop items (PDC-driven)
                int[] slots = {10,11,12,13,14, 19,20,21,22,23, 28,29,30,31,32};
                int idx = 0;
                for (BoosterType type : BoosterType.values()) {
                    if (idx >= slots.length) break;
                    ItemStack item = createBoosterItem(type, island, balance);
                    gui.setItem(slots[idx], item);
                    idx++;
                }

                // Close button
                ItemStack close = new ItemStack(Material.BARRIER);
                ItemMeta cMeta = close.getItemMeta();
                if (cMeta != null) {
                    cMeta.setDisplayName("§c§lClose");
                    close.setItemMeta(cMeta);
                }
                gui.setItem(49, close);

                player.openInventory(gui);
            });
        });
    }

    private ItemStack createBoosterItem(BoosterType type, Island island, double balance) {
        // Icon selection per type
        Material icon = switch (type) {
            case CROP_GROWTH -> Material.WHEAT;
            case SPAWNER_RATE -> Material.SPAWNER;
            case ISLAND_XP -> Material.EXPERIENCE_BOTTLE;
            case MONEY_EARN -> Material.GOLD_INGOT;
            case MINION_SPEED -> Material.IRON_PICKAXE;
        };
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + type.getDisplayName());

            double multiplier = plugin.getConfig().getDouble("boosters.multipliers." + type.name(), 2.0);
            long shortDur = plugin.getConfig().getLong("boosters.durations.short", 30);
            long medDur = plugin.getConfig().getLong("boosters.durations.medium", 60);
            long longDur = plugin.getConfig().getLong("boosters.durations.long", 120);

            int shortPrice = plugin.getConfig().getInt("boosters.prices." + type.name() + ".short", 5000);
            int medPrice = plugin.getConfig().getInt("boosters.prices." + type.name() + ".medium", 9000);
            int longPrice = plugin.getConfig().getInt("boosters.prices." + type.name() + ".long", 16000);

            // Check if currently active for this island
            double currentMult = (island != null && plugin.getBoosterManager() != null)
                    ? plugin.getBoosterManager().getBoosterMultiplier(island, type) : 1.0;
            String activeLine = (currentMult > 1.0) ? "§a✓ ACTIVE (" + String.format("%.1f", currentMult) + "x)" : "§7Inactive";

            meta.setLore(Arrays.asList(
                "§7" + type.getDescription(),
                "",
                activeLine,
                "",
                "§aShort (" + shortDur + "m): §e$" + shortPrice,
                "§aMedium (" + medDur + "m): §e$" + medPrice,
                "§aLong (" + longDur + "m): §e$" + longPrice,
                "",
                "§7Multiplier: §b" + String.format("%.1f", multiplier) + "x",
                "§7Click to buy §aShort §7duration"
            ));

            // PDC for robust click handling
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(BOOSTER_TYPE_KEY, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§6§lIsland Boosters")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;

        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Close button
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // PDC lookup for booster type (robust, no slot math)
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String typeName = pdc.get(BOOSTER_TYPE_KEY, PersistentDataType.STRING);
        if (typeName == null) return;

        BoosterType type;
        try {
            type = BoosterType.valueOf(typeName);
        } catch (IllegalArgumentException ex) {
            return;
        }

        // Buy shortest duration (shop default)
        long duration = plugin.getConfig().getLong("boosters.durations.short", 30) * 60 * 1000;
        int price = plugin.getConfig().getInt("boosters.prices." + type.name() + ".short", 5000);
        double multiplier = plugin.getConfig().getDouble("boosters.multipliers." + type.name(), 2.0);

        GridPosition pos = island.getGridPosition();

        plugin.getIslandBankManager().getBank(pos).thenAccept(bank -> {
            if (bank.getBalance() < price) {
                player.sendMessage("§cYour island does not have enough balance for this booster.");
                return;
            }

            plugin.getIslandBankManager().withdraw(pos, price).thenAccept(success -> {
                if (success) {
                    plugin.getBoosterManager().activateBooster(island, type, multiplier, duration);
                    player.sendMessage("§aPurchased §e" + type.getDisplayName() + " §abooster §7(Short)!");
                    // Refresh GUI with updated balance/active state
                    plugin.getThreadSafety().runOnMainThread(() -> open(player, island));
                } else {
                    player.sendMessage("§cPurchase failed.");
                }
            });
        });
    }
}