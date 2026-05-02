package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandUpgrade;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Island Upgrade GUI - Purchase island upgrades with island balance
 */
public class IslandUpgradeGUI implements Listener {

    private final FoliaSkyblock plugin;

    public IslandUpgradeGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, Island island) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6§lIsland Upgrades §7(" + island.getLevel() + ")");

        gui.setItem(4, createItem(Material.NETHER_STAR, "§6§lIsland Upgrades",
                "§7Upgrade your island with special perks",
                "§7Island Balance: §e$" + String.format("%.0f", getIslandBalance(island))));

        int slot = 10;
        for (IslandUpgrade upgrade : IslandUpgrade.values()) {
            if (slot > 44) break;

            int currentLevel = getUpgradeLevel(island, upgrade);
            boolean canAfford = getIslandBalance(island) >= upgrade.getCost();
            boolean canPurchase = canAfford && currentLevel < upgrade.getMaxLevel();

            Material material = getUpgradeMaterial(upgrade);
            String name = "§e§l" + upgrade.getDisplayName();
            if (currentLevel > 0) name += " §7[" + currentLevel + "/" + upgrade.getMaxLevel() + "]";

            List<String> lore = new ArrayList<>();
            lore.add("§7" + upgrade.getDescription());
            lore.add("");
            lore.add("§7Level: §e" + currentLevel + "§7/§e" + upgrade.getMaxLevel());
            lore.add("§7Cost: §e$" + String.format("%.0f", upgrade.getCost()));
            lore.add("");

            if (canPurchase) lore.add("§a§lClick to Purchase!");
            else if (currentLevel >= upgrade.getMaxLevel()) lore.add("§c§lMAX LEVEL REACHED");
            else { lore.add("§c§lCannot Afford"); lore.add("§7Need: §c$" + String.format("%.0f", upgrade.getCost() - getIslandBalance(island))); }

            gui.setItem(slot++, createItem(material, name, lore.toArray(new String[0])));
        }

        gui.setItem(49, createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));
        player.openInventory(gui);
    }

    private double getIslandBalance(Island island) { return 10000.0; }
    private int getUpgradeLevel(Island island, IslandUpgrade upgrade) { return 0; }

    private Material getUpgradeMaterial(IslandUpgrade upgrade) {
        return switch (upgrade) {
            case ISLAND_SIZE -> Material.DIAMOND_PICKAXE;
            case CROP_GROWTH -> Material.WHEAT;
            case SPAWNER_RATE -> Material.SPAWNER;
            case VAULT_SLOTS -> Material.CHEST;
            case AUTO_SELLER -> Material.GOLDEN_PICKAXE;
            case MOB_CAP -> Material.NETHERRACK;
            case HOPPER_LIMIT -> Material.ENDER_PEARL;
            default -> Material.PAPER;
        };
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("§6§lIsland Upgrades")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String itemName = clicked.getItemMeta().getDisplayName();
        if (itemName.contains("Close")) { player.closeInventory(); return; }

        for (IslandUpgrade upgrade : IslandUpgrade.values()) {
            if (itemName.contains(upgrade.getDisplayName())) {
                Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                if (island != null) {
                    plugin.getIslandUpgradeManager().purchaseUpgrade(player, island, upgrade);
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, island), 20L);
                }
                return;
            }
        }
    }
}