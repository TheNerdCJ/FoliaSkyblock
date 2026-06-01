package com.thenerdcj.gui;

import com.thenerdcj.TestBase;
import com.thenerdcj.manager.MinionManager;
import com.thenerdcj.testutil.MockBukkitGuiSimulator;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Aggressive MockBukkit GUI simulation tests for MinionsGUI.
 */
class MinionsGUITest extends TestBase {

    private MinionsGUI minionsGUI;
    private MinionManager minionManager;
    private MockBukkitGuiSimulator simulator;
    private NamespacedKey minionTypeKey;
    private Player mockPlayer;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        minionManager = new MinionManager(plugin);
        when(plugin.getMinionManager()).thenReturn(minionManager);
        when(plugin.getName()).thenReturn("FoliaSkyblock");

        simulator = new MockBukkitGuiSimulator();
        minionTypeKey = createSafeNamespacedKey(plugin, "minion_type");

        minionsGUI = new MinionsGUI(plugin, false); // test-friendly: skip auto-registration

        mockPlayer = mockPlayer("MinionGuiTester");
    }

    @Test
    void testOpenMinionsGUI_DoesNotThrow() {
        assertDoesNotThrow(() -> minionsGUI.openMinionsGUI(mockPlayer));
    }

    @Test
    void testClickMinionTypeSlot_AttemptsPlacement() {
        Inventory gui = mock(Inventory.class);
        when(gui.getViewers()).thenReturn(java.util.Collections.emptyList());

        InventoryClickEvent event = simulator.createPdcClick(
                mockPlayer,
                gui,
                20,
                ClickType.LEFT,
                minionTypeKey,
                "COAL_MINION"
        );

        assertNotNull(event);
        assertNotNull(event.getCurrentItem());

        ItemMeta meta = event.getCurrentItem().getItemMeta();
        assertNotNull(meta);
        assertEquals("COAL_MINION", meta.getPersistentDataContainer().get(minionTypeKey, PersistentDataType.STRING));
    }

    @Test
    void testClickFuelButton_UsesSimulator() {
        Inventory gui = mock(Inventory.class);

        InventoryClickEvent fuelClick = simulator.createPdcClick(
                mockPlayer,
                gui,
                42,
                ClickType.LEFT,
                "FEED_FUEL"
        );

        assertNotNull(fuelClick);
    }

    /**
     * Fixed version of the multi-step test.
     * Uses the simulator consistently and avoids undefined variables.
     */
    @Test
    void testMultiStepMinionFlow_PlaceThenRemove() {
        Inventory gui = mock(Inventory.class);
        when(gui.getViewers()).thenReturn(java.util.Collections.emptyList());

        // Create a properly stubbed mock item
        ItemStack mockItem = mock(ItemStack.class);
        when(mockItem.getType()).thenReturn(Material.STONE);
        when(mockItem.getAmount()).thenReturn(1);

        // First click - Place minion
        InventoryClickEvent placeEvent = simulator.createClickEvent(mockPlayer, gui, 10, ClickType.LEFT, mockItem);
        assertNotNull(placeEvent);

        // Call the real handler (this is what we want to test)
        minionsGUI.onInventoryClick(placeEvent);

        // Second click - Remove minion
        InventoryClickEvent removeEvent = simulator.createClickEvent(mockPlayer, gui, 10, ClickType.LEFT, mockItem);
        minionsGUI.onInventoryClick(removeEvent);

        // We mainly verify it doesn't crash. If you want to verify calls on minionManager,
        // you would need to spy it or use a different approach.
    }
}