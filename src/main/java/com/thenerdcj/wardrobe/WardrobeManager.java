package com.thenerdcj.wardrobe;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Core manager for the Wardrobe system.
 *
 * Features based on community research:
 * - Supports both Armor and Equipment presets (highly requested)
 * - Generous base slots (9 per category) to avoid P2W complaints
 * - In-memory caching with async DB persistence
 * - Safe equipping using ThreadSafety for Folia
 * - Foundation ready for future: custom icons/names, upgrade-based slot expansion,
 *   and (very carefully) auto-rules.
 */
public class WardrobeManager {

    // Base + upgrade scaling happens in IslandUpgradeManager
    public static final int DEFAULT_MAX_SLOTS = 9;

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    // Per-player caches: slot -> WardrobeSet
    private final Map<UUID, Map<Integer, WardrobeSet>> armorPresets = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, WardrobeSet>> equipmentPresets = new ConcurrentHashMap<>();

    // Light collection tracking for wardrobe equipment XP (forum-inspired "own 10 belts" style)
    // Per-player set of unique equipment materials they have saved in wardrobe
    private final Map<UUID, Set<Material>> wardrobeEquipmentCollection = new ConcurrentHashMap<>();

    public WardrobeManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    // ==================== DATA ACCESS ====================

    public Map<Integer, WardrobeSet> getArmorPresets(UUID uuid) {
        return armorPresets.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }

    public Map<Integer, WardrobeSet> getEquipmentPresets(UUID uuid) {
        return equipmentPresets.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }

    public WardrobeSet getArmorSet(UUID uuid, int slot) {
        if (slot < 0 || slot >= DEFAULT_MAX_SLOTS) return null;
        return getArmorPresets(uuid).get(slot);
    }

    public WardrobeSet getEquipmentSet(UUID uuid, int slot) {
        if (slot < 0 || slot >= DEFAULT_MAX_SLOTS) return null;
        return getEquipmentPresets(uuid).get(slot);
    }

    public void saveArmorSet(UUID uuid, int slot, WardrobeSet set) {
        if (slot < 0 || slot >= DEFAULT_MAX_SLOTS || set == null) return;
        getArmorPresets(uuid).put(slot, set);
        plugin.getDatabaseManager().saveWardrobeSet(uuid, slot, "ARMOR", set);
    }

    public void saveEquipmentSet(UUID uuid, int slot, WardrobeSet set) {
        if (slot < 0 || slot >= DEFAULT_MAX_SLOTS || set == null) return;
        getEquipmentPresets(uuid).put(slot, set);
        plugin.getDatabaseManager().saveWardrobeSet(uuid, slot, "EQUIPMENT", set);

        // === Light Wardrobe Equipment Collection XP ===
        // Awards small Island XP the first time a player saves a unique equipment material.
        // This is a very light tie-in inspired by community requests ("own 10 belts" style).
        awardCollectionXPIfNew(uuid, set.getEquipment());
    }

    public void clearArmorSet(UUID uuid, int slot) {
        getArmorPresets(uuid).remove(slot);
        plugin.getDatabaseManager().deleteWardrobeSet(uuid, slot, "ARMOR");
    }

    public void clearEquipmentSet(UUID uuid, int slot) {
        getEquipmentPresets(uuid).remove(slot);
        plugin.getDatabaseManager().deleteWardrobeSet(uuid, slot, "EQUIPMENT");
    }

    /**
     * Renames an existing set (armor or equipment).
     */
    public void renameSet(UUID uuid, int slot, String setType, String newName) {
        Map<Integer, WardrobeSet> presets = "ARMOR".equalsIgnoreCase(setType)
                ? getArmorPresets(uuid)
                : getEquipmentPresets(uuid);

        WardrobeSet set = presets.get(slot);
        if (set != null) {
            set.setName(newName);
            if ("ARMOR".equalsIgnoreCase(setType)) {
                saveArmorSet(uuid, slot, set);
            } else {
                saveEquipmentSet(uuid, slot, set);
            }
        }
    }

    // ==================== EQUIP LOGIC (Folia-safe) ====================

    /**
     * Equips a full armor + equipment loadout.
     * Runs the actual inventory mutation on the main thread via ThreadSafety.
     */
    public void equipSet(Player player, int armorSlot, int equipmentSlot) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        WardrobeSet armorSet = getArmorSet(uuid, armorSlot);
        WardrobeSet equipSet = getEquipmentSet(uuid, equipmentSlot);

        threadSafety.runOnMainThread(() -> {
            PlayerInventory inv = player.getInventory();

            if (armorSet != null) {
                ItemStack[] armor = armorSet.getArmor();
                inv.setHelmet(armor[0]);
                inv.setChestplate(armor[1]);
                inv.setLeggings(armor[2]);
                inv.setBoots(armor[3]);
            }

            if (equipSet != null) {
                // Improved equipment equipping: place pieces at the end of the player's inventory
                // (logical "extra storage" spots) rather than just dumping at the beginning.
                ItemStack[] eq = equipSet.getEquipment();
                for (ItemStack piece : eq) {
                    if (piece != null && !piece.getType().isAir()) {
                        placeItemAtEndOfInventory(player, piece.clone());
                    }
                }
            }

            player.sendMessage("§a§lWardrobe §7» Equipped loadout from slots §e" + armorSlot + " §7+ §e" + equipmentSlot);
        });
    }

    /**
     * Saves the player's current equipped armor into the given slot.
     * Creates or overwrites the WardrobeSet.
     */
    public void saveCurrentArmor(Player player, int slot, String customName) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        PlayerInventory inv = player.getInventory();

        ItemStack[] currentArmor = new ItemStack[4];
        currentArmor[0] = inv.getHelmet();
        currentArmor[1] = inv.getChestplate();
        currentArmor[2] = inv.getLeggings();
        currentArmor[3] = inv.getBoots();

        WardrobeSet set = getArmorSet(uuid, slot);
        if (set == null) {
            set = new WardrobeSet(customName != null ? customName : "Armor Set " + (slot + 1), Material.DIAMOND_CHESTPLATE);
        }
        for (int i = 0; i < 4; i++) {
            set.setArmorItem(i, currentArmor[i]);
        }
        if (customName != null) set.setName(customName);

        saveArmorSet(uuid, slot, set);
        player.sendMessage("§a§lWardrobe §7» Saved current armor to slot §e" + (slot + 1));
    }

    public void saveCurrentEquipment(Player player, int slot, String customName) {
        // Equipment handling improved for better player experience.
        // We save up to 4 "extra" non-armor items the player is currently carrying.
        // This gives a practical loadout system while we wait for better native equipment slot APIs.
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        ItemStack[] eq = new ItemStack[4];
        int idx = 0;

        // Priority: items in hand + offhand, then rest of inventory
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        if (main != null && !main.getType().isAir() && !isArmorPiece(main) && idx < 4) eq[idx++] = main.clone();
        if (off != null && !off.getType().isAir() && !isArmorPiece(off) && idx < 4) eq[idx++] = off.clone();

        for (ItemStack item : player.getInventory().getContents()) {
            if (idx >= 4) break;
            if (item != null && !item.getType().isAir() && !isArmorPiece(item) &&
                !item.equals(main) && !item.equals(off)) {
                eq[idx++] = item.clone();
            }
        }

        WardrobeSet set = getEquipmentSet(uuid, slot);
        if (set == null) {
            set = new WardrobeSet(customName != null ? customName : "Equipment Set " + (slot + 1), Material.GOLDEN_CHESTPLATE);
        }
        for (int i = 0; i < 4; i++) {
            set.setEquipmentItem(i, eq[i]);
        }
        if (customName != null) set.setName(customName);

        saveEquipmentSet(uuid, slot, set);
        player.sendMessage("§a§lWardrobe §7» Saved equipment to slot §e" + (slot + 1));
    }

    private boolean isArmorPiece(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        return m.name().endsWith("_HELMET") ||
               m.name().endsWith("_CHESTPLATE") ||
               m.name().endsWith("_LEGGINGS") ||
               m.name().endsWith("_BOOTS");
    }

    // ==================== LIFECYCLE ====================

    public void loadPlayer(UUID uuid) {
        // Clear any stale data
        armorPresets.remove(uuid);
        equipmentPresets.remove(uuid);
        wardrobeEquipmentCollection.remove(uuid);

        // Load from database into our caches
        plugin.getDatabaseManager().loadWardrobeForPlayer(uuid, this);

        // Load persistent collection for XP
        Set<Material> loadedCollection = plugin.getDatabaseManager().loadWardrobeCollection(uuid);
        if (!loadedCollection.isEmpty()) {
            wardrobeEquipmentCollection.put(uuid, ConcurrentHashMap.newKeySet());
            wardrobeEquipmentCollection.get(uuid).addAll(loadedCollection);
        }

        plugin.getLogger().fine("[Wardrobe] Loaded wardrobe data + collection for " + uuid);
    }

    public void savePlayer(UUID uuid) {
        // Individual changes are saved immediately to DB.
        // Hook exists for future bulk operations.
    }

    public void onDisable() {
        // Optional bulk save if we add dirty tracking later
    }

    // ==================== PROGRESSION (Island Upgrades) ====================

    /**
     * Returns the maximum number of wardrobe slots the player currently has access to,
     * based on their island's WARDROBE_SLOTS upgrade.
     */
    public int getMaxSlots(Player player) {
        if (player == null || plugin.getIslandUpgradeManager() == null) {
            return DEFAULT_MAX_SLOTS;
        }
        return plugin.getIslandUpgradeManager().getMaxWardrobeSlots(player);
    }

    public boolean canUseSlot(Player player, int slot) {
        return slot >= 0 && slot < getMaxSlots(player);
    }

    /**
     * Places an item into the player's inventory, preferring the end of the storage area
     * (slots 27-35) for "extra" equipment pieces. Falls back to normal addItem.
     */
    private void placeItemAtEndOfInventory(Player player, ItemStack item) {
        var inv = player.getInventory();
        // Try to place in the last few storage slots first (logical "bag" area)
        for (int i = 35; i >= 27; i--) {  // bottom row of storage, before hotbar
            if (inv.getItem(i) == null || inv.getItem(i).getType().isAir()) {
                inv.setItem(i, item);
                return;
            }
        }
        // Fallback: normal add (will stack or go to first available)
        inv.addItem(item);
    }

    // ==================== LIGHT WARDROBE COLLECTION XP ====================

    private static final int[] COLLECTION_MILESTONES = {5, 10, 25, 50, 100, 250};

    private void awardCollectionXPIfNew(UUID uuid, ItemStack[] equipment) {
        if (equipment == null) return;

        Set<Material> collected = wardrobeEquipmentCollection.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        boolean awardedAny = false;
        double totalXp = 0;

        int previousCount = collected.size();

        for (ItemStack item : equipment) {
            if (item != null && !item.getType().isAir()) {
                Material mat = item.getType();
                if (collected.add(mat)) {
                    // New unique equipment material saved in wardrobe
                    double xp = plugin.getConfig().getDouble("wardrobe.collection-xp.amount", 15.0);
                    totalXp += xp;
                    awardedAny = true;

                    // Persist immediately (lightweight insert)
                    plugin.getDatabaseManager().saveWardrobeCollectionEntry(uuid, mat.name());
                }
            }
        }

        if (awardedAny && totalXp > 0) {
            if (!plugin.getConfig().getBoolean("wardrobe.collection-xp.enabled", true)) {
                return;
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Light XP award via the main island XP system
                plugin.getIslandManager().addIslandXp(player, (int) totalXp);

                // Improved feedback: clear chat message for the collection unlock + action bar for XP
                player.sendMessage("§a§lWardrobe Collection §7» Unlocked a new equipment material! §e+" + String.format("%.0f", totalXp) + " Island XP");

                if (plugin.getConfig().getBoolean("xp.show-gain-messages", true)) {
                    player.sendActionBar("§a+" + String.format("%.0f", totalXp) + " XP §7(Wardrobe Collection)");
                }

                // Check and announce milestones
                checkAndAnnounceMilestones(player, previousCount, collected.size());
            }
        }
    }

    private void checkAndAnnounceMilestones(Player player, int previousCount, int newCount) {
        for (int milestone : COLLECTION_MILESTONES) {
            if (previousCount < milestone && newCount >= milestone) {
                player.sendMessage("§6§l★ Wardrobe Milestone! ★ §eYou have collected §a" + milestone + " §eunique equipment pieces!");
                // Could play a sound here in future
                break; // Only announce the highest crossed milestone per save
            }
        }
    }

    /**
     * Returns how many unique equipment materials this player has collected via wardrobe.
     * Useful for future commands or GUIs.
     */
    public int getEquipmentCollectionCount(UUID uuid) {
        Set<Material> set = wardrobeEquipmentCollection.get(uuid);
        return set == null ? 0 : set.size();
    }

    /**
     * Returns an unmodifiable view of the player's collected equipment materials.
     * Used by the /wardrobe collection view.
     */
    public java.util.Set<Material> getEquipmentCollection(UUID uuid) {
        Set<Material> set = wardrobeEquipmentCollection.get(uuid);
        if (set == null) {
            return java.util.Collections.emptySet();
        }
        return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(set));
    }
}