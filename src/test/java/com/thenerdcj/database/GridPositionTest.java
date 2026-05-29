package com.thenerdcj.database;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GridPositionTest {

    @Test
    void testBasicConstruction() {
        GridPosition pos = new GridPosition(15, -8);
        assertEquals(15, pos.getX());
        assertEquals(-8, pos.getZ());
        assertEquals(World.Environment.NORMAL, pos.getDimension());
    }

    @Test
    void testFromStringValid() {
        GridPosition pos = GridPosition.fromString("42,17,NETHER");
        assertEquals(42, pos.getX());
        assertEquals(17, pos.getZ());
        assertEquals(World.Environment.NETHER, pos.getDimension());
    }

    @Test
    void testFromStringInvalidFallsBack() {
        GridPosition bad = GridPosition.fromString("not,a,valid,position");
        assertEquals(0, bad.getX());
        assertEquals(0, bad.getZ());
    }

    @Test
    void testToStringRoundTrip() {
        GridPosition original = new GridPosition(-5, 99, World.Environment.THE_END);
        GridPosition parsed = GridPosition.fromString(original.toString());
        assertEquals(original.getX(), parsed.getX());
        assertEquals(original.getZ(), parsed.getZ());
        assertEquals(original.getDimension(), parsed.getDimension());
    }
}