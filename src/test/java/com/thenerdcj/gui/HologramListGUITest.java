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
 * GUI simulation tests for HologramListGUI.
 */
class HologramListGUITest extends TestBase {

    private HologramListGUI hologramGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        hologramGUI = new HologramListGUI(plugin, false);

        mockPlayer = mockPlayer("HologramTester");
    }

    @Test
    void testOpenHologramList_DoesNotThrow() {
        assertDoesNotThrow(() -> hologramGUI.open(mockPlayer));
    }

    @Test
    void testHologramClick_Simulated() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                20,
                ClickType.LEFT,
                "EDIT_HOLOGRAM"
        );

        hologramGUI.onInventoryClick(click);
        assertNotNull(click);
    }
}