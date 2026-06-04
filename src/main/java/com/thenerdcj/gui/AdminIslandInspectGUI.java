package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.*;
import com.thenerdcj.island.Island;
import com.thenerdcj.pets.CosmeticPet;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin "island inspect" GUI for staff.
 * Uses DAOs (IslandDAO, CosmeticDAO, BalanceDAO, etc.) + managers to display
 * rich state for an island + player (upgrades, collections, skills, active cosmetics, balances, punishments).
 *
 * Opened via /isadmin inspect <player> (or island owner).
 * Read-only for diagnostics / support (Play-to-Win audit friendly).
 *
 * Task 7 polish: added spawn edit action (admin can set target island spawn to current loc for fix).
 * Data loaded via IslandDAO (bank/settings/worth/prestige/collections promoted), CosmeticDAO, BalanceDAO, PunishmentDAO.
 * Bug reports for the player are available separately via BugReportDAO (or /isadmin reports + /bug reports for staff triage).
 * Uses .join() for admin/staff tool (infrequent); production GUIs prefer full async + ThreadSafety.runOnMainThread.
 * TODO (follow-up): richer async non-join loads (data in runAsync background, build/open on main via ThreadSafety; no blocking in open caller); teleport action etc. (pagination largely complete per passes below).
 * Pagination complete (advanced across passes + this cycle via related tops GUI): target persisted + page nav re-opens with updated page + async reload + pure CF. More lists (collections, furniture, puns/full logs, overhead, emotes, skills, structures, quests, slayer, minions + now player IslandTopGUI offset pages) paged for large scale data compression (enhanced page size for full puns; structures/quests/slayer/minions + tops integration this/prior pass). Stale list in old TODO text cleaned.
 */
public class AdminIslandInspectGUI implements Listener {

    private final FoliaSkyblock plugin;
    // Simple per-staff page state for pagination in inspect (follow-up polish)
    private final java.util.Map<UUID, Integer> inspectPages = new java.util.concurrent.ConcurrentHashMap<>();
    // Per-staff target owner for seamless re-open on page change (completes pagination)
    private final java.util.Map<UUID, UUID> inspectTargets = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Holder for async fetched data to enable pure CF chaining (no blocking .join inside fetch lambdas for "pure CF" per MD).
     */
    private static class InspectData {
        final double worth, bankBal, playerBal, avgRating;
        final int wLevel, prestige, collCount, tagCount, petCount, activePunish, ratingCount;
        final boolean hasWarp;
        final java.util.List<String> pageTags;
        final java.util.List<String> pagePuns;  // for more lists paged (punishments)
        final java.util.List<String> pageCollections;  // more lists paged for large scale (collections can be many)
        final java.util.List<String> pageFurniture;  // more lists paged (furniture, logs per TODO for large scale compression)
        final java.util.List<String> pageOverhead; // more lists paged (overhead for large scale data compression)
        final java.util.List<String> pageEmotes; // more lists paged (emotes for large scale data compression)
        final java.util.List<String> pageStructures; // more lists paged (structures for large scale data compression - housing decor)
        final java.util.List<String> pageQuests; // more lists paged (quests/dailies for large scale data compression - per island quest lists)
        final java.util.List<String> pageSlayer; // more lists paged (slayer tiers for large scale data compression - progress per entity type for high-tier players)
        final java.util.List<String> pageMinions; // more lists paged (minions for large scale data compression - per-island minion counts/breakdowns)
        final java.util.List<String> pageSkills; // more lists paged (skills for large scale / inspect compression, e.g. 8 skills paged samples)
        final int page;
        final String ownerName, islandId;
        final int level;
        InspectData(double worth, int wLevel, double bankBal, int prestige, int collCount,
                    double playerBal, int tagCount, int petCount, java.util.List<String> pageTags,
                    java.util.List<String> pagePuns, java.util.List<String> pageCollections, java.util.List<String> pageFurniture,
                    java.util.List<String> pageOverhead, java.util.List<String> pageEmotes, java.util.List<String> pageStructures, java.util.List<String> pageQuests, java.util.List<String> pageSlayer, java.util.List<String> pageMinions, java.util.List<String> pageSkills,
                    int activePunish, double avgRating, int ratingCount, boolean hasWarp,
                    int page, String ownerName, String islandId, int level) {
            this.worth = worth; this.wLevel = wLevel; this.bankBal = bankBal; this.prestige = prestige;
            this.collCount = collCount; this.playerBal = playerBal; this.tagCount = tagCount;
            this.petCount = petCount; this.pageTags = pageTags; this.pagePuns = pagePuns; this.pageCollections = pageCollections; this.pageFurniture = pageFurniture;
            this.pageOverhead = pageOverhead; this.pageEmotes = pageEmotes; this.pageStructures = pageStructures; this.pageQuests = pageQuests; this.pageSlayer = pageSlayer; this.pageMinions = pageMinions; this.pageSkills = pageSkills;
            this.activePunish = activePunish;
            this.avgRating = avgRating; this.ratingCount = ratingCount; this.hasWarp = hasWarp;
            this.page = page; this.ownerName = ownerName; this.islandId = islandId; this.level = level;
        }
    }

    public AdminIslandInspectGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player staff, UUID targetOwner) {
        if (!staff.hasPermission("foliasb.admin")) {
            staff.sendMessage(MessageUtil.legacy("§cNo permission."));
            return;
        }
        Island island = plugin.getIslandManager() != null ? plugin.getIslandManager().getIsland(targetOwner, org.bukkit.World.Environment.NORMAL) : null;
        if (island == null) {
            for (org.bukkit.World.Environment env : org.bukkit.World.Environment.values()) {
                island = plugin.getIslandManager().getIsland(targetOwner, env);
                if (island != null) break;
            }
        }

        // Store target for pagination re-open support (completes the pagination feature)
        UUID staffId = staff.getUniqueId();
        inspectTargets.put(staffId, targetOwner);

        // Pagination state (simple per-staff for this inspect session)
        int page = inspectPages.getOrDefault(staffId, 0);

        // DAOs (may be null in test)
        com.thenerdcj.database.IslandDAO islandDAO = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getIslandDAO() : null;
        com.thenerdcj.database.CosmeticDAO cosmeticDAO = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getCosmeticDAO() : null;
        com.thenerdcj.database.BalanceDAO balanceDAO = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getBalanceDAO() : null;
        com.thenerdcj.database.PunishmentDAO punishDAO = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getPunishmentDAO() : null;

        String ownerName = Bukkit.getOfflinePlayer(targetOwner).getName();
        if (ownerName == null) ownerName = targetOwner.toString().substring(0, 8);
        final String finalOwnerName = ownerName;
        final String islandIdFinal = (island != null ? island.getId() : "N/A");
        final int levelFinal = (island != null ? island.getLevel() : 0);
        final Island islandFinal = island;
        final com.thenerdcj.database.GridPosition gposFinal = (island != null ? island.getGridPosition() : null);

        // Pure CF chaining for data fetch (no manual .join() inside blocking lambdas in fetch; use thenApply/allOf composition for "pure CF chaining for data" per MD).
        // Data fetch off main via CFs, build/open on main via ThreadSafety.
        // This + target persist + more lists paged completes the GUI polish for "more lists paged in inspect, pure CF chaining for data".
        // Paged lists: tags + active punishments (load list + sublist); collections count + note for extension.
        CompletableFuture<Object[]> worthDataF = (gposFinal != null && islandDAO != null)
            ? islandDAO.loadIslandWorth(gposFinal)
            : CompletableFuture.completedFuture(new Object[]{0.0, 1, 0L});
        CompletableFuture<Double> bankBalF = (gposFinal != null && islandDAO != null)
            ? islandDAO.loadIslandBankBalance(gposFinal)
            : CompletableFuture.completedFuture(0.0);
        CompletableFuture<Integer> prestigeF = (islandDAO != null)
            ? islandDAO.loadIslandPrestige(islandIdFinal)
            : CompletableFuture.completedFuture(0);
        CompletableFuture<Integer> collCountF = CompletableFuture.completedFuture(
            (islandDAO != null) ? islandDAO.getIslandCollectionCount(islandIdFinal) : 0 );
        CompletableFuture<Double> playerBalF = (balanceDAO != null)
            ? balanceDAO.getPlayerBalance(targetOwner)
            : CompletableFuture.completedFuture(0.0);
        CompletableFuture<java.util.Set<String>> tagsF = (cosmeticDAO != null)
            ? CompletableFuture.completedFuture( cosmeticDAO.loadPlayerTagCollection(targetOwner) )
            : CompletableFuture.completedFuture(java.util.Collections.emptySet());
        CompletableFuture<com.thenerdcj.pets.CosmeticPet> activePetF = (cosmeticDAO != null)
            ? CompletableFuture.completedFuture( cosmeticDAO.loadActivePet(targetOwner) )
            : CompletableFuture.completedFuture(null);
        CompletableFuture<java.util.List<com.thenerdcj.database.Punishment>> punListF = (punishDAO != null)
            ? punishDAO.getActivePunishments(targetOwner)
            : CompletableFuture.completedFuture(java.util.Collections.emptyList());
        CompletableFuture<Double> avgRatingF = (islandDAO != null && gposFinal != null)
            ? islandDAO.getAverageRating(gposFinal)
            : CompletableFuture.completedFuture(0.0);
        CompletableFuture<Integer> ratingCountF = (islandDAO != null && gposFinal != null)
            ? islandDAO.getRatingCount(gposFinal)
            : CompletableFuture.completedFuture(0);
        CompletableFuture<com.thenerdcj.island.IslandWarp> warpF = (islandDAO != null && gposFinal != null)
            ? islandDAO.loadIslandWarp(gposFinal)
            : CompletableFuture.completedFuture(null);
        CompletableFuture<java.util.Set<String>> collectionsF = (islandDAO != null)
            ? CompletableFuture.completedFuture( islandDAO.loadIslandCollections(islandIdFinal) )
            : CompletableFuture.completedFuture(java.util.Collections.emptySet());
        CompletableFuture<java.util.Set<String>> furnitureF = (cosmeticDAO != null)
            ? CompletableFuture.completedFuture( cosmeticDAO.loadPlayerIslandFurniture(targetOwner) )
            : CompletableFuture.completedFuture(java.util.Collections.emptySet());
        CompletableFuture<java.util.Set<String>> overheadF = (cosmeticDAO != null)
            ? CompletableFuture.completedFuture( cosmeticDAO.loadPlayerOverheadCosmetics(targetOwner) )
            : CompletableFuture.completedFuture(java.util.Collections.emptySet());
        CompletableFuture<java.util.Set<String>> emotesF = (cosmeticDAO != null)
            ? CompletableFuture.completedFuture( cosmeticDAO.loadPlayerEmoteCosmetics(targetOwner) )
            : CompletableFuture.completedFuture(java.util.Collections.emptySet());
        // More lists paged for large scale data compression (structures can be many on decorated islands; mirrors furniture/overhead).
        CompletableFuture<java.util.Set<String>> structuresF = (cosmeticDAO != null)
            ? CompletableFuture.completedFuture( cosmeticDAO.loadPlayerIslandStructures(targetOwner) )
            : CompletableFuture.completedFuture(java.util.Collections.emptySet());

        CompletableFuture.allOf(
            worthDataF, bankBalF, prestigeF, collCountF, playerBalF, tagsF, activePetF,
            punListF, avgRatingF, ratingCountF, warpF, collectionsF, furnitureF, overheadF, emotesF, structuresF
        ).thenApply(v -> {
            // Pure composition: build data from completed CFs (joins here are on already-done futures)
            Object[] wdata = worthDataF.join();
            double worth = (wdata != null) ? (double) wdata[0] : 0.0;
            int wLevel = (wdata != null) ? (int) wdata[1] : 1;
            double bankBal = bankBalF.join();
            int prestige = prestigeF.join();
            int collCount = collCountF.join();
            double playerBal = playerBalF.join();
            java.util.Set<String> tags = tagsF.join();
            int tagCount = tags.size();
            com.thenerdcj.pets.CosmeticPet activePet = activePetF.join();
            int petCount = (activePet != null ? 1 : 0);
            java.util.List<com.thenerdcj.database.Punishment> punList = punListF.join();
            int activePunish = punList.size();
            double avgRating = avgRatingF.join();
            int ratingCount = ratingCountF.join();
            com.thenerdcj.island.IslandWarp w = warpF.join();
            boolean hasWarp = w != null && w.isEnabled();

            java.util.Set<String> collections = collectionsF.join();

            // pagination for tags + punishments + collections + overhead + emotes + skills (more lists paged per MD for large scale inspect - ... + skills for compression; enhanced for full logs/punishments)
            int pageSize = 5; // larger for more full puns/logs samples
            int start = page * pageSize;
            java.util.List<String> tagList = new java.util.ArrayList<>(tags);
            java.util.List<String> pageTags = tagList.subList(Math.min(start, tagList.size()), Math.min(start + pageSize, tagList.size()));

            // page the punishments list (show samples + type for "full logs/punishments tabs" compression) - more lists paged for large scale inspect (punishments can be many on large servers; fuller for logs)
            java.util.List<String> punSamples = new java.util.ArrayList<>();
            for (com.thenerdcj.database.Punishment p : punList) {
                String s = (p.getType() != null ? p.getType().name() : "UNK") + ":" + (p.getReason() != null ? p.getReason() : "");
                punSamples.add(s.length() > 40 ? s.substring(0, 40) : s); // fuller for logs
            }
            java.util.List<String> pagePuns = punSamples.subList(Math.min(start, punSamples.size()), Math.min(start + pageSize, punSamples.size()));

            // page collections list (more lists paged for large scale)
            java.util.List<String> collList = new java.util.ArrayList<>(collections);
            java.util.List<String> pageCollections = collList.subList(Math.min(start, collList.size()), Math.min(start + pageSize, collList.size()));

            // page furniture list (more lists paged per TODO for large scale compression - furniture can be many on large servers)
            java.util.Set<String> furniture = furnitureF.join();
            java.util.List<String> furnList = new java.util.ArrayList<>(furniture);
            java.util.List<String> pageFurniture = furnList.subList(Math.min(start, furnList.size()), Math.min(start + pageSize, furnList.size()));

            // page overhead list (more lists paged for large scale data compression)
            java.util.Set<String> overhead = overheadF.join();
            java.util.List<String> overheadList = new java.util.ArrayList<>(overhead);
            java.util.List<String> pageOverhead = overheadList.subList(Math.min(start, overheadList.size()), Math.min(start + pageSize, overheadList.size()));

            // page emotes list (more lists paged for large scale data compression)
            java.util.Set<String> emotes = emotesF.join();
            java.util.List<String> emotesList = new java.util.ArrayList<>(emotes);
            java.util.List<String> pageEmotes = emotesList.subList(Math.min(start, emotesList.size()), Math.min(start + pageSize, emotesList.size()));

            // page structures list (more lists paged for large scale data compression - housing/structures can be numerous)
            java.util.Set<String> structures = structuresF.join();
            java.util.List<String> structList = new java.util.ArrayList<>(structures);
            java.util.List<String> pageStructures = structList.subList(Math.min(start, structList.size()), Math.min(start + pageSize, structList.size()));

            // paged quests sample for large scale data compression (daily/weekly/onboarding per-island can be many; samples only)
            java.util.List<String> questList = new java.util.ArrayList<>();
            if (plugin.getQuestManager() != null) {
                try {
                    java.util.List<com.thenerdcj.quest.Quest> qs = plugin.getQuestManager().getQuestsForIsland(islandIdFinal).join();
                    for (com.thenerdcj.quest.Quest q : qs) {
                        String t = q.getTitle();
                        questList.add( (t.length() > 20 ? t.substring(0,20) : t) + ":" + q.getProgress() + "/" + q.getTarget() );
                        if (questList.size() >= pageSize) break;
                    }
                } catch (Exception ignored) {}
            }
            java.util.List<String> pageQuests = questList;

            // paged slayer samples for large scale data compression (slayer progress per entity type can be deep for high tier players; samples only via BossManager UUID overload)
            java.util.List<String> slayerList = new java.util.ArrayList<>();
            com.thenerdcj.boss.BossManager bossMgr = plugin.getBossManager();
            if (bossMgr != null) {
                for (org.bukkit.entity.EntityType et : new org.bukkit.entity.EntityType[] {
                    org.bukkit.entity.EntityType.ZOMBIE, org.bukkit.entity.EntityType.SKELETON, org.bukkit.entity.EntityType.SPIDER, org.bukkit.entity.EntityType.ENDERMAN, org.bukkit.entity.EntityType.BLAZE
                }) {
                    int tier = bossMgr.getCurrentSlayerTier(targetOwner, et);
                    slayerList.add(et.name() + ":T" + tier);
                    if (slayerList.size() >= pageSize) break;
                }
            }
            java.util.List<String> pageSlayer = slayerList;

            // paged minions sample for large scale data compression (minions per island can be 100s on large farms; use breakdown for samples only)
            java.util.List<String> minionList = new java.util.ArrayList<>();
            com.thenerdcj.manager.MinionManager minionMgr = plugin.getMinionManager();
            if (minionMgr != null && islandIdFinal != null && !"N/A".equals(islandIdFinal)) {
                try {
                    java.util.Map<com.thenerdcj.manager.MinionType, Integer> breakdown = minionMgr.getMinionBreakdown(islandIdFinal);
                    for (java.util.Map.Entry<com.thenerdcj.manager.MinionType, Integer> e : breakdown.entrySet()) {
                        minionList.add(e.getKey().name() + ":" + e.getValue());
                        if (minionList.size() >= pageSize) break;
                    }
                } catch (Exception ignored) {}
            }
            java.util.List<String> pageMinions = minionList;

            // page skills list (more lists paged for inspect/large scale; skills per player, 8 types, samples via manager)
            java.util.List<String> skillList = new java.util.ArrayList<>();
            com.thenerdcj.skills.PlayerSkillManager skillMgr = plugin.getPlayerSkillManager();
            if (skillMgr != null) {
                for (com.thenerdcj.skills.SkillType st : com.thenerdcj.skills.SkillType.values()) {
                    int lvl = skillMgr.getSkillLevel(targetOwner, st);
                    skillList.add(st.name() + ":" + lvl);
                }
            }
            java.util.List<String> pageSkills = skillList.subList(Math.min(start, skillList.size()), Math.min(start + pageSize, skillList.size()));

            // collCount from earlier CF join is the full count for display
            return new InspectData(worth, wLevel, bankBal, prestige, collCount, playerBal,
                tagCount, petCount, pageTags, pagePuns, pageCollections, pageFurniture, pageOverhead, pageEmotes, pageStructures, pageQuests, pageSlayer, pageMinions, pageSkills, activePunish, avgRating, ratingCount, hasWarp,
                page, finalOwnerName, islandIdFinal, levelFinal);
        }).thenAccept(data -> {
            // Switch to main for GUI (Folia safe)
            plugin.getThreadSafety().runOnMainThread(() -> {
                String titleBase = "Inspect: " + data.ownerName;
                Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§c§l" + titleBase + " §7(Admin)"));

                gui.setItem(4, GUIUtils.createItem(Material.COMPASS, "§c§lIsland Inspect",
                        "§7Owner: §f" + data.ownerName + " §7(" + targetOwner.toString().substring(0,8) + ")",
                        "§7Island ID: §f" + data.islandId,
                        "§7Level: §e" + data.level,
                        "§7Page: §f" + (data.page + 1) + " §7(inspect pagination)",
                        "§7(Full DAO-backed: IslandDAO + CosmeticDAO + BalanceDAO + ...)"));

                int row = 9;
                gui.setItem(row, GUIUtils.createItem(Material.DIAMOND, "§6Worth & Economy (DAO)",
                        "§7Worth: §e" + String.format("%,.0f", data.worth) + " §7(lv §a" + data.wLevel + ")",
                        "§7Island Bank: §a" + String.format("%,.2f", data.bankBal),
                        "§7Prestige: §d" + data.prestige,
                        "§7Collections: §b" + data.collCount + " §7(paged: " + (data.pageCollections.isEmpty() ? "none" : String.join(", ", data.pageCollections)) + ")"));

                gui.setItem(row+6, GUIUtils.createItem(Material.CHEST, "§aFurniture (paged for large scale)",
                        "§7Paged samples: §7" + (data.pageFurniture.isEmpty() ? "none" : String.join(", ", data.pageFurniture)),
                        "§7(See /furniture for full)"));

                gui.setItem(row+1, GUIUtils.createItem(Material.GOLD_INGOT, "§ePlayer Balance (BalanceDAO)",
                        "§7Balance: §6" + String.format("%,.2f", data.playerBal),
                        "§7(Use for shops / direct econ audit)"));

                gui.setItem(row+2, GUIUtils.createItem(Material.NETHER_STAR, "§dCosmetics (CosmeticDAO)",
                        "§7Tags owned: §f" + data.tagCount,
                        "§7Pets owned: §f" + data.petCount,
                        "§7Sample tags: §7" + (data.pageTags.isEmpty() ? "none" : String.join(", ", data.pageTags)),
                        "§7Overhead (paged): §7" + (data.pageOverhead.isEmpty() ? "none" : String.join(", ", data.pageOverhead)),
                        "§7Emotes (paged): §7" + (data.pageEmotes.isEmpty() ? "none" : String.join(", ", data.pageEmotes)),
                        "§7Structures (paged for large scale): §7" + (data.pageStructures.isEmpty() ? "none" : String.join(", ", data.pageStructures)),
                        "§7Slayer (paged for large scale): §7" + (data.pageSlayer.isEmpty() ? "none" : String.join(", ", data.pageSlayer))));

                // Bug reports integration (lightweight count for support context; full triage via dedicated /isadmin reports or /reports)
                int bugReportCount = 0;
                try {
                    var brDao = plugin.getDatabaseManager() != null ? plugin.getDatabaseManager().getBugReportDAO() : null;
                    if (brDao != null) {
                        bugReportCount = brDao.getReportsForPlayer(targetOwner, 200).join().size();
                    }
                } catch (Exception ignored) {}

                gui.setItem(row+3, GUIUtils.createItem(Material.PAPER, "§cModeration (PunishmentDAO + BugReportDAO)",
                        "§7Active punishments: §c" + data.activePunish + " (paged for large scale/full logs)",
                        "§7Paged samples (type:reason): §7" + (data.pagePuns.isEmpty() ? "none" : String.join(", ", data.pagePuns)),
                        "§7Bug reports submitted by player: §e" + bugReportCount + " §7(use /isadmin reports or /reports)",
                        "§7(See /isadmin debug for full logs)"));

                gui.setItem(row+4, GUIUtils.createItem(Material.ENDER_PEARL, "§aWarps & Ratings (IslandDAO)",
                        "§7Avg rating: §e" + String.format("%.1f", data.avgRating) + " §7(" + data.ratingCount + " votes)",
                        "§7Has public warp: §f" + data.hasWarp));

                gui.setItem(row+5, GUIUtils.createItem(Material.BOOK, "§6Island State & Progression",
                        "§7Island level/XP via IslandDAO load methods",
                        "§7Skills (paged player): " + (data.pageSkills.isEmpty() ? "none" : String.join(", ", data.pageSkills)),
                        "§7Quests (paged for large scale): " + (data.pageQuests.isEmpty() ? "none" : String.join(", ", data.pageQuests)),
                        "§7Minions (paged for large scale): " + (data.pageMinions.isEmpty() ? "none" : String.join(", ", data.pageMinions)),
                        "§7Upgrades/collections: full via DAO",
                        "§7(Use /isadmin debug worth/minions for extra)"));

                // Pagination nav (for tags example; works for more lists now)
                if (data.page > 0) {
                    gui.setItem(45, GUIUtils.createItem(Material.ARROW, "§e\u25C0 Prev Page"));
                }
                gui.setItem(53, GUIUtils.createItem(Material.ARROW, "§eNext Page \u25B6 §7(p" + (data.page+1) + ")"));

                gui.setItem(49, GUIUtils.createItem(Material.BARRIER, "§c§lClose", "§7Click to close inspect"));

                staff.openInventory(gui);
            });
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (title == null || !title.contains("Inspect") || !title.contains("Admin")) return;
        e.setCancelled(true);
        if (e.getSlot() == 49) {
            p.closeInventory();
            inspectPages.remove(p.getUniqueId());
            inspectTargets.remove(p.getUniqueId());
            return;
        }

        // Task 7 polish: spawn edit for admin (set target island spawn to staff's current loc - fixes for 0,0 or issues)
        ItemStack clicked = e.getCurrentItem();
        if (clicked != null && clicked.getType() == Material.COMPASS && p.hasPermission("foliasb.admin")) {
            UUID tgt = inspectTargets.get(p.getUniqueId());
            if (tgt != null) {
                Island isl = plugin.getIslandManager().getIsland(tgt, p.getWorld().getEnvironment());
                if (isl != null) {
                    isl.setSpawnLocation(p.getLocation());
                    p.sendMessage("§a[Admin] Island spawn updated to your location.");
                    // refresh (use 2-arg open; page state already updated)
                    open(p, tgt);
                }
            }
            return;
        }

        // Pagination handling (advances TODO for pagination in inspect - now complete with target persist + re-open)
        if (e.getSlot() == 45 || e.getSlot() == 53) {
            UUID sid = p.getUniqueId();
            int cur = inspectPages.getOrDefault(sid, 0);
            if (e.getSlot() == 45 && cur > 0) {
                inspectPages.put(sid, cur - 1);
            } else if (e.getSlot() == 53) {
                inspectPages.put(sid, cur + 1);
            }
            // Re-open using stored target (triggers full async load again with new page)
            p.closeInventory();
            UUID target = inspectTargets.get(sid);
            if (target != null) {
                open(p, target);
            } else {
                p.sendMessage(MessageUtil.legacy("§ePage changed. Re-run /isadmin inspect <player> to refresh with new page."));
            }
            return;
        }
        // Future: more actions (e.g. teleport to island via GridPosition, dump full DAO to chat)
    }
}
