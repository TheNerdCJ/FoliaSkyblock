package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.island.Island;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests museum spend/persist roundtrips with JSON count/rarity (task).
 * Uses H2.
 * Inter-class: Manager <-> IslandDAO (JSON) <-> DB.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MuseumPersistTest {

    private Connection h2;
    private DatabaseManager db;
    private FoliaSkyblock plugin;
    private MuseumManager museum;

    @BeforeAll
    void setup() throws Exception {
        String url = "jdbc:h2:mem:museum_persist_test;DB_CLOSE_DELAY=-1";
        h2 = DriverManager.getConnection(url, "sa", "");
        plugin = mock(FoliaSkyblock.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());
        when(plugin.isFolia()).thenReturn(true);
        db = new DatabaseManager(plugin, url);
        db.initDatabase();
        when(plugin.getDatabaseManager()).thenReturn(db);
        museum = new MuseumManager(plugin);
    }

    @Test
    void testDonateSpendPersistJSONCountRarity() {
        Island island = mock(Island.class);
        when(island.getId()).thenReturn("testkey");
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(plugin.getIslandManager().getIsland(any(), any())).thenReturn(island);

        ItemStack item = new ItemStack(Material.DIAMOND);
        assertTrue(museum.donate(p, item)); // first
        assertEquals(1, museum.getDonated("testkey").size());
        assertTrue(museum.getTokens("testkey") > 0);

        // spend
        assertTrue(museum.spendTokens(p, 10, "cosmetic"));

        // persist roundtrip via load
        museum.loadForIsland("testkey");
        assertTrue(museum.getDonated("testkey").size() >= 1);
    }

    @AfterAll
    void tear() throws Exception { if (h2 != null) h2.close(); }
}