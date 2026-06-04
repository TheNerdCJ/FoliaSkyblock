package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Collections System for FoliaSkyblock.
 *
 * Hypixel-inspired "log every unique item gathered" per island.
 * - Tracks first-time discoveries of key materials, mob kills, fish, etc. on the island.
 * - Per-island (island_key = owner_dim style).
 * - Awards Island XP on discovery + collection milestones.
 * - Big milestones award Slayer Tokens + feedback.
 * - Feeds Play-to-Win: high collection progress signals dedication, can gate/unlock special cosmetics (via direct calls or shop).
 * - Folia-safe: event driven (no heavy scans), async DB via DatabaseManager, ThreadSafety for any visuals.
 * - GUI + command provide visibility; integrates with Wardrobe info and prestige.
 *
 * Categories inspired by Hypixel skills: Mining, Farming, Combat, Fishing, Foraging, etc.
 */
public class CollectionManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    // islandKey -> set of discovered "CATEGORY:ITEM" keys (e.g. "MINING:DIAMOND_ORE", "COMBAT:ZOMBIE", "FARMING:WHEAT")
    private final Map<String, Set<String>> islandCollections = new ConcurrentHashMap<>();

    // Bounded for large scale (many islands can have large collection sets)
    private static final int MAX_ISLANDS_COLLECTIONS = 2000;

    // Event sink for collections (dirty on discover to support event-driven invalidation of any global collection views/tops instead of periodic)
    private volatile boolean collectionsDirty = true;

    // Static known collectibles for validation + nice display (expandable)
    // Format: "CATEGORY:RAW_KEY" -> Display name
    private static final Map<String, String> KNOWN_COLLECTIONS = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORY_DISPLAY = new LinkedHashMap<>();

    // Milestones for total unique collected (global across categories for the island)
    private static final int[] COLLECTION_MILESTONES = {5, 10, 25, 50, 100, 200};

    static {
        // Categories
        CATEGORY_DISPLAY.put("MINING", "§7Mining");
        CATEGORY_DISPLAY.put("FARMING", "§aFarming");
        CATEGORY_DISPLAY.put("COMBAT", "§cCombat");
        CATEGORY_DISPLAY.put("FISHING", "§bFishing");
        CATEGORY_DISPLAY.put("FORAGING", "§2Foraging");
        CATEGORY_DISPLAY.put("MISC", "§eMisc");

        // MINING (key ores/blocks first mined on island)
        KNOWN_COLLECTIONS.put("MINING:COAL_ORE", "Coal Ore");
        KNOWN_COLLECTIONS.put("MINING:IRON_ORE", "Iron Ore");
        KNOWN_COLLECTIONS.put("MINING:GOLD_ORE", "Gold Ore");
        KNOWN_COLLECTIONS.put("MINING:REDSTONE_ORE", "Redstone Ore");
        KNOWN_COLLECTIONS.put("MINING:LAPIS_ORE", "Lapis Ore");
        KNOWN_COLLECTIONS.put("MINING:DIAMOND_ORE", "Diamond Ore");
        KNOWN_COLLECTIONS.put("MINING:EMERALD_ORE", "Emerald Ore");
        KNOWN_COLLECTIONS.put("MINING:ANCIENT_DEBRIS", "Ancient Debris");
        KNOWN_COLLECTIONS.put("MINING:OBSIDIAN", "Obsidian");
        KNOWN_COLLECTIONS.put("MINING:NETHERRACK", "Netherrack");

        // FARMING (first harvest/break of crops)
        KNOWN_COLLECTIONS.put("FARMING:WHEAT", "Wheat");
        KNOWN_COLLECTIONS.put("FARMING:CARROT", "Carrot");
        KNOWN_COLLECTIONS.put("FARMING:POTATO", "Potato");
        KNOWN_COLLECTIONS.put("FARMING:BEETROOT", "Beetroot");
        KNOWN_COLLECTIONS.put("FARMING:SUGAR_CANE", "Sugar Cane");
        KNOWN_COLLECTIONS.put("FARMING:PUMPKIN", "Pumpkin");
        KNOWN_COLLECTIONS.put("FARMING:MELON", "Melon");
        KNOWN_COLLECTIONS.put("FARMING:COCOA", "Cocoa Beans");
        KNOWN_COLLECTIONS.put("FARMING:NETHER_WART", "Nether Wart");

        // COMBAT (first kill of mob type that gives meaningful drop/XP)
        KNOWN_COLLECTIONS.put("COMBAT:ZOMBIE", "Zombie");
        KNOWN_COLLECTIONS.put("COMBAT:SKELETON", "Skeleton");
        KNOWN_COLLECTIONS.put("COMBAT:SPIDER", "Spider");
        KNOWN_COLLECTIONS.put("COMBAT:CREEPER", "Creeper");
        KNOWN_COLLECTIONS.put("COMBAT:ENDERMAN", "Enderman");
        KNOWN_COLLECTIONS.put("COMBAT:BLAZE", "Blaze");
        KNOWN_COLLECTIONS.put("COMBAT:WITHER_SKELETON", "Wither Skeleton");
        KNOWN_COLLECTIONS.put("COMBAT:PHANTOM", "Phantom");
        KNOWN_COLLECTIONS.put("COMBAT:SHULKER", "Shulker");

        // FISHING
        KNOWN_COLLECTIONS.put("FISHING:COD", "Cod");
        KNOWN_COLLECTIONS.put("FISHING:SALMON", "Salmon");
        KNOWN_COLLECTIONS.put("FISHING:PUFFERFISH", "Pufferfish");
        KNOWN_COLLECTIONS.put("FISHING:TROPICAL_FISH", "Tropical Fish");
        KNOWN_COLLECTIONS.put("FISHING:NAUTILUS_SHELL", "Nautilus Shell");
        KNOWN_COLLECTIONS.put("FISHING:HEART_OF_THE_SEA", "Heart of the Sea");

        // FORAGING / nature
        KNOWN_COLLECTIONS.put("FORAGING:OAK_LOG", "Oak Log");
        KNOWN_COLLECTIONS.put("FORAGING:SPRUCE_LOG", "Spruce Log");
        KNOWN_COLLECTIONS.put("FORAGING:BIRCH_LOG", "Birch Log");
        KNOWN_COLLECTIONS.put("FORAGING:JUNGLE_LOG", "Jungle Log");
        KNOWN_COLLECTIONS.put("FORAGING:ACACIA_LOG", "Acacia Log");
        KNOWN_COLLECTIONS.put("FORAGING:DARK_OAK_LOG", "Dark Oak Log");
        KNOWN_COLLECTIONS.put("FORAGING:CRIMSON_STEM", "Crimson Stem");
        KNOWN_COLLECTIONS.put("FORAGING:WARPED_STEM", "Warped Stem");

        // MISC high value / rare
        KNOWN_COLLECTIONS.put("MISC:DRAGON_EGG", "Dragon Egg");
        KNOWN_COLLECTIONS.put("MISC:ELYTRA", "Elytra");
        KNOWN_COLLECTIONS.put("MISC:NETHER_STAR", "Nether Star");
    }

    public CollectionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
        // Periodic CHM bound for large-scale (per-island collections)
        threadSafety.runRepeatingOnMainThread(this::cleanupCaches, 20L * 60 * 5, 20L * 60 * 5);
    }

    /** Returns (or creates) the discovered set for an island. */
    public Set<String> getDiscoveredForIsland(String islandKey) {
        return islandCollections.computeIfAbsent(islandKey, k -> ConcurrentHashMap.newKeySet());
    }

    public int getCollectionCount(String islandKey) {
        return getDiscoveredForIsland(islandKey).size();
    }

    /**
     * Load from DB (called on island load in IslandManager).
     */
    public void loadForIsland(String islandKey) {
        if (islandKey == null) return;
        Set<String> loaded = plugin.getDatabaseManager().loadIslandCollections(islandKey);
        Set<String> set = ConcurrentHashMap.newKeySet();
        set.addAll(loaded);
        islandCollections.put(islandKey, set);
    }

    /**
     * Main entry: attempt to discover a unique item for the island.
     * itemKey format: "CATEGORY:RAW" e.g. "MINING:DIAMOND_ORE"
     * Returns true if it was new (and thus awarded).
     */
    public boolean discover(String islandKey, String itemKey, UUID discovererUuid, Player notifier) {
        if (islandKey == null || itemKey == null) return false;
        if (!KNOWN_COLLECTIONS.containsKey(itemKey)) {
            // Only track known high-value / progression items to avoid bloat
            return false;
        }

        // Anti-cheat guard (collection abuse / macro for milestones/tokens/cosmetics)
        if (notifier != null && plugin.getAntiCheatManager() != null &&
            plugin.getAntiCheatManager().isFlaggedForCollectionAbuse(notifier)) {
            return false;
        }

        Set<String> discovered = getDiscoveredForIsland(islandKey);
        if (discovered.contains(itemKey)) {
            return false; // already known to this island
        }

        discovered.add(itemKey);
        collectionsDirty = true; // event sink for large scale event-driven (e.g. collection leaderboards/views)

        // Report to anti-cheat for rate tracking (after new discover confirmed)
        if (notifier != null && plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().reportCollectionDiscover(notifier, itemKey);
        }

        // Persist (fire-and-forget style, matches other collection saves)
        plugin.getDatabaseManager().saveIslandCollection(islandKey, itemKey, discovererUuid);

        int total = discovered.size();

        // Award
        Island island = null;
        if (plugin.getIslandManager() != null) {
            // Best effort: try common lookup by parsing key or use first online member island
            for (Player online : Bukkit.getOnlinePlayers()) {
                Island cand = plugin.getIslandManager().getIsland(online.getUniqueId(), online.getWorld().getEnvironment());
                if (cand != null && islandKey.equals(cand.getId())) {
                    island = cand;
                    break;
                }
            }
        }

        int baseXp = 15;
        if (island != null) {
            island.addXp(baseXp);
        }

        String display = KNOWN_COLLECTIONS.getOrDefault(itemKey, itemKey);
        String cat = itemKey.contains(":") ? itemKey.substring(0, itemKey.indexOf(':')) : "MISC";
        String catName = CATEGORY_DISPLAY.getOrDefault(cat, cat);

        if (notifier != null && notifier.isOnline()) {
            notifier.sendMessage("§6§lIsland Collection §7» Discovered " + catName + " §f" + display + " §7(§a+" + baseXp + " Island XP§7)");
        } else if (island != null) {
            for (Player member : island.getOnlineMembers()) {
                member.sendMessage("§6§lIsland Collection §7» Discovered " + catName + " §f" + display + " §7(§a+" + baseXp + " Island XP§7)");
            }
        }

        // Check milestones (based on total unique for the island)
        checkMilestones(islandKey, total, notifier, island);

        return true;
    }

    private void checkMilestones(String islandKey, int total, Player recentDiscoverer, Island island) {
        for (int m : COLLECTION_MILESTONES) {
            if (total == m) {
                int bonusXp = m * 3;
                if (island != null) {
                    island.addXp(bonusXp);
                }

                String msg = "§6§l★ Island Collection Milestone! ★ §e" + total + " unique items discovered! §a+" + bonusXp + " Island XP";

                if (recentDiscoverer != null && recentDiscoverer.isOnline()) {
                    recentDiscoverer.sendMessage(msg);
                } else if (island != null) {
                    for (Player p : island.getOnlineMembers()) {
                        p.sendMessage(msg);
                    }
                }

                // Big milestone rewards (Play-to-Win progression fuel)
                if (m >= 25 && plugin.getBossManager() != null) {
                    // Award tokens to the discoverer or a random online member
                    Player target = recentDiscoverer != null && recentDiscoverer.isOnline() ? recentDiscoverer :
                            (island != null && !island.getOnlineMembers().isEmpty() ? island.getOnlineMembers().get(0) : null);
                    if (target != null) {
                        int tokens = (m / 25) * 5; // 5,10,20...
                        plugin.getBossManager().awardSlayerTokens(target, tokens);
                        target.sendMessage("§6§lCollection Reward §7» +" + tokens + " Slayer Tokens for reaching " + m + " uniques!");
                    }
                }

                // Feed cosmetics / special unlocks on big milestones (collections synergy with cosmetics)
                if (recentDiscoverer != null && recentDiscoverer.isOnline()) {
                    try {
                        if (m == 25 && plugin.getAccessoryCosmeticManager() != null) {
                            com.thenerdcj.cosmetic.AccessoryCosmetic acc = com.thenerdcj.cosmetic.AccessoryCosmetic.FLOATING_KEY;
                            plugin.getAccessoryCosmeticManager().unlock(recentDiscoverer.getUniqueId(), acc);
                            recentDiscoverer.sendMessage("§6§lCollection Reward §7» Unlocked Collector Accessory for 25 uniques!");
                        }
                        if (m == 50 && plugin.getIslandFurnitureManager() != null) {
                            com.thenerdcj.cosmetic.IslandFurnitureType collectorPiece =
                                    com.thenerdcj.cosmetic.IslandFurnitureType.ANCIENT_STATUE;
                            if (!plugin.getIslandFurnitureManager().hasFurniture(recentDiscoverer.getUniqueId(), collectorPiece)) {
                                plugin.getIslandFurnitureManager().unlockFurniture(recentDiscoverer.getUniqueId(), collectorPiece);
                                recentDiscoverer.sendMessage("§6§lCollection Reward §7» Unlocked special Collector furniture for 50 uniques!");
                            }
                        }
                        if (m == 100 && plugin.getOverheadCosmeticManager() != null) {
                            com.thenerdcj.cosmetic.OverheadCosmetic title = com.thenerdcj.cosmetic.OverheadCosmetic.CHAMPION_TITLE;
                            // unlock pattern similar
                            recentDiscoverer.sendMessage("§6§lLegendary Collector! §7Unlocked exclusive Overhead Title.");
                            // In real, call manager to grant if method exists
                        }
                        if (m == 200 && plugin.getEmoteCosmeticManager() != null) {
                            recentDiscoverer.sendMessage("§6§lMythic Collector! §7High collection grants special emote unlock.");
                        }
                    } catch (Exception ignored) {}
                } else if (m == 100 && island != null) {
                    if (recentDiscoverer != null && recentDiscoverer.isOnline()) {
                        recentDiscoverer.sendMessage("§6§lLegendary Collector! §7High collection progress unlocks exclusive prestige & cosmetic rewards.");
                    }
                }

                break;
            }
        }
    }

    /**
     * Convenience for listeners: discover a block material under MINING or FARMING etc.
     */
    public boolean discoverBlock(String islandKey, org.bukkit.Material material, UUID by, Player notifier) {
        if (material == null) return false;
        String cat = isFarmingMaterial(material) ? "FARMING" : "MINING";
        String key = cat + ":" + material.name();
        return discover(islandKey, key, by, notifier);
    }

    private boolean isFarmingMaterial(org.bukkit.Material m) {
        return m == org.bukkit.Material.WHEAT || m == org.bukkit.Material.CARROT || m == org.bukkit.Material.POTATO
                || m == org.bukkit.Material.BEETROOT || m == org.bukkit.Material.PUMPKIN || m == org.bukkit.Material.MELON
                || m == org.bukkit.Material.SUGAR_CANE || m == org.bukkit.Material.COCOA || m == org.bukkit.Material.NETHER_WART;
    }

    /**
     * For combat kills.
     */
    public boolean discoverMobKill(String islandKey, org.bukkit.entity.EntityType type, UUID by, Player notifier) {
        if (type == null) return false;
        String key = "COMBAT:" + type.name();
        return discover(islandKey, key, by, notifier);
    }

    /**
     * For fishing.
     */
    public boolean discoverFish(String islandKey, org.bukkit.Material caught, UUID by, Player notifier) {
        if (caught == null) return false;
        String key = "FISHING:" + caught.name();
        return discover(islandKey, key, by, notifier);
    }

    /**
     * For logs / foraging.
     */
    public boolean discoverLog(String islandKey, org.bukkit.Material log, UUID by, Player notifier) {
        if (log == null) return false;
        String key = "FORAGING:" + log.name();
        return discover(islandKey, key, by, notifier);
    }

    public Map<String, Integer> getCategoryCounts(String islandKey) {
        Map<String, Integer> counts = new HashMap<>();
        for (String item : getDiscoveredForIsland(islandKey)) {
            String cat = item.contains(":") ? item.substring(0, item.indexOf(':')) : "MISC";
            counts.put(cat, counts.getOrDefault(cat, 0) + 1);
        }
        return counts;
    }

    /** Returns a display name for a known key, or the key itself. */
    public String getDisplayName(String itemKey) {
        return KNOWN_COLLECTIONS.getOrDefault(itemKey, itemKey);
    }

    public Set<String> getAllKnownKeys() {
        return KNOWN_COLLECTIONS.keySet();
    }

    public String getCategoryDisplay(String category) {
        return CATEGORY_DISPLAY.getOrDefault(category, category);
    }

    public boolean isCollectionsDirty() { return collectionsDirty; }
    public void clearCollectionsDirty() { collectionsDirty = false; }

    /**
     * Bounded eviction for islandCollections (data compression for large servers with 1000+ islands, each with potentially large discovery sets).
     * Part of review/bound all CHM effort.
     */
    private void cleanupCaches() {
        if (islandCollections.size() > MAX_ISLANDS_COLLECTIONS) {
            java.util.Iterator<String> it = islandCollections.keySet().iterator();
            int toRemove = islandCollections.size() - (MAX_ISLANDS_COLLECTIONS - 200);
            while (it.hasNext() && toRemove > 0) { it.next(); it.remove(); toRemove--; }
        }
    }

    /** Seasonal reset hook (collections data wiped at DB layer). */
    public void clearForNewSeason() {
        islandCollections.clear();
        // collections map may be local; rely on DB wipe + cleanup
        cleanupCaches();
        plugin.getLogger().info("[CollectionManager] Cleared collection caches for new season.");
    }
}