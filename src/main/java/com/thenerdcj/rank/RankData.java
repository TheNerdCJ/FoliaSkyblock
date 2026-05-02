package com.thenerdcj.rank;

import java.util.List;
import java.util.ArrayList;

public class RankData {
    private final String id;
    private final String displayName;
    private final int level;
    private final String category;
    private final int voteRequirement;
    private final double price;
    private final String chatPrefix;
    private final String permission;
    private final List<String> perks;
    private final List<String> inherits;
    private final List<String> permissions;

    public RankData(String id, String displayName, int level, String category,
                    int voteRequirement, double price, String chatPrefix,
                    String permission, List<String> perks, List<String> inherits,
                    List<String> permissions) {
        this.id = id;
        this.displayName = displayName;
        this.level = level;
        this.category = category;
        this.voteRequirement = voteRequirement;
        this.price = price;
        this.chatPrefix = chatPrefix;
        this.permission = permission;
        this.perks = perks != null ? perks : new ArrayList<>();
        this.inherits = inherits != null ? inherits : new ArrayList<>();
        this.permissions = permissions != null ? permissions : new ArrayList<>();
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getLevel() { return level; }
    public String getCategory() { return category; }
    public int getVoteRequirement() { return voteRequirement; }
    public double getPrice() { return price; }
    public String getChatPrefix() { return chatPrefix; }
    public String getPermission() { return permission; }
    public List<String> getPerks() { return perks; }
    public List<String> getInherits() { return inherits; }
    public List<String> getPermissions() { return permissions; }

    public boolean isStaff() { return "staff".equalsIgnoreCase(category); }
    public boolean isDonor() { return "donor".equalsIgnoreCase(category); }
    public boolean isDefault() { return "default".equalsIgnoreCase(category); }
}