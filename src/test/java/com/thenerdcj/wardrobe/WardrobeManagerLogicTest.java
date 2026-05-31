package com.thenerdcj.wardrobe;

import com.thenerdcj.TestBase;
import com.thenerdcj.manager.IslandUpgradeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pure logic tests for WardrobeManager that avoid heavy Bukkit statics.
 * These run reliably in the current test environment.
 */
class WardrobeManagerLogicTest extends TestBase {

    private WardrobeManager wardrobeManager;
    private UUID playerId;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        // Minimal mock setup
        IslandUpgradeManager mockUpgrades = mock(IslandUpgradeManager.class);
        when(mockUpgrades.getMaxWardrobeSlots(any(org.bukkit.entity.Player.class))).thenReturn(12);

        when(plugin.getIslandUpgradeManager()).thenReturn(mockUpgrades);
        when(plugin.getDatabaseManager()).thenReturn(mock(com.thenerdcj.database.DatabaseManager.class));

        wardrobeManager = new WardrobeManager(plugin);
        playerId = UUID.randomUUID();
    }

    @Test
    void testMaxSlotsFromUpgrade() {
        org.bukkit.entity.Player p = mockPlayer("Tester");
        assertEquals(12, wardrobeManager.getMaxSlots(p));
    }

    @Test
    void testRenameViaManager() {
        WardrobeSet set = new WardrobeSet("Original", org.bukkit.Material.CHEST);
        wardrobeManager.saveArmorSet(playerId, 2, set);

        wardrobeManager.renameSet(playerId, 2, "ARMOR", "Renamed Set");

        WardrobeSet updated = wardrobeManager.getArmorSet(playerId, 2);
        assertEquals("Renamed Set", updated.getName());
    }

    @Test
    void testCanUseSlot() {
        org.bukkit.entity.Player p = mockPlayer("Tester");
        assertTrue(wardrobeManager.canUseSlot(p, 5));   // within 12
        assertFalse(wardrobeManager.canUseSlot(p, 15)); // beyond 12
    }
}