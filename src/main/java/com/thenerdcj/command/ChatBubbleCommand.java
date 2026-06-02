package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.ChatBubbleCosmetic;
import com.thenerdcj.cosmetic.ChatBubbleCosmeticManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatBubbleCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public ChatBubbleCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        ChatBubbleCosmeticManager manager = plugin.getChatBubbleCosmeticManager();
        if (manager == null) {
            player.sendMessage("§cChat Bubbles system not available.");
            return true;
        }

        if (args.length == 0) {
            plugin.getChatBubbleGUI().open(player);
            return true;
        }

        String bubbleName = args[0].toUpperCase();
        try {
            ChatBubbleCosmetic b = ChatBubbleCosmetic.valueOf(bubbleName);
            manager.setActive(player, b);
        } catch (Exception e) {
            player.sendMessage("§cUnknown chat bubble. Use /chatbubbles to open the menu.");
        }
        return true;
    }
}
