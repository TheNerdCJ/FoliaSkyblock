package com.thenerdcj.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Central utility for consistent message handling.
 * 
 * This class helps with the ongoing migration from legacy § color codes
 * to proper Adventure Components.
 */
public final class MessageUtil {

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = 
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {}

    public static Component legacy(String legacyText) {
        if (legacyText == null) return Component.empty();
        return LEGACY_AMPERSAND.deserialize(legacyText);
    }

    public static void sendMessage(Player player, String legacyText) {
        if (player == null || legacyText == null) return;
        player.sendMessage(legacy(legacyText));
    }

    public static void sendMessage(CommandSender sender, String legacyText) {
        if (sender == null || legacyText == null) return;
        sender.sendMessage(legacy(legacyText));
    }

    public static void sendActionBar(Player player, String legacyText) {
        if (player == null || legacyText == null) return;
        player.sendActionBar(legacy(legacyText));
    }

    public static Component forItem(String legacyText) {
        return legacy(legacyText);
    }

    public static void info(Logger logger, String legacyText) {
        if (logger == null || legacyText == null) return;
        logger.info(LEGACY_AMPERSAND.deserialize(legacyText).toString());
    }

    public static void warning(Logger logger, String legacyText) {
        if (logger == null || legacyText == null) return;
        logger.warning(LEGACY_AMPERSAND.deserialize(legacyText).toString());
    }

    public static void severe(Logger logger, String legacyText) {
        if (logger == null || legacyText == null) return;
        logger.severe(LEGACY_AMPERSAND.deserialize(legacyText).toString());
    }

    public static LegacyComponentSerializer getLegacySerializer() {
        return LEGACY_AMPERSAND;
    }
}