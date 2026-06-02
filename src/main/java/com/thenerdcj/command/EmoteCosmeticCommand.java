package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.EmoteCosmetic;
import com.thenerdcj.cosmetic.EmoteCosmeticManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EmoteCosmeticCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public EmoteCosmeticCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        EmoteCosmeticManager manager = plugin.getEmoteCosmeticManager();
        if (manager == null) {
            player.sendMessage("§cEmote system not available.");
            return true;
        }

        if (args.length == 0) {
            plugin.getEmoteCosmeticGUI().open(player);
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("trigger")) {
            String triggerKey = args[1].toLowerCase();
            String emoteName = args[2].toUpperCase();
            try {
                EmoteCosmetic e = EmoteCosmetic.valueOf(emoteName);
                if (!manager.has(player.getUniqueId(), e) && !e.isNone()) {
                    player.sendMessage("§cYou have not unlocked this emote.");
                    return true;
                }
                manager.setTrigger(player.getUniqueId(), triggerKey, e);
                player.sendMessage("§aTrigger '" + triggerKey + "' now set to " + e.getDisplayName() + " §7(per-player, persists). Fires on matching events if owned. Keys: join,kill,death,respawn,levelup,...");
            } catch (Exception e) {
                player.sendMessage("§cUnknown emote for trigger. Use /emote list or /emotes to see list. Example: /emote trigger kill cheer");
            }
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            // UX polish: list owned emotes in chat
            java.util.Set<EmoteCosmetic> owned = manager.getOwned(player.getUniqueId());
            player.sendMessage("§d§lYour Emotes (" + (owned.size()) + "):");
            for (EmoteCosmetic e : EmoteCosmetic.values()) {
                if (e.isNone()) continue;
                if (owned.contains(e)) {
                    player.sendMessage("  §a" + e.getDisplayName() + " §7(" + e.name().toLowerCase() + ")");
                }
            }
            player.sendMessage("§7Use /emote <name> to perform, or /emote trigger <key> <name> for auto (see GUI for config).");
            return true;
        }

        String emoteName = args[0].toUpperCase();
        try {
            EmoteCosmetic e = EmoteCosmetic.valueOf(emoteName);
            manager.performEmote(player, e);
        } catch (Exception e) {
            player.sendMessage("§cUnknown emote. Use /emote list or /emotes (GUI) to list + configure auto-triggers. Or /emote trigger <key> <emote> e.g. kill, death, join, respawn, levelup.");
        }
        return true;
    }
}
