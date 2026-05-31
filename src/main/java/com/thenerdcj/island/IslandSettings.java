package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;

/**
 * Island Settings - Per-island configuration
 */
public class IslandSettings {

    private final GridPosition gridPosition;

    private boolean pvpEnabled = false;
    private boolean visitorsAllowed = true;
    private boolean explosionsEnabled = false;
    private boolean fireSpreadEnabled = false;
    private boolean mobSpawningEnabled = true;
    private boolean cropTramplingEnabled = true;
    private boolean animalSpawningEnabled = true;
    private boolean leafDecayEnabled = true;
    private String borderColor = "BLUE";
    private int borderSize = 100;
    private boolean borderMarkersEnabled = false;  // Persistent hologram markers at corners
    private boolean warpEnabled = false;
    private String warpDescription = "";

    public IslandSettings(GridPosition gridPosition) {
        this.gridPosition = gridPosition;
    }

    public GridPosition getGridPosition() { return gridPosition; }

    public boolean isPvpEnabled() { return pvpEnabled; }
    public boolean isVisitorsAllowed() { return visitorsAllowed; }
    public boolean isExplosionsEnabled() { return explosionsEnabled; }
    public boolean isFireSpreadEnabled() { return fireSpreadEnabled; }
    public boolean isMobSpawningEnabled() { return mobSpawningEnabled; }
    public boolean isCropTramplingEnabled() { return cropTramplingEnabled; }
    public boolean isAnimalSpawningEnabled() { return animalSpawningEnabled; }
    public boolean isLeafDecayEnabled() { return leafDecayEnabled; }
    public String getBorderColor() { return borderColor; }
    public int getBorderSize() { return borderSize; }
    public boolean isWarpEnabled() { return warpEnabled; }
    public String getWarpDescription() { return warpDescription; }

    public void setPvpEnabled(boolean enabled) { this.pvpEnabled = enabled; }
    public void setVisitorsAllowed(boolean allowed) { this.visitorsAllowed = allowed; }
    public void setExplosionsEnabled(boolean enabled) { this.explosionsEnabled = enabled; }
    public void setFireSpreadEnabled(boolean enabled) { this.fireSpreadEnabled = enabled; }
    public void setMobSpawningEnabled(boolean enabled) { this.mobSpawningEnabled = enabled; }
    public void setCropTramplingEnabled(boolean enabled) { this.cropTramplingEnabled = enabled; }
    public void setAnimalSpawningEnabled(boolean enabled) { this.animalSpawningEnabled = enabled; }
    public void setLeafDecayEnabled(boolean enabled) { this.leafDecayEnabled = enabled; }
    public void setBorderColor(String color) { this.borderColor = color; }
    public void setBorderSize(int size) { this.borderSize = Math.max(50, Math.min(500, size)); }
    public boolean isBorderMarkersEnabled() { return borderMarkersEnabled; }
    public void setBorderMarkersEnabled(boolean enabled) { this.borderMarkersEnabled = enabled; }
    public void setWarpEnabled(boolean enabled) { this.warpEnabled = enabled; }
    public void setWarpDescription(String description) { this.warpDescription = description; }

    public boolean toggleSetting(String settingName) {
        switch (settingName.toUpperCase()) {
            case "PVP": this.pvpEnabled = !this.pvpEnabled; return this.pvpEnabled;
            case "VISITORS": this.visitorsAllowed = !this.visitorsAllowed; return this.visitorsAllowed;
            case "EXPLOSIONS": this.explosionsEnabled = !this.explosionsEnabled; return this.explosionsEnabled;
            case "FIRE": this.fireSpreadEnabled = !this.fireSpreadEnabled; return this.fireSpreadEnabled;
            case "MOBS": this.mobSpawningEnabled = !this.mobSpawningEnabled; return this.mobSpawningEnabled;
            case "CROPS": this.cropTramplingEnabled = !this.cropTramplingEnabled; return this.cropTramplingEnabled;
            case "ANIMALS": this.animalSpawningEnabled = !this.animalSpawningEnabled; return this.animalSpawningEnabled;
            case "LEAVES": this.leafDecayEnabled = !this.leafDecayEnabled; return this.leafDecayEnabled;
            case "WARP": this.warpEnabled = !this.warpEnabled; return this.warpEnabled;
            case "BORDER_MARKERS": this.borderMarkersEnabled = !this.borderMarkersEnabled; return this.borderMarkersEnabled;
            default: return false;
        }
    }

    public boolean getSetting(String settingName) {
        return switch (settingName.toUpperCase()) {
            case "PVP" -> pvpEnabled;
            case "VISITORS" -> visitorsAllowed;
            case "EXPLOSIONS" -> explosionsEnabled;
            case "FIRE" -> fireSpreadEnabled;
            case "MOBS" -> mobSpawningEnabled;
            case "CROPS" -> cropTramplingEnabled;
            case "ANIMALS" -> animalSpawningEnabled;
            case "LEAVES" -> leafDecayEnabled;
            case "WARP" -> warpEnabled;
            case "BORDER_MARKERS" -> borderMarkersEnabled;
            default -> false;
        };
    }
}