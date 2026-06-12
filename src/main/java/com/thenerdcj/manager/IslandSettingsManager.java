package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.database.IslandDAO;
import com.thenerdcj.island.IslandSettings;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandSettingsManager {

    private final FoliaSkyblock plugin;
    // Real LRU for large scale (access-order LinkedHashMap like worthCache for compression)
    private final Map<GridPosition, IslandSettings> settingsCache = Collections.synchronizedMap(new LinkedHashMap<GridPosition, IslandSettings>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GridPosition, IslandSettings> eldest) {
            return size() > 1000;
        }
    });

    public IslandSettingsManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        // Table creation is now centralized in DatabaseManager.createTables()
        // Persistence fully delegated to IslandDAO (withConnection + bridge complete)
        plugin.getThreadSafety().runRepeatingOnMainThread(this::cleanupCache, 36000L, 36000L);
    }

    /**
     * Non-blocking cached lookup. Returns cached value or a safe default (never joins).
     * Use this from event handlers, particle tasks, and other hot paths.
     * Schedules an async load/refresh in the background if not cached.
     */
    public IslandSettings getCachedSettings(GridPosition pos) {
        IslandSettings cached = settingsCache.get(pos);
        if (cached != null) {
            return cached;
        }
        // Schedule async load (fire and forget) so next call will hit cache
        getSettings(pos); // this populates the cache asynchronously
        return new IslandSettings(pos); // safe default (all false / blue / size 0 etc.)
    }

    public CompletableFuture<IslandSettings> getSettings(GridPosition pos) {
        IslandSettings cached = settingsCache.get(pos);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        // Delegate to IslandDAO (withConnection; removes all direct conn usage from this manager)
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        return dao.loadIslandSettings(pos).thenApply(settings -> {
            settingsCache.put(pos, settings);
            // Auto-create default row on first access (mirrors prior behavior)
            // Note: DAO load does not auto-save; save explicitly if needed by callers on toggle.
            return settings;
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load island settings via DAO: " + ex.getMessage());
            IslandSettings def = new IslandSettings(pos);
            settingsCache.put(pos, def);
            return def;
        });
    }

    public CompletableFuture<Void> saveSettings(IslandSettings settings) {
        // Delegate write to IslandDAO
        IslandDAO dao = plugin.getDatabaseManager().getIslandDAO();
        dao.saveIslandSettings(settings);
        GridPosition pos = settings.getGridPosition();
        settingsCache.put(pos, settings);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> toggleSetting(GridPosition pos, String settingName) {
        return getSettings(pos).thenCompose(settings -> {
            boolean newValue = settings.toggleSetting(settingName);
            return saveSettings(settings).thenApply(v -> newValue);
        });
    }

    public boolean isSettingEnabled(GridPosition pos, String settingName) {
        IslandSettings settings = settingsCache.get(pos);
        if (settings != null) return settings.getSetting(settingName);
        return switch (settingName.toUpperCase()) {
            case "PVP", "EXPLOSIONS", "FIRE", "WARP", "BORDER_MARKERS" -> false;  // Borders (markers + global visuals) disabled by default for islands
            default -> true;
        };
    }

    private void cleanupCache() {
        // Real LRU now via LinkedHashMap access-order removeEldest (auto evicts when >1000); no manual needed.
    }
}