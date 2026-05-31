package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.manager.TeleportRequestManager;
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
 * Tests for TPAListGUI handleClick using the simulator.
 */
class TPAListGUITest extends TestBase {

    private TPAListGUI tpaGUI;
    private TeleportRequestManager tpaManager;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        tpaManager = mock(TeleportRequestManager.class);
        simulator = new MockBukkitGuiSimulator();

        tpaGUI = new TPAListGUI(plugin, tpaManager);

        mockPlayer = mockPlayer("TPATester");
    }

    @Test
    void testHandleClick_DoesNotThrow() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                20,
                ClickType.LEFT,
                "ACCEPT_TPA"
        );

        // TPAListGUI exposes handleClick instead of implementing Listener
        assertDoesNotThrow(() -> tpaGUI.handleClick(click));
    }
}