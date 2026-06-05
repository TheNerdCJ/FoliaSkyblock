package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandUpgrade;
import com.thenerdcj.island.IslandUpgradeEvent;
import com.thenerdcj.manager.EconomyManager;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IslandUpgradeManager – Fully updated to work with the clean IslandUpgrade enum.
 *
 * The current level is now passed in as a parameter (correct architecture).
 * Cost is calculated dynamically using getCostForLevel(currentLevel).
 */
public class IslandUpgradeManager {

    private final FoliaSkyblock plugin;
    private final EconomyManager economyManager;

    public IslandUpgradeManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }


    /**
     * Apply an upgrade to an island.
     *
     * @param island        The island receiving the upgrade
     * @param upgrade       The type of upgrade being purchased
     * @param currentLevel  The CURRENT level of this upgrade on the island (before this purchase)
     * @param player        The player initiating the purchase
     */
    public CompletableFuture<Boolean> applyUpgrade(Island island, IslandUpgrade upgrade, int currentLevel, Player player) {
        GridPosition pos = island.getGridPosition();

        // Calculate the cost for the NEXT level
        int cost = upgrade.getCostForLevel(currentLevel);

        return economyManager.getIslandBalance(pos).thenCompose(currentBalance -> {

            if (currentBalance < cost) {
                SoundUtil.purchaseFail(player);
                    player.sendMessage("§cYour island does not have enough balance for this upgrade.");
                return CompletableFuture.completedFuture(false);
            }

            return economyManager.removeIslandBalance(pos, cost).thenCompose(success -> {
                if (!success) {
                    SoundUtil.error(player);
                    player.sendMessage("§cFailed to deduct island balance. Please try again.");
                    return CompletableFuture.completedFuture(false);
                }

                // Apply the upgrade effect (passing the new level)
                boolean applied = applyUpgradeEffect(island, upgrade, currentLevel + 1);

                if (applied) {
                    SoundUtil.purchaseSuccess(player);
                    player.sendMessage("§aUpgrade purchased successfully! §7(-$" + cost + ")");
                    // Persist immediately (idempotent with setUpgradeLevel)
                    String islandKey = island.getId();
                    plugin.getDatabaseManager().saveIslandUpgrade(islandKey, upgrade, currentLevel + 1);
                    return CompletableFuture.completedFuture(true);
                } else {
                    // Refund if application failed
                    return economyManager.addIslandBalance(pos, cost).thenApply(refund -> {
                        player.sendMessage("§cUpgrade failed to apply. Your balance has been refunded.");
                        return false;
                    });
                }
            });
        });
    }

    /**
     * Internal method that applies the actual upgrade effect.
     *
     * @param island    The island
     * @param upgrade   The upgrade type
     * @param newLevel  The level being upgraded TO
     */
    private boolean applyUpgradeEffect(Island island, IslandUpgrade upgrade, int newLevel) {
        // Real upgrade effects inspired by:
        // - SuperiorSkyblock2: Multipliers (crops-growth, spawner-rates), limits (border-size, team-limit), generator-rates
        // - IridiumSkyblock: EnhancementData pattern with per-island cached levels

        island.setUpgradeLevel(upgrade, newLevel);

        // Apply immediate or side-effect changes where possible
        switch (upgrade) {
            case ISLAND_SIZE:
                // Radius is now queryable via getEffectiveIslandRadius()
                // Protection listeners and ore generators can read it
                break;

            case CROP_GROWTH:
            case ORE_GENERATOR:
            case SPAWNER_RATE:
            case MOB_CAP:
                // Effects are reactive and read live from getCropGrowthMultiplier / getOreGeneratorLevel etc.
                // No immediate action needed here.
                break;

            case MINION_SLOTS:
            case MEMBER_LIMIT:
                // Enforced at usage sites (MinionManager, party/invite logic)
                break;

            case HOPPER_LIMIT:
            case VAULT_SLOTS:
            case AUTO_SELLER:
                // Storage/limit upgrades - enforced in relevant managers
                break;
        }

        return true;
    }

    private final ConcurrentHashMap<String, Double> cropGrowthCache = new ConcurrentHashMap<>();

    // Small cache for upgrade levels (avoids repeated DB hits for cold islands)
    private final ConcurrentHashMap<String, Integer> upgradeLevelCache = new ConcurrentHashMap<>();

    /**
     * Returns crop growth speed multiplier for this island (1.0 = normal).
     * Pulled from config and cached per island for performance.
     */
    public double getCropGrowthMultiplier(Island island) {
        String key = island.getId();
        return cropGrowthCache.computeIfAbsent(key, k -> {
            int level = island.getUpgradeLevel(IslandUpgrade.CROP_GROWTH);
            double perLevel = plugin.getConfig().getDouble("upgrades.crop-growth.multiplier-per-level", 0.25);
            return 1.0 + (level * perLevel);
        });
    }

    public void invalidateUpgradeCache(String islandId) {
        invalidateUpgradeCaches(islandId);
    }

    // ==================== UPGRADE-SPECIFIC HELPERS (Tier A) ====================

    private static final int BASE_HOPPER_LIMIT = 5;
    private static final int HOPPERS_PER_LEVEL = 5;

    public int getMaxHoppers(Island island) {
        if (island == null) return BASE_HOPPER_LIMIT;
        int level = getUpgradeLevel(island.getId(), IslandUpgrade.HOPPER_LIMIT);
        return BASE_HOPPER_LIMIT + (level * HOPPERS_PER_LEVEL);
    }

    public int getMaxHoppers(String islandId) {
        if (islandId == null) return BASE_HOPPER_LIMIT;
        int level = getUpgradeLevel(islandId, IslandUpgrade.HOPPER_LIMIT);
        return BASE_HOPPER_LIMIT + (level * HOPPERS_PER_LEVEL);
    }

    private static final int BASE_VAULT_SLOTS = 27; // 3 rows
    private static final int SLOTS_PER_VAULT_LEVEL = 9;

    public int getVaultInventorySize(Island island) {
        if (island == null) return BASE_VAULT_SLOTS;
        int level = getUpgradeLevel(island.getId(), IslandUpgrade.VAULT_SLOTS);
        int size = BASE_VAULT_SLOTS + (level * SLOTS_PER_VAULT_LEVEL);
        // Must be multiple of 9, max 54
        size = Math.min(54, ((size + 8) / 9) * 9);
        return Math.max(27, size);
    }

    private static final int BASE_WARDROBE_SLOTS = 4;
    private static final int WARDROBE_SLOTS_PER_LEVEL = 2;

    /**
     * Returns the maximum number of wardrobe loadout slots the player can use,
     * based on their island's WARDROBE_SLOTS upgrade level.
     * This provides the progression/grinding path requested by the community.
     */
    public int getMaxWardrobeSlots(Island island) {
        if (island == null) return BASE_WARDROBE_SLOTS;
        int level = getUpgradeLevel(island.getId(), IslandUpgrade.WARDROBE_SLOTS);
        return BASE_WARDROBE_SLOTS + (level * WARDROBE_SLOTS_PER_LEVEL);
    }

    public int getMaxWardrobeSlots(Player player) {
        if (player == null) return BASE_WARDROBE_SLOTS;
        Island island = plugin.getIslandManager().getIslandByOwner(
                player.getUniqueId(), player.getWorld().getEnvironment());
        return getMaxWardrobeSlots(island);
    }

    /**
     * Returns the level of the ore generator upgrade.
     */
    public int getOreGeneratorLevel(Island island) {
        return island.getUpgradeLevel(IslandUpgrade.ORE_GENERATOR);
    }

    /**
     * Returns the ore weight bonus factor for generators.
     */
    public double getOreGeneratorBonus(Island island) {
        int level = getOreGeneratorLevel(island);
        double perLevel = plugin.getConfig().getDouble("upgrades.ore-generator.bonus-per-level", 0.35);
        return 1.0 + (level * perLevel);
    }

    /**
     * Returns the extra build radius from ISLAND_SIZE upgrade.
     * Fully configurable via config.yml.
     */
    public int getExtraBuildRadius(Island island) {
        int level = island.getUpgradeLevel(IslandUpgrade.ISLAND_SIZE);
        int perLevel = plugin.getConfig().getInt("upgrades.island-size.radius-per-level", 8);
        return level * perLevel;
    }

    /**
     * Returns the effective island radius (base 256 + upgrade bonus).
     */
    public int getEffectiveIslandRadius(Island island) {
        int base = plugin.getConfig().getInt("upgrades.island-size.base-radius", 256);
        int fromSize = getExtraBuildRadius(island);

        int prestigeBonus = 0;
        if (plugin.getPrestigeManager() != null && plugin.getConfig().getBoolean("upgrades.island-size.prestige-bonus.enabled", true)) {
            int prestige = plugin.getPrestigeManager().getPrestigeLevel(island);
            int perPrestige = plugin.getConfig().getInt("upgrades.island-size.prestige-bonus.extra-per-prestige", 16);
            prestigeBonus = prestige * perPrestige;
        }

        int max = plugin.getConfig().getInt("upgrades.island-size.max-radius", 512);
        return Math.min(base + fromSize + prestigeBonus, max);
    }

    // ==================== HELPER METHODS (Cached) ====================

    public CompletableFuture<Double> getIslandBalance(Island island) {
        return economyManager.getIslandBalance(island.getGridPosition());
    }

    public CompletableFuture<Boolean> addIslandBalance(Island island, double amount) {
        return economyManager.addIslandBalance(island.getGridPosition(), amount);
    }

    public CompletableFuture<Boolean> removeIslandBalance(Island island, double amount) {
        return economyManager.removeIslandBalance(island.getGridPosition(), amount);
    }

    // ==================== METHODS REQUIRED BY OTHER CLASSES ====================

    /**
     * Gets the current level of a specific upgrade for an island.
     * Used by GUI, MinionManager, OreGenerator, etc.
     *
     * Reference: Similar to how IridiumSkyblock and SuperiorSkyblock2 cache upgrade levels on the Island object.
     */
    public int getUpgradeLevel(String islandKey, IslandUpgrade upgrade) {
        if (islandKey == null || upgrade == null) return 0;

        String cacheKey = islandKey + ":" + upgrade.name();

        // 1. Check in-memory cache first (very fast)
        Integer cached = upgradeLevelCache.get(cacheKey);
        if (cached != null) return cached;

        // 2. Try loaded islands (no blocking)
        for (var entry : plugin.getIslandManager().getAllLoadedIslands().entrySet()) {
            Island island = entry.getValue();
            if (island.getId().equals(islandKey)) {
                int level = island.getUpgradeLevel(upgrade);
                upgradeLevelCache.put(cacheKey, level);
                return level;
            }
        }

        // 3. Cold path: load from DB asynchronously and cache later
        plugin.getDatabaseManager().getIslandUpgradeLevel(islandKey, upgrade).thenAccept(level -> {
            upgradeLevelCache.put(cacheKey, level);
        });

        // Return 0 (or last known) immediately for cold islands — levels will warm up quickly
        return 0;
    }

    /**
     * Call this when an island's upgrades change (purchase, load, etc.) to keep caches fresh.
     */
    public void invalidateUpgradeCaches(String islandId) {
        cropGrowthCache.remove(islandId);
        // Remove any cached levels for this island
        upgradeLevelCache.keySet().removeIf(k -> k.startsWith(islandId + ":"));
    }

    /**
     * Purchases an upgrade for a player.
     * This is the main entry point called from IslandUpgradeGUI.
     */
    public void purchaseUpgrade(Player player, Island island, IslandUpgrade upgrade) {
        if (player == null || island == null || upgrade == null) return;

        int currentLevel = island.getUpgradeLevel(upgrade);

        // Fire cancellable event (polished extensibility, similar to SuperiorSkyblock2)
        IslandUpgradeEvent event = new IslandUpgradeEvent(player, island, upgrade, currentLevel, currentLevel + 1);
        plugin.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            player.sendMessage("§cUpgrade purchase was cancelled.");
            return;
        }

        // Apply cost multiplier from event (if any plugin modified it)
        int baseCost = upgrade.getCostForLevel(currentLevel);
        int finalCost = (int) (baseCost * event.getCostMultiplier());

        applyUpgradeWithCost(island, upgrade, currentLevel, player, finalCost).thenAccept(success -> {
            if (success) {
                island.setUpgradeLevel(upgrade, currentLevel + 1);
                String islandKey = island.getId();
                plugin.getDatabaseManager().saveIslandUpgrade(islandKey, upgrade, currentLevel + 1);
                invalidateUpgradeCache(islandKey);

                // Recalculate island worth when upgrades change (worth system integration)
                if (plugin.getIslandWorthManager() != null) {
                    plugin.getIslandWorthManager().invalidateCache(island);
                    plugin.getIslandWorthManager().recalculateAndUpdate(island);
                }

                // Sync border size setting when ISLAND_SIZE is upgraded (visual + protection consistency)
                if (upgrade == IslandUpgrade.ISLAND_SIZE && plugin.getIslandSettingsManager() != null) {
                    int effectiveRadius = getEffectiveIslandRadius(island);
                    plugin.getIslandSettingsManager().getSettings(island.getGridPosition()).thenAccept(settings -> {
                        settings.setBorderSize(effectiveRadius);
                        plugin.getIslandSettingsManager().saveSettings(settings);
                    });
                }

                // Visual expansion effects
                if (upgrade == IslandUpgrade.ISLAND_SIZE && plugin.getBorderVisualManager() != null) {
                    plugin.getBorderVisualManager().playExpansionEffect(player, island, currentLevel + 1);
                }
            }
        });
    }

    // Internal version that accepts a possibly modified cost
    private CompletableFuture<Boolean> applyUpgradeWithCost(Island island, IslandUpgrade upgrade, int currentLevel, Player player, int cost) {
        GridPosition pos = island.getGridPosition();

        return economyManager.getIslandBalance(pos).thenCompose(currentBalance -> {
            if (currentBalance < cost) {
                SoundUtil.purchaseFail(player);
                    player.sendMessage("§cYour island does not have enough balance for this upgrade.");
                return CompletableFuture.completedFuture(false);
            }

            return economyManager.removeIslandBalance(pos, cost).thenCompose(success -> {
                if (!success) {
                    SoundUtil.error(player);
                    player.sendMessage("§cFailed to deduct island balance. Please try again.");
                    return CompletableFuture.completedFuture(false);
                }

                boolean applied = applyUpgradeEffect(island, upgrade, currentLevel + 1);

                if (applied) {
                    SoundUtil.purchaseSuccess(player);
                    player.sendMessage("§aUpgrade purchased successfully! §7(-$" + cost + ")");
                    return CompletableFuture.completedFuture(true);
                } else {
                    return economyManager.addIslandBalance(pos, cost).thenApply(refund -> {
                        player.sendMessage("§cUpgrade failed to apply. Your balance has been refunded.");
                        return false;
                    });
                }
            });
        });
    }
    /**
     * Get the current level of a specific upgrade on an island.
     */
    public int getUpgradeLevel(Island island, IslandUpgrade upgrade) {
        if (island == null || upgrade == null) {
            return 0;
        }
        return island.getUpgradeLevel(upgrade);
    }

    /**
     * Async version - loads directly from database.
     */
    public CompletableFuture<Integer> getUpgradeLevelAsync(Island island, IslandUpgrade upgrade) {
        String islandKey = island.getOwnerUuid() + "_" + island.getDimension().name().toLowerCase();
        return plugin.getDatabaseManager().getIslandUpgradeLevel(islandKey, upgrade);
    }
    // ==================== CONVENIENCE METHODS ====================

    /**
     * Purchase/apply an upgrade (alias for applyUpgrade).
     * Use this if your GUIs/commands call purchaseUpgrade(...).
     */
    public CompletableFuture<Boolean> purchaseUpgrade(Island island, IslandUpgrade upgrade, int currentLevel, Player player) {
        return applyUpgrade(island, upgrade, currentLevel, player);
    }

    /**
     * Overloaded version that automatically gets the current level.
     */
    public CompletableFuture<Boolean> purchaseUpgrade(Island island, IslandUpgrade upgrade, Player player) {
        int currentLevel = getUpgradeLevel(island, upgrade);
        return applyUpgrade(island, upgrade, currentLevel, player);
    }
}
