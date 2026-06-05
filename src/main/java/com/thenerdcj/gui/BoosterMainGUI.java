package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.booster.BoosterType;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island booster shop (BaseGUI + PDC). Opened via {@link BoosterGUI#open(Player, Island)}.
 */
public class BoosterMainGUI extends BaseGUI {

    private final NamespacedKey BOOSTER_TYPE_KEY;
    private final Map<UUID, Island> sessionIslands = new ConcurrentHashMap<>();

    public BoosterMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
        this.BOOSTER_TYPE_KEY = new NamespacedKey(plugin, "booster_type");
    }

    public BoosterMainGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
        this.BOOSTER_TYPE_KEY = new NamespacedKey(plugin, "booster_type");
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lIsland Boosters";
    }

    @Override
    protected String formatTitle(Player player, int page) {
        return "§6§lIsland Boosters §7(Shop)";
    }

    @Override
    protected String getActionKeyName() {
        return "booster_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 15;
    }

    @Override
    protected int getTotalPages(Player player) {
        return 1;
    }

    public void open(Player player, Island island) {
        if (island == null) {
            player.sendMessage("§cYou must be on an island to use boosters.");
            return;
        }
        sessionIslands.put(player.getUniqueId(), island);
        GridPosition pos = island.getGridPosition();
        plugin.getIslandBankManager().getBank(pos).thenAccept(bank ->
                plugin.getThreadSafety().runOnMainThread(() -> {
                    playerPages.put(player.getUniqueId(), 0);
                    Inventory gui = Bukkit.createInventory(null, getInventorySize(player),
                            MessageUtil.legacy(formatTitle(player, 0)));
                    addHeader(gui, player, 0, bank.getBalance(), island);
                    populatePage(gui, player, 0, island, bank.getBalance());
                    addStandardNavigation(gui, 0, 1);
                    player.openInventory(gui);
                }));
    }

    private void addHeader(Inventory gui, Player player, int page, double balance, Island island) {
        gui.setItem(4, GUIUtils.createItem(Material.GOLD_INGOT, "§e§lIsland Balance",
                "§6$" + String.format("%,.0f", balance),
                "§7Click boosters below to purchase"));
        gui.setItem(0, GUIUtils.createItem(Material.BOOK, "§a§lBooster Info",
                "§7Boosters provide temporary multipliers",
                "§7that stack on top of island upgrades.",
                "§7Active boosters apply automatically."));
    }

    @Override
    protected void addHeader(Inventory gui, Player player, int page) {
        // Filled in custom open path
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        Island island = sessionIslands.get(player.getUniqueId());
        if (island == null) {
            return;
        }
        populatePage(gui, player, page, island, 0);
    }

    private void populatePage(Inventory gui, Player player, int page, Island island, double balance) {
        int[] slots = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23, 28, 29, 30, 31, 32};
        int idx = 0;
        for (BoosterType type : BoosterType.values()) {
            if (idx >= slots.length) {
                break;
            }
            gui.setItem(slots[idx], createBoosterItem(type, island, balance));
            idx++;
        }
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        Island island = sessionIslands.get(player.getUniqueId());
        if (island == null) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }
        String typeName = meta.getPersistentDataContainer().get(BOOSTER_TYPE_KEY, PersistentDataType.STRING);
        if (typeName == null) {
            return;
        }
        BoosterType type;
        try {
            type = BoosterType.valueOf(typeName);
        } catch (IllegalArgumentException ex) {
            return;
        }

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
                    plugin.getThreadSafety().runOnMainThread(() -> open(player, island));
                } else {
                    player.sendMessage("§cPurchase failed.");
                }
            });
        });
    }

    private ItemStack createBoosterItem(BoosterType type, Island island, double balance) {
        Material icon = switch (type) {
            case CROP_GROWTH -> Material.WHEAT;
            case SPAWNER_RATE -> Material.SPAWNER;
            case ISLAND_XP -> Material.EXPERIENCE_BOTTLE;
            case MONEY_EARN -> Material.GOLD_INGOT;
            case MINION_SPEED -> Material.IRON_PICKAXE;
        };

        double multiplier = plugin.getConfig().getDouble("boosters.multipliers." + type.name(), 2.0);
        long shortDur = plugin.getConfig().getLong("boosters.durations.short", 30);
        long medDur = plugin.getConfig().getLong("boosters.durations.medium", 60);
        long longDur = plugin.getConfig().getLong("boosters.durations.long", 120);

        int shortPrice = plugin.getConfig().getInt("boosters.prices." + type.name() + ".short", 5000);
        int medPrice = plugin.getConfig().getInt("boosters.prices." + type.name() + ".medium", 9000);
        int longPrice = plugin.getConfig().getInt("boosters.prices." + type.name() + ".long", 16000);

        double currentMult = (island != null && plugin.getBoosterManager() != null)
                ? plugin.getBoosterManager().getBoosterMultiplier(island, type) : 1.0;
        String activeLine = (currentMult > 1.0)
                ? "§a✓ ACTIVE (" + String.format("%.1f", currentMult) + "x)" : "§7Inactive";

        ItemStack item = GUIUtils.createItem(icon, "§e§l" + type.getDisplayName(),
                "§7" + type.getDescription(),
                "",
                activeLine,
                "",
                "§aShort (" + shortDur + "m): §e$" + shortPrice,
                "§aMedium (" + medDur + "m): §e$" + medPrice,
                "§aLong (" + longDur + "m): §e$" + longPrice,
                "",
                "§7Multiplier: §b" + String.format("%.1f", multiplier) + "x",
                "§7Click to buy §aShort §7duration");

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(BOOSTER_TYPE_KEY, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        return item;
    }
}