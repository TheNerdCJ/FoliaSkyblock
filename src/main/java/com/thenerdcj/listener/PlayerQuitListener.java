package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final FoliaSkyblock plugin;

    public PlayerQuitListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getChatManager() != null) {
            plugin.getChatManager().onPlayerQuit(player);
        }

        if (plugin.getTeleportRequestManager() != null) {
            plugin.getTeleportRequestManager().removePlayer(uuid);
        }

        if (plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().removePlayer(uuid);
        }

        if (plugin.getRankManager() != null) {
            plugin.getRankManager().removePlayer(uuid);
        }

        if (plugin.getParticleTrailManager() != null) {
            plugin.getParticleTrailManager().onPlayerQuit(player);
        }

        if (plugin.getPetManager() != null) {
            plugin.getPetManager().onPlayerQuit(player);
            plugin.getPetManager().savePlayer(uuid);
        }

        if (plugin.getPlayerTagManager() != null) {
            plugin.getPlayerTagManager().onPlayerQuit(player);
        }

        if (plugin.getPlayerNametagManager() != null) {
            plugin.getPlayerNametagManager().onPlayerQuit(player);
        }

        if (plugin.getElytraWingManager() != null) {
            plugin.getElytraWingManager().onPlayerQuit(player);
            plugin.getElytraWingManager().savePlayer(uuid);
        }

        if (plugin.getRuneManager() != null) {
            plugin.getRuneManager().onPlayerQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getIslandWorthManager() != null) {
            plugin.getIslandWorthManager().updatePlayerTabList(player);
        }

        if (plugin.getParticleTrailManager() != null) {
            plugin.getParticleTrailManager().onPlayerJoin(player);
        }

        if (plugin.getPetManager() != null) {
            // Load pets from DB
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getPetManager().loadPlayer(player.getUniqueId());
            });

            // TEMP: Give new players a few starter cosmetic pets for testing
            var petManager = plugin.getPetManager();
            var owned = petManager.getOwnedPets(player.getUniqueId());
            if (owned.isEmpty()) {
                petManager.addPet(player.getUniqueId(), new com.thenerdcj.pets.CosmeticPet(com.thenerdcj.pets.PetType.BABY_PARROT, "Chirpy"));
                petManager.addPet(player.getUniqueId(), new com.thenerdcj.pets.CosmeticPet(com.thenerdcj.pets.PetType.CAT, "Whiskers"));
            }

            plugin.getPetManager().onPlayerJoin(player);
        }

        if (plugin.getPlayerTagManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getPlayerTagManager().loadPlayer(player.getUniqueId());
            });
            plugin.getThreadSafety().runOnMainThreadLater(() -> {
                if (player.isOnline() && plugin.getPlayerTagManager() != null) {
                    plugin.getPlayerTagManager().refreshPlayerDisplay(player);
                }
            }, 5L);
        }

        if (plugin.getPlayerNametagManager() != null) {
            plugin.getThreadSafety().runOnMainThreadLater(() -> {
                if (player.isOnline() && plugin.getPlayerNametagManager() != null) {
                    plugin.getPlayerNametagManager().onPlayerJoin(player);
                }
            }, 12L);
        }

        if (plugin.getElytraWingManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getElytraWingManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getRuneManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getRuneManager().loadPlayer(player.getUniqueId());
            });
        }
    }
}