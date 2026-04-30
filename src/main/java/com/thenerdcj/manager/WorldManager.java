package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Optimized WorldManager for Folia 1.21+ with async world creation
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
        String overworldName = plugin.getConfig().getString("worlds.overworld");
        String netherName = plugin.getConfig().getString("worlds.nether");
        String endName = plugin.getConfig().getString("worlds.end");

        plugin.getLogger().info("§6Initializing Skyblock worlds (async)...");

        // Create worlds asynchronously using CompletableFuture
        CompletableFuture.allOf(
                createVoidWorldAsync(overworldName, World.Environment.NORMAL),
                createVoidWorldAsync(netherName, World.Environment.NETHER),
                createVoidWorldAsync(endName, World.Environment.THE_END)
        ).thenRun(() -> {
            plugin.getLogger().info("§a[✓] All Skyblock worlds initialized!");
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
            // Check if world already exists
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                return existingWorld;
            }

            // Remove default world files if they exist (async file deletion)
            removeDefaultWorldFilesAsync(worldName);

            try {
                WorldCreator creator = new WorldCreator(worldName);
                creator.environment(environment);
                creator.type(WorldType.FLAT);
                creator.generator(new VoidChunkGenerator());

                // Create world on main thread (required by Bukkit)
                return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    World world = creator.createWorld();
                    if (world != null) {
                        world.setSpawnLocation(0, SPAWN_Y, 0);

                        // Folia: Use regionized scheduling for spawn chunk loading
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
     * Remove default world files asynchronously (Folia-optimized)
     */
    private void removeDefaultWorldFilesAsync(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        if (worldFolder.exists()) {
            plugin.getLogger().info("§eRemoving existing world files for: " + worldName);

            // Use async file deletion for better performance
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

    /**
     * Void chunk generator - generates empty chunks (optimized)
     */
    private static class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, java.util.Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateBedrock() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}