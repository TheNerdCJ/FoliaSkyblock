package com.thenerdcj.challenge;

import java.util.*;

/**
 * Player Challenge Profile - AI Memory System
 *
 * Tracks player behavior for intelligent challenge generation:
 * - Completion rates per category
 * - Recent performance trends
 * - Category avoidance patterns
 * - Streak tracking
 * - Difficulty adaptation
 */
public class PlayerChallengeProfile {

    private final UUID playerId;

    // Historical data
    private final Map<String, Integer> totalAttempts = new HashMap<>();
    private final Map<String, Integer> totalCompletions = new HashMap<>();

    // Recent data (last 20 challenges)
    private final List<ChallengeAttempt> recentAttempts = new ArrayList<>();
    private static final int MAX_RECENT = 20;

    // Streak tracking
    private int currentStreak = 0;
    private int bestStreak = 0;
    private long lastCompletionTime = 0;

    // Difficulty tracking
    private double averageDifficulty = 1.0;
    private final List<Double> recentDifficulties = new ArrayList<>();

    public PlayerChallengeProfile(UUID playerId) {
        this.playerId = playerId;
    }

    /**
     * Record a challenge completion attempt
     */
    public void recordCompletion(String category, boolean success) {
        // Update totals
        totalAttempts.merge(category, 1, Integer::sum);
        if (success) {
            totalCompletions.merge(category, 1, Integer::sum);
        }

        // Update recent attempts
        recentAttempts.add(new ChallengeAttempt(category, success, System.currentTimeMillis()));
        if (recentAttempts.size() > MAX_RECENT) {
            recentAttempts.remove(0);
        }

        // Update streak
        if (success) {
            currentStreak++;
            bestStreak = Math.max(bestStreak, currentStreak);
            lastCompletionTime = System.currentTimeMillis();
        } else {
            currentStreak = 0;
        }

        // Update average difficulty (simulated - would come from actual challenge data)
        double difficulty = success ? 1.0 : 1.2;
        recentDifficulties.add(difficulty);
        if (recentDifficulties.size() > 10) {
            recentDifficulties.remove(0);
        }
        averageDifficulty = recentDifficulties.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
    }

    /**
     * Get overall completion rate for a category (0.0 - 1.0)
     */
    public double getCompletionRate(String category) {
        int attempts = totalAttempts.getOrDefault(category, 0);
        if (attempts == 0) return 0.5; // Default for new categories

        int completions = totalCompletions.getOrDefault(category, 0);
        return (double) completions / attempts;
    }

    /**
     * AI ENHANCEMENT: Get recent completion rate (last 20 attempts)
     */
    public double getRecentCompletionRate(String category) {
        List<ChallengeAttempt> categoryRecent = recentAttempts.stream()
                .filter(a -> a.category.equals(category))
                .toList();

        if (categoryRecent.isEmpty()) return getCompletionRate(category);

        long successes = categoryRecent.stream().filter(a -> a.success).count();
        return (double) successes / categoryRecent.size();
    }

    /**
     * AI ENHANCEMENT: Get category avoidance rate (how often player skips this category)
     */
    public double getCategoryAvoidanceRate(String category) {
        if (recentAttempts.isEmpty()) return 0.0;

        long categoryCount = recentAttempts.stream()
                .filter(a -> a.category.equals(category))
                .count();

        return 1.0 - ((double) categoryCount / recentAttempts.size());
    }

    /**
     * AI ENHANCEMENT: Get recent average completion rate across all categories
     */
    public double getRecentAverageCompletionRate() {
        if (recentAttempts.isEmpty()) return 0.5;

        long successes = recentAttempts.stream().filter(a -> a.success).count();
        return (double) successes / recentAttempts.size();
    }

    /**
     * AI ENHANCEMENT: Get recent failure rate for a specific category
     */
    public double getRecentFailureRate(String category) {
        List<ChallengeAttempt> categoryRecent = recentAttempts.stream()
                .filter(a -> a.category.equals(category))
                .toList();

        if (categoryRecent.isEmpty()) return 0.0;

        long failures = categoryRecent.stream().filter(a -> !a.success).count();
        return (double) failures / categoryRecent.size();
    }

    /**
     * AI ENHANCEMENT: Get recent engagement rate for a category
     */
    public double getRecentEngagementRate(String category) {
        if (recentAttempts.isEmpty()) return 0.0;

        long categoryCount = recentAttempts.stream()
                .filter(a -> a.category.equals(category))
                .count();

        return (double) categoryCount / recentAttempts.size();
    }

    public int getCurrentStreak() { return currentStreak; }
    public int getBestStreak() { return bestStreak; }
    public double getAverageDifficulty() { return averageDifficulty; }
    public UUID getPlayerId() { return playerId; }

    /**
     * Inner class to track individual challenge attempts
     */
    private static class ChallengeAttempt {
        String category;
        boolean success;
        long timestamp;

        ChallengeAttempt(String category, boolean success, long timestamp) {
            this.category = category;
            this.success = success;
            this.timestamp = timestamp;
        }
    }
}