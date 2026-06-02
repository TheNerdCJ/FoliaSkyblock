package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Cosmetic Helmet Skins.
 * 
 * Follows the exact same patterns as PetSkinManager / RuneManager.
 * Purely cosmetic — no gameplay power.
 */
public class HelmetSkinManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<HelmetSkin>> ownedSkins = new ConcurrentHashMap<>();
    private final Map<UUID, HelmetSkin> activeHelmetSkin = new ConcurrentHashMap<>();

    private static final int[] HELMET_SKIN_MILESTONES = {3, 5, 8, 12, 20};

    public HelmetSkinManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<HelmetSkin> getOwnedSkins(UUID uuid) {
        return ownedSkins.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public HelmetSkin getActiveHelmetSkin(UUID uuid) {
        return activeHelmetSkin.getOrDefault(uuid, HelmetSkin.NONE);
    }

    public void setActiveHelmetSkin(Player player, HelmetSkin skin) {
        UUID uuid = player.getUniqueId();
        if (skin == null) skin = HelmetSkin.NONE;

        if (!skin.isNone() && !hasSkin(uuid, skin)) {
            player.sendMessage("§cYou have not unlocked this helmet skin.");
            return;
        }

        activeHelmetSkin.put(uuid, skin);

        // Apply to currently worn helmet if present (Folia-safe)
        final ItemStack helmet = player.getInventory().getHelmet();
        final HelmetSkin finalSkin = skin;
        if (helmet != null && helmet.getType() != org.bukkit.Material.AIR) {
            threadSafety.runOnMainThread(() -> {
                if (finalSkin.isNone()) {
                    HelmetSkin.removeSkin(helmet);
                } else {
                    finalSkin.applyToHelmet(helmet);
                }
                player.getInventory().setHelmet(helmet);
            });
        }

        player.sendMessage("§aHelmet Skin " + (skin.isNone() ? "removed" : "activated: " + skin.getDisplayName()));
    }

    public void unlockSkin(UUID uuid, HelmetSkin skin) {
        if (skin == null || skin.isNone()) return;

        Set<HelmetSkin> skins = getOwnedSkins(uuid);
        boolean wasNew = skins.add(skin);

        if (wasNew) {
            int xp = skin.getRarity().getXpReward() / 2;

            if (plugin.getConfig().getBoolean("helmets.collection-xp.enabled", true)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getIslandManager().addIslandXp(player, xp);
                    player.sendMessage("§a§lHelmet Skin Collection §7» Unlocked " + skin.getRarity().getColorCode() + skin.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                    int count = skins.size();
                    for (int m : HELMET_SKIN_MILESTONES) {
                        if (count == m) {
                            player.sendMessage("§6§l★ Helmet Stylist Milestone! ★ §e" + m + " unique helmet skins unlocked!");
                            break;
                        }
                    }
                }
            }

            plugin.getDatabaseManager().savePlayerHelmetSkins(uuid, getSkinIdSet(uuid));
        }
    }

    public boolean hasSkin(UUID uuid, HelmetSkin skin) {
        if (skin == null || skin.isNone()) return true;
        return getOwnedSkins(uuid).contains(skin);
    }

    public int getSkinCollectionCount(UUID uuid) {
        return getOwnedSkins(uuid).size();
    }

    // ==================== ITEM APPLICATION ====================

    public ItemStack applySkinToHelmet(ItemStack helmet, HelmetSkin skin) {
        if (helmet == null || skin == null) return helmet;
        if (skin.isNone()) {
            return HelmetSkin.removeSkin(helmet);
        }
        return skin.applyToHelmet(helmet);
    }

    public HelmetSkin getSkinFromItem(ItemStack helmet) {
        if (helmet == null || !helmet.hasItemMeta()) return HelmetSkin.NONE;
        // In a full implementation we would read from PDC.
        // For now we return NONE (skins are tracked via active + applied on equip).
        return HelmetSkin.NONE;
    }

    // ==================== PERSISTENCE ====================

    public void loadPlayer(UUID uuid) {
        Set<String> skinIds = plugin.getDatabaseManager().loadPlayerHelmetSkins(uuid);
        Set<HelmetSkin> skins = ConcurrentHashMap.newKeySet();
        for (String id : skinIds) {
            try {
                HelmetSkin s = HelmetSkin.valueOf(id);
                if (!s.isNone()) skins.add(s);
            } catch (Exception ignored) {}
        }
        ownedSkins.put(uuid, skins);
    }

    public void savePlayer(UUID uuid) {
        Set<HelmetSkin> skins = ownedSkins.getOrDefault(uuid, Collections.emptySet());
        Set<String> ids = new HashSet<>();
        for (HelmetSkin s : skins) ids.add(s.name());
        plugin.getDatabaseManager().savePlayerHelmetSkins(uuid, ids);
    }

    private Set<String> getSkinIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (HelmetSkin s : getOwnedSkins(uuid)) ids.add(s.name());
        return ids;
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        ownedSkins.remove(player.getUniqueId());
        activeHelmetSkin.remove(player.getUniqueId());
    }
}