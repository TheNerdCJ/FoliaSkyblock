package com.thenerdcj.tags;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.pets.PetRarity;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cosmetic Player Tags (earned prefixes/suffixes shown in chat + tab).
 *
 * Follows the exact same high-quality patterns as:
 * - PetManager (collection/rarity XP, prestige grants, variants, owned list)
 * - ParticleTrailManager (prestige + slayer token unlocks, grant methods)
 *
 * One active tag per player. Composes with existing Rank prefix.
 */
public class PlayerTagManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    // Per-player data - now using TagInstance to support variants (like CosmeticPet)
    private final Map<UUID, Set<TagInstance>> ownedTags = new ConcurrentHashMap<>();
    private final Map<UUID, TagInstance> activeTags = new ConcurrentHashMap<>();

    // Collection tracking (reuses the exact same pattern + XP we built for pets)
    private final Map<UUID, Set<PlayerTag>> tagCollection = new ConcurrentHashMap<>();
    private static final int[] TAG_COLLECTION_MILESTONES = {3, 5, 8, 12, 20};

    public PlayerTagManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<TagInstance> getOwnedTags(UUID uuid) {
        return ownedTags.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    /**
     * Returns the base PlayerTag of the active tag (for backward compat with existing code).
     */
    public PlayerTag getActiveTag(UUID uuid) {
        TagInstance inst = activeTags.get(uuid);
        return inst != null ? inst.getType() : PlayerTag.NONE;
    }

    /**
     * Returns the full active TagInstance (with variant).
     */
    public TagInstance getActiveTagInstance(UUID uuid) {
        return activeTags.getOrDefault(uuid, new TagInstance(PlayerTag.NONE));
    }

    public void setActiveTag(Player player, PlayerTag tag) {
        setActiveTag(player, tag, PlayerTagVariant.NONE);
    }

    public void setActiveTag(Player player, PlayerTag tag, PlayerTagVariant variant) {
        UUID uuid = player.getUniqueId();

        if (tag == null || tag.isNone()) {
            activeTags.remove(uuid);
        } else {
            // Check if player owns this base tag (any variant counts)
            boolean owns = getOwnedTags(uuid).stream().anyMatch(t -> t.getType() == tag);
            if (!owns) {
                player.sendMessage("§cYou haven't unlocked this tag yet.");
                return;
            }

            TagInstance inst = new TagInstance(tag, variant);
            activeTags.put(uuid, inst);
        }

        // Immediately update display (chat + tab)
        refreshPlayerDisplay(player);

        // Also refresh overhead nametag if the manager exists
        if (plugin.getPlayerNametagManager() != null) {
            plugin.getPlayerNametagManager().refreshNametag(player);
        }

        if (tag != null && !tag.isNone()) {
            player.sendMessage("§aTag equipped: " + tag.getTagText() + " §7" + tag.getDisplayName());
        } else {
            player.sendMessage("§7Tag removed.");
        }

        savePlayer(uuid);
    }

    public void addTag(UUID uuid, PlayerTag tag) {
        addTag(uuid, tag, PlayerTagVariant.NONE);
    }

    public void addTag(UUID uuid, PlayerTag tag, PlayerTagVariant variant) {
        if (tag == null || tag.isNone()) return;

        Set<TagInstance> owned = getOwnedTags(uuid);
        TagInstance inst = new TagInstance(tag, variant);

        boolean wasNew = owned.add(inst);  // equals/hash based on base type

        if (wasNew) {
            awardCollectionXPIfNew(uuid, tag);
        }
    }

    public boolean hasTag(UUID uuid, PlayerTag tag) {
        return getOwnedTags(uuid).stream().anyMatch(t -> t.getType() == tag);
    }

    // ==================== DISPLAY COMPOSITION ====================

    /**
     * Returns the full cosmetic display string for a player (Rank + Active Tag + Name).
     * Used by chat, /list, and display name updates.
     * Now includes island prestige/level prefix for desired chat format: <P{prestige}/L{level}> [Rank] (Tag) name
     */
    public String getComposedDisplayName(UUID uuid, String playerName) {
        if (plugin.getRankManager() == null) {
            PlayerTag tag = getActiveTag(uuid);
            String base = (tag.isNone() ? "" : tag.getFormattedTag() + " ") + playerName;
            return prependIslandPrefix(uuid, base);
        }

        String rankDisplay = plugin.getRankManager().getPlayerDisplayName(uuid, playerName);

        PlayerTag tag = getActiveTag(uuid);
        String base;
        if (tag.isNone() || tag.getTagText().isEmpty()) {
            base = rankDisplay;
        } else {
            // Place tag after rank prefix, before name (common cosmetic pattern)
            // Example: [Rank] ★Legend PlayerName
            base = rankDisplay.replace(playerName, tag.getTagText() + "§r " + playerName);
        }

        // Apply wardrobe name color cosmetic to the player name portion only (default white, rank from config)
        base = applyNameColor(uuid, playerName, base);

        return prependIslandPrefix(uuid, base);
    }

    /**
     * Prepends <P{prestige}/L{level}> using island data (prefers current world, falls back to overworld).
     */
    private String prependIslandPrefix(UUID uuid, String baseDisplay) {
        if (plugin.getIslandManager() == null) {
            return baseDisplay;
        }
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) {
            // Offline: omit or default 0/1; for chat usually online anyway
            return baseDisplay;
        }
        Island island = plugin.getIslandManager().getIslandForPlayer(p);
        int prestige = 0;
        int level = 1;
        if (island != null) {
            level = island.getLevel();
            if (plugin.getPrestigeManager() != null) {
                prestige = plugin.getPrestigeManager().getPrestigeLevel(island);
            }
        }
        String prestigePrefix = "§8<§dP" + prestige + "§8/§aL§b" + level + "§8>§r ";
        return prestigePrefix + baseDisplay;
    }

    /**
     * Applies active name color from wardrobe cosmetic to the player name part.
     * Keeps rank portion colors exactly as configured in ranks.yml.
     */
    private String applyNameColor(UUID uuid, String playerName, String composed) {
        if (plugin.getNameColorManager() == null || playerName == null || playerName.isEmpty()) {
            return composed;
        }
        com.thenerdcj.cosmetic.NameColor nc = plugin.getNameColorManager().getActiveNameColor(uuid);
        if (nc == null || nc.isNone()) {
            // ensure white
            return composed.replace(playerName, "§f" + playerName);
        }
        String code = nc.getColorCode();
        // Replace the name occurrence (handles after rank/tag)
        return composed.replace(playerName, code + playerName + "§r");
    }

    /**
     * Applies the current rank + tag to the player's displayName and playerListName.
     * Called on tag change, rank change, login, etc.
     */
    public void refreshPlayerDisplay(Player player) {
        if (player == null || !player.isOnline()) return;

        String composed = getComposedDisplayName(player.getUniqueId(), player.getName());

        threadSafety.runOnMainThread(() -> {
            player.setDisplayName(composed);
            // Do not set playerListName here; tab list is managed by IslandWorthManager to include cosmetics + worth/level suffix
            // Re-apply tab to ensure latest cosmetics + worth after name/tag/rank change
            if (plugin.getIslandWorthManager() != null) {
                plugin.getIslandWorthManager().updatePlayerTabList(player);
            }
        });
    }

    // ==================== PRESTIGE + SLAYER INTEGRATION ====================

    public void grantPrestigeTagUnlocks(Player player, int newPrestigeLevel) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        Set<TagInstance> owned = getOwnedTags(uuid);
        boolean grantedAny = false;

        for (PlayerTag tag : PlayerTag.values()) {
            if (tag.isNone()) continue;
            boolean alreadyOwns = owned.stream().anyMatch(inst -> inst.getType() == tag);
            if (alreadyOwns) continue;
            if (tag.getMinPrestige() <= newPrestigeLevel && tag.getTokenCost() == 0) {
                addTag(uuid, tag);
                grantedAny = true;
                player.sendMessage("§d§lPrestige Tag Reward! §7Unlocked " + tag.getTagText() + " §e" + tag.getDisplayName());
            }
        }

        if (grantedAny) {
            savePlayer(uuid);
            refreshPlayerDisplay(player);
        }
    }

    public boolean unlockTagWithTokens(Player player, PlayerTag tag, int tokensToConsume) {
        if (player == null || tag == null || tag.isNone()) return false;
        UUID uuid = player.getUniqueId();

        if (hasTag(uuid, tag)) {
            player.sendMessage("§eYou already own this tag.");
            return false;
        }

        Island island = plugin.getIslandManager().getIsland(uuid, player.getWorld().getEnvironment());
        int prestige = (island != null && plugin.getPrestigeManager() != null)
                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

        if (prestige < tag.getMinPrestige()) {
            player.sendMessage("§cRequires Prestige " + tag.getMinPrestige() + ".");
            return false;
        }

        if (tokensToConsume > 0) {
            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, tokensToConsume)) {
                addTag(uuid, tag);
                savePlayer(uuid);
                player.sendMessage("§aPurchased tag: " + tag.getTagText() + " §e" + tag.getDisplayName() +
                        " §7for " + tokensToConsume + " tokens!");
                return true;
            } else {
                player.sendMessage("§cNot enough Slayer Tokens!");
                return false;
            }
        } else {
            addTag(uuid, tag);
            savePlayer(uuid);
            player.sendMessage("§aUnlocked tag (Prestige reward): " + tag.getTagText() + " §e" + tag.getDisplayName());
            return true;
        }
    }

    // ==================== COLLECTION / RARITY XP (identical pattern to pets) ====================

    private void awardCollectionXPIfNew(UUID uuid, PlayerTag tag) {
        Set<PlayerTag> collected = tagCollection.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        if (collected.add(tag)) {
            int xp = tag.getRarity().getXpReward();

            plugin.getDatabaseManager().savePlayerTagCollectionEntry(uuid, tag.name());

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (plugin.getConfig().getBoolean("tags.collection-xp.enabled", true)) {
                    plugin.getIslandManager().addIslandXp(player, xp);
                    player.sendMessage("§a§lTag Collection §7» Unlocked " + tag.getTagText() + " §e" + tag.getDisplayName() +
                            " §7(§a+" + xp + " Island XP§7)");
                }

                // Check milestones
                int count = collected.size();
                for (int m : TAG_COLLECTION_MILESTONES) {
                    if (count == m) {
                        player.sendMessage("§6§l★ Tag Collector Milestone! ★ §e" + m + " unique tags collected!");
                        break;
                    }
                }
            }
        }
    }

    public int getTagCollectionCount(UUID uuid) {
        Set<PlayerTag> set = tagCollection.get(uuid);
        return set == null ? 0 : set.size();
    }

    // ==================== PERSISTENCE ====================

    public void loadPlayer(UUID uuid) {
        // Load owned (support "TYPE" or "TYPE:VARIANT" format)
        Set<String> ownedIds = plugin.getDatabaseManager().loadPlayerTags(uuid);
        Set<TagInstance> owned = ConcurrentHashMap.newKeySet();
        for (String entry : ownedIds) {
            try {
                String[] parts = entry.split(":", 2);
                PlayerTag t = PlayerTag.valueOf(parts[0]);
                PlayerTagVariant v = PlayerTagVariant.NONE;
                if (parts.length > 1) {
                    try { v = PlayerTagVariant.valueOf(parts[1]); } catch (Exception ignored) {}
                }
                if (!t.isNone()) {
                    owned.add(new TagInstance(t, v));
                }
            } catch (Exception ignored) {}
        }
        ownedTags.put(uuid, owned);

        // Load collection (still base types only)
        Set<String> coll = plugin.getDatabaseManager().loadPlayerTagCollection(uuid);
        Set<PlayerTag> collected = ConcurrentHashMap.newKeySet();
        for (String id : coll) {
            try { collected.add(PlayerTag.valueOf(id)); } catch (Exception ignored) {}
        }
        tagCollection.put(uuid, collected);

        // Load active (support variant)
        String activeEntry = plugin.getDatabaseManager().loadActivePlayerTag(uuid);
        if (activeEntry != null) {
            try {
                String[] parts = activeEntry.split(":", 2);
                PlayerTag t = PlayerTag.valueOf(parts[0]);
                PlayerTagVariant v = PlayerTagVariant.NONE;
                if (parts.length > 1) {
                    try { v = PlayerTagVariant.valueOf(parts[1]); } catch (Exception ignored) {}
                }
                if (owned.stream().anyMatch(inst -> inst.getType() == t)) {
                    activeTags.put(uuid, new TagInstance(t, v));
                }
            } catch (Exception ignored) {}
        }
    }

    public void savePlayer(UUID uuid) {
        Set<TagInstance> owned = ownedTags.getOrDefault(uuid, Collections.emptySet());

        // For DB we store base type + variant as "TYPE:VARIANT" for simplicity
        Set<String> ownedIds = new HashSet<>();
        for (TagInstance inst : owned) {
            String entry = inst.getType().name();
            if (inst.getVariant() != PlayerTagVariant.NONE) {
                entry += ":" + inst.getVariant().name();
            }
            ownedIds.add(entry);
        }

        TagInstance activeInst = activeTags.get(uuid);
        String activeId = null;
        if (activeInst != null && !activeInst.getType().isNone()) {
            activeId = activeInst.getType().name();
            if (activeInst.getVariant() != PlayerTagVariant.NONE) {
                activeId += ":" + activeInst.getVariant().name();
            }
        }

        plugin.getDatabaseManager().savePlayerTags(uuid, ownedIds, activeId);
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
        // Apply current rank + tag display
        refreshPlayerDisplay(player);
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        savePlayer(uuid);
        ownedTags.remove(uuid);
        activeTags.remove(uuid);
        tagCollection.remove(uuid);
    }
}
