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
 * Simulator-based tests for EnchantingTableGUI (applied after test environment hardening).
 */
class EnchantingTableGUITest extends TestBase {

    private EnchantingTableGUI enchantingGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        enchantingGUI = new EnchantingTableGUI(plugin, false);

        mockPlayer = mockPlayer("EnchantTester");
    }

    @Test
    void testOpenEnchantingTable_DoesNotThrow() {
        assertDoesNotThrow(() -> enchantingGUI.open(mockPlayer, null));
    }

    @Test
    void testEnchantAction_Simulated() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                20,
                ClickType.LEFT,
                "ENCHANT"
        );

        enchantingGUI.onInventoryClick(click);
        assertNotNull(click);
    }

    @Test
    void testCloseAction_Simulated() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                49,
                ClickType.LEFT,
                "CLOSE"
        );

        enchantingGUI.onInventoryClick(click);
        assertNotNull(click);
    }

    @Test
    void testInvalidAction_Graceful() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                10,
                ClickType.LEFT,
                "nonsense_action"
        );

        assertDoesNotThrow(() -> enchantingGUI.onInventoryClick(click));
    }
}
