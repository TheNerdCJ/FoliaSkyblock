package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandPermission;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.Chunk;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.DragonFireball;

public class IslandProtectionListener implements Listener {

    private final FoliaSkyblock plugin;
    private final IslandManager islandManager;
    private final FileConfiguration config;
    private final com.thenerdcj.manager.IslandUpgradeManager upgradeManager;

    // Configurable values
    private int spawnRadius;
    private boolean wildernessProtection;
    private boolean explosionProtection;
    private boolean pistonProtection;
    private boolean fireProtection;
    private boolean endermanGrief;

    // Pending home island attribution for classic Ender Dragon respawn via End Crystals (central fountain spawn loc)
    // Allows "the island that spawned it" even when dragon entity appears at world origin rather than island grid.
    private String pendingCrystalDragonHome = null;

    public IslandProtectionListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
        this.config = plugin.getConfig();
        this.upgradeManager = plugin.getIslandUpgradeManager();
        loadConfig();
    }

    public void loadConfig() {
        this.spawnRadius = config.getInt("protection.spawn-radius", 50);
        this.wildernessProtection = config.getBoolean("protection.wilderness-protection", true);
        this.explosionProtection = config.getBoolean("protection.explosion-protection", true);
        this.pistonProtection = config.getBoolean("protection.piston-protection", true);
        this.fireProtection = config.getBoolean("protection.fire-protection", true);
        this.endermanGrief = config.getBoolean("protection.enderman-grief", true);
    }

    private boolean canBuild(Player player, Location location) {
        if (player.hasPermission("foliasb.admin.bypass")) return true;

        Island island = islandManager.getIslandAt(location);
        if (island == null) {
            if (isSpawnProtected(location)) {
                sendProtectedMessage(player, "no-build");
                return false;
            }
            return !wildernessProtection;
        }

        // Task 5: size upgrade affects build radius (worldborder already visuals; this makes mechanical)
        if (!isWithinIslandBuildRadius(location, island)) {
            sendProtectedMessage(player, "no-build");
            return false;
        }

        // Visitor system support
        if (!island.hasPermission(player.getUniqueId(), IslandPermission.BUILD)) {
            // Check if they are a visitor and visitors are allowed
            if (canVisitAsGuest(player, island)) {
                // Guests get limited build if owner allows (future: more granular guest perms)
                // For MVP, allow basic interact but restrict heavy building for safety
                return false; // Conservative: visitors cannot build by default
            }
            return false;
        }

        return true;
    }

    private boolean isWithinIslandBuildRadius(Location loc, Island island) {
        if (island == null || loc.getWorld() == null) return true;
        org.bukkit.Location center = island.getCenter(loc.getWorld());
        if (center == null) return true;
        int base = plugin.getConfig().getInt("upgrades.island-size.base-radius", 8);
        int per = plugin.getConfig().getInt("upgrades.island-size.radius-per-level", 2);
        int maxr = plugin.getConfig().getInt("upgrades.island-size.max-radius", 128);
        int lvl = 0;
        try { lvl = upgradeManager.getUpgradeLevel(island.getId(), com.thenerdcj.island.IslandUpgrade.ISLAND_SIZE); } catch (Exception ignored) {}
        int radius = Math.min(base + lvl * per, maxr);
        return loc.distance(center) <= radius;
    }

    private boolean canVisitAsGuest(Player player, Island island) {
        if (player.getUniqueId().equals(island.getOwnerUuid())) return true;
        if (island.hasPermission(player.getUniqueId(), IslandPermission.BUILD)) return true; // member

        // Folia-safe: never block on region thread. Use cached or safe default.
        return plugin.getIslandSettingsManager()
            .getCachedSettings(island.getGridPosition())
            .isVisitorsAllowed();
    }

    private void sendProtectedMessage(Player player, String messageKey) {
        String msg = config.getString("protection.messages." + messageKey, "§cYou cannot build here!");
        // Use MessageUtil.legacy to properly parse § colors (and hex) into Adventure Component.
        // Direct Component.text(msg) would show raw § codes instead of applying chat colors in the action bar.
        Component component = MessageUtil.legacy(msg);
        player.getScheduler().run(plugin, t -> player.sendActionBar(component), null);
    }

    private boolean canInteract(Player player, Location location) {
        if (player.hasPermission("foliasb.admin.bypass")) return true;

        Island island = islandManager.getIslandAt(location);
        if (island == null) return false;

        if (island.hasPermission(player.getUniqueId(), IslandPermission.INTERACT)) {
            return true;
        }

        // Visitor system: allow limited interact if visitors allowed
        // Folia-safe: never block on region thread. Use cached or safe default.
        return plugin.getIslandSettingsManager()
            .getCachedSettings(island.getGridPosition())
            .isVisitorsAllowed();
    }

    private boolean isSpawnProtected(Location loc) {
        return Math.abs(loc.getBlockX()) <= spawnRadius &&
                Math.abs(loc.getBlockZ()) <= spawnRadius;
    }

    // ==================== BLOCK EVENTS ====================

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            sendProtectedMessage(e.getPlayer(), "no-build");
            return;
        }

        // Decrement hopper counter when broken (HOPPER_LIMIT)
        if (e.getBlock().getType() == org.bukkit.Material.HOPPER) {
            Island island = islandManager.getIslandAt(e.getBlock().getLocation());
            if (island != null) {
                String islandId = islandManager.getIslandIdForHopperCount(island);
                islandManager.decrementHopperCount(islandId);
            }
        }

        // Invalidate + delta adjustment (Phase 1 incremental worth optimization)
        Island island = islandManager.getIslandAt(e.getBlock().getLocation());
        if (island != null && plugin.getIslandWorthManager() != null) {
            if (plugin.getIslandWorthManager().isProfileHotPaths()) {
                long start = System.nanoTime();
                plugin.getIslandWorthManager().invalidateCache(island);
                plugin.getIslandWorthManager().adjustBlockWorth(island, e.getBlock().getType(), -1);
                long ns = System.nanoTime() - start;
                if (ns > 100_000L) {
                    plugin.getLogger().info("[IslandProtectionListener] PROFILE: adjustBlockWorth (break) took " + (ns / 1_000_000.0) + " ms");
                }
            } else {
                plugin.getIslandWorthManager().invalidateCache(island);
                plugin.getIslandWorthManager().adjustBlockWorth(island, e.getBlock().getType(), -1);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            sendProtectedMessage(e.getPlayer(), "no-build");
            return;
        }

        // HOPPER_LIMIT enforcement (Tier A)
        if (e.getBlockPlaced().getType() == org.bukkit.Material.HOPPER) {
            Island island = islandManager.getIslandAt(e.getBlock().getLocation());
            if (island != null) {
                String islandId = islandManager.getIslandIdForHopperCount(island);
                int current = islandManager.getCurrentHopperCount(islandId);
                int max = upgradeManager.getMaxHoppers(island);

                if (current >= max) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage("§cYou have reached your hopper limit (" + max + "). Purchase Hopper Limit upgrades to place more.");
                    return;
                }

                islandManager.incrementHopperCount(islandId);
            }
        }

        // Invalidate + delta adjustment (Phase 1 incremental worth optimization)
        Island island = islandManager.getIslandAt(e.getBlock().getLocation());
        if (island != null && plugin.getIslandWorthManager() != null) {
            if (plugin.getIslandWorthManager().isProfileHotPaths()) {
                long start = System.nanoTime();
                plugin.getIslandWorthManager().invalidateCache(island);
                plugin.getIslandWorthManager().adjustBlockWorth(island, e.getBlock().getType(), +1);
                long ns = System.nanoTime() - start;
                if (ns > 100_000L) {
                    plugin.getLogger().info("[IslandProtectionListener] PROFILE: adjustBlockWorth (place) took " + (ns / 1_000_000.0) + " ms");
                }
            } else {
                plugin.getIslandWorthManager().invalidateCache(island);
                plugin.getIslandWorthManager().adjustBlockWorth(island, e.getBlock().getType(), +1);
            }
        }

        // Dragon summon tracking: record the island that placed an End Crystal in the End.
        // This attributes the subsequently spawned Ender Dragon to "the island that spawned it"
        // even if the actual dragon spawn location is the central fountain (grid 0,0, not a player island).
        if (e.getBlockPlaced().getType() == Material.END_CRYSTAL) {
            if (e.getBlockPlaced().getWorld().getEnvironment() == World.Environment.THE_END) {
                Island crystalIsland = islandManager.getIslandAt(e.getBlockPlaced().getLocation());
                if (crystalIsland == null && e.getPlayer() != null) {
                    crystalIsland = islandManager.getIsland(e.getPlayer().getUniqueId(), World.Environment.THE_END);
                }
                if (crystalIsland != null) {
                    pendingCrystalDragonHome = crystalIsland.getId();
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (!canInteract(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
            sendProtectedMessage(e.getPlayer(), "no-interact");
        }
    }

    // ==================== BUCKETS ====================

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    // ==================== EXPLOSIONS ====================

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!explosionProtection) return;

        EnderDragon dragon = resolveDragonForExplosion(e.getEntity());
        if (dragon != null) {
            // Special case for Ender Dragon (or its fireballs): only protect non-home islands.
            // The home ("spawned") island can be damaged by its dragon as part of the fight.
            // This projects/protects surrounding islands while allowing destruction on the attributed home island.
            String homeId = (plugin.getBossManager() != null) ? plugin.getBossManager().getDragonHomeIsland(dragon.getUniqueId()) : null;
            if (homeId != null) {
                e.blockList().removeIf(block -> {
                    Island island = islandManager.getIslandAt(block.getLocation());
                    return island != null && !homeId.equals(island.getId());
                });
                // Anti-cheat audit for projection (even if filtered, log attempt context)
                if (plugin.getAntiCheatManager() != null) {
                    plugin.getAntiCheatManager().reportDragonGriefAttempt(null, "EnderDragon home=" + homeId);
                }
            } else {
                e.blockList().removeIf(block -> islandManager.getIslandAt(block.getLocation()) != null);
            }
            return;
        }

        // Default: protect all islands from other explosions (creepers, tnt, wither, etc.)
        e.blockList().removeIf(block -> islandManager.getIslandAt(block.getLocation()) != null);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (explosionProtection) {
            e.blockList().removeIf(block -> islandManager.getIslandAt(block.getLocation()) != null);
        }
    }

    // ==================== PISTONS ====================

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (pistonProtection && islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
            for (org.bukkit.block.Block block : e.getBlocks()) {
                if (islandManager.getIslandAt(block.getLocation()) == null) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (pistonProtection && islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
            for (org.bukkit.block.Block block : e.getBlocks()) {
                if (islandManager.getIslandAt(block.getLocation()) == null) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ==================== VEHICLES ====================

    @EventHandler
    public void onVehicleCreate(VehicleCreateEvent e) {
        // Optional wilderness control
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        if (e.getAttacker() instanceof Player player) {
            if (!canBuild(player, e.getVehicle().getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    // ==================== HANGING & ARMOR STAND ====================

    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player player) {
            if (!canBuild(player, e.getEntity().getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        if (!canBuild(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ==================== FIRE & GRIEFING ====================

    @EventHandler
    public void onBlockBurn(BlockBurnEvent e) {
        if (fireProtection && islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null && !canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (endermanGrief && e.getEntity() instanceof org.bukkit.entity.Enderman) {
            if (islandManager.getIslandAt(e.getBlock().getLocation()) != null) {
                e.setCancelled(true);
            }
        }

        // Ender Dragon protection: project (protect) islands from other players' dragons
        // The dragon can destroy blocks ONLY on "the island that spawned it" (home island where it was summoned/spawned).
        // This prevents griefing of surrounding islands in shared End or multi-island dimensions.
        if (e.getEntity() instanceof EnderDragon dragon) {
            Island blockIsland = islandManager.getIslandAt(e.getBlock().getLocation());
            if (blockIsland == null) return; // allow wilderness

            if (plugin.getBossManager() != null) {
                String homeId = plugin.getBossManager().getDragonHomeIsland(dragon.getUniqueId());
                if (homeId == null || !homeId.equals(blockIsland.getId())) {
                    e.setCancelled(true);
                }
                // else: matches home island -> allow destruction (as per design for the spawned island)
            } else {
                // fallback: protect all islands if no boss manager
                e.setCancelled(true);
            }
        }
    }

    /**
     * Associate naturally spawned Ender Dragons (e.g. in the End dimension) with the island at spawn location.
     * Player-spawned dragons (via BossManager.spawnBoss) register their home in BossManager.
     * Classic crystal-based respawns are attributed via pendingCrystalDragonHome (set on END_CRYSTAL place).
     * This supports "the dragon can destroy the island that spawned it".
     */
    @EventHandler
    public void onEnderDragonSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.ENDER_DRAGON) return;
        Island island = islandManager.getIslandAt(e.getLocation());
        if (island == null && pendingCrystalDragonHome != null) {
            // Central fountain respawn (common "spawn the ender dragon" via crystals) landed on spawn grid (no island).
            // Attribute to the island of the player who placed the summoning crystal(s).
            if (plugin.getBossManager() != null) {
                plugin.getBossManager().registerDragonHome(e.getEntity().getUniqueId(), pendingCrystalDragonHome);
            }
            pendingCrystalDragonHome = null;
            return;
        }
        if (island != null && plugin.getBossManager() != null) {
            plugin.getBossManager().registerDragonHome(e.getEntity().getUniqueId(), island.getId());
        }
        // consume stale pending if a direct island spawn occurred
        if (pendingCrystalDragonHome != null) {
            pendingCrystalDragonHome = null;
        }
    }

    /**
     * Disable phantoms in the overworld (NORMAL environment) for large server balance.
     * Phantoms can still spawn in other dimensions (Nether/End) for boss mechanics etc.
     * This is a world-level rule, independent of per-island mob spawning settings.
     */
    @EventHandler
    public void onPhantomSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.PHANTOM) return;
        World world = e.getLocation().getWorld();
        if (world != null && world.getEnvironment() == World.Environment.NORMAL) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnderDragonDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof EnderDragon dragon && plugin.getBossManager() != null) {
            String homeId = plugin.getBossManager().getDragonHomeIsland(dragon.getUniqueId());
            plugin.getBossManager().removeBoss(dragon.getUniqueId());
            if (homeId != null && plugin.getQuestManager() != null) {
                plugin.getQuestManager().onEnderDragonKilled(homeId);
            }
        }
        // Clear any pending crystal attribution
        pendingCrystalDragonHome = null;
    }

    /**
     * Respect per-island "Keep Inventory" setting.
     * When enabled on the island where the player dies, keep their inventory and clear drops (standard skyblock friendly behavior).
     * Uses cached settings for hot path safety.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if (player == null) return;
        org.bukkit.Location loc = player.getLocation();
        Island island = islandManager.getIslandAt(loc);
        if (island != null && plugin.getIslandSettingsManager() != null) {
            if (plugin.getIslandSettingsManager().isSettingEnabled(island.getGridPosition(), "KEEP_INVENTORY")) {
                e.setKeepInventory(true);
                e.getDrops().clear();
                // Keep XP level optional (keep false to avoid free XP on death; players can toggle if desired later)
            }
        }
    }

    // ==================== FOLIA WORLD UNLOAD HOOK (edge case cleanup for large servers) ====================

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        World world = e.getWorld();
        if (world == null) return;
        // Delegate cleanup to managers holding per-world / per-island active entities (minions, furniture, structures)
        // Prevents holding references to entities in unloaded worlds (Folia region safety + memory).
        if (plugin.getMinionManager() != null) {
            plugin.getMinionManager().onWorldUnload(world);
        }
        if (plugin.getIslandFurnitureManager() != null) {
            plugin.getIslandFurnitureManager().onWorldUnload(world);
        }
        if (plugin.getIslandStructureManager() != null) {
            plugin.getIslandStructureManager().onWorldUnload(world);
        }
        plugin.getLogger().info("[FoliaSkyblock] Folia unload cleanup performed for world: " + world.getName());
        // Also delegate hologram cleanup (though chunk load will re-spawn persistent ones)
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().onWorldUnload(world);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().onChunkLoad(chunk);
        }
        // Could delegate to other managers (e.g. chest shops, minions) if they need per-chunk respawn.
    }

    // ==================== SPAWN PROTECTION ====================

    @EventHandler
    public void onSpawnInteract(PlayerInteractEvent e) {
        Location loc = e.getClickedBlock() != null ?
                e.getClickedBlock().getLocation() : e.getPlayer().getLocation();

        if (isSpawnProtected(loc)) {
            if (!e.getPlayer().hasPermission("foliasb.admin.bypass")) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * Prevent all damage to players while inside the spawn protection zone (0,0 area).
     * Admin bypass allowed via foliasb.admin.bypass permission.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) {
            return;
        }
        if (isSpawnProtected(player.getLocation())) {
            if (!player.hasPermission("foliasb.admin.bypass")) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * Prevent food level decrease and saturation loss while in spawn.
     * Keeps players at full hunger/saturation inside protected spawn area.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnFoodChange(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player player)) {
            return;
        }
        if (isSpawnProtected(player.getLocation())) {
            if (!player.hasPermission("foliasb.admin.bypass")) {
                if (e.getFoodLevel() < player.getFoodLevel()) {
                    e.setCancelled(true);
                    // Restore to full to prevent starvation/saturation drain
                    player.setFoodLevel(20);
                    player.setSaturation(20.0f);
                }
            }
        }
    }

    /**
     * Cancel exhaustion (the root cause of saturation and hunger drain over time)
     * while the player is inside spawn protection.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnExhaustion(org.bukkit.event.entity.EntityExhaustionEvent e) {
        if (e.getEntity() instanceof Player player) {
            if (isSpawnProtected(player.getLocation()) && !player.hasPermission("foliasb.admin.bypass")) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * Resolves an EnderDragon from a direct entity or from a projectile it fired (e.g. DragonFireball).
     * Used to apply home-island destruction rules for both the dragon entity and its fireballs/attacks.
     * This ensures the "dragon can destroy the island that spawned it" rule applies to fireballs too.
     */
    private EnderDragon resolveDragonForExplosion(org.bukkit.entity.Entity explodingEntity) {
        if (explodingEntity instanceof EnderDragon dragon) {
            return dragon;
        }
        if (explodingEntity instanceof DragonFireball fireball) {
            if (fireball.getShooter() instanceof EnderDragon dragon) {
                return dragon;
            }
        }
        return null;
    }
}