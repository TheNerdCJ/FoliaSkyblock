package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
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
    private final NamespacedKey filterKey;
    private final NamespacedKey pageActionKey;

    public QuestLogGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public QuestLogGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.questIdKey = new NamespacedKey(plugin, "quest_id");
        this.filterKey = new NamespacedKey(plugin, "quest_filter");
        this.pageActionKey = new NamespacedKey(plugin, "quest_page_action");
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

        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().generateOnboardingQuests(islandId);
            plugin.getQuestManager().generateDailyQuests(islandId);
            plugin.getQuestManager().generateWeeklyQuests(islandId);
        }

        int level = 1;
        if (plugin.getIslandManager() != null) {
            Island isle = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (isle != null) level = isle.getLevel();
        }
        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().generateStoryQuests(islandId, level);
        }

        plugin.getQuestManager().getQuestsForIsland(islandId).thenAccept(quests -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                // Determine active filter (persisted via player metadata for this session)
                String filter = "ALL";
                if (player.hasMetadata("quest_filter")) {
                    try {
                        filter = player.getMetadata("quest_filter").get(0).asString();
                    } catch (Exception ignored) {}
                }

                Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§6§lQuest Log"));

                // Top-row filter tabs (All / Storyline / Daily / Weekly / Recommended)
                // "Storyline" covers Onboarding (early story) + Main Story chapters for a continuous narrative feel.
                gui.setItem(0, createFilterTab(Material.BOOK, "§f§lAll", "ALL", filter));
                gui.setItem(1, createFilterTab(Material.NETHER_STAR, "§b§lStoryline", "STORY", filter));
                gui.setItem(2, createFilterTab(Material.CLOCK, "§e§lDaily", "DAILY", filter));
                gui.setItem(3, createFilterTab(Material.SUNFLOWER, "§6§lWeekly", "WEEKLY", filter));
                gui.setItem(4, createFilterTab(Material.COMPASS, "§a§lRecommended", "RECOMMENDED", filter));

                // Compute overall active for header (independent of filter)
                int active = (int) quests.stream()
                        .filter(q -> !q.isCompleted() && !q.isClaimed() && !q.isExpired())
                        .count();

                // Prominent "Main Story Progress" teaser (encourages linear chapter gameplay toward the dragon/dimensions)
                // Placed after the tabs so it doesn't look like another filter tab.
                // Dailies/Weeklies run simultaneously (different tabs).
                Quest activeMainStory = quests.stream()
                    .filter(q -> q.getQuestLine() == Quest.QuestLine.MAIN_STORY && !q.isClaimed() && !q.isCompleted() && !q.isExpired())
                    .findFirst().orElse(null);
                String storyLine = (activeMainStory != null)
                    ? "§bChapter " + activeMainStory.getChapter() + "/20  §5→ §dEnder Dragon"
                    : "§7Finish onboarding to start the Main Story arc";
                gui.setItem(5, createItem(Material.NETHER_STAR, "§d§lMain Story Progress",
                    "§7Sequential chapters (see Storyline tab for details).",
                    storyLine,
                    "§aDailies & Weeklies run at the same time in their tabs!"));

                gui.setItem(8, createItem(Material.BOOK, "§6§lQuest Log",
                        "§7Daily & Weekly Missions", "§7Complete for rewards!",
                        "§7Quests In Progress: §b" + active + "  §7Filter: §f" + filter));

                // Apply filter + simple recommended ordering (new GUI)
                java.util.List<Quest> toShow = new java.util.ArrayList<>(quests);
                if ("STORY".equals(filter)) {
                    // Storyline tab shows Onboarding (early story chapters) + Main Story (linear narrative to dragon/dimensions).
                    // This ensures the tab is populated early and provides the "story line of quests" to encourage dimension progression.
                    toShow.removeIf(q -> q.getQuestLine() != Quest.QuestLine.MAIN_STORY 
                                     && q.getQuestLine() != Quest.QuestLine.ONBOARDING);
                } else if ("DAILY".equals(filter)) {
                    toShow.removeIf(q -> q.getType() != Quest.QuestType.DAILY);
                } else if ("WEEKLY".equals(filter)) {
                    toShow.removeIf(q -> q.getType() != Quest.QuestType.WEEKLY);
                } else if ("RECOMMENDED".equals(filter)) {
                    toShow.removeIf(q -> q.isCompleted() || q.isClaimed() || q.isExpired());
                    toShow.sort((a, b) -> {
                        boolean sa = a.getQuestLine() == Quest.QuestLine.MAIN_STORY;
                        boolean sb = b.getQuestLine() == Quest.QuestLine.MAIN_STORY;
                        if (sa != sb) return sa ? -1 : 1;
                        return Integer.compare(a.getChapter(), b.getChapter());
                    });
                }
                // For ALL: keep natural order (onboarding/story first from generation, then d/w)

                // === PAGINATION (scrollable when many quests) ===
                // Uses "quest_page" player metadata + quest_page_action PDC on arrow items.
                // itemsPerPage chosen so content fits nicely below filters / above actions.
                int itemsPerPage = 24;
                int total = toShow.size();
                int totalPages = Math.max(1, (total + itemsPerPage - 1) / itemsPerPage);

                int page = 0;
                if (player.hasMetadata("quest_page")) {
                    try {
                        page = player.getMetadata("quest_page").get(0).asInt();
                    } catch (Exception ignored) {}
                }
                if (page < 0) page = 0;
                if (page >= totalPages) page = totalPages - 1;
                // Persist (clamped) page for this session
                player.setMetadata("quest_page", new org.bukkit.metadata.FixedMetadataValue(plugin, page));

                int start = page * itemsPerPage;
                int end = Math.min(start + itemsPerPage, total);
                java.util.List<Quest> pageQuests = toShow.subList(start, end);

                // Content area starts at row 1 (slot 9). Dense packing for max items.
                int slot = 9;
                for (Quest quest : pageQuests) {
                    if (slot >= 45) break; // protect bottom action row

                    Material material = getQuestMaterial(quest.getCategory());
                    String status = quest.isCompleted() ? "§a§lCOMPLETED" :
                            (quest.isExpired() ? "§c§lEXPIRED" : "§e§lIN PROGRESS");

                    gui.setItem(slot, createQuestItem(material, quest, status));
                    slot++;
                }

                if (pageQuests.isEmpty()) {
                    gui.setItem(22, createItem(Material.BARRIER, "§7No quests in this filter",
                            "§7Try another tab or Generate New Quests"));
                }

                // Bottom action bar: Generate + History + scroll arrows at the edges when needed.
                // History moved slightly to 51 so 53 can be Next when paginating.
                gui.setItem(49, createItem(Material.EMERALD, "§a§lGenerate New Quests",
                        "§7Click to get new daily/weekly quests"));

                // History button - claimed quests only appear here now (per design)
                gui.setItem(51, createItem(Material.WRITABLE_BOOK, "§d§lQuest History",
                        "§7View previously completed & claimed quests",
                        "§7(removed from main list after claim)"));

                // Scroll / pagination arrows (only when relevant). Feels scrollable.
                if (page > 0) {
                    gui.setItem(45, createPageButton(false, page, totalPages));
                }
                if (page < totalPages - 1) {
                    gui.setItem(53, createPageButton(true, page, totalPages));
                }

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

    private ItemStack createFilterTab(Material material, String name, String value, String currentFilter) {
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Show only " + name.replace("§f§l", "").replace("§b§l", "").replace("§e§l", "").replace("§6§l", "").replace("§a§l", "") + " quests");
        if (value.equals(currentFilter)) {
            lore.add("§a§lSELECTED");
        } else {
            lore.add("§7Click to filter");
        }
        ItemStack item = GUIUtils.createItem(material, name, lore.toArray(new String[0]));
        if (item.getItemMeta() != null) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(filterKey, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPageButton(boolean next, int currentPage, int totalPages) {
        String name = next ? "§e§lNext Page →" : "§e§l← Previous Page";
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Scroll through additional quests");
        lore.add("§7Page §f" + (currentPage + 1) + " §7/ §f" + totalPages);
        lore.add(next ? "§aClick to see more" : "§aClick to go back");
        Material mat = next ? Material.SPECTRAL_ARROW : Material.ARROW;
        ItemStack item = GUIUtils.createItem(mat, name, lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(pageActionKey, PersistentDataType.STRING, next ? "next" : "prev");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createQuestItem(Material material, Quest quest, String status) {
        String progressBar = createProgressBar(quest.getProgress(), quest.getTarget());

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7" + quest.getDescription());
        lore.add("");
        lore.add("§7Progress: " + progressBar + " §f" + quest.getProgress() + "/" + quest.getTarget());
        lore.add("§7Reward: §a" + quest.getRewardXp() + " XP §7+ §e$" + quest.getRewardMoney());

        // New GUI: prereq display ("Unlocked by") + extra rewards support from 6-step polish
        java.util.List<String> prereqs = quest.getPrerequisites();
        if (!prereqs.isEmpty()) {
            lore.add("§7Unlocked by: §e" + String.join(", ", prereqs));
        }
        java.util.List<Quest.QuestReward> extras = quest.getExtraRewards();
        if (!extras.isEmpty()) {
            lore.add("§7Bonus:");
            for (Quest.QuestReward ex : extras) {
                lore.add("§7  " + ex.getDescription());
            }
        }

        if ((quest.getQuestLine() == Quest.QuestLine.ONBOARDING || quest.getQuestLine() == Quest.QuestLine.MAIN_STORY) && quest.getChapter() > 0) {
            String line = quest.getQuestLine() == Quest.QuestLine.MAIN_STORY ? " (Main Story)" : "";
            lore.add("§7Chapter " + quest.getChapter() + line);
        }
        lore.add(status);
        lore.add(quest.isCompleted() && !quest.isClaimed() ? "§aClick for details & claim" : "§7Click for details");

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
        // Resilient title check (covers main log and history view)
        String title = event.getView().getTitle();
        if (!title.startsWith("§6§lQuest Log") && !title.startsWith("§6§lQuest History")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getItemMeta() == null) return;

        if (!player.hasMetadata("quest_island_id")) return;
        String islandId = player.getMetadata("quest_island_id").get(0).asString();

        String itemName = clicked.getItemMeta().getDisplayName();

        // Page scroll actions (makes the quest list scrollable / paginated)
        String pageAction = clicked.getItemMeta().getPersistentDataContainer()
                .get(pageActionKey, PersistentDataType.STRING);
        if (pageAction != null) {
            int currentPage = 0;
            if (player.hasMetadata("quest_page")) {
                try { currentPage = player.getMetadata("quest_page").get(0).asInt(); } catch (Exception ignored) {}
            }
            int newPage = currentPage + ("next".equals(pageAction) ? 1 : -1);
            if (newPage < 0) newPage = 0;
            player.setMetadata("quest_page", new org.bukkit.metadata.FixedMetadataValue(plugin, newPage));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                this.open(player, islandId);
            });
            return;
        }

        // Filter tab clicks (new GUI tabs) -- reset to first page when filter changes
        String filterVal = clicked.getItemMeta().getPersistentDataContainer()
                .get(filterKey, PersistentDataType.STRING);
        if (filterVal != null) {
            player.setMetadata("quest_filter", new org.bukkit.metadata.FixedMetadataValue(plugin, filterVal));
            player.setMetadata("quest_page", new org.bukkit.metadata.FixedMetadataValue(plugin, 0));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                this.open(player, islandId);
            });
            return;
        }

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
                player.setMetadata("quest_page", new org.bukkit.metadata.FixedMetadataValue(plugin, 0));
                this.open(player, islandId);
            });
            return;
        }

        // Quest item click -> open the new polished QuestDetailGUI (instead of direct claim)
        // This gives objectives list, full prereqs, extra rewards, claim button, reroll etc.
        String questId = clicked.getItemMeta().getPersistentDataContainer()
                .get(questIdKey, PersistentDataType.STRING);

        if (questId != null && plugin.getQuestManager() != null) {
            if (title.startsWith("§6§lQuest History")) {
                // History items are view-only (already claimed and moved here)
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            // Route to detail view (the "new gui" per-quest experience)
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            final String qid = questId;
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                if (plugin.getQuestDetailGUI() != null) {
                    plugin.getQuestDetailGUI().open(player, islandId, qid);
                } else {
                    // Fallback (should not happen now that we wire the detail instance)
                    boolean success = plugin.getQuestManager().claimQuest(islandId, qid, player);
                    if (success) {
                        this.open(player, islandId);
                    }
                }
            });
            return;
        }

        // Quest History button (claimed quests moved here after claim)
        if (itemName.contains("Quest History")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                openHistory(player, islandId);
            });
            return;
        }

        // Back from history view
        if (itemName.contains("Back to Quest Log")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                this.open(player, islandId);
            });
            return;
        }
    }

    private void openHistory(Player player, String islandId) {
        if (plugin.getQuestManager() == null) return;

        plugin.getQuestManager().getQuestHistory(islandId).thenAccept(history -> {
            plugin.getThreadSafety().runOnMainThread(() -> {
                Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§6§lQuest History"));

                gui.setItem(4, createItem(Material.BOOK, "§6§lClaimed Quest History",
                        "§7Quests you have completed and claimed.",
                        "§7Removed from main list after claim."));

                int slot = 10;
                for (Quest quest : history) {
                    if (slot >= 44) break;
                    Material material = getQuestMaterial(quest.getCategory());
                    String status = "§a§lCLAIMED";
                    gui.setItem(slot, createQuestItem(material, quest, status));
                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }

                if (history.isEmpty()) {
                    gui.setItem(22, createItem(Material.BARRIER, "§7No claimed history yet"));
                }

                // Back button
                gui.setItem(49, createItem(Material.ARROW, "§e§lBack to Quest Log"));

                // Filler
                ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
                for (int i = 0; i < 54; i++) {
                    if (gui.getItem(i) == null) gui.setItem(i, glass);
                }

                player.openInventory(gui);
            });
        });
    }
}