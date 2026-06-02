package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.MinionManager;
import com.thenerdcj.manager.MinionType;
import com.thenerdcj.cosmetic.MinionSkin;
import com.thenerdcj.cosmetic.MinionSkinManager;
import com.thenerdcj.util.MessageUtil;
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

import java.util.Map;

/**
 * MinionsGUI - Interactive GUI for managing island minions.
 * Displays current minion slots usage, available minion types, and allows placing/removing minions.
 * Minion types are determined by the upgrade level and island balance.
 *
 * Deep modernization (GUI migration sweep):
 * - All 15+ manual ItemStack + ItemMeta blocks converted to GUIUtils.createItem + targeted PDC helpers.
 * - Title now uses MessageUtil.legacy.
 * - Decorative fillers (black/purple glass, accents) centralized.
 * - Preserved complex logic: fuel tracking, slot limits, placement via PDC (MINION_TYPE_KEY), active minion list, feed/remove actions.
 * - Folia-safe (no blocking calls in hot paths).
 */
public class MinionsGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final MinionManager minionManager;
    private final MinionSkinManager minionSkinManager;
    private final NamespacedKey MINION_TYPE_KEY;
    private final NamespacedKey MINION_SKIN_ASSIGN_KEY;

    public MinionsGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    /**
     * Constructor for production (auto-registers) or tests (skip registration for easier mocking).
     */
    public MinionsGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.minionManager = plugin.getMinionManager();
        this.minionSkinManager = plugin.getMinionSkinManager();
        this.MINION_TYPE_KEY = new NamespacedKey(plugin, "minion_type");
        this.MINION_SKIN_ASSIGN_KEY = new NamespacedKey(plugin, "minion_skin_assign");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void openMinionsGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MessageUtil.legacy("§6§lMinion Management"));

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

        // Decorative top bar - modernized via GUIUtils (dark theme consistent with skyblock aesthetic)
        ItemStack filler = GUIUtils.createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, filler);
        }
        // Side accents for modern polish
        ItemStack accent = GUIUtils.createItem(Material.PURPLE_STAINED_GLASS_PANE, "§5 ");
        inv.setItem(9, accent);
        inv.setItem(17, accent);

        // Title item (center top) - Modern with XP synergy note
        ItemStack title = GUIUtils.createItem(Material.BEACON, "§6§lMinion Management",
                "§7Automate resource production & boost §eIsland XP",
                "§7Minions contribute to island progression (dimensions, bosses)",
                "§7Play to Win: Fuel & slots earned via grind/trade - no P2W");
        inv.setItem(4, title);

        if (!hasIsland) {
            // No island message - modernized
            ItemStack noIsland = GUIUtils.createItem(Material.BARRIER, "§c§lNo Island Found",
                    "§7You need an island in this dimension",
                    "§7to manage minions.",
                    "",
                    "§eUse §6/is create §eor teleport to your island.");
            inv.setItem(22, noIsland);
            player.openInventory(inv);
            return;
        }

        // Prominent slots usage display (row 1 center) - modernized
        int fuel = minionManager.getIslandFuel(islandId != null ? islandId : "");
        ItemStack slotsItem = GUIUtils.createItem(Material.CHEST, "§e§lMinion Slots",
                "§7Active: §f" + placed + " §7/ §f" + maxSlots,
                "§7Fuel: §e" + fuel + " §7units (consumed per production cycle)",
                "",
                "§7Accepted fuels: §fCoal, Blaze Rods, Lava Buckets, Wheat, Ender Pearls",
                "§7Use the §6Feed Fuel§7 button to power your minions.",
                "§7Upgrade §6Minion Slots§7 in /is upgrades for more capacity.",
                "",
                "§a§lPlay to Win§7: Everything earned via gameplay.");
        inv.setItem(13, slotsItem);

        // Section header for types - modernized
        ItemStack typesHeader = GUIUtils.createItem(Material.PAPER, "§a§lAvailable Minion Types",
                "§7Click a type below to place it on your island");
        inv.setItem(9, typesHeader);

        // Minion type buttons (click to place) - using expanded MinionType enum
        com.thenerdcj.manager.MinionType[] types = com.thenerdcj.manager.MinionType.values();
        int maxTypesToShow = Math.min(7, types.length);

        for (int i = 0; i < maxTypesToShow; i++) {
            com.thenerdcj.manager.MinionType mt = types[i];
            ItemStack typeItem = GUIUtils.createItem(mt.getIcon(), "§a§l" + mt.getDisplayName() + " Minion",
                    "§7Produces: §f" + mt.getResource().name().toLowerCase().replace('_', ' '),
                    "§7Efficiency: §e" + String.format("%.0f%%", mt.getProductionMultiplier() * 100),
                    "",
                    "§eClick to place this minion",
                    "§7(Requires free slot)");
            inv.setItem(18 + i, typeItem);
        }

        // Remove minion button - modernized
        ItemStack removeBtn = GUIUtils.createItem(Material.REDSTONE_BLOCK, "§c§lRemove Last Minion",
                "§7Removes one active minion",
                "§7Frees up a slot immediately",
                "§7(Visual entity will despawn)");
        inv.setItem(40, removeBtn);

        // Feed Fuel button - modernized
        ItemStack feedBtn = GUIUtils.createItem(Material.BLAZE_POWDER, "§6§lFeed Fuel",
                "§7Consumes fuel items from your inventory",
                "§7Accepted: Coal, Blaze Rods, Lava Buckets, etc.",
                "§7Gives minions more runtime",
                "",
                "§eClick to feed all valid fuel items");
        inv.setItem(42, feedBtn);

        // Bottom info bar - modernized
        ItemStack info = GUIUtils.createItem(Material.BOOK, "§b§lMinion Info",
                "§7• Minions work automatically while you are online",
                "§7• Higher tier minions gather rarer resources",
                "§7• Slots are shared across all minion types",
                "§7• Upgrade slots via island upgrades menu",
                "§7• Fuel persists across restarts (polished)");
        inv.setItem(49, info);

        // Active minions section header - modernized
        ItemStack activeHeader = GUIUtils.createItem(Material.ARMOR_STAND, "§e§lYour Minions (click to remove 1)",
                "§7Total: §f" + placed + " §7/ §f" + maxSlots);
        inv.setItem(27, activeHeader);

        // Place one item per active type for removal (starting slot 28) - modernized with PDC preservation
        int slot = 28;
        if (!breakdown.isEmpty()) {
            for (Map.Entry<MinionType, Integer> entry : breakdown.entrySet()) {
                if (entry.getValue() > 0 && slot < 35) {
                    MinionType mt = entry.getKey();
                    ItemStack typeItem = GUIUtils.createItem(mt.getIcon(), "§c§l" + mt.getDisplayName() + " §7x" + entry.getValue(),
                            "§7Click to remove §cone§7 of this type",
                            "§7Produces: §f" + mt.getResource().name().toLowerCase().replace('_', ' '));
                    // Preserve the existing PDC for click handling
                    ItemMeta tm = typeItem.getItemMeta();
                    if (tm != null) {
                        tm.getPersistentDataContainer().set(MINION_TYPE_KEY, PersistentDataType.STRING, mt.getKey());
                        typeItem.setItemMeta(tm);
                    }
                    inv.setItem(slot++, typeItem);
                }
            }
        } else {
            ItemStack none = GUIUtils.createItem(Material.BARRIER, "§7No minions placed");
            inv.setItem(28, none);
        }

        // Skin assignment section for per-minion polish (uses foundation in manager)
        if (hasIsland && minionSkinManager != null) {
            ItemStack skinHeader = GUIUtils.createItem(Material.LEATHER_HELMET, "§6§lMinion Skin Assignments (per-minion)",
                    "§7Your active skin: §f" + (minionSkinManager.getActiveSkin(player.getUniqueId()) != null ? minionSkinManager.getActiveSkin(player.getUniqueId()).getDisplayName() : "None"),
                    "§7Click a slot below to assign current active skin to that specific minion #",
                    "§7(Assigned overrides the global active skin for that minion only)",
                    "§7Shift+Click slot to clear assignment | Set global via /minionskins or Wardrobe tab");
            inv.setItem(36, skinHeader);

            String idForSkin = islandId;
            int skinSlot = 37;
            for (int m = 1; m <= maxSlots && skinSlot < 43; m++) {
                MinionSkin assigned = minionManager.getAssignedSkinForMinion(idForSkin, m, player.getUniqueId());
                String label = "§eMinion #" + m;
                String desc = (assigned != null && !assigned.isNone()) ? "§aAssigned: " + assigned.getDisplayName() : "§7Uses global active skin";
                ItemStack sItem = GUIUtils.createItem(Material.LEATHER_HELMET, label, desc,
                        "§7Click: assign your current active global skin here",
                        "§7Shift+Click: clear this minion's assignment (falls back to global)");
                ItemMeta sm = sItem.getItemMeta();
                if (sm != null) {
                    sm.getPersistentDataContainer().set(MINION_SKIN_ASSIGN_KEY, PersistentDataType.INTEGER, m);
                    sItem.setItemMeta(sm);
                }
                inv.setItem(skinSlot++, sItem);
            }
        }

        // Fill remaining empty slots with subtle glass - modernized (GUIUtils)
        ItemStack glass = GUIUtils.createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        int[] skipSlots = {0,1,2,3,4,5,6,7,8,9,13,18,19,20,21,22,23,24,25,26,36,37,38,39,40,41,42,49};
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
        // Resilient title check (modernized pattern)
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith("§6§lMinion Management")) return;

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

        // Per-minion skin assignment clicks (slots 37-42) - uses per-minion foundation + UX polish
        if (slot >= 37 && slot <= 42) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                Integer minionNum = pdc.get(MINION_SKIN_ASSIGN_KEY, PersistentDataType.INTEGER);
                if (minionNum != null) {
                    Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId(), player.getWorld().getEnvironment());
                    if (island != null) {
                        String id = island.getGridPosition().getX() + "," + island.getGridPosition().getZ();
                        boolean isClear = event.isShiftClick();
                        if (isClear) {
                            // Clear this minion's assignment
                            minionManager.clearSkinAssignmentForMinionSlot(id, minionNum, player.getUniqueId());
                            player.sendMessage("§eCleared skin assignment for minion #" + minionNum + " §7(now uses global active)");
                            player.sendActionBar("§7Minion #" + minionNum + " assignment cleared");
                            // Folia-safe feedback particles
                            com.thenerdcj.util.ThreadSafety ts = plugin.getThreadSafety();
                            org.bukkit.Location pLoc = player.getLocation();
                            ts.runAtLocation(pLoc, () -> {
                                if (pLoc.getWorld() != null) {
                                    pLoc.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, pLoc.clone().add(0, 1.2, 0), 8, 0.4, 0.4, 0.4, 0.01);
                                }
                            });
                        } else {
                            MinionSkin current = (minionSkinManager != null) ? minionSkinManager.getActiveSkin(player.getUniqueId()) : null;
                            if (current != null && !current.isNone()) {
                                minionManager.assignSkinToMinionSlot(id, minionNum, current, player.getUniqueId());
                                player.sendMessage("§aAssigned " + current.getDisplayName() + " skin to minion #" + minionNum + " §7(overrides global)");
                                player.sendActionBar("§aMinion #" + minionNum + " now uses " + current.getDisplayName());
                                // Folia-safe feedback particles
                                com.thenerdcj.util.ThreadSafety ts = plugin.getThreadSafety();
                                org.bukkit.Location pLoc = player.getLocation();
                                ts.runAtLocation(pLoc, () -> {
                                    if (pLoc.getWorld() != null) {
                                        pLoc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, pLoc.clone().add(0, 1.2, 0), 10, 0.5, 0.5, 0.5, 0.02);
                                    }
                                });
                            } else {
                                player.sendMessage("§cNo active minion skin to assign. Set one via /minionskins or Wardrobe.");
                            }
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
