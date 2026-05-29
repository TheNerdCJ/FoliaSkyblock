package com.thenerdcj.island;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IslandUpgradeTest {

    @Test
    void testBaseCostAndScaling() {
        IslandUpgrade size = IslandUpgrade.ISLAND_SIZE;
        assertEquals(5000, size.getBaseCost());
        assertEquals(5000, size.getCostForLevel(0));
        assertTrue(size.getCostForLevel(1) > 5000);
        assertTrue(size.getCostForLevel(3) > size.getCostForLevel(1));
    }

    @Test
    void testMaxLevelDefault() {
        assertEquals(10, IslandUpgrade.CROP_GROWTH.getMaxLevel());
        assertEquals(10, IslandUpgrade.AUTO_SELLER.getMaxLevel());
    }

    @Test
    void testAllUpgradesHaveValidData() {
        for (IslandUpgrade upgrade : IslandUpgrade.values()) {
            assertTrue(upgrade.getBaseCost() > 0, "Upgrade " + upgrade + " has invalid cost");
            assertNotNull(upgrade.getDisplayName());
            assertNotNull(upgrade.getDescription());
            assertTrue(upgrade.getLevelReq() >= 0);
        }
    }
}