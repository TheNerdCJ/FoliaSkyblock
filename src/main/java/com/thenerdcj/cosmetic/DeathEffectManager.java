package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Cosmetic Death / Kill Effects.
 * Follows the same patterns as HelmetSkinManager / RuneManager.
 */
public class DeathEffectManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<DeathEffect>> ownedEffects = new ConcurrentHashMap<>();
    private final Map<UUID, DeathEffect> activeDeathEffect = new ConcurrentHashMap<>();

    private static final int[] DEATH_EFFECT_MILESTONES = {3, 5, 8, 12};

    public DeathEffectManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<DeathEffect> getOwnedEffects(UUID uuid) {
        return ownedEffects.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public DeathEffect getActiveDeathEffect(UUID uuid) {
        return activeDeathEffect.getOrDefault(uuid, DeathEffect.NONE);
    }

    public void setActiveDeathEffect(Player player, DeathEffect effect) {
        if (effect == null) effect = DeathEffect.NONE;

        if (!effect.isNone() && !hasEffect(player.getUniqueId(), effect)) {
            player.sendMessage("§cYou have not unlocked this death effect.");
            return;
        }

        activeDeathEffect.put(player.getUniqueId(), effect);
        player.sendMessage("§aDeath Effect activated: " + (effect.isNone() ? "None" : effect.getDisplayName()));
    }

    public void unlockEffect(UUID uuid, DeathEffect effect) {
        if (effect == null || effect.isNone()) return;

        Set<DeathEffect> effects = getOwnedEffects(uuid);
        boolean wasNew = effects.add(effect);

        if (wasNew) {
            int xp = effect.getRarity().getXpReward() / 2;

            if (plugin.getConfig().getBoolean("death-effects.collection-xp.enabled", true)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getIslandManager().addIslandXp(player, xp);
                    player.sendMessage("§a§lDeath Effect Collection §7» Unlocked " + effect.getRarity().getColorCode() + effect.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                    int count = effects.size();
                    for (int m : DEATH_EFFECT_MILESTONES) {
                        if (count == m) {
                            player.sendMessage("§6§l★ Reaper Milestone! ★ §e" + m + " unique death effects unlocked!");
                            break;
                        }
                    }
                }
            }

            plugin.getDatabaseManager().savePlayerDeathEffects(uuid, getEffectIdSet(uuid));
        }
    }

    public boolean hasEffect(UUID uuid, DeathEffect effect) {
        if (effect == null || effect.isNone()) return true;
        return getOwnedEffects(uuid).contains(effect);
    }

    public int getEffectCollectionCount(UUID uuid) {
        return getOwnedEffects(uuid).size();
    }

    /**
     * Triggers the effect at the given location (Folia-safe via runAtLocation).
     */
    public void triggerEffect(Location loc, DeathEffect effect, Player player) {
        if (effect == null || effect.isNone() || loc == null) return;

        threadSafety.runAtLocation(loc, () -> {
            effect.trigger(loc, player);
        });
    }

    // ==================== PERSISTENCE ====================

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerDeathEffects(uuid);
        Set<DeathEffect> effects = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                DeathEffect e = DeathEffect.valueOf(id);
                if (!e.isNone()) effects.add(e);
            } catch (Exception ignored) {}
        }
        ownedEffects.put(uuid, effects);
    }

    public void savePlayer(UUID uuid) {
        Set<DeathEffect> effects = ownedEffects.getOrDefault(uuid, Collections.emptySet());
        Set<String> ids = new HashSet<>();
        for (DeathEffect e : effects) ids.add(e.name());
        plugin.getDatabaseManager().savePlayerDeathEffects(uuid, ids);
    }

    private Set<String> getEffectIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (DeathEffect e : getOwnedEffects(uuid)) ids.add(e.name());
        return ids;
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        ownedEffects.remove(player.getUniqueId());
        activeDeathEffect.remove(player.getUniqueId());
    }
}