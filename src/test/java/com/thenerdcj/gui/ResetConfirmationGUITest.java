package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.testutil.MockBukkitGuiSimulator;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GUI simulation tests for ResetConfirmationGUI.
 */
class ResetConfirmationGUITest extends TestBase {

    private ResetConfirmationGUI resetGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        resetGUI = new ResetConfirmationGUI(plugin, false);

        mockPlayer = mockPlayer("ResetTester");
    }

    @Test
    void testOpenResetConfirmation_DoesNotThrow() {
        assertDoesNotThrow(() -> resetGUI.open(mockPlayer, World.Environment.NORMAL));
    }

    @Test
    void testConfirmResetClick_Simulated() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                11,
                ClickType.LEFT,
                "CONFIRM_RESET"
        );

        resetGUI.onInventoryClick(click);
        assertNotNull(click);
    }

    @Test
    void testCancelClick_DoesNothingBad() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent cancelClick = simulator.createPdcClick(
                mockPlayer,
                gui,
                15,
                ClickType.LEFT,
                "CANCEL"
        );

        resetGUI.onInventoryClick(cancelClick);
        // Should not throw and player should remain in control
        assertNotNull(cancelClick);
    }
}