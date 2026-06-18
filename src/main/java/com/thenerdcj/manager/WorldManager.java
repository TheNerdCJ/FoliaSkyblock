package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.generator.VoidChunkGenerator;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * WorldManager - Creates and manages custom void worlds for FoliaSkyblock.
 * Generates a basic safe spawn platform at the configured center (default 0,100,0) so players don't fall into the void.
 * The platform is minimal: a flat solid area of safe blocks. 
 * This allows admins to build a custom spawn/hub on top or around it without it being overwritten (uses skip-if-built marker).
 * No complex procedural features, no schematics for spawn.
 * Batched for Folia region safety. Join can be blocked until ready via config.
 */
public class WorldManager {

    private final FoliaSkyblock plugin;
    private static final int SPAWN_Y = 100;
    private static final int RING_RADIUS = 26; // for consistent paved boulevard band around ring road

    /** False until hub spawn island generation finishes (or is detected as already built). */
    private volatile boolean hubSpawnReady;

    // Consistent world names (matches FoliaSkyblock.getSkyblockWorld())
    private static final String OVERWORLD_NAME = "skyblock";
    private static final String NETHER_NAME = "skyblock_nether";
    private static final String END_NAME = "skyblock_end";
    /** Hidden marker placed when hub generation completes (not used by vanilla spawn selection). */
    private static final Material SPAWN_COMPLETE_MARKER = Material.LODESTONE;

    public WorldManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize all custom Skyblock void worlds
     */
    public void initializeWorlds() {
        MessageUtil.info(plugin.getLogger(), "§6[WorldManager] Initializing custom void worlds...");
        hubSpawnReady = !isSpawnPlatformGenerationEnabled();

        initializeWorldsOnFolia();
        return;
    }

    /**
     * Folia does not support {@link WorldCreator#createWorld()} from the global region scheduler.
     * Resolve worlds from config/bukkit.yml instead and only build spawn on an existing overworld.
     */
    private static final int FOLIA_WORLD_RESOLVE_MAX_ATTEMPTS = 40;
    private volatile boolean foliaWorldInitCompleted;

    private void initializeWorldsOnFolia() {
        foliaWorldInitCompleted = false;
        attemptInitializeWorldsOnFolia(0);
    }

    private void attemptInitializeWorldsOnFolia(int attempt) {
        World overworld = resolveSkyblockWorld(World.Environment.NORMAL);
        World nether = resolveSkyblockWorld(World.Environment.NETHER);
        World end = resolveSkyblockWorld(World.Environment.THE_END);

        if (overworld == null && attempt < FOLIA_WORLD_RESOLVE_MAX_ATTEMPTS) {
            if (attempt == 0) {
                MessageUtil.info(plugin.getLogger(), "§e[WorldManager] Waiting for server level/dimensions to finish loading...");
            }
            plugin.getThreadSafety().runOnMainThreadLater(
                    () -> attemptInitializeWorldsOnFolia(attempt + 1), 5L);
            return;
        }

        if (overworld == null) {
            MessageUtil.warning(plugin.getLogger(), "§c[WorldManager] Overworld never loaded — cannot generate spawn hub. "
                    + "Allowing joins without spawn gate.");
            hubSpawnReady = true;
            // Still attempt to load holograms (spawn-area ones will gracefully no-op / warn)
            if (plugin.getHologramManager() != null) {
                plugin.getHologramManager().loadAndSpawnAll();
            }
            return;
        }

        logResolvedWorlds(overworld, nether, end);

        if (overworld != null && !foliaWorldInitCompleted) {
            foliaWorldInitCompleted = true;
            generateSpawnPlatform(overworld);
        }
    }

    private void logResolvedWorlds(World overworld, World nether, World end) {
        if (overworld == null) {
            MessageUtil.warning(plugin.getLogger(), "§e[WorldManager] Overworld not found. Set worlds.overworld to your server level-name "
                    + "(server.properties level-name, e.g. skyblock). Loaded worlds: " + describeLoadedWorlds());
            return;
        }

        MessageUtil.info(plugin.getLogger(), "§a[WorldManager] Overworld: " + overworld.getName() + " (" + overworld.getEnvironment() + ")");

        if (nether == null) {
            MessageUtil.warning(plugin.getLogger(), "§e[WorldManager] Nether dimension not loaded yet. "
                    + "On 26.1+ this is usually inside level '" + levelName() + "/dimensions/minecraft/the_nether' — "
                    + "visit nether once or ensure bukkit.yml lists the dimension.");
        } else {
            MessageUtil.info(plugin.getLogger(), "§a[WorldManager] Nether: " + nether.getName() + " (" + nether.getEnvironment() + ")");
        }

        if (end == null) {
            MessageUtil.warning(plugin.getLogger(), "§e[WorldManager] End dimension not loaded yet. "
                    + "On 26.1+ this is usually inside level '" + levelName() + "/dimensions/minecraft/the_end'.");
        } else {
            MessageUtil.info(plugin.getLogger(), "§a[WorldManager] End: " + end.getName() + " (" + end.getEnvironment() + ")");
        }

        if (overworld != null && nether != null && end != null) {
            MessageUtil.info(plugin.getLogger(), "§a[WorldManager] All Skyblock dimensions resolved on Folia.");
        }
    }

    private String describeLoadedWorlds() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (World world : Bukkit.getWorlds()) {
            if (!first) sb.append(", ");
            sb.append(world.getName()).append('/').append(world.getEnvironment());
            first = false;
        }
        if (first) sb.append("none");
        return sb.append(']').toString();
    }

    /**
     * Resolves a Skyblock dimension. Supports both legacy multi-folder worlds (skyblock_nether) and
     * modern single-level layouts (level-name skyblock with dimensions/ subfolders on 26.1+).
     */
    public World resolveSkyblockWorld(World.Environment environment) {
        if (environment == null) {
            return null;
        }

        for (String candidate : worldNameCandidates(environment)) {
            World world = Bukkit.getWorld(candidate);
            if (world != null && world.getEnvironment() == environment) {
                return world;
            }
        }

        String levelName = levelName();
        World levelMatch = null;
        World anyEnvMatch = null;

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != environment) {
                continue;
            }
            anyEnvMatch = world;
            if (world.getName().equalsIgnoreCase(levelName)) {
                levelMatch = world;
            }
        }

        if (levelMatch != null) {
            return levelMatch;
        }

        if (anyEnvMatch != null) {
            plugin.getLogger().fine("[WorldManager] Resolved " + environment + " via environment fallback: "
                    + anyEnvMatch.getName());
            return anyEnvMatch;
        }

        return null;
    }

    /** Server level-name (server.properties level-name / primary overworld folder). */
    public String levelName() {
        return plugin.getConfig().getString("worlds.overworld", OVERWORLD_NAME);
    }

    private java.util.List<String> worldNameCandidates(World.Environment environment) {
        String level = levelName();
        java.util.List<String> names = new java.util.ArrayList<>();
        switch (environment) {
            case NETHER -> {
                names.add(plugin.getConfig().getString("worlds.nether", NETHER_NAME));
                names.add(NETHER_NAME);
                names.add(level);
            }
            case THE_END -> {
                names.add(plugin.getConfig().getString("worlds.end", END_NAME));
                names.add(END_NAME);
                names.add("skyblock_the_end");
                names.add(level);
            }
            default -> {
                names.add(level);
                names.add(OVERWORLD_NAME);
            }
        }
        return names.stream().filter(n -> n != null && !n.isBlank()).distinct().toList();
    }

    /**
     * Returns the main overworld (skyblock world)
     */
    public World getMainWorld() {
        return resolveSkyblockWorld(World.Environment.NORMAL);
    }

    /**
     * Create a void world asynchronously
     */
    private CompletableFuture<World> createVoidWorldAsync(String worldName, World.Environment environment) {
        return CompletableFuture.supplyAsync(() -> {
            World existing = Bukkit.getWorld(worldName);
            if (existing != null) return existing;

            removeDefaultWorldFilesAsync(worldName);

            try {
                WorldCreator creator = new WorldCreator(worldName)
                        .environment(environment)
                        .type(WorldType.FLAT)
                        .generator(new VoidChunkGenerator());

                // Use Folia-aware scheduling for world creation
                CompletableFuture<World> worldFuture = new CompletableFuture<>();
                plugin.getThreadSafety().runOnMainThread(() -> {
                    World world = creator.createWorld();
                    if (world != null) {
                        world.setSpawnLocation(0, SPAWN_Y + 2, 0);
                        world.getChunkAtAsync(0, 0);
                    }
                    worldFuture.complete(world);
                });
                return worldFuture.get();

            } catch (Exception e) {
                MessageUtil.log(plugin.getLogger(), Level.SEVERE, "§cFailed to create world: " + worldName, e);
                return null;
            }
        }, runnable -> plugin.getThreadSafety().runAsync(runnable));
    }

    /**
     * Generates a cohesive village-style spawn hub island at (0, SPAWN_Y, 0).
     * Planned layout (not completely random):
     * - Central raised plaza with grand fountain/monument.
     * - Wide paved main roads (cardinal) + square ring road using polished andesite + stone brick accents.
     * - 4-6 small buildings placed deliberately along roads (market stalls, lodges, crate hall, nature cabin).
     * - NPC pads and crate zones integrated as plazas/stalls next to paths and buildings.
     * - One dedicated nature park quadrant with grass/moss tops, clustered trees, small pond, winding dirt trails.
     * - Consistent lamp posts, flower beds, arches, benches along roads for polish.
     * Light randomization only within zones (tree pos, flower types, small variants, extra decor).
     * Foundation + batched row/phase gen preserved for Folia.
     */
    public boolean isHubSpawnReady() {
        return hubSpawnReady;
    }

    public boolean isSpawnPlatformGenerationEnabled() {
        return plugin.getConfig().getBoolean("spawn-platform.enabled", true);
    }

    public boolean shouldBlockJoinUntilHubSpawnReady() {
        return isSpawnPlatformGenerationEnabled()
                && plugin.getConfig().getBoolean("spawn-platform.block-join-until-ready", true);
    }

    public int spawnCenterX() {
        return plugin.getConfig().getInt("spawn-platform.center-x", 0);
    }

    public int spawnCenterY() {
        return plugin.getConfig().getInt("spawn-platform.center-y", SPAWN_Y);
    }

    public int spawnCenterZ() {
        return plugin.getConfig().getInt("spawn-platform.center-z", 0);
    }

    public int spawnPlayerYOffset() {
        return plugin.getConfig().getInt("spawn-platform.player-y-offset", 4);
    }

    /**
     * Standing location for /spawn, first join, and {@link World#setSpawnLocation(Location)}.
     */
    public Location getHubSpawnLocation(World world) {
        if (world == null) {
            return null;
        }
        int cx = spawnCenterX();
        int cy = spawnCenterY();
        int cz = spawnCenterZ();
        float yaw = (float) plugin.getConfig().getDouble("spawn-platform.yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble("spawn-platform.pitch", 0.0);
        return new Location(world, cx + 0.5, cy + spawnPlayerYOffset(), cz + 0.5, yaw, pitch);
    }

    public Location getHubSpawnLocation() {
        return getHubSpawnLocation(resolveSkyblockWorld(World.Environment.NORMAL));
    }

    private void completeHubSpawn(World world) {
        Location spawn = getHubSpawnLocation(world);
        if (spawn == null) {
            return;
        }
        world.setSpawnLocation(spawn);
        hubSpawnReady = true;
        MessageUtil.info(plugin.getLogger(), "§a[WorldManager] Hub spawn ready at "
                + spawn.getBlockX() + ", " + spawn.getBlockY() + ", " + spawn.getBlockZ()
                + " (world: " + world.getName() + ")");

        // Trigger hologram loading/spawning *after* the central spawn platform/hub chunks
        // have been preloaded and built. This fixes "holograms don't generate in the spawn"
        // on Folia (central region tasks for holograms at 0,0 would otherwise race with
        // the batched platform generation and chunk preload).
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().loadAndSpawnAll();
        }
    }

    public void generateSpawnPlatform(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) return;
        if (!isSpawnPlatformGenerationEnabled()) {
            completeHubSpawn(world);
            return;
        }

        hubSpawnReady = false;
        int cx = spawnCenterX();
        int cy = spawnCenterY();
        int cz = spawnCenterZ();

        // Block reads/writes on region thread (Folia)
        Location anchor = new Location(world, cx, cy, cz);
        plugin.getThreadSafety().runAtLocation(anchor, () -> generateSpawnPlatformOnRegionThread(world, cx, cy, cz));
    }

    private void generateSpawnPlatformOnRegionThread(World world, int cx, int cy, int cz) {
        if (plugin.getConfig().getBoolean("spawn-platform.skip-if-built", true) && isSpawnPlatformBuilt(world, cx, cy, cz)) {
            markSpawnPlatformBuilt(world, cx, cy, cz);
            completeHubSpawn(world);
            MessageUtil.info(plugin.getLogger(), "§e[WorldManager] Spawn platform already present — skipping generation.");
            return;
        }

        MessageUtil.info(plugin.getLogger(), "§6[WorldManager] Generating detailed spawn platform/island at 0,0...");
        preloadSpawnChunks(world, cx, cy, cz, spawnRadius(), () -> generateSpawnPlatformBatched(world, cx, cy, cz));
    }

    private int spawnRadius() {
        return Math.max(8, plugin.getConfig().getInt("spawn-platform.radius", 70));
    }

    private long spawnBatchDelayTicks() {
        return Math.max(1L, plugin.getConfig().getLong("spawn-platform.batch-delay-ticks", 1L));
    }

    private long spawnPhaseDelayTicks() {
        return Math.max(1L, plugin.getConfig().getLong("spawn-platform.phase-delay-ticks", 5L));
    }

    private int spawnRowsPerTick() {
        return Math.max(1, plugin.getConfig().getInt("spawn-platform.rows-per-tick", 3));
    }

    private boolean isSpawnPlatformBuilt(World world, int cx, int cy, int cz) {
        return world.getBlockAt(cx, cy + 10, cz).getType() == SPAWN_COMPLETE_MARKER;
    }

    private void markSpawnPlatformBuilt(World world, int cx, int cy, int cz) {
        world.getBlockAt(cx, cy + 10, cz).setType(SPAWN_COMPLETE_MARKER);
    }

    private void preloadSpawnChunks(World world, int blockX, int blockY, int blockZ, int radius, Runnable onReady) {
        int chunkRadius = (radius >> 4) + 2;
        int centerChunkX = blockX >> 4;
        int centerChunkZ = blockZ >> 4;
        Location anchor = new Location(world, blockX, blockY, blockZ);
        List<CompletableFuture<org.bukkit.Chunk>> futures = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                futures.add(world.getChunkAtAsync(centerChunkX + dx, centerChunkZ + dz));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, ex) -> {
                    if (ex != null) {
                        MessageUtil.log(plugin.getLogger(), Level.WARNING, "§e[WorldManager] Spawn chunk preload incomplete; generation may be slower.", ex);
                    }
                    plugin.getThreadSafety().runAtLocation(anchor, onReady);
                });
    }

    private void generateSpawnPlatformBatched(World world, int cx, int cy, int cz) {
        int radius = Math.min(8, spawnRadius());  // basic small platform
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location anchor = new Location(world, cx, cy, cz);

        Runnable finishPhase = () -> finishSpawnPlatform(world, cx, cy, cz, radius, random);

        // Basic: only base platform + finish. No inner ring, no center plaza, no roads/districts, no nature.
        batchBasePlatformRows(world, cx, cy, cz, radius, random, -radius, anchor,
                () -> scheduleSpawnPhase(anchor, finishPhase));
    }

    private void scheduleSpawnPhase(Location anchor, Runnable task) {
        plugin.getThreadSafety().runAtLocationLater(anchor, task, spawnPhaseDelayTicks());
    }

    private void batchBasePlatformRows(World world, int cx, int cy, int cz, int radius, ThreadLocalRandom random,
                                       int nextX, Location anchor, Runnable onComplete) {
        plugin.getThreadSafety().runAtLocation(anchor, () -> {
            int rowsPerTick = spawnRowsPerTick();
            int endX = Math.min(nextX + rowsPerTick - 1, radius);
            for (int x = nextX; x <= endX; x++) {
                for (int z = -radius; z <= radius; z++) {
                    placeBasePlatformColumn(world, cx, cy, cz, x, z, radius, random);
                }
            }
            if (endX < radius) {
                plugin.getThreadSafety().runAtLocationLater(anchor,
                        () -> batchBasePlatformRows(world, cx, cy, cz, radius, random, endX + 1, anchor, onComplete),
                        spawnBatchDelayTicks());
            } else {
                onComplete.run();
            }
        });
    }

    // (Removed complex inner ring for basic spawn)

    // ==================== BUILD METHODS ====================

    private void buildSpawnStructure(World world, int cx, int cy, int cz) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // Basic safe platform - small fixed size to keep players from falling.
        // Admin can build custom spawn on/around it. Large radius config is for legacy; we use small for basic.
        int radius = Math.min(8, spawnRadius());  // small 17x17 platform

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                placeBasePlatformColumn(world, cx, cy, cz, x, z, radius, random);
            }
        }

        finishSpawnPlatform(world, cx, cy, cz, radius, random);
    }

    private void placeBasePlatformColumn(World world, int cx, int cy, int cz, int x, int z, int radius, ThreadLocalRandom random) {
        double dist = Math.sqrt(x * x + z * z);
        if (dist > radius) return;

        // Basic solid platform to prevent falling. Minimal layers.
        int baseY = (dist > radius - 2) ? -2 : -4;
        for (int y = baseY; y <= 0; y++) {
            Block b = world.getBlockAt(cx + x, cy + y, cz + z);
            if (y <= -4) b.setType(Material.DEEPSLATE);
            else if (y == -3) b.setType(Material.COBBLED_DEEPSLATE);
            else if (y <= -1) b.setType(Material.STONE_BRICKS);
            else b.setType(Material.STONE);
        }

        // Uniform safe top - no random, no grass, simple flat safe surface for admin to build on.
        Block top = world.getBlockAt(cx + x, cy + 1, cz + z);
        top.setType(Material.STONE_BRICKS);

        // Minimal outer edge for "island" feel, no full walls.
        if (dist > radius - 1) {
            Block edge = world.getBlockAt(cx + x, cy + 2, cz + z);
            edge.setType(Material.STONE_BRICK_WALL);
        }
    }

    /**
     * Central plaza: raised paved square ~25 radius with surrounding stairs, grand multi-tier fountain
     * in exact center as the iconic hub feature. Roads will connect into this.
     * Fixed cohesive design (no random central variant) for strong visual identity.
     */
    // (buildCentralPlaza removed for basic spawn)

    /**
     * Cohesive road network + districts (the heart of the new non-random design).
     * - Main paved roads (N/S/E/W) wide, bordered, using polished andesite + stone accents.
     * - Square ring road at ~r=26 connecting them.
     * - Then calls builders for buildings placed along roads, integrated NPC/crate plazas,
     *   and the nature park zone (NW quadrant for organic contrast).
     * All features deliberately positioned and connected by roads/trails.
     */
    // (buildRoadsAndDistricts and all related complex builders removed for basic spawn)

    // --- Road builders (cohesive paved vs natural trails) ---

    private void buildPavedRoadSegment(World world, int x1, int y, int z1, int x2, int y2, int z2, int width, boolean vertical, ThreadLocalRandom r) {
        // Simple axis-aligned for cleanliness and strong visual roads (cohesive village feel)
        int halfW = width / 2;
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        if (steps == 0) steps = 1;
        double dx = (x2 - x1) / (double) steps;
        double dz = (z2 - z1) / (double) steps;

        for (int i = 0; i <= steps; i++) {
            int cx = (int) Math.round(x1 + i * dx);
            int cz = (int) Math.round(z1 + i * dz);

            // Road surface (paved) - prominent andesite for clear "main road" feel
            for (int w = -halfW; w <= halfW; w++) {
                int rx = vertical ? cx + w : cx;
                int rz = vertical ? cz : cz + w;
                Material roadMat = (w == 0 && r.nextDouble() < 0.08) ? Material.MOSSY_STONE_BRICKS : Material.POLISHED_ANDESITE;
                world.getBlockAt(rx, y, rz).setType(roadMat);
                // Clear headroom
                for (int h = 1; h <= 3; h++) {
                    Block above = world.getBlockAt(rx, y + h, rz);
                    if (above.getType() != Material.LANTERN && above.getType() != Material.SEA_LANTERN) {
                        above.setType(Material.AIR);
                    }
                }
            }

            // Road borders (stone brick "curbs" on sides for definition, PMC style)
            int b1 = halfW + 1;
            int bx1 = vertical ? cx + b1 : cx;
            int bz1 = vertical ? cz : cz + b1;
            int bx2 = vertical ? cx - b1 : cx;
            int bz2 = vertical ? cz : cz - b1;
            world.getBlockAt(bx1, y, bz1).setType(Material.STONE_BRICKS);
            world.getBlockAt(bx2, y, bz2).setType(Material.STONE_BRICKS);
            // Occasional border accent (lamps/walls for rhythm)
            if (i % 4 == 0) {
                world.getBlockAt(bx1, y + 1, bz1).setType(Material.STONE_BRICK_WALL);
                world.getBlockAt(bx2, y + 1, bz2).setType(Material.STONE_BRICK_WALL);
                // Lamp on curb for fluid lighting along the path
                world.getBlockAt(bx1, y + 2, bz1).setType(Material.LANTERN);
                world.getBlockAt(bx2, y + 2, bz2).setType(Material.LANTERN);
            }

            // Sidewalks outside curbs for more fluid, walkable "street" feel (andesite slabs or blocks)
            int sw = b1 + 1;
            int swx1 = vertical ? cx + sw : cx;
            int swz1 = vertical ? cz : cz + sw;
            int swx2 = vertical ? cx - sw : cx;
            int swz2 = vertical ? cz : cz - sw;
            world.getBlockAt(swx1, y, swz1).setType(Material.POLISHED_ANDESITE);
            world.getBlockAt(swx2, y, swz2).setType(Material.POLISHED_ANDESITE);
            // Occasional sidewalk detail
            if (i % 3 == 0) {
                world.getBlockAt(swx1, y + 1, swz1).setType(Material.STONE_BRICK_SLAB);
                world.getBlockAt(swx2, y + 1, swz2).setType(Material.STONE_BRICK_SLAB);
            }
        }
    }

    private void buildWindingTrail(World world, int sx, int sy, int sz, int ex, int ey, int ez, ThreadLocalRandom r) {
        // Narrow (3 wide) more organic dirt/gravel trail for nature connection. Slight jitter.
        int steps = Math.max(Math.abs(ex - sx), Math.abs(ez - sz)) + 2;
        double dx = (ex - sx) / (double) steps;
        double dz = (ez - sz) / (double) steps;
        int cx = sx, cz = sz;

        for (int i = 0; i <= steps; i++) {
            // Add small random jitter perpendicular for "winding" without going crazy
            int jitter = (r.nextInt(3) - 1);
            int px = (int) Math.round(sx + i * dx) + (Math.abs(dz) > Math.abs(dx) ? jitter : 0);
            int pz = (int) Math.round(sz + i * dz) + (Math.abs(dx) > Math.abs(dz) ? jitter : 0);

            // Trail surface
            for (int w = -1; w <= 1; w++) {
                int tx = (Math.abs(dz) > Math.abs(dx)) ? px + w : px;
                int tz = (Math.abs(dx) > Math.abs(dz)) ? pz + w : pz;
                Material tmat = r.nextDouble() < 0.6 ? Material.DIRT_PATH : Material.GRAVEL;
                world.getBlockAt(tx, sy, tz).setType(tmat);
                world.getBlockAt(tx, sy + 1, tz).setType(Material.AIR);
                world.getBlockAt(tx, sy + 2, tz).setType(Material.AIR);
            }
            // Light borders sometimes
            if (r.nextDouble() < 0.4) {
                world.getBlockAt(px + 2, sy, pz).setType(Material.MOSSY_STONE_BRICKS);
                world.getBlockAt(px - 2, sy, pz).setType(Material.MOSSY_STONE_BRICKS);
            }
        }
    }

    /**
     * Adds controlled vegetation strips along the sides of the main roads.
     * This creates clean "shoulders" of grass/flowers/moss next to the paved roads
     * without sprinkling random dots across the entire stone platform.
     * Makes the roads feel intentional and cohesive (PMC style path borders).
     */
    private void addRoadShoulderVegetation(World world, int cx, int cy, int cz, int ringR, ThreadLocalRandom r) {
        int y = cy;
        // Simple shoulder logic for the 4 main roads and ring (approximate positions)
        // North road shoulder (Z positive) - outside the wider road
        addShoulderStrip(world, cx - 4, y, cz + 10, cx + 4, y, cz + ringR + 5, true, r);
        // South road shoulder
        addShoulderStrip(world, cx - 4, y, cz - ringR - 5, cx + 4, y, cz - 10, true, r);
        // East road shoulder
        addShoulderStrip(world, cx + 10, y, cz - 4, cx + ringR + 5, y, cz + 4, false, r);
        // West road shoulder (shorter for nature)
        addShoulderStrip(world, cx - ringR - 2, y, cz - 4, cx - 10, y, cz + 4, false, r);

        // Ring road shoulders (light vegetation on outer side of the boulevard)
        // North ring outer
        addShoulderStrip(world, cx - ringR - 2, y, cz + ringR + 1, cx + ringR + 2, y, cz + ringR + 3, true, r);
        // Similar for others (light to not overwhelm)
    }

    private void addShoulderStrip(World world, int x1, int y, int z1, int x2, int y2, int z2, boolean vertical, ThreadLocalRandom r) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        if (steps == 0) steps = 1;
        double dx = (x2 - x1) / (double) steps;
        double dz = (z2 - z1) / (double) steps;

        for (int i = 0; i <= steps; i++) {
            int bx = (int) Math.round(x1 + i * dx);
            int bz = (int) Math.round(z1 + i * dz);

            // Place on both "left" and "right" of the road (perpendicular)
            for (int side : new int[]{-4, 4}) {  // offset outside the road width
                int sx = vertical ? bx + side : bx;
                int sz = vertical ? bz : bz + side;
                if (r.nextDouble() < 0.7) {
                    Material mat = r.nextDouble() < 0.5 ? Material.GRASS_BLOCK : Material.MOSS_BLOCK;
                    world.getBlockAt(sx, y, sz).setType(mat);
                    if (r.nextDouble() < 0.6) {
                        world.getBlockAt(sx, y + 1, sz).setType(r.nextBoolean() ? Material.FERN : (r.nextBoolean() ? Material.DANDELION : Material.SHORT_GRASS));
                    }
                }
            }
        }
    }

    private void finishSpawnPlatform(World world, int cx, int cy, int cz, int radius, ThreadLocalRandom random) {
        markSpawnPlatformBuilt(world, cx, cy, cz);
        completeHubSpawn(world);
        MessageUtil.info(plugin.getLogger(), "§a[WorldManager] Basic spawn platform generated (radius: " + radius + ") - safe floor for custom admin spawn");
    }

    // (Old detailed central + npc pad builders removed - replaced by cohesive plaza + roads + deliberate buildings + zoned nature.
    // The new design prioritizes planned roads/buildings/nature integration over random central variants and radial pads.)

    // --- District / Building builders (deliberate, cohesive small structures along the road network) ---

    private void buildMarketStalls(World world, int bx, int by, int bz, ThreadLocalRandom r) {
        // East market: row of 3 small open-front stalls + central counter area. PMC market vibe.
        // Footprint ~11x7
        int stallY = by + 1;
        // Base platform + slight raise
        for (int x = -5; x <= 5; x++) {
            for (int z = -3; z <= 3; z++) {
                Material base = (Math.abs(x) == 5 || Math.abs(z) == 3) ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE;
                world.getBlockAt(bx + x, stallY, bz + z).setType(base);
                world.getBlockAt(bx + x, stallY + 1, bz + z).setType(Material.AIR);
                world.getBlockAt(bx + x, stallY + 2, bz + z).setType(Material.AIR);
            }
        }
        // Stall dividers / counters (3 stalls)
        for (int sx : new int[]{-3, 0, 3}) {
            // Counter
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(bx + sx, stallY + 1, bz + dz).setType(Material.STONE_BRICK_SLAB);
            }
            // Back wall of stall
            for (int h = 1; h <= 2; h++) {
                world.getBlockAt(bx + sx - 1, stallY + h, bz - 2).setType(Material.OAK_LOG);
                world.getBlockAt(bx + sx - 1, stallY + h, bz + 2).setType(Material.OAK_LOG);
            }
            // Roof overhang (stairs)
            for (int dz = -2; dz <= 2; dz++) {
                Block roof = world.getBlockAt(bx + sx - 1, stallY + 3, bz + dz);
                roof.setType(Material.SPRUCE_STAIRS);
                Stairs rs = (Stairs) roof.getBlockData();
                rs.setFacing(BlockFace.EAST);
                rs.setHalf(Bisected.Half.BOTTOM);
                roof.setBlockData(rs);
            }
            // Merch visual: barrel + flower pot or lantern
            world.getBlockAt(bx + sx, stallY + 1, bz).setType(Material.BARREL);
            if (r.nextBoolean()) world.getBlockAt(bx + sx - 2, stallY + 1, bz).setType(Material.FLOWER_POT);
            // Small hanging sign / detail on some stalls
            if (r.nextDouble() < 0.5) {
                world.getBlockAt(bx + sx, stallY + 3, bz + 3).setType(Material.OAK_SIGN);
            }
        }
        // Front "awning" posts + signs of life
        world.getBlockAt(bx + 5, stallY + 1, bz - 2).setType(Material.OAK_FENCE);
        world.getBlockAt(bx + 5, stallY + 2, bz - 2).setType(Material.LANTERN);
        world.getBlockAt(bx + 5, stallY + 1, bz + 2).setType(Material.OAK_FENCE);
        world.getBlockAt(bx + 5, stallY + 2, bz + 2).setType(Material.LANTERN);
        // Lamp posts on sides of market
        buildLampPost(world, bx - 6, stallY, bz - 4);
        buildLampPost(world, bx - 6, stallY, bz + 4);
    }

    private void buildIntegratedCratePlaza(World world, int px, int py, int pz, ThreadLocalRandom r) {
        // Crate area attached to market: 3x3 raised pads with barrels, walls, easy NPC/crate entity space.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(dx*dx + dz*dz);
                if (d > 4.5) continue;
                world.getBlockAt(px + dx, py, pz + dz).setType(Material.POLISHED_ANDESITE);
                if (d > 3.5) {
                    world.getBlockAt(px + dx, py + 1, pz + dz).setType(Material.STONE_BRICK_WALL);
                } else {
                    world.getBlockAt(px + dx, py + 1, pz + dz).setType(Material.AIR);
                }
            }
        }
        // Central barrel cluster (visual + functional for crates)
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            world.getBlockAt(px + dx, py + 1, pz + dz).setType(Material.BARREL);
        }
        // Lamp + decor
        buildLampPost(world, px + 5, py, pz);
        buildLampPost(world, px - 5, py, pz);
        world.getBlockAt(px, py + 2, pz + 4).setType(Material.LECTERN); // "claim crates here" vibe
    }

    private void buildCrateHall(World world, int bx, int by, int bz, ThreadLocalRandom r) {
        // South crate/reward hall: larger 9x7 "warehouse" style open building with lots of barrel space.
        int hY = by + 1;
        // Floor
        for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++) {
            world.getBlockAt(bx + x, hY, bz + z).setType(Material.STONE_BRICKS);
            for (int hh = 1; hh < 4; hh++) world.getBlockAt(bx + x, hY + hh, bz + z).setType(Material.AIR);
        }
        // Back and side walls (partial open front for access)
        for (int x = -4; x <= 4; x++) {
            for (int h = 1; h <= 3; h++) {
                if (Math.abs(x) == 4 || x == -4) {
                    world.getBlockAt(bx + x, hY + h, bz - 2).setType(h % 2 == 0 ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
                }
            }
        }
        // Roof (simple flat with slab edges + lanterns)
        for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++) {
            Material roofM = (Math.abs(x) == 4 || Math.abs(z) == 3) ? Material.STONE_BRICK_SLAB : Material.SPRUCE_SLAB;
            world.getBlockAt(bx + x, hY + 4, bz + z).setType(roofM);
        }
        // Lots of barrels for "rewards/crates"
        for (int x = -3; x <= 3; x += 2) for (int z = -2; z <= 2; z += 2) {
            world.getBlockAt(bx + x, hY + 1, bz + z).setType(Material.BARREL);
            if (r.nextDouble() < 0.5) world.getBlockAt(bx + x, hY + 2, bz + z).setType(Material.BARREL);
        }
        // Entrance posts + lamps
        world.getBlockAt(bx - 3, hY + 1, bz + 4).setType(Material.STONE_BRICK_WALL);
        world.getBlockAt(bx - 3, hY + 2, bz + 4).setType(Material.LANTERN);
        world.getBlockAt(bx + 3, hY + 1, bz + 4).setType(Material.STONE_BRICK_WALL);
        world.getBlockAt(bx + 3, hY + 2, bz + 4).setType(Material.LANTERN);
        buildLampPost(world, bx, hY, bz - 5);
    }

    private void buildSmallLodge(World world, int bx, int by, int bz, boolean isInfo, ThreadLocalRandom r) {
        // Versatile small lodge: 7x9 footprint. isInfo=true -> welcome/info style; false -> nature cabin (more wood/moss).
        int lY = by + 1;
        Material wallMat = isInfo ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
        Material accent = isInfo ? Material.POLISHED_ANDESITE : Material.OAK_LOG;

        // Floor + clear interior
        for (int x = -3; x <= 3; x++) for (int z = -4; z <= 4; z++) {
            world.getBlockAt(bx + x, lY, bz + z).setType(Material.POLISHED_ANDESITE);
            for (int h = 1; h <= 4; h++) {
                world.getBlockAt(bx + x, lY + h, bz + z).setType(Material.AIR);
            }
        }

        // Walls (3 high, with window/door frames) - leave entrance open
        for (int x = -3; x <= 3; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 4) {
                    boolean isCorner = Math.abs(x) == 3 && Math.abs(z) == 4;
                    if (isCorner) {
                        for (int h = 1; h <= 3; h++) world.getBlockAt(bx + x, lY + h, bz + z).setType(wallMat);
                    } else if (Math.abs(z) == 4 && Math.abs(x) <= 1) {
                        // Front entrance arch: leave mostly air (framed by corners + side walls)
                        continue;
                    } else {
                        for (int h = 1; h <= 3; h++) {
                            Material m = (h == 2 && Math.abs(x) % 2 == 0) ? accent : wallMat;
                            world.getBlockAt(bx + x, lY + h, bz + z).setType(m);
                        }
                    }
                }
            }
        }

        // Add nice windows (glass panes) on side walls for detail (PMC buildings have good fenestration)
        for (int wx : new int[]{-2, 2}) {
            for (int wz : new int[]{-2, 0, 2}) {
                if (Math.abs(wz) == 4) continue;
                Block win = world.getBlockAt(bx + wx, lY + 2, bz + wz);
                win.setType(Material.GLASS_PANE);
            }
        }

        // Door frame / entrance (open arch, south facing)
        int doorZ = bz + 4;
        world.getBlockAt(bx - 1, lY + 1, doorZ).setType(Material.AIR);
        world.getBlockAt(bx + 1, lY + 1, doorZ).setType(Material.AIR);
        world.getBlockAt(bx, lY + 2, doorZ).setType(Material.AIR);
        // Simple frame accents on sides of arch
        world.getBlockAt(bx - 1, lY + 1, doorZ - 1).setType(accent);
        world.getBlockAt(bx + 1, lY + 1, doorZ - 1).setType(accent);
        // Simple roof (sloped with stairs on long sides)
        for (int x = -4; x <= 4; x++) for (int z = -5; z <= 5; z++) {
            if (Math.abs(x) > 3 || Math.abs(z) > 4) continue;
            int roofH = 4 + (Math.abs(x) > 2 ? 1 : 0);
            Material roof = (Math.abs(x) == 3 || Math.abs(z) == 4) ? Material.SPRUCE_STAIRS : Material.SPRUCE_SLAB;
            Block rb = world.getBlockAt(bx + x, lY + roofH, bz + z);
            rb.setType(roof);
            if (roof == Material.SPRUCE_STAIRS) {
                Stairs rs = (Stairs) rb.getBlockData();
                rs.setFacing(Math.abs(x) > Math.abs(z) ? (x > 0 ? BlockFace.WEST : BlockFace.EAST) : (z > 0 ? BlockFace.NORTH : BlockFace.SOUTH));
                rs.setHalf(Bisected.Half.BOTTOM);
                rb.setBlockData(rs);
            }
        }
        // Interior details
        world.getBlockAt(bx, lY + 1, bz).setType(isInfo ? Material.LECTERN : Material.CRAFTING_TABLE);
        world.getBlockAt(bx + 2, lY + 1, bz - 2).setType(Material.BARREL);
        world.getBlockAt(bx - 2, lY + 1, bz + 2).setType(Material.FLOWER_POT);
        // Porch / lamps
        world.getBlockAt(bx, lY + 1, bz + 5).setType(Material.STONE_BRICK_SLAB);
        buildLampPost(world, bx - 4, lY, bz);
        buildLampPost(world, bx + 4, lY, bz);
    }

    private void buildNpcStage(World world, int px, int py, int pz, int size, ThreadLocalRandom r) {
        // Reusable raised NPC "stage" / pad, placed deliberately along roads or at plazas.
        // Clear center + front interaction space, borders, lectern, lamps. Fewer total than old design.
        int half = size / 2;
        int sY = py;
        for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > half + 0.5) continue;
            Material m = d > half - 1 ? Material.STONE_BRICK_SLAB : Material.POLISHED_ANDESITE;
            world.getBlockAt(px + x, sY, pz + z).setType(m);
            world.getBlockAt(px + x, sY + 1, pz + z).setType(Material.AIR);
        }
        // Border stairs
        for (int x = -half - 1; x <= half + 1; x++) for (int z = -half - 1; z <= half + 1; z++) {
            if (Math.abs(x) == half + 1 || Math.abs(z) == half + 1) {
                Block st = world.getBlockAt(px + x, sY, pz + z);
                st.setType(Material.STONE_BRICK_STAIRS);
                Stairs sd = (Stairs) st.getBlockData();
                sd.setFacing(Math.abs(x) > Math.abs(z) ? (x > 0 ? BlockFace.WEST : BlockFace.EAST) : (z > 0 ? BlockFace.NORTH : BlockFace.SOUTH));
                sd.setHalf(Bisected.Half.BOTTOM);
                st.setBlockData(sd);
            }
        }
        // NPC base + front clear + lectern
        world.getBlockAt(px, sY + 1, pz).setType(Material.POLISHED_ANDESITE);
        for (int f = 1; f <= 2; f++) for (int dx = -1; dx <= 1; dx++) {
            world.getBlockAt(px + dx, sY + 1, pz - f).setType(Material.AIR);
        }
        world.getBlockAt(px, sY + 2, pz + 2).setType(Material.LECTERN);
        // Corner lamps
        for (int dx : new int[]{-half + 1, half - 1}) for (int dz : new int[]{-half + 1, half - 1}) {
            world.getBlockAt(px + dx, sY + 1, pz + dz).setType(Material.STONE_BRICK_WALL);
            world.getBlockAt(px + dx, sY + 2, pz + dz).setType(Material.LANTERN);
        }
    }

    private void buildLampPost(World world, int x, int y, int z) {
        world.getBlockAt(x, y + 1, z).setType(Material.STONE_BRICK_WALL);
        world.getBlockAt(x, y + 2, z).setType(Material.LANTERN);
        // Extra glow chance
        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            world.getBlockAt(x, y + 3, z).setType(Material.SEA_LANTERN);
        }
    }

    /**
     * Nature park quadrant: larger, better blended organic area like high-quality PMC skyblock parks.
     * Big grass/moss fields, dense tree clusters, nice pond, winding internal paths, flower patches.
     * Much more "real park" than scattered dots.
     */
    private void buildNaturePark(World world, int startX, int baseY, int startZ, int widthX, int widthZ, ThreadLocalRandom r) {
        // Zone bounds
        int minX = startX, maxX = startX + widthX;
        int minZ = startZ, maxZ = startZ + widthZ;

        // Override surface to lush nature. Create bigger contiguous grass/moss areas + some dirt paths inside for cohesion.
        // Add some gentle height variation for more fluid, natural terrain feel (small "hills" and dips).
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dFromCenter = Math.sqrt( (x - (minX + widthX/2.0)) * (x - (minX + widthX/2.0)) + (z - (minZ + widthZ/2.0))*(z - (minZ + widthZ/2.0)) );
                if (dFromCenter > widthX * 0.72) continue; // rounder, larger effective area

                Block top = world.getBlockAt(x, baseY, z);
                double nr = r.nextDouble();

                // Mostly lush grass/moss, with some internal "meadow" variation
                Material nat;
                if (nr < 0.08) nat = Material.DIRT; // occasional bare dirt for paths/interest
                else if (nr < 0.45) nat = Material.GRASS_BLOCK;
                else nat = Material.MOSS_BLOCK;

                top.setType(nat);
                world.getBlockAt(x, baseY - 1, z).setType(Material.DIRT);

                // Gentle terrain variation: raise some spots for hills, lower for dips (fluid organic feel)
                int heightVar = 0;
                if (nr < 0.15) heightVar = 1; // small hill
                else if (nr > 0.85) heightVar = -1; // dip (but keep above base)

                if (heightVar != 0) {
                    Block varBlock = world.getBlockAt(x, baseY + heightVar, z);
                    if (varBlock.getType() == Material.AIR || varBlock.getType() == Material.STONE || varBlock.getType() == Material.STONE_BRICKS) {
                        varBlock.setType(nat);
                    }
                }

                // Clear old stuff
                for (int h = 1; h <= 6; h++) {
                    Block airB = world.getBlockAt(x, baseY + h, z);
                    if (!isNatureFriendly(airB.getType())) airB.setType(Material.AIR);
                }
            }
        }

        // Much denser and better clustered trees (more slots, higher spawn rate)
        int[][] treeSlots = {
            {4,4},{7,9},{11,5},{15,12},{5,18},{19,8},{9,16},{22,3},{2,13},{16,20},
            {25,11},{8,24},{13,2},{20,17},{3,22}
        };
        for (int[] slot : treeSlots) {
            if (r.nextDouble() < 0.82) {
                int tx = minX + slot[0];
                int tz = minZ + slot[1];
                buildSmallTree(world, tx, baseY + 1, tz, r);
                // Add a small bush/fern cluster at base of many trees
                if (r.nextDouble() < 0.6) {
                    world.getBlockAt(tx + 1, baseY + 1, tz).setType(Material.FERN);
                    world.getBlockAt(tx - 1, baseY + 1, tz + 1).setType(r.nextBoolean() ? Material.FERN : Material.SHORT_GRASS);
                }
            }
        }

        // Nicer, slightly larger irregular pond with better banks and a couple of "islands" or reeds
        int pondX = minX + 10 + r.nextInt(4);
        int pondZ = minZ + 9 + r.nextInt(4);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 3; dz++) {
                double pd = Math.sqrt(dx*dx + dz*dz);
                if (pd < 3.2) {
                    world.getBlockAt(pondX + dx, baseY, pondZ + dz).setType(Material.WATER);
                }
            }
        }
        // Mossy natural banks + some reeds (fern or grass in water edge for detail)
        for (int dx = -4; dx <= 4; dx++) for (int dz = -3; dz <= 4; dz++) {
            if (world.getBlockAt(pondX + dx, baseY, pondZ + dz).getType() != Material.WATER) {
                if (r.nextDouble() < 0.6) {
                    world.getBlockAt(pondX + dx, baseY, pondZ + dz).setType(Material.MOSS_BLOCK);
                }
            }
        }
        // Tiny reed-like details
        world.getBlockAt(pondX - 1, baseY + 1, pondZ + 1).setType(Material.FERN);
        world.getBlockAt(pondX + 2, baseY + 1, pondZ).setType(Material.SHORT_GRASS);

        // Larger, grouped flower/fern meadows (PMC style lush patches, not tiny singles)
        for (int b = 0; b < 9; b++) {
            int fx = minX + 3 + r.nextInt(widthX - 6);
            int fz = minZ + 3 + r.nextInt(widthZ - 6);
            for (int i = 0; i < 5 + r.nextInt(4); i++) {
                int ox = fx + r.nextInt(-2, 3);
                int oz = fz + r.nextInt(-2, 3);
                Material fl = r.nextBoolean() ? Material.DANDELION : (r.nextBoolean() ? Material.POPPY : Material.CORNFLOWER);
                world.getBlockAt(ox, baseY + 1, oz).setType(fl);
                if (r.nextDouble() < 0.55) {
                    world.getBlockAt(ox + r.nextInt(-1,2), baseY + 1, oz + r.nextInt(-1,2)).setType(Material.FERN);
                }
            }
        }

        // Add a few internal dirt/grass paths winding inside the park for extra cohesion
        if (r.nextDouble() < 0.7) {
            buildWindingTrail(world, minX + 5, baseY, minZ + 8, minX + 20, baseY, minZ + 18, r);
        }
    }

    private boolean isNatureFriendly(Material m) {
        return m == Material.AIR || m == Material.SHORT_GRASS || m == Material.FERN || m == Material.TALL_GRASS ||
               m == Material.OAK_LEAVES || m == Material.OAK_LOG || m == Material.WATER || m == Material.LANTERN;
    }

    private void buildSmallTree(World world, int tx, int ty, int tz, ThreadLocalRandom r) {
        int height = 3 + r.nextInt(2);
        for (int h = 0; h < height; h++) {
            world.getBlockAt(tx, ty + h, tz).setType(Material.OAK_LOG);
        }
        // Leaf blob
        int leafY = ty + height - 1;
        for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) {
            if (Math.abs(lx) + Math.abs(lz) <= 3) {
                world.getBlockAt(tx + lx, leafY, tz + lz).setType(Material.OAK_LEAVES);
                if (r.nextDouble() < 0.6 && (lx != 0 || lz != 0)) {
                    world.getBlockAt(tx + lx, leafY + 1, tz + lz).setType(Material.OAK_LEAVES);
                }
            }
        }
        // Base grass/fern
        world.getBlockAt(tx, ty - 1, tz).setType(Material.SHORT_GRASS);
        if (r.nextDouble() < 0.5) world.getBlockAt(tx + 1, ty - 1, tz).setType(Material.FERN);
    }

    // Keep old path for any legacy direct calls (will be phased out)
    private void buildEnhancedPath(World world, int startX, int startY, int startZ, int endX, int endZ, ThreadLocalRandom r) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        if (steps == 0) return;

        double dx = (endX - startX) / (double) steps;
        double dz = (endZ - startZ) / (double) steps;

        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(startX + i * dx);
            int z = (int) Math.round(startZ + i * dz);
            // Nicer path with borders and occasional steps
            world.getBlockAt(x, startY, z).setType(Material.DIRT_PATH);
            if (r.nextDouble() < 0.15) {
                world.getBlockAt(x, startY, z).setType(Material.STONE_BRICK_STAIRS);
            }
            // Path borders
            if (i % 2 == 0) {
                world.getBlockAt(x + 1, startY, z).setType(Material.STONE_BRICKS);
                world.getBlockAt(x - 1, startY, z).setType(Material.STONE_BRICKS);
            }
        }
    }

    // (addCohesiveRoadsideDecor and all complex decor removed)

    // ==================== UTILITY METHODS ====================

    private void removeDefaultWorldFilesAsync(String worldName) {
        File folder = new File(Bukkit.getWorldContainer(), worldName);
        if (!folder.exists()) return;

        CompletableFuture.runAsync(() -> {
            try {
                Files.walkFileTree(folder.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete old world folder: " + worldName, e);
            }
        });
    }

}