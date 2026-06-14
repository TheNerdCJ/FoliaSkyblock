package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.IslandUpgrade;
import com.thenerdcj.mission.Mission;
import com.thenerdcj.util.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQLite in-memory integration tests for critical persistence flows (production dialect).
 * Run: mvn test (default surefire) or mvn test -Pintegration-db (DB tests only)
 * Or: mvn test -Dtest=DatabaseCriticalFlowsTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseCriticalFlowsTest {

    private static final String SQLITE_MEMORY_URL = "jdbc:sqlite:file:folia_critical_mem?mode=memory&cache=shared";

    private DatabaseManager dbManager;
    private FoliaSkyblock plugin;

    @BeforeAll
    void setup() {
        plugin = Mockito.mock(FoliaSkyblock.class);
        Mockito.when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());
        Mockito.when(plugin.isFolia()).thenReturn(true);
        Mockito.when(plugin.getDataFolder()).thenReturn(new java.io.File("target/test-data"));
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);

        dbManager = new DatabaseManager(plugin, SQLITE_MEMORY_URL);
        dbManager.initDatabase();
    }

    private void awaitAsyncWrites() {
        dbManager.flushCoalescedIslandWrites().join();
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testIslandCreation_Party_DimensionReset_SkillXP_Prestige_Roundtrip() {
        IslandDAO islandDAO = dbManager.getIslandDAO();
        assertNotNull(islandDAO);

        UUID owner = UUID.randomUUID();
        String dimNormal = "NORMAL";
        String dimNether = "NETHER";

        assertTrue(islandDAO.saveIsland(0, 0, owner, dimNormal, "PLAINS").join());
        assertTrue(islandDAO.saveIsland(1, 1, owner, dimNether, "NETHER").join());

        assertNotNull(islandDAO.getIslandByOwner(owner, World.Environment.NORMAL));
        assertNotNull(islandDAO.getIslandByOwner(owner, World.Environment.NETHER));

        islandDAO.recordIslandReset(owner, World.Environment.NETHER);
        assertNotNull(islandDAO.getIslandByOwner(owner, World.Environment.NORMAL));

        assertTrue(islandDAO.saveIslandUpgrade("0,0,NORMAL", IslandUpgrade.ISLAND_SIZE, 2).join());
        Map<IslandUpgrade, Integer> ups = islandDAO.loadIslandUpgrades("0,0,NORMAL").join();
        assertEquals(2, ups.getOrDefault(IslandUpgrade.ISLAND_SIZE, 0));

        Map<com.thenerdcj.island.Island.Skill, Double> xp = new EnumMap<>(com.thenerdcj.island.Island.Skill.class);
        xp.put(com.thenerdcj.island.Island.Skill.MINING, 150.0);
        Map<com.thenerdcj.island.Island.Skill, Integer> lvls = new EnumMap<>(com.thenerdcj.island.Island.Skill.class);
        lvls.put(com.thenerdcj.island.Island.Skill.MINING, 3);
        assertTrue(islandDAO.saveIslandSkills("0,0,NORMAL", xp, lvls).join());
        assertTrue(islandDAO.loadIslandSkills("0,0,NORMAL").join().containsKey(com.thenerdcj.island.Island.Skill.MINING));

        islandDAO.saveIslandPrestige("0,0,NORMAL", 2);
        awaitAsyncWrites();
        assertEquals(2, islandDAO.loadIslandPrestige("0,0,NORMAL").join());

        islandDAO.saveIslandCollection("0,0,NORMAL", "STONE", owner);
        awaitAsyncWrites();
        assertTrue(islandDAO.getIslandCollectionCount("0,0,NORMAL") >= 1);

        MissionDAO missionDAO = dbManager.getMissionDAO();
        Mission testMission = new Mission(
                "0,0,NORMAL", owner, Mission.MissionType.DAILY,
                Mission.ObjectiveType.HARVEST_CROPS, "WHEAT", 5,
                100, 25, null, null, 0,
                "Test Harvest", "Harvest 5 wheat", 86400000L
        );
        assertTrue(missionDAO.saveMission(testMission).join());
        assertFalse(missionDAO.loadMissionsForIsland("0,0,NORMAL").join().isEmpty());

        PrestigeDAO prestigeDAO = dbManager.getPrestigeDAO();
        prestigeDAO.saveIslandPrestige("0,0,NORMAL", 3);
        awaitAsyncWrites();
        assertEquals(3, prestigeDAO.loadIslandPrestige("0,0,NORMAL").join());

        try {
            ItemStack sample = new ItemStack(Material.DIAMOND, 3);
            ItemStack deserialized = ItemSerializer.itemFromBase64(ItemSerializer.itemToBase64(sample));
            assertEquals(Material.DIAMOND, deserialized.getType());
        } catch (ExceptionInInitializerError | IllegalStateException ex) {
            // ItemStack/Material requires Paper registry (use -Pwith-mockbukkit for full item roundtrip)
        }

        GridPosition worthPos = new GridPosition(0, 0, World.Environment.NORMAL);
        islandDAO.saveIslandWorthAsync(worthPos, 12345.67, 42, System.currentTimeMillis()).join();
        awaitAsyncWrites();
        Object[] w = islandDAO.loadIslandWorth(worthPos).join();
        assertTrue(((Double) w[0]) > 10000.0);

        BalanceDAO balanceDAO = dbManager.getBalanceDAO();
        UUID playerBal = UUID.randomUUID();
        assertTrue(balanceDAO.setPlayerBalance(playerBal, 123.45).join());
        assertEquals(123.45, balanceDAO.getPlayerBalance(playerBal).join(), 0.01);

        assertNotNull(dbManager.getCosmeticDAO().loadPlayerTagCollection(owner));
        assertNotNull(dbManager.getPunishmentDAO().getActivePunishments(owner).join());
        assertNotNull(dbManager.getPendingItemsDAO().getPendingItems(owner).join());
    }

    @Test
    void testChestShopDAO_LoadAt() {
        ChestShopDAO shopDao = dbManager.getChestShopDAO();
        UUID owner = UUID.randomUUID();
        ChestShopDAO.ChestShopRow row = new ChestShopDAO.ChestShopRow(
                "lazy_world", 9, 70, 3, owner, "Lazy", "EMERALD", 5.0, 2.0, 12);
        assertTrue(shopDao.save(row).join());
        awaitAsyncWrites();
        var loaded = shopDao.loadAt("lazy_world", 9, 70, 3).join();
        assertTrue(loaded.isPresent());
        assertEquals("EMERALD", loaded.get().itemType());
        assertEquals(12, loaded.get().stock());
    }

    @Test
    void testChestShopDAO_LoadByChunk() {
        ChestShopDAO shopDao = dbManager.getChestShopDAO();
        UUID owner = UUID.randomUUID();
        int chunkX = 1;
        int chunkZ = 2;
        assertTrue(shopDao.save(new ChestShopDAO.ChestShopRow(
                "chunk_w", 20, 64, 35, owner, "In", "STONE", 1.0, 1.0, 1)).join());
        assertTrue(shopDao.save(new ChestShopDAO.ChestShopRow(
                "chunk_w", 25, 64, 38, owner, "In2", "DIRT", 2.0, 2.0, 2)).join());
        assertTrue(shopDao.save(new ChestShopDAO.ChestShopRow(
                "other_w", 20, 64, 35, owner, "Out", "SAND", 3.0, 3.0, 3)).join());
        awaitAsyncWrites();

        var inChunk = shopDao.loadByChunk("chunk_w", chunkX, chunkZ).join();
        assertEquals(2, inChunk.size());
        assertTrue(inChunk.stream().allMatch(r -> "chunk_w".equals(r.world())));
    }

    @Test
    void testChestShopDAO_FlushBatch() {
        ChestShopDAO shopDao = dbManager.getChestShopDAO();
        UUID owner = UUID.randomUUID();
        var rows = java.util.List.of(
                new ChestShopDAO.ChestShopRow("batch_w", 1, 2, 3, owner, "A", "STONE", 1.0, 1.0, 1),
                new ChestShopDAO.ChestShopRow("batch_w", 4, 5, 6, owner, "B", "DIRT", 2.0, 2.0, 2),
                new ChestShopDAO.ChestShopRow("batch_w", 7, 8, 9, owner, "C", "SAND", 3.0, 3.0, 3)
        );
        assertTrue(shopDao.flushBatch(rows).join());
        awaitAsyncWrites();
        assertEquals(3, shopDao.loadAll().join().stream().filter(r -> "batch_w".equals(r.world())).count());
    }

    @Test
    void testChestShopDAO_SaveLoadCount() {
        ChestShopDAO shopDao = dbManager.getChestShopDAO();
        assertNotNull(shopDao);
        UUID owner = UUID.randomUUID();
        ChestShopDAO.ChestShopRow row = new ChestShopDAO.ChestShopRow(
                "world", 1, 64, 2, owner, "Tester", "STONE", 10.0, 5.0, 3);
        assertTrue(shopDao.save(row).join());
        awaitAsyncWrites();
        assertEquals(1, shopDao.loadAll().join().stream()
                .filter(r -> r.x() == 1 && r.y() == 64).count());
        assertTrue(shopDao.delete("world", 1, 64, 2).join());
    }

    @Test
    void testGridDAO_LoadUsedPositions() {
        GridDAO gridDAO = dbManager.getGridDAO();
        assertNotNull(gridDAO);
        IslandDAO islandDAO = dbManager.getIslandDAO();
        UUID owner = UUID.randomUUID();
        islandDAO.saveIsland(7, 7, owner, "NORMAL", "PLAINS").join();
        awaitAsyncWrites();
        var used = gridDAO.loadUsedGridPositions().join();
        assertTrue(used.stream().anyMatch(p -> p.x() == 7 && p.z() == 7));
    }

    @Test
    void testAdminIslandInspect_DAOAccessFlows() {
        IslandDAO islandDAO = dbManager.getIslandDAO();
        UUID owner = UUID.randomUUID();
        GridPosition inspPos = new GridPosition(5, 5, World.Environment.NORMAL);

        islandDAO.saveIsland(5, 5, owner, "NORMAL", "PLAINS").join();
        islandDAO.saveIslandWorthAsync(inspPos, 9876.5, 7, System.currentTimeMillis()).join();
        islandDAO.saveIslandBankBalanceAsync(inspPos, 150.25).join();
        islandDAO.saveIslandSettingsAsync(new com.thenerdcj.island.IslandSettings(inspPos)).join();
        awaitAsyncWrites();

        assertTrue(((Double) islandDAO.loadIslandWorth(inspPos).join()[0]) > 9000.0);
        assertEquals(150.25, islandDAO.loadIslandBankBalance(inspPos).join(), 0.01);
        assertNotNull(islandDAO.loadIslandSettings(inspPos).join());

        islandDAO.saveIslandWarp(new com.thenerdcj.island.IslandWarp(inspPos));
        awaitAsyncWrites();
        assertNotNull(islandDAO.loadIslandWarp(inspPos).join());
        islandDAO.rateIsland(inspPos, owner, 4);
        awaitAsyncWrites();
        assertTrue(islandDAO.getAverageRating(inspPos).join() > 0);
    }

    @Test
    void testIslandBank_Settings_Persistence_Roundtrip() {
        IslandDAO islandDAO = dbManager.getIslandDAO();
        GridPosition bp = new GridPosition(2, 3, World.Environment.NORMAL);
        islandDAO.saveIsland(2, 3, UUID.randomUUID(), "NORMAL", "FOREST").join();

        islandDAO.saveIslandBankBalanceAsync(bp, 42.0).join();
        awaitAsyncWrites();
        assertEquals(42.0, islandDAO.loadIslandBankBalance(bp).join(), 0.001);

        com.thenerdcj.island.IslandSettings s = new com.thenerdcj.island.IslandSettings(bp);
        s.setPvpEnabled(true);
        s.setBorderColor("RED");
        s.setBorderSize(150);
        islandDAO.saveIslandSettingsAsync(s).join();
        awaitAsyncWrites();
        com.thenerdcj.island.IslandSettings loadedS = islandDAO.loadIslandSettings(bp).join();
        assertTrue(loadedS.isPvpEnabled());
        assertEquals("RED", loadedS.getBorderColor());
        assertEquals(150, loadedS.getBorderSize());
    }

    @Test
    void testCoalescedWrites_SingleCommitForManyIslands() {
        dbManager.enableCoalescedWritesForTests();
        IslandDAO islandDAO = dbManager.getIslandDAO();
        islandDAO.resetCoalescedFlushCount();

        int n = 150;
        for (int i = 0; i < n; i++) {
            GridPosition gp = new GridPosition(100 + i, 200 + i, World.Environment.NORMAL);
            islandDAO.saveIsland(100 + i, 200 + i, UUID.randomUUID(), "NORMAL", "PLAINS").join();
            islandDAO.saveIslandWorthAsync(gp, i * 100.0, i % 50, System.currentTimeMillis()).join();
            islandDAO.saveIslandBankBalanceAsync(gp, i * 2.0).join();
            com.thenerdcj.island.IslandSettings settings = new com.thenerdcj.island.IslandSettings(gp);
            islandDAO.saveIslandSettingsAsync(settings).join();
        }

        assertTrue(dbManager.getPendingCoalescedWriteCount() >= n,
                "Coalescer should queue writes before flush");
        islandDAO.flushCoalescedWrites().join();
        assertEquals(1, islandDAO.getCoalescedFlushCount(),
                "Batch flush should use a single SQLite commit for all coalesced rows");
        assertEquals(0, dbManager.getPendingCoalescedWriteCount());

        GridPosition sample = new GridPosition(100, 200, World.Environment.NORMAL);
        assertEquals(0.0, islandDAO.loadIslandBankBalance(sample).join(), 0.01);
        Object[] worth = islandDAO.loadIslandWorth(sample).join();
        assertTrue(((Double) worth[0]) >= 0.0);
    }

    @Test
    void testCoalescedShopPurchases_BatchFlush() {
        dbManager.enableCoalescedShopPurchasesForTests();
        String islandKey = "10,10,NORMAL";
        for (int i = 0; i < 25; i++) {
            dbManager.saveShopPurchase(islandKey, "item_" + i);
        }
        assertTrue(dbManager.getPendingShopPurchaseCount() >= 25);
        dbManager.flushCoalescedShopPurchases().join();
        assertEquals(0, dbManager.getPendingShopPurchaseCount());
        var purchased = dbManager.loadShopPurchasesForIsland(islandKey).join();
        assertEquals(25, purchased.size());
        assertTrue(purchased.contains("item_0"));
        assertTrue(purchased.contains("item_24"));
    }

    @Test
    void testLargeScale_Sqlite_Sim() {
        IslandDAO islandDAO = dbManager.getIslandDAO();
        for (int i = 0; i < 50; i++) {
            GridPosition gp = new GridPosition(i, i, World.Environment.NORMAL);
            UUID o = UUID.randomUUID();
            islandDAO.saveIsland(i, i, o, "NORMAL", "PLAINS").join();
            islandDAO.rateIsland(gp, o, 3 + (i % 3));
        }
        awaitAsyncWrites();
        assertNotNull(islandDAO.getTopRatedIslands(10).join());
        assertNotNull(islandDAO.getTopRatedIslands(5, 10).join());
        assertNotNull(dbManager.getTopIslandsByWorth(5, 5).join());
    }

    @AfterAll
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }
}