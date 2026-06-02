package com.thenerdcj.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Centralized, consistent sound effects for FoliaSkyblock.
 * 
 * Provides semantic helpers (success, error, crateOpen, prestige, etc.)
 * so we don't scatter raw playSound calls everywhere.
 * 
 * All methods are null-safe and Folia-friendly (no scheduler assumptions).
 */
public final class SoundUtil {

    private SoundUtil() {}

    /**
     * Low-level helper. Safe to call from anywhere (main thread recommended for sounds).
     */
    public static void play(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    // ==================== SEMANTIC HELPERS ====================

    /** Generic positive feedback (level up feel) */
    public static void success(Player player) {
        play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.15f);
    }

    /** Generic negative / error feedback */
    public static void error(Player player) {
        play(player, Sound.ENTITY_VILLAGER_NO, 0.7f, 0.85f);
    }

    /** Subtle UI click / button press (good for GUI nav) */
    public static void click(Player player) {
        play(player, Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
    }

    /** Chest / container open feel */
    public static void open(Player player) {
        play(player, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    /** Crate opening - more celebratory */
    public static void crateOpen(Player player) {
        play(player, Sound.BLOCK_CHEST_OPEN, 0.7f, 0.9f);
        play(player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 1.1f);
    }

    /** Prestige action - big celebratory moment */
    public static void prestige(Player player) {
        play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.7f);
        play(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.3f);
    }

    /** Boss defeated / major victory */
    public static void bossDefeat(Player player) {
        play(player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.8f);
        play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 0.9f);
    }

    /** Boss summoned / warning */
    public static void bossSummon(Player player) {
        play(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.7f);
        play(player, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.6f);
    }

    /** Successful purchase / upgrade */
    public static void purchaseSuccess(Player player) {
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
    }

    /** Failed purchase / insufficient funds */
    public static void purchaseFail(Player player) {
        play(player, Sound.ENTITY_VILLAGER_NO, 0.6f, 0.9f);
    }

    /** Slayer quest start */
    public static void slayerStart(Player player) {
        play(player, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.7f, 1.0f);
    }

    /** Item received / reward */
    public static void reward(Player player) {
        play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    /** GUI page change / navigation */
    public static void pageTurn(Player player) {
        play(player, Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.0f);
    }
}