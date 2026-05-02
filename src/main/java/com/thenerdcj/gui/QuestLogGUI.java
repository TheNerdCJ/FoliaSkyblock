package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.quest.Quest;
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
import java.util.List;

/**
 * Quest Log GUI - Structured quest display
 */
public class QuestLogGUI implements Listener {

    private final FoliaSkyblock plugin;

    public QuestLogGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, String islandId) {
        plugin.getQuestManager().getQuestsForIsland(islandId).thenAccept(quests -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory gui = Bukkit.createInventory(null, 54, "§6§lQuest Log");

                // Title
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

                // Generate quests button
                gui.setItem(49, createItem(Material.EMERALD, "§a§lGenerate New Quests",
                        "§7Click to get new daily/weekly quests"));

                // Fill empty slots
                for (int i = 0; i < 54; i++) {
                    if (gui.getItem(i) == null) gui.setItem(i, createGlassPane());
                }

                player.openInventory(gui);
                player.setMetadata("quest_island_id", new org.bukkit.metadata.FixedMetadataValue(plugin, islandId));
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
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + quest.getTitle());

        String progressBar = createProgressBar(quest.getProgress(), quest.getTarget());

        meta.setLore(Arrays.asList(
                "§7" + quest.getDescription(),
                "",
                "§7Progress: " + progressBar + " §f" + quest.getProgress() + "/" + quest.getTarget(),
                "§7Reward: §a" + quest.getRewardXp() + " XP §7+ §e$" + quest.getRewardMoney(),
                "",
                status,
                quest.isCompleted() ? "§aClick to claim reward!" : "§7Complete to claim reward"
        ));

        item.setItemMeta(meta);
        return item;
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
        ItemStack item = new ItemStack(material);
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6§lQuest Log")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata("quest_island_id")) return;

        String islandId = player.getMetadata("quest_island_id").get(0).asString();
        String itemName = event.getCurrentItem().getItemMeta().getDisplayName();

        if (itemName.contains("Generate New Quests")) {
            plugin.getQuestManager().generateDailyQuests(islandId);
            plugin.getQuestManager().generateWeeklyQuests(islandId);
            player.sendMessage("§aNew quests generated!");
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                new QuestLogGUI(plugin).open(player, islandId);
            });
        }
    }
}
