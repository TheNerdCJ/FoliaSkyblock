package com.thenerdcj.crate;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.Arrays;

/**
 * Manages crate rewards, key validation, and opening logic.
 * Integrates with Economy, Booster, Prestige, and Missions.
 */
public class CrateManager {

    private final FoliaSkyblock plugin;
    private final Map<CrateType, List<CrateReward>> rewards = new EnumMap<>(CrateType.class);
    private NamespacedKey CRATE_KEY;

    public CrateManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.CRATE_KEY = new NamespacedKey(plugin, "crate_key_type");
        loadDefaultRewards();
    }

    private void loadDefaultRewards() {
        // Common
        rewards.put(CrateType.COMMON, List.of(
            new CrateReward("Money", 1000, 0.6),
            new CrateReward("Booster 15m", "CROP_GROWTH:15", 0.3),
            new CrateReward("Item", "DIAMOND:4", 0.1)
        ));

        // Rare + higher tiers with better stuff (prestige points, better boosters, etc.)
        rewards.put(CrateType.RARE, List.of(
            new CrateReward("Money", 8000, 0.4),
            new CrateReward("Booster 45m", "ISLAND_XP:45", 0.35),
            new CrateReward("Prestige XP", "50", 0.15),
            new CrateReward("Item", "EMERALD_BLOCK:8", 0.1)
        ));

        // Epic and Legendary would have even better rewards (legendary items, high value boosters, crate keys, etc.)
    }

    public void openCrate(Player player, CrateType type) {
        List<CrateReward> pool = rewards.getOrDefault(type, rewards.get(CrateType.COMMON));
        CrateReward reward = weightedRandom(pool);

        // Grant reward
        reward.grant(player, plugin);

        SoundUtil.crateOpen(player);
        player.sendMessage("§aYou opened a " + type.getDisplayName() + " and received: §e" + reward.getName());
    }

    /** Creates a PDC-tagged key item for a crate type */
    public ItemStack createKeyItem(CrateType type) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + type.getDisplayName() + " Key");
            meta.setLore(Arrays.asList(
                "§7Use this to open a " + type.getDisplayName(),
                "§7in /is crate"
            ));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CRATE_KEY, PersistentDataType.STRING, type.name());
            key.setItemMeta(meta);
        }
        return key;
    }

    public boolean hasKey(Player player, CrateType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            String keyType = meta.getPersistentDataContainer().get(CRATE_KEY, PersistentDataType.STRING);
            if (type.name().equals(keyType)) return true;
        }
        return false;
    }

    public boolean consumeKey(Player player, CrateType type) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            String keyType = meta.getPersistentDataContainer().get(CRATE_KEY, PersistentDataType.STRING);
            if (type.name().equals(keyType)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    private CrateReward weightedRandom(List<CrateReward> pool) {
        double total = pool.stream().mapToDouble(CrateReward::getWeight).sum();
        double r = new Random().nextDouble() * total;
        for (CrateReward rw : pool) {
            r -= rw.getWeight();
            if (r <= 0) return rw;
        }
        return pool.get(0);
    }

    /** Simple reward definition for crates */
    public static class CrateReward {
        private final String name;
        private final Object value; // money amount, "TYPE:duration", item string, etc.
        private final double weight;

        public CrateReward(String name, Object value, double weight) {
            this.name = name;
            this.value = value;
            this.weight = weight;
        }

        public String getName() { return name; }
        public double getWeight() { return weight; }

        public void grant(Player player, FoliaSkyblock plugin) {
            if (value instanceof Integer) {
                // Money → Island Bank (consistent with prestige/mission rewards)
                com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                if (island != null && plugin.getIslandBankManager() != null) {
                    plugin.getIslandBankManager().deposit(island.getGridPosition(), (Integer) value)
                        .thenAccept(success -> {
                            if (success && player.isOnline()) {
                                player.sendMessage("§a+" + value + " coins added to island bank from crate!");
                            }
                        });
                } else {
                    player.sendMessage("§a+" + value + " coins from crate (no island bank)");
                }
            } else if (value instanceof String) {
                String s = (String) value;
                if (s.contains(":")) {
                    String[] parts = s.split(":", 2);
                    String type = parts[0].toUpperCase();
                    String data = parts[1];

                    if (type.startsWith("BOOSTER_") || type.matches(".*_GROWTH|.*_XP|.*_SELL")) {
                        // Booster reward e.g. "CROP_GROWTH:45"
                        try {
                            com.thenerdcj.booster.BoosterType bType = com.thenerdcj.booster.BoosterType.valueOf(type);
                            int minutes = Integer.parseInt(data);
                            com.thenerdcj.island.Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                            if (island != null && plugin.getBoosterManager() != null) {
                                double mult = plugin.getConfig().getDouble("boosters.multipliers." + bType.name(), 2.0);
                                plugin.getBoosterManager().activateBooster(island, bType, mult, minutes * 60L * 1000L);
                                player.sendMessage("§a+" + minutes + "m " + bType.getDisplayName() + " Booster from crate!");
                            }
                        } catch (Exception e) {
                            player.sendMessage("§eReceived unknown booster: " + s);
                        }
                    } else {
                        // Item e.g. "DIAMOND:4" or "EMERALD_BLOCK:8"
                        try {
                            org.bukkit.Material mat = org.bukkit.Material.valueOf(type);
                            int amount = Integer.parseInt(data);
                            ItemStack item = new ItemStack(mat, amount);
                            player.getInventory().addItem(item);
                            player.sendMessage("§aReceived " + amount + "x " + mat.name() + " from crate!");
                        } catch (Exception e) {
                            player.sendMessage("§eReceived crate reward: " + s);
                        }
                    }
                } else {
                    // Simple string reward (prestige XP etc.)
                    player.sendMessage("§aReceived crate reward: " + s);
                }
            }
        }
    }
}