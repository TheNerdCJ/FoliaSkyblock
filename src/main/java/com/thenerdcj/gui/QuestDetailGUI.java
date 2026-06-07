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

/**
 * Rich per-quest detail view.
 * - Lists every objective with its own progress bar + exact count (core user-friendly upgrade)
 * - Shows full rewards breakdown, "Why this quest" (adaptive + streak aware)
 * - Big claim / back buttons
 * - Reroll still available via shift or button for d/w
 *
 * Opened from QuestLogGUI on normal click for a quest (more complex = more info, less accidental claims).
 * Follows existing GUI patterns (GUIUtils, PDC for id, resilient title, MessageUtil.legacy).
 */
public class QuestDetailGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey questIdKey;
    private final NamespacedKey actionKey; // for claim/back/reroll buttons

    public QuestDetailGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public QuestDetailGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.questIdKey = new NamespacedKey(plugin, "quest_id");
        this.actionKey = new NamespacedKey(plugin, "quest_action");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, String ownerKey, String questId) {
        if (plugin.getQuestManager() == null) {
            player.sendMessage("§cQuest system not ready.");
            return;
        }

        // Ensure generated
        String playerKey = player.getUniqueId().toString();
        plugin.getQuestManager().generateOnboardingQuests(playerKey);
        plugin.getQuestManager().generateDailyQuests(ownerKey);
        plugin.getQuestManager().generateWeeklyQuests(ownerKey);

        // Find the quest (try both buckets)
        Quest quest = findQuest(ownerKey, questId);
        if (quest == null) {
            quest = findQuest(playerKey, questId);
        }
        if (quest == null) {
            player.sendMessage("§cQuest not found (may have expired or been rerolled).");
            // fallback to log
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                if (plugin.getQuestLogGUI() != null) plugin.getQuestLogGUI().open(player, ownerKey);
            });
            return;
        }

        final Quest q = quest;
        final String ok = ownerKey;

        plugin.getThreadSafety().runOnMainThread(() -> {
            Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§6§lQuest Details"));

            // Header
            gui.setItem(4, GUIUtils.createItem(getQuestMaterial(q.getCategory()), "§e§l" + q.getTitle(),
                    "§7" + q.getDescription(),
                    "§7Type: §f" + q.getType() + "   §7Category: §f" + q.getCategory(),
                    plugin.getQuestManager().getStreakInfo(ok)));

            // Objectives section (the complex/user friendly heart)
            int slot = 10;
            List<Quest.QuestObjective> objs = q.getObjectives();
            if (objs.isEmpty()) {
                // legacy single
                String bar = createProgressBar(q.getProgress(), q.getTarget());
                gui.setItem(slot, GUIUtils.createItem(Material.PAPER, "§fObjective",
                        "§7" + q.getDescription(),
                        "§7Progress: " + bar + " §f" + q.getProgress() + "/" + q.getTarget()));
                slot += 2;
            } else {
                for (Quest.QuestObjective obj : objs) {
                    if (slot > 34) break;
                    String bar = obj.getProgressBar();
                    String status = obj.isCompleted() ? "§aDONE" : "§e" + obj.getProgress() + "/" + obj.getTarget();
                    gui.setItem(slot, GUIUtils.createItem(Material.PAPER, "§f" + obj.getDescription(),
                            "§7Progress: " + bar + " §f" + status));
                    slot += (slot % 9 == 7 ? 3 : 2);
                }
            }

            // Rewards block (Step 3: base + extra typed rewards)
            List<String> rew = new ArrayList<>();
            rew.add("§7Base: §a" + q.getRewardXp() + " Island XP  §e+$" + q.getRewardMoney() + " to bank");
            if (q.getType() == Quest.QuestType.WEEKLY) rew.add("§7+ §650 Island XP milestone");
            if (q.getType() == Quest.QuestType.FIRST) rew.add("§7+ Starter cosmetic + personal coins");
            for (Quest.QuestReward r : q.getExtraRewards()) {
                rew.add("§7+ " + r.getDescription());
            }
            if (q.hasMultipleObjectives()) rew.add("§7Bonus for completing all sub-goals");
            gui.setItem(38, GUIUtils.createItem(Material.EMERALD, "§a§lRewards", rew.toArray(new String[0])));

            // Why this quest (adaptive + friendly) + chains info (Step 1)
            List<String> why = new ArrayList<>();
            why.add("§7This quest was chosen using your island's recent activity,");
            why.add("§7category balance, and progression needs (smart random).");
            if (q.getType() == Quest.QuestType.DAILY || q.getType() == Quest.QuestType.WEEKLY) {
                why.add("§7Helps continuous island leveling.");
                String streak = plugin.getQuestManager().getStreakInfo(ok);
                if (!streak.isEmpty()) why.add("§7" + streak);
            } else if (q.getQuestLine() == Quest.QuestLine.MAIN_STORY) {
                why.add("§b" + q.getQuestLineDisplay() + " §7- Chapter " + q.getChapter());
                why.add("§7Follow the story to unlock new features and rewards.");
            } else {
                why.add("§7Foundational onboarding / story - guided progression.");
            }
            List<String> prereqs = q.getPrerequisites();
            if (!prereqs.isEmpty()) {
                why.add("§cRequires prior story steps to be completed.");
            }
            gui.setItem(40, GUIUtils.createItem(Material.BOOK, "§6§lWhy this quest?", why.toArray(new String[0])));

            // Action buttons
            boolean canClaim = q.isCompleted() && !q.isClaimed() && !q.isExpired();
            Material claimMat = canClaim ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
            String claimName = canClaim ? "§a§lCLAIM REWARD" : (q.isClaimed() ? "§7Already claimed" : "§7Complete objectives to claim");
            ItemStack claim = GUIUtils.createItem(claimMat, claimName, "§7Click to claim if ready");
            ItemMeta cm = claim.getItemMeta();
            if (cm != null) {
                cm.getPersistentDataContainer().set(questIdKey, PersistentDataType.STRING, q.getId());
                cm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "claim");
                claim.setItemMeta(cm);
            }
            gui.setItem(45, claim);

            if (q.getType() == Quest.QuestType.DAILY || q.getType() == Quest.QuestType.WEEKLY) {
                ItemStack rer = GUIUtils.createItem(Material.CLOCK, "§e§lReroll (daily limit)",
                        "§7Shift or click: small cost, 24h cooldown per island");
                ItemMeta rm = rer.getItemMeta();
                if (rm != null) {
                    rm.getPersistentDataContainer().set(questIdKey, PersistentDataType.STRING, q.getId());
                    rm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "reroll");
                    rer.setItemMeta(rm);
                }
                gui.setItem(47, rer);
            }

            ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Quest Log");
            ItemMeta bm = back.getItemMeta();
            if (bm != null) {
                bm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back");
                back.setItemMeta(bm);
            }
            gui.setItem(49, back);

            // filler
            ItemStack glass = GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < 54; i++) if (gui.getItem(i) == null) gui.setItem(i, glass);

            player.openInventory(gui);
        });
    }

    private Quest findQuest(String key, String questId) {
        try {
            List<Quest> qs = plugin.getQuestManager().getQuestsForIsland(key).join();
            for (Quest q : qs) if (q.getId().equals(questId)) return q;
        } catch (Exception ignored) {}
        return null;
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

    private String createProgressBar(int current, int max) {
        int bars = 10;
        int filled = (int) ((double) current / max * bars);
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < bars; i++) sb.append(i < filled ? "█" : "░");
        return sb.toString();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§6§lQuest Details")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        if (!player.hasMetadata("quest_island_id")) return;
        String ownerKey = player.getMetadata("quest_island_id").get(0).asString();
        String playerKey = player.getUniqueId().toString();

        String questId = clicked.getItemMeta().getPersistentDataContainer()
                .get(questIdKey, PersistentDataType.STRING);
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);

        if ("back".equals(action)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                if (plugin.getQuestLogGUI() != null) plugin.getQuestLogGUI().open(player, ownerKey);
            });
            return;
        }

        if (questId == null) return;

        if ("claim".equals(action)) {
            boolean success = plugin.getQuestManager().claimQuest(ownerKey, questId, player);
            if (!success) success = plugin.getQuestManager().claimQuest(playerKey, questId, player);
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            // refresh detail or fall back to log
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                if (plugin.getQuestLogGUI() != null) plugin.getQuestLogGUI().open(player, ownerKey);
            });
            return;
        }

        if ("reroll".equals(action)) {
            boolean ok = plugin.getQuestManager().rerollDailyWeeklyQuest(ownerKey, questId, player);
            plugin.getThreadSafety().runOnMainThread(() -> {
                player.closeInventory();
                if (ok && plugin.getQuestLogGUI() != null) {
                    plugin.getQuestLogGUI().open(player, ownerKey);
                } else if (plugin.getQuestLogGUI() != null) {
                    plugin.getQuestLogGUI().open(player, ownerKey);
                }
            });
        }
    }
}
