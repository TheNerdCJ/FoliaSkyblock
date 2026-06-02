package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for Cosmetic Emotes.
 * Unlocks, collection XP, use via /emote.
 * Folia-safe particle effects.
 */
public class EmoteCosmeticManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<EmoteCosmetic>> owned = new ConcurrentHashMap<>();

    private static final int[] COLLECTION_MILESTONES = {3, 5, 8};

    public EmoteCosmeticManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<EmoteCosmetic> getOwned(UUID uuid) {
        return owned.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public void unlock(UUID uuid, EmoteCosmetic emote) {
        if (emote == null || emote.isNone()) return;
        Set<EmoteCosmetic> set = getOwned(uuid);
        boolean wasNew = set.add(emote);
        if (wasNew) {
            int xp = Math.max(10, emote.getRarity().getXpReward() / 3);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                plugin.getIslandManager().addIslandXp(p, xp);
                p.sendMessage("§a§lEmote Collection §7» Unlocked " + emote.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = set.size();
                for (int m : COLLECTION_MILESTONES) {
                    if (count == m) {
                        p.sendMessage("§6§l★ Emote Collector Milestone! ★ §e" + count + " unique emotes unlocked!");
                        break;
                    }
                }
            }
            plugin.getDatabaseManager().savePlayerEmoteCosmetics(uuid, getEmoteIdSet(uuid));
        }
    }

    public boolean has(UUID uuid, EmoteCosmetic e) {
        if (e == null || e.isNone()) return true;
        return getOwned(uuid).contains(e);
    }

    public void performEmote(Player player, EmoteCosmetic emote) {
        if (!has(player.getUniqueId(), emote)) {
            player.sendMessage("§cYou have not unlocked this emote.");
            return;
        }
        String key = emote.getEffectKey();
        Location loc = player.getLocation().add(0, 1, 0);

        // Visual effect Folia-safe
        threadSafety.runAtLocation(loc, () -> {
            switch (key) {
                case "wave":
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.5, 0.5, 0.5, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §7waves at everyone!");
                    break;
                case "cheer":
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 15, 0.6, 0.6, 0.6, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §e cheers!");
                    break;
                case "dance":
                    player.getWorld().spawnParticle(Particle.NOTE, loc, 8, 0.4, 0.4, 0.4, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §d is dancing!");
                    break;
                case "bow":
                    player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 5, 0.3, 0.3, 0.3, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §f bows respectfully.");
                    break;
                case "laugh":
                    player.getWorld().spawnParticle(Particle.HEART, loc, 6, 0.4, 0.4, 0.4, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §a laughs heartily!");
                    break;
                case "facepalm":
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 8, 0.3, 0.3, 0.3, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §7 facepalms.");
                    break;
                case "flex":
                    player.getWorld().spawnParticle(Particle.CRIT, loc, 10, 0.5, 0.5, 0.5, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §c flexes!");
                    break;
                case "salute":
                    player.getWorld().spawnParticle(Particle.FLAME, loc, 5, 0.3, 0.3, 0.3, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §6 salutes!");
                    break;
                case "spin":
                    player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 8, 0.5, 0.5, 0.5, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §5 spins dramatically!");
                    break;
                case "thumbs":
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.5, 0.5, 0.5, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §a gives a thumbs up!");
                    break;
                case "shrug":
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 6, 0.4, 0.4, 0.4, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §7 shrugs indifferently.");
                    break;
                case "clap":
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 12, 0.5, 0.5, 0.5, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §a claps enthusiastically!");
                    break;
                case "point":
                    player.getWorld().spawnParticle(Particle.CRIT, loc, 8, 0.4, 0.4, 0.4, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §e points accusingly!");
                    break;
                case "cry":
                    player.getWorld().spawnParticle(Particle.HEART, loc, 10, 0.5, 0.5, 0.5, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §9 cries sadly.");
                    break;
                case "victory":
                    player.getWorld().spawnParticle(Particle.FIREWORK, loc, 15, 0.6, 0.6, 0.6, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §6 celebrates victory!");
                    break;
                default:
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 5, 0.3, 0.3, 0.3, 0.01);
                    Bukkit.broadcastMessage(player.getName() + " §7 does an emote!");
            }
        });
    }

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerEmoteCosmetics(uuid);
        Set<EmoteCosmetic> set = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                EmoteCosmetic e = EmoteCosmetic.valueOf(id);
                if (!e.isNone()) set.add(e);
            } catch (Exception ignored) {}
        }
        owned.put(uuid, set);
        loadTriggersForPlayer(uuid);
    }

    public void savePlayer(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (EmoteCosmetic e : getOwned(uuid)) ids.add(e.name());
        plugin.getDatabaseManager().savePlayerEmoteCosmetics(uuid, ids);
    }

    private Set<String> getEmoteIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (EmoteCosmetic e : getOwned(uuid)) ids.add(e.name());
        return ids;
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
        // "join" trigger is now handled centrally via EmoteTriggerListener (more events + no double-fire).
        // triggerEmote(player, "join");  // moved to listener for consistency with kill/death
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        saveTriggersForPlayer(player.getUniqueId());
        owned.remove(player.getUniqueId());
        playerEmoteTriggers.remove(player.getUniqueId());
    }

    // Emote triggers support (per-player persisted; set via /emote trigger or GUI; "kill"/"death"/"join"/"respawn"/"levelup" etc. - advanced events)
    private final java.util.Map<UUID, java.util.Map<String, EmoteCosmetic>> playerEmoteTriggers = new java.util.concurrent.ConcurrentHashMap<>();

    public void setTrigger(UUID uuid, String triggerKey, EmoteCosmetic emote) {
        if (uuid == null) return;
        java.util.Map<String, EmoteCosmetic> map = playerEmoteTriggers.computeIfAbsent(uuid, k -> new java.util.concurrent.ConcurrentHashMap<>());
        if (emote == null || emote.isNone()) {
            map.remove(triggerKey.toLowerCase());
        } else {
            map.put(triggerKey.toLowerCase(), emote);
        }
        saveTriggersForPlayer(uuid);
    }

    public EmoteCosmetic getTrigger(UUID uuid, String triggerKey) {
        if (uuid == null) return null;
        java.util.Map<String, EmoteCosmetic> map = playerEmoteTriggers.get(uuid);
        return (map != null) ? map.get(triggerKey.toLowerCase()) : null;
    }

    public void triggerEmote(Player player, String triggerKey) {
        if (player == null) return;
        EmoteCosmetic e = getTrigger(player.getUniqueId(), triggerKey);
        if (e != null && has(player.getUniqueId(), e)) {
            performEmote(player, e);
        }
    }

    private void saveTriggersForPlayer(UUID uuid) {
        if (uuid == null || plugin.getDatabaseManager() == null) return;
        java.util.Map<String, EmoteCosmetic> map = playerEmoteTriggers.getOrDefault(uuid, java.util.Collections.emptyMap());
        java.util.Map<String, String> strMap = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, EmoteCosmetic> e : map.entrySet()) {
            strMap.put(e.getKey(), e.getValue().name());
        }
        plugin.getDatabaseManager().savePlayerEmoteTriggers(uuid, strMap);
    }

    private void loadTriggersForPlayer(UUID uuid) {
        if (uuid == null || plugin.getDatabaseManager() == null) return;
        java.util.Map<String, String> loaded = plugin.getDatabaseManager().loadPlayerEmoteTriggers(uuid);
        java.util.Map<String, EmoteCosmetic> tmap = new java.util.concurrent.ConcurrentHashMap<>();
        for (java.util.Map.Entry<String, String> entry : loaded.entrySet()) {
            try {
                EmoteCosmetic e = EmoteCosmetic.valueOf(entry.getValue());
                if (!e.isNone()) tmap.put(entry.getKey(), e);
            } catch (Exception ignored) {}
        }
        if (!tmap.isEmpty()) {
            playerEmoteTriggers.put(uuid, tmap);
        }
    }
}
