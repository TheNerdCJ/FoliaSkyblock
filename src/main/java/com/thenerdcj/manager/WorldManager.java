package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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

    // Consistent world names (matches FoliaSkyblock.getSkyblockWorld())
    private static final String OVERWORLD_NAME = "skyblock";
    private static final String NETHER_NAME = "skyblock_nether";
    private static final String END_NAME = "skyblock_end";

    public WorldManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize all custom Skyblock void worlds
     */
    public void initializeWorlds() {
        plugin.getLogger().info("§6[WorldManager] Initializing custom void worlds...");

        CompletableFuture<World> overworldFuture = createVoidWorldAsync(OVERWORLD_NAME, World.Environment.NORMAL);
        CompletableFuture<World> netherFuture = createVoidWorldAsync(NETHER_NAME, World.Environment.NETHER);
        CompletableFuture<World> endFuture = createVoidWorldAsync(END_NAME, World.Environment.THE_END);

        CompletableFuture.allOf(overworldFuture, netherFuture, endFuture)
                .thenRun(() -> {
                    plugin.getLogger().info("§a[WorldManager] All custom worlds initialized successfully.");

                    // Generate spawn platform after overworld is ready
                    overworldFuture.thenAccept(world -> {
                        if (world != null) {
                            generateSpawnPlatform(world);
                        }
                    });
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "§c[WorldManager] Failed to initialize worlds!", ex);
                    return null;
                });
    }

    /**
     * Returns the main overworld (skyblock world)
     */
    public World getMainWorld() {
        return Bukkit.getWorld(OVERWORLD_NAME);
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
                plugin.getLogger().log(Level.SEVERE, "§cFailed to create world: " + worldName, e);
                return null;
            }
        }, runnable -> plugin.getThreadSafety().runAsync(runnable));
    }

    /**
     * Generates a nice spawn platform at (0, SPAWN_Y, 0)
     */
    public void generateSpawnPlatform(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) return;

        plugin.getLogger().info("§6[WorldManager] Generating spawn platform at 0,0...");

        plugin.getThreadSafety().runAtLocation(new Location(world, 0, SPAWN_Y, 0), () -> {
            buildSpawnStructure(world, 0, SPAWN_Y, 0);
        });
    }

    // ==================== BUILD METHODS ====================

    private void buildSpawnStructure(World world, int cx, int cy, int cz) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = 35;

        // Base platform
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.sqrt(x * x + z * z) > radius) continue;

                for (int y = -4; y <= 0; y++) {
                    Block b = world.getBlockAt(cx + x, cy + y, cz + z);
                    b.setType(y == -4 ? Material.STONE : y == -3 ? Material.COBBLESTONE : Material.STONE_BRICKS);
                }

                Block top = world.getBlockAt(cx + x, cy + 1, cz + z);
                top.setType(random.nextDouble() < 0.15 ? Material.MOSSY_STONE_BRICKS :
                        random.nextDouble() < 0.08 ? Material.GRASS_BLOCK : Material.STONE_BRICKS);
            }
        }

        // Central feature (random)
        int variant = random.nextInt(4);
        switch (variant) {
            case 0 -> buildFountain(world, cx, cy + 2, cz, 7, random);
            case 1 -> buildTemple(world, cx, cy + 2, cz, 7, random);
            case 2 -> buildGarden(world, cx, cy + 2, cz, 7, random);
            default -> buildAltar(world, cx, cy + 2, cz, 7, random);
        }

        // NPC/Hologram pads + paths
        int numPads = 4 + random.nextInt(3);
        int[][] offsets = {{28, 0}, {-28, 0}, {0, 28}, {0, -28}, {22, 22}, {-22, -22}};

        for (int i = 0; i < numPads && i < offsets.length; i++) {
            int px = cx + offsets[i][0];
            int pz = cz + offsets[i][1];
            buildNpcHologramPad(world, px, cy + 1, pz, 7, random);
            buildPath(world, cx, cy + 1, cz, px, pz, random);
        }

        addRandomDecorations(world, cx, cy + 2, cz, radius, random);

        world.setSpawnLocation(cx, cy + 3, cz);
        plugin.getLogger().info("§a[WorldManager] Spawn platform generated (variant: " + variant + ")");
    }

    // --- Feature builders (kept concise) ---

    private void buildFountain(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        // Simple fountain implementation...
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                world.getBlockAt(cx + x, cy, cz + z).setType(Material.STONE_BRICKS);
            }
        }
        world.getBlockAt(cx, cy + 1, cz).setType(Material.WATER);
    }

    private void buildTemple(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                world.getBlockAt(cx + x, cy, cz + z).setType(Material.STONE_BRICKS);
            }
        }
    }

    private void buildGarden(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                world.getBlockAt(cx + x, cy, cz + z).setType(Material.MOSS_BLOCK);
            }
        }
    }

    private void buildAltar(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                world.getBlockAt(cx + x, cy, cz + z).setType(Material.SMOOTH_STONE);
            }
        }
    }

    private void buildNpcHologramPad(World world, int px, int py, int pz, int size, ThreadLocalRandom r) {
        int half = size / 2;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                world.getBlockAt(px + x, py, pz + z).setType(Material.POLISHED_ANDESITE);
            }
        }
        world.getBlockAt(px, py + 1, pz).setType(Material.LECTERN);
    }

    private void buildPath(World world, int startX, int startY, int startZ, int endX, int endZ, ThreadLocalRandom r) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        if (steps == 0) return;

        double dx = (endX - startX) / (double) steps;
        double dz = (endZ - startZ) / (double) steps;

        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(startX + i * dx);
            int z = (int) Math.round(startZ + i * dz);
            world.getBlockAt(x, startY, z).setType(Material.DIRT_PATH);
        }
    }

    private void addRandomDecorations(World world, int cx, int cy, int cz, int radius, ThreadLocalRandom r) {
        for (int i = 0; i < 10; i++) {
            int x = cx + r.nextInt(-radius + 5, radius - 4);
            int z = cz + r.nextInt(-radius + 5, radius - 4);
            if (Math.sqrt(x * x + z * z) < radius - 3) {
                world.getBlockAt(x, cy, z).setType(Material.SEA_LANTERN);
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

    private static class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, java.util.Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }

        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
}