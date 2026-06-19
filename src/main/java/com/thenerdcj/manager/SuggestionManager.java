package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.suggest.Suggestion;
import com.thenerdcj.util.MessageUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages player suggestions as a community poll system.
 *
 * Features:
 * - /suggest <idea> submits a new suggestion (or detects duplicates)
 * - Players can vote on existing suggestions via /suggest vote <id>
 * - Duplicate / spam protection (normalized text + per-player cooldown)
 * - Persistent storage in suggestions.json (structured, Grok-readable)
 * - Also appends human-friendly updates to suggestions.md for easy Grok analysis
 * - Fully Folia-safe: file I/O off main thread via CompletableFuture, main-thread feedback via GlobalRegionScheduler
 */
public class SuggestionManager {

    private final FoliaSkyblock plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final List<Suggestion> suggestions = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextId = new AtomicInteger(1);

    private final Map<UUID, Long> lastSuggestTime = new ConcurrentHashMap<>();

    private File suggestionsFile;
    private File markdownFile;

    public SuggestionManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        load();
    }

    public int getCooldownMinutes() {
        return plugin.getConfig().getInt("suggestions.cooldown-minutes", 10);
    }

    public int getMinLength() {
        return plugin.getConfig().getInt("suggestions.min-length", 10);
    }

    public int getMaxLength() {
        return plugin.getConfig().getInt("suggestions.max-length", 250);
    }

    public boolean canSubmit(Player player) {
        if (player.hasPermission("foliasb.admin") || player.hasPermission("foliasb.staff")) return true;
        long cooldown = getCooldownMinutes() * 60L * 1000L;
        Long last = lastSuggestTime.get(player.getUniqueId());
        return last == null || (System.currentTimeMillis() - last) >= cooldown;
    }

    public long getRemainingCooldownMillis(Player player) {
        long cooldown = getCooldownMinutes() * 60L * 1000L;
        Long last = lastSuggestTime.get(player.getUniqueId());
        if (last == null) return 0;
        return Math.max(0, cooldown - (System.currentTimeMillis() - last));
    }

    /** Main submission. Returns the id of the suggestion (new or existing similar). */
    public CompletableFuture<Integer> submitSuggestion(Player player, String rawText) {
        if (player == null || rawText == null) {
            return CompletableFuture.completedFuture(-1);
        }

        String text = rawText.trim();
        if (text.length() < getMinLength()) {
            MessageUtil.sendMessage(player, "§cYour suggestion is too short (minimum " + getMinLength() + " characters).");
            return CompletableFuture.completedFuture(-1);
        }
        if (text.length() > getMaxLength()) {
            text = text.substring(0, getMaxLength());
        }

        final String finalText = text;

        return CompletableFuture.supplyAsync(() -> {
            String normalized = normalize(finalText);

            // Check for very similar existing suggestion
            for (Suggestion s : suggestions) {
                String existingNorm = s.getNormalizedText();
                if (existingNorm.equals(normalized) ||
                    (existingNorm.length() > 8 && (existingNorm.contains(normalized) || normalized.contains(existingNorm)))) {
                    // Found similar
                    return s.id; // return existing id so caller can tell them to vote
                }
            }

            // Create new
            int id = nextId.getAndIncrement();
            Suggestion suggestion = new Suggestion(id, finalText, player.getUniqueId(), player.getName(), System.currentTimeMillis());
            suggestions.add(suggestion);

            lastSuggestTime.put(player.getUniqueId(), System.currentTimeMillis());

            save();
            appendToMarkdown(suggestion, "submitted");

            return id;
        }).thenApply(id -> {
            // Feedback must be on main thread
            plugin.getThreadSafety().runOnMainThread(() -> {
                if (id > 0) {
                    Suggestion existing = getById(id);
                    if (existing != null && existing.submitterUuid != null && existing.submitterUuid.equals(player.getUniqueId())) {
                        MessageUtil.sendMessage(player, "§aThank you! Your suggestion has been added as #" + id + ".");
                        MessageUtil.sendMessage(player, "§7Others can vote with §f/suggest vote " + id);
                    }
                }
            });
            return id;
        });
    }

    public boolean vote(Player player, int id) {
        Suggestion s = getById(id);
        if (s == null) {
            MessageUtil.sendMessage(player, "§cNo suggestion with ID #" + id + ". Use /suggest list to see available ones.");
            return false;
        }

        if (s.hasVoted(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "§eYou have already voted for suggestion #" + id + ".");
            return false;
        }

        s.addVote(player.getUniqueId());
        save();
        appendToMarkdown(s, "voted by " + player.getName());

        MessageUtil.sendMessage(player, "§aVote recorded for #" + id + " — \"" + truncate(s.text, 50) + "\"");
        MessageUtil.sendMessage(player, "§7Current votes: §f" + s.votes);
        return true;
    }

    public List<Suggestion> getTopSuggestions(int limit) {
        List<Suggestion> copy = new ArrayList<>(suggestions);
        copy.sort((a, b) -> Integer.compare(b.votes, a.votes));
        return copy.subList(0, Math.min(limit, copy.size()));
    }

    public Suggestion getById(int id) {
        synchronized (suggestions) {
            for (Suggestion s : suggestions) {
                if (s.id == id) return s;
            }
        }
        return null;
    }

    public int getTotalCount() {
        return suggestions.size();
    }

    private String normalize(String text) {
        return text.toLowerCase().trim()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ");
    }

    private String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    // ==================== PERSISTENCE ====================

    private synchronized void load() {
        try {
            suggestionsFile = new File(plugin.getDataFolder(), "suggestions.json");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            if (suggestionsFile.exists()) {
                Type listType = new TypeToken<List<Suggestion>>() {}.getType();
                try (FileReader reader = new FileReader(suggestionsFile)) {
                    List<Suggestion> loaded = gson.fromJson(reader, listType);
                    if (loaded != null) {
                        suggestions.clear();
                        suggestions.addAll(loaded);
                    }
                }
            }

            // Determine next ID
            int max = 0;
            for (Suggestion s : suggestions) {
                if (s.id > max) max = s.id;
            }
            nextId.set(max + 1);

            markdownFile = new File(plugin.getDataFolder(), "suggestions.md");
            if (!markdownFile.exists()) {
                writeMarkdownHeader();
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[SuggestionManager] Failed to load suggestions.json: " + e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            if (suggestionsFile == null) {
                suggestionsFile = new File(plugin.getDataFolder(), "suggestions.json");
            }
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

            try (FileWriter writer = new FileWriter(suggestionsFile)) {
                gson.toJson(suggestions, writer);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SuggestionManager] Failed to save suggestions.json: " + e.getMessage());
        }
    }

    private void writeMarkdownHeader() {
        try {
            String header = "# FoliaSkyblock Player Suggestions (Poll System)\n\n" +
                    "This file is maintained for persistence and easy reading by Grok / AI tools.\n" +
                    "It reflects the live state of community feature suggestions.\n\n" +
                    "**How it works:**\n" +
                    "- Players use `/suggest <your idea>` to propose features or changes.\n" +
                    "- Duplicate/similar suggestions are detected and players are guided to vote instead.\n" +
                    "- Use `/suggest vote <id>` to support ideas you like.\n" +
                    "- Top voted ideas bubble to the top.\n\n" +
                    "Suggestions are also stored in structured JSON (`suggestions.json`) for easy parsing.\n\n" +
                    "---\n\n";
            Files.writeString(markdownFile.toPath(), header, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }

    private void appendToMarkdown(Suggestion s, String action) {
        plugin.getThreadSafety().runOnMainThreadLater(() -> {
            try {
                if (markdownFile == null) markdownFile = new File(plugin.getDataFolder(), "suggestions.md");
                Path path = markdownFile.toPath();

                if (!Files.exists(path)) {
                    writeMarkdownHeader();
                }

                StringBuilder sb = new StringBuilder();
                sb.append("\n## Suggestion #").append(s.id).append("\n\n");
                sb.append("- **Time:** ").append(Instant.ofEpochMilli(s.timestamp)).append("\n");
                sb.append("- **Submitted by:** ").append(s.submitterName).append(" (").append(s.submitterUuid).append(")\n");
                sb.append("- **Votes:** ").append(s.votes).append("\n");
                sb.append("- **Action:** ").append(action).append("\n\n");
                sb.append("**Text:**\n\n> ").append(s.text.replace("\n", "\n> ")).append("\n\n");
                sb.append("---\n");

                Files.writeString(path, sb.toString(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);

            } catch (Exception e) {
                plugin.getLogger().warning("[SuggestionManager] Failed to update suggestions.md: " + e.getMessage());
            }
        }, 1L);
    }

    /** Returns current suggestions for command / GUI use (copy). */
    public List<Suggestion> getAllSuggestions() {
        synchronized (suggestions) {
            return new ArrayList<>(suggestions);
        }
    }
}
