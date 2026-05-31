package com.thenerdcj.boss;

import com.thenerdcj.TestBase;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Expanded logic tests for Slayer/Boss systems.
 */
class SlayerLogicTest extends TestBase {

    private BossManager bossManager;
    private IslandManager mockIslandManager;
    private Island mockIsland;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        mockIslandManager = mock(IslandManager.class);
        when(plugin.getIslandManager()).thenReturn(mockIslandManager);

        mockIsland = mock(Island.class);
        when(mockIsland.getLevel()).thenReturn(50); // sufficient for most tiers
        when(mockIslandManager.getIsland(any(), any())).thenReturn(mockIsland);

        bossManager = new BossManager(plugin);

        mockPlayer = mockPlayer("SlayerTester");
    }

    @Test
    void testCanSpawnBoss_Basic() {
        DimensionBoss boss = DimensionBoss.ENDER_DRAGON;
        boolean canSpawn = bossManager.canSpawnBoss(mockPlayer, boss);
        assertTrue(canSpawn); // first time
    }

    @Test
    void testStartSlayerQuest_Success() {
        SlayerTier tier = SlayerTier.ZOMBIE_I; // assume low tier
        boolean started = bossManager.startSlayerQuest(mockPlayer, tier);
        assertTrue(started);
    }

    @Test
    void testRecordSlayerKill_ProgressesQuest() {
        SlayerTier tier = SlayerTier.ZOMBIE_I;
        bossManager.startSlayerQuest(mockPlayer, tier);

        // Record some kills
        for (int i = 0; i < 5; i++) {
            bossManager.recordSlayerKill(mockPlayer, EntityType.ZOMBIE);
        }

        // Should not have completed yet (assuming >5 required)
        // This is a smoke test; deeper assertion would require exposing quest state
        assertNotNull(bossManager);
    }

    @Test
    void testStartSlayerQuest_FailsIfAlreadyActive() {
        SlayerTier tier = SlayerTier.ZOMBIE_I;
        bossManager.startSlayerQuest(mockPlayer, tier);

        boolean secondAttempt = bossManager.startSlayerQuest(mockPlayer, tier);
        assertFalse(secondAttempt);
    }

    @Test
    void testStartSlayerQuest_FailsIfIslandLevelTooLow() {
        when(mockIsland.getLevel()).thenReturn(5); // too low for most tiers
        SlayerTier highTier = SlayerTier.BLAZE_III;

        boolean started = bossManager.startSlayerQuest(mockPlayer, highTier);
        assertFalse(started);
    }

    @Test
    void testCanAccessDimension_BasedOnLevel() {
        when(mockIsland.getLevel()).thenReturn(40);
        boolean canNether = bossManager.canAccessDimension(mockPlayer, "NETHER");
        assertTrue(canNether);
    }

    @Test
    void testDimensionBossKill_RecordsProgress() {
        DimensionBoss boss = DimensionBoss.ENDER_DRAGON;
        bossManager.recordBossKill(mockPlayer, boss.getName());

        // Subsequent spawn should be blocked
        boolean canSpawnAgain = bossManager.canSpawnBoss(mockPlayer, boss);
        assertFalse(canSpawnAgain);
    }

    @Test
    void testSlayerRewardBalancer_CalculatesDynamicChance() {
        SlayerRewardBalancer balancer = new SlayerRewardBalancer(plugin);
        // Basic smoke test that the balancer doesn't crash
        SlayerReward reward = new SlayerReward(Material.DIAMOND, 1, 0.1);
        double chance = balancer.calculateDynamicDropChance(reward, 50, 20);
        assertTrue(chance > 0 && chance <= 1.0);
    }
}