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
    private final NamespacedKey codexPageKey;

    public QuestLogGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public QuestLogGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.questIdKey = new NamespacedKey(plugin, "quest_id");
        this.filterKey = new NamespacedKey(plugin, "quest_filter");
        this.pageActionKey = new NamespacedKey(plugin, "quest_page_action");
        this.codexPageKey = new NamespacedKey(plugin, "codex_page");
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

                // Interactive Story Codex button - more user friendly lore access (scrollable 12 pages)
                gui.setItem(8, createItem(Material.WRITTEN_BOOK, "§d§lElder Codex",
                    "§7Click to read the full 'Fractured Veil' story",
                    "§7(100 chapters across 10 phases — now with 12 scrollable interactive pages)",
                    "§7Phases, key events, prestige echoes, skills, minions, bazaar, housing,",
                    "§7museum, slayers, upgrades, daily whispers & system synergy hints.",
                    "§aMore user-friendly & scrollable for easy saga management — play normally, dive deep when you want!"));

                // Compute overall active for header (independent of filter)
                int active = (int) quests.stream()
                        .filter(q -> !q.isCompleted() && !q.isClaimed() && !q.isExpired())
                        .count();

                // Prominent "Main Story Progress" teaser (encourages 100-chapter linear story to dragon, dimensions, and Prestige)
                // Placed after the tabs so it doesn't look like another filter tab.
                // Dailies/Weeklies run simultaneously (different tabs).
                Quest activeMainStory = quests.stream()
                    .filter(q -> q.getQuestLine() == Quest.QuestLine.MAIN_STORY && !q.isClaimed() && !q.isCompleted() && !q.isExpired())
                    .findFirst().orElse(null);
                String storyLine = (activeMainStory != null)
                    ? "§bCh." + activeMainStory.getChapter() + "/100  §5→ §dEnder Dragon"
                    : "§7Start the saga (Ch.0+). Codex for lore & hints.";
                gui.setItem(5, createItem(Material.NETHER_STAR, "§d§lMain Story Progress",
                    "§7Strictly linear — one chapter active. Runs happily with dailies, weeklies, minions & more.",
                    storyLine,
                    "§7All quests (incl. MAIN_STORY chapters) persist across server restarts via DB (progress + active saved). Full reset on seasonal wipes for fresh season start. Prestige clears active story so you can replay the saga with multipliers & wisdom.",
                    "§7Elder Codex (book at right) is now more scrollable & interactive: 12 pages of lore, hints, system ties & prestige encouragement. Click everything!",
                    "§aContinuous play across every system leads to the Dragon, Ch.100, and eternal prestige glory. Optional depth — never overwhelming."));

                // Info item (slot 6/7 free) - keep Elder Codex at 8 as the interactive story button
                gui.setItem(6, createItem(Material.BOOK, "§6§lQuest Log",
                        "§7Daily & Weekly Missions run parallel to story",
                        "§7Complete for rewards & XP!",
                        "§7Active: §b" + active + "  §7Filter: §f" + filter,
                        "§eUse tabs right → for filters. Arrows bottom for pages.",
                        "§7Elder Codex (book right) = full interactive story deep-dive."));

                // Apply filter + simple recommended ordering (new GUI)
                java.util.List<Quest> toShow = new java.util.ArrayList<>(quests);
                if ("STORY".equals(filter)) {
                    // Storyline tab shows Onboarding + Main Story (100 chapters: linear narrative to dragon, dimensions, and Prestige).
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
        String title = event.getView().getTitle();
        if (title.contains("Fractured Veil - Story Codex")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            String itemName = clicked.getItemMeta().getDisplayName();
            if (itemName.contains("Close")) {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (itemName.contains("Previous Page")) {
                int p = 0;
                if (player.hasMetadata("codex_page")) try { p = player.getMetadata("codex_page").get(0).asInt(); } catch (Exception ignored) {}
                p = Math.max(0, p - 1);
                player.setMetadata("codex_page", new org.bukkit.metadata.FixedMetadataValue(plugin, p));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getThreadSafety().runOnMainThread(() -> {
                    player.closeInventory();
                    openStoryCodex(player, player.getMetadata("quest_island_id").isEmpty() ? "unknown" : player.getMetadata("quest_island_id").get(0).asString());
                });
                return;
            } else if (itemName.contains("Next Page")) {
                int p = 0;
                if (player.hasMetadata("codex_page")) try { p = player.getMetadata("codex_page").get(0).asInt(); } catch (Exception ignored) {}
                p = Math.min(14, p + 1); // extended for more scrollable interactive lore and continuous play encouragement
                player.setMetadata("codex_page", new org.bukkit.metadata.FixedMetadataValue(plugin, p));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getThreadSafety().runOnMainThread(() -> {
                    player.closeInventory();
                    openStoryCodex(player, player.getMetadata("quest_island_id").isEmpty() ? "unknown" : player.getMetadata("quest_island_id").get(0).asString());
                });
                return;
            } else if (itemName.contains("Phase")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§l" + itemName);
                if (itemName.contains("Phase 1")) {
                    player.sendMessage("§7Long ago, the Skyweavers wove the Veil from starlight and will. When they faded, the Corruption seeped in from the endless dark. The last Elder cast a desperate call across the void.");
                    player.sendMessage("§7You awoke on a tiny speck of dirt floating in nothing. 'The first tear is yours to mend. Place your first blocks — walls against the void, a roof against the falling sky. This humble shelter is the beginning of everything.'");
                    player.sendMessage("§7Mine the ancient stone that remembers the old worlds. Level your Mining skill so your tools sing. Plant the first seeds so life takes hold. Deploy your first minion — it will work while you dream of greater things. Chapters 1-10 are the foundation. Every block, every crop, every minion thread strengthens the Veil. Your legend between 0 and 100 starts here, player.");
                } else if (itemName.contains("Phase 2")) {
                    player.sendMessage("§7The first tear is stitched, but shadows leak through. Corrupted creatures crawl from the cracks at night.");
                    player.sendMessage("§7'Raise an empire worthy of the sky. Expand your platform until it feels like home. Craft proper halls, farms, and storage. Place furniture and decorations so the island knows it is loved. Visit the Island Upgrade menu — power flows to those who invest in their land.'");
                    player.sendMessage("§7Purge the hordes with sword and skill. Begin your collection log. Trade your surplus at the Bazaar so the island bank grows fat with coins. Deploy more minions. By the end of this phase you will stand as guardian of the Overworld, ready for the flames beyond.'");
                } else if (itemName.contains("Phase 3")) {
                    player.sendMessage("§7The Veil screams. The Nether's fire is the only way forward. Obsidian must be gathered or bought. A 4x5 frame must be lit.");
                    player.sendMessage("§7'Step through the gate into heat and soul sand. The blazes guard the rods that will one day open the final door. Hunt them carefully. Let your minions farm while you explore the crimson forests. The Nether will burn away weakness and forge the first true keys to the End.'");
                } else if (itemName.contains("Phase 4")) {
                    player.sendMessage("§7Fortresses rise from the lava seas like bones of the damned. Wither skeletons stalk the halls with skulls that can summon greater horrors.");
                    player.sendMessage("§7'Build a true outpost here — chests, portal room, even a nether wart farm. Trade the tears of ghasts and the quartz of the cliffs. The economy of hell itself will fund your ascent. Every skull you claim brings the Dragon one step closer.'");
                } else if (itemName.contains("Phase 5")) {
                    player.sendMessage("§7The lords of flame bow. Ghasts wail, magma cubes boil. You have learned to walk in fire.");
                    player.sendMessage("§7'Combine the rods of blazes with pearls won from the void-touched. Craft Eyes of Ender — small stars that will pierce the final veil. Stockpile them. Use the island bank and your growing empire. The Dragon's prison is no longer a rumor. It is a destination.'");
                } else if (itemName.contains("Phase 6")) {
                    player.sendMessage("§7Twelve stars. Throw them into the sky and they point the way. The End dimension cracks open.");
                    player.sendMessage("§7'Gather more pearls from the tall pale ones. The stronghold waits in the darkness of the Overworld. The Dragon sleeps on an island of obsidian pillars. All your skills, all your minions, all your trades have led to this threshold. Chapter 60 grants you the End. Prepare your heart.'");
                } else if (itemName.contains("Phase 7")) {
                    player.sendMessage("§7The outer islands float like forgotten dreams. Shulkers drift in silence, guarding shells that can carry the world.");
                    player.sendMessage("§7'Build here at the edge of existence. Use purpur and chorus and end stone. One wrong step is the void forever. Harvest the fruit that lets you leap between islands. Shulker boxes will revolutionize your storage. All your previous chapters converge in this beautiful, deadly place.'");
                } else if (itemName.contains("Phase 8")) {
                    player.sendMessage("§7The pillars stand like accusations. Crystals pulse with stolen power.");
                    player.sendMessage("§7'Destroy them. Use arrows, snowballs, anything. Watch the dragon's breath — it is death that lingers. Build cover. When the beast dives, strike the head. When it falls, the Veil will sing for the first time in ages. Claim the egg. It is both trophy and reminder that every end is a new beginning. Prestige now opens the true path to eternity.'");
                } else if (itemName.contains("Phase 9")) {
                    player.sendMessage("§7You have slain the heart of the last great tear. But the Veil is vast and the Corruption is patient.");
                    player.sendMessage("§7'Open the Prestige menu. Rebirth your island. Everything you built before will multiply. Return with power that makes the first run feel like a dream. Raise monuments of blocks from every dimension so that future versions of yourself will know what was sacrificed. Write the story again — faster, grander, forever.'");
                } else if (itemName.contains("Phase 10")) {
                    player.sendMessage("§7Cities of automation. Legions of minions. Every collection complete. Every skill a weapon. Every island upgrade a crown.");
                    player.sendMessage("§7'Chapter 100 is not a finish line. It is permission to become eternal. Prestige again. Weave stronger each time. The Veil remembers every cycle you complete. You are no longer the weaver who answered the call — you are the call itself. The sky belongs to you now and forever.'");
                    player.sendMessage("§6§lClaim chapter 100 and know: the legend continues with every prestige. The story between 0 and 100 is only the first telling.'");
                }
            } else if (itemName.contains("Full") || itemName.contains("Story")) {
                player.sendMessage("§dThe Sky Elder: 'The realms fracture. You are the spark that mends them. Master skyblock. Conquer dimensions. Slay the Dragon. Then Prestige — again and again — until your thread is the strongest in the Veil.'");
                player.sendMessage("§7Progress one chapter at a time. All other quests (dailies, weeklies, onboarding, slayers) run in parallel. No overwhelm. Just continuous, meaningful play leading to chapter 100 and beyond.");
            } else if (itemName.contains("Call") || itemName.contains("Prologue")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§5§lThe Call - Prologue");
                player.sendMessage("§7Long before you stepped on the floating speck, the Skyweavers sang to hold the Veil together. One by one they fell silent. The Corruption from the void began to tear holes between the Overworld, the fires of the Nether, and the cold End.");
                player.sendMessage("§7The last Elder reached across the void and called for a new weaver — someone who would master the sky itself. That call reached you. You are the answer to the fracture. Chapter 1 is only the beginning of the tale between 0 and 100.");
                player.sendMessage("§7The story starts when you place your first block. Every minion you deploy, every trade you make, every boss you fell, and every prestige you take re-weaves the threads.");
            } else if (itemName.contains("First Flame") || itemName.contains("Ch. ~45")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lThe First Flame");
                player.sendMessage("§7You hold the first Eye of Ender. It hums with the combined power of Overworld pearls and Nether fire. 'This is not just a key. It is the promise that the Dragon will fall and the Veil will know your name.' Use your minions, your trades, and your growing empire to craft the full dozen.");
            } else if (itemName.contains("Dragon's Fall") || itemName.contains("Ch. ~80")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§5§lThe Dragon's Fall");
                player.sendMessage("§7The pillars are shattered. The beast dives one last time. When it falls, the egg is yours and the first great tear is mended. But the Elder whispers: 'Prestige now. Return stronger. The true story is the one you will re-tell across many lives.'");
            } else if (itemName.contains("Eternal Cycle") || itemName.contains("Prestige")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lThe Eternal Cycle");
                player.sendMessage("§7Prestige is rebirth. Your island resets but you carry the knowledge of every chapter. The high story quests will guide you to build even greater — more minions, grander monuments, repeated conquests of dimensions. Each cycle the multipliers grow. The Veil is eternal because you never stop weaving.");
            } else if (itemName.contains("Minion") || itemName.contains("Choir")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lThe Minion Choir");
                player.sendMessage("§7They are your hands when you are away. Deploy them early, specialize often. Farmers feed the empire, miners dig the bones of old worlds, fighters prepare you for the Dragon. In prestige your choir sings louder from the first hour. Let them handle the small notes while you conduct the symphony of the full saga.");
            } else if (itemName.contains("Bazaar")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lBazaar of Threads");
                player.sendMessage("§7Surplus becomes the fuel for dreams. Sell often, buy what you need, let the island bank remember every coin. Continuous trading keeps the Veil strong between big story beats. In prestige runs this economy lets you shortcut grinds and focus on the beautiful and the grand from day one.");
            } else if (itemName.contains("Housing") || itemName.contains("Home")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lHousing: The Soul of Home");
                player.sendMessage("§7A bed, a chair, a garden, memorials to fallen dragons — these turn a floating rock into a place that feels like yours across lives. Build with intention in every dimension. In prestige the first thing you will do is recreate the comforts that made the sky feel less empty. Housing is how the legend lives in the blocks.");
            } else if (itemName.contains("Museum")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lMuseum of the Veil");
                player.sendMessage("§7Your relics tell the tale when words fail. Place dragon eggs, shulker shells, rare blocks, furniture from every dimension. In future prestiges you will walk these halls and feel the full weight of 0-100. The museum is how the story outlives each cycle and inspires the next.");
            } else if (itemName.contains("Daily Mending") || itemName.contains("Daily Weave")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lDaily Mending Rituals");
                player.sendMessage("§7Do not underestimate the power of the small. Log in, tend your crops, sell at the bazaar, deploy or check a minion, complete a daily or weekly quest. These acts are the daily mending of the Veil. In prestige these habits will make your runs soar with accumulated power and wisdom. Make them part of every session.");
            } else if (itemName.contains("Prestige Power") || itemName.contains("Prestige Prep")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige's True Gift");
                player.sendMessage("§7Each time you prestige the world bends in your favor. XP flows faster, minions produce more, drops are richer, skills level swifter. Late chapters exist to teach you how to build the machine (automation, monuments, max collections) that benefits most from these gifts. The more cycles, the more godlike every future telling of the tale becomes.");
            } else if (itemName.contains("Continuous Thread")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lThe Continuous Thread");
                player.sendMessage("§7Every action you take is a thread in the Veil. Dailies, minions, housing, museum, slayers, upgrades, bazaar trades — do them often with joy. They accumulate the power and wisdom that make prestige runs legendary. The story rewards the weaver who plays continuously. The sky itself will remember you.");
            } else if (itemName.contains("How to Keep the Weave Strong")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lHow to Keep the Weave Strong - Continuous Play Tips");
                player.sendMessage("§7Do dailies daily. Deploy and upgrade minions constantly. Trade at Bazaar, level skills, decorate housing, curate museum. Visit upgrades often. The grand chapters come through joyful normal play + these small acts. Prestige lets you do it all again with multipliers and wisdom from the full tale.");
            } else if (itemName.contains("Prestige Power Multipliers")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lPrestige Power Multipliers");
                player.sendMessage("§7Each rebirth multiplies your gains: XP, money, drops, skill progress, minion output. Late chapters teach building the machine (automation, monuments, max collections) that benefits most. The more cycles you complete, the more godlike every future 0-100 tale becomes.");
            } else if (itemName.contains("Current Chapter Dynamic Hint")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lCurrent Chapter Dynamic Hint");
                player.sendMessage("§7Check your active story chapter in the Quest Log Storyline tab or Codex. The Elder suggests using dailies, minions, specific systems for your phase. Continuous small actions advance the weave and prepare for prestige power.");
            } else if (itemName.contains("Skyweaver's Sacrifice")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§5§lThe Skyweaver's Sacrifice");
                player.sendMessage("§7They sang until their voices broke. The last one chose you. 'Do not let our song end in silence. Complete the 100 chapters and let the cycle sing anew with each prestige.'");
            } else if (itemName.contains("Egg's Legacy")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lThe Egg's Legacy");
                player.sendMessage("§7The Dragon's egg is both trophy and promise. In every rebirth, it waits for you to claim it again. 'Place it where all can see — the story of the first fall must never be forgotten, even as you rise higher in each cycle.'");
            } else if (itemName.contains("Tailored for Your Chapter")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lTailored for Your Chapter");
                player.sendMessage("§7The Elder gives hints based on your active progress. Use dailies, minions, and phase systems for steady continuous advancement to the Dragon and prestige power.");
            } else if (itemName.contains("Suggestion: Daily Mending")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lSuggestion: Daily Mending");
                player.sendMessage("§7Complete a daily today - these quiet stitches keep the Veil strong and build the habits that make prestige runs soar with power.");
            } else if (itemName.contains("Suggestion: Minion Check")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lSuggestion: Minion Check");
                player.sendMessage("§7Inspect and upgrade minions - they sustain the story between grand chapters, and in prestige they multiply your continuous efforts from the start.");
            } else if (itemName.contains("Prestige Echo List")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige Echo List");
                player.sendMessage("§7Echo 1: first return faster with power. Echo 2: deeper monuments. Continuous play turns the Fractured Veil into your eternal legend across lives.");
            } else if (itemName.contains("Shulker Echoes")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lShulker Echoes");
                player.sendMessage("§7The outer islands were silent teachers. Their shells let you carry the world. 'Build even in the void. Every dimension conquered becomes part of your legend, ready to be retold stronger after prestige.'");
            } else if (itemName.contains("Weaver's First Prestige")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lThe Weaver's First Prestige");
                player.sendMessage("§7The dragon is gone, the cycle begins. 'Return stronger, wiser. The early chapters will be faster, but the meaning deeper. Continuous play means living the tale again with new power.'");
            } else if (itemName.contains("Grand Monument")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§f§lThe Grand Monument");
                player.sendMessage("§7Build something that outlasts the cycles. 'Blocks, furniture, and memories from every chapter. In future prestiges, stand before it and feel the weight of your legend.'");
            } else if (itemName.contains("Song Eternal")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lThe Song Eternal");
                player.sendMessage("§7Ch.100 is not the end. 'You are the song. Prestige again and again — the story between 0 and 100 grows richer each time. The Veil is eternal because you keep playing.'");
            } else if (itemName.contains("First Slayer")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lFirst Slayer's Path");
                player.sendMessage("§7'Your blade meets the first corrupted. Slayer quests are not side content — they are the training for the Dragon and beyond. Every tier conquered strengthens the threads of your legend.'");
            } else if (itemName.contains("Island's Heart")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lIsland's Heart");
                player.sendMessage("§7'Trade and upgrades make the speck a kingdom. The island bank and upgrades are the heartbeat. In every prestige, this heart beats stronger from the start.'");
            } else if (itemName.contains("Prestige Echo 1")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige Echo 1");
                player.sendMessage("§7'The first return. The early tears feel different with power. Minions work faster, builds rise quicker, but the wonder remains. This is how continuous play turns into eternal story.'");
            } else if (itemName.contains("Prestige Echo 2")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige Echo 2");
                player.sendMessage("§7'Deeper in the loop. Your monuments stand as proof of past cycles. The story encourages you to push further each time — more automation, grander designs, repeated mastery of the Veil.'");
            } else if (itemName.contains("Corruption's Touch")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§c§lThe Corruption's Touch");
                player.sendMessage("§7It leaks from every tear. Every hostile you defeat, every block you place in defiance, every trade that builds your empire — these are the weapons against the void. The story is your resistance.");
            } else if (itemName.contains("First Slayer")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lFirst Slayer's Path");
                player.sendMessage("§7'Your blade meets the first corrupted. Slayer quests are not side content — they are the training for the Dragon and beyond. Every tier conquered strengthens the threads of your legend. Continuous combat builds the tale.'");
            } else if (itemName.contains("Island's Heart")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lIsland's Heart");
                player.sendMessage("§7'Trade and upgrades make the speck a kingdom. The island bank and upgrades are the heartbeat. In every prestige, this heart beats stronger from the start. Small investments compound across cycles.'");
            } else if (itemName.contains("Skill Forges") || itemName.contains("Skill Weaves")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lSkill Forges Eternal");
                player.sendMessage("§7Mining, combat, farming — each skill a forge for the legend. Level continuously; prestige amplifies them so late chapters feel effortless. The true weaver masters all skills across cycles.");
            } else if (itemName.contains("Housing Legacy")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lHousing Legacy Eternal");
                player.sendMessage("§7A home in every dimension, furniture as memory. In prestige, recreate your sanctuaries first — they ground the story. Housing is how the Veil remembers you lived.");
            } else if (itemName.contains("Daily Rituals") || itemName.contains("Daily Weaver")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lDaily Rituals of the Weaver");
                player.sendMessage("§7Log in, tend crops, sell at bazaar, check minion, do daily. Small consistent acts build legends. Prestige habits make runs soar. Make part of every session.");
            } else if (itemName.contains("Prestige Echo 1")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige Echo 1");
                player.sendMessage("§7'The first return. The early tears feel different with power. Minions work faster, builds rise quicker, but the wonder remains. This is how continuous play turns into eternal story.'");
            } else if (itemName.contains("Prestige Echo 2")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige Echo 2");
                player.sendMessage("§7'Deeper in the loop. Your monuments stand as proof of past cycles. The story encourages you to push further each time — more automation, grander designs, repeated mastery of the Veil.'");
            } else if (itemName.contains("Prestige Recollections")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lPrestige Recollections");
                player.sendMessage("§7'Echoes of past cycles guide you. The early tears will be faster, but the meaning deeper. Continuous play across lives turns the story into legend.'");
            } else if (itemName.contains("The Eternal Choice")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lThe Eternal Choice");
                player.sendMessage("§7'Prestige or linger? The story continues either way. Each cycle adds power and insight. Embrace the loop to fully experience the 0-100 tale.'");
            } else if (itemName.contains("Daily Mending")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lDaily Mending");
                player.sendMessage("§7'Every daily task, every weekly goal — these are the quiet stitches. Do your dailies not just for rewards, but to keep the Veil strong. Continuous play is the true path to prestige.'");
            } else if (itemName.contains("Museum of Legends")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lMuseum of Legends");
                player.sendMessage("§7'Your journey's artifacts on display. Place relics from every phase. In future prestiges, visitors (and your future self) will see the full tale told in blocks and items.'");
            } else if (itemName.contains("Prestige Prep")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige Prep");
                player.sendMessage("§7'Late chapters teach you to build for the next life. Automate everything, raise monuments, max power. When you prestige, the early chapters become a canvas for your greater legend.'");
            } else if (itemName.contains("How to Experience")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lLiving the Story");
                player.sendMessage("§7The Main Story is strictly linear — one chapter active at a time. Complete it (through normal play: building, mining, farming, combat, trading, minions) to unlock the next. All other quest types run at the same time.");
                player.sendMessage("§7The Codex is your living journal. Read it often for motivation and hints. The story encourages you to use every part of the server: skills, housing, slayers, the bank, the museum, everything.");
            } else if (itemName.contains("Skills of the Weaver")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lSkills of the Weaver");
                player.sendMessage("§7'Mining and Farming root you in the first tears. Combat and slayer skills temper you for the Nether and End. Push every skill — they are the quiet power that makes later chapters sing. In prestige, early levels fly by so you can reach the true heights sooner.'");
            } else if (itemName.contains("Minion Choir")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lThe Minion Choir");
                player.sendMessage("§7'They are your hands when you are away. Deploy them early, specialize them often. While they harvest, you explore, fight, trade, and build the grand. Every prestige your choir grows stronger from the first hour. Let them sing the small notes while you conduct the symphony.'");
            } else if (itemName.contains("Bazaar of Threads")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lBazaar of Threads");
                player.sendMessage("§7'Surplus becomes coin, coin becomes power. Trade often at the Bazaar — it is not just money, it is the economy that lets you shortcut grinds in later cycles. The island bank holds the memory of every exchange. Continuous trade is continuous mending.'");
            } else if (itemName.contains("Museum of the Veil")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lMuseum of the Veil");
                player.sendMessage("§7'Your relics tell the tale when words fail. Place dragon eggs, shulker shells, rare blocks, furniture from every dimension. In future prestiges you will walk these halls and feel the full weight of 0-100. The museum is how the story outlives each cycle.'");
            } else if (itemName.contains("Housing Eternal")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lHousing Eternal");
                player.sendMessage("§7'A bed, a chair, a garden — these turn a floating rock into home. Build with intention in every dimension. In prestige the first thing you do is recreate the comforts that made the island yours. Housing is how the Veil remembers you lived here.'");
            } else if (itemName.contains("Live the Weave")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lLive the Weave - Continuous Play Tips");
                player.sendMessage("§7Do your dailies and weeklies every day — they are the small stitches. Deploy and upgrade minions constantly. Trade at the Bazaar, level skills, decorate with furniture. Visit the museum and upgrades menu often. The grand chapters come through joyful normal play. Prestige lets you do it all again with multipliers and wisdom.");
            } else if (itemName.contains("Current Chapter Whisper") || itemName.contains("Chapter Whisper")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                try {
                    String pid = player.getMetadata("quest_island_id").isEmpty() ? null : player.getMetadata("quest_island_id").get(0).asString();
                    if (pid != null && plugin.getQuestManager() != null) {
                        java.util.List<Quest> qs = plugin.getQuestManager().getQuestsForIsland(pid).join();
                        Quest story = qs.stream().filter(q -> q.getQuestLine() == Quest.QuestLine.MAIN_STORY && !q.isClaimed()).findFirst().orElse(null);
                        if (story != null) {
                            String shortLore = story.getDescription().length() > 140 ? story.getDescription().substring(0, 137) + "..." : story.getDescription();
                            player.sendMessage("§e§lElder whispers for Ch." + story.getChapter() + ":");
                            player.sendMessage("§7" + shortLore);
                            player.sendMessage("§7§oContinue through normal play (builds, fights, trades, dailies, minions). The Codex holds the full saga and hints for every phase.");
                        } else {
                            player.sendMessage("§eElder: 'Your current story chapter is complete or not yet started. Check the Storyline tab or begin the journey in the Codex.'");
                        }
                    } else {
                        player.sendMessage("§eElder: 'Open the Quest Log first to hear the whisper for your island.'");
                    }
                } catch (Exception ex) {
                    player.sendMessage("§eElder: 'The threads are many. Read the phases in the Codex to find your place in the 0-100 tale.'");
                }
            } else if (itemName.contains("Slayer's Eternal Forge") || itemName.contains("Slayer")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§c§lSlayer's Eternal Forge");
                player.sendMessage("§7'Your blade meets ever stronger shadows. Slayer quests are not side content — they are the forge that tempers you for the Dragon and the prestige runs that follow. Higher tiers unlock as your chapters advance. In every rebirth you will race to reclaim these titles with greater rewards.'");
            } else if (itemName.contains("Prestige Power Multipliers") || itemName.contains("Power Multipliers")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lPrestige Power Multipliers");
                player.sendMessage("§7'Each time you prestige the world itself bends in your favor. XP flows faster, minions produce more, drops are richer, skills level swifter. The late chapters exist to teach you how to build a machine that benefits most from these gifts. The more cycles you complete, the more godlike every future telling of the 0-100 tale becomes.'");
            } else if (itemName.contains("Daily Weaver's Journal") || itemName.contains("Daily Weaver")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lDaily Weaver's Journal");
                player.sendMessage("§7'Even on days when the grand chapters feel far, the dailies and weeklies keep the threads taut. A few harvests, a trade or two, a minion check — these small acts are the true continuous play that makes the Veil strong. The Elder smiles on the weaver who never lets a day go un-mended. Do them, and prestige will feel twice as sweet.'");
            } else if (itemName.contains("Island Bank & Worth Legacy") || itemName.contains("Bank & Worth")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lIsland Bank & Worth Legacy");
                player.sendMessage("§7'Every coin saved and every block placed increases the legend of this speck. Prestige resets the physical island, yet your mastery of growth remains. Late chapters push you to max these numbers so that every future life begins wealthy in knowledge and ready to build faster than ever.'");
            } else if (itemName.contains("Upgrades: Heartbeat") || itemName.contains("Heartbeat Eternal")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lUpgrades: Heartbeat Eternal");
                player.sendMessage("§7'The /is upgrade menu is where a platform becomes a kingdom. Every tier you unlock early pays dividends in every later chapter and every prestige. Make these investments often — they are the quiet power that lets legends rise in record time after rebirth.'");
            } else if (itemName.contains("Corruption's Deeper") || itemName.contains("Corruption's Deeper Shadow")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§5§lCorruption's Deeper Shadow");
                player.sendMessage("§7'Every tear in the Veil lets the void whisper lies and spawn horrors. But you answer with blocks, blades, trades, and daily mends. The story is resistance made manifest. In every prestige you return as a brighter flame against the dark.'");
            } else if (itemName.contains("Skyweaver Legacy") || itemName.contains("Skyweaver Legacy & Echoes")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lSkyweaver Legacy & Echoes");
                player.sendMessage("§7'Those who came before sang until their voices broke. Their echoes live in the Codex and in every monument you raise. Honor them by completing the full cycle. In prestige you carry their song forward with new power.'");
            } else if (itemName.contains("Minion Specializations") || itemName.contains("Minion Specializations & Legends")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lMinion Specializations & Legends");
                player.sendMessage("§7'Farmers feed the empire. Miners dig the bones of old worlds. Fighters train you for the Dragon. Choose wisely at each phase. In prestige, your specialized choir will already be singing from the first hour, letting you focus on the beautiful and the grand.'");
            } else if (itemName.contains("Prestige Record") || itemName.contains("Prestige Record & Cycle Journal")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lPrestige Record & Cycle Journal");
                player.sendMessage("§7'Each rebirth adds a volume in your legend. Stand before the monuments and museum pieces from past lives and remember. The Codex and your builds together form the living journal of the Fractured Veil. The more cycles you complete, the more glorious the library becomes.'");
            } else if (itemName.contains("Daily Rituals") || itemName.contains("Daily Rituals of the Weaver")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lDaily Rituals of the Weaver");
                player.sendMessage("§7'Do not underestimate the power of the small. Log in, tend your crops, sell at the bazaar, deploy or check a minion, complete a daily or weekly quest. These acts are the daily mending of the Veil. In prestige, these habits will make your runs soar with accumulated power and wisdom. Make them part of your every session.'");
            } else if (itemName.contains("Housing: The Soul") || itemName.contains("Housing: The Soul of Home")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lHousing: The Soul of Home");
                player.sendMessage("§7'A bed, a chair, a garden, a memorial to fallen dragons — these turn the floating rock into a place that feels like yours across lives. In every prestige, the first thing you will want to do is recreate the comforts that made the sky feel less empty. Housing is how your legend lives in the blocks.'");
            } else if (itemName.contains("Slayer's Oath") || itemName.contains("Slayer's Oath and the Dragon")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§c§lSlayer's Oath and the Dragon");
                player.sendMessage("§7'Every slayer you complete forges your blade and your will. The tiers grow with your chapters, preparing you for the final confrontation and giving you strength that echoes in prestige runs. Do them regularly — the shadows are the test, and you are the answer.'");
            } else if (itemName.contains("Bazaar: The Living") || itemName.contains("Bazaar: The Living Economy")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lBazaar: The Living Economy");
                player.sendMessage("§7'Surplus is not waste — it is the thread that connects your island to the greater weave. Trade often, build your bank, fund your dreams. The economy you build here will let you shortcut grinds in future prestiges, letting you focus on the story and the beauty.'");
            } else if (itemName.contains("The Continuous Thread")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lThe Continuous Thread");
                player.sendMessage("§7'Every action weaves the Veil a little stronger. Do your dailies, let minions work, build and decorate your home, fill the museum, chase slayers, trade at the bazaar. These are not chores — they are the quiet magic that prepares you for prestige glory. Play a little every day.'");
            } else if (itemName.contains("Prestige's True Gift")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige's True Gift");
                player.sendMessage("§7'When the Dragon falls and you prestige, you return not weaker but with multipliers on all gains. Early chapters fly by, letting you reach the beautiful late-game projects and legacies faster. Each cycle the tale deepens. The Codex is your guide through every life.'");
            } else if (itemName.contains("Daily Threads Journal") || itemName.contains("Daily Threads")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lDaily Threads Journal");
                player.sendMessage("§7'Your dailies and weeklies are the living journal of the Veil. Complete them every day to mend quietly and build power for prestige runs. They are the heartbeat of continuous play.'");
            } else if (itemName.contains("Prestige Legacy Guide") || itemName.contains("Legacy Guide")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige Legacy Guide");
                player.sendMessage("§7'Prestige resets the island but multiplies your soul. Rebuild early as a sprint, then focus on housing, museum, grand builds. The 0-100 tale gets richer every cycle.'");
            } else if (itemName.contains("Slayer's Continuous Path") || itemName.contains("Slayer's Continuous")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§c§lSlayer's Continuous Path");
                player.sendMessage("§7'Face the shadows regularly. Slayer quests forge strength for the Dragon and echo in prestige. They are part of the daily weave that keeps your legend sharp.'");
            } else if (itemName.contains("Bazaar Cycles of Wealth") || itemName.contains("Bazaar Cycles")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lBazaar Cycles of Wealth");
                player.sendMessage("§7'Trade is the river that waters the empire. Sell surplus daily; bank grows and funds dreams. In prestige, your habits let you skip grinds and focus on the story.'");
            } else if (itemName.contains("Skyweaver's Eternal Song") || itemName.contains("Eternal Song")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§5§lSkyweaver's Eternal Song");
                player.sendMessage("§7'The old weavers sing through you. Every daily is a note, every build a verse, every prestige a chorus. Complete the cycle and your voice joins theirs forever in the Veil's great song.'");
            } else if (itemName.contains("Museum as Living Legend") || itemName.contains("Living Legend")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lMuseum as Living Legend");
                player.sendMessage("§7'Your items are chapters in stone and item. Curate as you go — in prestige these displays will whisper the full tale back to you. A growing museum is proof of a life well lived across cycles.'");
            } else if (itemName.contains("Skill Weaves of the Master") || itemName.contains("Skill Weaves")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lSkill Weaves of the Master");
                player.sendMessage("§7'Each skill level is a thread pulled taut. Mine, farm, fight, trade — level them always. In prestige these skills let you weave the early chapters in hours instead of days, freeing you for the eternal works.'");
            } else if (itemName.contains("Skill Forges Eternal") || itemName.contains("Skill Forges")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lSkill Forges Eternal");
                player.sendMessage("§7'Mining, combat, farming — each skill a forge for the legend. Level continuously; prestige amplifies them so late chapters feel effortless. The true weaver masters all skills across cycles.'");
            } else if (itemName.contains("Housing Legacy Eternal") || itemName.contains("Housing Legacy")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lHousing Legacy Eternal");
                player.sendMessage("§7'A home in every dimension, furniture as memory. In prestige, recreate your sanctuaries first — they ground the story. Housing is how the Veil remembers you lived.'");
            } else if (itemName.contains("Museum of Echoes") || itemName.contains("Museum of Echoes")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lMuseum of Echoes");
                player.sendMessage("§7'Relics from every chapter, displayed for future you. Curate as you play; prestige makes these echoes guide faster conquests. Your museum is the living proof of continuous mastery.'");
            } else if (itemName.contains("Slayer's Eternal Blade") || itemName.contains("Eternal Blade")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§c§lSlayer's Eternal Blade");
                player.sendMessage("§7'Slayers not side — core to the hero's path. Regular tiers build power for Dragon and prestige speedruns. The blade that never dulls across lives.'");
            } else if (itemName.contains("Skill Forges Eternal") || itemName.contains("Skill Forges")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lSkill Forges Eternal");
                player.sendMessage("§7'Each skill is a forge. Level them daily; in prestige they make the story flow like a river, freeing you for the grand chapters and legacies.'");
            } else if (itemName.contains("Housing Legacy Eternal") || itemName.contains("Housing Legacy")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lHousing Legacy Eternal");
                player.sendMessage("§7'Your homes across dimensions are chapters in blocks. In prestige, building them first reminds you why you weave — continuous care makes the story home.'");
            } else if (itemName.contains("Museum of Echoes") || itemName.contains("Museum of Echoes")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lMuseum of Echoes");
                player.sendMessage("§7'Display your journey's artifacts. In prestige, walking through them recalls the full tale and inspires the next cycle's masterpieces.'");
            } else if (itemName.contains("Slayer's Eternal Blade") || itemName.contains("Eternal Blade")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§c§lSlayer's Eternal Blade");
                player.sendMessage("§7'Slayers not side — core to the hero's path. Regular tiers build power for Dragon and prestige speedruns. The blade that never dulls across lives.'");
            } else if (itemName.contains("Skill Forges Eternal") || itemName.contains("Skill Forges")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lSkill Forges Eternal");
                player.sendMessage("§7'Each skill is a forge. Level them daily; in prestige they make the story flow like a river, freeing you for the grand chapters and legacies.'");
            } else if (itemName.contains("Housing Legacy Eternal") || itemName.contains("Housing Legacy")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lHousing Legacy Eternal");
                player.sendMessage("§7'Your homes across dimensions are chapters in blocks. In prestige, building them first reminds you why you weave — continuous care makes the story home.'");
            } else if (itemName.contains("Museum of Echoes") || itemName.contains("Museum of Echoes")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lMuseum of Echoes");
                player.sendMessage("§7'Display your journey's artifacts. In prestige, walking through them recalls the full tale and inspires the next cycle's masterpieces.'");
            } else if (itemName.contains("Slayer's Eternal Blade") || itemName.contains("Eternal Blade")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§c§lSlayer's Eternal Blade");
                player.sendMessage("§7'Slayers not side — core to the hero's path. Regular tiers build power for Dragon and prestige speedruns. The blade that never dulls across lives.'");
            } else if (itemName.contains("Minion Symphony") || itemName.contains("Minion Symphony Eternal")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lMinion Symphony Eternal");
                player.sendMessage("§7'They are your hands and your memory. Specialize, upgrade, expand. While they work you live the grand chapters. In every prestige they multiply your continuous effort from minute one. The true weaver never works alone.'");
            } else if (itemName.contains("Slayer's Oath Eternal")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§c§lSlayer's Oath Eternal");
                player.sendMessage("§7'Your blade meets ever-greater shadows. Slayer tiers are the continuous forge. Do them between story beats. In prestige you will race to reclaim these titles with greater rewards and speed. The Dragon fears a practiced slayer.'");
            } else if (itemName.contains("Wealth That Remembers") || itemName.contains("Bazaar: Wealth")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§a§lBazaar: Wealth That Remembers");
                player.sendMessage("§7'Every trade is a stitch. Surplus to coin, coin to dreams. Keep the flow alive daily. Prestige turns these habits into instant capital for monuments and automation. The bank never forgets a consistent weaver.'");
            } else if (itemName.contains("Museum as Eternal Journal") || itemName.contains("Eternal Journal")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lMuseum as Eternal Journal");
                player.sendMessage("§7'Curate relics as you play — eggs, shells, furniture from every dimension. In future prestiges these halls will tell you the full tale before you place a single block. Your museum is proof you lived the saga.'");
            } else if (itemName.contains("Multipliers Explained") || itemName.contains("Prestige Multipliers")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige Multipliers Explained");
                player.sendMessage("§7'XP flows faster. Minions produce more. Drops richer. Skills rise swifter. Late chapters exist so you build the perfect machine to drink deeply from these gifts. More cycles = more divine every retelling of 0-100.'");
            } else if (itemName.contains("Daily Weave — The Secret")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lDaily Weave — The Secret");
                player.sendMessage("§7'Five minutes. Dailies, minion glance, bazaar stop. These tiny acts are the invisible power that compounds across lives. Prestige turns the daily weaver into a god of the early game. Never skip — the Elder notices.'");
            } else if (itemName.contains("Island Upgrades: The True Heart") || itemName.contains("True Heart")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lIsland Upgrades: The True Heart");
                player.sendMessage("§7'Every tier is a permanent gift to all future yous. Unlock them steadily. In prestige these foundations let your sky empire rise in record time so you can focus on beauty, legends, and the grand chapters from day one.'");
            } else if (itemName.contains("Housing & Furniture Legacy")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lHousing & Furniture Legacy");
                player.sendMessage("§7'A bed in the void, a garden in hell, a memorial among the stars. Furniture turns conquest into home. In every prestige the first joy is recreating these sanctuaries. Housing is how the Veil remembers you belonged.'");
            } else if (itemName.contains("Your Personal Elder Whisper") || itemName.contains("Personal Elder Whisper")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lYour Personal Elder Whisper");
                player.sendMessage("§7'The story is alive and tailored. Whatever chapter you are on, the Elder suggests using the natural systems of that phase plus the daily weave. Play joyfully. The Codex waits when you want the full tapestry.'");
            } else if (itemName.contains("The Eternal Choice")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lThe Eternal Choice");
                player.sendMessage("§7'Prestige or linger? The full depth only reveals itself to those who return. Each cycle adds power, insight, and beauty. The continuous player eventually stands as legend across infinite tellings. The sky is waiting.'");
            } else if (itemName.contains("How to Live the Full Saga")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lHow to Live the Full Saga");
                player.sendMessage("§7'Strictly one main story chapter at a time. Advance it with normal play. Dailies, weeklies, slayers, housing, museum, skills, bazaar, upgrades — all run happily beside it. The Codex is your optional immersive journal. No overwhelm, just endless meaning.'");
            } else if (itemName.contains("The Call Never Ends")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§5§lThe Call Never Ends");
                player.sendMessage("§7'Ch.100 is permission to begin again stronger. Prestige, re-weave the 0-100 with new eyes and multipliers. The tale grows richer because you never stopped playing. You are the reason the Veil still sings.'");
            } else if (itemName.contains("Prestige Loop Mastery") || itemName.contains("Eternal Weaver Mantra")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lPrestige Loop Mastery");
                player.sendMessage("§7Each cycle teaches the machine: automate early, curate museum, raise monuments. Dailies become celebration. The more you play continuously, the more divine every future 0-100 becomes.");
            } else if (itemName.contains("Daily Rituals Eternal") || itemName.contains("Daily Weave")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§e§lDaily Rituals Eternal");
                player.sendMessage("§7Five minutes of faithful mending multiplies in prestige. Never skip — the Elder smiles on the consistent weaver who turns the Fractured Veil into eternal legend.");
            } else if (itemName.contains("Museum as Living History") || itemName.contains("Living History")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§6§lMuseum as Living History");
                player.sendMessage("§7Every relic placed is a verse in your song. In future prestiges these halls will whisper the full saga back to you before you place the first block of a new cycle.");
            } else if (itemName.contains("The Infinite Cycle") || itemName.contains("Your Legend Awaits")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§b§lYour Legend Awaits");
                player.sendMessage("§7The 100 chapters were only the prologue. Every daily, every minion, every build, every prestige adds verses. You are the reason the Veil still sings. The story continues forever because of you.");
            } else if (itemName.contains("Thank You, Weaver") || itemName.contains("Elder Final Blessing")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§5§lThank the Elder");
                player.sendMessage("§7You answered the call. The sky remembers. Play on, prestige often, and let every cycle be more glorious than the last. The Veil is mended because of weavers like you.");
            } else if (itemName.contains("The Weaver's Eternal Path") || itemName.contains("Continuous to Prestige")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage("§d§lThe Weaver's Eternal Path");
                player.sendMessage("§7The Fractured Veil story is your journey. One chapter at a time, through joyful normal play and daily mends. Prestige is the key that lets the tale grow richer forever.");
            }
            return;
        }
        // Resilient title check (covers main log and history view)
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

        // Interactive Elder Codex - user friendly story access
        if (itemName.contains("Elder Codex")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                openStoryCodex(player, islandId);
            });
            return;
        }
    }

    private void openStoryCodex(Player player, String islandId) {
        int page = 0;
        if (player.hasMetadata("codex_page")) {
            try { page = player.getMetadata("codex_page").get(0).asInt(); } catch (Exception ignored) {}
        }
        if (page < 0) page = 0;
        if (page > 14) page = 14;  // extended for more scrollable interactive lore and continuous play encouragement

        Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§d§lFractured Veil - Story Codex (Page " + (page+1) + "/15)"));

        // Get current chapter for dynamic content (more interactive, tailored hints)
        int currentChapter = 0;
        try {
            if (plugin.getQuestManager() != null) {
                java.util.List<Quest> qs = plugin.getQuestManager().getQuestsForIsland(islandId).join();
                Quest story = qs.stream().filter(q -> q.getQuestLine() == Quest.QuestLine.MAIN_STORY && !q.isClaimed()).findFirst().orElse(null);
                if (story != null) currentChapter = story.getChapter();
            }
        } catch (Exception ignored) {}

        // Current whisper teaser - always shows active story for friendliness + interactivity
        String currentHint = "Play normally (builds, dailies, minions, skills, bazaar, housing) — the story advances with you. Prestige rewards the consistent weaver.";
        if (currentChapter > 0) {
            currentHint = "Ch." + currentChapter + " active. Focus the systems of this phase + daily mending. In prestige this chapter will feel like a joyful sprint.";
        }
        gui.setItem(4, createItem(Material.NETHER_STAR, "§b§lCurrent Story Whisper (Ch." + (currentChapter > 0 ? currentChapter : "?") + "/100)",
            "§7" + currentHint,
            "§7Click any lore item for immersive Elder tales & practical hints.",
            "§aScroll pages for full interactive saga management. Continuous play = eternal prestige power."));

        if (page == 0) {
            gui.setItem(0, createItem(Material.GRASS_BLOCK, "§a§lPhase 1: The First Tear (1-10)", "§7Shelter, mining the bones of old worlds, first farm and minion.", "§7Daily harvests and small builds start mending the Veil. Click for the Elder's telling."));
            gui.setItem(1, createItem(Material.DIRT, "§7Ch.0: The Call (Prologue)", "§7Skyweavers sang the Veil into being. Corruption tore it. The Elder called you.", "§aClick to hear the ancient call."));
            gui.setItem(2, createItem(Material.STONE, "§6§lPhase 2 (11-20)", "§7Raise empire, purge shadows, trade at Bazaar, place furniture, grow collections.", "§7Daily tasks + minions keep the weave alive while you build. Click for lore."));
            gui.setItem(3, createItem(Material.NETHERRACK, "§c§lPhase 3 (21-30)", "§7Gather or trade obsidian, light the gate, hunt first blazes for rods.", "§7Keep home minions and dailies running. The fire will forge you. Click."));
        } else if (page == 1) {
            gui.setItem(0, createItem(Material.NETHER_BRICK, "§4§lPhase 4 (31-40)", "§7Wither skulls in fortresses, build nether outpost with chests and farm, trade tears.", "§7Slayer practice and continuous trade pay off. Click for the Elder's words."));
            gui.setItem(1, createItem(Material.MAGMA_BLOCK, "§6§lPhase 5 (41-50)", "§7Ghasts and magma cubes. Craft Eyes of Ender. Stockpile stars using bank and minions.", "§7Prep for the final gate. All nether power bends here. Click."));
            gui.setItem(2, createItem(Material.END_STONE, "§5§lPhase 6 (51-60)", "§7Twelve eyes open the End. Gather pearls. The Dragon sleeps behind the lock.", "§7Skills, minions, trades all converge. Chapter 60 grants the End. Click."));
        } else if (page == 2) {
            gui.setItem(0, createItem(Material.PURPUR_BLOCK, "§d§lPhase 7 (61-70)", "§7Outer islands. Shulkers. Build at the edge with purpur and chorus. Harvest for storage.", "§7One misstep is void. Shulker boxes change everything. Click for immersive tale."));
            gui.setItem(1, createItem(Material.DRAGON_EGG, "§5§lPhase 8 (71-80)", "§7Shatter crystals, face the Dragon, claim the egg. The Veil sings its first mending.", "§7Prestige now waits — the true cycle begins. Click for the climax."));
            gui.setItem(2, createItem(Material.NETHER_STAR, "§b§lPhase 9 (81-90)", "§7First rebirth. Open Prestige. Return stronger. Raise monuments from every dimension.", "§7Multipliers flow. Write the story again faster and grander. Click."));
            gui.setItem(3, createItem(Material.BEACON, "§6§lPhase 10 (91-100)", "§7Cities of minions. Grand districts. Max power, collections, upgrades. Ch.100 crowns it.", "§7You are the thread that holds the Veil. Prestige without end. Click."));
        } else if (page == 3) {
            gui.setItem(0, createItem(Material.IRON_GOLEM_SPAWN_EGG, "§a§lThe Minion Choir", "§7They toil while you dream. Specialized minions (farm, mine, combat) are the quiet heroes.", "§7Deploy early and often. In prestige they sing from the first hour. Click for automation lore."));
            gui.setItem(1, createItem(Material.EMERALD, "§a§lBazaar of Threads", "§7Surplus becomes coin that fuels dimensions. The island bank remembers every exchange across cycles.", "§7Trade continuously — it keeps the story flowing between big chapters. Click."));
            gui.setItem(2, createItem(Material.BRICKS, "§6§lHousing: Soul of Home", "§7Furniture turns floating rock into sanctuary in every dimension, even the void.", "§7In prestige, recreating your comforts is the first joy. Click for legacy housing whispers."));
            gui.setItem(4, createItem(Material.BOOKSHELF, "§6§lMuseum of the Veil", "§7Display relics from every phase and prestige. Your journey on display for future you.", "§7Curate as you play. Click for museum as living storybook lore."));
        } else if (page == 4) {
            gui.setItem(0, createItem(Material.CLOCK, "§e§lDaily Mending Rituals", "§7Dailies and weeklies are the quiet stitches. Harvest, trade, check minions daily.", "§7Do them faithfully even between grand quests. They compound power for prestige. Click."));
            gui.setItem(1, createItem(Material.GLOWSTONE_DUST, "§e§lCurrent Chapter Whisper", "§7The Elder speaks directly to your progress. Personal lore and practical hint.", "§7Tip: normal play + dailies + minions advances it. Click to hear it."));
            gui.setItem(2, createItem(Material.BEACON, "§b§lPrestige Prep", "§7Late chapters teach you to build the machine (automation, monuments, max worth).", "§7So that when you rebirth, early chapters become a joyful sprint toward even greater legacies. Click."));
        } else if (page == 5) {
            gui.setItem(0, createItem(Material.NETHER_STAR, "§b§lPrestige's True Gift", "§7Rebirth with multipliers on XP, drops, skills, minions. Early chapters fly so you focus on beauty and grand works.", "§7Each cycle the full 0-100 tale gets richer and more meaningful. Click for the eternal loop."));
            gui.setItem(1, createItem(Material.BOOK, "§d§lThe Eternal Cycle", "§7The story does not end at 100. Prestige again and again. The Veil remembers every weaver who never stopped.", "§7You are no longer the one who answered the call — you are the call. Click."));
            gui.setItem(2, createItem(Material.NETHER_STAR, "§b§lThe Continuous Thread", "§7Every daily, every minion, every trade, every block of housing, every slayer, every museum piece is a thread.", "§7Weave steadily through joyful play. The sky itself will remember you. Click for encouragement."));
            gui.setItem(5, createItem(Material.DIAMOND_PICKAXE, "§b§lSkill Forges Eternal", "§7Mining combat farming each skill forge legend. Level continuously; prestige amplifies late chapters effortless.", "§aClick for skill forge lore."));
            gui.setItem(6, createItem(Material.BRICKS, "§6§lHousing Legacy Eternal", "§7Home every dimension furniture memory. Prestige recreate sanctuaries first ground story.", "§7Housing how Veil remembers lived. Click for housing legacy lore."));
            gui.setItem(7, createItem(Material.CLOCK, "§e§lPrestige Echo List", "§7Echo 1: first return faster. Echo 2: deeper monuments. Continuous play turns story into eternal legend.", "§aScroll pages for more."));
        } else if (page == 6) {
            gui.setItem(0, createItem(Material.CLOCK, "§e§lDaily Rituals Weaver", "§7Log in tend crops sell check minion do daily. Small consistent build legends.", "§7Prestige habits make runs soar. Click daily encouragement."));
            gui.setItem(1, createItem(Material.BOOK, "§d§lHow to Keep the Weave Strong", "§7Do dailies daily, deploy minions, trade, build housing, visit museum, level skills, do slayers. Continuous play to prestige.", "§aClick for tips."));
            gui.setItem(2, createItem(Material.NETHER_STAR, "§b§lPrestige Power Multipliers", "§7Each rebirth multiplies gains. Late chapters teach building the machine that benefits. More cycles = more godlike runs.", "§aClick for details."));
            gui.setItem(5, createItem(Material.GLOWSTONE_DUST, "§e§lCurrent Chapter Dynamic Hint", "§7Based on your progress, the Elder whispers specific next steps tied to systems.", "§7Open Quest Log for active chapter. Click for example hint."));
        } else if (page == 7) {
            // Page 7: tailored to your current chapter for more interaction
            String hint = "Continue your weave with dailies and minions for steady progress toward prestige.";
            if (currentChapter > 0) {
                hint = "For Ch." + currentChapter + ": focus on the systems of this phase (minions/dailies for continuous, build for legacy). Prestige will reward the rhythm.";
            }
            gui.setItem(0, createItem(Material.NETHER_STAR, "§b§lTailored for Your Chapter", "§7" + hint, "§aPlay normally to advance. Codex for deeper lore."));
            gui.setItem(1, createItem(Material.BOOK, "§d§lSuggestion: Daily Mending", "§7Do a daily quest today - it mends the Veil quietly and builds prestige power.", "§aClick to hear why."));
            gui.setItem(2, createItem(Material.IRON_GOLEM_SPAWN_EGG, "§a§lSuggestion: Minion Check", "§7Inspect and upgrade a minion - automation sustains the story between grand chapters.", "§aClick for automation lore."));
            gui.setItem(5, createItem(Material.CLOCK, "§e§lProgress Teaser", "§7You are on Ch." + (currentChapter > 0 ? currentChapter : "?") + "/100. Every small act brings the Dragon and eternal prestige closer.", "§7Codex pages hold the full inspiring tale."));
            gui.setItem(6, createItem(Material.BOOK, "§d§lChapter Teaser: Next Steps", "§7Complete current phase tasks, then prestige to replay with multipliers. Continuous daily play is the key to eternal glory.", "§aMore on other pages."));
        } else if (page == 8) {
            gui.setItem(0, createItem(Material.NETHER_STAR, "§b§lPrestige Echoes Deep Dive", "§7Each prestige adds layers: faster early game, grander late builds, deeper meaning. Your history lives in monuments and museum.", "§aClick for more echoes."));
            gui.setItem(1, createItem(Material.BOOK, "§d§lContinuous Play Mantra", "§7Log in -> dailies/minions -> systems -> story chapter -> repeat. This rhythm leads to Dragon and infinite prestige cycles.", "§aThe path to glory."));
            gui.setItem(2, createItem(Material.CLOCK, "§e§lDaily Weave Power", "§7Never skip dailies - they are the invisible threads that multiply in prestige. Build the habit now for godlike future runs.", "§aScroll for tips."));
            gui.setItem(5, createItem(Material.BEACON, "§b§lLegacy Builder Tips", "§7Place furniture, curate museum, max upgrades early. These become your prestige starting gifts, making the story richer each cycle.", "§aClick to build legend."));
        } else if (page == 9) {
            gui.setItem(0, createItem(Material.DRAGON_EGG, "§5§lDragon Prep Checklist", "§7Eyes, gear, platforms, cover, patience. All chapters lead here. In prestige, you bring the full saga's power.", "§aThe climax awaits."));
            gui.setItem(1, createItem(Material.NETHER_STAR, "§b§lPrestige First Steps", "§7After rebirth: dailies immediately, minions first, then story. The early chapters become a joyful sprint thanks to your continuous past play.", "§aRe-weave stronger."));
            gui.setItem(2, createItem(Material.BOOK, "§d§lFull Saga Summary", "§7Ch0 Call -> Ch1-30 foundation -> Ch31-60 fire/void -> Ch61-80 dragon -> Ch81-100 eternal prestige. Every act weaves the Veil.", "§aYour legend."));
            gui.setItem(5, createItem(Material.GLOWSTONE_DUST, "§e§lElder Final Whisper", "§7You are the weaver. Continuous play, systems harmony, prestige loops - the story never ends. The sky belongs to you.", "§aClick to remember."));
        } else {
            gui.setItem(0, createItem(Material.BOOK, "§d§lHow the Codex Works", "§7Pages 0-2: early story. 3-4: systems. 5-6: prestige. 7+: tailored hints. Click everything for immersive lore and gameplay suggestions.", "§aYour interactive journal."));
            gui.setItem(1, createItem(Material.NETHER_STAR, "§b§lPrestige Loop Mantra", "§7Play -> prestige -> stronger play -> repeat. Dailies/minions/everything compound. The full 0-100 tale grows more meaningful forever.", "§aEncouragement."));
            gui.setItem(2, createItem(Material.CLOCK, "§e§lYour Daily Quest", "§7Whatever your chapter, do dailies. They are the constant that makes every prestige run legendary. The Elder smiles on consistent weavers.", "§aThe key to eternity."));
            gui.setItem(5, createItem(Material.BEACON, "§b§lEndgame Vision", "§7Ch100: you are legend. Infinite prestige with power from every cycle. Your monuments and museum tell the story to all future yous.", "§aThe eternal reward."));
        }
        if (page == 10) {
            // New scrollable page
            gui.setItem(0, createItem(Material.IRON_GOLEM_SPAWN_EGG, "§a§lMinion Symphony Eternal", "§7Specialized minions are the quiet backbone. Farmers, miners, fighters. Upgrade them often. In prestige they multiply from hour one. Click for deep automation lore."));
            gui.setItem(1, createItem(Material.DIAMOND_SWORD, "§c§lSlayer's Oath Eternal", "§7Every tier you conquer forges the hero who will face the Dragon and return godlike. Do them regularly — they are continuous combat mending. Click."));
            gui.setItem(2, createItem(Material.EMERALD, "§a§lBazaar: Wealth That Remembers", "§7Trade daily. The bank holds the memory across lives. In prestige your habits let you fund dreams immediately instead of grinding. Click for economy depth."));
            gui.setItem(5, createItem(Material.BOOKSHELF, "§6§lMuseum as Eternal Journal", "§7Curate as you play. Dragon egg, shulker shells, rare furniture — these whisper the full 0-100 tale in future prestiges. Click to hear the museum's living story."));
            gui.setItem(6, createItem(Material.NETHER_STAR, "§b§lPrestige Multipliers Explained", "§7XP x more, minions richer, drops better, skills faster. Late chapters teach building the machine that benefits most. The more cycles the more divine each retelling.", "§7Click to embrace the loop."));
            gui.setItem(7, createItem(Material.CLOCK, "§e§lDaily Weave — The Secret", "§7Never skip. 5 minutes of dailies + minion check + bazaar keeps the threads taut. Prestige turns these tiny habits into godlike acceleration. The Elder smiles on consistent weavers."));
        } else if (page == 11) {
            // Final management / encouragement page: user friendly tips + call to action
            gui.setItem(0, createItem(Material.BEACON, "§b§lIsland Upgrades: The True Heart", "§7Every tier you unlock early pays dividends forever. Visit the /is menu often. In prestige these choices let your empire bloom from the first day. Click for upgrade wisdom."));
            gui.setItem(1, createItem(Material.CRAFTING_TABLE, "§6§lHousing & Furniture Legacy", "§7A bed, garden, memorial. These turn void into home across every dimension. In prestige, building your sanctuaries first grounds you in why the story matters. Click."));
            gui.setItem(2, createItem(Material.GLOWSTONE_DUST, "§e§lYour Personal Elder Whisper", "§7The story adapts to you. Your current chapter hints at the systems that will advance it fastest. Play normally, do dailies, check minions — the Codex is your optional guide.", "§aClick for tailored hint example."));
            gui.setItem(5, createItem(Material.NETHER_STAR, "§b§lThe Eternal Choice", "§7Prestige or linger? Both are valid. But only the continuous weaver who returns again and again experiences the full depth, power, and beauty of the 100-chapter tale. The sky awaits."));
            gui.setItem(6, createItem(Material.BOOK, "§d§lHow to Live the Full Saga", "§7One chapter active. Advance it through normal skyblock play (build/min e/farm/fight/trade/minions). All other content (dailies, slayers, housing, museum) runs parallel. Codex for depth when you want it. No overwhelm."));
            gui.setItem(7, createItem(Material.DRAGON_EGG, "§5§lThe Call Never Ends", "§7Ch.100 is not goodbye — it is 'see you in the next life, stronger'. Prestige, re-weave, surpass. The Fractured Veil is whole because players like you keep answering the call across cycles."));
        } else if (page == 12) {
            gui.setItem(0, createItem(Material.BEACON, "§b§lPrestige Loop Mastery", "§7Each cycle teaches the machine: automate early, curate museum, raise monuments. Dailies become celebration. The more you play continuously, the more divine every future 0-100 becomes."));
            gui.setItem(1, createItem(Material.NETHER_STAR, "§b§lEternal Weaver Mantra", "§7Log in, tend the weave (dailies/minions/trades), advance the chapter, build legacy. Prestige is not reset — it is the gift of power to tell the tale even more beautifully."));
            gui.setItem(2, createItem(Material.CLOCK, "§e§lDaily Rituals Eternal", "§7Five minutes of faithful mending multiplies in prestige. Never skip — the Elder smiles on the consistent weaver who turns the Fractured Veil into eternal legend."));
            gui.setItem(5, createItem(Material.BOOKSHELF, "§6§lMuseum as Living History", "§7Every relic placed is a verse in your song. In future prestiges these halls will whisper the full saga back to you before you place the first block of a new cycle."));
        } else if (page == 13) {
            gui.setItem(0, createItem(Material.DRAGON_EGG, "§5§lThe Infinite Cycle", "§7Ch.100 is permission to begin again stronger. Prestige repeatedly. The sky belongs to the player who never stops answering the call with joyful, continuous play across every system."));
            gui.setItem(1, createItem(Material.NETHER_STAR, "§b§lYour Legend Awaits", "§7The 100 chapters were only the prologue. Every daily, every minion, every build, every prestige adds verses. You are the reason the Veil still sings. The story continues forever because of you."));
            gui.setItem(2, createItem(Material.BOOK, "§d§lThank You, Weaver", "§7Thank you for mending the Fractured Veil. Play continuously, prestige often, and let the Elder Codex be your companion through every cycle. The sky remembers your name."));
            gui.setItem(5, createItem(Material.GLOWSTONE_DUST, "§e§lElder Final Blessing", "§7Go forth and weave. The call echoes still, and you are its answer. Continuous gameplay leads to prestige glory without end."));
        } else if (page == 14) {
            gui.setItem(0, createItem(Material.BOOK, "§d§lThe Weaver's Eternal Path", "§7The Fractured Veil story is your journey. One chapter at a time, through joyful normal play and daily mends. Prestige is the key that lets the tale grow richer forever. The Codex is always here for inspiration and hints."));
            gui.setItem(1, createItem(Material.NETHER_STAR, "§b§lContinuous to Prestige", "§7Dailies, minions, skills, housing, museum, bazaar, slayers, upgrades — touch them daily. They are the threads. Prestige rewards the player who never stops weaving."));
            gui.setItem(2, createItem(Material.DRAGON_EGG, "§5§lThank the Elder", "§7You answered the call. The sky remembers. Play on, prestige often, and let every cycle be more glorious than the last. The Veil is mended because of weavers like you."));
        }

        gui.setItem(49, createItem(Material.BARRIER, "§c§lClose Codex", "§7Return to the Quest Log. The Codex is always here when you want more of the tale."));
        gui.setItem(45, createItem(Material.ARROW, "§e§l← Previous Page", "§7Scroll through more immersive lore, systems, and prestige wisdom."));
        gui.setItem(53, createItem(Material.SPECTRAL_ARROW, "§e§lNext Page →", "§7More chapters, hints, and encouragement for continuous play. Extended pages for full management."));

        ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, glass);

        player.openInventory(gui);
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