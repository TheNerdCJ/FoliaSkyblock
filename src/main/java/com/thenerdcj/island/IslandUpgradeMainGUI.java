package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.gui.BaseGUI;
import com.thenerdcj.gui.GUIUtils;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island upgrades (BaseGUI + PDC). Opened via {@link IslandUpgradeGUI#open(Player, Island)}.
 */
public class IslandUpgradeMainGUI extends BaseGUI {

    private final Map<UUID, Island> sessionIslands = new ConcurrentHashMap<>();

    public IslandUpgradeMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    public IslandUpgradeMainGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lIsland Upgrades";
    }

    @Override
    protected String getActionKeyName() {
        return "island_upgrade_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 0;
    }

    public void open(Player player, Island island) {
        if (island == null) {
            player.sendMessage("§cYou must be on an island to purchase upgrades.");
            return;
        }
        sessionIslands.put(player.getUniqueId(), island);
        GridPosition pos = island.getGridPosition();

        plugin.getEconomyManager().getIslandBalance(pos).thenAccept(balance ->
                player.getScheduler().run(plugin, task -> {
                    Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE,
                            MessageUtil.legacy(getTitlePrefix() + " §7(Lv." + island.getLevel() + ")"));

                    gui.setItem(4, GUIUtils.createItem(Material.NETHER_STAR, "§6§lIsland Upgrades",
                            "§7Balance: §e$" + String.format("%.0f", balance)));

                    int slot = 10;
                    for (IslandUpgrade upgrade : IslandUpgrade.values()) {
                        if (slot > 44) {
                            break;
                        }
                        int currentLevel = plugin.getIslandUpgradeManager().getUpgradeLevel(island, upgrade);
                        double cost = upgrade.getCostForLevel(currentLevel);
                        boolean canAfford = balance >= cost;
                        boolean maxed = currentLevel >= upgrade.getMaxLevel();

                        List<String> lore = new ArrayList<>();
                        lore.add("§7" + upgrade.getDescription());
                        lore.add("");
                        String effect = getEffectDescription(upgrade, currentLevel);
                        if (!effect.isEmpty()) {
                            lore.add("§aEffect: §f" + effect);
                        }
                        lore.add("§7Level: §e" + currentLevel + "§7/§e" + upgrade.getMaxLevel());
                        lore.add("§7Cost: §e$" + String.format("%.0f", cost));
                        if (maxed) {
                            lore.add("§c§lMAX LEVEL REACHED");
                        } else if (canAfford) {
                            lore.add("§a§lClick to Purchase!");
                        } else {
                            lore.add("§c§lCannot Afford");
                        }

                        ItemStack item = GUIUtils.createItem(getUpgradeMaterial(upgrade),
                                "§e§l" + upgrade.getDisplayName() + " §7[" + currentLevel + "/" + upgrade.getMaxLevel() + "]",
                                lore.toArray(new String[0]));
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null && !maxed) {
                            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                                    "UPGRADE_" + upgrade.name());
                            item.setItemMeta(meta);
                        }
                        gui.setItem(slot++, item);
                    }

                    gui.setItem(CLOSE_SLOT, GUIUtils.createNavButton(Material.BARRIER, "§c§lClose", ACTION_KEY, "CLOSE"));
                    player.openInventory(gui);
                }, null));
    }

    @Override
    public void open(Player player, int page) {
        Island island = sessionIslands.get(player.getUniqueId());
        if (island == null) {
            island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        }
        open(player, island);
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        Island island = sessionIslands.get(player.getUniqueId());
        if (island == null) {
            island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        }
        if (island == null) {
            return;
        }
        final Island targetIsland = island;
        if (action != null && action.startsWith("UPGRADE_")) {
            try {
                IslandUpgrade upgrade = IslandUpgrade.valueOf(action.substring(8));
                plugin.getIslandUpgradeManager().purchaseUpgrade(targetIsland, upgrade, player);
                player.closeInventory();
                player.getScheduler().runDelayed(plugin, task -> open(player, targetIsland), null, 5L);
            } catch (IllegalArgumentException ignored) {
            }
        }
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
        if (level <= 0) {
            return "";
        }
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
}