package com.thenerdcj.suggest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a player suggestion / feature poll item.
 * Stored in suggestions.json for easy Grok / external analysis.
 */
public class Suggestion {

    public int id;
    public String text;
    public UUID submitterUuid;
    public String submitterName;
    public long timestamp;
    public int votes;
    public Set<UUID> voterUuids;

    public Suggestion() {
        this.voterUuids = new HashSet<>();
    }

    public Suggestion(int id, String text, UUID submitterUuid, String submitterName, long timestamp) {
        this.id = id;
        this.text = text;
        this.submitterUuid = submitterUuid;
        this.submitterName = submitterName;
        this.timestamp = timestamp;
        this.votes = 1;
        this.voterUuids = new HashSet<>();
        if (submitterUuid != null) {
            this.voterUuids.add(submitterUuid);
        }
    }

    public boolean hasVoted(UUID uuid) {
        return voterUuids != null && voterUuids.contains(uuid);
    }

    public void addVote(UUID uuid) {
        if (voterUuids == null) voterUuids = new HashSet<>();
        if (voterUuids.add(uuid)) {
            votes = voterUuids.size();
        }
    }

    public String getNormalizedText() {
        if (text == null) return "";
        return text.toLowerCase().trim().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ");
    }
}
