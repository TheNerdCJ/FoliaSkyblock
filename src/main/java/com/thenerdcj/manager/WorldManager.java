package com.thenerdcj.manager;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.logging.Level;

public class WorldManager {

    private final FoliaSkyblock plugin;

    public WorldManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize all Skyblock worlds (Overworld, Nether, End)
     * Creates void worlds and removes default world files if they exist
     */
    public void initializeWorlds() {
        // Get world names from config
        String overworldName = plugin.getConfig().getString("worlds.overworld", "skyblock");
        String netherName = plugin.getConfig().getString("worlds.nether", "skyblock_nether");
        String endName = plugin.getConfig().getString("worlds.end", "skyblock_end");

        plugin.getLogger().info("§6Initializing Skyblock worlds...");

        // Create or load Overworld
        World overworld = createVoidWorld(overworldName, World.Environment.NORMAL);
        if (overworld != null) {
            plugin.getLogger().info("§a[✓] Overworld '" + overworldName + "' initialized");
        }

        // Create or load Nether
        World nether = createVoidWorld(netherName, World.Environment.NETHER);
        if (nether != null) {
            plugin.getLogger().info("§a[✓] Nether '" + netherName + "' initialized");
        }

        // Create or load End
        World end = createVoidWorld(endName, World.Environment.THE_END);
        if (end != null) {
            plugin.getLogger().info("§a[✓] End '" + endName + "' initialized");
        }

        plugin.getLogger().info("§a[✓] All Skyblock worlds initialized!");
    }

    /**
     * Create a void world or load if it already exists
     */
    private World createVoidWorld(String worldName, World.Environment environment) {
        // Check if world already exists
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            return existingWorld;
        }

        // Remove default world files if they exist
        removeDefaultWorldFiles(worldName);

        try {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(environment);
            creator.type(WorldType.FLAT);
            creator.generator(new VoidChunkGenerator());

            World world = creator.createWorld();

            if (world != null) {
                // Set spawn location
                world.setSpawnLocation(0, 100, 0);
                return world;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "§cFailed to create world: " + worldName, e);
        }

        return null;
    }

    /**
     * Remove default world files if they exist
     */
    private void removeDefaultWorldFiles(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        if (worldFolder.exists()) {
            plugin.getLogger().info("§eRemoving existing world files for: " + worldName);
            deleteDirectory(worldFolder);
        }
    }

    /**
     * Recursively delete a directory
     */
    private boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            return directory.delete();
        }
        return false;
    }

    /**
     * Void chunk generator - generates empty chunks
     */
    private static class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, java.util.Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}