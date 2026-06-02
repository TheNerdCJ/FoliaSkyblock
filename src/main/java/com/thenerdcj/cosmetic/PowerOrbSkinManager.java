package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Power Orb Skin Manager.
 * Full manager for cosmetic Power Orb Skins (visual only).
 * Now includes real item creation + Folia-safe trigger effects.
 */
public class PowerOrbSkinManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<PowerOrbSkin>> ownedSkins = new ConcurrentHashMap<>();
    private final Map<UUID, PowerOrbSkin> activeSkin = new ConcurrentHashMap<>();

    public PowerOrbSkinManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<PowerOrbSkin> getOwnedSkins(UUID uuid) {
        return ownedSkins.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public PowerOrbSkin getActiveSkin(UUID uuid) {
        return activeSkin.getOrDefault(uuid, PowerOrbSkin.NONE);
    }

    public void unlockSkin(UUID uuid, PowerOrbSkin skin) {
        if (skin == null || skin.isNone()) return;
        Set<PowerOrbSkin> skins = getOwnedSkins(uuid);
        boolean wasNew = skins.add(skin);
        if (wasNew) {
            int xp = skin.getRarity().getXpReward() / 2;
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && plugin.getConfig().getBoolean("powerorbs.collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(player, xp);
                player.sendMessage("§a§lPower Orb Skin Collection §7» Unlocked " + skin.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = skins.size();
                if (count == 5 || count == 10) {
                    player.sendMessage("§6§l★ Orb Collector Milestone! ★ §e" + count + " unique power orb skins unlocked!");
                }
            }
            plugin.getDatabaseManager().savePlayerPowerOrbSkins(uuid, getSkinIdSet(uuid));
        }
    }

    public boolean hasSkin(UUID uuid, PowerOrbSkin skin) {
        if (skin == null || skin.isNone()) return true;
        return getOwnedSkins(uuid).contains(skin);
    }

    public void setActiveSkin(Player player, PowerOrbSkin skin) {
        if (skin != null && !skin.isNone() && !hasSkin(player.getUniqueId(), skin)) {
            player.sendMessage("§cYou have not unlocked this power orb skin.");
            return;
        }
        activeSkin.put(player.getUniqueId(), skin != null ? skin : PowerOrbSkin.NONE);
        player.sendMessage("§aPower Orb Skin " + (skin == null || skin.isNone() ? "removed" : "activated: " + skin.getDisplayName()));
    }

    public void loadPlayer(UUID uuid) {
        Set<String> skinIds = plugin.getDatabaseManager().loadPlayerPowerOrbSkins(uuid);
        Set<PowerOrbSkin> skins = ConcurrentHashMap.newKeySet();
        for (String id : skinIds) {
            try {
                PowerOrbSkin s = PowerOrbSkin.valueOf(id);
                if (!s.isNone()) skins.add(s);
            } catch (Exception ignored) {}
        }
        ownedSkins.put(uuid, skins);
    }

    public void savePlayer(UUID uuid) {
        Set<PowerOrbSkin> skins = ownedSkins.getOrDefault(uuid, Collections.emptySet());
        Set<String> ids = new HashSet<>();
        for (PowerOrbSkin s : skins) ids.add(s.name());
        plugin.getDatabaseManager().savePlayerPowerOrbSkins(uuid, ids);
    }

    private Set<String> getSkinIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (PowerOrbSkin s : getOwnedSkins(uuid)) {
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

    // ==================== POWER ORB ITEM + EFFECT SYSTEM ====================

    private static final org.bukkit.NamespacedKey ORB_SKIN_KEY = new org.bukkit.NamespacedKey("foliaskyblock", "power_orb_skin");

    /**
     * Creates a cosmetic Power Orb item for the given skin (or default if none).
     * The item carries the skin via PDC so use listeners can apply the correct visuals.
     */
    public ItemStack createOrbItem(PowerOrbSkin skin) {
        if (skin == null) skin = PowerOrbSkin.NONE;

        Material base = Material.BEACON; // good orb-like base; can be changed to END_CRYSTAL or custom later
        ItemStack item = new ItemStack(base);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(skin.isNone() ? "§bPower Orb" : skin.getRarity().getColorCode() + skin.getDisplayName());
            meta.setLore(java.util.Arrays.asList(
                    "§7Cosmetic Power Orb",
                    skin.isNone() ? "§7No skin applied" : "§7Skin: " + skin.getRarity().getColorCode() + skin.getDisplayName(),
                    "§eRight-click to release a visual burst!"
            ));
            if (skin.getCustomModelData() > 0) {
                meta.setCustomModelData(skin.getCustomModelData());
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ORB_SKIN_KEY, PersistentDataType.STRING, skin.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Gives the player a Power Orb item for the specified skin (uses their active skin if NONE passed).
     */
    public void giveOrb(Player player, PowerOrbSkin skin) {
        if (player == null) return;
        if (skin == null || skin.isNone()) {
            skin = getActiveSkin(player.getUniqueId());
        }
        ItemStack orb = createOrbItem(skin);
        player.getInventory().addItem(orb);
        player.sendMessage("§aReceived " + (skin.isNone() ? "§bPower Orb" : skin.getRarity().getColorCode() + skin.getDisplayName() + " §aPower Orb") + " §7(Right-click to activate visual effect)");
    }

    /**
     * Triggers a purely cosmetic visual effect for the given skin.
     * Fully Folia-safe: uses ThreadSafety for location-based scheduling.
     */
    public void triggerOrbEffect(Player player, PowerOrbSkin skin) {
        if (player == null || !player.isOnline() || skin == null || skin.isNone()) {
            // Default nice burst if no skin
            spawnDefaultBurst(player);
            return;
        }

        Location loc = player.getLocation().add(0, 1, 0);
        String name = skin.name();

        // Theme the burst based on skin
        Runnable effect = () -> {
            if (!player.isOnline()) return;
            Location center = player.getLocation().add(0, 1.2, 0);

            switch (name) {
                case "DISCO_BALL" -> {
                    // Colorful firework + dust
                    player.getWorld().spawnParticle(Particle.FIREWORK, center, 30, 1.2, 1.2, 1.2, 0.05);
                    for (int i = 0; i < 12; i++) {
                        Color c = Color.fromRGB(50 + (int)(Math.random()*200), 50 + (int)(Math.random()*200), 200);
                        player.getWorld().spawnParticle(Particle.DUST, center, 3, 0.8, 0.8, 0.8, 0.01, new Particle.DustOptions(c, 1.5f));
                    }
                }
                case "CELESTIAL", "GEM" -> {
                    player.getWorld().spawnParticle(Particle.END_ROD, center, 25, 1.0, 1.0, 1.0, 0.02);
                    player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, center, 15, 0.7, 0.7, 0.7, 0.1);
                }
                case "DRACONIC", "SLAYER_PRIDE" -> {
                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH, center, 20, 0.9, 0.9, 0.9, 0.01);
                    player.getWorld().spawnParticle(Particle.SMOKE, center, 12, 0.6, 0.6, 0.6, 0.02);
                }
                case "ABYSSAL" -> {
                    player.getWorld().spawnParticle(Particle.PORTAL, center, 35, 1.1, 1.1, 1.1, 0.01);
                    player.getWorld().spawnParticle(Particle.ASH, center, 18, 0.8, 0.8, 0.8, 0.02);
                }
                case "FLORAL" -> {
                    player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, center, 25, 1.0, 0.8, 1.0, 0.02);
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 8, 0.6, 0.6, 0.6, 0.01);
                }
                default -> {
                    // Generic nice burst
                    player.getWorld().spawnParticle(Particle.FIREWORK, center, 20, 0.9, 0.9, 0.9, 0.04);
                    player.getWorld().spawnParticle(Particle.DUST, center, 10, 0.7, 0.7, 0.7, 0.01, new Particle.DustOptions(Color.AQUA, 1.2f));
                }
            }
        };

        // Schedule the burst (and a follow-up for longer effects)
        threadSafety.runAtLocation(loc, effect);
        threadSafety.runOnMainThreadLater(() -> {
            if (player.isOnline()) {
                threadSafety.runAtLocation(player.getLocation().add(0, 1, 0), effect);
            }
        }, 8L);
    }

    private void spawnDefaultBurst(Player player) {
        if (player == null || !player.isOnline()) return;
        Location center = player.getLocation().add(0, 1.2, 0);
        threadSafety.runAtLocation(center, () -> {
            player.getWorld().spawnParticle(Particle.FIREWORK, center, 15, 0.8, 0.8, 0.8, 0.03);
        });
    }

    /**
     * Helper for listeners: extract the skin stored on an orb item.
     */
    public PowerOrbSkin getSkinFromOrbItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return PowerOrbSkin.NONE;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String skinName = pdc.get(ORB_SKIN_KEY, PersistentDataType.STRING);
        if (skinName == null) return PowerOrbSkin.NONE;
        try {
            return PowerOrbSkin.valueOf(skinName);
        } catch (Exception e) {
            return PowerOrbSkin.NONE;
        }
    }
}