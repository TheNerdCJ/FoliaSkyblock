package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandSettings;
import com.thenerdcj.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
 * Island settings (BaseGUI + PDC toggles). Opened via {@link IslandSettingsGUI#open(Player, Island)}.
 */
public class IslandSettingsMainGUI extends BaseGUI {

    private final Map<UUID, GridPosition> sessionGrid = new ConcurrentHashMap<>();

    public IslandSettingsMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    public IslandSettingsMainGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lIsland Settings";
    }

    @Override
    protected String getActionKeyName() {
        return "island_settings_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 0;
    }

    @Override
    protected int getTotalPages(Player player) {
        return 1;
    }

    @Override
    public void open(Player player, int page) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null) {
            open(player, island);
        }
    }

    public void open(Player player, Island island) {
        if (island == null) {
            player.sendMessage("§cYou must be on an island to change settings.");
            return;
        }
        GridPosition pos = island.getGridPosition();
        plugin.getIslandSettingsManager().getSettings(pos).thenAccept(settings ->
                plugin.getThreadSafety().runOnMainThread(() -> {
                    sessionGrid.put(player.getUniqueId(), pos);
                    Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(getTitlePrefix()));
                    buildInventory(gui, player, island, settings);
                    ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
                    for (int i = 0; i < INVENTORY_SIZE; i++) {
                        if (gui.getItem(i) == null) {
                            gui.setItem(i, glass);
                        }
                    }
                    player.openInventory(gui);
                }));
    }

    private void buildInventory(Inventory gui, Player player, Island island, IslandSettings settings) {
        gui.setItem(4, GUIUtils.createItem(Material.NETHER_STAR, "§6§lIsland Settings",
                "§7Configure your island's behavior"));

        gui.setItem(10, toggleButton(Material.DIAMOND_SWORD, "§cPvP",
                "§7Allow players to fight on your island", settings.isPvpEnabled(), "TOGGLE_PVP"));
        gui.setItem(12, toggleButton(Material.PLAYER_HEAD, "§aVisitors",
                "§7Allow other players to visit your island", settings.isVisitorsAllowed(), "TOGGLE_VISITORS"));
        gui.setItem(14, toggleButton(Material.TNT, "§cExplosions",
                "§7Allow explosions on your island", settings.isExplosionsEnabled(), "TOGGLE_EXPLOSIONS"));
        gui.setItem(16, toggleButton(Material.FLINT_AND_STEEL, "§6Fire Spread",
                "§7Allow fire to spread on your island", settings.isFireSpreadEnabled(), "TOGGLE_FIRE"));
        gui.setItem(19, toggleButton(Material.ZOMBIE_HEAD, "§5Mob Spawning",
                "§7Allow hostile mobs to spawn", settings.isMobSpawningEnabled(), "TOGGLE_MOBS"));
        gui.setItem(21, toggleButton(Material.WHEAT, "§2Crop Trampling",
                "§7Allow players to trample crops", settings.isCropTramplingEnabled(), "TOGGLE_CROPS"));
        gui.setItem(23, toggleButton(Material.COW_SPAWN_EGG, "§eAnimal Spawning",
                "§7Allow passive mobs to spawn", settings.isAnimalSpawningEnabled(), "TOGGLE_ANIMALS"));
        gui.setItem(25, toggleButton(Material.OAK_LEAVES, "§aLeaf Decay",
                "§7Allow leaves to decay naturally", settings.isLeafDecayEnabled(), "TOGGLE_LEAVES"));

        ItemStack borderBtn = GUIUtils.createItem(Material.BLUE_STAINED_GLASS_PANE, "§9Border Color",
                "§7Current: §b" + settings.getBorderColor(),
                "§7Click to cycle presets.",
                "§7Supports hex: #RRGGBB or #RGB (set via /isadmin border setcolor #hex)");
        ItemMeta borderMeta = borderBtn.getItemMeta();
        if (borderMeta != null) {
            borderMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "CYCLE_BORDER_COLOR");
            borderBtn.setItemMeta(borderMeta);
        }
        gui.setItem(28, borderBtn);

        int effectiveRadius = plugin.getIslandUpgradeManager() != null
                ? plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island)
                : settings.getBorderSize();
        gui.setItem(30, GUIUtils.createItem(Material.STICK, "§eBorder Size",
                "§7Current: §f" + effectiveRadius + " blocks",
                "§7Upgrade via §b/is upgrade §7(ISLAND_SIZE)"));

        gui.setItem(32, toggleButton(Material.ENDER_PEARL, "§5Island Warp",
                "§7Allow others to warp to your island", settings.isWarpEnabled(), "TOGGLE_WARP"));
        gui.setItem(34, GUIUtils.createItem(Material.WRITABLE_BOOK, "§dWarp Description",
                "§7" + (settings.getWarpDescription().isEmpty() ? "No description set" : settings.getWarpDescription())));

        gui.setItem(49, GUIUtils.createItem(Material.BOOK, "§eHow Settings Work",
                "§7• Green = Setting is enabled", "§7• Red = Setting is disabled"));
    }

    private ItemStack toggleButton(Material material, String name, String lore, boolean enabled, String action) {
        String status = enabled ? "§a§lENABLED" : "§c§lDISABLED";
        ItemStack item = GUIUtils.createItem(material, name, lore, "", "§7Status: " + status, "§7Click to toggle");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
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

        if ("CYCLE_BORDER_COLOR".equals(action)) {
            plugin.getIslandSettingsManager().getSettings(pos).thenAccept(settings -> {
                String next = switch (settings.getBorderColor().toUpperCase()) {
                    case "BLUE" -> "RED";
                    case "RED" -> "GREEN";
                    case "GREEN" -> "GOLD";
                    case "GOLD" -> "PURPLE";
                    case "PURPLE" -> "PINK";
                    case "PINK" -> "WHITE";
                    default -> "BLUE";
                };
                settings.setBorderColor(next);
                plugin.getIslandSettingsManager().saveSettings(settings);
                player.sendMessage("§aBorder color changed to §b" + next);

                // Immediately refresh the island's worldborder + colored particles for live feedback
                plugin.getThreadSafety().runOnMainThread(() -> {
                    if (player.isOnline()) {
                        var island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        if (island != null && plugin.getBorderVisualManager() != null) {
                            plugin.getBorderVisualManager().forceBorderRefresh(player, island);
                        }
                    }
                    reopen(player);
                });
            });
            return;
        }

        if (action != null && action.startsWith("TOGGLE_")) {
            String key = action.substring(7);
            String displayName = toggleDisplayName(key);
            plugin.getIslandSettingsManager().toggleSetting(pos, key).thenAccept(newValue -> {
                player.sendMessage("§e" + displayName + " has been " + (newValue ? "§aenabled" : "§cdisabled") + "§e.");
                // For border markers (hologram corners), immediately spawn or remove using the hologram system
                if ("BORDER_MARKERS".equals(key) && plugin.getBorderVisualManager() != null) {
                    Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                    if (island != null) {
                        if (newValue) {
                            plugin.getBorderVisualManager().spawnBorderHologramMarkers(island, player);
                        } else {
                            plugin.getBorderVisualManager().removeBorderHologramMarkers(island);
                        }
                    }
                }
                reopen(player);
            });
        }
    }

    private static String toggleDisplayName(String key) {
        return switch (key) {
            case "PVP" -> "PvP";
            case "VISITORS" -> "Visitors";
            case "EXPLOSIONS" -> "Explosions";
            case "FIRE" -> "Fire Spread";
            case "MOBS" -> "Mob Spawning";
            case "CROPS" -> "Crop Trampling";
            case "ANIMALS" -> "Animal Spawning";
            case "LEAVES" -> "Leaf Decay";
            case "WARP" -> "Island Warp";
            case "BORDER_MARKERS" -> "Border Markers (hologram corners)";
            default -> key;
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