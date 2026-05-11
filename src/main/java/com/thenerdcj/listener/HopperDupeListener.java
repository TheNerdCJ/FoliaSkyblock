package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.AntiCheatManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Small dedicated listener for InventoryMoveItemEvent to close the hopper/chest cross-island dupe gap.
 * 
 * This integrates with the strengthened AntiCheatManager.recordHopperTransfer(...) 
 * which now gracefully handles null Player by resolving island owner (if online) or logging.
 * 
 * Why separate from IslandProtectionListener? 
 * - Keeps concerns separated (protection listener focuses on player actions like break/place/interact; this is for automated block-entity transfers).
 * - Easy to toggle/disable independently via config.
 * - Follows single-responsibility for maintainability in a large skyblock plugin.
 * 
 * Communication with other classes:
 * - AntiCheatManager: calls recordHopperTransfer for detection/punishment
 * - IslandManager (via AntiCheatManager.getIslandAtLocation): to determine if cross-claim
 * - FoliaSkyblock: for plugin instance, config, logger
 * - Standard Bukkit InventoryMoveItemEvent: fired by hoppers, droppers, etc.
 * 
 * Folia compatibility: Event is handled on the correct region thread automatically by Folia.
 * No need for manual RegionScheduler here as we're only reading locations and delegating to manager (which uses thread-safe maps).
 * 
 * Play to Win: Prevents players (or their offline farms) from using hopper networks to dupe items across island boundaries
 * or into the protected spawn area (0,0), which would bypass normal progression, starter chest, island blocks, trading system, and economy.
 * This keeps island XP, upgrades, and leveling balanced even in party/multiplayer setups.
 */
public class HopperDupeListener implements Listener {

    private final FoliaSkyblock plugin;
    private final AntiCheatManager antiCheatManager;

    public HopperDupeListener(FoliaSkyblock plugin, AntiCheatManager antiCheatManager) {
        this.plugin = plugin;
        this.antiCheatManager = antiCheatManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Quick exit if anti-cheat or hopper checks disabled
        if (!antiCheatManager.isEnabled()) return;
        if (!plugin.getConfig().getBoolean("anticheat.hopper-dupe-check", true)) return;

        Location sourceLoc = event.getSource().getLocation();
        Location destLoc = event.getDestination().getLocation();

        // Some inventories (e.g. ender chest, custom) may not have a world location; skip those
        if (sourceLoc == null || sourceLoc.getWorld() == null || 
            destLoc == null || destLoc.getWorld() == null) {
            return;
        }

        // Ignore transfers within the same block (shouldn't happen but defensive)
        if (sourceLoc.equals(destLoc)) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        int amount = item.getAmount();

        // Call the strengthened method - player may be null (automated hopper), handled inside
        // This detects and flags/punishes cross-claim or spawn-area transfers
        antiCheatManager.recordHopperTransfer(null, sourceLoc, destLoc, item, amount);
    }
}
