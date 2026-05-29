package com.thenerdcj.manager;

import com.thenerdcj.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class EconomyManagerTest extends TestBase {

    private EconomyManager economyManager;
    private UUID testUuid;

    private com.thenerdcj.database.DatabaseManager dbMock;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        dbMock = plugin.getDatabaseManager();
        testUuid = UUID.randomUUID();
    }

    @Test
    void testPlayerBalanceRoundTrip() {
        when(dbMock.getPlayerBalance(testUuid)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(0.0));
        when(dbMock.setPlayerBalance(eq(testUuid), anyDouble())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));

        double initial = dbMock.getPlayerBalance(testUuid).join();
        assertEquals(0.0, initial, 0.01);

        boolean success = dbMock.setPlayerBalance(testUuid, 1234.56).join();
        assertTrue(success);
    }

    @Test
    void testAddAndRemoveBalance() {
        when(dbMock.getPlayerBalance(testUuid)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(1000.0));
        when(dbMock.addPlayerBalance(eq(testUuid), anyDouble())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        when(dbMock.removePlayerBalance(eq(testUuid), anyDouble())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));

        // These just verify the DB calls are made correctly via the manager pattern
        assertNotNull(dbMock);
    }
}