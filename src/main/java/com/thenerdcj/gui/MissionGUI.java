package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.mission.Mission;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paginated Mission GUI for the expanded island mission system.
 * Shows daily/weekly/etc. missions with progress, rewards (money, XP, boosters), and claim support.
 *
 * Deep modernization pass:
 * - Manual ItemStack creation in createMissionItem (rich lore + PDC) and createButton converted to GUIUtils.createItem + attach helper.
 * - Dynamic title now uses MessageUtil.legacy.
 * - Preserved async mission loading, pagination, PDC-based claim routing (MISSION_ID_KEY), SoundUtil feedback, and all claim logic.
 */
public class MissionGUI implements Listener {

    private final FoliaSkyblock plugin;
    private static final int ITEMS_PER_PAGE = 45;
    private final NamespacedKey MISSION_ID_KEY;

    // Temporary storage for open GUIs (mission list per player for click handling)
    private final Map<UUID, java.util.List<Mission>> openMissions = new HashMap<>();

    public MissionGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public MissionGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.MISSION_ID_KEY = new NamespacedKey(plugin, "mission_id");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, int page) {
        String islandKey = getIslandKey(player);
        UUID playerId = player.getUniqueId();

        plugin.getMissionManager().getMissionsForIsland(islandKey).thenAccept(missions -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                openMissions.put(playerId, missions);

                int totalPages = Math.max(1, (int) Math.ceil(missions.size() / (double) ITEMS_PER_PAGE));
                int validPage = Math.max(0, Math.min(page, totalPages - 1));

                String title = "§6§lIsland Missions §7(Page " + (validPage + 1) + "/" + totalPages + ")";
                Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy(title));

                int start = validPage * ITEMS_PER_PAGE;
                int end = Math.min(start + ITEMS_PER_PAGE, missions.size());

                int slot = 10;
                for (int i = start; i < end; i++) {
                    Mission m = missions.get(i);
                    ItemStack item = createMissionItem(m);
                    gui.setItem(slot, item);
                    slot++;
                    if ((slot - 9) % 9 == 0) slot += 2;
                }

                // Navigation and categories
                gui.setItem(45, createButton("§aPrevious", Material.ARROW));
                gui.setItem(49, createButton("§eDaily", Material.SUNFLOWER));
                gui.setItem(50, createButton("§eWeekly", Material.CLOCK));
                gui.setItem(53, createButton("§aNext", Material.ARROW));

                player.openInventory(gui);
            });
        });
    }

    private ItemStack createMissionItem(Mission m) {
        Material icon = switch (m.getObjective()) {
            case BREAK_BLOCKS -> Material.DIAMOND_PICKAXE;
            case KILL_MOBS -> Material.IRON_SWORD;
            case SELL_ITEMS -> Material.GOLD_INGOT;
            default -> Material.PAPER;
        };

        String status = m.isClaimed() ? "§aClaimed" : (m.isCompleted() ? "§6Click to Claim" : "§fIn Progress");

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7" + m.getDescription());
        lore.add("§7Progress: §b" + m.getProgress() + "§7/§b" + m.getTarget());
        lore.add("§7Reward: §6$" + m.getRewardMoney() + " §7+ §a" + m.getRewardIslandXp() + " XP");
        if (m.getRewardBoosterType() != null && m.getRewardBoosterDurationMinutes() > 0) {
            lore.add("§dBooster: §e" + m.getRewardBoosterType().getDisplayName() + " §7for §b" + m.getRewardBoosterDurationMinutes() + "m");
        }
        lore.add("");
        lore.add(status);

        // Base item via GUIUtils (modernized)
        ItemStack item = GUIUtils.createItem(icon, "§e" + m.getTitle(), lore.toArray(new String[0]));

        // Attach PDC for claim routing (preserved)
        attachMissionPDC(item, m.getId());
        return item;
    }

    private void attachMissionPDC(ItemStack item, String missionId) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(MISSION_ID_KEY, PersistentDataType.STRING, missionId);
            item.setItemMeta(meta);
        }
    }

    private ItemStack createButton(String name, Material mat) {
        return GUIUtils.createItem(mat, name);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§6§lIsland Missions")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        UUID playerId = player.getUniqueId();

        java.util.List<Mission> currentMissions = openMissions.get(playerId);
        if (currentMissions == null) return;

        // Navigation
        if (slot == 45) {
            open(player, 0); // simplified
            return;
        } else if (slot == 53) {
            open(player, 1);
            return;
        }

        // Claim logic: find the mission from the item in that slot
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String missionId = pdc.get(MISSION_ID_KEY, PersistentDataType.STRING);
        if (missionId == null) return;

        // Find the mission and attempt claim (async: money is deposited before the claim finalizes)
        for (Mission m : currentMissions) {
            if (m.getId().equals(missionId)) {
                if (m.isCompleted() && !m.isClaimed()) {
                    plugin.getMissionManager().claimMission(getIslandKey(player), missionId, player)
                            .thenAccept(success -> plugin.getThreadSafety().runOnMainThread(() -> {
                                if (Boolean.TRUE.equals(success)) {
                                    SoundUtil.reward(player);
                                    open(player, 0); // refresh; reward messages come from claimMission
                                } else {
                                    player.sendMessage("§cThis mission could not be claimed.");
                                }
                            }));
                } else {
                    player.sendMessage("§cThis mission is not ready to claim.");
                }
                return;
            }
        }
    }

    private String getIslandKey(Player player) {
        // Anchor to the overworld (NORMAL) island so the GUI shows — and claims from — the same
        // canonical mission set that generation and progress tracking use, regardless of which
        // dimension the player opened the GUI in.
        com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), org.bukkit.World.Environment.NORMAL);
        if (island != null) {
            return island.getId();
        }
        return player.getUniqueId().toString(); // fallback
    }
}