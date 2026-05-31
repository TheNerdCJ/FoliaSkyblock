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
 * GUI simulation tests for IslandBrowseGUI.
 */
class IslandBrowseGUITest extends TestBase {

    private IslandBrowseGUI browseGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        browseGUI = new IslandBrowseGUI(plugin, false);

        mockPlayer = mockPlayer("BrowseTester");
    }

    @Test
    void testOpenBrowse_DoesNotThrow() {
        assertDoesNotThrow(() -> browseGUI.open(mockPlayer, 1));
    }

    @Test
    void testBrowseClick_Simulated() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                20,
                ClickType.LEFT,
                "VISIT_ISLAND"
        );

        browseGUI.onInventoryClick(click);
        assertNotNull(click);
    }
}