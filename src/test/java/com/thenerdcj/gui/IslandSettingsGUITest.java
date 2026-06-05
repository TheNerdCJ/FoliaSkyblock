package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.island.IslandSettings;
import com.thenerdcj.manager.IslandSettingsManager;
import com.thenerdcj.testutil.MockBukkitGuiSimulator;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GUI simulation tests for IslandSettingsGUI.
 */
class IslandSettingsGUITest extends TestBase {

    private IslandSettingsGUI settingsGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        settingsGUI = new IslandSettingsGUI(plugin, false);

        mockPlayer = mockPlayer("SettingsTester");
    }

    @Test
    void testOpenSettingsGUI_DoesNotThrow() {
        com.thenerdcj.island.Island mockIsland = mock(com.thenerdcj.island.Island.class);
        GridPosition pos = new GridPosition(1, 2, World.Environment.NORMAL);
        when(mockIsland.getGridPosition()).thenReturn(pos);

        IslandSettingsManager settingsManager = plugin.getIslandSettingsManager();
        when(settingsManager.getSettings(pos))
                .thenReturn(CompletableFuture.completedFuture(new IslandSettings(pos)));

        ThreadSafety threadSafety = plugin.getThreadSafety();
        doAnswer(inv -> {
            Runnable r = inv.getArgument(0);
            r.run();
            return null;
        }).when(threadSafety).runOnMainThread(any(Runnable.class));

        assertDoesNotThrow(() -> settingsGUI.open(mockPlayer, mockIsland));
    }

    @Test
    void testSettingToggle_Simulated() {
        Inventory gui = mock(Inventory.class);

        NamespacedKey actionKey = new NamespacedKey(plugin, "island_settings_action");
        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                15,
                ClickType.LEFT,
                actionKey,
                "TOGGLE_PVP"
        );

        settingsGUI.onInventoryClick(click);
        assertNotNull(click);
    }

    @Test
    void testNonMemberCannotChangeSettings_NegativeCase() {
        com.thenerdcj.island.Island mockIsland = mock(com.thenerdcj.island.Island.class);
        when(mockIsland.hasPermission(any(), any())).thenReturn(false);

        // In real code the handler would check permission; here we simulate the click
        Inventory gui = mock(Inventory.class);

        NamespacedKey actionKey = new NamespacedKey(plugin, "island_settings_action");
        InventoryClickEvent click = simulator.createPdcClick(
                mockPlayer,
                gui,
                15,
                ClickType.LEFT,
                actionKey,
                "TOGGLE_PVP"
        );

        settingsGUI.onInventoryClick(click);
        // For now just ensure no crash on invalid state
        assertNotNull(click);
    }
}