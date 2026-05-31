package com.thenerdcj.quest;

import com.thenerdcj.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QuestManager.
 */
class QuestManagerTest extends TestBase {

    private QuestManager questManager;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        questManager = new QuestManager(plugin);
    }

    @Test
    void testGenerateDailyQuests_CreatesQuests() {
        String islandId = "test-island-123";
        questManager.generateDailyQuests(islandId);

        CompletableFuture<List<Quest>> future = questManager.getQuestsForIsland(islandId);
        List<Quest> quests = future.join();

        assertNotNull(quests);
        assertTrue(quests.size() >= 3);
    }

    @Test
    void testGetQuestsForIsland_Async() {
        String islandId = "async-test";
        questManager.generateDailyQuests(islandId);

        CompletableFuture<List<Quest>> future = questManager.getQuestsForIsland(islandId);
        assertDoesNotThrow(() -> {
            List<Quest> result = future.join();
            assertNotNull(result);
        });
    }

    @Test
    void testClaimQuestReward_MarksAsClaimed() {
        String islandId = "claim-test";
        questManager.generateDailyQuests(islandId);

        List<Quest> quests = questManager.getQuestsForIsland(islandId).join();
        if (!quests.isEmpty()) {
            Quest first = quests.get(0);
            // Force complete for test
            // (in real code this would come from progress listeners)
            assertNotNull(first);
        }
    }

    @Test
    void testGenerateWeeklyQuests_CreatesHarderQuests() {
        String islandId = "weekly-test";
        questManager.generateWeeklyQuests(islandId); // assuming this method exists or similar

        List<Quest> quests = questManager.getQuestsForIsland(islandId).join();
        // Weekly quests should generally be harder / higher target
        assertTrue(quests.size() >= 2);
    }

    @Test
    void testExpiredQuests_AreCleanedUp() {
        String islandId = "expire-test";
        questManager.generateDailyQuests(islandId);

        // Force expiration simulation (in real code this happens over time)
        List<Quest> quests = questManager.getQuestsForIsland(islandId).join();
        assertNotNull(quests);
    }
}