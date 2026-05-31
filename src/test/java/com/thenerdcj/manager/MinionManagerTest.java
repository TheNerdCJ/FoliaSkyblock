package com.thenerdcj.manager;

import com.thenerdcj.TestBase;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.IslandUpgradeManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MinionManagerTest extends TestBase {

    private MinionManager minionManager;
    private Island mockIsland;
    private Player mockPlayer;
    private String islandId;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        minionManager = new MinionManager(plugin);

        mockIsland = mock(Island.class);
        mockPlayer = mockPlayer("MinionTester");

        islandId = "0,0";
        when(mockIsland.getGridPosition()).thenReturn(new com.thenerdcj.database.GridPosition(0, 0, World.Environment.NORMAL));
        when(mockIsland.isMember(any(UUID.class))).thenReturn(true);
    }

    @Test
    void testGetAcceptedFuelMaterials() {
        assertFalse(MinionManager.getAcceptedFuelMaterials().isEmpty());
    }

    @Test
    void testMaxMinionSlots_Default() {
        when(plugin.getIslandUpgradeManager()).thenReturn(null);
        assertEquals(5, minionManager.getMaxMinionSlots(mockIsland));
    }

    @Test
    void testCanPlaceMinion_RespectsLimit() {
        // Mock upgrade manager to give base slots
        IslandUpgradeManager mockUpgrades = mock(IslandUpgradeManager.class);
        when(mockUpgrades.getUpgradeLevel(anyString(), eq(com.thenerdcj.island.IslandUpgrade.MINION_SLOTS))).thenReturn(0);
        when(plugin.getIslandUpgradeManager()).thenReturn(mockUpgrades);

        // Player is member
        when(mockIsland.isMember(mockPlayer.getUniqueId())).thenReturn(true);

        // Should be able to place when at 0
        assertTrue(minionManager.canPlaceMinion(mockPlayer, mockIsland));
    }

    @Test
    void testGetMinionBreakdown_EmptyIsland() {
        Map<MinionType, Integer> breakdown = minionManager.getMinionBreakdown(islandId);
        assertTrue(breakdown.isEmpty());
    }

    @Test
    void testFuelValuesLoaded() {
        // The manager exposes fuel values statically or via instance
        assertFalse(MinionManager.getAcceptedFuelMaterials().isEmpty());
    }
}