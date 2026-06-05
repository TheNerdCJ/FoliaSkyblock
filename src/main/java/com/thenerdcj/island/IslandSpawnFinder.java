package com.thenerdcj.island;

import com.thenerdcj.island.generator.BiomeTemplate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Finds a natural standing spot on generated island terrain (no artificial spawn pads).
 * Avoids spawning inside blocks or on tree trunks / leaf canopies.
 * <p><b>Folia:</b> all methods read blocks and must run on the region thread that owns {@code center}.
 */
public final class IslandSpawnFinder {

    private static final int DEFAULT_SEARCH_RADIUS = 12;

    private IslandSpawnFinder() {}

    public static Location findNearCenter(Location center) {
        return findNear(center, null, DEFAULT_SEARCH_RADIUS);
    }

    public static Location findNear(Location center, BiomeTemplate template, int searchRadius) {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int hintY = center.getBlockY();

        Location best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 0; r <= searchRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    Integer groundY = findGroundY(world, x, z, hintY, template);
                    if (groundY == null) {
                        continue;
                    }
                    double score = dx * (double) dx + dz * (double) dz;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Location(world, x + 0.5, groundY + 1.0, z + 0.5);
                    }
                }
            }
            if (best != null) {
                break;
            }
        }

        if (best != null) {
            return best;
        }
        return new Location(world, cx + 0.5, hintY + 1.0, cz + 0.5);
    }

    private static Integer findGroundY(World world, int x, int z, int hintY, BiomeTemplate template) {
        int start = Math.min(world.getMaxHeight() - 4, hintY + 16);
        int end = Math.max(world.getMinHeight(), hintY - 10);

        for (int y = start; y >= end; y--) {
            Material ground = world.getBlockAt(x, y, z).getType();
            if (!isValidSpawnGround(ground, template)) {
                continue;
            }
            if (!hasHeadroom(world, x, y, z)) {
                continue;
            }
            if (!hasSolidSupport(world, x, y, z)) {
                continue;
            }
            return y;
        }
        return null;
    }

    private static boolean isValidSpawnGround(Material material, BiomeTemplate template) {
        if (!material.isSolid() || material.isAir()) {
            return false;
        }
        if (isTreeOrUnsafe(material)) {
            return false;
        }
        if (template != null) {
            Material surface = template.getSurfaceBlock();
            Material base = template.getBaseBlock();
            if (material == surface || material == base) {
                return true;
            }
        }
        return material.isOccluding() && !material.name().contains("STAIRS")
                && !material.name().contains("SLAB")
                && material != Material.CACTUS
                && material != Material.MAGMA_BLOCK
                && material != Material.CHEST;
    }

    private static boolean isTreeOrUnsafe(Material material) {
        if (Tag.LOGS.isTagged(material) || Tag.LEAVES.isTagged(material)) {
            return true;
        }
        if (Tag.CLIMBABLE.isTagged(material)) {
            return true;
        }
        String name = material.name();
        return name.contains("MUSHROOM_BLOCK")
                || name.contains("SHROOMLIGHT")
                || material == Material.BAMBOO
                || material == Material.CACTUS
                || material == Material.CHEST
                || material == Material.SPAWNER;
    }

    private static boolean hasHeadroom(World world, int x, int groundY, int z) {
        for (int h = 1; h <= 2; h++) {
            Material above = world.getBlockAt(x, groundY + h, z).getType();
            if (isTreeOrUnsafe(above)) {
                return false;
            }
            if (above.isSolid() && !isPassableVegetation(above)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPassableVegetation(Material material) {
        return Tag.FLOWERS.isTagged(material)
                || Tag.SMALL_FLOWERS.isTagged(material)
                || material == Material.SHORT_GRASS
                || material == Material.TALL_GRASS
                || material == Material.FERN
                || material == Material.LARGE_FERN
                || material == Material.DEAD_BUSH
                || material == Material.SNOW;
    }

    private static boolean hasSolidSupport(World world, int x, int groundY, int z) {
        if (groundY <= world.getMinHeight()) {
            return true;
        }
        Block below = world.getBlockAt(x, groundY - 1, z);
        return below.getType().isSolid() && !isTreeOrUnsafe(below.getType());
    }
}