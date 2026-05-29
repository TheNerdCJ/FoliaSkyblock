package com.thenerdcj.manager;

import com.thenerdcj.TestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinionManagerTest extends TestBase {

    @Test
    void testGetAcceptedFuelMaterials() {
        // Pure method - no plugin needed
        assertFalse(MinionManager.getAcceptedFuelMaterials().isEmpty());
    }
}