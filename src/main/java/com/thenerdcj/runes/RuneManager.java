package com.thenerdcj.runes;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Cosmetic Runes.
 * 
 * Runes are applied to items via PersistentDataContainer.
 * Effects trigger on combat (hits, criticals, etc.).
 * 
 * Follows the same patterns as PetSkin, ElytraWing, PlayerTag.
 */
public class RuneManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<Rune>> ownedRunes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Rune>> runeCollection = new ConcurrentHashMap<>();

    private static final int[] RUNE_COLLECTION_MILESTONES = {3, 5, 8, 12, 20};

    private final NamespacedKey RUNE_KEY;

    public RuneManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
        this.RUNE_KEY = new NamespacedKey(plugin, "cosmetic_rune");
    }

    public Set<Rune> getOwnedRunes(UUID uuid) {
        return ownedRunes.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public void unlockRune(UUID uuid, Rune rune) {
        if (rune == null || rune.isNone()) return;

        Set<Rune> owned = getOwnedRunes(uuid);
        boolean wasNew = owned.add(rune);

        if (wasNew) {
            awardCollectionXPIfNew(uuid, rune);
        }
    }

    private void awardCollectionXPIfNew(UUID uuid, Rune rune) {
        Set<Rune> collected = runeCollection.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        if (collected.add(rune)) {
            int xp = rune.getRarity().getXpReward() / 2; // lighter than pet types

            plugin.getDatabaseManager().saveRuneCollectionEntry(uuid, rune.name());

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (plugin.getConfig().getBoolean("runes.collection-xp.enabled", true)) {
                    plugin.getIslandManager().addIslandXp(player, xp);
                    player.sendMessage("§a§lRune Collection §7» Unlocked " + rune.getDisplayName() + " §7(§a+" + xp + " XP§7)");
                }

                // Milestones
                int count = collected.size();
                for (int m : RUNE_COLLECTION_MILESTONES) {
                    if (count == m) {
                        player.sendMessage("§6§l★ Rune Collector Milestone! ★ §e" + m + " unique runes unlocked!");
                        break;
                    }
                }
            }
        }
    }

    public boolean hasRune(UUID uuid, Rune rune) {
        if (rune == null || rune.isNone()) return true;
        return getOwnedRunes(uuid).contains(rune);
    }

    // ==================== ITEM APPLICATION ====================

    public boolean applyRuneToItem(Player player, ItemStack item, Rune rune) {
        return applyRuneToItem(player, item, rune, 1);
    }

    /**
     * Apply a rune with a specific tier (1 to maxTier).
     */
    public boolean applyRuneToItem(Player player, ItemStack item, Rune rune, int tier) {
        if (item == null || rune == null) return false;

        if (!rune.isNone() && !hasRune(player.getUniqueId(), rune)) {
            player.sendMessage("§cYou have not unlocked this rune.");
            return false;
        }

        if (tier < 1) tier = 1;
        if (tier > rune.getMaxTier()) tier = rune.getMaxTier();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (rune.isNone()) {
            pdc.remove(RUNE_KEY);
            player.sendMessage("§7Rune removed from item.");
        } else {
            String value = rune.name() + ":" + tier;
            pdc.set(RUNE_KEY, PersistentDataType.STRING, value);
            player.sendMessage("§aApplied " + rune.getDisplayName() + " §7(Tier " + tier + ") §ato the item.");
        }

        item.setItemMeta(meta);
        return true;
    }

    public Rune getRuneFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Rune.NONE;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String value = pdc.get(RUNE_KEY, PersistentDataType.STRING);

        if (value == null) return Rune.NONE;

        try {
            String[] parts = value.split(":");
            return Rune.valueOf(parts[0]);
        } catch (Exception e) {
            return Rune.NONE;
        }
    }

    public int getRuneTierFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String value = pdc.get(RUNE_KEY, PersistentDataType.STRING);

        if (value == null) return 1;

        try {
            String[] parts = value.split(":");
            if (parts.length > 1) {
                return Math.max(1, Integer.parseInt(parts[1]));
            }
        } catch (Exception ignored) {}
        return 1;
    }

    // ==================== EFFECTS ====================

    public void triggerHitEffect(Player attacker, org.bukkit.entity.Entity target, Rune rune) {
        triggerHitEffect(attacker, target, rune, 1);
    }

    public void triggerHitEffect(Player attacker, org.bukkit.entity.Entity target, Rune rune, int tier) {
        if (rune == null || rune.isNone() || target == null) return;

        Location loc = target.getLocation().add(0, 1, 0);
        int count = 10 + (tier * 6);
        double spread = 0.4 + (tier * 0.15);
        double speed = 0.04 + (tier * 0.02);

        // Folia-safe: schedule at target's location region (not attacker's world thread)
        threadSafety.runAtLocation(loc, () -> {
            if (loc.getWorld() == null) return;
            loc.getWorld().spawnParticle(
                rune.getEffectParticle(),
                loc,
                count,
                spread, spread, spread,
                speed
            );

            if (tier >= 3) {
                loc.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    loc,
                    8,
                    0.6, 0.6, 0.6,
                    0.01
                );
            }
        });
    }

    /**
     * Folia-safe location-based rune particle effect (for armor on victim, projectile block hits, etc.).
     * Uses RegionScheduler via ThreadSafety.
     */
    public void triggerLocationEffect(Location loc, Rune rune, int tier) {
        if (rune == null || rune.isNone() || loc == null) return;

        int count = 8 + (tier * 4);
        double spread = 0.4 + (tier * 0.1);
        double speed = 0.02 + (tier * 0.01);

        threadSafety.runAtLocation(loc, () -> {
            if (loc.getWorld() == null) return;
            loc.getWorld().spawnParticle(
                rune.getEffectParticle(),
                loc,
                count,
                spread, spread, spread,
                speed
            );
            if (tier >= 3) {
                loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 5, 0.5, 0.5, 0.5, 0.01);
            }
        });
    }

    // ==================== PERSISTENCE ====================

    public void loadPlayer(UUID uuid) {
        Set<String> runeIds = plugin.getDatabaseManager().loadPlayerRunes(uuid);
        Set<Rune> runes = ConcurrentHashMap.newKeySet();
        for (String id : runeIds) {
            try {
                Rune r = Rune.valueOf(id);
                if (!r.isNone()) runes.add(r);
            } catch (Exception ignored) {}
        }
        ownedRunes.put(uuid, runes);

        // Load collection
        Set<String> collIds = plugin.getDatabaseManager().loadRuneCollection(uuid);
        Set<Rune> collected = ConcurrentHashMap.newKeySet();
        for (String id : collIds) {
            try { collected.add(Rune.valueOf(id)); } catch (Exception ignored) {}
        }
        runeCollection.put(uuid, collected);
    }

    public void savePlayer(UUID uuid) {
        Set<Rune> runes = ownedRunes.getOrDefault(uuid, Collections.emptySet());
        Set<String> ids = new HashSet<>();
        for (Rune r : runes) ids.add(r.name());
        plugin.getDatabaseManager().savePlayerRunes(uuid, ids);
    }

    public int getRuneCollectionCount(UUID uuid) {
        Set<Rune> set = runeCollection.get(uuid);
        return set == null ? 0 : set.size();
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        ownedRunes.remove(player.getUniqueId());
    }
}
