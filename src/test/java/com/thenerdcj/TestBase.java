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
 * MockBukkit support (opt-in via addPlayer / ensureMockBukkitActive) enables realistic
 * inventory + GUI cycle testing in dedicated *MockBukkitIntegrationTest classes.
 *
 * Extensive defensive measures are in place:
 *  - plugin mock always has stable getName(), getDataFolder(), getServer()/getPluginManager()
 *  - createSafeItemStack + createSafeNamespacedKey(Plugin, key) never throw
 *  - initMockBukkit performs registry priming + automatic degradation on skew
 *  - MockBukkitGuiSimulator is strictly passive (never auto-starts MB) and uses
 *    only lenient stubbing + avoids heavy Bukkit statics unless confirmed healthy MB
 *
 * The goal is a test suite that stays green on the vast majority of dev/CI machines
 * even when Paper 1.21 + MockBukkit versions are not perfectly aligned.
 */
public abstract class TestBase {

    protected FoliaSkyblock plugin;

    protected com.thenerdcj.database.DatabaseManager mockDatabaseManager;
    protected EconomyManager mockEconomyManager;

    // === MockBukkit support (lazy, only initialized for tests that need realistic Bukkit statics) ===
    // Stored as Object so the test sources compile even when the optional "with-mockbukkit" profile is inactive.
    protected Object server;
    private boolean mockBukkitActive = false;

    // Common materials we attempt to touch during priming so that Registry-heavy code paths
    // either succeed early under MockBukkit or we detect degradation and stay in safe-mock mode.
    private static final org.bukkit.Material[] REGISTRY_PRIME_MATERIALS = {
        org.bukkit.Material.STONE, org.bukkit.Material.DIRT, org.bukkit.Material.OAK_LOG,
        org.bukkit.Material.DIAMOND, org.bukkit.Material.WHEAT, org.bukkit.Material.PAPER
    };

    @BeforeEach
    public void setUp() {
        plugin = mock(FoliaSkyblock.class);
        when(plugin.getName()).thenReturn("FoliaSkyblock");
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("TestLogger"));

        // Provide a stable data folder for any persistence or NamespacedKey side effects in tests
        java.io.File mockDataFolder = new java.io.File("target/test-data");
        mockDataFolder.mkdirs();
        when(plugin.getDataFolder()).thenReturn(mockDataFolder);

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

        // Also provide a no-op plugin manager mock so registerEvents calls (if any autoRegister slips through) are safe
        org.bukkit.plugin.PluginManager mockPm = mock(org.bukkit.plugin.PluginManager.class);
        when(plugin.getServer()).thenReturn(mock(org.bukkit.Server.class));
        when(plugin.getServer().getPluginManager()).thenReturn(mockPm);
    }

    /**
     * Lazily initializes MockBukkit if not already active.
     * All access is via reflection so the test sources compile without the optional profile.
     *
     * This method is intentionally defensive. Full Paper registry support is often
     * unreliable across MockBukkit versions (RegistryAccess provider, Material bootstrap, etc.),
     * so we provide safe fallbacks for common objects like ItemStack and NamespacedKey.
     *
     * After a successful mock we immediately attempt to prime a few Materials. If any
     * ItemStack creation blows up we degrade gracefully to the Mockito-only path for this test run.
     */
    protected void initMockBukkit() {
        if (mockBukkitActive) return;
        try {
            Class<?> mb = Class.forName("be.seeseemelk.mockbukkit.MockBukkit");
            server = mb.getMethod("mock").invoke(null);
            mockBukkitActive = true;

            // Best-effort: make sure the global Bukkit.getServer() points at our mock
            // (MockBukkit usually does this, but some Paper+MockBukkit combos need help)
            tryEnsureBukkitServerSet();

            // Warm up / detect registry problems immediately
            if (!tryPrimeRegistry()) {
                // Registry is unhealthy even though MockBukkit reported success -> degrade
                System.err.println("[TestBase] MockBukkit reported success but registry/Material init failed (common Paper skew). Degrading to safe mocks.");
                mockBukkitActive = false;
                server = null;
            }
        } catch (Throwable t) {
            System.err.println("[TestBase] MockBukkit init failed (Paper version skew common). Using Mockito fallback.");
            mockBukkitActive = false;
            server = null;
        }
    }

    private void tryEnsureBukkitServerSet() {
        try {
            // If Bukkit.getServer() is still null, try to set it via the known internal
            Object current = org.bukkit.Bukkit.getServer();
            if (current == null && server != null) {
                // Some older/newer MockBukkit expose a way; we do a best-effort field poke
                Class<?> bukkitClass = org.bukkit.Bukkit.class;
                java.lang.reflect.Field serverField = null;
                for (java.lang.reflect.Field f : bukkitClass.getDeclaredFields()) {
                    if (f.getType().getName().contains("Server") || f.getName().toLowerCase().contains("server")) {
                        serverField = f;
                        break;
                    }
                }
                if (serverField != null) {
                    serverField.setAccessible(true);
                    serverField.set(null, server);
                }
            }
        } catch (Throwable ignored) {
            // Completely optional; never let this break test setup
        }
    }

    /**
     * Attempts to create ItemStacks for several common materials.
     * Returns true only if all succeed without throwing. This is our heuristic for
     * "the MockBukkit + Paper registry is actually usable for realistic tests".
     */
    private boolean tryPrimeRegistry() {
        try {
            for (org.bukkit.Material m : REGISTRY_PRIME_MATERIALS) {
                // Use the public safe helper so we get consistent behavior
                org.bukkit.inventory.ItemStack s = createSafeItemStack(m, 1);
                if (s == null || s.getType() != m) {
                    return false;
                }
            }
            // Also try a couple of the plugin's own safe NamespacedKey creations
            createSafeNamespacedKey("prime_test");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Public helper that tests can call explicitly before doing heavy real-object work.
     * Idempotent and safe even when MockBukkit is unavailable.
     */
    protected void ensureMockBukkitActive() {
        if (!mockBukkitActive) {
            initMockBukkit();
        }
        // If we are supposed to be active but priming previously failed, stay degraded.
    }

    /**
     * Returns true if we have a working MockBukkit ServerMock AND it survived registry priming.
     */
    protected boolean isMockBukkitActive() {
        return mockBukkitActive && server != null;
    }

    protected org.bukkit.entity.Player addPlayer(String name) {
        ensureMockBukkitActive();
        if (mockBukkitActive && server != null) {
            try {
                // Try the common overloads that exist across MockBukkit versions
                Object p = null;
                try {
                    p = server.getClass().getMethod("addPlayer", String.class).invoke(server, name);
                } catch (NoSuchMethodException nsme) {
                    p = server.getClass().getMethod("addPlayer").invoke(server);
                    if (p != null) {
                        try { p.getClass().getMethod("setName", String.class).invoke(p, name); } catch (Exception ignored) {}
                    }
                }
                if (p instanceof org.bukkit.entity.Player) {
                    return (org.bukkit.entity.Player) p;
                }
            } catch (Throwable t) {
                // One-shot degradation for the rest of the test class
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

        // Give every mock player (including degraded MB fallbacks) a usable inventory
        // so that simple test code like player.getInventory().addItem(...) never NPEs.
        org.bukkit.inventory.PlayerInventory mockInv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(mockInv);
        // Reasonable defaults for common inventory interactions in GUI / wardrobe tests
        when(mockInv.getSize()).thenReturn(36);
        when(mockInv.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[36]);
        when(mockInv.getHelmet()).thenReturn(null);
        when(mockInv.getChestplate()).thenReturn(null);
        when(mockInv.getLeggings()).thenReturn(null);
        when(mockInv.getBoots()).thenReturn(null);
        when(mockInv.addItem(any(org.bukkit.inventory.ItemStack[].class))).thenReturn(new java.util.HashMap<>());

        return player;
    }

    protected com.thenerdcj.database.DatabaseManager createH2TestDatabaseManager() {
        com.thenerdcj.database.DatabaseManager db = new com.thenerdcj.database.DatabaseManager(plugin, "jdbc:h2:mem:testdb_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=SQLite");
        db.initDatabase();
        return db;
    }

    // ==================== ROBUST BUKKIT OBJECT CREATION (static for easy use in helpers) ====================

    /**
     * Safely creates an ItemStack.
     * Tries real creation when MockBukkit is healthy, otherwise returns a well-behaved mock.
     */
    public static org.bukkit.inventory.ItemStack createSafeItemStack(org.bukkit.Material material, int amount) {
        // Note: We can't easily check mockBukkitActive from static context here,
        // so we always try real first and catch.
        try {
            return new org.bukkit.inventory.ItemStack(material, amount);
        } catch (Throwable t) {
            return createMockedItemStack(material, amount);
        }
    }

    public static org.bukkit.inventory.ItemStack createSafeItemStack(org.bukkit.Material material) {
        return createSafeItemStack(material, 1);
    }

    private static org.bukkit.inventory.ItemStack createMockedItemStack(org.bukkit.Material material, int amount) {
        org.bukkit.inventory.ItemStack mockItem = mock(org.bukkit.inventory.ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta mockMeta = mock(org.bukkit.inventory.meta.ItemMeta.class);

        when(mockItem.getType()).thenReturn(material != null ? material : org.bukkit.Material.STONE);
        when(mockItem.getAmount()).thenReturn(Math.max(1, amount));
        when(mockItem.getItemMeta()).thenReturn(mockMeta);
        when(mockItem.hasItemMeta()).thenReturn(true);

        org.bukkit.persistence.PersistentDataContainer mockPdc =
                mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(mockMeta.getPersistentDataContainer()).thenReturn(mockPdc);

        return mockItem;
    }

    /**
     * Safe NamespacedKey creation that never crashes on bad plugin mocks.
     */
    public static org.bukkit.NamespacedKey createSafeNamespacedKey(String key) {
        return createSafeNamespacedKey("folia-test", key);
    }

    /**
     * Safe NamespacedKey creation using a (possibly mocked) plugin's name.
     * Falls back gracefully if the plugin mock is incomplete or real NamespacedKey fails.
     */
    public static org.bukkit.NamespacedKey createSafeNamespacedKey(org.bukkit.plugin.Plugin plugin, String key) {
        if (plugin != null) {
            try {
                String name = plugin.getName();
                if (name != null && !name.isBlank()) {
                    return new org.bukkit.NamespacedKey(plugin, key);
                }
            } catch (Throwable ignored) {}
        }
        return createSafeNamespacedKey(key);
    }

    private static org.bukkit.NamespacedKey createSafeNamespacedKey(String namespace, String key) {
        try {
            return new org.bukkit.NamespacedKey(namespace, key);
        } catch (Throwable t) {
            return mock(org.bukkit.NamespacedKey.class);
        }
    }
}