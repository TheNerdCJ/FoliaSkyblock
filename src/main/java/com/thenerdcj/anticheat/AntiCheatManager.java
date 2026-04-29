package com.thenerdcj.anticheat;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AntiCheatManager {

    private final FoliaSkyblock plugin;
    private FileConfiguration config;

    // Player violations: UUID -> violation count
    private final Map<UUID, Integer> violations = new ConcurrentHashMap<>();

    public AntiCheatManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        loadConfig();

        // Schedule violation decay using Folia's GlobalRegionScheduler (runs on main thread safely)
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> decayViolations(), 
                20L * 30,   // Start after 30 seconds
                20L * 60);  // Run every 60 seconds
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "anticheat.yml");
        if (!file.exists()) {
            plugin.saveResource("anticheat.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        plugin.getLogger().info("§aAntiCheat configuration loaded successfully.");
    }

    /**
     * Add a violation to a player.
     */
    public void addViolation(Player player, String checkName, int weight) {
        if (!config.getBoolean("enabled", true)) return;

        UUID uuid = player.getUniqueId();
        int current = violations.compute(uuid, (k, v) -> (v == null ? 0 : v) + weight);

        plugin.getLogger().warning("§c[AntiCheat] " + player.getName() + " flagged for " + checkName + " (violations: " + current + ")");

        int max = config.getInt("max-violations", 15);

        if (current >= max) {
            punishPlayer(player, checkName);
        } else if (current >= max - 5 && config.getBoolean("punishment.warn", true)) {
            player.sendMessage("§c§lAntiCheat §8» §eSlow down! You are being flagged for suspicious activity.");
        }

        // Staff alert (async to avoid blocking)
        if (config.getBoolean("punishment.broadcast-to-staff", true)) {
            String alert = "§c[AntiCheat] §e" + player.getName() + " §7flagged for §c" + checkName + " §7(" + current + " violations)";
            plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("foliaskyblock.staff.alert"))
                        .forEach(p -> p.sendMessage(alert))
            );
        }
    }

    private void punishPlayer(Player player, String reason) {
        String level3Action = config.getString("punishment.level-3", "ban");

        if (level3Action.equalsIgnoreCase("ban")) {
            int hours = config.getInt("punishment.ban-duration-hours", 24);
            player.getServer().getBanList(org.bukkit.BanList.Type.NAME)
                    .addBan(player.getName(), "§cAntiCheat: " + reason, null, "AntiCheat");
            player.kickPlayer("§cYou have been banned for cheating.");
        } else if (level3Action.equalsIgnoreCase("kick")) {
            player.kickPlayer("§c§lAntiCheat §8» §eYou have been kicked for suspicious activity.");
        }

        violations.remove(player.getUniqueId());
    }

    /**
     * Periodic decay of violations - scheduled safely with Folia GlobalRegionScheduler
     */
    private void decayViolations() {
        violations.entrySet().removeIf(entry -> entry.getValue() <= 1);
    }

    public void clearViolations(UUID uuid) {
        violations.remove(uuid);
    }

    public FileConfiguration getConfig() {
        return config;
    }
}