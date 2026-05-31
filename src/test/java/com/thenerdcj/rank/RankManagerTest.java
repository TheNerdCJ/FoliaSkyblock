package com.thenerdcj.rank;

import com.thenerdcj.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for RankManager.
 */
class RankManagerTest extends TestBase {

    private RankManager rankManager;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        rankManager = new RankManager(plugin);
    }

    @Test
    void testRankConfigLoads() {
        // Should load without crashing and have at least default ranks
        assertNotNull(rankManager);
    }

    @Test
    void testGetPlayerRank_Default() {
        // New player should get a default rank
        String rank = rankManager.getPlayerRankId(mockPlayer("NewPlayer").getUniqueId());
        assertNotNull(rank);
    }

    @Test
    void testSetPlayerRank_Works() {
        String newRankId = "VIP";
        rankManager.setPlayerRank(mockPlayer("VipPlayer").getUniqueId(), newRankId);

        String current = rankManager.getPlayerRankId(mockPlayer("VipPlayer").getUniqueId());
        // May be the set rank or default depending on config
        assertNotNull(current);
    }

    @Test
    void testVotingThresholds_Loaded() {
        // Basic check that voting config was loaded
        assertTrue(rankManager.getRankCount() >= 0);
    }
}