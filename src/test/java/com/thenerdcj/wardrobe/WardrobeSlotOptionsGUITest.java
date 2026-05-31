package com.thenerdcj.wardrobe;

import com.thenerdcj.TestBase;
import com.thenerdcj.testutil.MockBukkitGuiSimulator;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WardrobeSlotOptionsGUI click and chat handling.
 * Uses Mockito for events + real WardrobeManager with H2 where possible.
 */
class WardrobeSlotOptionsGUITest extends TestBase {

    private WardrobeSlotOptionsGUI optionsGUI;
    private WardrobeManager wardrobeManager;
    private Player mockPlayer;
    private UUID playerId;
    private MockBukkitGuiSimulator simulator;
    private NamespacedKey optionKey;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        // Force MockBukkit init so Bukkit statics (including getPluginManager) work
        addPlayer("dummy"); // this triggers defensive MockBukkit initialization in TestBase

        // Setup a real-ish wardrobe manager with H2
        com.thenerdcj.database.DatabaseManager h2 = createH2TestDatabaseManager();
        when(plugin.getDatabaseManager()).thenReturn(h2);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("WardrobeTest"));
        when(plugin.getName()).thenReturn("FoliaSkyblock");

        wardrobeManager = new WardrobeManager(plugin);
        when(plugin.getWardrobeManager()).thenReturn(wardrobeManager);

        // Mock the main WardrobeGUI that options re-opens
        WardrobeGUI mockMainGUI = mock(WardrobeGUI.class);
        when(plugin.getWardrobeGUI()).thenReturn(mockMainGUI);

        optionsGUI = new WardrobeSlotOptionsGUI(plugin, false); // test-friendly: skip auto-registration

        simulator = new MockBukkitGuiSimulator();
        optionKey = createSafeNamespacedKey(plugin, "wardrobe_option");

        mockPlayer = mockPlayer("OptionsTester");
        playerId = mockPlayer.getUniqueId();
    }

    @Test
    void testOpenOptions_DoesNotThrow() {
        // Basic smoke test
        assertDoesNotThrow(() -> optionsGUI.openOptions(mockPlayer, 2, true, "ARMOR"));
    }

    @Test
    void testRightClickClear_CallsManagerAndReopensGUI() {
        // Setup a set first
        WardrobeSet set = new WardrobeSet("ToClear", Material.DIAMOND_SWORD);
        wardrobeManager.saveArmorSet(playerId, 3, set);

        // Create a mock click event for the CLEAR button
        InventoryClickEvent event = createMockClickEvent(mockPlayer, "CLEAR_3_ARMOR_ARMOR");

        optionsGUI.onInventoryClick(event);

        // Verify the set was cleared
        assertNull(wardrobeManager.getArmorSet(playerId, 3));

        // Verify it tried to re-open the main GUI
        verify(plugin.getWardrobeGUI()).openWardrobe(eq(mockPlayer), any());
    }

    @Test
    void testRenameClick_EntersRenameMode() {
        InventoryClickEvent event = createMockClickEvent(mockPlayer, "RENAME_1_EQUIP_EQUIP");

        optionsGUI.onInventoryClick(event);

        // After rename click, player should receive a chat prompt message
        // (we can't easily assert the exact sendMessage without more mocking,
        // but we can verify no exception and that a context was registered internally)
        // For now, just ensure it doesn't crash and inventory was closed
        verify(mockPlayer).closeInventory();
    }

    @Test
    void testChatRename_UpdatesSetName() {
        // First put the player into rename mode
        InventoryClickEvent renameClick = createMockClickEvent(mockPlayer, "RENAME_4_ARMOR_ARMOR");
        optionsGUI.onInventoryClick(renameClick);

        // Simulate the player typing a new name in chat
        AsyncPlayerChatEvent chatEvent = mock(AsyncPlayerChatEvent.class);
        when(chatEvent.getPlayer()).thenReturn(mockPlayer);
        when(chatEvent.getMessage()).thenReturn("My New Set Name");

        optionsGUI.onPlayerChat(chatEvent);

        // Verify rename happened
        WardrobeSet updated = wardrobeManager.getArmorSet(playerId, 4);
        assertNotNull(updated);
        assertEquals("My New Set Name", updated.getName());

        // Verify chat was cancelled
        verify(chatEvent).setCancelled(true);
    }

    private InventoryClickEvent createMockClickEvent(Player player, String actionValue) {
        // Try aggressive real simulation first via the simulator
        if (simulator.isMockBukkitActive()) {
            try {
                InventoryView realView = player.getOpenInventory();
                return simulator.createClickEvent(
                        player,
                        realView.getTopInventory(),
                        10, // arbitrary slot for the button
                        ClickType.RIGHT,
                        optionKey,
                        actionValue
                );
            } catch (Throwable ignored) {
                // Fall back to mocked event
            }
        }

        // Fallback mocked event (stable)
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);

        InventoryView view = mock(InventoryView.class);
        when(view.getTitle()).thenReturn("§6§lWardrobe Options - Slot 1");
        when(event.getView()).thenReturn(view);

        ItemStack clicked = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(), eq(PersistentDataType.STRING))).thenReturn(actionValue);

        when(clicked.getItemMeta()).thenReturn(meta);
        when(clicked.getType()).thenReturn(Material.PAPER);

        when(event.getCurrentItem()).thenReturn(clicked);
        return event;
    }
}