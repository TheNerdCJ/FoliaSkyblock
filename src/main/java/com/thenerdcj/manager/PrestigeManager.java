package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Material;
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
     * Performs the prestige action.
     * - Increments prestige level
     * - Resets island level + XP (core power reset)
     * - Resets worth cache (will recalc from current blocks)
     * - Grants configured rewards
     * - Persists everything
     */
    public boolean performPrestige(Island island, Player performer) {
        if (!canPrestige(island)) {
            MessageUtil.sendMessage(performer, getPrestigeBlockerMessage(island));
            SoundUtil.error(performer);
            return false;
        }

        String key = getIslandKey(island);
        int oldLevel = getPrestigeLevel(island);
        int newLevel = oldLevel + 1;

        prestigeLevels.put(key, newLevel);

        // === THE RESET (high-endgame feel) ===
        int oldIslandLevel = island.getLevel();
        island.setLevel(1);
        island.setXp(0);

        // Invalidate and force worth recalc (prestige doesn't delete blocks, just the "level" progress)
        plugin.getIslandWorthManager().invalidateCache(island);
        plugin.getIslandWorthManager().recalculateAndUpdate(island);

        // Persist prestige + level reset
        plugin.getDatabaseManager().saveIslandPrestige(key, newLevel);
        plugin.getDatabaseManager().saveIslandLevel(key, 1, 0.0); // reuse existing island_levels table

        // Grant rewards
        grantPrestigeRewards(island, performer, newLevel);

        SoundUtil.prestige(performer);

        // Notify
        MessageUtil.sendMessage(performer, "§6§lPRESTIGE! §eYou have reached Prestige §b" + newLevel + "§e!");
        MessageUtil.sendMessage(performer, "§7Your island level has been reset, but you now have permanent power multipliers.");
        MessageUtil.sendMessage(performer, "§aNew multipliers active: §b+" + String.format("%.0f", (getPrestigeMultiplier(island, PrestigeMultiplierType.XP) - 1) * 100) + "% XP, "
                + String.format("%.0f", (getPrestigeMultiplier(island, PrestigeMultiplierType.WORTH) - 1) * 100) + "% Worth, etc.");

        // Optional: update tab for the player
        plugin.getIslandWorthManager().updatePlayerTabList(performer);

        // Personal cosmetics: grant any newly unlocked particle trails (prestige rewards + gated ones)
        if (plugin.getParticleTrailManager() != null) {
            plugin.getParticleTrailManager().grantPrestigeUnlocks(performer, newLevel);
        }

        // Pet prestige rewards (Play-to-Win vanity via collection/rarity)
        if (plugin.getPetManager() != null) {
            plugin.getPetManager().grantPrestigePetUnlocks(performer, newLevel);
        }

        // Cosmetic Player Tags (chat + tab display)
        if (plugin.getPlayerTagManager() != null) {
            plugin.getPlayerTagManager().grantPrestigeTagUnlocks(performer, newLevel);
        }

        // Elytra Wing Cosmetics (gliding visual effects)
        if (plugin.getElytraWingManager() != null) {
            plugin.getElytraWingManager().grantPrestigeWingUnlocks(performer, newLevel);
        }

        // Cosmetic Runes
        if (plugin.getRuneManager() != null) {
            // Grant some runes on prestige (example implementation)
            // You can expand this with specific rune rewards per level
        }

        return true;
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

    private String getIslandKey(Island island) {
        GridPosition pos = island.getGridPosition();
        return pos.x() + ":" + pos.z() + ":" + island.getDimension().name();
    }

    public enum PrestigeMultiplierType {
        XP, WORTH, MONEY_EARN, BOOSTER_EFFECTIVENESS
    }
}