package com.thenerdcj.challenge;

import com.thenerdcj.TestBase;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the AI-powered Challenge system.
 */
class ChallengeManagerTest extends TestBase {

    private ChallengeManager challengeManager;
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
        when(mockIsland.getLevel()).thenReturn(25);
        when(mockIslandManager.getIsland(any(), any())).thenReturn(mockIsland);

        challengeManager = new ChallengeManager(plugin);

        mockPlayer = mockPlayer("ChallengeTester");
    }

    @Test
    void testGenerateDailyChallenges_ReturnsList() {
        List<Challenge> challenges = challengeManager.generateChallenges(mockPlayer, false);
        assertNotNull(challenges);
        assertFalse(challenges.isEmpty());
        assertTrue(challenges.size() >= 3); // at least some challenges
    }

    @Test
    void testGenerateWeeklyChallenges_ReturnsMore() {
        List<Challenge> weekly = challengeManager.generateChallenges(mockPlayer, true);
        assertNotNull(weekly);
        assertTrue(weekly.size() >= 5);
    }

    @Test
    void testCreateSmartChallenge_Validates() {
        PlayerChallengeProfile profile = new PlayerChallengeProfile(mockPlayer.getUniqueId());
        Challenge challenge = challengeManager.createSmartChallenge(
                mockPlayer, 25, java.util.Collections.emptyMap(), false,
                java.util.concurrent.ThreadLocalRandom.current(), profile
        );

        assertNotNull(challenge);
        assertTrue(challenge.getTarget() > 0);
        assertTrue(challenge.getRewardXP() > 0);
    }

    @Test
    void testValidator_PreventsImpossibleChallenges() {
        ChallengeValidator validator = new ChallengeValidator();
        Challenge badChallenge = new Challenge(
                mockPlayer.getUniqueId(), Challenge.Type.DAILY, "FARMING",
                "Harvest 999999 wheat", 999999, 100
        );

        boolean valid = validator.isValidChallenge(badChallenge, mockPlayer, 25);
        assertFalse(valid); // should be rejected
    }

    @Test
    void testPlayerProfile_TracksHistory() {
        PlayerChallengeProfile profile = new PlayerChallengeProfile(mockPlayer.getUniqueId());
        // Basic construction and usage test
        assertNotNull(profile);
    }

    @Test
    void testGenerateChallenges_RespectsIslandLevel() {
        when(mockIsland.getLevel()).thenReturn(5); // very low level

        List<Challenge> lowLevelChallenges = challengeManager.generateChallenges(mockPlayer, false);
        assertFalse(lowLevelChallenges.isEmpty());

        // All challenges should have reasonable targets for low level
        for (Challenge c : lowLevelChallenges) {
            assertTrue(c.getTarget() <= 100); // rough sanity
        }
    }

    @Test
    void testValidator_AllowsReasonableChallenges() {
        ChallengeValidator validator = new ChallengeValidator();
        Challenge goodChallenge = new Challenge(
                mockPlayer.getUniqueId(), Challenge.Type.DAILY, "MINING",
                "Mine 50 stone", 50, 20
        );

        boolean valid = validator.isValidChallenge(goodChallenge, mockPlayer, 25);
        assertTrue(valid);
    }
}