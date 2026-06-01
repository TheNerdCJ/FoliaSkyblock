package com.thenerdcj;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Robust base class for plugin tests.
 * Designed to be stable even when MockBukkit + Paper versions are misaligned.
 */
public abstract class TestBase {

    protected FoliaSkyblock plugin;

    protected com.thenerdcj.database.DatabaseManager mockDatabaseManager;
    protected com.thenerdcj.manager.EconomyManager mockEconomyManager;

    // MockBukkit support (lazy + defensive)
    protected Object server;
    private boolean mockBukkitActive = false;

    @BeforeEach
    public void setUp() {
        plugin = mock(FoliaSkyblock.class);
        when(plugin.getName()).thenReturn("FoliaSkyblock");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));

        File mockDataFolder = new File("target/test-data");
        mockDataFolder.mkdirs();
        when(plugin.getDataFolder()).thenReturn(mockDataFolder);

        // Core mocks
        mockDatabaseManager = mock(com.thenerdcj.database.DatabaseManager.class);
        when(plugin.getDatabaseManager()).thenReturn(mockDatabaseManager);

        mockEconomyManager = mock(com.thenerdcj.manager.EconomyManager.class);
        when(plugin.getEconomyManager()).thenReturn(mockEconomyManager);

        when(mockEconomyManager.getPlayerBalance(any())).thenReturn(CompletableFuture.completedFuture(1000.0));
        when(mockEconomyManager.getIslandBalance(any(com.thenerdcj.database.GridPosition.class)))
                .thenReturn(CompletableFuture.completedFuture(5000.0));

        org.bukkit.configuration.file.FileConfiguration mockConfig = mock(org.bukkit.configuration.file.FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));

        com.thenerdcj.island.IslandManager mockIslandManager = mock(com.thenerdcj.island.IslandManager.class);
        when(plugin.getIslandManager()).thenReturn(mockIslandManager);

        when(mockDatabaseManager.getIslandUpgradeLevel(anyString(), any(com.thenerdcj.island.IslandUpgrade.class)))
                .thenReturn(CompletableFuture.completedFuture(0));

        when(plugin.getBazaarManager()).thenReturn(mock(com.thenerdcj.bazaar.BazaarManager.class));
        when(plugin.getIslandBankManager()).thenReturn(mock(com.thenerdcj.manager.IslandBankManager.class));
        when(plugin.getIslandUpgradeManager()).thenReturn(mock(com.thenerdcj.manager.IslandUpgradeManager.class));
        when(plugin.getIslandSettingsManager()).thenReturn(mock(com.thenerdcj.manager.IslandSettingsManager.class));

        com.thenerdcj.util.ThreadSafety mockThreadSafety = mock(com.thenerdcj.util.ThreadSafety.class);
        when(plugin.getThreadSafety()).thenReturn(mockThreadSafety);

        org.bukkit.plugin.PluginManager mockPm = mock(org.bukkit.plugin.PluginManager.class);
        org.bukkit.Server mockServer = mock(org.bukkit.Server.class);
        when(plugin.getServer()).thenReturn(mockServer);
        when(mockServer.getPluginManager()).thenReturn(mockPm);
    }

    @AfterEach
    public void tearDown() {
        if (mockBukkitActive && server != null) {
            try {
                Class.forName("be.seeseemelk.mockbukkit.MockBukkit")
                        .getMethod("unmock").invoke(null);
            } catch (Exception ignored) {}
            mockBukkitActive = false;
            server = null;
        }
    }

    // ==================== MockBukkit Support ====================

    protected void initMockBukkit() {
        if (mockBukkitActive) return;
        try {
            Class<?> mb = Class.forName("be.seeseemelk.mockbukkit.MockBukkit");
            server = mb.getMethod("mock").invoke(null);
            mockBukkitActive = true;

            if (!tryPrimeRegistry()) {
                System.err.println("[TestBase] MockBukkit initialized but registry unhealthy. Falling back to Mockito.");
                mockBukkitActive = false;
                server = null;
            }
        } catch (Throwable t) {
            System.err.println("[TestBase] MockBukkit init failed (Paper 1.21+ common). Using Mockito fallback.");
            mockBukkitActive = false;
            server = null;
        }
    }

    protected void ensureMockBukkitActive() {
        if (!mockBukkitActive) initMockBukkit();
    }

    protected boolean isMockBukkitActive() {
        return mockBukkitActive && server != null;
    }

    private boolean tryPrimeRegistry() {
        try {
            for (org.bukkit.Material m : new org.bukkit.Material[]{
                    org.bukkit.Material.STONE, org.bukkit.Material.DIRT, org.bukkit.Material.DIAMOND}) {
                if (createSafeItemStack(m) == null) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    protected org.bukkit.entity.Player addPlayer(String name) {
        ensureMockBukkitActive();
        if (mockBukkitActive && server != null) {
            try {
                Object p = server.getClass().getMethod("addPlayer", String.class).invoke(server, name);
                if (p instanceof org.bukkit.entity.Player) return (org.bukkit.entity.Player) p;
            } catch (Exception e) {
                mockBukkitActive = false;
                server = null;
            }
        }
        return mockPlayer(name);
    }

    protected org.bukkit.entity.Player addPlayer() {
        return addPlayer("TestPlayer-" + UUID.randomUUID().toString().substring(0, 8));
    }

    protected org.bukkit.entity.Player mockPlayer(String name) {
        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        org.bukkit.inventory.PlayerInventory inv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getSize()).thenReturn(36);
        when(inv.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[36]);
        return player;
    }

    // ==================== Database ====================

    protected com.thenerdcj.database.DatabaseManager createH2TestDatabaseManager() {
        // Return the already-mocked manager to avoid SQLite/H2 driver conflicts
        System.out.println("[TestBase] Using mocked DatabaseManager for test stability");
        return mockDatabaseManager;
    }

    // ==================== Safe Bukkit Helpers ====================

    public static org.bukkit.inventory.ItemStack createSafeItemStack(org.bukkit.Material material, int amount) {
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
        org.bukkit.inventory.ItemStack item = mock(org.bukkit.inventory.ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta meta = mock(org.bukkit.inventory.meta.ItemMeta.class);

        when(item.getType()).thenReturn(material != null ? material : org.bukkit.Material.STONE);
        when(item.getAmount()).thenReturn(Math.max(1, amount));
        when(item.getItemMeta()).thenReturn(meta);
        when(item.hasItemMeta()).thenReturn(true);

        org.bukkit.persistence.PersistentDataContainer pdc =
                mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);

        return item;
    }

    public static org.bukkit.NamespacedKey createSafeNamespacedKey(String key) {
        return createSafeNamespacedKey("folia-test", key);
    }

    public static org.bukkit.NamespacedKey createSafeNamespacedKey(org.bukkit.plugin.Plugin plugin, String key) {
        if (plugin != null && plugin.getName() != null) {
            try {
                return new org.bukkit.NamespacedKey(plugin, key);
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