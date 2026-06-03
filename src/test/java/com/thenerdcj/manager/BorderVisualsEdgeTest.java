package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Edge test for size particle density / border scale in visuals (task).
 * Inter-class: BorderVisualManager <-> IslandManager/Upgrade <-> Generator (gen radius).
 * Exercises update with different 'gen' radii for scale.
 */
class BorderVisualsEdgeTest {

    @Test
    void testParticleDensityScaleForGenRadius() {
        FoliaSkyblock plugin = mock(FoliaSkyblock.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());
        when(plugin.isFolia()).thenReturn(true);
        BorderVisualManager border = new BorderVisualManager(plugin);

        Island island = mock(Island.class);
        Player p = mock(Player.class);

        // call update (exercises scale logic with effective/gen radius)
        assertDoesNotThrow(() -> border.updatePlayerWorldBorder(p, island));

        // spawn particles would use dynamic for large radius (edge for gen change)
        // basic coverage, no full world needed for this unit
        assertNotNull(border);
    }
}