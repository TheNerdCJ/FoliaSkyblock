package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MinionSkinManager - Full Play-to-Win cosmetic skin system for island minions.
 *
 * Follows the exact established pattern from PowerOrbSkinManager / BackpackSkinManager / Pet skins.
 * - Per-player ownership + active theme (applies to all minions on the player's islands).
 * - Collection XP on first unlock (island XP + chat milestones).
 * - Folia-safe via ThreadSafety (no direct BukkitScheduler).
 * - Persistence via DatabaseManager.
 *
 * Visual application happens in MinionManager (spawn/respawn hooks call into here for skin data).
 */
public class MinionSkinManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<MinionSkin>> ownedSkins = new ConcurrentHashMap<>();
    private final Map<UUID, MinionSkin> activeSkin = new ConcurrentHashMap<>();
    // Per-minion assignments: islandKey -> minionNum -> skin (for per-assignment polish)
    private final Map<String, Map<Integer, MinionSkin>> minionAssignments = new ConcurrentHashMap<>();

    private static final int[] COLLECTION_MILESTONES = {3, 5, 10, 15, 25};

    public MinionSkinManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<MinionSkin> getOwnedSkins(UUID uuid) {
        return ownedSkins.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public MinionSkin getActiveSkin(UUID uuid) {
        return activeSkin.getOrDefault(uuid, MinionSkin.NONE);
    }

    public void unlockSkin(UUID uuid, MinionSkin skin) {
        if (skin == null || skin.isNone()) return;
        Set<MinionSkin> skins = getOwnedSkins(uuid);
        boolean wasNew = skins.add(skin);
        if (wasNew) {
            int xp = Math.max(10, skin.getRarity().getXpReward() / 3);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && plugin.getConfig().getBoolean("minions.collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(player, xp);
                player.sendMessage("§a§lMinion Skin Collection §7» Unlocked " + skin.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = skins.size();
                for (int m : COLLECTION_MILESTONES) {
                    if (count == m) {
                        player.sendMessage("§6§l★ Minion Collector Milestone! ★ §e" + count + " unique minion skins unlocked!");
                        player.sendActionBar("§6Minion Skin Collection: " + count + " / " + (MinionSkin.values().length - 1));
                        break;
                    }
                }
            }
            plugin.getDatabaseManager().savePlayerMinionSkins(uuid, getSkinIdSet(uuid));
        }
    }

    public boolean hasSkin(UUID uuid, MinionSkin skin) {
        if (skin == null || skin.isNone()) return true;
        return getOwnedSkins(uuid).contains(skin);
    }

    public void setActiveSkin(Player player, MinionSkin skin) {
        if (skin != null && !skin.isNone() && !hasSkin(player.getUniqueId(), skin)) {
            player.sendMessage("§cYou have not unlocked this minion skin.");
            return;
        }
        activeSkin.put(player.getUniqueId(), skin != null ? skin : MinionSkin.NONE);
        player.sendMessage("§aMinion Skin " + (skin == null || skin.isNone() ? "removed" : "activated: " + skin.getDisplayName())
                + " §7(Applies to all minions on your islands)");
    }

    // Per-minion assignment support (polish for individual minion skins)
    public void assignSkinToMinion(String islandKey, int minionNum, MinionSkin skin, UUID ownerUuid) {
        if (skin == null || skin.isNone()) {
            // Clear assignment
            Map<Integer, MinionSkin> map = minionAssignments.get(islandKey);
            if (map != null) {
                map.remove(minionNum);
                if (map.isEmpty()) {
                    minionAssignments.remove(islandKey);
                }
            }
            // Persistence handled by caller (saveMinionDataForIsland will re-save remaining; explicit delete preferred)
            return;
        }
        if (!hasSkin(ownerUuid, skin)) return;
        minionAssignments.computeIfAbsent(islandKey, k -> new ConcurrentHashMap<>()).put(minionNum, skin);
        // Note: persistence via DB calls from MinionManager or listener when saving minion data
    }

    /** Explicit clear (removes from map and signals DB delete needed). */
    public void clearMinionAssignment(String islandKey, int minionNum) {
        Map<Integer, MinionSkin> map = minionAssignments.get(islandKey);
        if (map != null) {
            map.remove(minionNum);
            if (map.isEmpty()) {
                minionAssignments.remove(islandKey);
            }
        }
    }

    public MinionSkin getAssignedOrActiveSkin(String islandKey, int minionNum, UUID ownerUuid) {
        Map<Integer, MinionSkin> map = minionAssignments.get(islandKey);
        if (map != null && map.containsKey(minionNum)) {
            return map.get(minionNum);
        }
        return getActiveSkin(ownerUuid);
    }

    public void loadMinionAssignmentsForIsland(String islandKey) {
        Map<Integer, String> data = plugin.getDatabaseManager().loadMinionSkinAssignments(islandKey);
        Map<Integer, MinionSkin> m = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, String> e : data.entrySet()) {
            try {
                MinionSkin s = MinionSkin.valueOf(e.getValue());
                if (!s.isNone()) m.put(e.getKey(), s);
            } catch (Exception ignored) {}
        }
        if (!m.isEmpty()) minionAssignments.put(islandKey, m);
    }

    public void saveMinionAssignmentsForIsland(String islandKey) {
        Map<Integer, MinionSkin> m = minionAssignments.get(islandKey);
        if (m == null) return;
        for (Map.Entry<Integer, MinionSkin> e : m.entrySet()) {
            plugin.getDatabaseManager().saveMinionSkinAssignment(islandKey, e.getKey(), e.getValue().name());
        }
    }

    public void loadPlayer(UUID uuid) {
        Set<String> skinIds = plugin.getDatabaseManager().loadPlayerMinionSkins(uuid);
        Set<MinionSkin> skins = ConcurrentHashMap.newKeySet();
        for (String id : skinIds) {
            try {
                MinionSkin s = MinionSkin.valueOf(id);
                if (!s.isNone()) skins.add(s);
            } catch (Exception ignored) {}
        }
        ownedSkins.put(uuid, skins);
    }

    public void savePlayer(UUID uuid) {
        Set<MinionSkin> skins = ownedSkins.getOrDefault(uuid, Collections.emptySet());
        Set<String> ids = new HashSet<>();
        for (MinionSkin s : skins) ids.add(s.name());
        plugin.getDatabaseManager().savePlayerMinionSkins(uuid, ids);
    }

    private Set<String> getSkinIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (MinionSkin s : getOwnedSkins(uuid)) {
            ids.add(s.name());
        }
        return ids;
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        ownedSkins.remove(player.getUniqueId());
        activeSkin.remove(player.getUniqueId());
    }

    /**
     * Returns a cosmetic helmet ItemStack for a minion based on active skin for the owner.
     * Called from MinionManager during spawn/respawn.
     * Prefers skin override head (PLAYER_HEAD with SkullMeta), falls back to icon + CMD.
     */
    public org.bukkit.inventory.ItemStack getMinionHelmetForSkin(MinionSkin skin, org.bukkit.Material fallbackIcon) {
        if (skin == null || skin.isNone() || fallbackIcon == null) {
            return new org.bukkit.inventory.ItemStack(fallbackIcon != null ? fallbackIcon : org.bukkit.Material.COBBLESTONE);
        }

        String headOwner = skin.getEffectiveHeadOwner();
        if (headOwner != null) {
            org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            if (meta != null) {
                try {
                    meta.setOwner(headOwner); // legacy but widely supported for MHF_*
                } catch (Exception ignored) {}
                meta.setDisplayName("§6" + skin.getDisplayName());
                head.setItemMeta(meta);
            }
            return head;
        }

        // Fallback: base icon with CustomModelData for resource pack support
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(fallbackIcon);
        if (skin.getCustomModelData() > 0) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(skin.getCustomModelData());
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Light cosmetic particle accents for premium minion skins (Folia-safe).
     * Called periodically from minion task or on spawn if desired.
     */
    public void spawnMinionCosmeticParticles(ArmorStand minion, MinionSkin skin) {
        if (minion == null || !minion.isValid() || skin == null || skin.isNone()) return;

        String theme = skin.getParticleTheme();
        if (theme == null) return;

        // Use RegionScheduler / EntityScheduler wrapper via ThreadSafety for safety
        Runnable particleTask = () -> {
            if (!minion.isValid()) return;
            org.bukkit.Location loc = minion.getLocation().add(0, 0.8, 0);
            switch (theme) {
                case "dragon", "slayer" -> {
                    minion.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 3, 0.3, 0.2, 0.3, 0.01);
                    minion.getWorld().spawnParticle(Particle.SMOKE, loc, 2, 0.2, 0.1, 0.2, 0.01);
                }
                case "celestial", "allay" -> {
                    minion.getWorld().spawnParticle(Particle.END_ROD, loc, 4, 0.4, 0.3, 0.4, 0.01);
                    minion.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc, 2, 0.3, 0.2, 0.3, 0.02);
                }
                default -> {
                    minion.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 2, 0.3, 0.2, 0.3, 0.01);
                }
            }
        };

        // Schedule lightly via entity scheduler when possible
        if (threadSafety.isFolia() && minion.isValid()) {
            threadSafety.runRepeatingForEntity(minion, particleTask, 20L * 8, 20L * 12);
        } else {
            threadSafety.runRepeatingOnMainThread(particleTask, 20L * 8, 20L * 12);
        }
    }
}
