package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI for selecting a starting (or reset) island biome.
 * Donor-only cosmetic feature (Play-to-Win compliant — no mechanical advantage).
 *
 * Deep modernization pass:
 * - All repetitive manual ItemStack + ItemMeta + setLore blocks replaced with GUIUtils.createItem.
 * - Dynamic titles now fully routed through MessageUtil.legacy(...).
 * - Resilient title guard in click handler.
 * - Introduced attachBiomePDCs helper for the three required NamespacedKeys (consistent with recent reset GUIs).
 * - Preserved all donor gating, level-based dimension unlocking, isReset vs create flows, and PDC-driven action routing.
 */
public class BiomeSelectionGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final String GUI_TITLE = "§6§lSelect Your Island Biome";

    // Temporary storage for donors who chose to reroll personality during a reset flow
    private final Map<UUID, World.Environment> pendingPersonalityRerolls = new ConcurrentHashMap<>();

    public BiomeSelectionGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public BiomeSelectionGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    /**
     * Opens the biome selection GUI.
     * @param player     The player opening the GUI
     * @param isReset    true = this is part of a per-dimension reset flow
     * @param dimension  The target dimension (Overworld/Nether/End)
     */
    public void open(Player player, boolean isReset, World.Environment dimension) {
        // Only donors can choose custom biomes (on both creation and reset)
        if (!player.hasPermission("foliasb.donor") && !player.hasPermission("foliasb.donor.biome")) {
            player.sendMessage("§cOnly donors can select custom biomes on island creation or reset!");
            return;
        }

        String dimDisplay = switch (dimension) {
            case NETHER -> "§cNether";
            case THE_END -> "§5End";
            default -> "§aOverworld";
        };

        // Modernized: full dynamic title through MessageUtil.legacy
        String fullTitle = GUI_TITLE +
                (isReset ? " §7(Reset " + dimDisplay + "§7)" : " §7(" + dimDisplay + "§7)");
        Inventory gui = Bukkit.createInventory(null, 45, MessageUtil.legacy(fullTitle));

        gui.setItem(4, createTitleItem(isReset, dimDisplay));

        int playerMainLevel = getPlayerMainIslandLevel(player);

        if (dimension == World.Environment.NORMAL) {
            gui.setItem(10, createBiomeItem(Material.GRASS_BLOCK, "§aPlains", "PLAINS", dimension, isReset));
            gui.setItem(12, createBiomeItem(Material.OAK_LOG, "§2Forest", "FOREST", dimension, isReset));
            gui.setItem(14, createBiomeItem(Material.SAND, "§eDesert", "DESERT", dimension, isReset));
            gui.setItem(16, createBiomeItem(Material.SPRUCE_LOG, "§bTaiga", "TAIGA", dimension, isReset));
            gui.setItem(20, createBiomeItem(Material.JUNGLE_LOG, "§2Jungle", "JUNGLE", dimension, isReset));

            // Nether & End unlock information (based on main island level)
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

        // Fill empty slots with glass panes - modernized
        ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }

        // Donor-only: Reroll personality/style option (purely cosmetic, during reset)
        if (isReset && (player.hasPermission("foliasb.donor") || player.hasPermission("foliasb.donor.biome"))) {
            ItemStack reroll = GUIUtils.createItem(Material.NETHER_STAR, "§d§lReroll Island Personality",
                    "§7Donor Perk",
                    "§7Click to get a completely different island",
                    "§7layout, terrain shape, and features",
                    "§7while keeping your chosen biome.",
                    "",
                    "§cThis is cosmetic only (Play-to-Win safe)");
            NamespacedKey rerollKey = new NamespacedKey(plugin, "reroll_personality");
            ItemMeta rerollMeta = reroll.getItemMeta();
            if (rerollMeta != null) {
                rerollMeta.getPersistentDataContainer().set(rerollKey, PersistentDataType.STRING, "true");
                rerollMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "target_dimension"), PersistentDataType.STRING, dimension.name());
                reroll.setItemMeta(rerollMeta);
            }
            gui.setItem(40, reroll);
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
        String name = isReset ? "§6§lChoose New Biome for Reset" : "§6§lChoose Your Starting Biome";
        String[] lore = isReset
                ? new String[]{
                        "§7Dimension: " + dimDisplay,
                        "§cThis will reset your island in this dimension with the new biome!",
                        "§7Only donors can choose custom biomes on reset."
                  }
                : new String[]{
                        "§7Dimension: " + dimDisplay,
                        "§7Donor perk: pick your favorite starting biome"
                  };

        ItemStack item = GUIUtils.createItem(Material.NETHER_STAR, name, lore);
        return item; // No extra PDC needed on the header
    }

    private ItemStack createBiomeItem(Material material, String name, String biomeKey,
                                      World.Environment dimension, boolean isReset) {
        String[] lore = isReset
                ? new String[]{
                        "§7Click to §creset§7 your " + dimension.name() + " island to this biome",
                        "§cAll current progress in this dimension will be lost!"
                  }
                : new String[]{"§7Click to create your " + dimension.name() + " island with this biome"};

        ItemStack item = GUIUtils.createItem(material, name, lore);

        // Attach the three required PDCs (biome choice + context for the click handler)
        attachBiomePDCs(item, biomeKey, dimension, isReset);
        return item;
    }

    private void attachBiomePDCs(ItemStack item, String biomeKey, World.Environment dimension, boolean isReset) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        NamespacedKey biomeKeyNS = new NamespacedKey(plugin, "biome_key");
        meta.getPersistentDataContainer().set(biomeKeyNS, PersistentDataType.STRING, biomeKey);

        NamespacedKey dimKey = new NamespacedKey(plugin, "target_dimension");
        meta.getPersistentDataContainer().set(dimKey, PersistentDataType.STRING, dimension.name());

        NamespacedKey resetKey = new NamespacedKey(plugin, "is_reset");
        meta.getPersistentDataContainer().set(resetKey, PersistentDataType.STRING, String.valueOf(isReset));

        item.setItemMeta(meta);
    }

    private ItemStack createLockedItem(String name, String lore) {
        return GUIUtils.createItem(Material.BARRIER, name, lore, "§7Progress on your main island to unlock!");
    }

    private ItemStack createInfoItem(String name, String lore) {
        return GUIUtils.createItem(Material.BOOK, name, lore);
    }

    /**
     * Gets the player's MAIN island level (Overworld).
     * This is used for progression gating (Nether at lvl 15, End at lvl 30).
     */
    private int getPlayerMainIslandLevel(Player player) {
        if (player == null) return 0;

        Island mainIsland = plugin.getIslandManager().getIsland(player.getUniqueId(), World.Environment.NORMAL);
        if (mainIsland != null) {
            return mainIsland.getLevel();
        }

        // Fallback: check any dimension
        for (World.Environment dim : new World.Environment[]{World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END}) {
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), dim);
            if (island != null) {
                return island.getLevel();
            }
        }
        return 0;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Resilient title check (handles dynamic " (Reset ...)" suffix)
        if (!event.getView().getTitle().startsWith("§6§lSelect Your Island Biome")) return;

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

        if (biome == null || dimStr == null) {
            handleLockedClick(player, clicked);
            return;
        }

        World.Environment dimension = World.Environment.valueOf(dimStr);
        boolean isReset = Boolean.parseBoolean(isResetStr != null ? isResetStr : "false");

        // Check if this was the reroll personality button
        NamespacedKey rerollKey = new NamespacedKey(plugin, "reroll_personality");
        String rerollFlag = meta.getPersistentDataContainer().get(rerollKey, PersistentDataType.STRING);
        if ("true".equals(rerollFlag) && isReset) {
            pendingPersonalityRerolls.put(player.getUniqueId(), dimension);
            SoundUtil.click(player);
            // Re-open the same GUI so they can now pick the biome (or the same one)
            plugin.getThreadSafety().runOnMainThread(() -> open(player, true, dimension));
            return;
        }

        player.closeInventory();

        boolean wantsReroll = pendingPersonalityRerolls.remove(player.getUniqueId()) != null && isReset;

        if (isReset) {
            plugin.getIslandManager().resetIslandWithBiome(player, biome, dimension, wantsReroll);
        } else {
            plugin.getIslandManager().createIsland(player, biome, dimension);
        }
    }

    // Fallback: if someone clicks a locked item (no biome key), give helpful feedback
    private void handleLockedClick(Player player, ItemStack clicked) {
        SoundUtil.error(player);
        String name = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "";
        if (name.contains("Nether") || name.contains("End")) {
            MessageUtil.sendMessage(player, "§cThat dimension is locked. Progress your main island level to unlock it!");
        } else {
            MessageUtil.sendMessage(player, "§cThis option is currently locked.");
        }
    }
}