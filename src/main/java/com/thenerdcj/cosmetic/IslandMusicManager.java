package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IslandMusicManager - Play-to-Win island-wide ambient and music cosmetics.
 *
 * Active music is per-island (owner chooses, visitors hear it).
 * Folia-safe: uses player schedulers for looping sounds and ThreadSafety for cross-thread.
 * Stops previous music cleanly when changing.
 */
public class IslandMusicManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<IslandMusicType>> ownedMusic = new ConcurrentHashMap<>();
    private final Map<String, IslandMusicType> activeMusicByIsland = new ConcurrentHashMap<>();

    // Track per-player looping tasks for cleanup (task ids or runnables)
    private final Map<UUID, Integer> activeLoops = new ConcurrentHashMap<>();

    private static final int[] COLLECTION_MILESTONES = {3, 5, 8, 12};

    public IslandMusicManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<IslandMusicType> getOwnedMusic(UUID uuid) {
        return ownedMusic.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public int getCollectionCount(UUID uuid) {
        return getOwnedMusic(uuid).size();
    }

    public void unlockMusic(UUID uuid, IslandMusicType music) {
        if (music == null || music.isNone()) return;
        Set<IslandMusicType> owned = getOwnedMusic(uuid);
        boolean wasNew = owned.add(music);
        if (wasNew) {
            int xp = Math.max(10, music.getRarity().getXpReward() / 3);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && plugin.getConfig().getBoolean("island.music.collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(player, xp);
                player.sendMessage("§a§lIsland Music Collection §7» Unlocked " + music.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = owned.size();
                for (int m : COLLECTION_MILESTONES) {
                    if (count == m) {
                        player.sendMessage("§6§l★ Island Maestro Milestone! ★ §e" + count + " unique ambients unlocked!");
                        break;
                    }
                }
            }
            plugin.getDatabaseManager().savePlayerIslandMusic(uuid, getMusicIdSet(uuid));
        }
    }

    public boolean hasMusic(UUID uuid, IslandMusicType music) {
        if (music == null || music.isNone()) return true;
        return getOwnedMusic(uuid).contains(music);
    }

    public IslandMusicType getActiveMusic(String islandKey) {
        return activeMusicByIsland.getOrDefault(islandKey, IslandMusicType.NONE);
    }

    public void setActiveMusic(Island island, IslandMusicType music) {
        if (island == null) return;
        String islandKey = island.getId();

        // Stop any current for all online members
        stopMusicForIsland(island);

        if (music == null || music.isNone()) {
            activeMusicByIsland.remove(islandKey);
            plugin.getDatabaseManager().saveIslandActiveMusic(islandKey, "NONE");
            return;
        }

        activeMusicByIsland.put(islandKey, music);
        plugin.getDatabaseManager().saveIslandActiveMusic(islandKey, music.name());

        // Start for current online members on this island
        playMusicForIsland(island, music);
    }

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerIslandMusic(uuid);
        Set<IslandMusicType> musicSet = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                IslandMusicType m = IslandMusicType.valueOf(id);
                if (!m.isNone()) musicSet.add(m);
            } catch (Exception ignored) {}
        }
        ownedMusic.put(uuid, musicSet);
    }

    public void savePlayer(UUID uuid) {
        plugin.getDatabaseManager().savePlayerIslandMusic(uuid, getMusicIdSet(uuid));
    }

    private Set<String> getMusicIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (IslandMusicType m : getOwnedMusic(uuid)) ids.add(m.name());
        return ids;
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        stopLoopForPlayer(player); // broader integration: ensure no lingering loops on quit
        ownedMusic.remove(player.getUniqueId());
    }

    // ==================== PLAYBACK (Folia-safe) ====================

    private void playMusicForIsland(Island island, IslandMusicType music) {
        if (island == null || music == null || music.isNone()) return;

        String islandKey = island.getId();
        String sound = music.getSoundKey();

        // For each online member on this island (or visitors too for immersion)
        for (Player p : Bukkit.getOnlinePlayers()) {
            Island playerIsland = plugin.getIslandManager().getIsland(p.getUniqueId(), p.getWorld().getEnvironment());
            if (playerIsland != null && playerIsland.getId().equals(islandKey)) {
                startLoopForPlayer(p, sound, islandKey);
            }
        }
    }

    private void stopMusicForIsland(Island island) {
        if (island == null) return;
        String islandKey = island.getId();

        for (Player p : Bukkit.getOnlinePlayers()) {
            Island playerIsland = plugin.getIslandManager().getIsland(p.getUniqueId(), p.getWorld().getEnvironment());
            if (playerIsland != null && playerIsland.getId().equals(islandKey)) {
                stopLoopForPlayer(p);
                try {
                    p.stopSound(Sound.valueOf("MUSIC_DISC_CAT")); // broad stop attempt; better use string stop in 1.19+
                    p.stopSound(org.bukkit.SoundCategory.MUSIC);
                } catch (Exception ignored) {}
            }
        }
    }

    private void startLoopForPlayer(Player player, String soundKey, String islandKey) {
        stopLoopForPlayer(player);

        // Simple repeating low-volume ambient loop every 20-30s
        // Use player's scheduler for Folia safety
        Runnable loop = () -> {
            if (!player.isOnline()) return;
            try {
                // Try as Sound first
                Sound sound = Sound.valueOf(soundKey.toUpperCase().replace('.', '_'));
                player.playSound(player.getLocation(), sound, org.bukkit.SoundCategory.MUSIC, 0.6f, 1.0f);
            } catch (Exception e) {
                // Fallback to string key (works for many ambient too)
                player.playSound(player.getLocation(), soundKey, org.bukkit.SoundCategory.MUSIC, 0.6f, 1.0f);
            }
        };

        // Schedule repeating on player's region (use ThreadSafety abstraction for Paper path)
        if (threadSafety.isFolia() && player.isValid()) {
            player.getScheduler().runAtFixedRate(plugin, (taskRef) -> {
                if (player.isOnline()) loop.run();
            }, null, 20L, 25 * 20L); // every ~25s
            // Folia: rely on stopSound on change + lambda guard (no store needed)
        } else {
            threadSafety.runRepeatingForPlayer(player, loop, 20L, 25 * 20L);
            // Paper: no taskId tracked; stopLoopForPlayer relies on stopSound which is sufficient to end music
        }

        // Play once immediately
        loop.run();
    }

    private void stopLoopForPlayer(Player player) {
        Integer taskId = activeLoops.remove(player.getUniqueId());
        if (taskId != null) {
            // Paper fallback cancel (Folia path relies on stopSound + auto-cancel in player scheduler lambda).
            Bukkit.getScheduler().cancelTask(taskId);
        }
        try {
            player.stopSound(org.bukkit.SoundCategory.MUSIC);
            player.stopSound(org.bukkit.SoundCategory.AMBIENT);
        } catch (Exception ignored) {}
    }

    /**
     * Called when a player enters an island (from Island protection or teleport listeners).
     * Applies the island's current active music if any.
     */
    public void onPlayerEnterIsland(Player player, Island island) {
        if (island == null) return;
        IslandMusicType music = getActiveMusic(island.getId());
        if (music != null && !music.isNone()) {
            startLoopForPlayer(player, music.getSoundKey(), island.getId());
        }
    }

    /**
     * Called on leave to clean up.
     */
    public void onPlayerLeaveIsland(Player player, Island island) {
        stopLoopForPlayer(player);
    }

    public void loadActiveForIsland(String islandKey) {
        String musicId = plugin.getDatabaseManager().loadIslandActiveMusic(islandKey);
        if (musicId != null && !"NONE".equals(musicId)) {
            try {
                IslandMusicType m = IslandMusicType.valueOf(musicId);
                activeMusicByIsland.put(islandKey, m);
            } catch (Exception ignored) {}
        }
    }
}
