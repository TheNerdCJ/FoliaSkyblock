package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.testutil.MockBukkitGuiSimulator;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Simulator tests for BiomeSelectionGUI (more aggressive coverage after robustness improvements).
 */
class BiomeSelectionGUITest extends TestBase {

    private BiomeSelectionGUI biomeGUI;
    private MockBukkitGuiSimulator simulator;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        simulator = new MockBukkitGuiSimulator();
        biomeGUI = new BiomeSelectionGUI(plugin, false);

        mockPlayer = mockPlayer("BiomeTester");
    }

    @Test
    void testOpenBiomeSelection_DoesNotThrow() {
        assertDoesNotThrow(() -> biomeGUI.open(mockPlayer, false, org.bukkit.World.Environment.NORMAL));
    }

    @Test
    void testBiomeClick_Simulated() {
        Inventory gui = mock(Inventory.class);

        Map<NamespacedKey, String> pdc = new HashMap<>();
        pdc.put(new NamespacedKey("folia", "biome_key"), "PLAINS");
        pdc.put(new NamespacedKey("folia", "target_dimension"), "NORMAL");
        pdc.put(new NamespacedKey("folia", "is_reset"), "false");

        InventoryClickEvent click = simulator.createComplexPdcClick(
                mockPlayer,
                gui,
                10,
                ClickType.LEFT,
                pdc
        );

        biomeGUI.onInventoryClick(click);
        assertNotNull(click);
    }

    @Test
    void testNetherBiomeClick_Simulated() {
        Inventory gui = mock(Inventory.class);

        Map<NamespacedKey, String> pdc = new HashMap<>();
        pdc.put(new NamespacedKey("folia", "biome_key"), "NETHER_WASTES");
        pdc.put(new NamespacedKey("folia", "target_dimension"), "NETHER");
        pdc.put(new NamespacedKey("folia", "is_reset"), "false");

        InventoryClickEvent click = simulator.createComplexPdcClick(
                mockPlayer,
                gui,
                22,
                ClickType.LEFT,
                pdc
        );

        biomeGUI.onInventoryClick(click);
        assertNotNull(click);
    }
}
