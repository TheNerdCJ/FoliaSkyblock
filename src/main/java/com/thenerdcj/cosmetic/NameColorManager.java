package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cosmetic name colors for player chat / display names.
 * - Default: white (§f)
 * - Overrides only the player name portion (rank colors from config always respected)
 * - Unlocks via prestige / tokens / shop (basic support)
 */
public class NameColorManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    // Active color per player
    private final Map<UUID, NameColor> activeColors = new ConcurrentHashMap<>();

    // Owned colors (for future gating)
    private final Map<UUID, Set<NameColor>> ownedColors = new ConcurrentHashMap<>();

    public NameColorManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public NameColor getActiveNameColor(UUID uuid) {
        return activeColors.getOrDefault(uuid, NameColor.NONE);
    }

    public NameColor getActiveNameColor(Player player) {
        return getActiveNameColor(player.getUniqueId());
    }

    public void setActiveNameColor(Player player, NameColor color) {
        if (color == null) color = NameColor.NONE;
        UUID uuid = player.getUniqueId();

        // For now allow all (expand with owns check later)
        activeColors.put(uuid, color);
        savePlayer(uuid);

        threadSafety.runOnMainThread(() -> {
            if (player.isOnline() && plugin.getPlayerTagManager() != null) {
                plugin.getPlayerTagManager().refreshPlayerDisplay(player);
            }
        });

        if (color.isNone()) {
            player.sendMessage("§7Name color reset to white.");
        } else {
            player.sendMessage("§aName color set to " + color.getColorCode() + color.getDisplayName() + "§a.");
        }
    }

    public Set<NameColor> getOwnedColors(UUID uuid) {
        return ownedColors.computeIfAbsent(uuid, k -> EnumSet.noneOf(NameColor.class));
    }

    public boolean hasNameColor(UUID uuid, NameColor color) {
        if (color.isNone()) return true;
        return getOwnedColors(uuid).contains(color);
    }

    public void addNameColor(UUID uuid, NameColor color) {
        if (color == null || color.isNone()) return;
        getOwnedColors(uuid).add(color);
        savePlayer(uuid);
    }

    public void grantPrestigeNameColorUnlocks(Player player, int newPrestige) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        boolean granted = false;

        for (NameColor nc : NameColor.values()) {
            if (nc.isNone()) continue;
            if (nc.getMinPrestige() > 0 && nc.getMinPrestige() <= newPrestige && !hasNameColor(uuid, nc)) {
                if (nc.getTokenCost() == 0) { // prestige free ones
                    addNameColor(uuid, nc);
                    granted = true;
                    player.sendMessage("§d§lName Color Unlocked! §7" + nc.getColorCode() + nc.getDisplayName());
                }
            }
        }

        if (granted) {
            savePlayer(uuid);
            if (plugin.getPlayerTagManager() != null) {
                plugin.getPlayerTagManager().refreshPlayerDisplay(player);
            }
        }
    }

    // Simple persistence hooks (expand with full DAO later)
    public void loadPlayer(UUID uuid) {
        // For this implementation, default to NONE or load from DB if methods exist
        String saved = null;
        try {
            if (plugin.getDatabaseManager() != null) {
                saved = plugin.getDatabaseManager().loadPlayerNameColor(uuid);
            }
        } catch (Exception ignored) {}

        NameColor color = NameColor.NONE;
        if (saved != null) {
            for (NameColor nc : NameColor.values()) {
                if (nc.getColorCode().equals(saved) || nc.name().equalsIgnoreCase(saved)) {
                    color = nc;
                    break;
                }
            }
        }
        activeColors.put(uuid, color);

        // Auto-grant commons for demo / basic support
        Set<NameColor> owned = getOwnedColors(uuid);
        for (NameColor nc : NameColor.values()) {
            if (nc.getMinPrestige() == 0 && nc.getTokenCost() == 0) {
                owned.add(nc);
            }
        }
    }

    public void savePlayer(UUID uuid) {
        NameColor color = activeColors.getOrDefault(uuid, NameColor.NONE);
        try {
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().savePlayerNameColor(uuid, color.getColorCode());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[NameColorManager] Failed to save for " + uuid + ": " + e.getMessage());
        }
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
        // Refresh so color applies immediately
        if (plugin.getPlayerTagManager() != null) {
            threadSafety.runOnMainThreadLater(() -> {
                if (player.isOnline()) plugin.getPlayerTagManager().refreshPlayerDisplay(player);
            }, 5L);
        }
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        savePlayer(uuid);
        activeColors.remove(uuid);
        ownedColors.remove(uuid);
    }

    public String getFormattedNameWithColor(UUID uuid, String playerName) {
        NameColor nc = getActiveNameColor(uuid);
        if (nc == null || nc.isNone()) {
            return "§f" + playerName;
        }
        return nc.getColorCode() + playerName + "§r";
    }
}
