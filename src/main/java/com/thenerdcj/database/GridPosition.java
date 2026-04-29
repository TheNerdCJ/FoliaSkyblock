package com.thenerdcj.database;

public record GridPosition(int x, int z) {

    public boolean isSpawn() {
        return x == 0 && z == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GridPosition other) {
            return this.x == other.x && this.z == other.z;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return x * 31 + z;
    }
}