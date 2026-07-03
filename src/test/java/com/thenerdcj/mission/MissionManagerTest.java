package com.thenerdcj.mission;

import com.thenerdcj.TestBase;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the wired Mission system.
 *
 * Primary guard: the null-dimension regression. {@code MissionManager.addProgress} must resolve
 * the player's overworld (NORMAL) island; the old code passed a null dimension, which
 * {@code IslandManager.getIsland} always rejects, so every progress event was silently dropped.
 *
 * Uses plain Mockito only (no MockBukkit/Registry) so it runs in the default {@code mvn test}.
 */
class MissionManagerTest extends TestBase {

    private static final String ISLAND_KEY = "0,0,NORMAL";

    private MissionManager missionManager;
    private IslandManager islandManager;
    private Island island;
    private Player player;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        missionManager = new MissionManager(plugin);

        islandManager = plugin.getIslandManager(); // shared mock created in TestBase
        island = mock(Island.class);
        when(island.getId()).thenReturn(ISLAND_KEY);
        when(island.getLevel()).thenReturn(10);

        player = mockPlayer("MissionTester");
        when(islandManager.getIsland(eq(player.getUniqueId()), eq(World.Environment.NORMAL)))
                .thenReturn(island);
    }

    @Test
    void testAddProgress_AdvancesMatchingMission() {
        missionManager.generateDailyMissions(ISLAND_KEY, 10);
        List<Mission> missions = missionManager.getMissionsForIsland(ISLAND_KEY).join();
        assertFalse(missions.isEmpty(), "daily generation should create missions");

        Mission target = missions.get(0);
        int before = target.getProgress();

        missionManager.addProgress(player.getUniqueId(),
                target.getObjective(), target.getTargetMaterial(), 1);

        Mission after = missionManager.getMissionsForIsland(ISLAND_KEY).join().stream()
                .filter(m -> m.getId().equals(target.getId()))
                .findFirst().orElseThrow();
        assertTrue(after.getProgress() > before,
                "progress must advance now that the island resolves via the NORMAL dimension");
    }

    @Test
    void testAddProgress_NoIsland_DoesNotThrow() {
        UUID stranger = UUID.randomUUID(); // no island stubbed -> getIsland returns null
        assertDoesNotThrow(() ->
                missionManager.addProgress(stranger, Mission.ObjectiveType.BREAK_BLOCKS, "STONE", 5));
    }

    @Test
    void testGenerateDailyMissions_CreatesFive() {
        missionManager.generateDailyMissions(ISLAND_KEY, 5);
        long dailies = missionManager.getMissionsForIsland(ISLAND_KEY).join().stream()
                .filter(m -> m.getType() == Mission.MissionType.DAILY)
                .count();
        assertEquals(5, dailies);
    }
}
