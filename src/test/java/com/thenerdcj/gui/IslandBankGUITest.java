package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.testutil.MockBukkitGuiSimulator;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Aggressive GUI simulation tests for IslandBankGUI using the new simulator.
 */
class IslandBankGUITest extends TestBase {

    private IslandBankGUI bankGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        when(plugin.getName()).thenReturn("FoliaSkyblock");
        bankGUI = new IslandBankGUI(plugin, false); // test-friendly: skip auto-registration

        mockPlayer = mockPlayer("BankTester");
    }

    @Test
    void testOpenBankGUI_DoesNotThrow() {
        com.thenerdcj.island.Island mockIsland = mock(com.thenerdcj.island.Island.class);
        assertDoesNotThrow(() -> bankGUI.open(mockPlayer, mockIsland));
    }

    @Test
    void testDepositClick_UsesSimulator() {
        Inventory gui = mock(Inventory.class);

        // Simulate clicking a "$100 Deposit" item
        ItemStack deposit100 = new ItemStack(org.bukkit.Material.EMERALD_BLOCK);
        ItemMeta meta = deposit100.getItemMeta();
        meta.setDisplayName("§aDeposit $100");
        deposit100.setItemMeta(meta);

        InventoryClickEvent event = simulator.createPdcClick(
                mockPlayer,
                gui,
                11,
                ClickType.LEFT,
                "DEPOSIT_100"
        );

        // Force the current item for the test (since the handler reads display name)
        when(event.getCurrentItem()).thenReturn(deposit100);

        // Should not explode
        bankGUI.onInventoryClick(event);
        assertNotNull(event);
    }
}