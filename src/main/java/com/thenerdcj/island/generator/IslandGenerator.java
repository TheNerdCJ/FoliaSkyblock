package com.thenerdcj.island.generator;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IslandGenerator - Generates custom islands based on biome and dimension.
 *
 * Features:
 * - Biome-specific block palettes (from BiomeTemplate)
 * - Random biome for normal players
 * - Donor biome selection support
 * - Dimension-aware generation (Overworld, Nether, End)
 * - Starter chest with biome-appropriate items
 * - Trees, ores, and special features
 * - Full biome-specific terrain generation (dunes, hills, ponds, etc.)
 */
public class IslandGenerator {

    private final FoliaSkyblock plugin;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public IslandGenerator(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    // ==================== MAIN GENERATION METHOD ====================

    public void generateIsland(Island island, Player player, Biome chosenBiome, boolean isDonor) {
        World world = getWorldForDimension(island.getDimension());
        if (world == null) {
            plugin.getLogger().severe("Could not find world for dimension: " + island.getDimension());
            return;
        }

        Location center = island.getCenter(world);

        Biome finalBiome = determineFinalBiome(chosenBiome, isDonor, island.getDimension());
        island.setBiome(finalBiome.getKey().getKey());

        plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
            generateIslandStructure(center, finalBiome, island.getDimension());
            placeStarterChest(center, finalBiome, player);
            setBiomeInChunk(center, finalBiome);

            plugin.getLogger().info("§aGenerated " + finalBiome.getKey().getKey() + " island for " + player.getName());
        });
    }

    // ==================== BIOME SELECTION LOGIC ====================

    private Biome determineFinalBiome(Biome chosenBiome, boolean isDonor, World.Environment dimension) {
        if (isDonor && chosenBiome != null) {
            if (isValidBiomeForDimension(chosenBiome, dimension)) {
                return chosenBiome;
            }
        }
        return getRandomBiomeForDimension(dimension);
    }

    private boolean isValidBiomeForDimension(Biome biome, World.Environment dimension) {
        return switch (dimension) {
            case NORMAL -> BiomeTemplate.getAllowedOverworldBiomes().contains(biome);
            case NETHER -> BiomeTemplate.getAllowedNetherBiomes().contains(biome);
            case THE_END -> BiomeTemplate.getAllowedEndBiomes().contains(biome);
            default -> false;
        };
    }

    private Biome getRandomBiomeForDimension(World.Environment dimension) {
        List<Biome> allowed = switch (dimension) {
            case NORMAL -> BiomeTemplate.getAllowedOverworldBiomes();
            case NETHER -> BiomeTemplate.getAllowedNetherBiomes();
            case THE_END -> BiomeTemplate.getAllowedEndBiomes();
            default -> List.of(Biome.PLAINS);
        };
        return allowed.get(random.nextInt(allowed.size()));
    }

    // ==================== BIOME-SPECIFIC TERRAIN GENERATION ====================

    private void generateIslandStructure(Location center, Biome biome, World.Environment dimension) {
        BiomeTemplate template = BiomeTemplate.getTemplate(biome);

        int radius = 8;
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        World world = center.getWorld();

        generateBiomeTerrain(world, centerX, centerY, centerZ, radius, template, biome);

        if (dimension == World.Environment.NETHER) {
            addNetherFeatures(world, centerX, centerY, centerZ, radius, template);
        } else if (dimension == World.Environment.THE_END) {
            addEndFeatures(world, centerX, centerY, centerZ, radius, template);
        } else {
            addOverworldFeatures(world, centerX, centerY, centerZ, radius, template, biome);
        }

        addOres(world, centerX, centerY, centerZ, radius, template);

        if (template.getTreeLog() != null && random.nextDouble() < template.getTreeChance()) {
            addTree(world, centerX + random.nextInt(5) - 2, centerY + 2, centerZ + random.nextInt(5) - 2, template);
        }

        addBiomeSpecialFeatures(world, centerX, centerY, centerZ, radius, template, biome);
    }

    private void generateBiomeTerrain(World world, int cx, int cy, int cz, int radius, BiomeTemplate template, Biome biome) {
        String biomeName = biome.getKey().getKey().toUpperCase();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);

                if (distance <= radius) {
                    int baseY = cy;
                    int heightVariation = getBiomeHeightVariation(biomeName, x, z, distance, radius);

                    for (int y = baseY - 2; y <= baseY + heightVariation; y++) {
                        Block block = world.getBlockAt(cx + x, y, cz + z);
                        block.setType(template.getBaseBlock());
                    }

                    Block surface = world.getBlockAt(cx + x, baseY + heightVariation + 1, cz + z);
                    surface.setType(template.getSurfaceBlock());

                    if (heightVariation > 0 && random.nextDouble() < 0.4) {
                        Block extra = world.getBlockAt(cx + x, baseY + heightVariation + 2, cz + z);
                        extra.setType(template.getSurfaceBlock());
                    }
                }
            }
        }
    }

    private int getBiomeHeightVariation(String biomeName, int x, int z, double distance, int radius) {
        return switch (biomeName) {
            case "DESERT" -> (int) Math.max(0, Math.sin(x * 0.5) * Math.cos(z * 0.5) * 2 + (radius - distance) * 0.3);
            case "JUNGLE" -> (int) Math.max(0, (Math.sin(x * 0.3) + Math.cos(z * 0.3)) * 1.5 + random.nextInt(2));
            case "TAIGA" -> random.nextInt(2);
            case "FOREST" -> random.nextInt(3);
            case "NETHER_WASTES" -> random.nextInt(4);
            case "THE_END" -> random.nextInt(2);
            default -> random.nextInt(2);
        };
    }

    // ==================== OVERWORLD FEATURES ====================

    private void addOverworldFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template, Biome biome) {
        String biomeName = biome.getKey().getKey().toUpperCase();

        int featureCount = switch (biomeName) {
            case "JUNGLE" -> 20;
            case "FOREST" -> 15;
            case "PLAINS" -> 12;
            case "TAIGA" -> 10;
            case "DESERT" -> 6;
            default -> 8;
        };

        for (int i = 0; i < featureCount; i++) {
            int x = cx + random.nextInt(radius * 2) - radius;
            int z = cz + random.nextInt(radius * 2) - radius;
            Block block = world.getBlockAt(x, cy + 2, z);

            if (block.getType() == template.getSurfaceBlock()) {
                if (biomeName.equals("DESERT")) {
                    if (random.nextDouble() < 0.3) block.setType(Material.CACTUS);
                } else if (biomeName.equals("JUNGLE") || biomeName.equals("FOREST")) {
                    block.setType(random.nextDouble() < 0.5 ? Material.SHORT_GRASS : Material.FERN);
                } else {
                    block.setType(random.nextDouble() < 0.6 ? Material.SHORT_GRASS : Material.DANDELION);
                }
            }
        }

        if ((biomeName.equals("FOREST") || biomeName.equals("JUNGLE")) && random.nextDouble() < 0.7) {
            addTree(world, cx + random.nextInt(5) - 2, cy + 2, cz + random.nextInt(5) - 2, template);
        }
    }

    // ==================== DIMENSION FEATURES ====================

    private void addNetherFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 8; i++) {
            int x = cx + random.nextInt(radius * 2) - radius;
            int z = cz + random.nextInt(radius * 2) - radius;
            Block block = world.getBlockAt(x, cy + 1, z);
            block.setType(random.nextDouble() < 0.5 ? Material.SOUL_SAND : Material.FIRE);
        }
        for (int i = 0; i < 3; i++) {
            int x = cx + random.nextInt(radius) - radius / 2;
            int z = cz + random.nextInt(radius) - radius / 2;
            world.getBlockAt(x, cy + 4, z).setType(Material.GLOWSTONE);
        }
    }

    private void addEndFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 5; i++) {
            int x = cx + random.nextInt(radius) - radius / 2;
            int z = cz + random.nextInt(radius) - radius / 2;
            world.getBlockAt(x, cy + 1, z).setType(Material.OBSIDIAN);
            world.getBlockAt(x, cy + 2, z).setType(Material.CHORUS_FLOWER);
        }
    }

    // ==================== ORES & TREES ====================

    private void addOres(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        int oreCount = (int) (radius * template.getOreChance() * 2);

        for (int i = 0; i < oreCount; i++) {
            int x = cx + random.nextInt(radius * 2) - radius;
            int y = cy + random.nextInt(3);
            int z = cz + random.nextInt(radius * 2) - radius;

            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == template.getBaseBlock() || block.getType() == template.getSurfaceBlock()) {
                Material ore = template.getAllowedOres().isEmpty()
                        ? template.getOreBlock()
                        : template.getAllowedOres().get(random.nextInt(template.getAllowedOres().size()));
                block.setType(ore);
            }
        }
    }

    private void addTree(World world, int x, int y, int z, BiomeTemplate template) {
        int height = 4 + random.nextInt(3);

        for (int i = 0; i < height; i++) {
            world.getBlockAt(x, y + i, z).setType(template.getTreeLog());
        }

        for (int lx = -2; lx <= 2; lx++) {
            for (int lz = -2; lz <= 2; lz++) {
                for (int ly = height - 2; ly <= height + 1; ly++) {
                    if (Math.abs(lx) == 2 && Math.abs(lz) == 2) continue;
                    world.getBlockAt(x + lx, y + ly, z + lz).setType(template.getTreeLeaves());
                }
            }
        }
    }

    // ==================== BIOME-SPECIFIC SPECIAL FEATURES ====================

    private void addBiomeSpecialFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template, Biome biome) {
        String biomeName = biome.getKey().getKey().toUpperCase();

        switch (biomeName) {
            case "PLAINS" -> addPlainsFeatures(world, cx, cy, cz, radius, template);
            case "DESERT" -> addDesertFeatures(world, cx, cy, cz, radius, template);
            case "JUNGLE" -> addJungleFeatures(world, cx, cy, cz, radius, template);
            case "FOREST" -> addForestFeatures(world, cx, cy, cz, radius, template);
            case "TAIGA" -> addTaigaFeatures(world, cx, cy, cz, radius, template);
            case "NETHER_WASTES" -> addNetherSpecialFeatures(world, cx, cy, cz, radius, template);
            case "THE_END" -> addEndSpecialFeatures(world, cx, cy, cz, radius, template);
        }
    }

    private void addPlainsFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        if (random.nextDouble() < 0.4) {
            int pondX = cx + random.nextInt(4) - 2;
            int pondZ = cz + random.nextInt(4) - 2;
            world.getBlockAt(pondX, cy + 1, pondZ).setType(Material.WATER);
            world.getBlockAt(pondX + 1, cy + 1, pondZ).setType(Material.WATER);
            world.getBlockAt(pondX, cy + 1, pondZ + 1).setType(Material.WATER);
        }
        if (random.nextDouble() < 0.3) {
            for (int i = 0; i < 3; i++) {
                int x = cx + random.nextInt(5) - 2;
                int z = cz + random.nextInt(5) - 2;
                if (world.getBlockAt(x, cy + 2, z).getType() == template.getSurfaceBlock()) {
                    world.getBlockAt(x, cy + 2, z).setType(Material.PUMPKIN);
                }
            }
        }
        if (random.nextDouble() < 0.2) {
            int x = cx + random.nextInt(4) - 2;
            int z = cz + random.nextInt(4) - 2;
            world.getBlockAt(x, cy + 2, z).setType(Material.HAY_BLOCK);
        }
    }

    private void addDesertFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 5; i++) {
            int x = cx + random.nextInt(radius * 2) - radius;
            int z = cz + random.nextInt(radius * 2) - radius;
            if (world.getBlockAt(x, cy + 2, z).getType() == Material.SAND) {
                world.getBlockAt(x, cy + 2, z).setType(Material.DEAD_BUSH);
            }
        }
        if (random.nextDouble() < 0.25) {
            int wellX = cx + random.nextInt(3) - 1;
            int wellZ = cz + random.nextInt(3) - 1;
            world.getBlockAt(wellX, cy + 2, wellZ).setType(Material.COBBLESTONE);
            world.getBlockAt(wellX + 1, cy + 2, wellZ).setType(Material.COBBLESTONE);
            world.getBlockAt(wellX, cy + 2, wellZ + 1).setType(Material.COBBLESTONE);
            world.getBlockAt(wellX + 1, cy + 2, wellZ + 1).setType(Material.COBBLESTONE);
            world.getBlockAt(wellX, cy + 1, wellZ).setType(Material.WATER);
        }
        for (int i = 0; i < 4; i++) {
            int x = cx + random.nextInt(radius) - radius / 2;
            int z = cz + random.nextInt(radius) - radius / 2;
            if (world.getBlockAt(x, cy + 2, z).getType() == Material.SAND) {
                world.getBlockAt(x, cy + 2, z).setType(Material.CACTUS);
                if (random.nextDouble() < 0.3) world.getBlockAt(x, cy + 3, z).setType(Material.CACTUS);
            }
        }
    }

    private void addJungleFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 8; i++) {
            int x = cx + random.nextInt(radius * 2) - radius;
            int z = cz + random.nextInt(radius * 2) - radius;
            Block block = world.getBlockAt(x, cy + 3, z);
            if (block.getType() == template.getTreeLeaves()) {
                world.getBlockAt(x, cy + 2, z).setType(Material.VINE);
            }
        }
        if (random.nextDouble() < 0.4) {
            int x = cx + random.nextInt(4) - 2;
            int z = cz + random.nextInt(4) - 2;
            if (world.getBlockAt(x, cy + 2, z).getType() == template.getTreeLog()) {
                world.getBlockAt(x, cy + 2, z).setType(Material.COCOA);
            }
        }
        if (random.nextDouble() < 0.3) {
            for (int i = 0; i < 3; i++) {
                int x = cx + random.nextInt(4) - 2;
                int z = cz + random.nextInt(4) - 2;
                if (world.getBlockAt(x, cy + 2, z).getType() == template.getSurfaceBlock()) {
                    world.getBlockAt(x, cy + 2, z).setType(Material.MELON);
                }
            }
        }
    }

    private void addForestFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 4; i++) {
            int x = cx + random.nextInt(radius) - radius / 2;
            int z = cz + random.nextInt(radius) - radius / 2;
            if (world.getBlockAt(x, cy + 2, z).getType() == template.getSurfaceBlock()) {
                world.getBlockAt(x, cy + 2, z).setType(random.nextBoolean() ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM);
            }
        }
        for (int i = 0; i < 6; i++) {
            int x = cx + random.nextInt(radius * 2) - radius;
            int z = cz + random.nextInt(radius * 2) - radius;
            if (world.getBlockAt(x, cy + 2, z).getType() == template.getSurfaceBlock()) {
                world.getBlockAt(x, cy + 2, z).setType(Material.POPPY);
            }
        }
    }

    private void addTaigaFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 10; i++) {
            int x = cx + random.nextInt(radius * 2) - radius;
            int z = cz + random.nextInt(radius * 2) - radius;
            if (world.getBlockAt(x, cy + 2, z).getType() == template.getSurfaceBlock()) {
                world.getBlockAt(x, cy + 3, z).setType(Material.SNOW);
            }
        }
        if (random.nextDouble() < 0.3) {
            for (int i = 0; i < 3; i++) {
                int x = cx + random.nextInt(4) - 2;
                int z = cz + random.nextInt(4) - 2;
                if (world.getBlockAt(x, cy + 2, z).getType() == template.getSurfaceBlock()) {
                    world.getBlockAt(x, cy + 2, z).setType(Material.SWEET_BERRY_BUSH);
                }
            }
        }
    }

    private void addNetherSpecialFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 3; i++) {
            int x = cx + random.nextInt(radius) - radius / 2;
            int z = cz + random.nextInt(radius) - radius / 2;
            world.getBlockAt(x, cy + 4, z).setType(Material.GLOWSTONE);
        }
        if (random.nextDouble() < 0.4) {
            int x = cx + random.nextInt(3) - 1;
            int z = cz + random.nextInt(3) - 1;
            world.getBlockAt(x, cy + 2, z).setType(Material.SOUL_SAND);
            world.getBlockAt(x, cy + 3, z).setType(Material.NETHER_WART);
        }
    }

    private void addEndSpecialFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 5; i++) {
            int x = cx + random.nextInt(radius) - radius / 2;
            int z = cz + random.nextInt(radius) - radius / 2;
            world.getBlockAt(x, cy + 2, z).setType(Material.CHORUS_FLOWER);
        }
        if (random.nextDouble() < 0.3) {
            int x = cx + random.nextInt(3) - 1;
            int z = cz + random.nextInt(3) - 1;
            world.getBlockAt(x, cy + 2, z).setType(Material.END_STONE_BRICKS);
        }
    }

    // ==================== STARTER CHEST ====================

    private void placeStarterChest(Location center, Biome biome, Player player) {
        World world = center.getWorld();
        int chestX = center.getBlockX() + 3;
        int chestY = center.getBlockY() + 2;
        int chestZ = center.getBlockZ();

        Block chestBlock = world.getBlockAt(chestX, chestY, chestZ);
        chestBlock.setType(Material.CHEST);

        if (chestBlock.getState() instanceof Chest chest) {
            var inventory = chest.getBlockInventory();

            inventory.addItem(new ItemStack(Material.DIAMOND, 2));
            inventory.addItem(new ItemStack(Material.IRON_INGOT, 8));
            inventory.addItem(new ItemStack(Material.GOLD_INGOT, 4));
            inventory.addItem(new ItemStack(Material.BREAD, 16));
            inventory.addItem(new ItemStack(Material.OAK_SAPLING, 4));

            BiomeTemplate template = BiomeTemplate.getTemplate(biome);
            if (template == BiomeTemplate.DESERT) {
                inventory.addItem(new ItemStack(Material.SAND, 32));
                inventory.addItem(new ItemStack(Material.CACTUS, 8));
            } else if (template == BiomeTemplate.NETHER) {
                inventory.addItem(new ItemStack(Material.NETHERRACK, 16));
                inventory.addItem(new ItemStack(Material.SOUL_SAND, 8));
            } else if (template == BiomeTemplate.END) {
                inventory.addItem(new ItemStack(Material.END_STONE, 16));
                inventory.addItem(new ItemStack(Material.OBSIDIAN, 4));
            } else {
                inventory.addItem(new ItemStack(Material.DIRT, 32));
                inventory.addItem(new ItemStack(Material.BONE_MEAL, 16));
            }

            var book = new ItemStack(Material.WRITTEN_BOOK);
            var meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
            meta.setTitle("§6Welcome to FoliaSkyblock!");
            meta.setAuthor("Server");
            meta.addPage("§aWelcome to your new island!\n\n§7This island was generated with the §e" + template.getDisplayName() + "§7 biome.\n\n§bGood luck and have fun!");
            book.setItemMeta(meta);
            inventory.addItem(book);
        }
    }

    // ==================== HELPER METHODS ====================

    private World getWorldForDimension(World.Environment dimension) {
        String worldName = switch (dimension) {
            case NORMAL -> plugin.getConfig().getString("worlds.overworld", "skyblock");
            case NETHER -> plugin.getConfig().getString("worlds.nether", "skyblock_nether");
            case THE_END -> plugin.getConfig().getString("worlds.end", "skyblock_end");
            default -> "skyblock";
        };
        return Bukkit.getWorld(worldName);
    }

    private void setBiomeInChunk(Location center, Biome biome) {
        World world = center.getWorld();
        int chunkX = center.getBlockX() >> 4;
        int chunkZ = center.getBlockZ() >> 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                world.setBiome(chunkX * 16 + x, center.getBlockY(), chunkZ * 16 + z, biome);
            }
        }
    }

    public void openBiomeSelectionGUI(Player player, Island island) {
        player.sendMessage("§eDonor biome selection GUI coming soon!");
    }
}