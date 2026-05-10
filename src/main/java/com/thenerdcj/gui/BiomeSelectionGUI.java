package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
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
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Open biome selection GUI for the given dimension context.
     * This is the main method - fully supports multi-dimension islands.
     */
    public void open(Player player, boolean isReset, World.Environment dimension) {
        if (!player.hasPermission("foliasb.donor") && !player.hasPermission("foliasb.donor.biome")) {
            player.sendMessage("§cOnly donors can select custom biomes!");
            return;
        }

        String dimName = dimension.name();
        String dimDisplay = switch (dimension) {
            case NETHER -> "§cNether";
            case THE_END -> "§5End";
            default -> "§aOverworld";
        };

        Inventory gui = Bukkit.createInventory(null, 45, GUI_TITLE + 
            (isReset ? " §7(Reset " + dimDisplay + "§7)" : " §7(" + dimDisplay + "§7)"));

        gui.setItem(4, createTitleItem(isReset, dimDisplay));

        int playerMainLevel = getPlayerIslandLevel(player); // always main (NORMAL) for gating higher dims

        if (dimension == World.Environment.NORMAL) {
            // Overworld biomes - all available to donors (matches BiomeTemplate)
            gui.setItem(10, createBiomeItem(Material.GRASS_BLOCK, "§aPlains", "PLAINS", dimension, isReset));
            gui.setItem(12, createBiomeItem(Material.OAK_LOG, "§2Forest", "FOREST", dimension, isReset));
            gui.setItem(14, createBiomeItem(Material.SAND, "§eDesert", "DESERT", dimension, isReset));
            gui.setItem(16, createBiomeItem(Material.SPRUCE_LOG, "§bTaiga", "TAIGA", dimension, isReset));
            gui.setItem(20, createBiomeItem(Material.JUNGLE_LOG, "§2Jungle", "JUNGLE", dimension, isReset));

            // Info about higher dimensions (locked conceptually - use /is create nether/end once unlocked)
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
            // Nether-specific (only NETHER_WASTES supported per BiomeTemplate)
            if (playerMainLevel >= 15) {
                gui.setItem(22, createBiomeItem(Material.NETHERRACK, "§cNether Wastes", "NETHER_WASTES", dimension, isReset));
            } else {
                gui.setItem(22, createLockedItem("§cNether Wastes", "§7Requires main island level 15+"));
            }
        } else if (dimension == World.Environment.THE_END) {
            // End-specific
            if (playerMainLevel >= 30) {
                gui.setItem(22, createBiomeItem(Material.END_STONE, "§5The End", "THE_END", dimension, isReset));
            } else {
                gui.setItem(22, createLockedItem("§5The End", "§7Requires main island level 30+"));
            }
        }

        // Fill remaining with glass panes
        for (int i = 0; i < 45; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, createGlassPane());
            }
        }

        player.openInventory(gui);
    }

    /**
     * Convenience overload - uses player's current world environment as dimension context.
     * Works well for reset flows where player is still in the target dimension.
     */
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

    /**
     * Creates a clickable biome item that stores all necessary context (biome, target dim, reset flag)
     * in PersistentDataContainer for robust click handling.
     */
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

        // Store all state in PDC so click handler doesn't rely on fragile title parsing
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

    /**
     * Gets the level of the player's MAIN (Overworld/NORMAL) island for progression gating.
     * Higher dimension unlocks are based on main island progress.
     */
    private int getPlayerIslandLevel(Player player) {
        com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(
            player.getUniqueId(), World.Environment.NORMAL);
        return island != null ? island.getLevel() : 1;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(GUI_TITLE)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.BARRIER) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        var container = meta.getPersistentDataContainer();

        String biome = container.get(new NamespacedKey(plugin, "biome_key"), PersistentDataType.STRING);
        String dimName = container.get(new NamespacedKey(plugin, "target_dimension"), PersistentDataType.STRING);
        String isResetStr = container.get(new NamespacedKey(plugin, "is_reset"), PersistentDataType.STRING);

        if (biome != null && dimName != null) {
            player.closeInventory();

            World.Environment targetDim;
            try {
                targetDim = World.Environment.valueOf(dimName);
            } catch (IllegalArgumentException e) {
                targetDim = World.Environment.NORMAL; // fallback
            }

            boolean isResetMode = Boolean.parseBoolean(isResetStr != null ? isResetStr : "false");

            if (isResetMode) {
                plugin.getIslandManager().resetIslandWithBiome(player, biome, targetDim);
            } else {
                // createIsland is async but manager handles success messages internally
                plugin.getIslandManager().createIsland(player, biome, targetDim);
            }
        }
    }
}
