package com.thenerdcj.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central utility for consistent message handling.
 *
 * In-game chat uses Adventure legacy section (§) codes.
 * Server console output is translated to ANSI so colors render in the terminal.
 */
public final class MessageUtil {

    private static final String ANSI_RESET = "\u001B[0m";

    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.builder().character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private MessageUtil() {}

    public static Component legacy(String legacyText) {
        if (legacyText == null) return Component.empty();
        if (legacyText.indexOf('§') >= 0) {
            return LEGACY_SECTION.deserialize(legacyText);
        }
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

    /** Plain text with § codes translated to ANSI for JVM console / log files. */
    public static String toConsole(String legacyText) {
        if (legacyText == null) return "";
        return legacyToAnsi(legacyText);
    }

    public static void info(Logger logger, String legacyText) {
        if (logger == null || legacyText == null) return;
        logger.info(toConsole(legacyText));
    }

    public static void warning(Logger logger, String legacyText) {
        if (logger == null || legacyText == null) return;
        logger.warning(toConsole(legacyText));
    }

    public static void severe(Logger logger, String legacyText) {
        if (logger == null || legacyText == null) return;
        logger.severe(toConsole(legacyText));
    }

    public static void log(Logger logger, Level level, String legacyText) {
        if (logger == null || legacyText == null || level == null) return;
        logger.log(level, toConsole(legacyText));
    }

    public static void log(Logger logger, Level level, String legacyText, Throwable thrown) {
        if (logger == null || legacyText == null || level == null) return;
        logger.log(level, toConsole(legacyText), thrown);
    }

    public static void info(Logger logger, Component component) {
        if (logger == null || component == null) return;
        info(logger, LEGACY_SECTION.serialize(component));
    }

    public static void warning(Logger logger, Component component) {
        if (logger == null || component == null) return;
        warning(logger, LEGACY_SECTION.serialize(component));
    }

    public static LegacyComponentSerializer getLegacySerializer() {
        return LEGACY_SECTION;
    }

    public static LegacyComponentSerializer getLegacyAmpersandSerializer() {
        return LEGACY_AMPERSAND;
    }

    private static String legacyToAnsi(String input) {
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '§' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(++i));
                String ansi = ansiForMinecraftCode(code);
                if (!ansi.isEmpty()) {
                    out.append(ansi);
                }
            } else if (ch == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(++i));
                String ansi = ansiForMinecraftCode(code);
                if (!ansi.isEmpty()) {
                    out.append(ansi);
                }
            } else {
                out.append(ch);
            }
        }
        return out.append(ANSI_RESET).toString();
    }

    private static String ansiForMinecraftCode(char code) {
        return switch (code) {
            case '0' -> "\u001B[30m";
            case '1' -> "\u001B[34m";
            case '2' -> "\u001B[32m";
            case '3' -> "\u001B[36m";
            case '4' -> "\u001B[31m";
            case '5' -> "\u001B[35m";
            case '6' -> "\u001B[33m";
            case '7' -> "\u001B[37m";
            case '8' -> "\u001B[90m";
            case '9' -> "\u001B[94m";
            case 'a' -> "\u001B[92m";
            case 'b' -> "\u001B[96m";
            case 'c' -> "\u001B[91m";
            case 'd' -> "\u001B[95m";
            case 'e' -> "\u001B[93m";
            case 'f' -> "\u001B[97m";
            case 'l' -> "\u001B[1m";
            case 'm' -> "\u001B[9m";
            case 'n' -> "\u001B[4m";
            case 'o' -> "\u001B[3m";
            case 'r' -> ANSI_RESET;
            case 'x' -> ""; // hex handled upstream by serializer for chat; skip in raw console strings
            default -> "";
        };
    }
}