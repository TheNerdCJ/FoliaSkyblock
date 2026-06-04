package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for Light Accessory Cosmetics (floating items around player).
 * Uses ItemDisplay for visual "accessories" + themed particles.
 * Folia-safe via player scheduler and ThreadSafety.
 * Purely cosmetic.
 */
public class AccessoryCosmeticManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<AccessoryCosmetic>> owned = new ConcurrentHashMap<>();
    private final Map<UUID, AccessoryCosmetic> active = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.entity.Entity> activeDisplay = new ConcurrentHashMap<>();

    private static final int[] COLLECTION_MILESTONES = {3, 5, 8, 12};

    public AccessoryCosmeticManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<AccessoryCosmetic> getOwned(UUID uuid) {
        return owned.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public AccessoryCosmetic getActive(UUID uuid) {
        return active.getOrDefault(uuid, AccessoryCosmetic.NONE);
    }

    public void unlock(UUID uuid, AccessoryCosmetic accessory) {
        if (accessory == null || accessory.isNone()) return;
        Set<AccessoryCosmetic> set = getOwned(uuid);
        boolean wasNew = set.add(accessory);
        if (wasNew) {
            int xp = Math.max(15, accessory.getRarity().getXpReward() / 3);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && plugin.getConfig().getBoolean("accessories.collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(p, xp);
                p.sendMessage("§a§lAccessory Collection §7» Unlocked " + accessory.getRarity().getColorCode() + accessory.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = set.size();
                for (int m : COLLECTION_MILESTONES) {
                    if (count == m) {
                        p.sendMessage("§6§l★ Accessory Collector Milestone! ★ §e" + count + " unique accessories unlocked!");
                        break;
                    }
                }
            }
            plugin.getDatabaseManager().savePlayerAccessories(uuid, getIdSet(uuid));
        }
    }

    public boolean has(UUID uuid, AccessoryCosmetic a) {
        if (a == null || a.isNone()) return true;
        return getOwned(uuid).contains(a);
    }

    public void setActive(Player player, AccessoryCosmetic accessory) {
        if (accessory == null) accessory = AccessoryCosmetic.NONE;
        if (!accessory.isNone() && !has(player.getUniqueId(), accessory)) {
            player.sendMessage("§cYou have not unlocked this accessory.");
            return;
        }

        removeActive(player);
        active.put(player.getUniqueId(), accessory);

        if (!accessory.isNone()) {
            spawnAccessory(player, accessory);
        }
    }

    private void spawnAccessory(Player player, AccessoryCosmetic accessory) {
        Runnable task = () -> {
            if (!player.isOnline() || !active.containsKey(player.getUniqueId())) return;
            updateAccessoryDisplay(player, accessory);
        };

        if (threadSafety.isFolia() && player.isValid()) {
            player.getScheduler().runAtFixedRate(plugin, (scheduledTask) -> {
                if (!player.isOnline() || !active.containsKey(player.getUniqueId())) {
                    scheduledTask.cancel();
                    return;
                }
                updateAccessoryDisplay(player, accessory);
            }, null, 1L, 20L);
        } else {
            threadSafety.runRepeatingOnMainThread(task, 1L, 20L);
        }
    }

    private void updateAccessoryDisplay(Player player, AccessoryCosmetic accessory) {
        Location loc = player.getLocation().add(0, 1.8, 0); // around chest/head height

        org.bukkit.entity.Entity old = activeDisplay.remove(player.getUniqueId());
        if (old != null && old.isValid()) old.remove();

        String type = accessory.getAccessoryType();

        // Use ItemDisplay for "item" feel
        ItemDisplay display = player.getWorld().spawn(loc, ItemDisplay.class);

        ItemStack item = new ItemStack(Material.NETHER_STAR); // base, CMD for visuals via pack
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Set CMD based on type for resource pack support (like other cosmetics)
            int cmd = 7000; // base range
            if ("star".equals(type)) cmd = 7001;
            else if ("orb".equals(type)) cmd = 7002;
            else if ("sword".equals(type)) cmd = 7003;
            else if ("balloon".equals(type)) cmd = 7004;
            else if ("companion".equals(type)) cmd = 7005;
            else if ("crown".equals(type)) cmd = 7006;
            else if ("dragon".equals(type)) cmd = 7007;
            else if ("rainbow".equals(type)) cmd = 7008;
            else if ("lantern".equals(type)) cmd = 7009;
            else if ("shield".equals(type)) cmd = 7010;
            else if ("compass".equals(type)) cmd = 7011;
            else if ("crystal".equals(type)) cmd = 7012;
            else if ("feather".equals(type)) cmd = 7013;
            else if ("key".equals(type)) cmd = 7014;
            else if ("gem".equals(type)) cmd = 7015;
            else if ("book".equals(type)) cmd = 7016;
            else if ("rune".equals(type)) cmd = 7017;
            meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        display.setItemStack(item);

        // Position/scale/rotation per type for "floating" variety
        float scale = 0.6f;
        float yOff = 0f;
        float xOff = 0.8f;
        if ("star".equals(type) || "rainbow".equals(type)) {
            scale = 0.5f; xOff = 1.0f; yOff = 0.3f;
        } else if ("sword".equals(type)) {
            scale = 0.7f; xOff = 0.9f; yOff = -0.2f;
        } else if ("balloon".equals(type)) {
            scale = 0.8f; xOff = 0.6f; yOff = 0.8f;
        } else if ("dragon".equals(type) || "companion".equals(type)) {
            scale = 0.65f; xOff = 1.1f; yOff = 0.1f;
        } else if ("lantern".equals(type)) {
            scale = 0.7f; xOff = 0.7f; yOff = 0.5f;
        } else if ("shield".equals(type)) {
            scale = 0.65f; xOff = 1.0f; yOff = -0.1f;
        } else if ("compass".equals(type)) {
            scale = 0.55f; xOff = 0.9f; yOff = 0.4f;
        } else if ("crystal".equals(type)) {
            scale = 0.6f; xOff = 1.05f; yOff = 0.2f;
        } else if ("feather".equals(type)) {
            scale = 0.75f; xOff = 0.85f; yOff = 0.6f;
        } else if ("key".equals(type)) {
            scale = 0.5f; xOff = 0.95f; yOff = 0.3f;
        } else if ("gem".equals(type)) {
            scale = 0.65f; xOff = 1.0f; yOff = 0.25f;
        } else if ("mystic_orb".equals(type)) {
            scale = 0.6f; xOff = 0.9f; yOff = 0.4f;
        } else if ("slayer_trophy".equals(type)) {
            scale = 0.55f; xOff = 0.85f; yOff = 0.5f;
        } else if ("book".equals(type)) {
            scale = 0.55f; xOff = 0.9f; yOff = 0.35f;
        } else if ("rune".equals(type)) {
            scale = 0.6f; xOff = 1.0f; yOff = 0.2f;
        }
        display.setTransformation(new Transformation(
            new Vector3f(xOff, yOff, 0),
            new AxisAngle4f(),
            new Vector3f(scale, scale, scale),
            new AxisAngle4f()
        ));
        display.setBillboard(ItemDisplay.Billboard.CENTER);

        // Themed particles
        if ("star".equals(type) || "rainbow".equals(type)) {
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.3, 0.3, 0.3, 0.01);
        } else if ("orb".equals(type)) {
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 4, 0.4, 0.2, 0.4, 0.01);
        } else if ("sword".equals(type)) {
            player.getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.3, 0.2, 0.3, 0.01);
        } else if ("balloon".equals(type)) {
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 2, 0.2, 0.2, 0.2, 0.01);
        } else if ("dragon".equals(type)) {
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 3, 0.3, 0.2, 0.3, 0.01);
        } else if ("crown".equals(type)) {
            player.getWorld().spawnParticle(Particle.CRIT, loc, 4, 0.3, 0.2, 0.3, 0.01);
        } else if ("lantern".equals(type)) {
            player.getWorld().spawnParticle(Particle.FLAME, loc, 4, 0.3, 0.2, 0.3, 0.01);
        } else if ("shield".equals(type)) {
            player.getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.3, 0.2, 0.3, 0.01);
        } else if ("compass".equals(type)) {
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 3, 0.25, 0.25, 0.25, 0.01);
        } else if ("crystal".equals(type)) {
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 5, 0.3, 0.2, 0.3, 0.01);
            player.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.2, 0.2, 0.2, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 100, 255), 0.8f));
        } else if ("feather".equals(type)) {
            player.getWorld().spawnParticle(Particle.CLOUD, loc, 2, 0.2, 0.15, 0.2, 0.01);
            player.getWorld().spawnParticle(Particle.CRIT, loc, 2, 0.2, 0.1, 0.2, 0.01);
        } else if ("key".equals(type)) {
            player.getWorld().spawnParticle(Particle.CRIT, loc, 4, 0.3, 0.2, 0.3, 0.01);
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 2, 0.2, 0.1, 0.2, 0.01);
        } else if ("gem".equals(type)) {
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 4, 0.3, 0.2, 0.3, 0.01);
            player.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.2, 0.2, 0.2, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 105, 180), 0.9f));
        } else if ("mystic_orb".equals(type)) {
            player.getWorld().spawnParticle(Particle.PORTAL, loc, 5, 0.3, 0.3, 0.3, 0.01);
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 3, 0.2, 0.2, 0.2, 0.01);
        } else if ("slayer_trophy".equals(type)) {
            player.getWorld().spawnParticle(Particle.FLAME, loc, 4, 0.25, 0.2, 0.25, 0.01);
            player.getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.2, 0.1, 0.2, 0.01);
        } else if ("book".equals(type)) {
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 4, 0.3, 0.25, 0.3, 0.01);
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 2, 0.2, 0.2, 0.2, 0.01);
        } else if ("rune".equals(type)) {
            player.getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.25, 0.2, 0.25, 0.01);
            player.getWorld().spawnParticle(Particle.PORTAL, loc, 3, 0.2, 0.15, 0.2, 0.01);
        }

        activeDisplay.put(player.getUniqueId(), display);
    }

    public void removeActive(Player player) {
        active.remove(player.getUniqueId());
        org.bukkit.entity.Entity e = activeDisplay.remove(player.getUniqueId());
        if (e != null && e.isValid()) e.remove();
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        removeActive(player);
        owned.remove(player.getUniqueId());
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
        AccessoryCosmetic current = getActive(player.getUniqueId());
        if (!current.isNone()) {
            spawnAccessory(player, current);
        }
    }

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerAccessories(uuid);
        Set<AccessoryCosmetic> set = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                AccessoryCosmetic a = AccessoryCosmetic.valueOf(id);
                if (!a.isNone()) set.add(a);
            } catch (Exception ignored) {}
        }
        owned.put(uuid, set);
    }

    public void savePlayer(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (AccessoryCosmetic a : getOwned(uuid)) ids.add(a.name());
        plugin.getDatabaseManager().savePlayerAccessories(uuid, ids);
    }

    private Set<String> getIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (AccessoryCosmetic a : getOwned(uuid)) ids.add(a.name());
        return ids;
    }
}
