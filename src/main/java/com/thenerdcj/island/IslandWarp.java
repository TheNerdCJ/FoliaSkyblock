package com.thenerdcj.island;

import com.thenerdcj.database.GridPosition;
import org.bukkit.Location;

/**
 * Island Warp - Teleport location for visiting islands
 */
public class IslandWarp {

    private final GridPosition gridPosition;
    private Location warpLocation;
    private boolean enabled;

    public IslandWarp(GridPosition gridPosition) {
        this.gridPosition = gridPosition;
        this.enabled = false;
    }

    public GridPosition getGridPosition() { return gridPosition; }
    public Location getWarpLocation() { return warpLocation; }
    public void setWarpLocation(Location location) {
        this.warpLocation = location;
        this.enabled = true;
    }
    public boolean isEnabled() { return enabled && warpLocation != null; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}