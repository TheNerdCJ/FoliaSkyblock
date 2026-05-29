package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.IslandUpgradeManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * CropGrowthListener
 *
 * Accelerates crop growth based on the island's CROP_GROWTH upgrade level.
 *
 * References & Design:
 * - SuperiorSkyblock2: crops-growth multiplier (applied to random ticks / growth events).
 * - IridiumSkyblock: EnhancementData with per-island cached growth bonuses.
 *
 * This version directly advances Ageable blocks for reliable, immediate effect.
 */
public class CropGrowthListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandUpgradeManager upgradeManager;

    public CropGrowthListener(FoliaSkyblock plugin, IslandUpgradeManager upgradeManager) {
        this.plugin = plugin;
        this.upgradeManager = upgradeManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        Block block = event.getBlock();

        if (!(block.getBlockData() instanceof Ageable ageable)) return;

        Island island = plugin.getIslandManager().getIslandAt(block.getLocation());
        if (island == null) return;

        double multiplier = upgradeManager.getCropGrowthMultiplier(island);
        if (multiplier <= 1.0) return;

        // Chance to grant extra growth tick(s) based on multiplier
        int extraTicks = (int) Math.floor(multiplier - 1.0);
        double chance = (multiplier - 1.0) % 1.0;

        if (ThreadLocalRandom.current().nextDouble() < chance) extraTicks++;

        if (extraTicks > 0) {
            int max = ageable.getMaximumAge();
            if (ageable.getAge() < max) {
                ageable.setAge(Math.min(max, ageable.getAge() + extraTicks));
                block.setBlockData(ageable);
            }
        }
    }
}
