package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stub Manager for Backpack Skins (exploration phase).
 * 
 * Follows the pattern of other cosmetic managers.
 * Full implementation would require a Backpack system first.
 */
public class BackpackSkinManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<BackpackSkin>> ownedSkins = new ConcurrentHashMap<>();
    private final Map<UUID, BackpackSkin> activeSkin = new ConcurrentHashMap<>();

    public BackpackSkinManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<BackpackSkin> getOwnedSkins(UUID uuid) {
        return ownedSkins.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public BackpackSkin getActiveSkin(UUID uuid) {
        return activeSkin.getOrDefault(uuid, BackpackSkin.NONE);
    }

    public void unlockSkin(UUID uuid, BackpackSkin skin) {
        if (skin == null || skin.isNone()) return;
        Set<BackpackSkin> skins = getOwnedSkins(uuid);
        boolean wasNew = skins.add(skin);
        if (wasNew) {
            int xp = skin.getRarity().getXpReward() / 2;
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && plugin.getConfig().getBoolean("backpacks.collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(player, xp);
                player.sendMessage("§a§lBackpack Skin Collection §7» Unlocked " + skin.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = skins.size();
                if (count == 5 || count == 10) {
                    player.sendMessage("§6§l★ Backpack Collector Milestone! ★ §e" + count + " unique backpack skins unlocked!");
                }
            }
            plugin.getDatabaseManager().savePlayerBackpackSkins(uuid, getSkinIdSet(uuid));
        }
    }

    public boolean hasSkin(UUID uuid, BackpackSkin skin) {
        if (skin == null || skin.isNone()) return true;
        return getOwnedSkins(uuid).contains(skin);
    }

    public void setActiveSkin(Player player, BackpackSkin skin) {
        if (skin != null && !skin.isNone() && !hasSkin(player.getUniqueId(), skin)) {
            player.sendMessage("§cYou have not unlocked this backpack skin.");
            return;
        }
        activeSkin.put(player.getUniqueId(), skin != null ? skin : BackpackSkin.NONE);
        player.sendMessage("§aBackpack Skin " + (skin == null || skin.isNone() ? "removed" : "activated: " + skin.getDisplayName()));

        // Note: Actual application to a backpack item (via PDC + CustomModelData) 
        // will be implemented once a Backpack system exists. For now, the active skin is persisted.
    }

    public void loadPlayer(UUID uuid) {
        Set<String> skinIds = plugin.getDatabaseManager().loadPlayerBackpackSkins(uuid);
        Set<BackpackSkin> skins = ConcurrentHashMap.newKeySet();
        for (String id : skinIds) {
            try {
                BackpackSkin s = BackpackSkin.valueOf(id);
                if (!s.isNone()) skins.add(s);
            } catch (Exception ignored) {}
        }
        ownedSkins.put(uuid, skins);
    }

    public void savePlayer(UUID uuid) {
        Set<BackpackSkin> skins = ownedSkins.getOrDefault(uuid, Collections.emptySet());
        Set<String> ids = new HashSet<>();
        for (BackpackSkin s : skins) ids.add(s.name());
        plugin.getDatabaseManager().savePlayerBackpackSkins(uuid, ids);
    }

    private Set<String> getSkinIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (BackpackSkin s : getOwnedSkins(uuid)) {
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
}