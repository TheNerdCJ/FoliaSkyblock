package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.ChatBubbleCosmetic;
import com.thenerdcj.cosmetic.ChatBubbleCosmeticManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Triggers cosmetic chat bubbles when players send chat messages.
 * Folia-safe: visuals are scheduled via ThreadSafety / manager.
 * Respects cancelled events (e.g. rename inputs in GUIs).
 */
public class ChatBubbleListener implements Listener {

    private final FoliaSkyblock plugin;

    public ChatBubbleListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        ChatBubbleCosmeticManager manager = plugin.getChatBubbleCosmeticManager();
        if (manager == null) return;

        ChatBubbleCosmetic active = manager.getActive(player.getUniqueId());
        if (active.isNone()) return;

        // Delegate to manager (which handles thread safety for spawning)
        // Pass the raw message; manager will shorten + style it.
        String message = event.getMessage();
        // Run trigger on main thread to be safe with entity spawn
        plugin.getThreadSafety().runOnMainThread(() -> {
            if (player.isOnline()) {
                manager.triggerChatBubble(player, message);
            }
        });
    }
}
