package com.thenerdcj.hologram;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for a persistent admin hologram.
 * Stored in database and used to (re)spawn TextDisplay entities.
 * FoliaSkyblock - Play to Win compliant, admin-only tool for info/leaderboards.
 */
public class HologramData {

    private int id = -1;
    private String name;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private String billboard = "CENTER";
    private String backgroundColor; // e.g. "#00000080" or null for default
    private double scale = 1.0;
    private boolean seeThrough = false;
    private boolean shadow = true;
    private String permission; // optional permission node or rank name
    private final List<String> lines = new ArrayList<>();

    // Dynamic leaderboard support
    private boolean dynamic = false;
    private String dynamicType;      // e.g. "TOP_ISLANDS_LEVEL", "TOP_ISLANDS_XP", "TOP_PLAYERS_BALANCE"
    private int updateInterval = 300; // seconds (default 5 minutes)

    public HologramData() {}

    public HologramData(String name, String worldName, double x, double y, double z) {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public String getBillboard() { return billboard; }
    public void setBillboard(String billboard) { this.billboard = billboard; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

    public double getScale() { return scale; }
    public void setScale(double scale) { this.scale = scale; }

    public boolean isSeeThrough() { return seeThrough; }
    public void setSeeThrough(boolean seeThrough) { this.seeThrough = seeThrough; }

    public boolean isShadow() { return shadow; }
    public void setShadow(boolean shadow) { this.shadow = shadow; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public List<String> getLines() { return lines; }

    public void setLines(List<String> lines) {
        this.lines.clear();
        if (lines != null) this.lines.addAll(lines);
    }

    public void addLine(String line) {
        this.lines.add(line);
    }

    public void setLine(int index, String line) {
        if (index >= 0 && index < lines.size()) {
            lines.set(index, line);
        }
    }

    public void removeLine(int index) {
        if (index >= 0 && index < lines.size()) {
            lines.remove(index);
        }
    }

    // === Dynamic Leaderboard Getters/Setters ===
    public boolean isDynamic() { return dynamic; }
    public void setDynamic(boolean dynamic) { this.dynamic = dynamic; }

    public String getDynamicType() { return dynamicType; }
    public void setDynamicType(String dynamicType) { this.dynamicType = dynamicType; }

    public int getUpdateInterval() { return updateInterval; }
    public void setUpdateInterval(int updateInterval) { this.updateInterval = Math.max(30, updateInterval); } // minimum 30s
}
