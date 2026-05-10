package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.MinionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * MinionsGUI - Interactive GUI for managing island minions.
 * Displays current minion slots usage, available minion types, and allows placing/removing minions.
 * Minion types are determined by the upgrade level and island balance.
 */
public class MinionsGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final MinionManager minionManager;

    public MinionsGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.minionManager = plugin.getMinionManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMinionsGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lMinion Management");

        // Get island context first
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId(), player.getWorld().getEnvironment());
        String islandId = null;
        int maxSlots = 5;
        int placed = 0;
        boolean hasIsland = island != null;
        if (hasIsland) {
            islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
            maxSlots = minionManager.getMaxMinionSlots(island);
            placed = minionManager.getPlacedMinionCount(islandId);
        }

        // Decorative top bar - Modern dark theme (skyblock professional look, consistent with Hypixel/Iridium style)
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName("§8 ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, filler);
        }
        // Side accents for modern polish
        ItemStack accent = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta accentMeta = accent.getItemMeta();
        accentMeta.setDisplayName("§5 ");
        accent.setItemMeta(accentMeta);
        inv.setItem(9, accent);
        inv.setItem(17, accent);

        // Title item (center top) - Modern with XP synergy note
        ItemStack title = new ItemStack(Material.BEACON);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setDisplayName("§6§lMinion Management");
        titleMeta.setLore(Arrays.asList(
            "§7Automate resource production & boost §eIsland XP",
            "§7Minions contribute to island progression (dimensions, bosses)",
            "§7Play to Win: Fuel & slots earned via grind/trade - no P2W"
        ));
        title.setItemMeta(titleMeta);
        inv.setItem(4, title);

        if (!hasIsland) {
            // No island message
            ItemStack noIsland = new ItemStack(Material.BARRIER);
            ItemMeta noMeta = noIsland.getItemMeta();
            noMeta.setDisplayName("§c§lNo Island Found");
            noMeta.setLore(Arrays.asList(
                "§7You need an island in this dimension",
                "§7to manage minions.",
                "",
                "§eUse §6/is create §eor teleport to your island."
            ));
            noIsland.setItemMeta(noMeta);
            inv.setItem(22, noIsland);
            player.openInventory(inv);
            return;
        }

        // Prominent slots usage display (row 1 center)
        ItemStack slotsItem = new ItemStack(Material.CHEST);
        ItemMeta slotsMeta = slotsItem.getItemMeta();
        slotsMeta.setDisplayName("§e§lMinion Slots");
        int fuel = minionManager.getIslandFuel(islandId != null ? islandId : "");
        slotsMeta.setLore(Arrays.asList(
            "§7Active: §f" + placed + " §7/ §f" + maxSlots,
            "§7Fuel: §e" + fuel + " §7units (consumed per production cycle)",
            "",
            "§7Upgrade §6Minion Slots§7 in §a/is upgrades §7to increase capacity.",
            "§7Refuel via §6craft/trade§7 (resources from farming/mining - ties to §eIsland XP§7).",
            "§7Minions help grind XP sources passively (block break equiv via production).",
            "",
            "§a§lPlay to Win§7: Everything earned via gameplay. Donors get §6cosmetic biome choice only§7."
        ));
        slotsItem.setItemMeta(slotsMeta);
        inv.setItem(13, slotsItem);

        // Section header for types
        ItemStack typesHeader = new ItemStack(Material.PAPER);
        ItemMeta headerMeta = typesHeader.getItemMeta();
        headerMeta.setDisplayName("§a§lAvailable Minion Types");
        headerMeta.setLore(Arrays.asList("§7Click a type below to place it on your island"));
        typesHeader.setItemMeta(headerMeta);
        inv.setItem(9, typesHeader);

        // Minion type buttons (click to place) - row 2 (slots 18-26)
        String[] minionTypes = {"WHEAT", "COBBLESTONE", "IRON", "GOLD", "DIAMOND"};
        Material[] typeIcons = {Material.WHEAT, Material.COBBLESTONE, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND};
        String[] typeNames = {"Wheat Minion", "Cobble Minion", "Iron Minion", "Gold Minion", "Diamond Minion"};
        String[] typeDescs = {
            "§7Gathers §fwheat §7and crops",
            "§7Gathers §fcobblestone §7and stone",
            "§7Gathers §firon ingots",
            "§7Gathers §fgold ingots",
            "§7Gathers §fdiamonds"
        };

        for (int i = 0; i < 5; i++) {
            ItemStack typeItem = new ItemStack(typeIcons[i]);
            ItemMeta typeMeta = typeItem.getItemMeta();
            typeMeta.setDisplayName("§a§l" + typeNames[i]);
            typeMeta.setLore(Arrays.asList(
                typeDescs[i],
                "",
                "§eClick to place this minion",
                "§7(Requires free slot)"
            ));
            typeItem.setItemMeta(typeMeta);
            inv.setItem(18 + i, typeItem);
        }

        // Remove minion button
        ItemStack removeBtn = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta removeMeta = removeBtn.getItemMeta();
        removeMeta.setDisplayName("§c§lRemove Last Minion");
        removeMeta.setLore(Arrays.asList(
            "§7Removes one active minion",
            "§7Frees up a slot immediately",
            "§7(Visual entity will despawn)"
        ));
        removeBtn.setItemMeta(removeMeta);
        inv.setItem(40, removeBtn);

        // Bottom info bar
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§b§lMinion Info");
        infoMeta.setLore(Arrays.asList(
            "§7• Minions work automatically while you are online",
            "§7• Higher tier minions gather rarer resources",
            "§7• Slots are shared across all minion types",
            "§7• Upgrade slots via island upgrades menu"
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        // Fill remaining empty slots with subtle glass (avoiding key positions)
        ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        int[] skipSlots = {0,1,2,3,4,5,6,7,8,9,13,18,19,20,21,22,23,24,25,26,40,49};
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                boolean skip = false;
                for (int s : skipSlots) if (s == i) { skip = true; break; }
                if (!skip) inv.setItem(i, glass);
            }
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle() == null || !event.getView().getTitle().equals("§6§lMinion Management")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        // Handle minion type placement (slots 18-22)
        if (slot >= 18 && slot <= 22) {
            int typeIndex = slot - 18;
            String[] minionTypes = {"WHEAT", "COBBLESTONE", "IRON", "GOLD", "DIAMOND"};
            String type = minionTypes[typeIndex];

            Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island == null) {
                player.sendMessage("§cYou need an island to place minions!");
                return;
            }
            if (minionManager.canPlaceMinion(player, island)) {
                boolean success = minionManager.placeMinion(player, island, type);
                if (success) {
                    player.sendMessage("§a" + type + " Minion placed successfully!");
                    openMinionsGUI(player); // refresh
                } else {
                    player.sendMessage("§cCould not place minion. Check slots or island space.");
                }
            } else {
                player.sendMessage("§cNo available minion slots! Upgrade §6Minion Slots§c in /is upgrades.");
            }
            return;
        }

        // Handle remove minion (slot 40)
        if (slot == 40) {
            Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island == null) {
                player.sendMessage("§cNo island in this dimension!");
                return;
            }
            String islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
            int current = minionManager.getPlacedMinionCount(islandId);
            if (current > 0) {
                minionManager.removeMinion(islandId);
                player.sendMessage("§aRemoved one minion. Slots freed: §f" + (current - 1) + " / " + minionManager.getMaxMinionSlots(island));
                openMinionsGUI(player);
            } else {
                player.sendMessage("§cNo minions to remove!");
            }
            return;
        }

        // Slots info or other info clicks - show hint
        if (slot == 13 || slot == 9 || slot == 49) {
            player.sendMessage("§7Upgrade your §6Minion Slots§7 using the island upgrades menu for more capacity!");
        }
    }
}
