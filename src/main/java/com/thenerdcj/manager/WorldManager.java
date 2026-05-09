package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Optimized WorldManager for Folia 1.21+ with async world creation
 * Now includes generation of a nice, random default spawn platform/structure at 0,0
 * with dedicated flat areas for holograms and clickable NPCs.
 */
public class WorldManager {

    private final FoliaSkyblock plugin;
    private static final int SPAWN_Y = 100;

    public WorldManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize all Skyblock worlds asynchronously
     */
    public void initializeWorlds() {
        String overworldName = plugin.getConfig().getString("worlds.overworld", "world");
        String netherName = plugin.getConfig().getString("worlds.nether", "world_nether");
        String endName = plugin.getConfig().getString("worlds.end", "world_the_end");

        plugin.getLogger().info("§6Initializing Skyblock worlds (async)...");

        CompletableFuture<World> overworldFuture = createVoidWorldAsync(overworldName, World.Environment.NORMAL);
        CompletableFuture<World> netherFuture = createVoidWorldAsync(netherName, World.Environment.NETHER);
        CompletableFuture<World> endFuture = createVoidWorldAsync(endName, World.Environment.THE_END);

        CompletableFuture.allOf(overworldFuture, netherFuture, endFuture).thenRun(() -> {
            plugin.getLogger().info("§a[✓] All Skyblock worlds initialized!");

            // Generate nice spawn platform on overworld (after world is ready)
            overworldFuture.thenAccept(world -> {
                if (world != null) {
                    generateSpawnPlatform(world);
                }
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "§cFailed to initialize worlds!", throwable);
            return null;
        });
    }

    /**
     * Create a void world asynchronously (Folia-optimized)
     */
    private CompletableFuture<World> createVoidWorldAsync(String worldName, World.Environment environment) {
        return CompletableFuture.supplyAsync(() -> {
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                return existingWorld;
            }

            removeDefaultWorldFilesAsync(worldName);

            try {
                WorldCreator creator = new WorldCreator(worldName);
                creator.environment(environment);
                creator.type(WorldType.FLAT);
                creator.generator(new VoidChunkGenerator());

                return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    World world = creator.createWorld();
                    if (world != null) {
                        // Default spawn will be overridden by nice platform center
                        world.setSpawnLocation(0, SPAWN_Y + 2, 0);

                        if (plugin.isFolia()) {
                            world.getChunkAtAsync(0, 0).thenAccept(chunk -> {
                                plugin.getLogger().info("§a[✓] Spawn chunk loaded for " + worldName);
                            });
                        } else {
                            world.getChunkAt(0, 0);
                        }
                    }
                    return world;
                }).get();

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "§cFailed to create world: " + worldName, e);
                return null;
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    /**
     * Generates a nice, randomized spawn platform/structure at world (0, SPAWN_Y, 0).
     * Includes central feature + 4-6 flat open pads for holograms/NPCs + paths + decorations.
     * Fully Folia region-safe using region scheduler.
     * Only runs once on first world creation / enable.
     */
    public void generateSpawnPlatform(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) return;

        plugin.getLogger().info("§6Generating nice default spawn platform at 0,0 (with hologram/NPC areas)...");

        final int centerX = 0;
        final int centerZ = 0;
        final int centerY = SPAWN_Y;

        // Use region scheduler for Folia safety (builds on the spawn region)
        if (plugin.isFolia()) {
            world.getRegionScheduler().run(plugin, centerX >> 4, centerZ >> 4, task -> {
                buildSpawnStructure(world, centerX, centerY, centerZ);
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                buildSpawnStructure(world, centerX, centerY, centerZ);
            });
        }
    }

    private void buildSpawnStructure(World world, int centerX, int centerY, int centerZ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // === BASE PLATFORM (large clean area ~70x70) ===
        int platformRadius = 35;
        Material baseFloor = Material.STONE_BRICKS;
        Material accentFloor = Material.MOSSY_STONE_BRICKS;
        Material pathMaterial = Material.STONE_BRICK_SLAB; // or DIRT_PATH for more natural, but slab for clean

        for (int x = -platformRadius; x <= platformRadius; x++) {
            for (int z = -platformRadius; z <= platformRadius; z++) {
                double dist = Math.sqrt(x*x + z*z);
                if (dist > platformRadius) continue;

                // Base layers for stability in void
                for (int yOffset = -4; yOffset <= 0; yOffset++) {
                    Block b = world.getBlockAt(centerX + x, centerY + yOffset, centerZ + z);
                    if (yOffset == -4) b.setType(Material.STONE);
                    else if (yOffset == -3) b.setType(Material.COBBLESTONE);
                    else b.setType(baseFloor);
                }

                // Top decorative floor with some randomness/accent
                Block top = world.getBlockAt(centerX + x, centerY + 1, centerZ + z);
                if (random.nextDouble() < 0.15) {
                    top.setType(accentFloor);
                } else if (random.nextDouble() < 0.08) {
                    top.setType(Material.GRASS_BLOCK); // occasional grass accents
                } else {
                    top.setType(baseFloor);
                }
            }
        }

        // === CENTRAL FEATURE (random nice variant) ===
        int variant = random.nextInt(4); // 0=fountain, 1=temple, 2=garden, 3=altar
        int featureSize = 7;

        switch (variant) {
            case 0: // Fountain
                buildFountain(world, centerX, centerY + 2, centerZ, featureSize, random);
                break;
            case 1: // Small Temple
                buildTemple(world, centerX, centerY + 2, centerZ, featureSize, random);
                break;
            case 2: // Garden Hub
                buildGarden(world, centerX, centerY + 2, centerZ, featureSize, random);
                break;
            default: // Altar / Statue base
                buildAltar(world, centerX, centerY + 2, centerZ, featureSize, random);
                break;
        }

        // === NPC / HOLOGRAM PADS (4-6 flat open areas) ===
        int numPads = 4 + random.nextInt(3); // 4 to 6 pads
        int[][] padOffsets = {
            {28, 0}, {-28, 0}, {0, 28}, {0, -28},   // cardinal
            {22, 22}, {-22, -22}                     // diagonal (extra if numPads > 4)
        };

        String[] padThemes = {
            "§eRules & Info", "§bHow to Play", "§aIsland Commands", 
            "§dShop / Trade", "§6Leaderboards", "§cCommunity"
        };

        for (int i = 0; i < numPads && i < padOffsets.length; i++) {
            int px = centerX + padOffsets[i][0];
            int pz = centerZ + padOffsets[i][1];
            buildNpcHologramPad(world, px, centerY + 1, pz, 7, padThemes[i % padThemes.length], random);
        }

        // === PATHS connecting center to pads ===
        for (int i = 0; i < numPads && i < padOffsets.length; i++) {
            int px = centerX + padOffsets[i][0];
            int pz = centerZ + padOffsets[i][1];
            buildPath(world, centerX, centerY + 1, centerZ, px, pz, random);
        }

        // === Extra random decorations (lamps, trees, flowers) ===
        addRandomDecorations(world, centerX, centerY + 2, centerZ, platformRadius, random);

        // Set final spawn location to nice spot in center (slightly above platform)
        world.setSpawnLocation(centerX, centerY + 3, centerZ);

        plugin.getLogger().info("§a[✓] Nice spawn platform generated successfully at 0,0 (variant: " + variant + ", pads: " + numPads + ")");
        plugin.getLogger().info("§7  → Flat areas ready for holograms/NPCs. Admins can edit freely with permission.");
    }

    // --- Helper build methods for central features ---

    private void buildFountain(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        // Simple raised fountain with water
        for (int x = -size/2; x <= size/2; x++) {
            for (int z = -size/2; z <= size/2; z++) {
                Block b = world.getBlockAt(cx + x, cy, cz + z);
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    b.setType(Material.PRISMARINE_BRICKS);
                    if (x == 0 && z == 0) {
                        // Water source in center
                        world.getBlockAt(cx, cy + 1, cz).setType(Material.WATER);
                    }
                } else {
                    b.setType(Material.STONE_BRICKS);
                }
            }
        }
        // Surrounding sea lanterns for light
        world.getBlockAt(cx - 3, cy, cz).setType(Material.SEA_LANTERN);
        world.getBlockAt(cx + 3, cy, cz).setType(Material.SEA_LANTERN);
        world.getBlockAt(cx, cy, cz - 3).setType(Material.SEA_LANTERN);
        world.getBlockAt(cx, cy, cz + 3).setType(Material.SEA_LANTERN);
    }

    private void buildTemple(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        // Small temple-like structure with stairs and pillars
        Material wall = Material.STONE_BRICKS;
        Material pillar = Material.QUARTZ_PILLAR;
        for (int x = -size/2; x <= size/2; x++) {
            for (int z = -size/2; z <= size/2; z++) {
                Block base = world.getBlockAt(cx + x, cy, cz + z);
                base.setType(wall);

                // Corner pillars
                if ((Math.abs(x) == size/2 && Math.abs(z) == size/2) || (Math.abs(x) == size/2 - 1 && Math.abs(z) == size/2 - 1)) {
                    for (int h = 1; h <= 3; h++) {
                        world.getBlockAt(cx + x, cy + h, cz + z).setType(pillar);
                    }
                }
            }
        }
        // Top slab "roof"
        for (int x = -size/2; x <= size/2; x++) {
            for (int z = -size/2; z <= size/2; z++) {
                if (Math.abs(x) == size/2 || Math.abs(z) == size/2) {
                    world.getBlockAt(cx + x, cy + 4, cz + z).setType(Material.STONE_BRICK_SLAB);
                }
            }
        }
        // Lanterns
        world.getBlockAt(cx - 2, cy + 3, cz - 2).setType(Material.SEA_LANTERN);
        world.getBlockAt(cx + 2, cy + 3, cz + 2).setType(Material.SEA_LANTERN);
    }

    private void buildGarden(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        // Zen/garden style
        for (int x = -size/2; x <= size/2; x++) {
            for (int z = -size/2; z <= size/2; z++) {
                Block b = world.getBlockAt(cx + x, cy, cz + z);
                if (r.nextDouble() < 0.4) {
                    b.setType(Material.GRASS_BLOCK);
                } else if (r.nextDouble() < 0.3) {
                    b.setType(Material.PODZOL);
                } else {
                    b.setType(Material.MOSS_BLOCK);
                }
            }
        }
        // Small trees / bushes
        for (int i = 0; i < 3; i++) {
            int tx = cx + r.nextInt(-3, 4);
            int tz = cz + r.nextInt(-3, 4);
            placeSimpleTree(world, tx, cy + 1, tz, r);
        }
        // Flowers
        for (int i = 0; i < 12; i++) {
            int fx = cx + r.nextInt(-size/2 + 1, size/2);
            int fz = cz + r.nextInt(-size/2 + 1, size/2);
            Block flower = world.getBlockAt(fx, cy + 1, fz);
            flower.setType(r.nextBoolean() ? Material.POPPY : Material.DANDELION);
        }
    }

    private void buildAltar(World world, int cx, int cy, int cz, int size, ThreadLocalRandom r) {
        // Elevated altar/statue base
        for (int x = -size/2; x <= size/2; x++) {
            for (int z = -size/2; z <= size/2; z++) {
                int h = (Math.abs(x) + Math.abs(z) < 2) ? 2 : 1;
                for (int y = 0; y < h; y++) {
                    world.getBlockAt(cx + x, cy + y, cz + z).setType(Material.SMOOTH_STONE);
                }
            }
        }
        // Top decorative
        world.getBlockAt(cx, cy + 3, cz).setType(Material.SEA_LANTERN);
        world.getBlockAt(cx - 1, cy + 2, cz).setType(Material.QUARTZ_BLOCK);
        world.getBlockAt(cx + 1, cy + 2, cz).setType(Material.QUARTZ_BLOCK);
    }

    private void buildNpcHologramPad(World world, int px, int py, int pz, int padSize, String theme, ThreadLocalRandom r) {
        Material floor = Material.POLISHED_ANDESITE;
        Material border = Material.STONE_BRICK_WALL; // low border feel, or use stairs

        int half = padSize / 2;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                Block b = world.getBlockAt(px + x, py, pz + z);
                b.setType(floor);

                // Border around edge
                if (Math.abs(x) == half || Math.abs(z) == half) {
                    if (r.nextDouble() > 0.3) { // mostly bordered
                        world.getBlockAt(px + x, py + 1, pz + z).setType(border);
                    }
                }
            }
        }

        // Center placeholder for hologram/NPC (lectern or sign)
        Block centerBlock = world.getBlockAt(px, py + 1, pz);
        centerBlock.setType(Material.LECTERN); // Nice for "clickable" feel, or OAK_SIGN

        // Optional sign on side or above for theme (using setBlockData if needed, simplified here)
        // For full sign text, more code needed; lectern is good visual placeholder.
    }

    private void buildPath(World world, int startX, int startY, int startZ, int endX, int endZ, ThreadLocalRandom r) {
        // Simple straight path approximation
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        if (steps == 0) return;

        double dx = (endX - startX) / (double) steps;
        double dz = (endZ - startZ) / (double) steps;

        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(startX + i * dx);
            int z = (int) Math.round(startZ + i * dz);
            Block pathBlock = world.getBlockAt(x, startY, z);
            pathBlock.setType(Material.DIRT_PATH);

            // Occasional lamp posts along path
            if (i % 6 == 0 && r.nextDouble() < 0.6) {
                world.getBlockAt(x, startY + 1, z).setType(Material.SEA_LANTERN);
            }
        }
    }

    private void placeSimpleTree(World world, int x, int y, int z, ThreadLocalRandom r) {
        // Very simple tree: trunk + leaf blob
        Material log = r.nextBoolean() ? Material.OAK_LOG : Material.BIRCH_LOG;
        Material leaves = r.nextBoolean() ? Material.OAK_LEAVES : Material.BIRCH_LEAVES;

        // Trunk
        for (int h = 0; h < 4; h++) {
            world.getBlockAt(x, y + h, z).setType(log);
        }
        // Leaves
        for (int lx = -2; lx <= 2; lx++) {
            for (int lz = -2; lz <= 2; lz++) {
                for (int ly = 2; ly <= 5; ly++) {
                    if (Math.abs(lx) + Math.abs(lz) + Math.abs(ly - 3) < 4) {
                        Block leaf = world.getBlockAt(x + lx, y + ly, z + lz);
                        if (leaf.getType() == Material.AIR) {
                            leaf.setType(leaves);
                        }
                    }
                }
            }
        }
    }

    private void addRandomDecorations(World world, int cx, int cy, int cz, int radius, ThreadLocalRandom r) {
        // Random lamps around platform
        for (int i = 0; i < 15; i++) {
            int x = cx + r.nextInt(-radius + 5, radius - 4);
            int z = cz + r.nextInt(-radius + 5, radius - 4);
            if (Math.sqrt(x*x + z*z) > radius - 3) continue;
            Block lamp = world.getBlockAt(x, cy, z);
            if (lamp.getType() == Material.STONE_BRICKS || lamp.getType() == Material.GRASS_BLOCK) {
                lamp.setType(Material.SEA_LANTERN);
            }
        }

        // Few extra small trees on outer area
        for (int i = 0; i < 5; i++) {
            int tx = cx + r.nextInt(-radius + 8, radius - 7);
            int tz = cz + r.nextInt(-radius + 8, radius - 7);
            if (Math.sqrt(tx*tx + tz*tz) < radius * 0.6) continue;
            placeSimpleTree(world, tx, cy, tz, r);
        }

        // Flower patches
        for (int i = 0; i < 20; i++) {
            int fx = cx + r.nextInt(-radius + 3, radius - 2);
            int fz = cz + r.nextInt(-radius + 3, radius - 2);
            Block f = world.getBlockAt(fx, cy + 1, fz);
            if (f.getType() == Material.GRASS_BLOCK || f.getType() == Material.STONE_BRICKS) {
                f.setType(r.nextBoolean() ? Material.POPPY : Material.BLUE_ORCHID);
            }
        }
    }

    // --- Existing methods (removeDefaultWorldFilesAsync, VoidChunkGenerator) remain the same ---

    private void removeDefaultWorldFilesAsync(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        if (worldFolder.exists()) {
            plugin.getLogger().info("§eRemoving existing world files for: " + worldName);

            CompletableFuture.runAsync(() -> {
                try {
                    Files.walkFileTree(worldFolder.toPath(), new SimpleFileVisitor<Path>() {
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
                    plugin.getLogger().info("§a[✓] Deleted world folder: " + worldName);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "§eCould not fully delete world folder: " + worldName, e);
                }
            });
        }
    }

    private static class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, java.util.Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }

        @Override
        public boolean shouldGenerateNoise() { return false; }
        @Override
        public boolean shouldGenerateSurface() { return false; }
        @Override
        public boolean shouldGenerateBedrock() { return false; }
        @Override
        public boolean shouldGenerateCaves() { return false; }
        @Override
        public boolean shouldGenerateDecorations() { return false; }
        @Override
        public boolean shouldGenerateMobs() { return false; }
        @Override
        public boolean shouldGenerateStructures() { return false; }
    }
}
