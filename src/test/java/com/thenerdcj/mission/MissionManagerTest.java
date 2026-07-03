package com.thenerdcj.mission;

import com.thenerdcj.TestBase;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.manager.IslandBankManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    @Test
    void testGeneratedMissions_OnlyUseFedObjectives() {
        missionManager.generateDailyMissions(ISLAND_KEY, 8);
        missionManager.generateWeeklyMissions(ISLAND_KEY, 8);
        List<Mission.ObjectiveType> fed = List.of(
                Mission.ObjectiveType.BREAK_BLOCKS, Mission.ObjectiveType.PLACE_BLOCKS,
                Mission.ObjectiveType.KILL_MOBS, Mission.ObjectiveType.HARVEST_CROPS,
                Mission.ObjectiveType.FISH_ITEMS, Mission.ObjectiveType.COMPLETE_SLAYERS);
        for (Mission m : missionManager.getMissionsForIsland(ISLAND_KEY).join()) {
            assertTrue(fed.contains(m.getObjective()),
                    "generation must not create dead-end missions: " + m.getObjective());
        }
    }

    @Test
    void testClaim_Success_MarksClaimedAndDepositsOnce() {
        Mission m = completedMoneyMission("mid-success", 1000);
        missionManager.loadMissionsForIsland(ISLAND_KEY, List.of(m));

        IslandBankManager bank = plugin.getIslandBankManager();
        when(island.getGridPosition()).thenReturn(new GridPosition(0, 0, World.Environment.NORMAL));
        when(bank.deposit(any(GridPosition.class), anyDouble())).thenReturn(CompletableFuture.completedFuture(true));

        Boolean ok = missionManager.claimMission(ISLAND_KEY, "mid-success", player).join();

        assertTrue(ok);
        assertTrue(m.isClaimed());
        verify(bank, times(1)).deposit(any(GridPosition.class), eq(1000.0));
    }

    @Test
    void testClaim_DoubleClaim_PaysOnce() {
        Mission m = completedMoneyMission("mid-double", 1000);
        missionManager.loadMissionsForIsland(ISLAND_KEY, List.of(m));

        IslandBankManager bank = plugin.getIslandBankManager();
        when(island.getGridPosition()).thenReturn(new GridPosition(0, 0, World.Environment.NORMAL));
        when(bank.deposit(any(GridPosition.class), anyDouble())).thenReturn(CompletableFuture.completedFuture(true));

        Boolean first = missionManager.claimMission(ISLAND_KEY, "mid-double", player).join();
        Boolean second = missionManager.claimMission(ISLAND_KEY, "mid-double", player).join();

        assertTrue(first);
        assertFalse(second, "a second claim must not pay the reward again");
        verify(bank, times(1)).deposit(any(GridPosition.class), anyDouble());
    }

    @Test
    void testClaim_DepositFails_RollsBackReservation() {
        Mission m = completedMoneyMission("mid-fail", 1000);
        missionManager.loadMissionsForIsland(ISLAND_KEY, List.of(m));

        IslandBankManager bank = plugin.getIslandBankManager();
        when(island.getGridPosition()).thenReturn(new GridPosition(0, 0, World.Environment.NORMAL));
        when(bank.deposit(any(GridPosition.class), anyDouble())).thenReturn(CompletableFuture.completedFuture(false));

        Boolean ok = missionManager.claimMission(ISLAND_KEY, "mid-fail", player).join();

        assertFalse(ok, "a failed deposit must not report success");
        assertFalse(m.isClaimed(), "a failed deposit must roll back the claim so the reward isn't lost");
    }

    @Test
    void testFlushDirtyMissions_PersistsAdvancedMissions() {
        when(mockDatabaseManager.saveMission(any(Mission.class)))
                .thenReturn(CompletableFuture.completedFuture(true));

        missionManager.generateDailyMissions(ISLAND_KEY, 10);
        Mission target = missionManager.getMissionsForIsland(ISLAND_KEY).join().get(0);
        missionManager.addProgress(player.getUniqueId(),
                target.getObjective(), target.getTargetMaterial(), 1);

        missionManager.flushDirtyMissions();

        verify(mockDatabaseManager, atLeastOnce()).saveMission(any(Mission.class));
    }

    /** A daily mission already at target (completed, unclaimed) that rewards money. */
    private Mission completedMoneyMission(String id, int money) {
        long now = System.currentTimeMillis();
        return new Mission(id, ISLAND_KEY, null, Mission.MissionType.DAILY,
                Mission.ObjectiveType.KILL_MOBS, "ANY", 5, 5,
                money, 0, null, null, 0,
                true, false, now, now + 86_400_000L, "Kill Mobs", "desc");
    }
}
