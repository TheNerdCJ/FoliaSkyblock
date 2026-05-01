package com.thenerdcj.challenge;

import java.util.HashMap;
import java.util.Map;

public class PlayerChallengeProfile {

    private final Map<String, Integer> completed = new HashMap<>();
    private final Map<String, Integer> attempted = new HashMap<>();
    private int currentStreak = 0;
    private long lastCompletionTime = 0;

    public void recordCompletion(String category, boolean success) {
        attempted.put(category, attempted.getOrDefault(category, 0) + 1);

        if (success) {
            completed.put(category, completed.getOrDefault(category, 0) + 1);
            currentStreak++;
            lastCompletionTime = System.currentTimeMillis();
        } else {
            currentStreak = 0;
        }
    }

    public double getCompletionRate(String category) {
        int attempts = attempted.getOrDefault(category, 0);
        if (attempts == 0) return 0.7;
        int successes = completed.getOrDefault(category, 0);
        return (double) successes / attempts;
    }

    public double getAverageDifficulty() {
        return Math.min(2.0, 1.0 + (currentStreak * 0.1));
    }

    public int getCurrentStreak() { return currentStreak; }
    public long getLastCompletionTime() { return lastCompletionTime; }
}