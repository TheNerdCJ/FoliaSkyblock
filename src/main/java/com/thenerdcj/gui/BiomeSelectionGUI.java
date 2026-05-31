package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
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

import java.util.Arrays;

public class BiomeSelectionGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final String GUI_TITLE = "§6§lSelect Your Island Biome";

    public BiomeSelectionGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public BiomeSelectionGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, boolean isReset, World.Environment dimension) {
        if (!player.hasPermission("foliasb.donor") && !player.hasPermission("foliasb.donor.biome")) {
            player.sendMessage("§cOnly donors can select custom biomes!");
            return;
        }

        String dimDisplay = switch (dimension) {
            case NETHER -> "§cNether";
            case THE_END -> "§5End";
            default -> "§aOverworld";
        };

        Inventory gui = Bukkit.createInventory(null, 45, GUI_TITLE +
                (isReset ? " §7(Reset " + dimDisplay + "§7)" : " §7(" + dimDisplay + "§7)"));

        gui.setItem(4, createTitleItem(isReset, dimDisplay));

        int playerMainLevel = getPlayerIslandLevel(player);

        if (dimension == World.Environment.NORMAL) {
            gui.setItem(10, createBiomeItem(Material.GRASS_BLOCK, "§aPlains", "PLAINS", dimension, isReset));
            gui.setItem(12, createBiomeItem(Material.OAK_LOG, "§2Forest", "FOREST", dimension, isReset));
            gui.setItem(14, createBiomeItem(Material.SAND, "§eDesert", "DESERT", dimension, isReset));
            gui.setItem(16, createBiomeItem(Material.SPRUCE_LOG, "§bTaiga", "TAIGA", dimension, isReset));
            gui.setItem(20, createBiomeItem(Material.JUNGLE_LOG, "§2Jungle", "JUNGLE", dimension, isReset));

            if (playerMainLevel < 15) {
                gui.setItem(28, createLockedItem("§cNether Island", "§7Reach level 15 on main island to unlock"));
            } else {
                gui.setItem(28, createInfoItem("§cNether Island", "§7Use §b/is create nether §7(donor for biome)"));
            }
            if (playerMainLevel < 30) {
                gui.setItem(34, createLockedItem("§5End Island", "§7Reach level 30 on main island to unlock"));
            } else {
                gui.setItem(34, createInfoItem("§5End Island", "§7Use §b/is create end §7(donor for biome)"));
            }
        } else if (dimension == World.Environment.NETHER) {
            if (playerMainLevel >= 15) {
                gui.setItem(22, createBiomeItem(Material.NETHERRACK, "§cNether Wastes", "NETHER_WASTES", dimension, isReset));
            } else {
                gui.setItem(22, createLockedItem("§cNether Wastes", "§7Requires main island level 15+"));
            }
        } else if (dimension == World.Environment.THE_END) {
            if (playerMainLevel >= 30) {
                gui.setItem(22, createBiomeItem(Material.END_STONE, "§5The End", "THE_END", dimension, isReset));
            } else {
                gui.setItem(22, createLockedItem("§5The End", "§7Requires main island level 30+"));
            }
        }

        for (int i = 0; i < 45; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, createGlassPane());
            }
        }

        player.openInventory(gui);
    }

    public void open(Player player, boolean isReset) {
        open(player, isReset, player.getWorld().getEnvironment());
    }

    public void open(Player player) {
        open(player, false);
    }

    private ItemStack createTitleItem(boolean isReset, String dimDisplay) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (isReset) {
            meta.setDisplayName("§6§lChoose New Biome for Reset");
            meta.setLore(Arrays.asList(
                    "§7Dimension: " + dimDisplay,
                    "§cWarning: Island will be reset with new biome!",
                    "§7Only donors can choose custom biomes."
            ));
        } else {
            meta.setDisplayName("§6§lChoose Your Starting Biome");
            meta.setLore(Arrays.asList(
                    "§7Dimension: " + dimDisplay,
                    "§7Donor perk: pick your favorite biome"
            ));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBiomeItem(Material material, String name, String biomeKey,
                                      World.Environment dimension, boolean isReset) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (isReset) {
            meta.setLore(Arrays.asList("§7Click to §creset§7 your " + dimension.name() + " island to this biome"));
        } else {
            meta.setLore(Arrays.asList("§7Click to create your " + dimension.name() + " island with this biome"));
        }

        NamespacedKey biomeKeyNS = new NamespacedKey(plugin, "biome_key");
        meta.getPersistentDataContainer().set(biomeKeyNS, PersistentDataType.STRING, biomeKey);

        NamespacedKey dimKey = new NamespacedKey(plugin, "target_dimension");
        meta.getPersistentDataContainer().set(dimKey, PersistentDataType.STRING, dimension.name());

        NamespacedKey resetKey = new NamespacedKey(plugin, "is_reset");
        meta.getPersistentDataContainer().set(resetKey, PersistentDataType.STRING, String.valueOf(isReset));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLockedItem(String name, String lore) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore, "§7Progress on your main island to unlock!"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(String name, String lore) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlassPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    // ==================== FIXED METHOD ====================
    private int getPlayerIslandLevel(Player player) {
        if (player == null) return 1;

        // Always check MAIN island (Overworld) for progression gating
        Island mainIsland = plugin.getIslandManager().getIsland(
                player.getUniqueId(),
                World.Environment.NORMAL
        );

        if (mainIsland != null) {
            return mainIsland.getLevel();
        }

        // Fallback: check any dimension
        for (World.Environment dim : World.Environment.values()) {
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), dim);
            if (island != null) {
                return island.getLevel();
            }
        }

        return 1; // New player
    }

    // ==================== CLICK HANDLER ====================
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith(GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE || clicked.getType() == Material.BARRIER) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        NamespacedKey biomeKey = new NamespacedKey(plugin, "biome_key");
        NamespacedKey dimKey = new NamespacedKey(plugin, "target_dimension");
        NamespacedKey resetKey = new NamespacedKey(plugin, "is_reset");

        String biome = meta.getPersistentDataContainer().get(biomeKey, PersistentDataType.STRING);
        String dimStr = meta.getPersistentDataContainer().get(dimKey, PersistentDataType.STRING);
        String isResetStr = meta.getPersistentDataContainer().get(resetKey, PersistentDataType.STRING);

        if (biome == null || dimStr == null) return;

        World.Environment dimension = World.Environment.valueOf(dimStr);
        boolean isReset = Boolean.parseBoolean(isResetStr != null ? isResetStr : "false");

        player.closeInventory();

        if (isReset) {
            plugin.getIslandManager().resetIslandWithBiome(player, biome, dimension);
        } else {
            plugin.getIslandManager().createIsland(player, biome, dimension);
        }
    }
}