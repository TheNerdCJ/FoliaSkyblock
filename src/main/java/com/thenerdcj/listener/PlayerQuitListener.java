package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final FoliaSkyblock plugin;

    public PlayerQuitListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Personalized leave message using rank + active tag (consistent with join and chat)
        if (plugin.getChatManager() != null) {
            String display = plugin.getChatManager().getRichDisplayName(player);
            event.setQuitMessage("§8[§c-§8] " + display + " §7has left the game.");
        }

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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Personalized join message using rank + active tag (from ChatManager / RankManager / PlayerTagManager)
        if (plugin.getChatManager() != null) {
            String display = plugin.getChatManager().getRichDisplayName(player);
            event.setJoinMessage("§8[§a+§8] " + display + " §7has joined the game.");
        }

        if (plugin != null) {
            plugin.setServerTabHeaderFooter(player);
        }
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

        // Disable built-in Minecraft (vanilla) advancements/achievements if configured.
        // Keeps focus on custom Play-to-Win progression (slayer, collections, skills, etc.).
        // Revoke on join + prevent future via event. Enabled by default.
        if (plugin.isVanillaAdvancementsDisabled()) {
            revokeVanillaAdvancements(player);
        }
    }

    private void revokeVanillaAdvancements(Player player) {
        if (player == null) return;
        java.util.Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            if (!"minecraft".equals(advancement.getKey().getNamespace())) {
                continue;
            }
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : new java.util.ArrayList<>(progress.getAwardedCriteria())) {
                progress.revokeCriteria(criterion);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!plugin.isVanillaAdvancementsDisabled()) {
            return;
        }
        Advancement advancement = event.getAdvancement();
        if (!"minecraft".equals(advancement.getKey().getNamespace())) {
            return;
        }
        Player player = event.getPlayer();
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        for (String criterion : new java.util.ArrayList<>(progress.getAwardedCriteria())) {
            progress.revokeCriteria(criterion);
        }
    }

    /**
     * Prevent vanilla Minecraft advancements from ever being granted by cancelling
     * the criterion grant event. This stops repeated awarding (e.g. "Stone Age"
     * re-triggering every time cobblestone is placed due to inventory_changed criteria).
     * Combined with join-time revocation.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAdvancementCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
        if (!plugin.isVanillaAdvancementsDisabled()) {
            return;
        }
        org.bukkit.advancement.Advancement advancement = event.getAdvancement();
        if ("minecraft".equals(advancement.getKey().getNamespace())) {
            event.setCancelled(true);
        }
    }
}