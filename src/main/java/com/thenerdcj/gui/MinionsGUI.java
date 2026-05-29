package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.MinionManager;
import com.thenerdcj.manager.MinionType;
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
import java.util.Map;

/**
 * MinionsGUI - Interactive GUI for managing island minions.
 * Displays current minion slots usage, available minion types, and allows placing/removing minions.
 * Minion types are determined by the upgrade level and island balance.
 */
public class MinionsGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final MinionManager minionManager;
    private final NamespacedKey MINION_TYPE_KEY;

    public MinionsGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.minionManager = plugin.getMinionManager();
        this.MINION_TYPE_KEY = new NamespacedKey(plugin, "minion_type");
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
        java.util.Map<com.thenerdcj.manager.MinionType, Integer> breakdown = java.util.Collections.emptyMap();
        if (hasIsland) {
            islandId = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
            maxSlots = minionManager.getMaxMinionSlots(island);
            placed = minionManager.getPlacedMinionCount(islandId);
            breakdown = minionManager.getMinionBreakdown(islandId);
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
            "§7Accepted fuels: §fCoal, Blaze Rods, Lava Buckets, Wheat, Ender Pearls",
            "§7Use the §6Feed Fuel§7 button to power your minions.",
            "§7Upgrade §6Minion Slots§7 in /is upgrades for more capacity.",
            "",
            "§a§lPlay to Win§7: Everything earned via gameplay."
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

        // Minion type buttons (click to place) - using expanded MinionType enum
        com.thenerdcj.manager.MinionType[] types = com.thenerdcj.manager.MinionType.values();
        int maxTypesToShow = Math.min(7, types.length);

        for (int i = 0; i < maxTypesToShow; i++) {
            com.thenerdcj.manager.MinionType mt = types[i];
            ItemStack typeItem = new ItemStack(mt.getIcon());
            ItemMeta typeMeta = typeItem.getItemMeta();
            typeMeta.setDisplayName("§a§l" + mt.getDisplayName() + " Minion");
            typeMeta.setLore(Arrays.asList(
                "§7Produces: §f" + mt.getResource().name().toLowerCase().replace('_', ' '),
                "§7Efficiency: §e" + String.format("%.0f%%", mt.getProductionMultiplier() * 100),
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

        // Feed Fuel button (real items - Tier B expansion)
        ItemStack feedBtn = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta feedMeta = feedBtn.getItemMeta();
        feedMeta.setDisplayName("§6§lFeed Fuel");
        feedMeta.setLore(Arrays.asList(
            "§7Consumes fuel items from your inventory",
            "§7Accepted: Coal, Blaze Rods, Lava Buckets, etc.",
            "§7Gives minions more runtime",
            "",
            "§eClick to feed all valid fuel items"
        ));
        feedBtn.setItemMeta(feedMeta);
        inv.setItem(42, feedBtn);

        // Bottom info bar
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§b§lMinion Info");
        infoMeta.setLore(Arrays.asList(
            "§7• Minions work automatically while you are online",
            "§7• Higher tier minions gather rarer resources",
            "§7• Slots are shared across all minion types",
            "§7• Upgrade slots via island upgrades menu",
            "§7• Fuel persists across restarts (polished)"
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        // Active minions section with individual removal buttons (polished nice-to-have)
        ItemStack activeHeader = new ItemStack(Material.ARMOR_STAND);
        ItemMeta activeHeaderMeta = activeHeader.getItemMeta();
        activeHeaderMeta.setDisplayName("§e§lYour Minions (click to remove 1)");
        activeHeaderMeta.setLore(Arrays.asList("§7Total: §f" + placed + " §7/ §f" + maxSlots));
        activeHeader.setItemMeta(activeHeaderMeta);
        inv.setItem(27, activeHeader);

        // Place one item per active type for removal (starting slot 28)
        int slot = 28;
        if (!breakdown.isEmpty()) {
            for (Map.Entry<MinionType, Integer> entry : breakdown.entrySet()) {
                if (entry.getValue() > 0 && slot < 35) {
                    MinionType mt = entry.getKey();
                    ItemStack typeItem = new ItemStack(mt.getIcon(), Math.min(64, entry.getValue()));
                    ItemMeta typeMeta = typeItem.getItemMeta();
                    typeMeta.setDisplayName("§c§l" + mt.getDisplayName() + " §7x" + entry.getValue());
                    typeMeta.setLore(Arrays.asList(
                        "§7Click to remove §cone§7 of this type",
                        "§7Produces: §f" + mt.getResource().name().toLowerCase().replace('_', ' ')
                    ));
                    PersistentDataContainer pdc = typeMeta.getPersistentDataContainer();
                    pdc.set(MINION_TYPE_KEY, PersistentDataType.STRING, mt.getKey());
                    typeItem.setItemMeta(typeMeta);
                    inv.setItem(slot++, typeItem);
                }
            }
        } else {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta noneMeta = none.getItemMeta();
            noneMeta.setDisplayName("§7No minions placed");
            none.setItemMeta(noneMeta);
            inv.setItem(28, none);
        }

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

        // Handle minion type placement (dynamic from MinionType enum)
        int maxPlaceSlots = 18 + Math.min(7, com.thenerdcj.manager.MinionType.values().length);
        if (slot >= 18 && slot < maxPlaceSlots) {
            int typeIndex = slot - 18;
            com.thenerdcj.manager.MinionType[] allTypes = com.thenerdcj.manager.MinionType.values();
            if (typeIndex < allTypes.length) {
                com.thenerdcj.manager.MinionType type = allTypes[typeIndex];

                Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId(), player.getWorld().getEnvironment());
                if (island == null) {
                    player.sendMessage("§cYou need an island to place minions!");
                    return;
                }
                if (minionManager.canPlaceMinion(player, island)) {
                    boolean success = minionManager.placeMinion(player, island, type);
                    if (success) {
                        player.sendMessage("§a" + type.getDisplayName() + " Minion placed successfully!");
                        openMinionsGUI(player); // refresh
                    } else {
                        player.sendMessage("§cCould not place minion. Check slots or island space.");
                    }
                } else {
                    player.sendMessage("§cNo available minion slots! Upgrade §6Minion Slots§c in /is upgrades.");
                }
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
                int newCount = minionManager.getPlacedMinionCount(islandId);
                player.sendMessage("§aRemoved one minion. Slots freed: §f" + newCount + " / " + minionManager.getMaxMinionSlots(island));
                openMinionsGUI(player);
            } else {
                player.sendMessage("§cNo minions to remove!");
            }
            return;
        }

        // Feed Fuel button (real items)
        if (slot == 42) {
            Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island == null) {
                player.sendMessage("§cYou need an island to feed minions!");
                return;
            }
            minionManager.feedFuel(player, island);
            openMinionsGUI(player); // refresh to show updated fuel
            return;
        }

        // Individual minion removal via PDC (slots 28-34)
        if (slot >= 28 && slot <= 34) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                String typeKey = pdc.get(MINION_TYPE_KEY, PersistentDataType.STRING);
                if (typeKey != null) {
                    MinionType typeToRemove = MinionType.fromString(typeKey);
                    Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId(), player.getWorld().getEnvironment());
                    if (island != null) {
                        String id = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
                        int before = minionManager.getPlacedMinionCount(id);
                        minionManager.removeMinionType(id, typeToRemove);
                        int after = minionManager.getPlacedMinionCount(id);
                        if (after < before) {
                            player.sendMessage("§aRemoved one §e" + typeToRemove.getDisplayName() + "§a minion.");
                        } else {
                            player.sendMessage("§cNo " + typeToRemove.getDisplayName() + " minions left.");
                        }
                        openMinionsGUI(player);
                    }
                }
            }
            return;
        }

        // Info clicks
        if (slot == 13 || slot == 9 || slot == 49 || slot == 27) {
            player.sendMessage("§7Upgrade your §6Minion Slots§7 using the island upgrades menu for more capacity!");
        }
    }
}
