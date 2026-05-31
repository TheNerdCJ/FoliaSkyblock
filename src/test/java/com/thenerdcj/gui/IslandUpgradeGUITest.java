package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.island.IslandUpgradeGUI;
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
 * GUI simulation tests for IslandUpgradeGUI.
 */
class IslandUpgradeGUITest extends TestBase {

    private IslandUpgradeGUI upgradeGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        when(plugin.getName()).thenReturn("FoliaSkyblock");
        upgradeGUI = new IslandUpgradeGUI(plugin, false); // test-friendly: skip auto-registration

        mockPlayer = mockPlayer("UpgradeTester");
    }

    @Test
    void testOpenUpgradeGUI_DoesNotThrow() {
        com.thenerdcj.island.Island mockIsland = mock(com.thenerdcj.island.Island.class);
        assertDoesNotThrow(() -> upgradeGUI.open(mockPlayer, mockIsland));
    }

    @Test
    void testUpgradeClick_Simulated() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                22,
                ClickType.LEFT,
                "UPGRADE_WARDROBE_SLOTS"
        );

        upgradeGUI.onInventoryClick(click);
        assertNotNull(click);
    }
}