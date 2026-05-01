package com.thenerdcj.database;

import org.bukkit.World;

/**
 * Represents a grid position for an island.
 * Includes X, Z coordinates and the dimension (Overworld, Nether, End).
 */
public record GridPosition(int x, int z, World.Environment dimension) {

    public GridPosition(int x, int z) {
        this(x, z, World.Environment.NORMAL);
    }

    public int getX() { return x; }
    public int getZ() { return z; }
    public World.Environment getDimension() { return dimension; }

    @Override
    public String toString() {
        return x + "," + z + "," + dimension.name();
    }

    public static GridPosition fromString(String str) {
        String[] parts = str.split(",");
        if (parts.length == 3) {
            try {
                int x = Integer.parseInt(parts[0]);
                int z = Integer.parseInt(parts[1]);
                World.Environment dim = World.Environment.valueOf(parts[2]);
                return new GridPosition(x, z, dim);
            } catch (Exception e) {
                return new GridPosition(0, 0);
            }
        }
        return new GridPosition(0, 0);
    }
}