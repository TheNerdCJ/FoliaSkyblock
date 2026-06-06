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

    private static final int DEFAULT_SEARCH_RADIUS = 6; // shrunk for classic small starter islands; still allows unique nearby spawns via terrain variation

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

    /**
     * Find a safe spawn location for a minion (or similar small entity) at a specific offset from island center.
     * Uses the terrain-aware ground finder to avoid spawning inside blocks, trees, or on invalid surfaces.
     * Falls back to the naive offset +1.2 if no safe ground found at/near the spot.
     * Must be called on the correct Folia region thread for the location.
     */
    public static Location findSafeMinionLocation(Location center, int xOffset, int zOffset, BiomeTemplate template) {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        World world = center.getWorld();
        int x = center.getBlockX() + xOffset;
        int z = center.getBlockZ() + zOffset;
        int hintY = center.getBlockY();

        Integer groundY = findGroundY(world, x, z, hintY, template);
        if (groundY != null) {
            // +1.2 to match historical minion floating height for ArmorStand visuals
            return new Location(world, x + 0.5, groundY + 1.2, z + 0.5);
        }

        // Fallback (rare): use the old blind placement so at least something spawns
        return center.clone().add(xOffset, 1.2, zOffset);
    }

    /**
     * Find a safe minion spawn location near where the player is looking.
     * Prefers the exact x/z of the target block, then searches a small radius for safe ground.
     * This allows minions to be placed at the block the player is targeting, without ending up inside terrain.
     * Must be called on the correct Folia region thread.
     */
    public static Location findSafeMinionLocationNearTarget(Location target, BiomeTemplate template) {
        if (target == null || target.getWorld() == null) {
            return null;
        }
        World world = target.getWorld();
        int tx = target.getBlockX();
        int tz = target.getBlockZ();
        int hintY = target.getBlockY();

        // Try the exact target column first
        Integer groundY = findGroundY(world, tx, tz, hintY, template);
        if (groundY != null) {
            return new Location(world, tx + 0.5, groundY + 1.2, tz + 0.5);
        }

        // Small search around the target (up to 2 blocks away) for a safe spot
        for (int r = 1; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) < r && Math.abs(dz) < r) continue; // skip inner ring already checked
                    Integer y = findGroundY(world, tx + dx, tz + dz, hintY, template);
                    if (y != null) {
                        return new Location(world, tx + dx + 0.5, y + 1.2, tz + dz + 0.5);
                    }
                }
            }
        }

        // Fallback: just above the target block
        return target.clone().add(0, 1.2, 0);
    }

    // Expose for reuse in minion placement (and potentially furniture etc.)
    public static Integer findGroundY(World world, int x, int z, int hintY, BiomeTemplate template) {
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
}