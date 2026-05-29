package com.thenerdcj.island;

import com.thenerdcj.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IslandManagerCacheTest extends TestBase {

    private IslandManager islandManager;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        islandManager = new IslandManager(plugin);
    }

    @Test
    void testIslandManagerCanBeCreated() {
        assertNotNull(islandManager);
    }

    @Test
    void testGetIslandByPositionWithNullReturnsNull() {
        assertNull(islandManager.getIslandByPosition(null));
    }
}