package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.testutil.MockBukkitGuiSimulator;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GUI simulation tests for QuestLogGUI.
 */
class QuestLogGUITest extends TestBase {

    private QuestLogGUI questGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        questGUI = new QuestLogGUI(plugin, false);

        mockPlayer = mockPlayer("QuestTester");
    }

    @Test
    void testOpenQuestLog_DoesNotThrow() {
        String islandId = "test-island";
        assertDoesNotThrow(() -> questGUI.open(mockPlayer, islandId));
    }

    @Test
    void testQuestClick_Simulated() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                20,
                ClickType.LEFT,
                "VIEW_QUEST"
        );

        questGUI.onInventoryClick(click);
        assertNotNull(click);
    }
}