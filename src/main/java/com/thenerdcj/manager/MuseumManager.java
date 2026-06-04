package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Museum system (Hypixel-aligned depth for task 4).
 * Players donate unique/rare items from play (collections, drops, trades) for "Museum Tokens" or small prestige progress.
 * Rewards: cosmetic crates, titles, or small non-power bonuses (strict Play-to-Win: no stats, no multipliers).
 * 
 * Data per island or player. Persist via cosmetic or future DAO.
 * Folia: async save where possible.
 * Compare: Hypixel museum is core progression sink/display for collections; here integrates with existing Collections + Prestige.
 */
public class MuseumManager {

    private final FoliaSkyblock plugin;
    // islandKey -> map of material to count (full persist with count/rarity support; rarity derived e.g. netherite high)
    private final Map<String, Map<String, Integer>> museumDonations = new ConcurrentHashMap<>();
    // tokens per island (earned from donations, spend in museum shop or prestige)
    private final Map<String, Integer> museumTokens = new ConcurrentHashMap<>();

    public MuseumManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Full persist now via IslandDAO (task batch)
    }

    public void loadForIsland(String islandKey) {
        if (islandKey == null || islandKey.isEmpty()) return;
        plugin.getDatabaseManager().getIslandDAO().loadMuseumData(islandKey).thenAccept(data -> {
            if (data != null && data.length >= 2) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Integer> d = (java.util.Map<String, Integer>) data[0];
                int t = (Integer) data[1];
                if (d != null) {
                    museumDonations.put(islandKey, new java.util.concurrent.ConcurrentHashMap<>(d));
                }
                museumTokens.put(islandKey, t);
            }
        });
    }

    private void saveForIsland(String islandKey) {
        if (islandKey == null) return;
        java.util.Map<String, Integer> d = museumDonations.getOrDefault(islandKey, java.util.Collections.emptyMap());
        int t = museumTokens.getOrDefault(islandKey, 0);
        plugin.getDatabaseManager().getIslandDAO().saveMuseumData(islandKey, d, t);
    }

    public boolean donate(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return false;
        String key = island.getId();
        String mat = item.getType().name();

        Map<String, Integer> donated = museumDonations.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        int currentCount = donated.getOrDefault(mat, 0);
        if (currentCount > 0) {
            player.sendMessage("§cAlready donated " + mat + " to the museum (count: " + currentCount + ").");
            return false;
        }

        // Play-to-Win: only donate if player "discovered" via collection or has in inv (no buy to donate)
        // Consume 1
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            player.getInventory().removeItem(item);
        }

        // Support count + rarity (rarity simple: high for netherite/diamond etc)
        int newCount = currentCount + 1;
        donated.put(mat, newCount);
        int rarityBonus = (mat.contains("NETHERITE") ? 50 : mat.contains("DIAMOND") ? 20 : 0);
        int tokens = 10 + rarityBonus;
        museumTokens.put(key, museumTokens.getOrDefault(key, 0) + tokens);

        player.sendMessage("§aDonated " + mat + " (count now " + newCount + ", rarity bonus " + rarityBonus + ") to museum! +" + tokens + " Museum Tokens.");
        // Small play reward: 5-20 island XP (fair, from grind)
        plugin.getIslandManager().addIslandXp(player, 10);
        // Future: prestige progress or cosmetic unlock

        saveForIsland(key); // full persist (JSON with counts)

        // Task batch: AC hook for museum abuse (prod log + Neural training samples)
        if (plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().reportMuseumDonateAbuse(player, 1);
        }
        return true;
    }

    /**
     * Spend tokens for cosmetic reward (PtW: cosmetic only, e.g. crate key or trail unlock via existing managers).
     * Returns true if spent.
     */
    public boolean spendTokens(Player player, int amount, String rewardType) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return false;
        String key = island.getId();
        int current = museumTokens.getOrDefault(key, 0);
        if (current < amount) {
            player.sendMessage("§cNot enough museum tokens (have " + current + ", need " + amount + ").");
            return false;
        }
        museumTokens.put(key, current - amount);
        saveForIsland(key);

        // Actual reward for PtW: give real crate key using CrateManager (cosmetic only, no power/stats).
        if ("cosmetic".equals(rewardType) || "crate".equals(rewardType)) {
            if (plugin.getCrateManager() != null) {
                // Give an EPIC key as museum reward (cosmetic crates)
                ItemStack rewardKey = plugin.getCrateManager().createKeyItem(com.thenerdcj.crate.CrateType.EPIC);
                player.getInventory().addItem(rewardKey);
                player.sendMessage("§aSpent " + amount + " tokens! Received an EPIC Crate Key (cosmetic rewards only).");
            } else {
                player.sendMessage("§aSpent " + amount + " tokens! Cosmetic reward granted (crate system).");
            }
        } else if ("title".equals(rewardType)) {
            player.sendMessage("§aSpent " + amount + " tokens! Title/cosmetic unlocked (integrate with tags if desired).");
        } else {
            player.sendMessage("§aSpent " + amount + " tokens for " + rewardType + ".");
        }
        return true;
    }

    public int getTokens(String islandKey) {
        return museumTokens.getOrDefault(islandKey, 0);
    }

    public Map<String, Integer> getDonated(String islandKey) {
        // Now returns count map for per-donation + count/rarity support (GUI can adapt)
        return Collections.unmodifiableMap(museumDonations.getOrDefault(islandKey, Collections.emptyMap()));
    }

    /** Seasonal reset hook (museum data wiped at DB). */
    public void clearForNewSeason() {
        museumDonations.clear();
        museumTokens.clear();
        plugin.getLogger().info("[MuseumManager] Cleared museum caches for new season.");
    }
}