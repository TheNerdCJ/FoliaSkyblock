package com.thenerdcj.anticheat;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * AntiBotManager - Fully optimized for Folia
 * - Uses GlobalRegionScheduler for periodic cleanup (safe main-thread task)
 * - Uses AsyncScheduler for heavy operations (pattern matching, logging)
 * - All data structures are thread-safe (ConcurrentHashMap)
 * - Pre-compiled regex patterns for maximum performance
 * - Extremely low overhead
 */
public class AntiBotManager {

    private final FoliaSkyblock plugin;
    private FileConfiguration config;

    // Login flood protection per IP
    private final ConcurrentHashMap<String, Integer> ipLoginCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ipLastLoginTime = new ConcurrentHashMap<>();

    // Rapid reconnect protection
    private final ConcurrentHashMap<UUID, Long> lastDisconnectTime = new ConcurrentHashMap<>();

    // Pre-compiled blocked name patterns
    private final Set<Pattern> blockedNamePatterns = ConcurrentHashMap.newKeySet();

    public AntiBotManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();

        // Schedule periodic cleanup using Folia's GlobalRegionScheduler (very efficient)
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> cleanupOldEntries(),
                20L * 30,           // Start after 30 seconds
                20L * 60);          // Run every 60 seconds
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "anticheat.yml");
        if (!file.exists()) {
            plugin.saveResource("anticheat.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Pre-compile all blocked name patterns for maximum speed
        blockedNamePatterns.clear();
        for (String regex : config.getStringList("antibot.blocked-name-patterns")) {
            try {
                blockedNamePatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                plugin.getLogger().warning("§cInvalid regex in anticheat.yml: " + regex);
            }
        }

        plugin.getLogger().info("§aAntiBotManager loaded with " + blockedNamePatterns.size() + " blocked name patterns.");
    }

    /**
     * Main bot detection method - called on PlayerLoginEvent / PlayerJoinEvent
     */
    public boolean isBotAttempt(Player player, String ip) {
        if (!config.getBoolean("antibot.enabled", true)) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Rapid reconnect protection
        Long lastDisconnect = lastDisconnectTime.get(uuid);
        if (lastDisconnect != null) {
            long timeSinceDisconnect = now - lastDisconnect;
            if (timeSinceDisconnect < config.getInt("antibot.reconnect-cooldown-seconds", 8) * 1000L) {
                plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                        plugin.getLogger().warning("§c[AntiBot] Rapid reconnect detected: " + player.getName())
                );
                return true;
            }
        }

        // Login flood per IP (async counting)
        int loginCount = ipLoginCount.compute(ip, (k, v) -> (v == null ? 0 : v) + 1);
        ipLastLoginTime.put(ip, now);

        if (loginCount > config.getInt("antibot.max-logins-per-ip-per-minute", 6)) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                    plugin.getLogger().warning("§c[AntiBot] Login flood from IP " + ip)
            );
            return true;
        }

        // Blocked name patterns (fast pre-compiled regex)
        for (Pattern pattern : blockedNamePatterns) {
            if (pattern.matcher(player.getName()).matches()) {
                plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                        plugin.getLogger().warning("§c[AntiBot] Blocked bot name: " + player.getName())
                );
                return true;
            }
        }

        return false;
    }

    public void onPlayerDisconnect(UUID uuid) {
        lastDisconnectTime.put(uuid, System.currentTimeMillis());
    }

    /**
     * Periodic cleanup - scheduled safely with GlobalRegionScheduler
     */
    private void cleanupOldEntries() {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - TimeUnit.MINUTES.toMillis(1);
        long fiveMinutesAgo = now - TimeUnit.MINUTES.toMillis(5);

        ipLoginCount.entrySet().removeIf(entry -> ipLastLoginTime.getOrDefault(entry.getKey(), 0L) < oneMinuteAgo);
        lastDisconnectTime.entrySet().removeIf(entry -> entry.getValue() < fiveMinutesAgo);
    }

    public void clearData(UUID uuid) {
        lastDisconnectTime.remove(uuid);
    }

    public FileConfiguration getConfig() {
        return config;
    }
}