package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.quest.Quest;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Quest Log GUI for daily/weekly quests.
 * Supports async loading, progress, rewards, claiming, and regeneration.
 *
 * Deep modernization pass:
 * - All manual ItemStack helpers (createQuestItem with PDC, createItem, createGlassPane) converted to GUIUtils.createItem + attach helper.
 * - Title now uses MessageUtil.legacy.
 * - Click handler title check made resilient (startsWith).
 * - Modern filler glass.
 * - Preserved async quests, metadata for islandId, claim/generate flows, PDC questIdKey, sounds.
 */
public class QuestLogGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey questIdKey;

    public QuestLogGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public QuestLogGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.questIdKey = new NamespacedKey(plugin, "quest_id");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, String islandId) {
        player.removeMetadata("quest_island_id", plugin);
        player.setMetadata("quest_island_id", new org.bukkit.metadata.FixedMetadataValue(plugin, islandId));

        if (plugin.getQuestManager() == null) {
            player.sendMessage("§cQuest system is not ready yet.");
            return;
        }

        plugin.getQuestManager().getQuestsForIsland(islandId).thenAccept(quests -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§6§lQuest Log"));

                gui.setItem(4, createItem(Material.BOOK, "§6§lQuest Log",
                        "§7Daily & Weekly Missions", "§7Complete for rewards!"));

                int slot = 10;
                for (Quest quest : quests) {
                    if (slot >= 44) break;

                    Material material = getQuestMaterial(quest.getCategory());
                    String status = quest.isCompleted() ? "§a§lCOMPLETED" :
                            (quest.isExpired() ? "§c§lEXPIRED" : "§e§lIN PROGRESS");

                    gui.setItem(slot, createQuestItem(material, quest, status));
                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }

                gui.setItem(49, createItem(Material.EMERALD, "§a§lGenerate New Quests",
                        "§7Click to get new daily/weekly quests"));

                // Modernized filler
                ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
                for (int i = 0; i < 54; i++) {
                    if (gui.getItem(i) == null) gui.setItem(i, glass);
                }

                player.openInventory(gui);
            });
        });
    }

    private Material getQuestMaterial(Quest.QuestCategory category) {
        return switch (category) {
            case MINING -> Material.DIAMOND_PICKAXE;
            case FARMING -> Material.WHEAT;
            case COMBAT -> Material.DIAMOND_SWORD;
            case BUILDING -> Material.BRICKS;
            case EXPLORATION -> Material.COMPASS;
            case TRADING -> Material.EMERALD;
            case CHALLENGE -> Material.NETHER_STAR;
        };
    }

    private ItemStack createQuestItem(Material material, Quest quest, String status) {
        String progressBar = createProgressBar(quest.getProgress(), quest.getTarget());

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7" + quest.getDescription());
        lore.add("");
        lore.add("§7Progress: " + progressBar + " §f" + quest.getProgress() + "/" + quest.getTarget());
        lore.add("§7Reward: §a" + quest.getRewardXp() + " XP §7+ §e$" + quest.getRewardMoney());
        lore.add("");
        lore.add(status);
        lore.add(quest.isCompleted() && !quest.isClaimed() ? "§aClick to claim reward!" : "§7Complete to claim reward");

        // Base via GUIUtils (modernized)
        ItemStack item = GUIUtils.createItem(material, "§e" + quest.getTitle(), lore.toArray(new String[0]));

        // Attach PDC for reliable quest identification (preserved)
        attachQuestPDC(item, quest.getId());
        return item;
    }

    private void attachQuestPDC(ItemStack item, String questId) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(questIdKey, PersistentDataType.STRING, questId);
            item.setItemMeta(meta);
        }
    }

    private String createProgressBar(int current, int max) {
        int bars = 10;
        int filled = (int) ((double) current / max * bars);
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? "█" : "░");
        }
        return sb.toString();
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        return GUIUtils.createItem(material, name, lore);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Resilient title check (modernized)
        if (!event.getView().getTitle().startsWith("§6§lQuest Log")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getItemMeta() == null) return;

        if (!player.hasMetadata("quest_island_id")) return;
        String islandId = player.getMetadata("quest_island_id").get(0).asString();

        String itemName = clicked.getItemMeta().getDisplayName();

        // Generate New Quests button
        if (itemName.contains("Generate New Quests")) {
            if (plugin.getQuestManager() != null) {
                plugin.getQuestManager().generateDailyQuests(islandId);
                plugin.getQuestManager().generateWeeklyQuests(islandId);
            }
            player.sendMessage("§aNew quests generated!");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                this.open(player, islandId);
            });
            return;
        }

        // Claim reward logic
        String questId = clicked.getItemMeta().getPersistentDataContainer()
                .get(questIdKey, PersistentDataType.STRING);

        if (questId != null && plugin.getQuestManager() != null) {
            boolean success = plugin.getQuestManager().claimQuest(islandId, questId, player);

            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                plugin.getThreadSafety().runOnMainThread(() -> {
                    player.closeInventory();
                    this.open(player, islandId); // Refresh GUI
                });
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }
}