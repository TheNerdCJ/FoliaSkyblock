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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
        open(player, islandId, "all", 0);
    }

    public void open(Player player, String islandId, String filter) {
        open(player, islandId, filter, 0);
    }

    public void open(Player player, String islandId, String filter, int page) {
        // Quests now use player UUID as key (per-player, persistent via DB for onboarding FIRST quests).
        // Allows simultaneous achievement of all quests in parallel - no requirement to finish a "prior" quest first.
        String playerKey = player.getUniqueId().toString();
        String islandKey = islandId; // passed as island or fallback
        player.removeMetadata("quest_island_id", plugin);
        player.setMetadata("quest_island_id", new org.bukkit.metadata.FixedMetadataValue(plugin, islandKey));
        player.removeMetadata("quest_filter", plugin);
        player.setMetadata("quest_filter", new org.bukkit.metadata.FixedMetadataValue(plugin, filter != null ? filter : "all"));
        player.removeMetadata("quest_page", plugin);
        player.setMetadata("quest_page", new org.bukkit.metadata.FixedMetadataValue(plugin, Math.max(0, page)));

        if (plugin.getQuestManager() == null) {
            player.sendMessage("§cQuest system is not ready yet.");
            return;
        }

        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().generateOnboardingQuests(playerKey); // per player
            plugin.getQuestManager().generateDailyQuests(islandKey); // per island
            plugin.getQuestManager().generateWeeklyQuests(islandKey);
        }
        // Load streak for header (user friendly)
        int streak = (plugin.getQuestManager() != null) ? plugin.getQuestManager().getDailyStreak(islandKey) : 0;
        // Combine per-player (onboarding + main story) + per-island (daily/weekly) for display
        CompletableFuture<List<Quest>> playerQs = plugin.getQuestManager().getQuestsForIsland(playerKey);
        CompletableFuture<List<Quest>> islandQs = plugin.getQuestManager().getQuestsForIsland(islandKey);
        final String finalFilter = filter != null ? filter : "all";
        CompletableFuture.allOf(playerQs, islandQs).thenAccept(v -> {
            List<Quest> combined = new ArrayList<>(playerQs.join());
            for (Quest q : islandQs.join()) {
                if (combined.stream().noneMatch(c -> c.getId().equals(q.getId()))) {
                    combined.add(q);
                }
            }

            // Step 1: Filter to only available quests (respect prerequisites / chains)
            java.util.Set<String> claimedIds = new java.util.HashSet<>();
            for (Quest q : combined) {
                if (q.isClaimed() || (q.isCompleted() && q.getType() == Quest.QuestType.FIRST)) {
                    claimedIds.add(q.getId());
                }
            }
            combined.removeIf(q -> !q.isAvailable(claimedIds));

            // Step 4: Apply UI filter (tabs: all, story, daily, weekly, completed, recommended, history)
            List<Quest> filtered = new ArrayList<>();
            java.util.List<String[]> historyEntries = null;
            if ("history".equalsIgnoreCase(finalFilter)) {
                historyEntries = plugin.getQuestManager() != null ? 
                    null : null; // will load below in main thread
            } else {
                for (Quest q : combined) {
                    boolean include = true;
                    if ("story".equalsIgnoreCase(finalFilter)) {
                        include = q.getQuestLine() == Quest.QuestLine.MAIN_STORY || q.getType() == Quest.QuestType.FIRST;
                    } else if ("daily".equalsIgnoreCase(finalFilter)) {
                        include = q.getType() == Quest.QuestType.DAILY;
                    } else if ("weekly".equalsIgnoreCase(finalFilter)) {
                        include = q.getType() == Quest.QuestType.WEEKLY;
                    } else if ("completed".equalsIgnoreCase(finalFilter)) {
                        include = q.isClaimed();
                    } else if ("recommended".equalsIgnoreCase(finalFilter)) {
                        // Leverage adaptive + rep from Step 1
                        int rep = plugin.getQuestManager() != null ? plugin.getQuestManager().getReputation(islandKey, q.getCategory()) : 0;
                        include = rep > 0 || q.getQuestLine() == Quest.QuestLine.MAIN_STORY;
                    }

                    // Category filter: if finalFilter matches a QuestCategory, only include quests of that category
                    if (include && finalFilter != null) {
                        try {
                            Quest.QuestCategory catFilter = Quest.QuestCategory.valueOf(finalFilter.toUpperCase());
                            include = (q.getCategory() == catFilter);
                        } catch (Exception ignored) {
                            // not a category filter, keep previous include
                        }
                    }
                    if (include) filtered.add(q);
                }
            }
            final List<Quest> finalFiltered = filtered;
            final java.util.List<String[]> finalHistory = historyEntries;
            final int usePage = Math.max(0, page);
            plugin.getThreadSafety().runOnMainThread(() -> {
                // For history filter, load now in main thread
                java.util.List<String[]> historyToShow = null;
                if ("history".equalsIgnoreCase(finalFilter)) {
                    historyToShow = plugin.getDatabaseManager() != null ? 
                        plugin.getDatabaseManager().loadRecentPlayerQuestHistory(playerKey, 12) : java.util.Collections.emptyList();
                }

                Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§6§lQuest Log"));

                String streakLine = (streak >= 2) ? "§6Daily Streak: §e" + streak + "d 🔥" : "§7Daily & Weekly for island leveling";
                String statsLine = (plugin.getQuestManager() != null) ? plugin.getQuestManager().getQuestMasterStats(playerKey) : "";
                gui.setItem(4, GUIUtils.createItem(Material.BOOK, "§6§lQuest Log",
                        "§7Onboarding (per player, parallel) + Daily/Weekly (per island)",
                        streakLine,
                        statsLine,
                        "§7Click quest for details • Shift reroll d/w • Use filters below"));

                // Top row filters with unique icons to indicate current view and help filter
                // all, story, daily, weekly + completed, recommended, history
                addFilterButton(gui, 0, "all", "§fAll", finalFilter, Material.BOOK);
                addFilterButton(gui, 1, "story", "§bStory", finalFilter, Material.ENCHANTED_BOOK);
                addFilterButton(gui, 2, "daily", "§eDaily", finalFilter, Material.CLOCK);
                addFilterButton(gui, 3, "weekly", "§5Weekly", finalFilter, Material.PAPER);
                addFilterButton(gui, 5, "completed", "§aCompleted", finalFilter, Material.EMERALD);
                addFilterButton(gui, 6, "recommended", "§6Recommended", finalFilter, Material.NETHER_STAR);
                addFilterButton(gui, 8, "history", "§dHistory", finalFilter, Material.WRITABLE_BOOK);

                // Step 6: Quest Master stats button (player-facing, uses manager stats + history)
                if (plugin.getQuestManager() != null) {
                    String stats = plugin.getQuestManager().getQuestMasterStats(playerKey);
                    java.util.Map<com.thenerdcj.quest.Quest.QuestCategory, Integer> breakdown = plugin.getQuestManager().getCategoryBreakdown(playerKey);
                    java.util.List<String> statLore = new java.util.ArrayList<>();
                    statLore.add("§7" + stats);
                    statLore.add("§7Breakdown:");
                    for (java.util.Map.Entry<com.thenerdcj.quest.Quest.QuestCategory, Integer> e : breakdown.entrySet()) {
                        statLore.add("§f  " + e.getKey() + ": §a" + e.getValue());
                    }
                    statLore.add("§7(History available in filter)");
                    gui.setItem(7, GUIUtils.createItem(Material.NETHER_STAR, "§6§lQuest Master Stats", statLore.toArray(new String[0])));
                }

                if ("history".equalsIgnoreCase(finalFilter) && historyToShow != null) {
                    // History view (Step 4)
                    int hslot = 10;
                    for (String[] entry : historyToShow) {
                        if (hslot >= 44) break;
                        String htitle = entry[0];
                        String hcat = entry[1];
                        String hline = entry[2];
                        String htime = entry[3];
                        Material hmat = getQuestMaterial(Quest.QuestCategory.valueOf(hcat));
                        gui.setItem(hslot, GUIUtils.createItem(hmat, "§7" + htitle,
                            "§7Completed: §f" + htime,
                            "§7Line: §f" + hline,
                            "§8(Archived in your Quest Master log)"));
                        hslot++;
                        if (hslot % 9 == 8) hslot += 2;
                    }
                    if (historyToShow.isEmpty()) {
                        gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§7No quest history yet"));
                    }
                } else {
                    // Normal quest list with sections (enhanced for filters) + pagination support.
                    // Pages are used when the filtered list (after top-row type filter + category intersect)
                    // would overflow the available content slots (10-48 with group header items + item skips).
                    // Prev at 45, Next at 51, Page indicator at 52 (bottom row; claim 49 + refresh 53 stay fixed).
                    // Only mixed views (all/completed/recommended) emit the group header items.
                    // Headers now use the *same* consistent item (OAK_SIGN) for all sections so players
                    // instantly recognize them as separators/labels (not clickable quests). Colored names
                    // + lore still differentiate Story / Daily / Weekly and explain the separation.
                    boolean showGroupHeaders = "all".equalsIgnoreCase(finalFilter)
                            || "completed".equalsIgnoreCase(finalFilter)
                            || "recommended".equalsIgnoreCase(finalFilter);

                    List<Object> displayList = new ArrayList<>();

                    // Story section (onboarding + main story)
                    boolean hasStory = false;
                    for (Quest quest : finalFiltered) {
                        if (quest.getType() != Quest.QuestType.FIRST && quest.getQuestLine() != Quest.QuestLine.MAIN_STORY) continue;
                        if (!hasStory) {
                            if (showGroupHeaders) {
                                displayList.add("HEADER_STORY");
                            }
                            hasStory = true;
                        }
                        displayList.add(quest);
                    }

                    // Daily section
                    boolean hasDaily = false;
                    for (Quest quest : finalFiltered) {
                        if (quest.getType() != Quest.QuestType.DAILY) continue;
                        if (!hasDaily) {
                            if (showGroupHeaders) {
                                displayList.add("HEADER_DAILY");
                            }
                            hasDaily = true;
                        }
                        displayList.add(quest);
                    }

                    // Weekly section
                    boolean hasWeekly = false;
                    for (Quest quest : finalFiltered) {
                        if (quest.getType() != Quest.QuestType.WEEKLY) continue;
                        if (!hasWeekly) {
                            if (showGroupHeaders) {
                                displayList.add("HEADER_WEEKLY");
                            }
                            hasWeekly = true;
                        }
                        displayList.add(quest);
                    }

                    // Pagination calculation (per-player page state via metadata, reset on filter change)
                    int pageNum = usePage;
                    int itemsPerPage = 24; // safe for headers + visual column skips from (slot%9==8 ? +2)
                    int totalEntries = displayList.size();
                    int totalPages = (totalEntries == 0) ? 1 : (totalEntries + itemsPerPage - 1) / itemsPerPage;
                    if (pageNum < 0) pageNum = 0;
                    if (pageNum >= totalPages) pageNum = totalPages - 1;

                    // Sync metadata to the actually displayed (clamped) page so prev/next clicks
                    // read a correct curPage even if a high page was requested for a smaller filter result.
                    player.removeMetadata("quest_page", plugin);
                    player.setMetadata("quest_page", new org.bukkit.metadata.FixedMetadataValue(plugin, pageNum));

                    int from = pageNum * itemsPerPage;
                    int to = Math.min(from + itemsPerPage, totalEntries);
                    List<Object> pageDisplay = (from < to) ? displayList.subList(from, to) : java.util.Collections.emptyList();

                    int slot = 10;
                    for (Object entry : pageDisplay) {
                        if (slot >= 49) break;
                        if (entry instanceof String s && s.startsWith("HEADER_")) {
                            // Use the *exact same item* for every section header so players recognize
                            // them at a glance as non-clickable separators/labels (different from quest items).
                            // OAK_SIGN was chosen as a clear "label / section marker" icon.
                            // Visual separation between Story/Daily/Weekly still comes from the
                            // colored display names + the explanatory lore (scope, reset rules, purpose).
                            ItemStack headerItem;
                            if (s.equals("HEADER_STORY")) {
                                headerItem = GUIUtils.createItem(
                                        Material.OAK_SIGN,
                                        "§a§lOnboarding & Main Story (guided chains)",
                                        "§7Per-player quests — progress in parallel",
                                        "§7Story chains unlock features & minions",
                                        "§7Persistent across seasonal wipes"
                                );
                            } else if (s.equals("HEADER_DAILY")) {
                                String name = "§e§lDAILY (unique per island)" + (streak >= 1 ? "  §6Streak " + streak + " 🔥" : "");
                                headerItem = GUIUtils.createItem(
                                        Material.OAK_SIGN,
                                        name,
                                        "§7Resets daily • Unique to your island",
                                        "§7Adaptive to your recent activity",
                                        "§7Steady progress + streak bonuses"
                                );
                            } else if (s.equals("HEADER_WEEKLY")) {
                                headerItem = GUIUtils.createItem(
                                        Material.OAK_SIGN,
                                        "§5§lWEEKLY (unique per island, big rewards)",
                                        "§7Resets weekly • Unique to your island",
                                        "§7Larger rewards and milestones",
                                        "§7Fair competition per island"
                                );
                            } else {
                                headerItem = GUIUtils.createItem(Material.OAK_SIGN, "§7Quests");
                            }
                            gui.setItem(slot, headerItem);
                            slot++;
                            if (slot % 9 == 8) slot += 2;
                            continue;
                        }
                        if (entry instanceof Quest quest) {
                            Material material = getQuestMaterial(quest.getCategory());
                            String status = quest.isCompleted() ? "§a§lCOMPLETED" :
                                    ((quest.getType() == Quest.QuestType.DAILY || quest.getType() == Quest.QuestType.WEEKLY) ?
                                            (quest.isExpired() ? "§c§lEXPIRED" : "§e§lIN PROGRESS") : "§e§lIN PROGRESS");
                            gui.setItem(slot, createQuestItem(material, quest, status));
                            slot++;
                            if (slot % 9 == 8) slot += 2;
                            continue;
                        }
                    }

                    if (pageDisplay.isEmpty()) {
                        if (showGroupHeaders && ("all".equalsIgnoreCase(finalFilter) || "story".equalsIgnoreCase(finalFilter))) {
                            gui.setItem(10, GUIUtils.createItem(Material.BARRIER, "§7No story quests in this filter"));
                        } else {
                            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§7No quests match this filter"));
                        }
                    }

                    // Page navigation buttons (only when list requires multiple pages)
                    if (totalPages > 1) {
                        NamespacedKey pageActionKey = new NamespacedKey(plugin, "quest_page_action");
                        if (pageNum > 0) {
                            ItemStack prev = GUIUtils.createItem(Material.ARROW, "§e§l« Previous Page",
                                    "§7Page " + (pageNum) + " of " + totalPages + "  (" + totalEntries + " total)");
                            ItemMeta pmeta = prev.getItemMeta();
                            if (pmeta != null) {
                                GUIUtils.setPDCAction(pmeta, pageActionKey, "prev");
                                prev.setItemMeta(pmeta);
                            }
                            gui.setItem(45, prev);
                        }
                        ItemStack info = GUIUtils.createItem(Material.PAPER,
                                "§7Page §f" + (pageNum + 1) + "§7/§f" + totalPages);
                        gui.setItem(52, info);
                        if (pageNum < totalPages - 1) {
                            ItemStack next = GUIUtils.createItem(Material.SPECTRAL_ARROW, "§e§lNext Page »",
                                    "§7Page " + (pageNum + 2) + " of " + totalPages);
                            ItemMeta nmeta = next.getItemMeta();
                            if (nmeta != null) {
                                GUIUtils.setPDCAction(nmeta, pageActionKey, "next");
                                next.setItemMeta(nmeta);
                            }
                            gui.setItem(51, next);
                        }
                    }
                }

                // Claim All (user friendly for completed stack)
                gui.setItem(49, GUIUtils.createItem(Material.LIME_DYE, "§a§lClaim All Completed",
                        "§7Claims every ready quest at once", "§7(Great for multi-objective days)"));

                gui.setItem(53, GUIUtils.createItem(Material.EMERALD, "§a§lGenerate / Refresh",
                        "§7New dailies/weeklies (respects adaptive)"));

                // Modernized filler
                ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
                for (int i = 0; i < 54; i++) {
                    if (gui.getItem(i) == null) gui.setItem(i, glass);
                }

                player.openInventory(gui);
            });
        });
    }

    private void addFilterButton(Inventory gui, int slot, String filterKey, String label, String currentFilter, Material icon) {
        boolean active = filterKey.equalsIgnoreCase(currentFilter);
        Material mat = (icon != null) ? icon : (active ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        String prefix = active ? "§a§l" : "§7";
        gui.setItem(slot, GUIUtils.createItem(mat, prefix + label, "§7Click to filter quest log"));
        // Attach filter key via PDC for click handler
        ItemStack item = gui.getItem(slot);
        if (item != null && item.getItemMeta() != null) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "quest_filter"), PersistentDataType.STRING, filterKey);
            item.setItemMeta(meta);
        }
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
        java.util.List<String> lore = new java.util.ArrayList<>();

        // Step 4: Hidden teaser support (show reward, hide objectives/desc until discovered)
        boolean isTeaser = quest.isHidden() && !quest.isAvailable(new java.util.HashSet<>()); // rough check, full in detail
        if (isTeaser) {
            lore.add("§8??? Hidden Quest");
            lore.add("§7Complete more story or activities to reveal.");
            lore.add("");
            lore.add("§7Teaser Rewards:");
            lore.add("§7Base: §a" + quest.getRewardXp() + " Island XP  §e+$" + quest.getRewardMoney());
            for (Quest.QuestReward r : quest.getExtraRewards()) {
                lore.add("§7+ " + r.getDescription());
            }
            lore.add("");
            lore.add(status);
            ItemStack item = GUIUtils.createItem(Material.BARRIER, "§8??? Hidden Quest", lore.toArray(new String[0]));
            attachQuestPDC(item, quest.getId());
            return item;
        }

        lore.add("§7" + quest.getDescription());
        lore.add("");

        // COMPLEX + USER FRIENDLY: show per-objective progress bars when present (core upgrade)
        java.util.List<Quest.QuestObjective> objs = quest.getObjectives();
        if (!objs.isEmpty() && objs.size() > 1) {
            lore.add("§6Objectives (" + quest.getCompletedObjectiveCount() + "/" + objs.size() + "):");
            int shown = 0;
            for (Quest.QuestObjective o : objs) {
                if (shown++ > 2) { lore.add("§7..."); break; }
                lore.add("  §7" + o.getDescription());
                lore.add("  §7" + o.getProgressBar() + " §f" + o.getProgress() + "/" + o.getTarget() + (o.isCompleted() ? " §a✓" : ""));
            }
            lore.add("");
        } else {
            // legacy or single
            String progressBar = createProgressBar(quest.getProgress(), quest.getTarget());
            lore.add("§7Progress: " + progressBar + " §f" + quest.getProgress() + "/" + quest.getTarget());
            lore.add("");
        }

        lore.add("§7Reward: §a" + quest.getRewardXp() + " Island XP §7+ §e$" + quest.getRewardMoney() + " bank");
        if (quest.getType() == Quest.QuestType.WEEKLY) lore.add("§7+ Weekly milestone XP");
        lore.add("");

        // Step 1: Chain / story info
        if (quest.getQuestLine() == Quest.QuestLine.MAIN_STORY) {
            lore.add("§b" + quest.getQuestLineDisplay() + " §7Chapter " + quest.getChapter());
            List<String> prereqs = quest.getPrerequisites();
            if (!prereqs.isEmpty()) {
                lore.add("§7Unlocked by completing prior story steps.");
            }
        } else if (quest.getQuestLine() == Quest.QuestLine.SIDE) {
            lore.add("§d" + quest.getQuestLineDisplay());
        }

        if (quest.getType() == Quest.QuestType.DAILY || quest.getType() == Quest.QuestType.WEEKLY) {
            lore.add("§7Why: Tailored via adaptive generation (recent activity + balance) for your island's continuous leveling.");
        } else {
            lore.add("§7Onboarding / Story: follow the guided path for feature unlocks.");
        }
        lore.add("");
        lore.add(status);
        lore.add(quest.isCompleted() && !quest.isClaimed() ? "§a§lClick for details & claim" : "§7Click for full details & objectives");
        if (!quest.isCompleted() && (quest.getType() == Quest.QuestType.DAILY || quest.getType() == Quest.QuestType.WEEKLY)) {
            lore.add("§7Shift-click to reroll (cooldown + small bank cost)");
        }

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
        String ownerKey = player.getMetadata("quest_island_id").get(0).asString();

        String itemName = clicked.getItemMeta().getDisplayName();

        String questId = clicked.getItemMeta().getPersistentDataContainer()
                .get(questIdKey, PersistentDataType.STRING);

        // Claim All (user friendly for when many objectives finish at once)
        if (itemName.contains("Claim All")) {
            String playerKey = player.getUniqueId().toString();
            int claimed = 0;
            if (plugin.getQuestManager() != null) {
                // Try both buckets
                claimed += tryClaimAllForKey(ownerKey, player);
                if (!ownerKey.equals(playerKey)) claimed += tryClaimAllForKey(playerKey, player);
            }
            player.playSound(player.getLocation(), claimed > 0 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(claimed > 0 ? "§aClaimed §f" + claimed + "§a quest(s)!" : "§7Nothing ready to claim right now.");
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                this.open(player, ownerKey);
            });
            return;
        }

        // Generate / Refresh
        if (itemName.contains("Generate") || itemName.contains("Refresh")) {
            if (plugin.getQuestManager() != null) {
                plugin.getQuestManager().generateDailyQuests(ownerKey);
                plugin.getQuestManager().generateWeeklyQuests(ownerKey);
            }
            player.sendMessage("§aQuests refreshed (adaptive generation)!");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                this.open(player, ownerKey);
            });
            return;
        }

        // Step 4: Filter tab clicks (use PDC for filter key)
        String filterAction = clicked.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "quest_filter"), PersistentDataType.STRING);
        if (filterAction != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                this.open(player, ownerKey, filterAction);
            });
            return;
        }

        // Pagination: next/previous page buttons (preserves current filter via player metadata)
        String pageAction = clicked.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "quest_page_action"), PersistentDataType.STRING);
        if (pageAction != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            int curPage = 0;
            if (player.hasMetadata("quest_page")) {
                try { curPage = player.getMetadata("quest_page").get(0).asInt(); } catch (Exception ignored) {}
            }
            String currentFilter = "all";
            if (player.hasMetadata("quest_filter")) {
                try { currentFilter = player.getMetadata("quest_filter").get(0).asString(); } catch (Exception ignored) {}
            }
            int newPage = "prev".equalsIgnoreCase(pageAction) ? Math.max(0, curPage - 1) : curPage + 1;
            final int np = newPage;
            final String cf = currentFilter;
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                this.open(player, ownerKey, cf, np);
            });
            return;
        }

        // Reroll with shift click (or normal on reroll item) for agency (daily/weekly)
        if ((event.isShiftClick() || itemName.contains("Reroll")) && questId != null) {
            boolean ok = plugin.getQuestManager().rerollDailyWeeklyQuest(ownerKey, questId, player);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                this.open(player, ownerKey);
            });
            return;
        }

        // Normal click on a quest item -> open rich Detail view (shows all objectives with bars, why, claim there)
        if (questId != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f);
            if (plugin.getQuestDetailGUI() != null) {
                plugin.getThreadSafety().runOnMainThread(() -> {
                    player.closeInventory();
                    plugin.getQuestDetailGUI().open(player, ownerKey, questId);
                });
            } else {
                // Fallback: direct claim attempt (old behavior)
                String playerKey = player.getUniqueId().toString();
                boolean success = plugin.getQuestManager().claimQuest(ownerKey, questId, player);
                if (!success) success = plugin.getQuestManager().claimQuest(playerKey, questId, player);
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
                plugin.getThreadSafety().runOnMainThread(() -> this.open(player, ownerKey));
            }
            return;
        }
    }

    private int tryClaimAllForKey(String key, Player player) {
        int count = 0;
        try {
            java.util.List<Quest> qs = plugin.getQuestManager().getQuestsForIsland(key).join();
            for (Quest q : qs) {
                if (q.isCompleted() && !q.isClaimed() && !q.isExpired()) {
                    if (plugin.getQuestManager().claimQuest(key, q.getId(), player)) count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    // Step 4: Quest Journal item (right-click to open enhanced quest log / story)
    public static ItemStack createQuestJournal(FoliaSkyblock plugin) {
        ItemStack book = GUIUtils.createItem(Material.BOOK, "§6§lQuest Journal",
                "§7Right-click to open your Quest Log,",
                "§7Main Story progress, dailies, and history.",
                "§7Track your island's journey!");
        if (book.hasItemMeta()) {
            ItemMeta meta = book.getItemMeta();
            NamespacedKey key = new NamespacedKey(plugin, "quest_journal");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "true");
            book.setItemMeta(meta);
        }
        return book;
    }

    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            // fallback check off-hand
            item = player.getInventory().getItemInOffHand();
            if (item == null || !item.hasItemMeta()) return;
        }
        NamespacedKey journalKey = new NamespacedKey(plugin, "quest_journal");
        String pdcVal = item.getItemMeta().getPersistentDataContainer().get(journalKey, PersistentDataType.STRING);
        String name = item.getItemMeta().getDisplayName();
        if ("true".equals(pdcVal) || (name != null && name.contains("Quest Journal"))) {
            event.setCancelled(true);
            if (plugin.getQuestManager() != null && plugin.getQuestLogGUI() != null) {
                com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                String key = (island != null) ? island.getId() : player.getUniqueId().toString();
                player.closeInventory();
                plugin.getQuestLogGUI().open(player, key);
            }
        }
    }
}