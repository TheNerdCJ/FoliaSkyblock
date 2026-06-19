package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prestige System - High-endgame feature.
 * Players can reset significant island progress (level, XP, worth) in exchange for
 * permanent prestige levels that grant powerful, stacking multipliers across the entire progression system.
 *
 * Design goals:
 * - Folia-safe
 * - Layers cleanly on top of upgrades + boosters
 * - Config-driven requirements, multipliers, and rewards per level
 * - Strong "endgame" feel with titles and exclusive power
 */
public class PrestigeManager {

    private final FoliaSkyblock plugin;

    // islandKey -> current prestige level
    private final Map<String, Integer> prestigeLevels = new ConcurrentHashMap<>();

    // Cached config values
    private int maxPrestige = 10;
    private int minLevelReq = 50;
    private double minWorthReq = 250000;
    private double xpMultiplierPerLevel = 0.10;
    private double worthMultiplierPerLevel = 0.08;
    private double moneyMultiplierPerLevel = 0.12;
    private double boosterMultiplierPerLevel = 0.05;

    public PrestigeManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void reloadConfig() {
        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection prestigeSec = plugin.getConfig().getConfigurationSection("island.prestige");
        if (prestigeSec == null) return;

        maxPrestige = prestigeSec.getInt("max_prestige", 10);
        if (maxPrestige < 0) maxPrestige = Integer.MAX_VALUE;

        ConfigurationSection req = prestigeSec.getConfigurationSection("requirements");
        if (req != null) {
            minLevelReq = req.getInt("min_island_level", 50);
            minWorthReq = req.getDouble("min_worth", 250000);
        }

        ConfigurationSection mult = prestigeSec.getConfigurationSection("multipliers");
        if (mult != null) {
            xpMultiplierPerLevel = mult.getDouble("xp", 0.10);
            worthMultiplierPerLevel = mult.getDouble("worth", 0.08);
            moneyMultiplierPerLevel = mult.getDouble("money_earn", 0.12);
            boosterMultiplierPerLevel = mult.getDouble("booster_effectiveness", 0.05);
        }
    }

    public int getPrestigeLevel(Island island) {
        if (island == null) return 0;
        String key = getIslandKey(island);
        return prestigeLevels.getOrDefault(key, 0);
    }

    public int getPrestigeLevel(String islandKey) {
        return prestigeLevels.getOrDefault(islandKey, 0);
    }

    /**
     * Returns an effective "height" for the island that incorporates the prestige number.
     * This allows prestige to directly contribute to "how high level / powerful" the island
     * is considered for leaderboards, displays, borders, and top-island competition,
     * while the raw Island level remains the progress within the current prestige run.
     *
     * Formula: baseLevel + (prestige * scale). Scale is configurable (default 100).
     */
    public int getEffectiveLevel(Island island) {
        if (island == null) return 0;
        int base = island.getLevel();
        int p = getPrestigeLevel(island);
        int scale = plugin.getConfig().getInt("island.prestige.level-incorporation-scale", 100);
        if (scale < 0) scale = 0;
        return base + (p * scale);
    }

    /**
     * Returns the total multiplier for a given type (1.0 = no bonus).
     * This is the key integration point for the rest of the plugin.
     */
    public double getPrestigeMultiplier(Island island, PrestigeMultiplierType type) {
        int level = getPrestigeLevel(island);
        if (level <= 0) return 1.0;

        double perLevel = switch (type) {
            case XP -> xpMultiplierPerLevel;
            case WORTH -> worthMultiplierPerLevel;
            case MONEY_EARN -> moneyMultiplierPerLevel;
            case BOOSTER_EFFECTIVENESS -> boosterMultiplierPerLevel;
        };

        return 1.0 + (perLevel * level);
    }

    public boolean canPrestige(Island island) {
        if (island == null) return false;

        int current = getPrestigeLevel(island);
        if (current >= maxPrestige) return false;

        if (island.getLevel() < minLevelReq) return false;

        double worth = plugin.getIslandWorthManager().getCachedWorth(island);
        if (worth < minWorthReq) {
            // Try forcing a recalc if cache is stale/low
            plugin.getIslandWorthManager().recalculateAndUpdate(island);
            worth = plugin.getIslandWorthManager().getCachedWorth(island);
        }
        return worth >= minWorthReq;
    }

    /**
     * Returns a helpful, specific error message explaining exactly why the player
     * cannot prestige yet. Used for better UX in commands and GUIs.
     */
    public String getPrestigeBlockerMessage(Island island) {
        if (island == null) {
            return "§cNo island found.";
        }

        int currentPrestige = getPrestigeLevel(island);
        if (currentPrestige >= maxPrestige) {
            return "§cYou have reached the maximum Prestige level (" + maxPrestige + ").";
        }

        int islandLevel = island.getLevel();
        double worth = plugin.getIslandWorthManager().getCachedWorth(island);

        StringBuilder msg = new StringBuilder("§cYou cannot prestige yet. Requirements:\n");

        if (islandLevel < minLevelReq) {
            int needed = minLevelReq - islandLevel;
            msg.append("§7 • Island Level: §e").append(minLevelReq)
               .append(" §7(you are §c").append(islandLevel).append("§7, need §a+").append(needed).append("§7)\n");
        } else {
            msg.append("§7 • Island Level: §a").append(minLevelReq).append(" §7(✓ you have §e").append(islandLevel).append("§7)\n");
        }

        if (worth < minWorthReq) {
            double needed = minWorthReq - worth;
            msg.append("§7 • Island Worth: §e$").append(String.format("%,.0f", minWorthReq))
               .append(" §7(you have §6$").append(String.format("%,.0f", worth))
               .append("§7, need §a+$").append(String.format("%,.0f", needed)).append("§7)");
        } else {
            msg.append("§7 • Island Worth: §a$").append(String.format("%,.0f", minWorthReq))
               .append(" §7(✓ you have §6$").append(String.format("%,.0f", worth)).append("§7)");
        }

        return msg.toString();
    }

    /**
     * Performs the prestige action (default path).
     * Resets the target island back to a fresh starter using its *current* biome (no new biome choice).
     * Does NOT cascade/reset other dimension islands.
     * Use performPrestigeRebirth(...) when the player chose a new biome (for cascade support).
     */
    public boolean performPrestige(Island island, Player performer) {
        if (island == null || performer == null) return false;
        String currentBiome = island.getBiomeName();
        // Default: no cascade of other dims, no personality reroll
        return performPrestigeRebirth(island, performer, currentBiome, false, false);
    }

    /**
     * Full prestige + island rebirth.
     * - Increments prestige (permanent power + multipliers).
     * - Uses the chosenBiome for the main island (may be the current one = "default").
     * - If cascadeOtherDimensions && the player chose a *new* biome (different from current), the player's existing
     *   Nether and End islands are also reset to fresh starters (using their previous biomes).
     * - The physical plots are re-allocated and small starter islands are generated (PMC-style compact).
     * - Prestige number is applied to all affected dimension islands for this owner.
     * - Player cosmetics for the new prestige level are granted once.
     */
    public boolean performPrestigeRebirth(Island mainIsland, Player performer, String chosenBiomeName,
                                          boolean cascadeOtherDimensions) {
        return performPrestigeRebirth(mainIsland, performer, chosenBiomeName, cascadeOtherDimensions, false);
    }

    public boolean performPrestigeRebirth(Island mainIsland, Player performer, String chosenBiomeName,
                                          boolean cascadeOtherDimensions, boolean donorRerollPersonality) {
        if (mainIsland == null || performer == null) return false;
        if (!canPrestige(mainIsland)) {
            MessageUtil.sendMessage(performer, getPrestigeBlockerMessage(mainIsland));
            SoundUtil.error(performer);
            return false;
        }

        String mainStableKey = getIslandKey(mainIsland);
        int oldP = getPrestigeLevel(mainIsland);
        int newP = oldP + 1;

        // Update in-memory for main immediately
        prestigeLevels.put(mainStableKey, newP);

        // Figure out which dimensions will be reborn
        java.util.List<World.Environment> dimsToRebirth = new java.util.ArrayList<>();
        dimsToRebirth.add(World.Environment.NORMAL);

        boolean isChoosingNewBiome = (chosenBiomeName != null)
                && !chosenBiomeName.equalsIgnoreCase(mainIsland.getBiomeName());

        if (cascadeOtherDimensions && isChoosingNewBiome) {
            Island nether = plugin.getIslandManager().getIsland(performer.getUniqueId(), World.Environment.NETHER);
            if (nether != null) dimsToRebirth.add(World.Environment.NETHER);
            Island end = plugin.getIslandManager().getIsland(performer.getUniqueId(), World.Environment.THE_END);
            if (end != null) dimsToRebirth.add(World.Environment.THE_END);
        }

        // Persist the new prestige for every affected dimension using the *stable* owner_dim key.
        // This way prestige travels with the player's "skyblock identity" across rebirths.
        for (World.Environment d : dimsToRebirth) {
            Island target = (d == World.Environment.NORMAL ? mainIsland
                    : plugin.getIslandManager().getIsland(performer.getUniqueId(), d));
            String k = (target != null) ? getIslandKey(target)
                    : (performer.getUniqueId().toString() + "_" + d.name().toLowerCase());
            prestigeLevels.put(k, newP);
            plugin.getDatabaseManager().saveIslandPrestige(k, newP);
        }

        // Dirty tops (prestige affects multipliers + ranking feel)
        if (plugin.getIslandWorthManager() != null) {
            plugin.getIslandWorthManager().markWorthTopsDirty();
            plugin.getIslandWorthManager().markLevelTopsDirty();
            plugin.getIslandWorthManager().markMembersTopsDirty();
        }

        // Grant the (player-global) prestige cosmetics / rewards once
        grantPrestigeRewards(mainIsland, performer, newP);

        // Also the big list of unlocks that used to live in the old performPrestige body
        // (kept here so all the cosmetic grants still fire on rebirth)
        int newLevel = newP;
        if (plugin.getParticleTrailManager() != null) {
            plugin.getParticleTrailManager().grantPrestigeUnlocks(performer, newLevel);
        }
        if (plugin.getPetManager() != null) {
            plugin.getPetManager().grantPrestigePetUnlocks(performer, newLevel);
        }
        if (plugin.getPlayerTagManager() != null) {
            plugin.getPlayerTagManager().grantPrestigeTagUnlocks(performer, newLevel);
            if (plugin.getNameColorManager() != null) {
                plugin.getNameColorManager().grantPrestigeNameColorUnlocks(performer, newLevel);
            }
        }
        if (plugin.getElytraWingManager() != null) {
            plugin.getElytraWingManager().grantPrestigeWingUnlocks(performer, newLevel);
        }
        if (plugin.getHelmetSkinManager() != null && newLevel >= 5) {
            plugin.getHelmetSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.HelmetSkin.BLAZING_CRIMSON);
        }
        if (plugin.getDeathEffectManager() != null && newLevel >= 4) {
            plugin.getDeathEffectManager().unlockEffect(performer.getUniqueId(), com.thenerdcj.cosmetic.DeathEffect.LIGHTNING);
        }
        if (plugin.getDeathMessageManager() != null && newLevel >= 3) {
            plugin.getDeathMessageManager().unlockMessage(performer.getUniqueId(), com.thenerdcj.cosmetic.DeathMessageCosmetic.CLASSIC);
        }
        if (plugin.getBackpackSkinManager() != null && newLevel >= 3) {
            plugin.getBackpackSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.BackpackSkin.GARDEN_BUNNY);
        }
        if (plugin.getPowerOrbSkinManager() != null && newLevel >= 4) {
            plugin.getPowerOrbSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.PowerOrbSkin.DISCO_BALL);
            plugin.getPowerOrbSkinManager().giveOrb(performer, com.thenerdcj.cosmetic.PowerOrbSkin.DISCO_BALL);
        }
        if (plugin.getMinionSkinManager() != null && newLevel >= 3) {
            plugin.getMinionSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.MinionSkin.BUNNY);
        }
        if (plugin.getMinionSkinManager() != null && newLevel >= 5) {
            plugin.getMinionSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.MinionSkin.DRAGON);
        }
        if (plugin.getMinionSkinManager() != null && newLevel >= 4) {
            plugin.getMinionSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.MinionSkin.PUMPKIN);
        }
        if (plugin.getMinionSkinManager() != null && newLevel >= 6) {
            plugin.getMinionSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.MinionSkin.ROBOT);
        }
        if (plugin.getMinionSkinManager() != null && newLevel >= 3) {
            plugin.getMinionSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.MinionSkin.GHOST);
        }
        if (plugin.getMinionSkinManager() != null && newLevel >= 5) {
            plugin.getMinionSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.MinionSkin.ANCIENT_GOLEM);
        }
        if (plugin.getPowerOrbSkinManager() != null && newLevel >= 6) {
            plugin.getPowerOrbSkinManager().unlockSkin(performer.getUniqueId(), com.thenerdcj.cosmetic.PowerOrbSkin.SUPREME);
            plugin.getPowerOrbSkinManager().giveOrb(performer, com.thenerdcj.cosmetic.PowerOrbSkin.SUPREME);
        }
        if (plugin.getIslandFurnitureManager() != null && newLevel >= 3) {
            plugin.getIslandFurnitureManager().unlockFurniture(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandFurnitureType.WOODEN_CHAIR);
        }
        if (plugin.getIslandFurnitureManager() != null && newLevel >= 5) {
            plugin.getIslandFurnitureManager().unlockFurniture(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandFurnitureType.FOUNTAIN);
        }
        if (plugin.getIslandFurnitureManager() != null && newLevel >= 4) {
            plugin.getIslandFurnitureManager().unlockFurniture(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandFurnitureType.CELESTIAL_LAMP);
        }
        if (plugin.getIslandFurnitureManager() != null && newLevel >= 3) {
            plugin.getIslandFurnitureManager().unlockFurniture(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandFurnitureType.DECORATIVE_GLOBE);
        }
        if (plugin.getIslandMusicManager() != null && newLevel >= 2) {
            plugin.getIslandMusicManager().unlockMusic(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandMusicType.CALM_OCEAN);
        }
        if (plugin.getIslandMusicManager() != null && newLevel >= 4) {
            plugin.getIslandMusicManager().unlockMusic(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandMusicType.CELESTIAL_CHIMES);
        }
        if (plugin.getIslandMusicManager() != null && newLevel >= 3) {
            plugin.getIslandMusicManager().unlockMusic(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandMusicType.JUNGLE_RHYTHM);
        }
        if (plugin.getIslandMusicManager() != null && newLevel >= 5) {
            plugin.getIslandMusicManager().unlockMusic(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandMusicType.ANCIENT_RUINS);
        }
        if (plugin.getOverheadCosmeticManager() != null && newLevel >= 3) {
            plugin.getOverheadCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.OverheadCosmetic.STAR_HALO);
        }
        if (plugin.getOverheadCosmeticManager() != null && newLevel >= 5) {
            plugin.getOverheadCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.OverheadCosmetic.CELESTIAL_AURA);
        }
        if (plugin.getOverheadCosmeticManager() != null && newLevel >= 5) {
            plugin.getOverheadCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.OverheadCosmetic.RUNIC_TITLE);
        }
        if (plugin.getOverheadCosmeticManager() != null && newLevel >= 6) {
            plugin.getOverheadCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.OverheadCosmetic.VOID_CROWN);
        }
        if (plugin.getOverheadCosmeticManager() != null && newLevel >= 5) {
            plugin.getOverheadCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.OverheadCosmetic.CELESTIAL_TITLE);
        }
        if (plugin.getOverheadCosmeticManager() != null && newLevel >= 6) {
            plugin.getOverheadCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.OverheadCosmetic.SLAYER_SIGIL);
            plugin.getOverheadCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.OverheadCosmetic.ETHEREAL_CROWN);
        }
        // Collections bonus (uses main island collections before rebirth)
        if (plugin.getCollectionManager() != null && mainIsland != null) {
            int collCount = plugin.getCollectionManager().getCollectionCount(mainIsland.getId());
            if (collCount >= 50 && plugin.getAccessoryCosmeticManager() != null) {
                try {
                    plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.ORBITING_CRYSTAL);
                    performer.sendMessage("§6§lPrestige Collection Bonus §7» High island collections unlocked extra accessory!");
                } catch (Exception ignored) {}
            }
        }
        if (plugin.getEmoteCosmeticManager() != null && newLevel >= 2) {
            plugin.getEmoteCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.EmoteCosmetic.WAVE);
        }
        if (plugin.getEmoteCosmeticManager() != null && newLevel >= 4) {
            plugin.getEmoteCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.EmoteCosmetic.CHEER);
        }
        if (plugin.getEmoteCosmeticManager() != null && newLevel >= 3) {
            plugin.getEmoteCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.EmoteCosmetic.CLAP);
        }
        if (plugin.getEmoteCosmeticManager() != null && newLevel >= 5) {
            plugin.getEmoteCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.EmoteCosmetic.VICTORY);
        }
        if (plugin.getEmoteCosmeticManager() != null && newLevel >= 1) {
            plugin.getEmoteCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.EmoteCosmetic.NOD);
        }
        if (plugin.getEmoteCosmeticManager() != null && newLevel >= 2) {
            plugin.getEmoteCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.EmoteCosmetic.HIGH_FIVE);
        }
        if (plugin.getEnchantmentManager() != null && newLevel >= 4) {
            org.bukkit.inventory.ItemStack book = plugin.getEnchantmentManager().createEnchantmentBook(com.thenerdcj.enchant.CustomEnchantment.OVERLOAD, 3);
            performer.getInventory().addItem(book);
            performer.sendMessage("§6§lPrestige Reward §7» Received Overload III enchantment book!");
        }
        if (plugin.getEnchantmentManager() != null && newLevel >= 6) {
            org.bukkit.inventory.ItemStack book = plugin.getEnchantmentManager().createEnchantmentBook(com.thenerdcj.enchant.CustomEnchantment.DRAGON_HUNTER, 5);
            performer.getInventory().addItem(book);
            performer.sendMessage("§6§lPrestige Reward §7» Received Dragon Hunter V enchantment book!");
        }
        if (plugin.getIslandStructureManager() != null && newLevel >= 3) {
            plugin.getIslandStructureManager().unlockStructure(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandStructureCosmetic.STONE_PILLAR);
        }
        if (plugin.getIslandStructureManager() != null && newLevel >= 5) {
            plugin.getIslandStructureManager().unlockStructure(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandStructureCosmetic.CRYSTAL_CLUSTER);
        }
        if (plugin.getChatBubbleCosmeticManager() != null && newLevel >= 2) {
            plugin.getChatBubbleCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.ChatBubbleCosmetic.HEART_BUBBLE);
        }
        if (plugin.getChatBubbleCosmeticManager() != null && newLevel >= 4) {
            plugin.getChatBubbleCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.ChatBubbleCosmetic.MAGIC_BUBBLE);
        }
        if (plugin.getChatBubbleCosmeticManager() != null && newLevel >= 3) {
            plugin.getChatBubbleCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.ChatBubbleCosmetic.SPARK_BUBBLE);
        }
        if (plugin.getChatBubbleCosmeticManager() != null && newLevel >= 5) {
            plugin.getChatBubbleCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.ChatBubbleCosmetic.SKULL_BUBBLE);
        }
        if (plugin.getIslandWeatherCosmeticManager() != null && newLevel >= 3) {
            plugin.getIslandWeatherCosmeticManager().unlockWeather(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandWeatherCosmetic.GENTLE_RAIN);
        }
        if (plugin.getIslandWeatherCosmeticManager() != null && newLevel >= 5) {
            plugin.getIslandWeatherCosmeticManager().unlockWeather(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandWeatherCosmetic.AURORA);
        }
        if (plugin.getIslandWeatherCosmeticManager() != null && newLevel >= 4) {
            plugin.getIslandWeatherCosmeticManager().unlockWeather(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandWeatherCosmetic.SANDSTORM);
        }
        if (plugin.getIslandWeatherCosmeticManager() != null && newLevel >= 6) {
            plugin.getIslandWeatherCosmeticManager().unlockWeather(performer.getUniqueId(), com.thenerdcj.cosmetic.IslandWeatherCosmetic.FIREFLY_GLOW);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 2) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.FLOATING_STAR);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 4) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.ORBITING_ORB);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 3) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.FLOATING_COMPASS);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 5) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.ORBITING_CRYSTAL);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 4) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.FLOATING_KEY);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 6) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.ORBITING_GEM);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 4) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.ORBITING_SWORD);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 3) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.GLOWING_LANTERN);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 4) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.FLOATING_BOOK);
        }
        if (plugin.getAccessoryCosmeticManager() != null && newLevel >= 3) {
            plugin.getAccessoryCosmeticManager().unlock(performer.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.ORBITING_RUNE);
        }

        SoundUtil.prestige(performer);

        // Now actually rebirth the physical islands (fresh plots + small starter gens)
        for (World.Environment d : dimsToRebirth) {
            String biomeForDim;
            if (d == World.Environment.NORMAL) {
                biomeForDim = (chosenBiomeName != null && !chosenBiomeName.isEmpty())
                        ? chosenBiomeName : mainIsland.getBiomeName();
            } else {
                Island dimI = plugin.getIslandManager().getIsland(performer.getUniqueId(), d);
                biomeForDim = (dimI != null && dimI.getBiomeName() != null)
                        ? dimI.getBiomeName() : getDefaultBiomeForDim(d);
            }
            boolean wantsRerollForThis = donorRerollPersonality && (d == World.Environment.NORMAL);
            plugin.getIslandManager().performPrestigeIslandRebirth(performer, biomeForDim, d, wantsRerollForThis);
        }

        // Notify (use effective level to emphasize the "how high" incorporation)
        int effective = getEffectiveLevel(mainIsland);
        MessageUtil.sendMessage(performer, "§6§lPRESTIGE " + newP + "! §eYour island has been reborn as a fresh starter.");
        if (cascadeOtherDimensions && isChoosingNewBiome) {
            MessageUtil.sendMessage(performer, "§7Your Nether and End islands were also reset as part of this prestige.");
        }
        MessageUtil.sendMessage(performer, "§7Permanent Prestige §b" + newP + " §7active. Effective height: §b" + effective + "§7.");
        MessageUtil.sendMessage(performer, "§aMultipliers: §b+" + String.format("%.0f", (getPrestigeMultiplier(mainIsland, PrestigeMultiplierType.XP) - 1) * 100)
                + "% XP, " + String.format("%.0f", (getPrestigeMultiplier(mainIsland, PrestigeMultiplierType.WORTH) - 1) * 100) + "% Worth, etc.");

        plugin.getIslandWorthManager().updatePlayerTabList(performer);

        return true;
    }

    private String getDefaultBiomeForDim(World.Environment dim) {
        if (dim == World.Environment.NETHER) return "NETHER_WASTES";
        if (dim == World.Environment.THE_END) return "THE_END";
        return "PLAINS";
    }

    private void grantPrestigeRewards(Island island, Player player, int newPrestigeLevel) {
        ConfigurationSection rewardsSec = plugin.getConfig().getConfigurationSection("island.prestige.rewards.level_" + newPrestigeLevel);
        if (rewardsSec == null) {
            // Generic fallback reward
            if (plugin.getEconomyManager() != null) {
                // Best effort money reward
                MessageUtil.sendMessage(player, "§aYou received a prestige bonus!");
            }
            return;
        }

        double money = rewardsSec.getDouble("money", 0);
        if (money > 0 && plugin.getEconomyManager() != null) {
            // Using island bank as prestige reward (fits the economy loop)
            GridPosition pos = island.getGridPosition();
            plugin.getIslandBankManager().deposit(pos, money);
            MessageUtil.sendMessage(player, "§a+§e$" + String.format("%,.0f", money) + " §7added to island bank (Prestige reward)");
        }

        // Simple item rewards (parse "MATERIAL:amount")
        List<String> itemStrings = rewardsSec.getStringList("items");
        for (String entry : itemStrings) {
            try {
                String[] parts = entry.split(":");
                Material mat = Material.valueOf(parts[0].toUpperCase());
                int amt = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                ItemStack stack = new ItemStack(mat, amt);
                player.getInventory().addItem(stack);
            } catch (Exception ignored) {}
        }

        String title = rewardsSec.getString("title");
        if (title != null) {
            MessageUtil.sendMessage(player, "§6Prestige Title Unlocked: " + title);
            // Future: store per-player titles
        }
    }

    public void loadPrestigeForIsland(String islandKey, int level) {
        if (level > 0) {
            prestigeLevels.put(islandKey, level);
        }
    }

    // ==================== PRESTIGE REBIRTH + BIOME CHOICE CONTEXT (for picker flow) ====================

    /** Transient per-player context when they chose "Prestige + Choose New Biome" and we opened the picker. */
    private static class PrestigeRebirthContext {
        final Island island;
        final boolean cascadeOtherDimensions;
        PrestigeRebirthContext(Island island, boolean cascade) {
            this.island = island;
            this.cascadeOtherDimensions = cascade;
        }
    }

    private final Map<UUID, PrestigeRebirthContext> pendingRebirthChoices = new ConcurrentHashMap<>();

    /** Called from PrestigeMainGUI when player clicks the "choose new biome" option. */
    public void startPrestigeBiomeChoice(Player player, Island island, boolean cascadeOtherDimensions) {
        if (player == null || island == null) return;
        pendingRebirthChoices.put(player.getUniqueId(), new PrestigeRebirthContext(island, cascadeOtherDimensions));
    }

    public boolean hasPendingPrestigeBiomeChoice(UUID playerUuid) {
        return pendingRebirthChoices.containsKey(playerUuid);
    }

    /** Returns the context (or null) and removes it. */
    public PrestigeRebirthContext consumePrestigeBiomeChoice(UUID playerUuid) {
        return pendingRebirthChoices.remove(playerUuid);
    }

    /**
     * Called by BiomeSelectionGUI when a prestige biome choice is made.
     * Consumes the pending context and executes the rebirth (with cascade if the context asked for it).
     */
    public void finishPrestigeBiomeChoice(Player player, String chosenBiome, boolean wantsPersonalityReroll) {
        if (player == null) return;
        PrestigeRebirthContext ctx = consumePrestigeBiomeChoice(player.getUniqueId());
        if (ctx == null || ctx.island == null) {
            // Fallback: treat as normal prestige with the chosen biome (no cascade)
            Island fallback = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (fallback == null) {
                fallback = plugin.getIslandManager().getIsland(player.getUniqueId(), World.Environment.NORMAL);
            }
            if (fallback != null) {
                performPrestigeRebirth(fallback, player, chosenBiome, false, wantsPersonalityReroll);
            }
            return;
        }
        performPrestigeRebirth(ctx.island, player, chosenBiome, ctx.cascadeOtherDimensions, wantsPersonalityReroll);
    }

    private String getIslandKey(Island island) {
        // Use stable owner+dimension key (matches Island.getId() and quest keys).
        // This ensures prestige survives plot reallocations on reset/prestige rebirth
        // and is consistent with the owner_dim fallback used in tops queries.
        return island.getId();
    }

    public enum PrestigeMultiplierType {
        XP, WORTH, MONEY_EARN, BOOSTER_EFFECTIVENESS
    }

    /**
     * Seasonal reset hook (prestige data is island-bound and deleted by the DAO wipe).
     */
    public void clearForNewSeason() {
        prestigeLevels.clear();
        plugin.getLogger().info("[PrestigeManager] Cleared prestige level cache for new season.");
    }
}