package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Fancy /discord command.
 * Sends a styled, clickable Adventure component with hover info to join the Discord.
 * Configurable via config.yml under social.discord
 * Inspired by popular plugins like AddaDiscord, LinkCord, server social commands, and Adventure examples.
 * Uses modern Adventure API for click-to-open-URL and hover text (fancy in-chat experience).
 */
public class DiscordCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public DiscordCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        String link = plugin.getConfig().getString("social.discord.link", "https://discord.gg/UdpSpPhEp5");
        String prefix = plugin.getConfig().getString("social.discord.prefix", "&6&l[Discord] &r");
        String message = plugin.getConfig().getString("social.discord.message", "&fJoin our official Discord for support, events, giveaways, and community chats!");
        String hover = plugin.getConfig().getString("social.discord.hover", "&eClick to open the Discord invite in your browser");

        // Build fancy Adventure component (supports colors, hover, click)
        Component fancyLink = Component.text(" [Click to Join] ")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize(hover)));

        Component fullMessage = Component.text()
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(prefix))
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(message))
                .append(fancyLink)
                .append(Component.text(" " + link).color(NamedTextColor.BLUE)) // fallback plain link
                .build();

        player.sendMessage(fullMessage);

        // Extra fancy: send a second line with more info
        Component info = Component.text("Our Discord is the best place for skyblock tips, bug reports, events and hanging out with the community!")
                .color(NamedTextColor.YELLOW);
        player.sendMessage(info);

        // FANCY: Open a written book with more info and the link (common in servers for "rules" / "socials")
        try {
            Component bookTitle = Component.text("FoliaSkyblock Discord", NamedTextColor.GOLD);
            Component author = Component.text("The Server Team", NamedTextColor.DARK_GRAY);

            Component page1 = Component.text()
                    .append(Component.text("Welcome to our Discord!\n\n", NamedTextColor.DARK_BLUE))
                    .append(Component.text("Click the link below or use /discord again.\n\n", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Join for:\n- Support & Help\n- Events & Giveaways\n- Community Chat\n- Updates\n\n", NamedTextColor.DARK_GREEN))
                    .append(fancyLink)
                    .build();

            net.kyori.adventure.inventory.Book book = net.kyori.adventure.inventory.Book.book(bookTitle, author, page1);
            player.openBook(book);
        } catch (Exception e) {
            // Fallback if book API issue
            player.sendMessage(Component.text("(You can also find the link in chat above.)").color(NamedTextColor.GRAY));
        }

        return true;
    }
}
