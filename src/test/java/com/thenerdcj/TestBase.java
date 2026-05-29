package com.thenerdcj;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thenerdcj.manager.EconomyManager;

/**
 * Lightweight base for plugin tests using Mockito.
 *
 * MockBukkit support is now integrated for truly realistic tests (AutoSeller cycles etc.).
 * Tests that need a real server/player/inventory simulation should call addPlayer(...)
 * (which lazily initializes the MockBukkit ServerMock). All other tests remain pure Mockito + H2.
 *
 * Cleanup of MockBukkit happens automatically in @AfterEach when active.
 */
public abstract class TestBase {

    protected FoliaSkyblock plugin;

    protected com.thenerdcj.database.DatabaseManager mockDatabaseManager;
    protected EconomyManager mockEconomyManager;

    // === MockBukkit support (lazy, only initialized for tests that need realistic Bukkit statics) ===
    // Stored as Object so the test sources compile even when the optional "with-mockbukkit" profile is inactive.
    protected Object server;
    private boolean mockBukkitActive = false;

    @BeforeEach
    public void setUp() {
        plugin = mock(FoliaSkyblock.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("TestLogger"));

        mockDatabaseManager = mock(com.thenerdcj.database.DatabaseManager.class);
        when(plugin.getDatabaseManager()).thenReturn(mockDatabaseManager);

        mockEconomyManager = mock(EconomyManager.class);
        when(plugin.getEconomyManager()).thenReturn(mockEconomyManager);

        when(mockEconomyManager.getPlayerBalance(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(1000.0));
        when(mockEconomyManager.getIslandBalance(any(com.thenerdcj.database.GridPosition.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(5000.0));

        org.bukkit.configuration.file.FileConfiguration mockConfig = mock(org.bukkit.configuration.file.FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));

        com.thenerdcj.island.IslandManager mockIslandManager = mock(com.thenerdcj.island.IslandManager.class);
        when(plugin.getIslandManager()).thenReturn(mockIslandManager);

        when(mockDatabaseManager.getIslandUpgradeLevel(anyString(), any(com.thenerdcj.island.IslandUpgrade.class)))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(0));

        when(plugin.getBazaarManager()).thenReturn(mock(com.thenerdcj.bazaar.BazaarManager.class));
        when(plugin.getIslandBankManager()).thenReturn(mock(com.thenerdcj.manager.IslandBankManager.class));
        when(plugin.getIslandUpgradeManager()).thenReturn(mock(com.thenerdcj.manager.IslandUpgradeManager.class));

        com.thenerdcj.util.ThreadSafety mockThreadSafety = mock(com.thenerdcj.util.ThreadSafety.class);
        when(plugin.getThreadSafety()).thenReturn(mockThreadSafety);
    }

    /**
     * Lazily initializes MockBukkit if not already active.
     * All access is via reflection so the test sources compile without the optional profile.
     */
    protected void initMockBukkit() {
        if (mockBukkitActive) return;
        try {
            Class<?> mb = Class.forName("be.seeseemelk.mockbukkit.MockBukkit");
            server = mb.getMethod("mock").invoke(null);
            mockBukkitActive = true;
        } catch (Throwable t) {
            System.err.println("[TestBase] MockBukkit init failed (Paper version skew common). Using Mockito fallback.");
            mockBukkitActive = false;
            server = null;
        }
    }

    protected org.bukkit.entity.Player addPlayer(String name) {
        if (!mockBukkitActive) initMockBukkit();
        if (mockBukkitActive && server != null) {
            try {
                Object p = server.getClass().getMethod("addPlayer").invoke(server);
                try { p.getClass().getMethod("setName", String.class).invoke(p, name); } catch (Exception ignored) {}
                return (org.bukkit.entity.Player) p;
            } catch (Throwable t) {
                mockBukkitActive = false;
                server = null;
            }
        }
        return mockPlayer(name);
    }

    protected org.bukkit.entity.Player addPlayer() {
        return addPlayer("TestPlayer" + java.util.UUID.randomUUID().toString().substring(0, 8));
    }

    @AfterEach
    public void tearDown() {
        if (mockBukkitActive && server != null) {
            try {
                Class.forName("be.seeseemelk.mockbukkit.MockBukkit").getMethod("unmock").invoke(null);
            } catch (Exception ignored) {}
            mockBukkitActive = false;
            server = null;
        }
    }

    protected org.bukkit.entity.Player mockPlayer(String name) {
        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        return player;
    }

    protected com.thenerdcj.database.DatabaseManager createH2TestDatabaseManager() {
        com.thenerdcj.database.DatabaseManager db = new com.thenerdcj.database.DatabaseManager(plugin, "jdbc:h2:mem:testdb_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=SQLite");
        db.initDatabase();
        return db;
    }
}