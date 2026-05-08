package com.thenerdcj.challenge;

import org.bukkit.entity.Player;

/**
 * ChallengeValidator - Prevents impossible or unfair AI-generated challenges.
 * Works with the project's Challenge class (Type = DAILY / WEEKLY / SPECIAL).
 */
public class ChallengeValidator {

    /**
     * Validates whether a generated challenge is realistically achievable.
     */
    public boolean isValidChallenge(Challenge challenge, Player player, int islandLevel) {
        if (challenge == null) return false;

        String category = challenge.getCategory();
        int target = challenge.getTarget();
        if (target <= 0) return false;

        // Basic level-based caps
        int maxTarget = getMaxTargetForLevel(category, islandLevel);
        if (target > maxTarget) {
            return false;
        }

        // Extra safety for early game
        if (islandLevel < 10 && target > 80) {
            return false;
        }

        // Combat challenges get stricter at low levels
        if (category.equalsIgnoreCase("COMBAT") && islandLevel < 15 && target > 40) {
            return false;
        }

        return true;
    }

    private int getMaxTargetForLevel(String category, int level) {
        return switch (category.toUpperCase()) {
            case "MINING" -> 40 + level * 8;
            case "FARMING" -> 35 + level * 7;
            case "COMBAT" -> 20 + level * 5;
            case "BUILDING" -> 30 + level * 9;
            case "EXPLORATION" -> 10 + level * 2;
            default -> 50 + level * 5;
        };
    }

    /**
     * Creates a safe, always-achievable fallback challenge.
     */
    public Challenge createSafeFallback(int level, boolean isWeekly) {
        String category = (level < 15) ? "FARMING" : "MINING";
        int target = Math.max(25, level * 6);
        if (isWeekly) target *= 5;

        String description = switch (category) {
            case "FARMING" -> "Harvest " + target + " crops on your island";
            default -> "Mine " + target + " blocks";
        };

        int reward = target / 2;
        Challenge.Type type = isWeekly ? Challenge.Type.WEEKLY : Challenge.Type.DAILY;

        return new Challenge(null, type, category, description, target, reward);
    }
}