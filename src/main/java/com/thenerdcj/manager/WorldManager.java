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
 * Generates a nice spawn platform at 0,0 with areas for holograms/NPCs.
 */
public class WorldManager {

    private final FoliaSkyblock plugin;
    private static final int SPAWN_Y = 100;

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

        if (plugin.isFolia()) {
            initializeWorldsOnFolia();
            return;
        }

        CompletableFuture<World> overworldFuture = createVoidWorldAsync(OVERWORLD_NAME, World.Environment.NORMAL);
        CompletableFuture<World> netherFuture = createVoidWorldAsync(NETHER_NAME, World.Environment.NETHER);
        CompletableFuture<World> endFuture = createVoidWorldAsync(END_NAME, World.Environment.THE_END);

        CompletableFuture.allOf(overworldFuture, netherFuture, endFuture)
                .thenRun(() -> {
                    MessageUtil.info(plugin.getLogger(), "§a[WorldManager] All custom worlds initialized successfully.");

                    overworldFuture.thenAccept(world -> {
                        if (world != null) {
                            generateSpawnPlatform(world);
                        }
                    });
                })
                .exceptionally(ex -> {
                    MessageUtil.log(plugin.getLogger(), Level.SEVERE, "§c[WorldManager] Failed to initialize worlds!", ex);
                    return null;
                });
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
                        if (plugin.isFolia()) {
                            world.getChunkAtAsync(0, 0);
                        } else {
                            world.getChunkAt(0, 0);
                        }
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
     * Generates a nice spawn platform at (0, SPAWN_Y, 0).
     * Enhanced for a more detailed "spawn island" hub with complex central structures,
     * tiered platform, better paths, foliage, lighting, and decorative elements.
     * Still fully procedural + random variants for replayability.
     * Runs on RegionScheduler for Folia safety.
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

        if (plugin.isFolia()) {
            // Block reads/writes must run on the owning region thread, not during onEnable on the global server thread.
            Location anchor = new Location(world, cx, cy, cz);
            plugin.getThreadSafety().runAtLocation(anchor, () -> generateSpawnPlatformOnRegionThread(world, cx, cy, cz));
            return;
        }

        if (plugin.getConfig().getBoolean("spawn-platform.skip-if-built", true) && isSpawnPlatformBuilt(world, cx, cy, cz)) {
            markSpawnPlatformBuilt(world, cx, cy, cz);
            completeHubSpawn(world);
            MessageUtil.info(plugin.getLogger(), "§e[WorldManager] Spawn platform already present — skipping generation.");
            return;
        }

        MessageUtil.info(plugin.getLogger(), "§6[WorldManager] Generating detailed spawn platform/island at 0,0...");
        plugin.getThreadSafety().runAtLocation(new Location(world, cx, cy, cz), () -> buildSpawnStructure(world, cx, cy, cz));
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
        return Math.max(8, plugin.getConfig().getInt("spawn-platform.radius", 55));
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
        int radius = spawnRadius();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location anchor = new Location(world, cx, cy, cz);

        Runnable finishPhase = () -> finishSpawnPlatform(world, cx, cy, cz, radius, random);
        Runnable npcPhase = () -> {
            buildSpawnNpcAndCrateAreas(world, cx, cy, cz, radius, random, () -> {});
            scheduleSpawnPhase(anchor, finishPhase);
        };
        Runnable centerPhase = () -> {
            buildSpawnCenterAndPavilions(world, cx, cy, cz, random, () -> {});
            scheduleSpawnPhase(anchor, npcPhase);
        };
        Runnable innerRingPhase = () -> batchInnerRingRows(world, cx, cy, cz, random, -18, anchor,
                () -> scheduleSpawnPhase(anchor, centerPhase));

        batchBasePlatformRows(world, cx, cy, cz, radius, random, -radius, anchor,
                () -> scheduleSpawnPhase(anchor, innerRingPhase));
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

    private void batchInnerRingRows(World world, int cx, int cy, int cz, ThreadLocalRandom random,
                                    int nextX, Location anchor, Runnable onComplete) {
        final int innerRadius = 18;
        plugin.getThreadSafety().runAtLocation(anchor, () -> {
            int rowsPerTick = spawnRowsPerTick();
            int endX = Math.min(nextX + rowsPerTick - 1, innerRadius);
            for (int x = nextX; x <= endX; x++) {
                for (int z = -innerRadius; z <= innerRadius; z++) {
                    placeInnerRingColumn(world, cx, cy, cz, x, z, innerRadius);
                }
            }
            if (endX < innerRadius) {
                plugin.getThreadSafety().runAtLocationLater(anchor,
                        () -> batchInnerRingRows(world, cx, cy, cz, random, endX + 1, anchor, onComplete),
                        spawnBatchDelayTicks());
            } else {
                onComplete.run();
            }
        });
    }

    // ==================== BUILD METHODS ====================

    private void buildSpawnStructure(World world, int cx, int cy, int cz) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = spawnRadius();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                placeBasePlatformColumn(world, cx, cy, cz, x, z, radius, random);
            }
        }

        for (int x = -18; x <= 18; x++) {
            for (int z = -18; z <= 18; z++) {
                placeInnerRingColumn(world, cx, cy, cz, x, z, 18);
            }
        }

        buildSpawnCenterAndPavilions(world, cx, cy, cz, random, () -> {});
        buildSpawnNpcAndCrateAreas(world, cx, cy, cz, radius, random, () -> {});
        finishSpawnPlatform(world, cx, cy, cz, radius, random);
    }

    private void placeBasePlatformColumn(World world, int cx, int cy, int cz, int x, int z, int radius, ThreadLocalRandom random) {
        double dist = Math.sqrt(x * x + z * z);
        if (dist > radius) return;

        int baseY = (dist > radius - 5) ? -5 : -6;
        for (int y = baseY; y <= 0; y++) {
            Block b = world.getBlockAt(cx + x, cy + y, cz + z);
            if (y <= -6) b.setType(Material.DEEPSLATE);
            else if (y == -5) b.setType(Material.COBBLED_DEEPSLATE);
            else if (y <= -3) b.setType(Material.STONE_BRICKS);
            else if (y == -2) b.setType(Material.MOSSY_STONE_BRICKS);
            else b.setType(Material.STONE);
        }

        Block top = world.getBlockAt(cx + x, cy + 1, cz + z);
        double r = random.nextDouble();
        if (dist < 6) {
            top.setType(Material.POLISHED_ANDESITE);
        } else if (r < 0.1) {
            top.setType(Material.MOSS_BLOCK);
        } else if (r < 0.22) {
            top.setType(Material.GRASS_BLOCK);
        } else if (r < 0.32) {
            top.setType(Material.MOSSY_STONE_BRICKS);
        } else if (r < 0.4 && dist > 15) {
            top.setType(Material.TUFF);
        } else {
            top.setType(Material.STONE_BRICKS);
        }

        if (dist > radius - 3 && dist <= radius) {
            Block wallBase = world.getBlockAt(cx + x, cy + 2, cz + z);
            wallBase.setType(Material.STONE_BRICK_WALL);
            if (random.nextDouble() < 0.25) {
                world.getBlockAt(cx + x, cy + 3, cz + z).setType(Material.STONE_BRICKS);
            }
            if (Math.abs(x) % 4 == 0 || Math.abs(z) % 4 == 0) {
                world.getBlockAt(cx + x, cy + 4, cz + z).setType(Material.STONE_BRICK_SLAB);
            }
        }
    }

    private void placeInnerRingColumn(World world, int cx, int cy, int cz, int x, int z, int innerRadius) {
        double dist = Math.sqrt(x * x + z * z);
        if (dist > innerRadius) return;
        Block stepBlock = world.getBlockAt(cx + x, cy + 2, cz + z);
        if (dist > 10) {
            stepBlock.setType(Material.STONE_BRICK_STAIRS);
            Stairs stairs = (Stairs) stepBlock.getBlockData();
            if (Math.abs(x) > Math.abs(z)) {
                stairs.setFacing(x > 0 ? BlockFace.WEST : BlockFace.EAST);
            } else {
                stairs.setFacing(z > 0 ? BlockFace.NORTH : BlockFace.SOUTH);
            }
            stairs.setHalf(Bisected.Half.BOTTOM);
            stepBlock.setBlockData(stairs);
        } else if (dist > 7) {
            stepBlock.setType(Material.STONE_BRICK_SLAB);
        } else {
            stepBlock.setType(Material.POLISHED_ANDESITE);
        }
    }

    private void buildSpawnCenterAndPavilions(World world, int cx, int cy, int cz, ThreadLocalRandom random, Runnable ignored) {
        // Central feature (random, now much more detailed)
        int variant = random.nextInt(4);
        switch (variant) {
            case 0 -> buildDetailedFountain(world, cx, cy + 3, cz, 11, random);
            case 1 -> buildDetailedTemple(world, cx, cy + 3, cz, 11, random);
            case 2 -> buildDetailedGarden(world, cx, cy + 3, cz, 11, random);
            default -> buildDetailedAltar(world, cx, cy + 3, cz, 11, random);
        }

        // Additional small detailed structures around the central area for a richer hub/spawn island feel
        // Inspired by PMC detailed skyblock spawns (small buildings, gazebos, statues, bridges)
        // 4 symmetric small pavilions / info areas
        int[][] smallStructOffsets = {{18, 0}, {-18, 0}, {0, 18}, {0, -18}};
        for (int[] off : smallStructOffsets) {
            int sx = cx + off[0];
            int sz = cz + off[1];
            // Small raised platform with stairs border
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                        Block stairB = world.getBlockAt(sx + dx, cy + 2, sz + dz);
                        stairB.setType(Material.ANDESITE_STAIRS);
                        Stairs s = (Stairs) stairB.getBlockData();
                        if (Math.abs(dx) > Math.abs(dz)) s.setFacing(dx > 0 ? BlockFace.WEST : BlockFace.EAST);
                        else s.setFacing(dz > 0 ? BlockFace.NORTH : BlockFace.SOUTH);
                        s.setHalf(Bisected.Half.BOTTOM);
                        stairB.setBlockData(s);
                    } else {
                        world.getBlockAt(sx + dx, cy + 2, sz + dz).setType(Material.POLISHED_ANDESITE);
                    }
                }
            }
            // Small "building" or gazebo on top: pillars + roof
            world.getBlockAt(sx - 1, cy + 3, sz - 1).setType(Material.STONE_BRICKS); // pillar proxy
            world.getBlockAt(sx + 1, cy + 3, sz - 1).setType(Material.STONE_BRICK_WALL);
            world.getBlockAt(sx - 1, cy + 3, sz + 1).setType(Material.STONE_BRICK_WALL);
            world.getBlockAt(sx + 1, cy + 3, sz + 1).setType(Material.STONE_BRICK_WALL);
            // Roof with slabs/stairs
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block roof = world.getBlockAt(sx + dx, cy + 5, sz + dz);
                    roof.setType(Material.STONE_BRICK_SLAB);
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        roof.setType(Material.STONE_SLAB);
                    }
                }
            }
            // Decor on small struct
            world.getBlockAt(sx, cy + 4, sz).setType(Material.LANTERN);
            if (random.nextDouble() < 0.5) {
                world.getBlockAt(sx, cy + 3, sz).setType(Material.FLOWER_POT);
            }
        }
    }

    private void buildSpawnNpcAndCrateAreas(World world, int cx, int cy, int cz, int radius, ThreadLocalRandom random, Runnable ignored) {
        // === DEDICATED NPC / INTERACTIVE AREAS (many clear pads for automatic NPC spawning) ===
        // Inspired by PMC skyblock hubs (e.g. "15x Places for NPC's", "11 NPC & hologram spots", "places for NPCs, crates, tops, information").
        // Each pad is raised, bordered with stairs/walls/fences for visual definition, flat clear center (for ArmorStand/Villager NPCs with space in front for players),
        // lectern or similar for "interaction", lanterns for lighting. 16+ pads in rings + some special ones.
        // Pads are positioned outside central feature, with paths connecting them. Clear air above for spawning.

        int[][] npcOffsets = {
            {40, 0}, {-40, 0}, {0, 40}, {0, -40},  // cardinal far
            {28, 28}, {-28, 28}, {28, -28}, {-28, -28},  // diagonals
            {40, 20}, {40, -20}, {-40, 20}, {-40, -20}, {20, 40}, {-20, 40}, {20, -40}, {-20, -40}  // more around ring
        };

        for (int[] off : npcOffsets) {
            int px = cx + off[0];
            int pz = cz + off[1];
            buildDetailedNpcPad(world, px, cy + 1, pz, 7, random);  // 7-block pads: enough for NPC + player space
            buildEnhancedPath(world, cx, cy + 1, cz, px, pz, random);
        }

        // Special larger "plaza" NPC areas for important interactive ones (e.g. main shop, crates, leaderboards)
        int[][] specialPlazas = {{0, 50}, {50, 0}, {0, -50}, {-50, 0}};
        for (int[] off : specialPlazas) {
            int px = cx + off[0];
            int pz = cz + off[1];
            buildDetailedNpcPad(world, px, cy + 1, pz, 11, random);  // larger for groups or big NPCs
            buildEnhancedPath(world, cx, cy + 1, cz, px, pz, random);
        }

        // Dedicated crate platforms (skyblock essential - flat, accessible areas for automatic crate entity spawning, with signs/decor)
        int[][] crateOffsets = {{35, 35}, {-35, 35}, {35, -35}, {-35, -35}, {0, 45}, {45, 0}, {0, -45}, {-45, 0}};
        for (int[] off : crateOffsets) {
            int px = cx + off[0];
            int pz = cz + off[1];
            // Simple raised flat crate pad
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    world.getBlockAt(px + dx, cy + 1, pz + dz).setType(Material.POLISHED_ANDESITE);
                    if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                        world.getBlockAt(px + dx, cy + 2, pz + dz).setType(Material.STONE_BRICK_WALL);
                    }
                }
            }
            // Crate "base" blocks in center (clear for entities)
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
                world.getBlockAt(px + i, cy + 2, pz + j).setType(Material.BARREL); // visual for crates
            }
            buildEnhancedPath(world, cx, cy + 1, cz, px, pz, random);
        }
    }

    private void finishSpawnPlatform(World world, int cx, int cy, int cz, int radius, ThreadLocalRandom random) {
        addDetailedDecorations(world, cx, cy + 2, cz, radius, random);
        markSpawnPlatformBuilt(world, cx, cy, cz);
        completeHubSpawn(world);
        MessageUtil.info(plugin.getLogger(), "§a[WorldManager] Detailed spawn island/platform generated (radius: " + radius + ")");
    }

    // --- Detailed feature builders for richer spawn island ---

    private void buildDetailedFountain(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        int half = size / 2;
        // Tiered stone base
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                double d = Math.sqrt(x*x + z*z);
                if (d > half) continue;
                world.getBlockAt(cx + x, cy, cz + z).setType(Material.STONE_BRICKS);
                if (d < half - 1) {
                    world.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.MOSSY_STONE_BRICKS);
                }
            }
        }
        // Central water feature + basin
        world.getBlockAt(cx, cy + 1, cz).setType(Material.WATER);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                world.getBlockAt(cx + i, cy + 1, cz + j).setType(Material.WATER);
            }
        }
        // Surrounding decorative ring + small "waterfalls" (source blocks + slabs)
        for (int x = -half + 2; x <= half - 2; x++) {
            for (int z = -half + 2; z <= half - 2; z++) {
                if (Math.abs(x) == half - 2 || Math.abs(z) == half - 2) {
                    world.getBlockAt(cx + x, cy + 1, cz + z).setType(Material.STONE_BRICK_SLAB);
                }
            }
        }
        // Lantern posts around fountain
        for (int angle = 0; angle < 360; angle += 45) {
            int px = cx + (int)(Math.cos(Math.toRadians(angle)) * (half - 1));
            int pz = cz + (int)(Math.sin(Math.toRadians(angle)) * (half - 1));
            world.getBlockAt(px, cy + 2, pz).setType(Material.STONE_BRICK_WALL);
            world.getBlockAt(px, cy + 3, pz).setType(Material.SEA_LANTERN);
        }
    }

    private void buildDetailedTemple(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        int half = size / 2;
        // Main floor + raised platform
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                world.getBlockAt(cx + x, cy, cz + z).setType(Material.STONE_BRICKS);
                if (Math.abs(x) < half - 1 && Math.abs(z) < half - 1) {
                    world.getBlockAt(cx + x, cy + 1, cz + z).setType(Material.POLISHED_ANDESITE);
                }
            }
        }
        // Pillar corners and walls (more 3D detail)
        for (int x = -half; x <= half; x += 2) {
            for (int z = -half; z <= half; z += 2) {
                if (Math.abs(x) == half || Math.abs(z) == half) {
                    for (int h = 1; h <= 4; h++) {
                        Material mat = (h % 2 == 0) ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
                        world.getBlockAt(cx + x, cy + h, cz + z).setType(mat);
                    }
                }
            }
        }
        // Simple sloped roof using stairs (approximated with blocks + occasional stairs)
        for (int x = -half + 1; x <= half - 1; x++) {
            for (int z = -half + 1; z <= half - 1; z++) {
                int h = 5 + (int)(Math.abs(x) * 0.3) + (int)(Math.abs(z) * 0.3);
                world.getBlockAt(cx + x, cy + h, cz + z).setType(Material.STONE_BRICKS);
                if (r.nextDouble() < 0.6) {
                    Block roofStair = world.getBlockAt(cx + x, cy + h + 1, cz + z);
                    roofStair.setType(Material.STONE_BRICK_STAIRS);
                    Stairs rs = (Stairs) roofStair.getBlockData();
                    if (Math.abs(x) > Math.abs(z)) rs.setFacing(x > 0 ? BlockFace.EAST : BlockFace.WEST);
                    else rs.setFacing(z > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
                    rs.setHalf(Bisected.Half.TOP);
                    roofStair.setBlockData(rs);
                }
            }
        }
        // Inner altar detail
        world.getBlockAt(cx, cy + 2, cz).setType(Material.ENCHANTING_TABLE);
        world.getBlockAt(cx + 1, cy + 2, cz).setType(Material.CANDLE);
    }

    private void buildDetailedGarden(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        int half = size / 2;
        // Mossy natural floor with patches
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                double d = Math.sqrt(x*x + z*z);
                if (d > half) continue;
                Material base = r.nextDouble() < 0.6 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK;
                world.getBlockAt(cx + x, cy, cz + z).setType(base);
                if (r.nextDouble() < 0.25) {
                    world.getBlockAt(cx + x, cy + 1, cz + z).setType(Material.FERN);
                }
            }
        }
        // Small "trees" (logs + leaf clusters) for detail
        for (int i = 0; i < 5; i++) {
            int tx = cx + r.nextInt(-half + 3, half - 2);
            int tz = cz + r.nextInt(-half + 3, half - 2);
            // Trunk
            for (int h = 1; h <= 4; h++) {
                world.getBlockAt(tx, cy + h, tz).setType(Material.OAK_LOG);
            }
            // Leaves
            for (int lx = -2; lx <= 2; lx++) {
                for (int lz = -2; lz <= 2; lz++) {
                    if (Math.abs(lx) + Math.abs(lz) <= 3) {
                        world.getBlockAt(tx + lx, cy + 4, tz + lz).setType(Material.OAK_LEAVES);
                        if (r.nextDouble() < 0.5) {
                            world.getBlockAt(tx + lx, cy + 5, tz + lz).setType(Material.OAK_LEAVES);
                        }
                    }
                }
            }
        }
        // Flower patches + path details
        for (int i = 0; i < 12; i++) {
            int fx = cx + r.nextInt(-half + 2, half - 1);
            int fz = cz + r.nextInt(-half + 2, half - 1);
            world.getBlockAt(fx, cy + 1, fz).setType(r.nextBoolean() ? Material.DANDELION : Material.POPPY);
        }
    }

    private void buildDetailedAltar(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        int half = size / 2;
        // Grand stepped base
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                double d = Math.sqrt(x*x + z*z);
                if (d > half) continue;
                int tier = (int)(d / 3);
                Material mat = (tier % 2 == 0) ? Material.SMOOTH_STONE : Material.POLISHED_BLACKSTONE_BRICKS;
                world.getBlockAt(cx + x, cy + tier, cz + z).setType(mat);
            }
        }
        // Dramatic central pillar + crystal-like top (amethyst)
        for (int h = 0; h < 7; h++) {
            world.getBlockAt(cx, cy + 4 + h, cz).setType(h < 5 ? Material.POLISHED_BLACKSTONE : Material.AMETHYST_BLOCK);
        }
        // Surrounding ritual circle with candles / lanterns
        for (int angle = 0; angle < 360; angle += 30) {
            int px = cx + (int)(Math.cos(Math.toRadians(angle)) * (half - 2));
            int pz = cz + (int)(Math.sin(Math.toRadians(angle)) * (half - 2));
            world.getBlockAt(px, cy + 5, pz).setType(Material.CANDLE);
            if (r.nextDouble() < 0.4) {
                world.getBlockAt(px, cy + 6, pz).setType(Material.SEA_LANTERN);
            }
        }
        // Gold accents for "valuable" altar feel
        world.getBlockAt(cx - 1, cy + 4, cz).setType(Material.GOLD_BLOCK);
        world.getBlockAt(cx + 1, cy + 4, cz).setType(Material.GOLD_BLOCK);
    }

    private void buildDetailedNpcPad(World world, int px, int py, int pz, int size, ThreadLocalRandom r) {
        int half = size / 2;
        // Raised detailed NPC pad: border with stairs + walls for definition (PMC style "stage" for interactive NPCs).
        // Flat clear center (3x3 to 5x5 depending on size) for automatic NPC spawning (e.g. ArmorStand with custom head or Villager).
        // Space in front (south side clear for player interaction). Lectern for "talk" UI or hologram base.
        // Lanterns, pots, fences for polish. Enough pads (16+ regular + 4 large) for various NPCs: shop, crates, info, quests, tops, etc.

        // Base platform, raised
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                double d = Math.sqrt(x*x + z*z);
                if (d > half) continue;
                Material baseMat = (d > half - 2) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_ANDESITE;
                world.getBlockAt(px + x, py, pz + z).setType(baseMat);
                // Slight elevation on edges
                if (d > half - 1.5) {
                    world.getBlockAt(px + x, py + 1, pz + z).setType(Material.STONE_BRICK_SLAB);
                }
            }
        }

        // Stair border around the pad for nice "raised platform" look (detailed)
        for (int x = -half - 1; x <= half + 1; x++) {
            for (int z = -half - 1; z <= half + 1; z++) {
                if (Math.abs(x) == half + 1 || Math.abs(z) == half + 1) {
                    Block stair = world.getBlockAt(px + x, py, pz + z);
                    stair.setType(Material.STONE_BRICK_STAIRS);
                    Stairs s = (Stairs) stair.getBlockData();
                    if (Math.abs(x) > Math.abs(z)) {
                        s.setFacing(x > 0 ? BlockFace.WEST : BlockFace.EAST);
                    } else {
                        s.setFacing(z > 0 ? BlockFace.NORTH : BlockFace.SOUTH);
                    }
                    s.setHalf(Bisected.Half.BOTTOM);
                    stair.setBlockData(s);
                }
            }
        }

        // Clear center for NPC (leave air, place a nice base block)
        int centerClear = Math.max(2, half / 2);
        for (int x = -centerClear; x <= centerClear; x++) {
            for (int z = -centerClear; z <= centerClear; z++) {
                world.getBlockAt(px + x, py + 1, pz + z).setType(Material.AIR); // ensure clear
            }
        }
        // NPC base platform (e.g. for entity to stand on)
        world.getBlockAt(px, py + 1, pz).setType(Material.POLISHED_ANDESITE); // or SMOOTH_STONE

        // "Front" clear space (assume south for interaction)
        for (int z = 1; z <= 3; z++) {
            for (int x = -1; x <= 1; x++) {
                world.getBlockAt(px + x, py + 1, pz - z).setType(Material.AIR);
            }
        }

        // Interactive element: lectern (for NPC "talk" or info)
        world.getBlockAt(px, py + 2, pz + 2).setType(Material.LECTERN);

        // Decor: lanterns on corners, flower pots, fence "railing"
        for (int dx : new int[]{-half+1, half-1}) {
            for (int dz : new int[]{-half+1, half-1}) {
                world.getBlockAt(px + dx, py + 2, pz + dz).setType(Material.STONE_BRICK_WALL);
                world.getBlockAt(px + dx, py + 3, pz + dz).setType(Material.LANTERN);
            }
        }
        world.getBlockAt(px + 2, py + 2, pz).setType(Material.FLOWER_POT);

        // Small fence detail for "enclosed" NPC area feel
        if (size > 6) {
            world.getBlockAt(px + half - 1, py + 2, pz).setType(Material.OAK_FENCE);
            world.getBlockAt(px - half + 1, py + 2, pz).setType(Material.OAK_FENCE);
        }
    }

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

    private void addDetailedDecorations(World world, int cx, int cy, int cz, int radius, ThreadLocalRandom r) {
        // Rich PMC-inspired decor: lanterns, flowers, small ruins, arches, lots of foliage, small trees, water accents.
        // Creates a lively, detailed spawn island hub feel with many "photo spots" and NPC-adjacent areas.
        for (int i = 0; i < 40; i++) {
            int x = cx + r.nextInt(-radius + 8, radius - 7);
            int z = cz + r.nextInt(-radius + 8, radius - 7);
            double dist = Math.sqrt(x * x + z * z);
            if (dist < radius - 5 && dist > 8) {
                double roll = r.nextDouble();
                if (roll < 0.25) {
                    world.getBlockAt(x, cy + 1, z).setType(Material.SEA_LANTERN);
                    if (r.nextDouble() < 0.5) world.getBlockAt(x, cy + 2, z).setType(Material.LANTERN);
                } else if (roll < 0.45) {
                    world.getBlockAt(x, cy + 1, z).setType(r.nextBoolean() ? Material.DANDELION : Material.CORNFLOWER);
                    if (r.nextDouble() < 0.3) world.getBlockAt(x, cy + 2, z).setType(Material.SHORT_GRASS);
                } else if (roll < 0.6) {
                    // Small pillar/ruin/statue
                    world.getBlockAt(x, cy + 1, z).setType(Material.STONE_BRICKS);
                    world.getBlockAt(x, cy + 2, z).setType(Material.STONE_BRICK_WALL);
                    if (r.nextDouble() < 0.4) world.getBlockAt(x, cy + 3, z).setType(Material.STONE_BRICK_SLAB);
                } else if (roll < 0.75) {
                    // Grass/fern + occasional flower
                    world.getBlockAt(x, cy + 1, z).setType(Material.SHORT_GRASS);
                    if (r.nextDouble() < 0.5) world.getBlockAt(x, cy + 2, z).setType(Material.FERN);
                    if (r.nextDouble() < 0.2) world.getBlockAt(x, cy + 2, z).setType(Material.POPPY);
                } else {
                    // Small arch or detail
                    world.getBlockAt(x, cy + 1, z).setType(Material.STONE_BRICK_WALL);
                    world.getBlockAt(x + 1, cy + 1, z).setType(Material.STONE_BRICKS);
                    world.getBlockAt(x - 1, cy + 1, z).setType(Material.STONE_BRICKS);
                }
            }
        }
        // More small tree clusters and bushes (PMC vegetation)
        for (int i = 0; i < 8; i++) {
            int tx = cx + r.nextInt(-radius + 10, radius - 9);
            int tz = cz + r.nextInt(-radius + 10, radius - 9);
            if (Math.sqrt(tx*tx + tz*tz) > 12) {
                // Trunk + leaves
                for (int h = 1; h <= 3 + r.nextInt(2); h++) {
                    world.getBlockAt(tx, cy + h, tz).setType(Material.OAK_LOG);
                }
                for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) {
                    if (Math.abs(lx) + Math.abs(lz) <= 3) {
                        world.getBlockAt(tx + lx, cy + 4, tz + lz).setType(Material.OAK_LEAVES);
                        if (r.nextDouble() < 0.6) world.getBlockAt(tx + lx, cy + 5, tz + lz).setType(Material.OAK_LEAVES);
                    }
                }
            }
        }
        // Occasional small water features or ponds for interest
        if (r.nextDouble() < 0.7) {
            int wx = cx + r.nextInt(-radius + 15, radius - 14);
            int wz = cz + r.nextInt(-radius + 15, radius - 14);
            if (Math.sqrt(wx*wx + wz*wz) > 15) {
                world.getBlockAt(wx, cy + 1, wz).setType(Material.WATER);
                world.getBlockAt(wx + 1, cy + 1, wz).setType(Material.WATER);
                world.getBlockAt(wx, cy + 1, wz + 1).setType(Material.WATER);
            }
        }
    }

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