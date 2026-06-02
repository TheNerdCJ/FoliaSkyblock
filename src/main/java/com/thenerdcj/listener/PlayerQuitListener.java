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

        if (plugin.getHelmetSkinManager() != null) {
            plugin.getHelmetSkinManager().onPlayerQuit(player);
        }

        if (plugin.getDeathEffectManager() != null) {
            plugin.getDeathEffectManager().onPlayerQuit(player);
        }

        if (plugin.getDeathMessageManager() != null) {
            plugin.getDeathMessageManager().savePlayer(player.getUniqueId());
            plugin.getDeathMessageManager().onPlayerQuit(player);
        }

        if (plugin.getBackpackSkinManager() != null) {
            plugin.getBackpackSkinManager().onPlayerQuit(player);
        }

        if (plugin.getPowerOrbSkinManager() != null) {
            plugin.getPowerOrbSkinManager().onPlayerQuit(player);
        }

        if (plugin.getMinionSkinManager() != null) {
            plugin.getMinionSkinManager().onPlayerQuit(player);
        }

        if (plugin.getIslandFurnitureManager() != null) {
            plugin.getIslandFurnitureManager().onPlayerQuit(player);
        }

        if (plugin.getIslandMusicManager() != null) {
            plugin.getIslandMusicManager().onPlayerQuit(player);
        }

        if (plugin.getOverheadCosmeticManager() != null) {
            plugin.getOverheadCosmeticManager().onPlayerQuit(player);
        }

        if (plugin.getEmoteCosmeticManager() != null) {
            plugin.getEmoteCosmeticManager().onPlayerQuit(player);
        }

        if (plugin.getIslandStructureManager() != null) {
            plugin.getIslandStructureManager().onPlayerQuit(player);
        }

        if (plugin.getChatBubbleCosmeticManager() != null) {
            plugin.getChatBubbleCosmeticManager().onPlayerQuit(player);
        }

        if (plugin.getIslandWeatherCosmeticManager() != null) {
            plugin.getIslandWeatherCosmeticManager().onPlayerQuit(player);
        }

        if (plugin.getAccessoryCosmeticManager() != null) {
            plugin.getAccessoryCosmeticManager().onPlayerQuit(player);
        }

        if (plugin.getPlayerSkillManager() != null) {
            plugin.getPlayerSkillManager().onPlayerQuit(player);
            plugin.getPlayerSkillManager().savePlayer(player.getUniqueId());
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

        if (plugin.getHelmetSkinManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getHelmetSkinManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getDeathEffectManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getDeathEffectManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getDeathMessageManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getDeathMessageManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getBackpackSkinManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getBackpackSkinManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getPowerOrbSkinManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getPowerOrbSkinManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getMinionSkinManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getMinionSkinManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getIslandFurnitureManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getIslandFurnitureManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getIslandMusicManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getIslandMusicManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getOverheadCosmeticManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getOverheadCosmeticManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getEmoteCosmeticManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getEmoteCosmeticManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getIslandStructureManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getIslandStructureManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getChatBubbleCosmeticManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getChatBubbleCosmeticManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getIslandWeatherCosmeticManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getIslandWeatherCosmeticManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getAccessoryCosmeticManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getAccessoryCosmeticManager().loadPlayer(player.getUniqueId());
            });
        }

        if (plugin.getPlayerSkillManager() != null) {
            plugin.getThreadSafety().runAsync(() -> {
                plugin.getPlayerSkillManager().loadPlayer(player.getUniqueId());
            });
        }
    }
}