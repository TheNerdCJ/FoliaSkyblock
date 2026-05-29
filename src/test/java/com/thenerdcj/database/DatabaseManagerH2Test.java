package com.thenerdcj.database;

import com.thenerdcj.TestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerH2Test extends TestBase {

    @Test
    void testH2HelperAvailable() {
        assertNotNull(createH2TestDatabaseManager());
    }
}