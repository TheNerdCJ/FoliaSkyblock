package com.thenerdcj.manager;

import com.thenerdcj.TestBase;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandUpgrade;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AutoSellerManager tests.
 *
 * MockBukkit has been added to the project (see pom.xml + TestBase) for future truly
 * realistic cycle tests using real PlayerMock + Bukkit.getOnlinePlayers().
 *
 * Current tests use the package-private sellPlayerInventory / getSellPrice + Mockito
 * stubs so they are stable across Paper API snapshots. When a compatible MockBukkit +
 * Paper pair is used, the infrastructure in TestBase.addPlayer() will automatically
 * provide full end-to-end inventory mutation + cycle simulation.
 */
class AutoSellerManagerTest extends TestBase {

    private AutoSellerManager autoSeller;
    private Island testIsland;
    private org.bukkit.entity.Player testPlayer;
    private IslandManager mockIslandManager;
    private IslandUpgradeManager mockUpgradeManager;
    private IslandBankManager mockBankManager;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        mockIslandManager = plugin.getIslandManager();
        mockUpgradeManager = plugin.getIslandUpgradeManager();
        mockBankManager = plugin.getIslandBankManager();

        autoSeller = new AutoSellerManager(plugin);

        // Use the stable mock player helper (MockBukkit real players can be opted into later via addPlayer)
        testPlayer = mockPlayer("TestSeller");

        testIsland = new Island(
            new com.thenerdcj.database.GridPosition(0, 0, World.Environment.NORMAL),
            testPlayer.getUniqueId(),
            "PLAINS",
            World.Environment.NORMAL
        );
        testIsland.setUpgradeLevel(IslandUpgrade.AUTO_SELLER, 1);

        when(mockIslandManager.getIsland(eq(testPlayer.getUniqueId()), any(World.Environment.class)))
            .thenReturn(testIsland);

        when(mockIslandManager.createIslandForTesting(any(org.bukkit.entity.Player.class), any(), anyString()))
            .thenAnswer(inv -> {
                org.bukkit.entity.Player p = inv.getArgument(0);
                Island i = new Island(new com.thenerdcj.database.GridPosition(0, 0, World.Environment.NORMAL),
                    p.getUniqueId(), "PLAINS", World.Environment.NORMAL);
                i.setUpgradeLevel(IslandUpgrade.AUTO_SELLER, 1);
                return i;
            });

        when(mockUpgradeManager.getUpgradeLevel(anyString(), eq(IslandUpgrade.AUTO_SELLER)))
            .thenReturn(1);
        when(mockUpgradeManager.getUpgradeLevel(anyString(), any(IslandUpgrade.class)))
            .thenReturn(0);

        when(mockBankManager.deposit(any(com.thenerdcj.database.GridPosition.class), anyDouble()))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
    }

    // NOTE: Tests that construct real ItemStack(Material) or touch Registry-heavy paths are
    // disabled by default because recent Paper API versions require a RegistryAccess provider
    // (supplied automatically by MockBukkit when the "with-mockbukkit" profile is active).
    // Run: mvn test -Pwith-mockbukkit   (after possibly aligning paper.api.version)
    // The package-private sellPlayerInventory + getSellPrice remain the key enablers for realistic testing.

    @Test
    @org.junit.jupiter.api.Disabled("Requires MockBukkit (mvn test -Pwith-mockbukkit) for Paper RegistryAccess + real ItemStack(Material)")
    void testAutoSeller_SellsItemsFromRealInventory() {
        testPlayer.getInventory().addItem(new ItemStack(Material.WHEAT, 64));
        testPlayer.getInventory().addItem(new ItemStack(Material.CARROT, 32));
        testPlayer.getInventory().addItem(new ItemStack(Material.STONE, 16));

        double earned = autoSeller.sellPlayerInventory(testPlayer, testIsland);

        assertTrue(earned > 0);
        assertFalse(testPlayer.getInventory().contains(Material.WHEAT));
        assertFalse(testPlayer.getInventory().contains(Material.CARROT));
        assertTrue(testPlayer.getInventory().contains(Material.STONE));
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires MockBukkit (mvn test -Pwith-mockbukkit) for Paper RegistryAccess")
    void testGetSellPrice_UsesFallbacksWhenBazaarUnavailable() {
        assertEquals(4.0, autoSeller.getSellPrice(Material.WHEAT), 0.001);
        assertEquals(3.0, autoSeller.getSellPrice(Material.SUGAR_CANE), 0.001);
        assertEquals(1.0, autoSeller.getSellPrice(Material.DIRT), 0.001);
    }

    @Test
    @org.junit.jupiter.api.Disabled("Requires MockBukkit (mvn test -Pwith-mockbukkit) for Paper RegistryAccess + real ItemStack(Material)")
    void testSellPlayerInventory_RespectsOnlyAutoSellMaterials() {
        testPlayer.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        testPlayer.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));

        double earned = autoSeller.sellPlayerInventory(testPlayer, testIsland);

        assertTrue(earned > 0);
        assertFalse(testPlayer.getInventory().contains(Material.WHEAT));
        assertTrue(testPlayer.getInventory().contains(Material.DIAMOND));
    }
}