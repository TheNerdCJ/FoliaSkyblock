package com.thenerdcj.bazaar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BazaarDomainTest {

    @Test
    void testBazaarItemBasicProperties() {
        BazaarItem diamond = new BazaarItem("DIAMOND", "Diamond", 100.0, 80.0, 1000);
        assertEquals("DIAMOND", diamond.getMaterial());
        assertEquals("Diamond", diamond.getDisplayName());
        assertEquals(100.0, diamond.getBuyPrice());
        assertEquals(80.0, diamond.getSellPrice());
        assertTrue(diamond.canBuy());
        assertTrue(diamond.canSell());
    }

    @Test
    void testBazaarOrderTotalPrice() {
        BazaarOrder buyOrder = new BazaarOrder(
                "abc123", java.util.UUID.randomUUID(),
                "WHEAT", 64, 5.25, true,
                System.currentTimeMillis(), false
        );

        assertEquals(64 * 5.25, buyOrder.getTotalPrice(), 0.001);
        assertTrue(buyOrder.isBuyOrder());
        assertFalse(buyOrder.isFilled());
    }

    @Test
    void testBazaarOrderSellOrder() {
        BazaarOrder sellOrder = new BazaarOrder(
                "def456", java.util.UUID.randomUUID(),
                "IRON_INGOT", 32, 14.0, false,
                System.currentTimeMillis(), false
        );

        assertFalse(sellOrder.isBuyOrder());
        assertEquals(32 * 14.0, sellOrder.getTotalPrice(), 0.001);
    }
}