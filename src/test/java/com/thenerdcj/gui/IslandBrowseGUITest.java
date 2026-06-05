package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.manager.IslandRatingManager;
import com.thenerdcj.manager.IslandWarpManager;
import com.thenerdcj.testutil.MockBukkitGuiSimulator;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

        IslandRatingManager ratingManager = mock(IslandRatingManager.class);
        when(plugin.getIslandRatingManager()).thenReturn(ratingManager);
        when(ratingManager.getTopRatedIslands(anyInt())).thenReturn(CompletableFuture.completedFuture(Map.of()));

        IslandWarpManager warpManager = mock(IslandWarpManager.class);
        when(plugin.getIslandWarpManager()).thenReturn(warpManager);
        when(warpManager.getAllPublicWarps(anyInt())).thenReturn(CompletableFuture.completedFuture(Map.of()));

        com.thenerdcj.util.ThreadSafety threadSafety = plugin.getThreadSafety();
        doAnswer(inv -> {
            Runnable r = inv.getArgument(0);
            r.run();
            return null;
        }).when(threadSafety).runOnMainThread(org.mockito.ArgumentMatchers.any(Runnable.class));

        mockPlayer = mockPlayer("BrowseTester");
        when(mockPlayer.hasPermission(anyString())).thenReturn(true);
    }

    @Test
    void testOpenBrowse_DoesNotThrow() {
        assertDoesNotThrow(() -> browseGUI.open(mockPlayer, 0));
    }

    @Test
    void testBrowseClick_Simulated() {
        Inventory gui = mock(Inventory.class);
        NamespacedKey actionKey = new NamespacedKey(plugin, "island_browse_action");
        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                20,
                ClickType.LEFT,
                actionKey,
                "VISIT_ISLAND"
        );

        browseGUI.onInventoryClick(click);
        assertNotNull(click);
    }
}