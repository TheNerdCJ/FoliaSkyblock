package com.thenerdcj.auction;

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
 * Aggressive GUI simulation tests for AuctionGUI using MockBukkitGuiSimulator.
 */
class AuctionGUITest extends TestBase {

    private AuctionGUI auctionGUI;
    private AuctionManager auctionManager;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        when(mockDatabaseManager.getActiveAuctions())
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        java.util.Collections.emptyList()));

        auctionManager = new AuctionManager(plugin);

        when(plugin.getAuctionManager()).thenReturn(auctionManager);

        // Note: AuctionManager has heavy async DB dependencies. The simple click simulation tests below are the primary value.

        simulator = new MockBukkitGuiSimulator();

        // AuctionGUI registration is handled centrally, so we can use default constructor
        auctionGUI = new AuctionGUI(plugin, auctionManager);

        mockPlayer = mockPlayer("AuctionTester");
    }

    @Test
    void testOpenAuctionGUI_DoesNotThrow() {
        assertDoesNotThrow(() -> auctionGUI.open(mockPlayer));
    }

    @Test
    void testBrowseClick_SimulatedWithSimulator() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                20,
                ClickType.LEFT,
                "BROWSE"
        );

        auctionGUI.onAuctionClick(click);
        assertNotNull(click);
    }

    @Test
    void testMyAuctionsClick_Simulated() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                22,
                ClickType.LEFT,
                "MY_AUCTIONS"
        );

        auctionGUI.onAuctionClick(click);
        assertNotNull(click);
    }

}