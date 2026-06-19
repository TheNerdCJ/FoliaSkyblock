package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Handles player chat to enforce custom format:
 * "<island prestige/level> [Rank] (Cosmetic Tag) playername: message"
 *
 * Delegates to ChatManager for:
 * - Mute checks
 * - Island chat mode routing (global vs island only)
 * - Rich display name (now includes prestige/level prefix)
 *
 * Uses HIGHEST ignoreCancelled so GUI chat inputs (renames etc) that cancel early are respected.
 * Folia compatible (message extraction only; sends inside ChatManager).
 */
public class ChatListener implements Listener {

    private final FoliaSkyblock plugin;

    public ChatListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }

        // If cancelled by input GUI (rename, hologram edit, pet rename etc at HIGHEST), skip - they consume the chat.
        if (event.isCancelled()) {
            return;
        }

        String message = event.getMessage();

        // Cancel default chat so we fully control the format + routing (including prestige/level prefix)
        event.setCancelled(true);

        // Delegate (handles mute, island/global mode, and uses getRichDisplayName with prestige/level from composed)
        if (plugin.getChatManager() != null) {
            plugin.getChatManager().handleChat(player, message);
        } else {
            // Fallback to vanilla if manager missing (shouldn't happen)
            player.sendMessage("§c[Chat] " + player.getName() + ": " + message);
            return;
        }

        // Manually trigger chat bubble cosmetic for real chat messages (the event is cancelled so the MONITOR bubble listener won't see it).
        // This keeps rename/hologram input texts from spawning bubbles.
        if (plugin.getChatBubbleCosmeticManager() != null) {
            plugin.getThreadSafety().runOnMainThread(() -> {
                if (player.isOnline()) {
                    plugin.getChatBubbleCosmeticManager().triggerChatBubble(player, message);
                }
            });
        }
    }
}
