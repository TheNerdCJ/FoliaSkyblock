package com.thenerdcj.wardrobe;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.ParticleTrail;
import com.thenerdcj.gui.GUIUtils;
import com.thenerdcj.pets.CosmeticPet;
import com.thenerdcj.pets.PetType;
import com.thenerdcj.runes.Rune;
import com.thenerdcj.runes.RuneManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interactive Wardrobe GUI.
 *
 * Supports Armor and Equipment presets (addressing the top community request).
 * Now includes a "Cosmetics & Trails" tab for live particle trail previews.
 * Players can equip wardrobe sets, then switch tabs to preview and activate
 * unlocked trails in real-time (particles visible while GUI is open).
 */
public class WardrobeGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final WardrobeManager wardrobeManager;
    private final NamespacedKey ACTION_KEY;

    // Simple per-player state for the Cosmetics tab (pagination + filtering)
    private static class CosmeticsState {
        int page = 0;
        String filter = "ALL"; // ALL, WINGS, SPHERES, FOOTPRINTS, HALOS, NATURE, MAGIC, OTHER
    }

    private final Map<UUID, CosmeticsState> cosmeticsStates = new ConcurrentHashMap<>();

    // Per-player state for the Pets tab (pagination + rarity filtering, item 4)
    private static class PetState {
        int page = 0;
        String filter = "ALL"; // ALL, COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC
    }

    private final Map<UUID, PetState> petStates = new ConcurrentHashMap<>();

    // View modes
    public enum View { ARMOR, EQUIPMENT, COSMETICS, PETS, TAGS, WINGS, RUNES, HELMET_SKINS, DEATH_EFFECTS, DEATH_MESSAGES, BACKPACK_SKINS, POWER_ORB_SKINS, MINION_SKINS, FURNITURE, MUSIC, OVERHEAD, EMOTES, STRUCTURES, CHAT_BUBBLES, WEATHER, ACCESSORIES }

    public WardrobeGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public WardrobeGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.wardrobeManager = plugin.getWardrobeManager();
        this.ACTION_KEY = new NamespacedKey(plugin, "wardrobe_action");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void openWardrobe(Player player) {
        openWardrobe(player, View.ARMOR);
    }

    public void openWardrobe(Player player, View view) {
        String viewName = switch (view) {
            case ARMOR -> "§bArmor";
            case EQUIPMENT -> "§dEquipment";
            case COSMETICS -> "§5Cosmetics";
            case PETS -> "§dPets";
            case TAGS -> "§5Tags";
            case WINGS -> "§bWings";
            case RUNES -> "§5Runes";
            case HELMET_SKINS -> "§6Helmets";
            case DEATH_EFFECTS -> "§4Death Effects";
            case DEATH_MESSAGES -> "§4Death Messages";
            case BACKPACK_SKINS -> "§aBackpacks";
            case POWER_ORB_SKINS -> "§bPower Orbs";
            case MINION_SKINS -> "§6Minions";
            case FURNITURE -> "§6Housing";
            case MUSIC -> "§dMusic";
            case OVERHEAD -> "§5Overhead";
            case EMOTES -> "§dEmotes";
            case STRUCTURES -> "§6Structures";
            case CHAT_BUBBLES -> "§dChat Bubbles";
            case WEATHER -> "§3Island Weather";
            case ACCESSORIES -> "§6Accessories";
        };
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lWardrobe §8- " + viewName);

        // Fillers (consistent with project style)
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName("§8 ");
        filler.setItemMeta(fm);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Title item
        ItemStack title = new ItemStack(Material.ARMOR_STAND);
        ItemMeta tm = title.getItemMeta();
        tm.setDisplayName("§6§lWardrobe");
        tm.setLore(Arrays.asList(
            "§7Save and quickly swap gear loadouts",
            "§7Supports both §bArmor §7and §dEquipment",
            "",
            "§eLeft-click §7a set to equip",
            "§7Shift+Left §7= Save current gear to slot",
            "§7Right-click §7= More options",
            "",
            "§8Collect unique equipment for Island XP"
        ));
        title.setItemMeta(tm);
        inv.setItem(4, title);

        // Tab buttons
        ItemStack armorTab = createTabItem("§b§lArmor Sets", view == View.ARMOR, "VIEW_ARMOR");
        ItemStack equipTab = createTabItem("§d§lEquipment Sets", view == View.EQUIPMENT, "VIEW_EQUIPMENT");
        ItemStack cosmeticsTab = createTabItem("§5§lCosmetics & Trails", view == View.COSMETICS, "VIEW_COSMETICS");
        ItemStack petsTab = createTabItem("§d§lPets", view == View.PETS, "VIEW_PETS");
        ItemStack tagsTab = createTabItem("§5§lTags", view == View.TAGS, "VIEW_TAGS");
        ItemStack wingsTab = createTabItem("§b§lWings", view == View.WINGS, "VIEW_WINGS");
        ItemStack runesTab = createTabItem("§5§lRunes", view == View.RUNES, "VIEW_RUNES");
        ItemStack helmetSkinsTab = createTabItem("§6§lHelmet Skins", view == View.HELMET_SKINS, "VIEW_HELMET_SKINS");
        ItemStack deathEffectsTab = createTabItem("§4§lDeath Effects", view == View.DEATH_EFFECTS, "VIEW_DEATH_EFFECTS");
        ItemStack deathMessagesTab = createTabItem("§4§lDeath Messages", view == View.DEATH_MESSAGES, "VIEW_DEATH_MESSAGES");
        ItemStack backpackSkinsTab = createTabItem("§a§lBackpack Skins", view == View.BACKPACK_SKINS, "VIEW_BACKPACK_SKINS");
        ItemStack powerOrbSkinsTab = createTabItem("§b§lPower Orb Skins", view == View.POWER_ORB_SKINS, "VIEW_POWER_ORB_SKINS");
        ItemStack minionSkinsTab = createTabItem("§6§lMinion Skins", view == View.MINION_SKINS, "VIEW_MINION_SKINS");
        ItemStack furnitureTab = createTabItem("§6§lHousing Decor", view == View.FURNITURE, "VIEW_FURNITURE");
        ItemStack musicTab = createTabItem("§d§lIsland Music", view == View.MUSIC, "VIEW_MUSIC");
        ItemStack overheadTab = createTabItem("§5§lOverhead Effects", view == View.OVERHEAD, "VIEW_OVERHEAD");
        ItemStack emotesTab = createTabItem("§d§lEmotes", view == View.EMOTES, "VIEW_EMOTES");
        ItemStack structuresTab = createTabItem("§6§lStructures", view == View.STRUCTURES, "VIEW_STRUCTURES");
        ItemStack chatBubblesTab = createTabItem("§d§lChat Bubbles", view == View.CHAT_BUBBLES, "VIEW_CHAT_BUBBLES");
        ItemStack weatherTab = createTabItem("§3§lIsland Weather", view == View.WEATHER, "VIEW_WEATHER");
        ItemStack accessoriesTab = createTabItem("§6§lAccessories", view == View.ACCESSORIES, "VIEW_ACCESSORIES");
        inv.setItem(0, armorTab);
        inv.setItem(1, equipTab);
        inv.setItem(2, cosmeticsTab);
        inv.setItem(3, petsTab);
        inv.setItem(5, tagsTab);  // small offset so it doesn't overlap too much
        inv.setItem(6, wingsTab);
        inv.setItem(7, runesTab);
        inv.setItem(8, helmetSkinsTab);
        inv.setItem(9, deathEffectsTab);
        inv.setItem(10, deathMessagesTab);
        inv.setItem(11, backpackSkinsTab);
        inv.setItem(11, powerOrbSkinsTab);
        inv.setItem(12, minionSkinsTab);
        inv.setItem(13, furnitureTab);
        inv.setItem(14, musicTab);
        inv.setItem(15, overheadTab);
        inv.setItem(16, emotesTab);
        inv.setItem(17, structuresTab);
        inv.setItem(18, chatBubblesTab);
        inv.setItem(19, weatherTab);
        inv.setItem(20, accessoriesTab);

        int[] slotPositions = {19, 20, 21, 22, 23, 24, 25, 28, 29}; // nice 3x3-ish layout

        if (view == View.COSMETICS) {
            // === IMPROVED TRAIL PREVIEW WITH PAGINATION + FILTERING ===
            renderTrailPreview(player, inv);
        } else if (view == View.PETS) {
            // === PETS TAB (Step 1: Full Wardrobe Integration) ===
            renderPetsPreview(player, inv);
        } else if (view == View.TAGS) {
            renderTagsPreview(player, inv);
        } else if (view == View.WINGS) {
            renderWingsPreview(player, inv);
        } else if (view == View.RUNES) {
            renderRunesPreview(player, inv);
        } else if (view == View.HELMET_SKINS) {
            renderHelmetSkinsPreview(player, inv);
        } else if (view == View.DEATH_EFFECTS) {
            renderDeathEffectsPreview(player, inv);
        } else if (view == View.DEATH_MESSAGES) {
            renderDeathMessagesPreview(player, inv);
        } else if (view == View.BACKPACK_SKINS) {
            renderBackpackSkinsPreview(player, inv);
        } else if (view == View.POWER_ORB_SKINS) {
            renderPowerOrbSkinsPreview(player, inv);
        } else if (view == View.MINION_SKINS) {
            renderMinionSkinsPreview(player, inv);
        } else if (view == View.FURNITURE) {
            renderFurniturePreview(player, inv);
        } else if (view == View.MUSIC) {
            renderMusicPreview(player, inv);
        } else if (view == View.OVERHEAD) {
            renderOverheadPreview(player, inv);
        } else if (view == View.EMOTES) {
            renderEmotesPreview(player, inv);
        } else if (view == View.STRUCTURES) {
            renderStructuresPreview(player, inv);
        } else if (view == View.CHAT_BUBBLES) {
            renderChatBubblesPreview(player, inv);
        } else if (view == View.WEATHER) {
            renderWeatherPreview(player, inv);
        } else if (view == View.ACCESSORIES) {
            renderAccessoriesPreview(player, inv);
        } else {
            // Render the 9 slots (original armor/equip)
            Map<Integer, WardrobeSet> presets = (view == View.ARMOR)
                    ? wardrobeManager.getArmorPresets(player.getUniqueId())
                    : wardrobeManager.getEquipmentPresets(player.getUniqueId());

            int maxSlots = wardrobeManager.getMaxSlots(player);

            for (int i = 0; i < WardrobeManager.DEFAULT_MAX_SLOTS; i++) {
                int pos = slotPositions[i];
                WardrobeSet set = presets.get(i);
                boolean unlocked = i < maxSlots;

                ItemStack item;
                if (!unlocked) {
                    item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName("§c§lLocked Slot " + (i + 1));
                    meta.setLore(Arrays.asList(
                        "§7Unlock more wardrobe slots",
                        "§7by upgrading §6Wardrobe Slots §7on your island."
                    ));
                    item.setItemMeta(meta);
                } else if (set != null && !set.isEmpty()) {
                    item = set.createDisplayItem();
                } else {
                    item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName("§7§lEmpty Slot " + (i + 1));
                    meta.setLore(Arrays.asList(
                        "§7Shift+Left-click §7while wearing gear",
                        "§7to save this loadout."
                    ));
                    item.setItemMeta(meta);
                }

                if (unlocked) {
                    // Store action data only for unlocked slots
                    PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                    pdc.set(ACTION_KEY, PersistentDataType.STRING, (view == View.ARMOR ? "ARMOR_" : "EQUIP_") + i);
                    item.setItemMeta(item.getItemMeta());
                }

                inv.setItem(pos, item);
            }

            // Bottom info — now includes collection progress for better visibility of the light XP system
            ItemStack info = new ItemStack(Material.BOOK);
            ItemMeta im = info.getItemMeta();
            im.setDisplayName("§e§lWardrobe Info");

            int collectionCount = wardrobeManager.getEquipmentCollectionCount(player.getUniqueId());

            im.setLore(Arrays.asList(
                "§7Saved sets: §a" + presets.size(),
                "§7Max slots: §a" + maxSlots + " §7(upgrade via /is upgrade)",
                "",
                "§7Equipment Collection: §a" + collectionCount + " §7unique pieces",
                "§8Save new equipment types to earn Island XP"
            ));
            info.setItemMeta(im);
            inv.setItem(49, info);

            // Dedicated Collection display item (small polish feature)
            ItemStack collectionItem = new ItemStack(Material.CHEST);
            ItemMeta cm = collectionItem.getItemMeta();
            cm.setDisplayName("§6§lEquipment Collection");
            cm.setLore(Arrays.asList(
                "§7Unique pieces collected: §a" + collectionCount,
                "§7Save new equipment materials in any slot",
                "§7to grow your collection and earn Island XP.",
                "",
                "§eUse §6/wardrobe collection §eto view details"
            ));
            collectionItem.setItemMeta(cm);
            inv.setItem(47, collectionItem);

            // Core Island Collections preview (synergy with gathering progression)
            if (plugin.getCollectionManager() != null) {
                String ik = null;
                try {
                    var isle = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                    if (isle != null) ik = isle.getId();
                } catch (Exception ignored) {}
                int collTotal = (ik != null) ? plugin.getCollectionManager().getCollectionCount(ik) : 0;
                ItemStack collCore = new ItemStack(Material.BOOK);
                ItemMeta ccm = collCore.getItemMeta();
                ccm.setDisplayName("§6§lIsland Collections");
                ccm.setLore(Arrays.asList(
                    "§7Unique items discovered on this island: §a" + collTotal,
                    "§7Gather, mine, fight, fish on your island to log discoveries.",
                    "§7Milestones award Island XP + cosmetic rewards.",
                    "",
                    "§eUse §6/collections §eto view full progress"
                ));
                collCore.setItemMeta(ccm);
                inv.setItem(46, collCore);
            }
        }

        player.openInventory(inv);
    }

    private ItemStack createTabItem(String name, boolean selected, String action) {
        ItemStack item = new ItemStack(selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Improved Cosmetics & Trails tab with pagination + category filtering.
     * Much better layout for the large number of trails now available.
     */
    private void renderTrailPreview(Player player, Inventory inv) {
        var trailManager = plugin.getParticleTrailManager();
        ParticleTrail active = trailManager.getActiveTrail(player.getUniqueId());
        Set<ParticleTrail> unlocked = trailManager.getUnlockedTrails(player.getUniqueId());

        // Get or create state
        CosmeticsState state = cosmeticsStates.computeIfAbsent(player.getUniqueId(), k -> new CosmeticsState());

        // === HEADER ROW ===
        ItemStack title = GUIUtils.createItem(Material.FIREWORK_ROCKET, "§5§lParticle Trails",
                "§7Live preview while in wardrobe",
                "§7Click any trail to activate it instantly");
        inv.setItem(4, title);

        ItemStack current = GUIUtils.createItem(Material.GLOWSTONE_DUST, "§e§lActive: §a" + active.getDisplayName());
        inv.setItem(8, current);

        ItemStack noneBtn = GUIUtils.createItem(Material.BARRIER, "§c§lDisable Trail");
        PersistentDataContainer npdc = noneBtn.getItemMeta().getPersistentDataContainer();
        npdc.set(ACTION_KEY, PersistentDataType.STRING, "TRAIL_NONE");
        noneBtn.setItemMeta(noneBtn.getItemMeta());
        inv.setItem(0, noneBtn);

        ItemStack fullBtn = GUIUtils.createItem(Material.BOOK, "§6§lFull Trail Menu");
        PersistentDataContainer fpdc = fullBtn.getItemMeta().getPersistentDataContainer();
        fpdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_TRAIL_GUI");
        fullBtn.setItemMeta(fullBtn.getItemMeta());
        inv.setItem(5, fullBtn);

        // === FILTER BUTTONS (row 1) ===
        String[] filters = {"ALL", "WINGS", "SPHERES", "FOOTPRINTS", "HALOS", "NATURE", "MAGIC", "OTHER"};
        int[] filterSlots = {9, 10, 11, 12, 13, 14, 15, 16};

        for (int i = 0; i < filters.length; i++) {
            String f = filters[i];
            boolean selected = f.equals(state.filter);
            ItemStack filterItem = GUIUtils.createItem(
                selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                (selected ? "§a§l" : "§7") + f
            );
            PersistentDataContainer pdc = filterItem.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "FILTER_" + f);
            filterItem.setItemMeta(filterItem.getItemMeta());
            inv.setItem(filterSlots[i], filterItem);
        }

        // === TRAIL GRID (much larger - rows 2-5) ===
        List<ParticleTrail> filtered = new ArrayList<>();
        for (ParticleTrail t : ParticleTrail.values()) {
            if (t == ParticleTrail.NONE) continue;
            if (!unlocked.contains(t)) continue;
            if (matchesFilter(t, state.filter)) {
                filtered.add(t);
            }
        }

        int itemsPerPage = 28; // nice 4x7 grid
        int start = state.page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, filtered.size());

        int[] gridSlots = {
            18,19,20,21,22,23,24,25,
            27,28,29,30,31,32,33,34,
            36,37,38,39,40,41,42,43,
            45,46,47,48
        };

        int gridIdx = 0;
        for (int i = start; i < end && gridIdx < gridSlots.length; i++, gridIdx++) {
            ParticleTrail trail = filtered.get(i);
            boolean isActive = trail == active;

            ItemStack item = GUIUtils.createItem(getTrailIcon(trail),
                    (isActive ? "§a§l★ " : "§e") + trail.getDisplayName(),
                    "§7Click to activate (live preview)",
                    isActive ? "§aCurrently active" : "");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "TRAIL_" + trail.name());
            item.setItemMeta(item.getItemMeta());

            inv.setItem(gridSlots[gridIdx], item);
        }

        // Fill empty grid slots with glass
        ItemStack glass = GUIUtils.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int s : gridSlots) {
            if (inv.getItem(s) == null) inv.setItem(s, glass);
        }

        // === PAGINATION (bottom row) ===
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) itemsPerPage));

        if (state.page > 0) {
            ItemStack prev = GUIUtils.createItem(Material.ARROW, "§e§lPrevious Page");
            PersistentDataContainer ppdc = prev.getItemMeta().getPersistentDataContainer();
            ppdc.set(ACTION_KEY, PersistentDataType.STRING, "PAGE_PREV");
            prev.setItemMeta(prev.getItemMeta());
            inv.setItem(45, prev);
        }

        ItemStack pageInfo = GUIUtils.createItem(Material.PAPER, "§7Page §e" + (state.page + 1) + "§7 / §e" + totalPages,
                "§7Showing " + (end - start) + " of " + filtered.size() + " trails");
        inv.setItem(49, pageInfo);

        if (state.page < totalPages - 1) {
            ItemStack next = GUIUtils.createItem(Material.ARROW, "§e§lNext Page");
            PersistentDataContainer npdc2 = next.getItemMeta().getPersistentDataContainer();
            npdc2.set(ACTION_KEY, PersistentDataType.STRING, "PAGE_NEXT");
            next.setItemMeta(next.getItemMeta());
            inv.setItem(53, next);
        }

        // Info
        ItemStack info = GUIUtils.createItem(Material.BOOK, "§6§lTips",
                "§7• Click a trail to preview it live",
                "§7• Change filter to narrow results",
                "§7• Use arrows to browse pages");
        inv.setItem(52, info);
    }

    private boolean matchesFilter(ParticleTrail trail, String filter) {
        if ("ALL".equals(filter)) return true;
        String name = trail.name().toUpperCase();

        return switch (filter) {
            case "WINGS" -> name.contains("WING");
            case "SPHERES" -> name.contains("SPHERE");
            case "FOOTPRINTS" -> name.contains("FOOTPRINT");
            case "HALOS" -> name.contains("HALO");
            case "NATURE" -> name.contains("LEAF") || name.contains("VINE") || name.contains("FLOWER") || name.contains("MUSHROOM");
            case "MAGIC" -> name.contains("ARCANE") || name.contains("RUNE") || name.contains("MAGIC") || name.contains("ENCHANT");
            case "OTHER" -> !matchesFilter(trail, "WINGS") && !matchesFilter(trail, "SPHERES") &&
                            !matchesFilter(trail, "FOOTPRINTS") && !matchesFilter(trail, "HALOS") &&
                            !matchesFilter(trail, "NATURE") && !matchesFilter(trail, "MAGIC");
            default -> true;
        };
    }

    private Material getTrailIcon(ParticleTrail trail) {
        // Simple icon mapping for preview (can be expanded)
        return switch (trail.name()) {
            case "FLAME_TRAIL", "LAVA_TRAIL", "FIREWORK_TRAIL", "METEOR_TRAIL" -> Material.BLAZE_POWDER;
            case "HEART_AURA", "SOUL_TRAIL" -> Material.REDSTONE;
            case "WING_AURA", "COSMIC_WINGS" -> Material.FEATHER;
            case "FROST_AURA", "BUBBLE_AURA" -> Material.SNOWBALL;
            case "GALAXY_AURA", "VOID_AURA", "PORTAL_AURA" -> Material.ENDER_PEARL;
            case "HELIX_TRAIL", "DOUBLE_HELIX", "ASCENDING_SPIRAL" -> Material.STRING;
            case "RING_AURA" -> Material.SLIME_BALL;
            case "ORBITING_ORBS" -> Material.ENDER_EYE;
            case "ELECTRIC_TRAIL", "CRIT_SPARK" -> Material.LIGHTNING_ROD;
            case "NOTE_TRAIL" -> Material.NOTE_BLOCK;
            case "DRAGON_BREATH", "ETERNAL_FLAME" -> Material.DRAGON_BREATH;
            case "HOLY_AURA", "SPARKLE_AURA" -> Material.GLOWSTONE_DUST;
            case "LEAF_TRAIL" -> Material.OAK_LEAVES;
            case "SHADOW_AURA" -> Material.COAL;
            case "VINE_TRAIL" -> Material.VINE;
            case "FLOWER_AURA" -> Material.POPPY;
            case "MUSHROOM_TRAIL" -> Material.RED_MUSHROOM;
            case "ARCANE_AURA" -> Material.ENCHANTED_BOOK;
            case "STAR_TRAIL" -> Material.NETHER_STAR;
            case "RUNE_CIRCLE" -> Material.END_CRYSTAL;
            case "WIND_TRAIL" -> Material.FEATHER;
            case "EARTH_AURA" -> Material.DIRT;
            case "THUNDER_AURA" -> Material.LIGHTNING_ROD;
            case "CONFETTI_BURST" -> Material.FIREWORK_ROCKET;
            case "HEART_RING" -> Material.RED_DYE;
            case "STAR_BURST" -> Material.NETHER_STAR;
            case "PHOENIX_AURA" -> Material.BLAZE_POWDER;
            case "NEBULA_AURA" -> Material.ENDER_PEARL;
            case "FOOTPRINT_STONE" -> Material.STONE;
            case "FOOTPRINT_LILY" -> Material.LILY_PAD;
            case "FOOTPRINT_CREEPER" -> Material.CREEPER_HEAD;
            case "FOOTPRINT_RAINBOW" -> Material.RED_DYE;
            case "GUARDIAN_SPHERE" -> Material.PRISMARINE_CRYSTALS;
            case "GALAXY_SPHERE" -> Material.ENDER_EYE;
            case "CRYSTAL_SPHERE" -> Material.END_CRYSTAL;
            case "DRAGON_WINGS" -> Material.DRAGON_HEAD;
            case "BAT_WINGS" -> Material.LEATHER;
            case "FAIRY_WINGS" -> Material.POPPY;
            case "RAINBOW_WINGS" -> Material.FEATHER;
            case "HALO_GOLD" -> Material.GOLD_NUGGET;
            case "HALO_ANGEL" -> Material.GLOWSTONE_DUST;
            case "HALO_DEMON" -> Material.MAGMA_CREAM;
            case "VOID_VORTEX" -> Material.OBSIDIAN;
            case "WATER_VORTEX" -> Material.WATER_BUCKET;
            case "GROUND_WAVE" -> Material.SLIME_BALL;
            case "SHOCKWAVE" -> Material.TNT;
            case "CRYSTAL_ARCH" -> Material.END_CRYSTAL;
            case "MAGIC_ARCH" -> Material.BLAZE_ROD;
            case "MAGIC_CIRCLE" -> Material.ENCHANTING_TABLE;
            case "GUARDIAN_AURA" -> Material.PRISMARINE_SHARD;
            case "MYTHIC_AURA" -> Material.NETHER_STAR;
            case "CUTE_HEARTS" -> Material.RED_DYE;
            case "ANGRY_SPARKS" -> Material.FIRE_CHARGE;
            case "LAVA_POP" -> Material.LAVA_BUCKET;
            case "SLINKY_TRAIL" -> Material.NOTE_BLOCK;
            case "SHOOTER_TRAIL" -> Material.BOW;
            case "MAGICIAN_CIRCLE" -> Material.BREWING_STAND;
            default -> Material.FIREWORK_ROCKET;
        };
    }

    /**
     * Renders the Pets tab in the Wardrobe GUI.
     * Now with pagination + rarity filtering (modeled exactly after the improved Cosmetics/Trails tab).
     */
    private void renderPetsPreview(Player player, Inventory inv) {
        var petManager = plugin.getPetManager();

        // Get or create per-player pet pagination/filter state
        PetState state = petStates.computeIfAbsent(player.getUniqueId(), k -> new PetState());

        // Header
        ItemStack titleItem = GUIUtils.createItem(Material.BONE, "§d§lCosmetic Pets",
                "§7Vanity followers - Purely cosmetic (with rarity)",
                "§7Click to equip/preview • Filters + pages like trails");
        inv.setItem(4, titleItem);

        ItemStack current = GUIUtils.createItem(Material.LEAD, "§e§lActive Pet",
                petManager.getActivePet(player.getUniqueId()) != null 
                    ? "§a" + petManager.getActivePet(player.getUniqueId()).getCustomName()
                    : "§7None");
        inv.setItem(8, current);

        // Open dedicated GUI button
        ItemStack openFull = GUIUtils.createItem(Material.BOOK, "§6§lOpen Full Pets Menu");
        PersistentDataContainer openPdc = openFull.getItemMeta().getPersistentDataContainer();
        openPdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_PET_GUI");
        openFull.setItemMeta(openFull.getItemMeta());
        inv.setItem(0, openFull);

        // Get owned pets
        List<CosmeticPet> owned = petManager.getOwnedPets(player.getUniqueId());

        // Collection progress (pets + skins)
        int collectionCount = petManager.getPetCollectionCount(player.getUniqueId());
        int skinCount = petManager.getSkinCollectionCount(player.getUniqueId());
        ItemStack collItem = GUIUtils.createItem(Material.BOOK, "§6§lPet Collection",
                "§7Unique pets collected: §a" + collectionCount,
                "§7Skins unlocked: §d" + skinCount,
                "§7Rarity XP awarded on new discoveries");
        inv.setItem(47, collItem);

        if (owned.isEmpty()) {
            ItemStack empty = GUIUtils.createItem(Material.BARRIER, "§cYou don't own any pets yet",
                    "§7Pets can be earned through gameplay,",
                    "§7prestige, or special events.");
            inv.setItem(22, empty);
            return;
        }

        // === RARITY FILTERS (row similar to cosmetics) ===
        String[] filters = {"ALL", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
        int[] filterSlots = {9, 10, 11, 12, 13, 14, 15};

        for (int i = 0; i < filters.length && i < filterSlots.length; i++) {
            String f = filters[i];
            boolean selected = f.equals(state.filter);
            ItemStack fi = GUIUtils.createItem(
                selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                (selected ? "§a§l" : "§7") + f
            );
            PersistentDataContainer fpdc = fi.getItemMeta().getPersistentDataContainer();
            fpdc.set(ACTION_KEY, PersistentDataType.STRING, "PETS_FILTER_" + f);
            fi.setItemMeta(fi.getItemMeta());
            inv.setItem(filterSlots[i], fi);
        }

        // === FILTER + PAGINATE owned pets ===
        List<CosmeticPet> filtered = new ArrayList<>();
        for (CosmeticPet p : owned) {
            if (matchesPetFilter(p, state.filter)) {
                filtered.add(p);
            }
        }

        int itemsPerPage = 21; // 3x7 grid (fits wardrobe nicely)
        int start = state.page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, filtered.size());

        // Grid slots (expanded from old fixed)
        int[] petSlots = {
            18,19,20,21,22,23,24,
            27,28,29,30,31,32,33,
            36,37,38,39,40,41,42
        };

        CosmeticPet active = petManager.getActivePet(player.getUniqueId());

        int gridIdx = 0;
        for (int i = start; i < end && gridIdx < petSlots.length; i++, gridIdx++) {
            CosmeticPet pet = filtered.get(i);
            boolean isActive = pet == active;

            ItemStack item = pet.createDisplayItem();
            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : new ArrayList<>());
            lore.add("");
            if (pet.getSkin() != null && !pet.getSkin().isNone()) {
                lore.add("§7Skin: " + pet.getSkin().getRarity().getColorCode() + pet.getSkin().getDisplayName());
            }
            lore.add(isActive ? "§a§lCurrently Equipped" : "§eClick to equip & live preview");
            lore.add("§7Right-click for Skins (via /pets)");
            meta.setLore(lore);

            // Robust identification (works across pages/filters) - type + sanitized name
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String safeName = pet.getCustomName().replace(" ", "_").replace(":", "");
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "PET_WARDROBE_" + pet.getType().name() + "_" + safeName);
            item.setItemMeta(meta);

            inv.setItem(petSlots[gridIdx], item);
        }

        // Fill remaining grid with glass
        ItemStack glass = GUIUtils.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int s : petSlots) {
            if (inv.getItem(s) == null) inv.setItem(s, glass);
        }

        // === PAGINATION (bottom, like cosmetics) ===
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) itemsPerPage));

        if (state.page > 0) {
            ItemStack prev = GUIUtils.createItem(Material.ARROW, "§e§lPrevious Page");
            PersistentDataContainer ppdc = prev.getItemMeta().getPersistentDataContainer();
            ppdc.set(ACTION_KEY, PersistentDataType.STRING, "PETS_PAGE_PREV");
            prev.setItemMeta(prev.getItemMeta());
            inv.setItem(45, prev);
        }

        ItemStack pageInfo = GUIUtils.createItem(Material.PAPER, "§7Page §e" + (state.page + 1) + "§7 / §e" + totalPages,
                "§7Showing " + (end - start) + " of " + filtered.size() + " pets  •  Filter: " + state.filter);
        inv.setItem(49, pageInfo);

        if (state.page < totalPages - 1) {
            ItemStack next = GUIUtils.createItem(Material.ARROW, "§e§lNext Page");
            PersistentDataContainer npdc = next.getItemMeta().getPersistentDataContainer();
            npdc.set(ACTION_KEY, PersistentDataType.STRING, "PETS_PAGE_NEXT");
            next.setItemMeta(next.getItemMeta());
            inv.setItem(53, next);
        }

        // Info tip
        ItemStack tips = GUIUtils.createItem(Material.BOOK, "§6§lPets Tips",
                "§7• Filter by rarity tier",
                "§7• Use arrows to page",
                "§7• Click = equip/preview live");
        inv.setItem(52, tips);
    }

    private boolean matchesPetFilter(CosmeticPet pet, String filter) {
        if ("ALL".equals(filter)) return true;
        String r = pet.getType().getRarity().name();
        return r.equalsIgnoreCase(filter);
    }

    /**
     * Basic render for Elytra Wings tab in Wardrobe (pagination + filtering ready).
     */
    private void renderWingsPreview(Player player, Inventory inv) {
        var wingManager = plugin.getElytraWingManager();
        if (wingManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cWings system unavailable"));
            return;
        }

        // Reuse PetState for simplicity
        PetState state = petStates.computeIfAbsent(player.getUniqueId(), k -> new PetState());

        ItemStack title = GUIUtils.createItem(Material.ELYTRA, "§b§lElytra Wing Cosmetics",
                "§7Special visual effects while gliding",
                "§7Select your active wing style");
        inv.setItem(4, title);

        ItemStack current = GUIUtils.createItem(Material.PAPER, "§e§lActive Wings",
                wingManager.getActiveWing(player.getUniqueId()).getDisplayName());
        inv.setItem(8, current);

        // Simple list of owned wings
        java.util.Set<com.thenerdcj.wings.ElytraWing> owned = wingManager.getOwnedWings(player.getUniqueId());
        com.thenerdcj.wings.ElytraWing active = wingManager.getActiveWing(player.getUniqueId());

        int slot = 18;
        for (com.thenerdcj.wings.ElytraWing wing : owned) {
            if (slot > 44) break;
            if (wing.isNone()) continue;

            boolean isActive = wing == active;

            ItemStack item = GUIUtils.createItem(Material.ELYTRA,
                    (isActive ? "§a§l★ " : "§e") + wing.getDisplayName(),
                    "§7" + wing.getDescription(),
                    "§7Rarity: " + wing.getRarity().getColorCode() + wing.getRarity().getDisplayName(),
                    "",
                    isActive ? "§aCurrently Active" : "§eClick to equip");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "WING_EQUIP_" + wing.name());
            item.setItemMeta(item.getItemMeta());

            inv.setItem(slot++, item);
        }

        if (owned.isEmpty() || owned.size() <= 1) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo wing styles unlocked yet",
                    "§7Earn them via prestige or the Slayer Shop"));
        }
    }

    /**
     * Renders the Runes tab in the Wardrobe.
     * Mirrors the simple preview style of Wings/Tags tabs.
     * Runes are applied to held items (not globally "active"), so we show collection + owned list
     * and provide a button to open the full Rune Table / dedicated GUI.
     */
    private void renderRunesPreview(Player player, Inventory inv) {
        var runeManager = plugin.getRuneManager();
        if (runeManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cRunes system unavailable"));
            return;
        }

        int collCount = runeManager.getRuneCollectionCount(player.getUniqueId());
        int totalRunes = Rune.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.ENCHANTED_BOOK, "§5§lCosmetic Runes",
                "§7Apply visual effects to weapons & tools",
                "§7Collection: §a" + collCount + " / " + totalRunes + " §7runes unlocked");
        inv.setItem(4, title);

        // Open full dedicated Rune GUI / Rune Table
        ItemStack openFull = GUIUtils.createItem(Material.BOOK, "§6§lOpen Full Rune Menu",
                "§7Browse, apply, and upgrade runes on your held item");
        PersistentDataContainer openPdc = openFull.getItemMeta().getPersistentDataContainer();
        openPdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_RUNE_GUI");
        openFull.setItemMeta(openFull.getItemMeta());
        inv.setItem(0, openFull);

        // Show owned runes (simple list, similar to wings)
        java.util.Set<Rune> owned = runeManager.getOwnedRunes(player.getUniqueId());

        int slot = 18;
        for (Rune rune : owned) {
            if (slot > 44) break;
            if (rune.isNone()) continue;

            ItemStack item = GUIUtils.createItem(Material.ENCHANTED_BOOK,
                    "§e" + rune.getDisplayName(),
                    "§7" + rune.getDescription(),
                    "§7Rarity: " + rune.getRarity().getColorCode() + rune.getRarity().getDisplayName(),
                    "§7Max Tier: §e" + rune.getMaxTier() + "§7/3",
                    "",
                    "§eClick to open Rune Menu");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_RUNE_GUI");
            item.setItemMeta(item.getItemMeta());

            inv.setItem(slot++, item);
        }

        if (owned.isEmpty() || owned.size() <= 1) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo runes unlocked yet",
                    "§7Earn them via prestige, slayer tokens, or milestones."));
        }
    }

    /**
     * Basic preview for Helmet Skins tab (modeled after Runes tab).
     */
    private void renderHelmetSkinsPreview(Player player, Inventory inv) {
        var skinManager = plugin.getHelmetSkinManager();
        if (skinManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cHelmet Skins system unavailable"));
            return;
        }

        int collCount = skinManager.getSkinCollectionCount(player.getUniqueId());
        int total = com.thenerdcj.cosmetic.HelmetSkin.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.DIAMOND_HELMET, "§6§lHelmet Skins",
                "§7Cosmetic overrides for your helmet",
                "§7Collection: §a" + collCount + " / " + total + " §7skins unlocked");
        inv.setItem(4, title);

        com.thenerdcj.cosmetic.HelmetSkin active = skinManager.getActiveHelmetSkin(player.getUniqueId());
        ItemStack current = GUIUtils.createItem(Material.PAPER, "§e§lActive Helmet Skin",
                active.isNone() ? "§7None" : active.getRarity().getColorCode() + active.getDisplayName());
        inv.setItem(8, current);

        ItemStack openFull = GUIUtils.createItem(Material.BOOK, "§6§lOpen Full Helmet Skins Menu");
        PersistentDataContainer openPdc = openFull.getItemMeta().getPersistentDataContainer();
        openPdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_HELMET_SKIN_GUI");
        openFull.setItemMeta(openFull.getItemMeta());
        inv.setItem(0, openFull);

        java.util.Set<com.thenerdcj.cosmetic.HelmetSkin> owned = skinManager.getOwnedSkins(player.getUniqueId());
        int slot = 18;
        for (com.thenerdcj.cosmetic.HelmetSkin skin : owned) {
            if (slot > 44) break;
            if (skin.isNone()) continue;

            ItemStack item = GUIUtils.createItem(Material.DIAMOND_HELMET,
                    "§e" + skin.getDisplayName(),
                    "§7" + skin.getDescription(),
                    "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                    "",
                    "§eClick to open Helmet Skins menu");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_HELMET_SKIN_GUI");
            item.setItemMeta(item.getItemMeta());
            inv.setItem(slot++, item);
        }

        if (owned.isEmpty() || owned.size() <= 1) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo helmet skins unlocked yet",
                    "§7Earn them via prestige or the Slayer Shop"));
        }
    }

    /**
     * Basic preview for Death Effects tab.
     */
    private void renderDeathEffectsPreview(Player player, Inventory inv) {
        var effectManager = plugin.getDeathEffectManager();
        if (effectManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cDeath Effects system unavailable"));
            return;
        }

        int collCount = effectManager.getEffectCollectionCount(player.getUniqueId());
        int total = com.thenerdcj.cosmetic.DeathEffect.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.SKELETON_SKULL, "§4§lDeath Effects",
                "§7Cosmetic effects on death and kills",
                "§7Collection: §a" + collCount + " / " + total + " §7effects unlocked");
        inv.setItem(4, title);

        com.thenerdcj.cosmetic.DeathEffect active = effectManager.getActiveDeathEffect(player.getUniqueId());
        ItemStack current = GUIUtils.createItem(Material.PAPER, "§e§lActive Death Effect",
                active.isNone() ? "§7None" : active.getRarity().getColorCode() + active.getDisplayName());
        inv.setItem(8, current);

        ItemStack openFull = GUIUtils.createItem(Material.BOOK, "§6§lOpen Full Death Effects Menu");
        PersistentDataContainer openPdc = openFull.getItemMeta().getPersistentDataContainer();
        openPdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_DEATH_EFFECT_GUI");
        openFull.setItemMeta(openFull.getItemMeta());
        inv.setItem(0, openFull);

        java.util.Set<com.thenerdcj.cosmetic.DeathEffect> owned = effectManager.getOwnedEffects(player.getUniqueId());
        int slot = 18;
        for (com.thenerdcj.cosmetic.DeathEffect effect : owned) {
            if (slot > 44) break;
            if (effect.isNone()) continue;

            ItemStack item = GUIUtils.createItem(Material.SKELETON_SKULL,
                    "§e" + effect.getDisplayName(),
                    "§7" + effect.getDescription(),
                    "§7Rarity: " + effect.getRarity().getColorCode() + effect.getRarity().getDisplayName(),
                    "",
                    "§eClick to open Death Effects menu");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_DEATH_EFFECT_GUI");
            item.setItemMeta(item.getItemMeta());
            inv.setItem(slot++, item);
        }

        if (owned.isEmpty() || owned.size() <= 1) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo death effects unlocked yet",
                    "§7Earn them via prestige or the Slayer Shop"));
        }
    }

    /**
     * Preview for Death Messages tab (new cosmetic system).
     */
    private void renderDeathMessagesPreview(Player player, Inventory inv) {
        var msgManager = plugin.getDeathMessageManager();
        if (msgManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cDeath Messages system unavailable"));
            return;
        }

        int collCount = msgManager.getMessageCollectionCount(player.getUniqueId());
        int total = com.thenerdcj.cosmetic.DeathMessageCosmetic.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.WRITABLE_BOOK, "§4§lDeath Messages",
                "§7Custom text on death and kills",
                "§7Collection: §a" + collCount + " / " + total + " §7messages unlocked");
        inv.setItem(4, title);

        com.thenerdcj.cosmetic.DeathMessageCosmetic active = msgManager.getActiveDeathMessage(player.getUniqueId());
        ItemStack current = GUIUtils.createItem(Material.NAME_TAG, "§e§lActive Death Message",
                active.isNone() ? "§7None" : active.getRarity().getColorCode() + active.getDisplayName());
        inv.setItem(8, current);

        ItemStack openFull = GUIUtils.createItem(Material.BOOK, "§6§lOpen Full Death Messages Menu");
        PersistentDataContainer openPdc = openFull.getItemMeta().getPersistentDataContainer();
        openPdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_DEATH_MESSAGE_GUI");
        openFull.setItemMeta(openFull.getItemMeta());
        inv.setItem(0, openFull);

        java.util.Set<com.thenerdcj.cosmetic.DeathMessageCosmetic> owned = msgManager.getOwnedMessages(player.getUniqueId());
        int slot = 18;
        for (com.thenerdcj.cosmetic.DeathMessageCosmetic msg : owned) {
            if (slot > 44) break;
            if (msg.isNone()) continue;
            ItemStack item = GUIUtils.createItem(Material.PAPER, msg.getRarity().getColorCode() + msg.getDisplayName(),
                    "§7" + msg.getDescription());
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_DEATH_MESSAGE_GUI");
            item.setItemMeta(item.getItemMeta());
            inv.setItem(slot++, item);
        }

        if (owned.isEmpty() || owned.size() <= 1) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo death messages unlocked yet",
                    "§7Earn them via prestige or the Slayer Shop"));
        }
    }

    /**
     * Basic preview for Backpack Skins tab (exploration).
     */
    private void renderBackpackSkinsPreview(Player player, Inventory inv) {
        var skinManager = plugin.getBackpackSkinManager();
        if (skinManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cBackpack Skins system unavailable"));
            return;
        }

        int collCount = skinManager.getOwnedSkins(player.getUniqueId()).size();
        int total = com.thenerdcj.cosmetic.BackpackSkin.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.CHEST, "§a§lBackpack Skins",
                "§7Cosmetic overrides for backpacks/storage",
                "§7Collection: §a" + collCount + " / " + total + " §7skins");
        inv.setItem(4, title);

        com.thenerdcj.cosmetic.BackpackSkin active = skinManager.getActiveSkin(player.getUniqueId());
        ItemStack current = GUIUtils.createItem(Material.PAPER, "§e§lActive Backpack Skin",
                active.isNone() ? "§7None" : active.getRarity().getColorCode() + active.getDisplayName());
        inv.setItem(8, current);

        ItemStack openFull = GUIUtils.createItem(Material.BOOK, "§6§lOpen Full Backpack Skins Menu");
        PersistentDataContainer openPdc = openFull.getItemMeta().getPersistentDataContainer();
        openPdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_BACKPACK_SKIN_GUI");
        openFull.setItemMeta(openFull.getItemMeta());
        inv.setItem(0, openFull);

        java.util.Set<com.thenerdcj.cosmetic.BackpackSkin> owned = skinManager.getOwnedSkins(player.getUniqueId());
        int slot = 18;
        for (com.thenerdcj.cosmetic.BackpackSkin skin : owned) {
            if (slot > 44) break;
            if (skin.isNone()) continue;

            boolean isActive = skin == active;

            ItemStack item = GUIUtils.createItem(Material.CHEST,
                    (isActive ? "§a§l★ " : "§e") + skin.getDisplayName(),
                    "§7" + skin.getDescription(),
                    "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                    "",
                    isActive ? "§aCurrently Active" : "§eClick to open Backpack Skins menu");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_BACKPACK_SKIN_GUI");
            item.setItemMeta(item.getItemMeta());
            inv.setItem(slot++, item);
        }

        if (owned.isEmpty() || owned.size() <= 1) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo backpack skins unlocked yet",
                    "§7Earn them via prestige or the Slayer Shop"));
        }
    }

    /**
     * Basic preview for Power Orb Skins tab (new system).
     */
    private void renderPowerOrbSkinsPreview(Player player, Inventory inv) {
        var skinManager = plugin.getPowerOrbSkinManager();
        if (skinManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cPower Orb Skins system unavailable"));
            return;
        }

        int collCount = skinManager.getOwnedSkins(player.getUniqueId()).size();
        int total = com.thenerdcj.cosmetic.PowerOrbSkin.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.BEACON, "§b§lPower Orb Skins",
                "§7Cosmetic overrides for Power Orbs",
                "§7Collection: §a" + collCount + " / " + total + " §7skins");
        inv.setItem(4, title);

        com.thenerdcj.cosmetic.PowerOrbSkin active = skinManager.getActiveSkin(player.getUniqueId());
        ItemStack current = GUIUtils.createItem(Material.PAPER, "§e§lActive Power Orb Skin",
                active.isNone() ? "§7None" : active.getRarity().getColorCode() + active.getDisplayName());
        inv.setItem(8, current);

        ItemStack openFull = GUIUtils.createItem(Material.BOOK, "§6§lOpen Full Power Orb Skins Menu");
        PersistentDataContainer openPdc = openFull.getItemMeta().getPersistentDataContainer();
        openPdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_POWER_ORB_SKIN_GUI");
        openFull.setItemMeta(openFull.getItemMeta());
        inv.setItem(0, openFull);

        inv.setItem(22, GUIUtils.createItem(Material.BEACON, "§7Power Orb Skins",
                "§7Foundation + GUI complete. Ready for orb item integration."));
    }

    /**
     * Preview for Minion Skins tab (new system).
     * Shows collection progress, active theme, and button to open full dedicated GUI.
     */
    private void renderMinionSkinsPreview(Player player, Inventory inv) {
        var skinManager = plugin.getMinionSkinManager();
        if (skinManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cMinion Skins system unavailable"));
            return;
        }

        int collCount = skinManager.getOwnedSkins(player.getUniqueId()).size();
        int total = com.thenerdcj.cosmetic.MinionSkin.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.COBBLESTONE, "§6§lMinion Skins",
                "§7Themed appearances for your island minions",
                "§7Collection: §a" + collCount + " / " + total + " §7skins",
                "§7Active skin applies to all minions on your islands");
        inv.setItem(4, title);

        com.thenerdcj.cosmetic.MinionSkin active = skinManager.getActiveSkin(player.getUniqueId());
        ItemStack current = GUIUtils.createItem(Material.PAPER, "§e§lActive Minion Theme",
                active.isNone() ? "§7Default appearance" : active.getRarity().getColorCode() + active.getDisplayName());
        inv.setItem(8, current);

        ItemStack openFull = GUIUtils.createItem(Material.BOOK, "§6§lOpen Full Minion Skins Menu");
        ItemMeta openMeta = openFull.getItemMeta();
        if (openMeta != null) {
            PersistentDataContainer openPdc = openMeta.getPersistentDataContainer();
            openPdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_MINION_SKIN_GUI");
            openFull.setItemMeta(openMeta);
        }
        inv.setItem(0, openFull);

        // Placeholder grid hint (actual application visible on-island)
        inv.setItem(22, GUIUtils.createItem(Material.COBBLESTONE, "§7Minion Skins",
                "§7Unlock via Prestige or Slayer Shop",
                "§7Active theme changes minion helmet visuals + light effects"));
    }

    /**
     * Preview for Island Furniture / Housing tab (foundation).
     */
    private void renderFurniturePreview(Player player, Inventory inv) {
        var furnManager = plugin.getIslandFurnitureManager();
        if (furnManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cHousing system unavailable"));
            return;
        }

        int count = furnManager.getCollectionCount(player.getUniqueId());
        int total = com.thenerdcj.cosmetic.IslandFurnitureType.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.CHEST, "§6§lIsland Housing Decor",
                "§7Furniture & decorations for your island",
                "§7Collection: §a" + count + " / " + total + " §7pieces");
        inv.setItem(4, title);

        ItemStack open = GUIUtils.createItem(Material.BOOK, "§6§lOpen Furniture Catalog");
        ItemMeta m = open.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OPEN_FURNITURE_GUI");
            open.setItemMeta(m);
        }
        inv.setItem(0, open);

        // Live active set bonuses (pride visuals when full themed sets are placed on *this* island)
        String activeSetsLine = "§6Active set bonuses: complete themed sets (Celestial/Slayer/Mystic) for glowing + pride particles + XP";
        try {
            var isle = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (isle != null && furnManager != null) {
                java.util.Set<String> active = furnManager.getActiveSetBonuses(isle.getId());
                if (!active.isEmpty()) {
                    activeSetsLine = "§a★ Active on this island: §e" + String.join(", ", active) + " §7(glowing pieces + visitor pride)";
                }
            }
        } catch (Exception ignored) {}

        inv.setItem(22, GUIUtils.createItem(Material.CHEST, "§7Island Furniture",
                "§7Unlock via Prestige or Slayer Shop",
                "§7Place decorative pieces on your island",
                activeSetsLine,
                "§7Shift+Click items for preview hologram. Use /furniture list to manage placed."));
    }

    /**
     * Preview for Island Music & Ambience tab.
     */
    private void renderMusicPreview(Player player, Inventory inv) {
        var musicManager = plugin.getIslandMusicManager();
        if (musicManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cMusic system unavailable"));
            return;
        }

        int count = musicManager.getCollectionCount(player.getUniqueId());
        int total = com.thenerdcj.cosmetic.IslandMusicType.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.NOTE_BLOCK, "§d§lIsland Music & Ambience",
                "§7Cosmetic sounds and music for your island",
                "§7Collection: §a" + count + " / " + total + " §7tracks");
        inv.setItem(4, title);

        ItemStack open = GUIUtils.createItem(Material.BOOK, "§d§lOpen Music Catalog");
        ItemMeta m = open.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OPEN_MUSIC_GUI");
            open.setItemMeta(m);
        }
        inv.setItem(0, open);

        inv.setItem(22, GUIUtils.createItem(Material.NOTE_BLOCK, "§7Island Music",
                "§7Unlock via Prestige or Slayer Shop",
                "§7Set active ambience that plays for visitors on your island"));
    }

    /**
     * Preview for Advanced Overhead Cosmetics tab.
     */
    private void renderOverheadPreview(Player player, Inventory inv) {
        var overheadManager = plugin.getOverheadCosmeticManager();
        if (overheadManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cOverhead system unavailable"));
            return;
        }

        int count = overheadManager.getOwned(player.getUniqueId()).size();
        int total = com.thenerdcj.cosmetic.OverheadCosmetic.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.END_ROD, "§5§lOverhead Cosmetics",
                "§7Floating visual effects above your head",
                "§7Collection: §a" + count + " / " + total + " §7effects");
        inv.setItem(4, title);

        ItemStack open = GUIUtils.createItem(Material.BOOK, "§5§lOpen Overhead Catalog");
        ItemMeta m = open.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OPEN_OVERHEAD_GUI");
            open.setItemMeta(m);
        }
        inv.setItem(0, open);

        inv.setItem(22, GUIUtils.createItem(Material.END_ROD, "§7Overhead Effects",
                "§7Unlock via Prestige or Slayer Shop",
                "§7Floating titles, halos, auras and more above your head",
                "§5Titles expansion: Champion, Mystic, Runic, Void, Celestial, Slayer Sigil, Ethereal + more"));
    }

    /**
     * Preview for Emotes tab.
     */
    private void renderEmotesPreview(Player player, Inventory inv) {
        var emoteManager = plugin.getEmoteCosmeticManager();
        if (emoteManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cEmotes system unavailable"));
            return;
        }

        int count = emoteManager.getOwned(player.getUniqueId()).size();
        int total = com.thenerdcj.cosmetic.EmoteCosmetic.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.TOTEM_OF_UNDYING, "§d§lEmotes",
                "§7Fun visual and chat emotes",
                "§7Collection: §a" + count + " / " + total);
        inv.setItem(4, title);

        ItemStack open = GUIUtils.createItem(Material.BOOK, "§d§lOpen Emotes Catalog");
        ItemMeta m = open.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OPEN_EMOTES_GUI");
            open.setItemMeta(m);
        }
        inv.setItem(0, open);

        inv.setItem(22, GUIUtils.createItem(Material.TOTEM_OF_UNDYING, "§7Emotes",
                "§7Unlock via Prestige or Slayer Shop",
                "§7Use fun emotes with /emote <name>",
                "§dAuto-triggers (join/kill/death) configurable in catalog GUI"));
    }

    /**
     * Preview for Structures tab.
     */
    private void renderStructuresPreview(Player player, Inventory inv) {
        var structManager = plugin.getIslandStructureManager();
        if (structManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cStructures system unavailable"));
            return;
        }

        int count = structManager.getCollectionCount(player.getUniqueId());
        int total = com.thenerdcj.cosmetic.IslandStructureCosmetic.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.BRICKS, "§6§lIsland Structures",
                "§7Larger decorative clusters for your island",
                "§7Collection: §a" + count + " / " + total);
        inv.setItem(4, title);

        ItemStack open = GUIUtils.createItem(Material.BOOK, "§6§lOpen Structures Catalog");
        ItemMeta m = open.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OPEN_STRUCTURES_GUI");
            open.setItemMeta(m);
        }
        inv.setItem(0, open);

        inv.setItem(22, GUIUtils.createItem(Material.BRICKS, "§7Structures",
                "§7Unlock via Prestige or Slayer Shop",
                "§7Place large cosmetic structures on your island"));
    }

    /**
     * Preview for Chat Bubbles tab.
     */
    private void renderChatBubblesPreview(Player player, Inventory inv) {
        var bubbleManager = plugin.getChatBubbleCosmeticManager();
        if (bubbleManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cChat Bubbles system unavailable"));
            return;
        }

        int count = bubbleManager.getCollectionCount(player.getUniqueId());
        int total = com.thenerdcj.cosmetic.ChatBubbleCosmetic.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.PAPER, "§d§lChat Bubbles",
                "§7Floating styled messages when you chat",
                "§7Collection: §a" + count + " / " + total);
        inv.setItem(4, title);

        ItemStack open = GUIUtils.createItem(Material.BOOK, "§d§lOpen Chat Bubbles Catalog");
        ItemMeta m = open.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OPEN_CHAT_BUBBLES_GUI");
            open.setItemMeta(m);
        }
        inv.setItem(0, open);

        // Show active if any
        var active = bubbleManager.getActive(player.getUniqueId());
        String activeLine = active != null && !active.isNone() ? "§aActive: " + active.getRarity().getColorCode() + active.getDisplayName() : "§7No bubble active";
        inv.setItem(22, GUIUtils.createItem(Material.PAPER, "§7Chat Bubbles",
                "§7Unlock via Prestige or Slayer Shop",
                activeLine,
                "§7Messages appear as floating text + particles"));
    }

    /**
     * Preview for Island Weather tab.
     */
    private void renderWeatherPreview(Player player, Inventory inv) {
        var weatherManager = plugin.getIslandWeatherCosmeticManager();
        if (weatherManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cIsland Weather system unavailable"));
            return;
        }

        int count = weatherManager.getCollectionCount(player.getUniqueId());
        int total = com.thenerdcj.cosmetic.IslandWeatherCosmetic.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.WATER_BUCKET, "§3§lIsland Weather",
                "§7Cosmetic particle weather for your island",
                "§7Collection: §a" + count + " / " + total);
        inv.setItem(4, title);

        ItemStack open = GUIUtils.createItem(Material.BOOK, "§3§lOpen Weather Catalog");
        ItemMeta m = open.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OPEN_WEATHER_GUI");
            open.setItemMeta(m);
        }
        inv.setItem(0, open);

        inv.setItem(22, GUIUtils.createItem(Material.WATER_BUCKET, "§7Island Weather",
                "§7Unlock via Prestige or Slayer Shop",
                "§7Set active while on your island (visible to visitors)"));
    }

    /**
     * Preview for Accessories tab.
     */
    private void renderAccessoriesPreview(Player player, Inventory inv) {
        var accManager = plugin.getAccessoryCosmeticManager();
        if (accManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cAccessories system unavailable"));
            return;
        }

        int count = accManager.getOwned(player.getUniqueId()).size();
        int total = com.thenerdcj.cosmetic.AccessoryCosmetic.values().length - 1;

        ItemStack title = GUIUtils.createItem(Material.NETHER_STAR, "§6§lAccessories",
                "§7Small floating cosmetic items around you",
                "§7Collection: §a" + count + " / " + total);
        inv.setItem(4, title);

        ItemStack open = GUIUtils.createItem(Material.BOOK, "§6§lOpen Accessories Catalog");
        ItemMeta m = open.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "OPEN_ACCESSORIES_GUI");
            open.setItemMeta(m);
        }
        inv.setItem(0, open);

        inv.setItem(22, GUIUtils.createItem(Material.NETHER_STAR, "§7Accessories",
                "§7Unlock via Prestige or Slayer Shop",
                "§7Floating visuals around the player"));
    }

    /**
     * Renders the Tags tab in the Wardrobe (pagination + rarity filtering).
     * Follows the exact same UX we built for Pets and Cosmetics tabs.
     */
    private void renderTagsPreview(Player player, Inventory inv) {
        var tagManager = plugin.getPlayerTagManager();
        if (tagManager == null) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cTags system not loaded"));
            return;
        }

        // Reuse the same PetState map for simplicity (or we could make a shared state class later)
        PetState state = petStates.computeIfAbsent(player.getUniqueId(), k -> new PetState());

        ItemStack title = GUIUtils.createItem(Material.NAME_TAG, "§5§lCosmetic Tags",
                "§7Appear next to your name in chat & tab",
                "§7Click to equip • Filter by rarity");
        inv.setItem(4, title);

        ItemStack current = GUIUtils.createItem(Material.PAPER, "§e§lActive Tag",
                tagManager.getActiveTag(player.getUniqueId()).getTagText() + " §f" +
                tagManager.getActiveTag(player.getUniqueId()).getDisplayName());
        inv.setItem(8, current);

        // Open dedicated full Tags GUI (like Pets tab)
        ItemStack openFullTags = GUIUtils.createItem(Material.BOOK, "§6§lOpen Full Tags Menu");
        PersistentDataContainer openTagPdc = openFullTags.getItemMeta().getPersistentDataContainer();
        openTagPdc.set(ACTION_KEY, PersistentDataType.STRING, "OPEN_TAG_GUI");
        openFullTags.setItemMeta(openFullTags.getItemMeta());
        inv.setItem(0, openFullTags);

        // Rarity filters
        String[] filters = {"ALL", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
        int[] filterSlots = {9,10,11,12,13,14,15};
        for (int i = 0; i < filters.length && i < filterSlots.length; i++) {
            String f = filters[i];
            boolean sel = f.equals(state.filter);
            ItemStack fi = GUIUtils.createItem(sel ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    (sel ? "§a§l" : "§7") + f);
            PersistentDataContainer fp = fi.getItemMeta().getPersistentDataContainer();
            fp.set(ACTION_KEY, PersistentDataType.STRING, "TAGS_FILTER_" + f);
            fi.setItemMeta(fi.getItemMeta());
            inv.setItem(filterSlots[i], fi);
        }

        // Owned + filtered (now using TagInstance)
        java.util.List<com.thenerdcj.tags.TagInstance> ownedList = new java.util.ArrayList<>(tagManager.getOwnedTags(player.getUniqueId()));
        java.util.List<com.thenerdcj.tags.PlayerTag> filtered = new java.util.ArrayList<>();
        for (var inst : ownedList) {
            com.thenerdcj.tags.PlayerTag t = inst.getType();
            if (t.isNone()) continue;
            if ("ALL".equals(state.filter) || t.getRarity().name().equalsIgnoreCase(state.filter)) {
                filtered.add(t);
            }
        }

        int itemsPerPage = 21;
        int start = state.page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, filtered.size());

        int[] slots = {18,19,20,21,22,23,24,27,28,29,30,31,32,33,36,37,38,39,40,41,42};

        com.thenerdcj.tags.PlayerTag active = tagManager.getActiveTag(player.getUniqueId());

        int idx = 0;
        for (int i = start; i < end && idx < slots.length; i++, idx++) {
            com.thenerdcj.tags.PlayerTag tag = filtered.get(i);
            boolean isActive = tag == active;

            ItemStack item = GUIUtils.createItem(Material.NAME_TAG,
                    (isActive ? "§a§l★ " : "§e") + tag.getTagText() + " §f" + tag.getDisplayName(),
                    "§7Rarity: " + tag.getRarity().getColorCode() + tag.getRarity().getDisplayName(),
                    "§7" + tag.getDescription(),
                    "",
                    isActive ? "§aCurrently Equipped" : "§eClick to equip");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "TAG_EQUIP_" + tag.name());
            item.setItemMeta(item.getItemMeta());
            inv.setItem(slots[idx], item);
        }

        // Pagination
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) itemsPerPage));
        if (state.page > 0) {
            ItemStack prev = GUIUtils.createItem(Material.ARROW, "§e§lPrevious");
            prev.getItemMeta().getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "TAGS_PAGE_PREV");
            prev.setItemMeta(prev.getItemMeta());
            inv.setItem(45, prev);
        }
        inv.setItem(49, GUIUtils.createItem(Material.PAPER, "§7Page " + (state.page+1) + " / " + totalPages));
        if (state.page < totalPages-1) {
            ItemStack next = GUIUtils.createItem(Material.ARROW, "§e§lNext");
            next.getItemMeta().getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "TAGS_PAGE_NEXT");
            next.setItemMeta(next.getItemMeta());
            inv.setItem(53, next);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTitle().startsWith("§6§lWardrobe")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;

            PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
            String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
            if (action == null) return;

            boolean isShift = event.isShiftClick();
            boolean isRight = event.isRightClick();

            if (action.equals("VIEW_ARMOR")) {
                openWardrobe(player, View.ARMOR);
                return;
            }
            if (action.equals("VIEW_EQUIPMENT")) {
                openWardrobe(player, View.EQUIPMENT);
                return;
            }
            if (action.equals("VIEW_COSMETICS")) {
                openWardrobe(player, View.COSMETICS);
                return;
            }
            if (action.equals("VIEW_PETS")) {
                openWardrobe(player, View.PETS);
                return;
            }
            if (action.equals("VIEW_TAGS")) {
                openWardrobe(player, View.TAGS);
                return;
            }
            if (action.equals("VIEW_WINGS")) {
                openWardrobe(player, View.WINGS);
                return;
            }
            if (action.equals("VIEW_RUNES")) {
                openWardrobe(player, View.RUNES);
                return;
            }
            if (action.equals("VIEW_HELMET_SKINS")) {
                openWardrobe(player, View.HELMET_SKINS);
                return;
            }
            if (action.equals("VIEW_DEATH_EFFECTS")) {
                openWardrobe(player, View.DEATH_EFFECTS);
                return;
            }
            if (action.equals("VIEW_DEATH_MESSAGES")) {
                openWardrobe(player, View.DEATH_MESSAGES);
                return;
            }
            if (action.equals("VIEW_BACKPACK_SKINS")) {
                openWardrobe(player, View.BACKPACK_SKINS);
                return;
            }
            if (action.equals("VIEW_POWER_ORB_SKINS")) {
                openWardrobe(player, View.POWER_ORB_SKINS);
                return;
            }
            if (action.equals("VIEW_MINION_SKINS")) {
                openWardrobe(player, View.MINION_SKINS);
                return;
            }
            if (action.equals("VIEW_FURNITURE")) {
                openWardrobe(player, View.FURNITURE);
                return;
            }
            if (action.equals("VIEW_MUSIC")) {
                openWardrobe(player, View.MUSIC);
                return;
            }
            if (action.equals("VIEW_OVERHEAD")) {
                openWardrobe(player, View.OVERHEAD);
                return;
            }
            if (action.equals("VIEW_EMOTES")) {
                openWardrobe(player, View.EMOTES);
                return;
            }
            if (action.equals("VIEW_STRUCTURES")) {
                openWardrobe(player, View.STRUCTURES);
                return;
            }
            if (action.equals("VIEW_CHAT_BUBBLES")) {
                openWardrobe(player, View.CHAT_BUBBLES);
                return;
            }
            if (action.equals("VIEW_WEATHER")) {
                openWardrobe(player, View.WEATHER);
                return;
            }
            if (action.equals("VIEW_ACCESSORIES")) {
                openWardrobe(player, View.ACCESSORIES);
                return;
            }

            // Slot actions: ARMOR_3 or EQUIP_5
            if (action.startsWith("ARMOR_") || action.startsWith("EQUIP_")) {
                boolean isArmor = action.startsWith("ARMOR_");
                int slot = Integer.parseInt(action.substring(action.indexOf('_') + 1));

                if (isShift) {
                    // Save current gear
                    String defaultName = (isArmor ? "Armor Set " : "Equipment Set ") + (slot + 1);
                    if (isArmor) {
                        wardrobeManager.saveCurrentArmor(player, slot, defaultName);
                    } else {
                        wardrobeManager.saveCurrentEquipment(player, slot, defaultName);
                    }
                    // Refresh
                    openWardrobe(player, isArmor ? View.ARMOR : View.EQUIPMENT);
                } else if (isRight) {
                    // Open options menu (rename / clear)
                    plugin.getWardrobeSlotOptionsGUI().openOptions(player, slot, isArmor, isArmor ? "ARMOR" : "EQUIP");
                } else {
                    // Equip
                    int armorSlot = isArmor ? slot : -1;
                    int equipSlot = isArmor ? -1 : slot;

                    // If in armor view, equip the armor slot + last used equipment (or 0)
                    // For simplicity in v1: equip the chosen category + default 0 in the other
                    if (isArmor) {
                        wardrobeManager.equipSet(player, slot, 0);
                    } else {
                        wardrobeManager.equipSet(player, 0, slot);
                    }
                    // Refresh instead of close so player can immediately switch to Cosmetics tab
                    // to preview trails with the newly equipped set (great for combined previews)
                    openWardrobe(player, isArmor ? View.ARMOR : View.EQUIPMENT);
                }
            }

            // === COSMETICS / TRAIL PREVIEW ACTIONS ===
            if (action.equals("OPEN_TRAIL_GUI")) {
                player.closeInventory();
                plugin.getParticleTrailGUI().open(player);  // assumes getter or direct
                return;
            }
            if (action.equals("TRAIL_NONE")) {
                plugin.getParticleTrailManager().setActiveTrail(player, ParticleTrail.NONE);
                openWardrobe(player, View.COSMETICS);
                return;
            }
            if (action.startsWith("TRAIL_")) {
                String trailName = action.substring(6);
                try {
                    ParticleTrail trail = ParticleTrail.valueOf(trailName);
                    plugin.getParticleTrailManager().setActiveTrail(player, trail);
                    player.sendMessage("§aTrail activated: §e" + trail.getDisplayName());
                    openWardrobe(player, View.COSMETICS);
                } catch (Exception ignored) {
                    player.sendMessage("§cUnknown trail.");
                }
                return;
            }

            // === PETS TAB ACTIONS (updated for pagination + filtering) ===
            if (action.equals("OPEN_PET_GUI")) {
                player.closeInventory();
                plugin.getPetGUI().open(player);
                return;
            }
            if (action.equals("OPEN_RUNE_GUI")) {
                player.closeInventory();
                if (plugin.getRuneGUI() != null) {
                    plugin.getRuneGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_HELMET_SKIN_GUI")) {
                player.closeInventory();
                if (plugin.getHelmetSkinGUI() != null) {
                    plugin.getHelmetSkinGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_DEATH_EFFECT_GUI")) {
                player.closeInventory();
                if (plugin.getDeathEffectGUI() != null) {
                    plugin.getDeathEffectGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_DEATH_MESSAGE_GUI")) {
                player.closeInventory();
                if (plugin.getDeathMessageGUI() != null) {
                    plugin.getDeathMessageGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_BACKPACK_SKIN_GUI")) {
                player.closeInventory();
                if (plugin.getBackpackSkinGUI() != null) {
                    plugin.getBackpackSkinGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_POWER_ORB_SKIN_GUI")) {
                player.closeInventory();
                if (plugin.getPowerOrbSkinGUI() != null) {
                    plugin.getPowerOrbSkinGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_MINION_SKIN_GUI")) {
                player.closeInventory();
                if (plugin.getMinionSkinGUI() != null) {
                    plugin.getMinionSkinGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_FURNITURE_GUI")) {
                player.closeInventory();
                if (plugin.getIslandFurnitureGUI() != null) {
                    plugin.getIslandFurnitureGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_MUSIC_GUI")) {
                player.closeInventory();
                if (plugin.getIslandMusicGUI() != null) {
                    plugin.getIslandMusicGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_OVERHEAD_GUI")) {
                player.closeInventory();
                if (plugin.getOverheadCosmeticGUI() != null) {
                    plugin.getOverheadCosmeticGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_EMOTES_GUI")) {
                player.closeInventory();
                if (plugin.getEmoteCosmeticGUI() != null) {
                    plugin.getEmoteCosmeticGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_STRUCTURES_GUI")) {
                player.closeInventory();
                if (plugin.getIslandStructureGUI() != null) {
                    plugin.getIslandStructureGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_CHAT_BUBBLES_GUI")) {
                player.closeInventory();
                if (plugin.getChatBubbleGUI() != null) {
                    plugin.getChatBubbleGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_WEATHER_GUI")) {
                player.closeInventory();
                if (plugin.getIslandWeatherGUI() != null) {
                    plugin.getIslandWeatherGUI().open(player);
                }
                return;
            }
            if (action.equals("OPEN_ACCESSORIES_GUI")) {
                player.closeInventory();
                if (plugin.getAccessoryCosmeticGUI() != null) {
                    plugin.getAccessoryCosmeticGUI().open(player);
                }
                return;
            }
            if (action.startsWith("PET_WARDROBE_")) {
                // Find pet by type + name (robust across filter/page)
                String data = action.substring(13);
                String[] parts = data.split("_", 2);
                if (parts.length < 2) return;

                try {
                    PetType type = PetType.valueOf(parts[0]);
                    String custom = parts[1].replace("_", " ");

                    List<CosmeticPet> owned = plugin.getPetManager().getOwnedPets(player.getUniqueId());
                    for (CosmeticPet pet : owned) {
                        if (pet.getType() == type && pet.getCustomName().equalsIgnoreCase(custom)) {
                            if (event.isRightClick()) {
                                // Right-click → open dedicated Pet GUI (has full skin selection)
                                player.closeInventory();
                                plugin.getPetGUI().open(player);
                            } else {
                                plugin.getPetManager().setActivePet(player, pet);
                                player.sendMessage("§aEquipped & previewing: §d" + pet.getCustomName());
                                openWardrobe(player, View.PETS);
                            }
                            return;
                        }
                    }
                } catch (Exception ignored) {}
                return;
            }

            // === NEW: Category Filters ===
            if (action.startsWith("FILTER_")) {
                String newFilter = action.substring(7);
                CosmeticsState state = cosmeticsStates.computeIfAbsent(player.getUniqueId(), k -> new CosmeticsState());
                state.filter = newFilter;
                state.page = 0; // reset to first page when changing filter
                openWardrobe(player, View.COSMETICS);
                return;
            }

            // === NEW: Pagination ===
            if (action.equals("PAGE_PREV")) {
                CosmeticsState state = cosmeticsStates.computeIfAbsent(player.getUniqueId(), k -> new CosmeticsState());
                if (state.page > 0) state.page--;
                openWardrobe(player, View.COSMETICS);
                return;
            }
            if (action.equals("PAGE_NEXT")) {
                CosmeticsState state = cosmeticsStates.computeIfAbsent(player.getUniqueId(), k -> new CosmeticsState());
                state.page++;
                openWardrobe(player, View.COSMETICS);
                return;
            }

            // === PETS TAB: Rarity Filters + Pagination (item 4) ===
            if (action.startsWith("PETS_FILTER_")) {
                String newFilter = action.substring(12);
                PetState pstate = petStates.computeIfAbsent(player.getUniqueId(), k -> new PetState());
                pstate.filter = newFilter;
                pstate.page = 0;
                openWardrobe(player, View.PETS);
                return;
            }
            if (action.equals("PETS_PAGE_PREV")) {
                PetState pstate = petStates.computeIfAbsent(player.getUniqueId(), k -> new PetState());
                if (pstate.page > 0) pstate.page--;
                openWardrobe(player, View.PETS);
                return;
            }
            if (action.equals("PETS_PAGE_NEXT")) {
                PetState pstate = petStates.computeIfAbsent(player.getUniqueId(), k -> new PetState());
                pstate.page++;
                openWardrobe(player, View.PETS);
                return;
            }

            // === TAGS TAB ACTIONS (Wardrobe integration) ===
            if (action.equals("OPEN_TAG_GUI")) {
                player.closeInventory();
                if (plugin.getTagGUI() != null) {
                    plugin.getTagGUI().open(player);
                }
                return;
            }
            if (action.startsWith("TAG_EQUIP_")) {
                String tagName = action.substring(10);
                try {
                    com.thenerdcj.tags.PlayerTag tag = com.thenerdcj.tags.PlayerTag.valueOf(tagName);
                    var tm = plugin.getPlayerTagManager();
                    if (tm != null) {
                        tm.setActiveTag(player, tag);
                    }
                    openWardrobe(player, View.TAGS);
                } catch (Exception ignored) {}
                return;
            }
            if (action.startsWith("TAGS_FILTER_")) {
                String f = action.substring(12);
                PetState ps = petStates.computeIfAbsent(player.getUniqueId(), k -> new PetState());
                ps.filter = f;
                ps.page = 0;
                openWardrobe(player, View.TAGS);
                return;
            }
            if (action.equals("TAGS_PAGE_PREV")) {
                PetState ps = petStates.computeIfAbsent(player.getUniqueId(), k -> new PetState());
                if (ps.page > 0) ps.page--;
                openWardrobe(player, View.TAGS);
                return;
            }
            if (action.equals("TAGS_PAGE_NEXT")) {
                PetState ps = petStates.computeIfAbsent(player.getUniqueId(), k -> new PetState());
                ps.page++;
                openWardrobe(player, View.TAGS);
                return;
            }

            // Elytra Wings tab actions
            if (action.startsWith("WING_EQUIP_")) {
                String wingName = action.substring(11);
                try {
                    com.thenerdcj.wings.ElytraWing wing = com.thenerdcj.wings.ElytraWing.valueOf(wingName);
                    var wm = plugin.getElytraWingManager();
                    if (wm != null) {
                        wm.setActiveWing(player, wing);
                    }
                    openWardrobe(player, View.WINGS);
                } catch (Exception ignored) {}
                return;
            }
        }
    }
}