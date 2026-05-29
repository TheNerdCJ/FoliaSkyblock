package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * High-level manager for handling punishments.
 * Handles logging + checking active punishments on join.
 */
public class PunishmentManager {

    private final FoliaSkyblock plugin;
    private final DatabaseManager databaseManager;

    public PunishmentManager(FoliaSkyblock plugin) {

        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager(); // ← Important
    }

    /**
     * Logs a punishment and returns whether it was successful.
     */
    public CompletableFuture<Boolean> logPunishment(UUID target, UUID staff,
                                                    Punishment.Type type, String reason, long durationMillis) {
        return plugin.getDatabaseManager().logPunishment(target, staff, type, reason, durationMillis);
    }

    /**
     * Checks if a player has any active (non-expired) bans or mutes.
     * Returns the most severe active punishment if found.
     */
    public CompletableFuture<Punishment> getActiveBanOrMute(UUID uuid) {
        return plugin.getDatabaseManager().getActivePunishments(uuid).thenApply(punishments -> {
            for (Punishment p : punishments) {
                if ((p.getType() == Punishment.Type.BAN || p.getType() == Punishment.Type.TEMPBAN ||
                     p.getType() == Punishment.Type.MUTE) && !p.isExpired()) {
                    return p;
                }
            }
            return null;
        });
    }

    /**
     * Gets punishment history for a player (for /history or staff lookup).
     */
    public CompletableFuture<List<Punishment>> getPunishmentHistory(UUID uuid) {
        return plugin.getDatabaseManager().getPunishmentsForPlayer(uuid);
    }
    public CompletableFuture<Boolean> unbanPlayer(Player staff, UUID target) {
        return databaseManager.unbanPlayer(target).thenApply(success -> {
            if (success) {
                if (staff != null && staff.isOnline()) {
                    staff.sendMessage("§aSuccessfully unbanned player.");
                }
                // Use NameCache (non-blocking) instead of Bukkit.getOfflinePlayer on async thread
                String targetName = plugin.getNameCache().getName(target);
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("foliasb.staff"))
                        .forEach(p -> p.sendMessage("§7[Staff] §e" + staff.getName() + " §7unbanned §c" + targetName));
            } else {
                if (staff != null && staff.isOnline()) {
                    staff.sendMessage("§cPlayer is not currently banned.");
                }
            }
            return success;
        });
    }
}
