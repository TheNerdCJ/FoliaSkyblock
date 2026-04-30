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
 */
public class IslandGenerator {

    private final FoliaSkyblock plugin;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public IslandGenerator(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    // ==================== MAIN GENERATION METHOD ====================

    /**
     * Generates a complete island at the given location.
     *
     * @param island The island object (contains grid position and owner)
     * @param player The player creating the island
     * @param chosenBiome Optional biome chosen by donor (null = random)
     * @param isDonor Whether the player has donor perks (can choose biome)
     */
    public void generateIsland(Island island, Player player, Biome chosenBiome, boolean isDonor) {
        World world = getWorldForDimension(island.getDimension());
        if (world == null) {
            plugin.getLogger().severe("Could not find world for dimension: " + island.getDimension());
            return;
        }

        Location center = island.getCenter(world);

        // Determine final biome
        Biome finalBiome = determineFinalBiome(chosenBiome, isDonor, island.getDimension());

        // Store the chosen biome in the island object
        island.setBiome(finalBiome.name());

        // Generate the island structure (run on correct region thread for Folia)
        plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
            generateIslandStructure(center, finalBiome, island.getDimension());
            placeStarterChest(center, finalBiome, player);
            setBiomeInChunk(center, finalBiome);

            plugin.getLogger().info("§aGenerated " + finalBiome.name() + " island for " + player.getName());
        });
    }

    // ==================== BIOME SELECTION LOGIC ====================

    private Biome determineFinalBiome(Biome chosenBiome, boolean isDonor, World.Environment dimension) {
        if (isDonor && chosenBiome != null) {
            // Donor gets their chosen biome (if valid for dimension)
            if (isValidBiomeForDimension(chosenBiome, dimension)) {
                return chosenBiome;
            }
        }

        // Normal player or invalid donor choice → random biome
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

    // ==================== ISLAND STRUCTURE GENERATION ====================

    private void generateIslandStructure(Location center, Biome biome, World.Environment dimension) {
        BiomeTemplate template = BiomeTemplate.getTemplate(biome);

        int radius = 8;
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        World world = center.getWorld();

        // Generate base platform (elliptical shape)
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);

                if (distance <= radius) {
                    int y = centerY;

                    Block base = world.getBlockAt(centerX + x, y, centerZ + z);
                    base.setType(template.getBaseBlock());

                    if (distance <= radius - 1) {
                        Block surface = world.getBlockAt(centerX + x, y + 1, centerZ + z);
                        surface.setType(template.getSurfaceBlock());
                    }

                    if (distance <= radius - 2 && random.nextDouble() < 0.3) {
                        Block extra = world.getBlockAt(centerX + x, y + 2, centerZ + z);
                        extra.setType(template.getSurfaceBlock());
                    }
                }
            }
        }

        // Add special dimension features
        if (dimension == World.Environment.NETHER) {
            addNetherFeatures(world, centerX, centerY, centerZ, radius, template);
        } else if (dimension == World.Environment.THE_END) {
            addEndFeatures(world, centerX, centerY, centerZ, radius, template);
        } else {
            addOverworldFeatures(world, centerX, centerY, centerZ, radius, template);
        }

        // Add ores
        addOres(world, centerX, centerY, centerZ, radius, template);

        // Add trees
        if (template.getTreeLog() != null && random.nextDouble() < template.getTreeChance()) {
            addTree(world, centerX + random.nextInt(5) - 2, centerY + 2, centerZ + random.nextInt(5) - 2, template);
        }
    }

    private void addOverworldFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 12; i++) {
            int x = cx + random.nextInt(radius * 2) - radius;
            int z = cz + random.nextInt(radius * 2) - radius;
            Block block = world.getBlockAt(x, cy + 2, z);

            if (block.getType() == template.getSurfaceBlock()) {
                if (random.nextDouble() < 0.6) {
                    block.setType(Material.SHORT_GRASS);
                } else {
                    block.setType(Material.DANDELION);
                }
            }
        }
    }

    private void addNetherFeatures(World world, int cx, int cy, int cz, int radius, BiomeTemplate template) {
        for (int i = 0; i < 8; i++) {
            int x = cx + random.nextInt(radius * 2) - radius;
            int z = cz + random.nextInt(radius * 2) - radius;
            Block block = world.getBlockAt(x, cy + 1, z);

            if (random.nextDouble() < 0.5) {
                block.setType(Material.SOUL_SAND);
            } else {
                block.setType(Material.FIRE);
            }
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
        player.sendMessage("§7For now, your island was generated with a random biome.");
    }
}