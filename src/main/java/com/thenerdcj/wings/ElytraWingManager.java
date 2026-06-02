package com.thenerdcj.wings;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.pets.PetRarity;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cosmetic Elytra Wings.
 * 
 * When a player is gliding with an elytra and has an active wing cosmetic,
 * special themed particle effects are spawned around them (Folia-safe via EntityScheduler).
 * 
 * Follows exact patterns from PetManager, PlayerTagManager, ParticleTrailManager.
 * - Rarity + Collection XP
 * - Prestige + Slayer token unlocks
 * - Owned / Active tracking
 */
public class ElytraWingManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<ElytraWing>> ownedWings = new ConcurrentHashMap<>();
    private final Map<UUID, ElytraWing> activeWings = new ConcurrentHashMap<>();

    // Collection for XP (like pets/tags)
    private final Map<UUID, Set<ElytraWing>> wingCollection = new ConcurrentHashMap<>();
    private static final int[] WING_COLLECTION_MILESTONES = {3, 5, 8, 12, 20};

    public ElytraWingManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<ElytraWing> getOwnedWings(UUID uuid) {
        return ownedWings.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public ElytraWing getActiveWing(UUID uuid) {
        return activeWings.getOrDefault(uuid, ElytraWing.NONE);
    }

    public void setActiveWing(Player player, ElytraWing wing) {
        UUID uuid = player.getUniqueId();

        if (wing == null || wing.isNone()) {
            activeWings.remove(uuid);
            player.sendMessage("§7Elytra wing cosmetic disabled.");
        } else {
            if (!getOwnedWings(uuid).contains(wing)) {
                player.sendMessage("§cYou have not unlocked this wing style yet.");
                return;
            }
            activeWings.put(uuid, wing);
            player.sendMessage("§aElytra wings set to: " + wing.getDisplayName());
        }

        savePlayer(uuid);
    }

    public void unlockWing(UUID uuid, ElytraWing wing) {
        if (wing == null || wing.isNone()) return;

        Set<ElytraWing> owned = getOwnedWings(uuid);
        boolean wasNew = owned.add(wing);

        if (wasNew) {
            awardCollectionXPIfNew(uuid, wing);
        }
    }

    public boolean hasWing(UUID uuid, ElytraWing wing) {
        if (wing == null || wing.isNone()) return true;
        return getOwnedWings(uuid).contains(wing);
    }

    // ==================== GLIDING EFFECTS (Folia Safe) ====================

    /**
     * Called when a player is gliding. Applies the cosmetic wing particle effect.
     * Enhanced with more variety per wing type + integration with active ParticleTrail.
     */
    public void applyGlidingEffect(Player player) {
        if (player == null || !player.isOnline() || !player.isGliding()) return;

        ElytraWing wing = getActiveWing(player.getUniqueId());
        if (wing == null || wing.isNone()) return;

        var loc = player.getLocation();
        Particle particle = wing.getPrimaryParticle();
        if (particle == null) particle = Particle.CLOUD;

        Vector direction = player.getLocation().getDirection().normalize().multiply(-1);

        Vector left = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(1.4);
        Vector right = left.clone().multiply(-1);

        org.bukkit.Location leftLoc = loc.clone().add(left).add(direction.multiply(0.7));
        org.bukkit.Location rightLoc = loc.clone().add(right).add(direction.multiply(0.7));

        // Wing-specific variety
        switch (wing) {
            case DRAGON, PHOENIX -> {
                player.getWorld().spawnParticle(Particle.FLAME, leftLoc, 6, 0.4, 0.4, 0.4, 0.01);
                player.getWorld().spawnParticle(Particle.FLAME, rightLoc, 6, 0.4, 0.4, 0.4, 0.01);
                player.getWorld().spawnParticle(Particle.LAVA, loc.clone().add(0, 0.3, 0), 3, 0.6, 0.3, 0.6, 0.01);
            }
            case ANGEL, FAIRY -> {
                player.getWorld().spawnParticle(Particle.END_ROD, leftLoc, 5, 0.3, 0.3, 0.3, 0.02);
                player.getWorld().spawnParticle(Particle.END_ROD, rightLoc, 5, 0.3, 0.3, 0.3, 0.02);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.8, 0), 4, 0.5, 0.5, 0.5, 0.01);
            }
            case VOID, GALAXY -> {
                player.getWorld().spawnParticle(Particle.PORTAL, leftLoc, 8, 0.5, 0.3, 0.5, 0.01);
                player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, rightLoc, 8, 0.5, 0.3, 0.5, 0.01);
            }
            default -> {
                player.getWorld().spawnParticle(particle, leftLoc, 4, 0.3, 0.3, 0.3, 0.02);
                player.getWorld().spawnParticle(particle, rightLoc, 4, 0.3, 0.3, 0.3, 0.02);
            }
        }

        // High tier extra flair
        if (wing.getRarity() == PetRarity.EPIC || wing.getRarity() == PetRarity.LEGENDARY) {
            player.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0), 3, 0.7, 0.3, 0.7, 0.01);
        }

        // === INTEGRATION WITH EXISTING TRAILS ===
        // If the player has an active ParticleTrail, layer some of its particles while gliding
        var trailManager = plugin.getParticleTrailManager();
        if (trailManager != null) {
            var activeTrail = trailManager.getActiveTrail(player.getUniqueId());
            if (activeTrail != com.thenerdcj.cosmetic.ParticleTrail.NONE) {
                // Spawn a modified version of the trail at the player's position (gliding offset)
                // This reuses the trail's particle style for nice synergy
                try {
                    // Simple reuse: spawn the primary particle of the trail behind the player
                    Particle trailParticle = activeTrail.getParticle();
                    if (trailParticle != null) {
                        org.bukkit.Location trailLoc = loc.clone().add(direction.multiply(1.5));
                        player.getWorld().spawnParticle(trailParticle, trailLoc, 2, 0.4, 0.4, 0.4, 0.01);
                    }
                } catch (Exception ignored) {
                    // Safe fallback if trail particle access changes
                }
            }
        }
    }

    // ==================== PRESTIGE + SLAYER + COLLECTION ====================

    public void grantPrestigeWingUnlocks(Player player, int newPrestigeLevel) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        Set<ElytraWing> owned = getOwnedWings(uuid);
        boolean grantedAny = false;

        for (ElytraWing wing : ElytraWing.values()) {
            if (wing.isNone()) continue;
            if (owned.contains(wing)) continue;
            if (wing.getMinPrestige() <= newPrestigeLevel && wing.getTokenCost() == 0) {
                unlockWing(uuid, wing);
                grantedAny = true;
                player.sendMessage("§d§lPrestige Wing Reward! §7Unlocked " + wing.getDisplayName());
            }
        }

        if (grantedAny) {
            savePlayer(uuid);
        }
    }

    public boolean unlockWingWithTokens(Player player, ElytraWing wing, int tokensToConsume) {
        if (player == null || wing == null || wing.isNone()) return false;
        UUID uuid = player.getUniqueId();

        if (hasWing(uuid, wing)) {
            player.sendMessage("§eYou already own this wing style.");
            return false;
        }

        Island island = plugin.getIslandManager().getIsland(uuid, player.getWorld().getEnvironment());
        int prestige = (island != null && plugin.getPrestigeManager() != null)
                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

        if (prestige < wing.getMinPrestige()) {
            player.sendMessage("§cRequires Prestige " + wing.getMinPrestige() + ".");
            return false;
        }

        if (tokensToConsume > 0) {
            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, tokensToConsume)) {
                unlockWing(uuid, wing);
                savePlayer(uuid);
                player.sendMessage("§aPurchased wing style: " + wing.getDisplayName() + " §7for " + tokensToConsume + " tokens!");
                return true;
            } else {
                player.sendMessage("§cNot enough Slayer Tokens!");
                return false;
            }
        } else {
            unlockWing(uuid, wing);
            savePlayer(uuid);
            player.sendMessage("§aUnlocked wing style (Prestige reward): " + wing.getDisplayName());
            return true;
        }
    }

    private void awardCollectionXPIfNew(UUID uuid, ElytraWing wing) {
        Set<ElytraWing> collected = wingCollection.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        if (collected.add(wing)) {
            int xp = wing.getRarity().getXpReward();

            plugin.getDatabaseManager().saveElytraWingCollectionEntry(uuid, wing.name());

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (plugin.getConfig().getBoolean("wings.collection-xp.enabled", true)) {
                    plugin.getIslandManager().addIslandXp(player, xp);
                    player.sendMessage("§a§lElytra Wing Collection §7» Unlocked " + wing.getDisplayName() +
                            " §7(§a+" + xp + " Island XP§7)");
                }
            }
        }
    }

    public int getWingCollectionCount(UUID uuid) {
        Set<ElytraWing> set = wingCollection.get(uuid);
        return set == null ? 0 : set.size();
    }

    // ==================== PERSISTENCE ====================

    public void loadPlayer(UUID uuid) {
        Set<String> ownedIds = plugin.getDatabaseManager().loadPlayerElytraWings(uuid);
        Set<ElytraWing> owned = ConcurrentHashMap.newKeySet();
        for (String id : ownedIds) {
            try {
                ElytraWing w = ElytraWing.valueOf(id);
                if (!w.isNone()) owned.add(w);
            } catch (Exception ignored) {}
        }
        ownedWings.put(uuid, owned);

        String activeId = plugin.getDatabaseManager().loadActiveElytraWing(uuid);
        if (activeId != null) {
            try {
                ElytraWing active = ElytraWing.valueOf(activeId);
                if (owned.contains(active)) {
                    activeWings.put(uuid, active);
                }
            } catch (Exception ignored) {}
        }

        // Load collection
        Set<String> coll = plugin.getDatabaseManager().loadElytraWingCollection(uuid);
        Set<ElytraWing> collected = ConcurrentHashMap.newKeySet();
        for (String id : coll) {
            try { collected.add(ElytraWing.valueOf(id)); } catch (Exception ignored) {}
        }
        wingCollection.put(uuid, collected);
    }

    public void savePlayer(UUID uuid) {
        Set<ElytraWing> owned = ownedWings.getOrDefault(uuid, Collections.emptySet());
        Set<String> ownedIds = new HashSet<>();
        for (ElytraWing w : owned) ownedIds.add(w.name());

        ElytraWing active = activeWings.getOrDefault(uuid, ElytraWing.NONE);
        String activeId = active.isNone() ? null : active.name();

        plugin.getDatabaseManager().savePlayerElytraWings(uuid, ownedIds, activeId);
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        savePlayer(uuid);
        ownedWings.remove(uuid);
        activeWings.remove(uuid);
        wingCollection.remove(uuid);
    }

    // ==================== CUSTOM ELYTRA ITEMS WITH MODEL DATA ====================

    /**
     * Creates a cosmetic elytra item for the given wing using CustomModelData.
     * Requires a matching resource pack on the client for visual models.
     * The item stores the wing ID in PDC so the server can identify it.
     */
    public ItemStack getCosmeticElytraItem(ElytraWing wing) {
        if (wing == null || wing.isNone()) {
            return new ItemStack(Material.ELYTRA);
        }

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();

        // CustomModelData: base 10000 + wing ordinal for easy mapping in resource pack
        int modelData = 10000 + wing.ordinal();
        meta.setCustomModelData(modelData);

        // Store wing for server-side logic
        NamespacedKey key = new NamespacedKey(plugin, "elytra_wing_id");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, wing.name());

        meta.setDisplayName("§b" + wing.getDisplayName() + " §7(Cosmetic Elytra)");
        meta.setLore(java.util.Arrays.asList(
            "§7" + wing.getDescription(),
            "§7Rarity: " + wing.getRarity().getColorCode() + wing.getRarity().getDisplayName(),
            "",
            "§8Requires resource pack for full visuals"
        ));

        // Make it "special" - unbreakable for cosmetics
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            damageable.setUnbreakable(true);
        }

        elytra.setItemMeta(meta);
        return elytra;
    }

    /**
     * Checks if an ItemStack is a cosmetic elytra for a specific wing.
     */
    public ElytraWing getWingFromElytraItem(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA || !item.hasItemMeta()) return ElytraWing.NONE;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "elytra_wing_id");
        String wingName = pdc.get(key, PersistentDataType.STRING);

        if (wingName == null) return ElytraWing.NONE;

        try {
            return ElytraWing.valueOf(wingName);
        } catch (Exception e) {
            return ElytraWing.NONE;
        }
    }

    /**
     * Replaces the player's elytra with the cosmetic version if they have an active wing.
     * Call this on equip/glide start events.
     */
    public void applyCosmeticElytra(Player player) {
        if (player == null || !player.isOnline()) return;

        ElytraWing active = getActiveWing(player.getUniqueId());
        if (active.isNone()) return;

        // Check if player has a vanilla elytra equipped or in inventory
        ItemStack chest = player.getInventory().getChestplate();
        if (chest != null && chest.getType() == Material.ELYTRA) {
            ElytraWing current = getWingFromElytraItem(chest);
            if (current == ElytraWing.NONE) {
                // Replace with cosmetic version
                ItemStack cosmetic = getCosmeticElytraItem(active);
                // Preserve some durability if possible, but for cosmetics often we give fresh
                player.getInventory().setChestplate(cosmetic);
            }
        }
    }
}
