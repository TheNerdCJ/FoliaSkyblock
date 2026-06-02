package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IslandUpgradeGUI implements Listener {

    private final FoliaSkyblock plugin;

    public IslandUpgradeGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public IslandUpgradeGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, Island island) {
        Inventory gui = Bukkit.createInventory(null, 54,
                MessageUtil.legacy("§6§lIsland Upgrades §7(Lv." + island.getLevel() + ")"));

        GridPosition pos = island.getGridPosition();

        // Async balance fetch
        plugin.getEconomyManager().getIslandBalance(pos).thenAccept(balance -> {

            // Schedule on the player's region (Folia optimized)
            player.getScheduler().run(plugin, scheduledTask -> {

                gui.setItem(4, createItem(Material.NETHER_STAR, "§6§lIsland Upgrades",
                        "§7Balance: §e$" + String.format("%.0f", balance)));

                int slot = 10;
                for (IslandUpgrade upgrade : IslandUpgrade.values()) {
                    if (slot > 44) break;

                    int currentLevel = plugin.getIslandUpgradeManager().getUpgradeLevel(island.getId(), upgrade);
                    double cost = upgrade.getCostForLevel(currentLevel);
                    boolean canAfford = balance >= cost;
                    boolean maxed = currentLevel >= upgrade.getMaxLevel();

                    Material material = getUpgradeMaterial(upgrade);
                    String name = "§e§l" + upgrade.getDisplayName() +
                            " §7[" + currentLevel + "/" + upgrade.getMaxLevel() + "]";

                    List<String> lore = new ArrayList<>();
                    lore.add("§7" + upgrade.getDescription());
                    lore.add("");

                    // Show actual effect values (polished, reference: Iridium/Superior style)
                    String effect = getEffectDescription(upgrade, currentLevel);
                    if (!effect.isEmpty()) {
                        lore.add("§aEffect: §f" + effect);
                    }

                    lore.add("§7Level: §e" + currentLevel + "§7/§e" + upgrade.getMaxLevel());
                    lore.add("§7Cost: §e$" + String.format("%.0f", cost));

                    if (maxed) lore.add("§c§lMAX LEVEL REACHED");
                    else if (canAfford) lore.add("§a§lClick to Purchase!");
                    else lore.add("§c§lCannot Afford");

                    gui.setItem(slot++, createItem(material, name, lore.toArray(new String[0])));
                }

                gui.setItem(49, createItem(Material.BARRIER, "§c§lClose"));
                player.openInventory(gui);

            }, null);
        });
    }

    private Material getUpgradeMaterial(IslandUpgrade upgrade) {
        return switch (upgrade) {
            case ISLAND_SIZE -> Material.DIAMOND_PICKAXE;
            case CROP_GROWTH -> Material.WHEAT;
            case SPAWNER_RATE -> Material.SPAWNER;
            case VAULT_SLOTS -> Material.CHEST;
            case WARDROBE_SLOTS -> Material.ARMOR_STAND;
            case AUTO_SELLER -> Material.GOLDEN_PICKAXE;
            case MOB_CAP -> Material.NETHERRACK;
            case HOPPER_LIMIT -> Material.ENDER_PEARL;
            default -> Material.PAPER;
        };
    }

    private String getEffectDescription(IslandUpgrade upgrade, int level) {
        if (level <= 0) return "";

        return switch (upgrade) {
            case CROP_GROWTH -> "+" + (level * 25) + "% growth speed";
            case ORE_GENERATOR -> "Better ores from generators (Lv." + level + ")";
            case ISLAND_SIZE -> {
                int perLevel = plugin.getConfig().getInt("upgrades.island-size.radius-per-level", 8);
                yield "+" + (level * perLevel) + " block radius";
            }
            case MINION_SLOTS -> "+" + level + " minion slots";
            case MEMBER_LIMIT -> "+" + level + " member limit";
            case WARDROBE_SLOTS -> "+" + (level * 2) + " wardrobe slots";
            case SPAWNER_RATE -> "+" + (level * 15) + "% spawner speed";
            default -> "";
        };
    }

    private ItemStack createItem(Material material, String legacyName, String... legacyLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(legacyName));

        if (legacyLore.length > 0) {
            List<Component> loreList = new ArrayList<>();
            for (String line : legacyLore) {
                loreList.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            meta.lore(loreList);
        }

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component viewTitle = event.getView().title();
        String title = LegacyComponentSerializer.legacySection().serialize(viewTitle);

        if (!title.contains("Island Upgrades")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String itemName = "";
        if (clicked.getItemMeta() != null && clicked.getItemMeta().displayName() != null) {
            itemName = LegacyComponentSerializer.legacySection()
                    .serialize(Objects.requireNonNull(clicked.getItemMeta().displayName()));
        }

        if (itemName.contains("Close")) {
            player.closeInventory();
            return;
        }

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;

        for (IslandUpgrade upgrade : IslandUpgrade.values()) {
            if (itemName.contains(upgrade.getDisplayName())) {
                plugin.getIslandUpgradeManager().purchaseUpgrade(player, island, upgrade);
                player.closeInventory();

                // Use player's scheduler for GUI refresh (Folia optimized)
                player.getScheduler().runDelayed(plugin,
                        scheduledTask -> open(player, island), null, 5L);
                return;
            }
        }
    }
}