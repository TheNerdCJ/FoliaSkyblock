package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.generator.IslandOreGenerator;
import com.thenerdcj.manager.GridManager;
import com.thenerdcj.manager.IslandUpgradeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

/**
 * CobbleGeneratorListener - Listens for cobblestone formation events (lava + water)
 * and delegates to IslandOreGenerator for possible upgrade-based ore replacement.
 * 
 * Registered in FoliaSkyblock main class onEnable().
 * 
 * Communicates with:
 * - IslandOreGenerator (core logic)
 * - GridManager & IslandUpgradeManager (via the generator class)
 * 
 * Priority HIGH to run after protection checks but before other plugins.
 * Uses existing systems for full integration without duplicating logic.
 */
public class CobbleGeneratorListener implements Listener {

    private final IslandOreGenerator oreGenerator;

    public CobbleGeneratorListener(FoliaSkyblock plugin, GridManager gridManager, IslandUpgradeManager upgradeManager) {
        this.oreGenerator = new IslandOreGenerator(plugin, gridManager, upgradeManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCobbleForm(BlockFormEvent event) {
        oreGenerator.processCobbleFormation(event);
        // Note: processCobbleFormation already checks island ownership, spawn protection, upgrade level
        // If it changes the block, the event's newState is still COBBLESTONE but we overrode with setType()
        // For better compatibility, consider cancelling and setting manually, but setType in form event works in most cases.
    }

    // Optional getter if other classes need direct access to generator instance
    public IslandOreGenerator getOreGenerator() {
        return oreGenerator;
    }
}
