package com.thenerdcj.island.generator;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandSpawnFinder;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
/**
 * IslandGenerator - Deeply procedural, per-island unique generation that stays 100% faithful to the chosen biome.
 * <p>
 * Design goals (no schematics):
 * - Every island of the same biome still feels distinct ("unique generation").
 * - Strong use of seeded deterministic randomness so the same island always regenerates identically (important for resets).
 * - Multiple "Archetypes" per biome group that dramatically change terrain shape, feature composition, and vibe
 *   while respecting biome block palettes and overall resource density (Play-to-Win safe).
 * - Richer procedural features: varied terrain profiles (plateaus, valleys, crags, scattered islets), better tree
 *   variety (multiple canopy shapes + fallen logs), coherent micro-landmarks (ruins, arches, sinkholes, meadows),
 *   more interesting water/lava bodies.
 * - All block work done via Folia RegionScheduler.
 * - Existing BiomeTemplate densities + allowed blocks remain the "budget" — archetypes only rearrange how the budget is spent.
 * <p>
 * This version significantly deepens variety compared to previous iterations without introducing external files or schematics.
 */
public class IslandGenerator {

    private final FoliaSkyblock plugin;

    public IslandGenerator(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    // ==================== MAIN ENTRY (signature preserved for compatibility) ====================

    public void generateIsland(Island island, Player player, Biome chosenBiome, boolean isDonor) {
        World world = getWorldForDimension(island.getDimension());

        if (world == null) {
            plugin.getLogger().severe("Could not find world for dimension: " + island.getDimension());
            return;
        }

        Location center = island.getCenter(world);
        BiomeTemplate template = BiomeTemplate.getTemplate(chosenBiome);
        Biome finalBiome = determineFinalBiome(chosenBiome, isDonor, island.getDimension(), template);

        // Use island's persisted generationSeed (if donor rerolled it) for unique personality
        long extraSeed = island.getGenerationSeed();
        long seed = computeIslandSeed(center, island.getDimension(), finalBiome, extraSeed);

        // Task batch: size upgrade also affects gen radius (expands terrain spread on reset/creation after upgrades; protection already uses it).
        int sizeLevel = 0;
        if (plugin.getIslandUpgradeManager() != null) {
            try {
                sizeLevel = plugin.getIslandUpgradeManager().getUpgradeLevel(island.getId(), com.thenerdcj.island.IslandUpgrade.ISLAND_SIZE);
            } catch (Exception ignored) {}
        }
        final int sizeLevelFinal = sizeLevel;

        plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
            generateIslandStructure(center, finalBiome, island.getDimension(), template, seed, sizeLevelFinal);
            Location spawn = IslandSpawnFinder.findNear(center, template, 12);
            island.setSpawnLocation(spawn);
            if (player != null && player.isOnline()) {
                // Snap to safe pad after terrain (no chat — create already warped the player)
                plugin.getIslandManager().teleportPlayerToIslandSpawn(player, island, spawn, false);
            }
            placeStarterChest(center, finalBiome, player, seed);
            setBiomeInChunk(center, finalBiome);
        });
    }

    private World getWorldForDimension(World.Environment dimension) {
        if (plugin.getWorldManager() != null) {
            return plugin.getWorldManager().resolveSkyblockWorld(dimension);
        }
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == dimension)
                .findFirst()
                .orElse(null);
    }

    private Biome determineFinalBiome(Biome chosenBiome, boolean isDonor, World.Environment dimension, BiomeTemplate template) {
        if (isDonor && chosenBiome != null && isValidBiomeForDimension(chosenBiome, dimension)) {
            return chosenBiome;
        }
        // Random from allowed for this dimension (non-donors or invalid)
        List<Biome> allowed = switch (dimension) {
            case NORMAL -> BiomeTemplate.getAllowedOverworldBiomes();
            case NETHER -> BiomeTemplate.getAllowedNetherBiomes();
            case THE_END -> BiomeTemplate.getAllowedEndBiomes();
            default -> List.of(Biome.PLAINS);
        };
        return allowed.get(new Random(computeIslandSeed(null, dimension, null)).nextInt(allowed.size()));
    }

    private boolean isValidBiomeForDimension(Biome biome, World.Environment dimension) {
        return switch (dimension) {
            case NORMAL -> BiomeTemplate.getAllowedOverworldBiomes().contains(biome);
            case NETHER -> BiomeTemplate.getAllowedNetherBiomes().contains(biome);
            case THE_END -> BiomeTemplate.getAllowedEndBiomes().contains(biome);
            default -> false;
        };
    }

    private long computeIslandSeed(Location center, World.Environment dimension, Biome biome) {
        return computeIslandSeed(center, dimension, biome, 0L);
    }

    /**
     * Computes the final deterministic seed for generation.
     * Combines position + dimension + biome with an optional extra generationSeed (for donor rerolls).
     * When generationSeed != 0, it introduces new variety while remaining fully reproducible.
     */
    private long computeIslandSeed(Location center, World.Environment dimension, Biome biome, long extraGenerationSeed) {
        long base = (center != null ? (center.getBlockX() * 341873128712L + center.getBlockZ() * 132897987541L) : 0L);
        base ^= dimension.ordinal() * 9876543210L;
        if (biome != null) base ^= biome.getKey().getKey().hashCode() * 31L;
        if (extraGenerationSeed != 0) {
            base ^= extraGenerationSeed;
        }
        return base;
    }

    // ==================== PER-ISLAND UNIQUE GENERATION ARCHETYPES (no schematics) ====================
    // Deepened in this iteration: 7+ distinct terrain languages + richer trees + coherent micro-landmarks.
    // Every island of the same biome now has a strong "personality" while remaining fully biome-faithful
    // and Play-to-Win balanced (resource budgets controlled by BiomeTemplate).

    /**
     * Archetypes give each island of the same biome a distinct personality and terrain language.
     * Chosen deterministically from the island seed so regeneration is stable.
     * Resource "budget" (tree count, ore count, veg density) is still governed by BiomeTemplate.
     */
    public enum IslandArchetype {
        ROLLING_HILLS,      // Gentle to moderate slopes, classic skyblock feel with soft waves
        PLATEAUS,           // Flat tops + occasional steep drops / cliffs
        VALLEYS_RIVERS,     // More carving, ponds connected by shallow "river" paths
        CRAGGY_OUTCROPS,    // Rocky, exposed stone, more verticality and rock formations
        SCATTERED_ISLETS,   // Lower average height, more small "islands" and water within the radius
        DENSE_CANOPY,       // (Forest/Jungle bias) thicker tree placement + undergrowth feel
        BARREN_WASTES,      // (Desert/Nether bias) more rock, less surface vegetation, dramatic
        PILLARS_SPIRES      // (Nether/End bias) more vertical elements
    }

    private static class GenerationProfile {
        final IslandArchetype archetype;
        final double terrainScale;      // multiplier on height noise
        final double featureBias;       // 0..1 — higher = more "special" features vs scattered veg
        final double verticality;       // how much cliffs / plateaus vs rolling
        final double waterBias;         // higher = more ponds/rivers/carving

        GenerationProfile(IslandArchetype archetype, double terrainScale, double featureBias,
                          double verticality, double waterBias) {
            this.archetype = archetype;
            this.terrainScale = terrainScale;
            this.featureBias = featureBias;
            this.verticality = verticality;
            this.waterBias = waterBias;
        }
    }

    /**
     * Computes a rich per-island profile from the stable seed.
     * This is the main lever for making every island feel unique while staying biome-true.
     */
    private GenerationProfile computeGenerationProfile(long seed, BiomeTemplate template, Biome biome, World.Environment dimension) {
        Random rand = new Random(seed ^ 0x9E3779B97F4A7C15L); // extra mixing for archetype choice

        String bname = biome.getKey().getKey().toUpperCase();

        List<IslandArchetype> candidates = new ArrayList<>();

        // Biome-group aware candidate pools (keeps everything feeling "right" for the biome)
        if (bname.contains("DESERT")) {
            candidates.addAll(List.of(IslandArchetype.ROLLING_HILLS, IslandArchetype.PLATEAUS, IslandArchetype.CRAGGY_OUTCROPS, IslandArchetype.BARREN_WASTES));
        } else if (bname.contains("JUNGLE") || bname.contains("FOREST")) {
            candidates.addAll(List.of(IslandArchetype.ROLLING_HILLS, IslandArchetype.VALLEYS_RIVERS, IslandArchetype.DENSE_CANOPY, IslandArchetype.CRAGGY_OUTCROPS));
        } else if (bname.contains("NETHER")) {
            candidates.addAll(List.of(IslandArchetype.CRAGGY_OUTCROPS, IslandArchetype.BARREN_WASTES, IslandArchetype.PILLARS_SPIRES, IslandArchetype.VALLEYS_RIVERS));
        } else if (bname.contains("END")) {
            candidates.addAll(List.of(IslandArchetype.PLATEAUS, IslandArchetype.PILLARS_SPIRES, IslandArchetype.SCATTERED_ISLETS, IslandArchetype.CRAGGY_OUTCROPS));
        } else {
            // Plains / Taiga / generic
            candidates.addAll(List.of(IslandArchetype.ROLLING_HILLS, IslandArchetype.PLATEAUS, IslandArchetype.VALLEYS_RIVERS, IslandArchetype.SCATTERED_ISLETS, IslandArchetype.CRAGGY_OUTCROPS));
        }

        IslandArchetype chosen = candidates.get(rand.nextInt(candidates.size()));

        // Archetype-specific intensity knobs (these create the "personality")
        double terrainScale = 1.0 + (rand.nextDouble() - 0.5) * 0.6;
        double featureBias = 0.4 + rand.nextDouble() * 0.5;
        double verticality = 0.3 + rand.nextDouble() * 0.7;
        double waterBias = 0.3 + rand.nextDouble() * 0.6;

        switch (chosen) {
            case PLATEAUS -> { verticality = Math.max(verticality, 0.75); terrainScale *= 0.85; }
            case VALLEYS_RIVERS -> { waterBias = Math.max(waterBias, 0.75); terrainScale *= 1.15; }
            case CRAGGY_OUTCROPS -> { verticality = Math.max(verticality, 0.8); featureBias += 0.15; }
            case SCATTERED_ISLETS -> { waterBias = Math.max(waterBias, 0.8); terrainScale *= 0.7; }
            case DENSE_CANOPY -> { featureBias += 0.2; terrainScale *= 0.9; }
            case BARREN_WASTES -> { waterBias *= 0.6; featureBias *= 0.85; }
            case PILLARS_SPIRES -> { verticality = Math.max(verticality, 0.9); }
            default -> {}
        }

        // Clamp for sanity
        terrainScale = Math.max(0.6, Math.min(2.2, terrainScale));
        featureBias = Math.max(0.2, Math.min(0.95, featureBias));
        verticality = Math.max(0.2, Math.min(0.95, verticality));
        waterBias = Math.max(0.15, Math.min(0.95, waterBias));

        return new GenerationProfile(chosen, terrainScale, featureBias, verticality, waterBias);
    }

    private int getRandomizedRadius(BiomeTemplate template, long seed) {
        Random rand = new Random(seed);
        return rand.nextInt(template.getMaxRadius() - template.getMinRadius() + 1) + template.getMinRadius();
    }

    // ==================== CORE STRUCTURE GENERATION (expanded) ====================

    private void generateIslandStructure(Location center, Biome biome, World.Environment dimension,
                                         BiomeTemplate template, long seed, int sizeLevel) {
        Random rand = new Random(seed); // seeded for reproducibility + uniqueness per position

        int radius = getRandomizedRadius(template, seed);
        // Task batch: size upgrade also affects gen radius (expands terrain for upgraded islands on reset/gen)
        if (sizeLevel > 0) {
            radius = (int) Math.min(radius * (1.0 + sizeLevel * 0.10), 400);
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        World world = center.getWorld();

        // Compute rich per-island profile — this is the primary source of "every island feels unique"
        GenerationProfile profile = computeGenerationProfile(seed, template, biome, dimension);

        // 1. Archetype-driven terrain (the biggest lever for uniqueness)
        generateArchetypeTerrain(world, cx, cy, cz, radius, template, biome, rand, profile);

        // 2. Add randomized features (now also modulated by profile)
        addRandomizedFeatures(world, cx, cy, cz, radius, template, biome, dimension, rand, profile);

        // 3. Ores with slight density variation but balanced expected value
        addBalancedOreVeins(world, cx, cy, cz, radius, template, rand);

        // 4. Trees (variable count, min distance, biome specific) — enhanced variety
        addVariableTrees(world, cx, cy, cz, radius, template, rand, profile);

        // 5. Biome-specific special features + new coherent micro-landmarks
        addBiomeSpecialFeaturesEnhanced(world, cx, cy, cz, radius, template, biome, dimension, rand, profile);
    }

    /**
     * The heart of unique-per-island generation.
     * Dramatically different terrain languages based on the chosen archetype while staying within
     * the biome's block palette and overall "mass" expectations.
     */
    private void generateArchetypeTerrain(World world, int cx, int cy, int cz, int radius,
                                          BiomeTemplate template, Biome biome, Random rand,
                                          GenerationProfile profile) {
        String biomeName = biome.getKey().getKey().toUpperCase();
        IslandArchetype arch = profile.archetype;

        double scale = profile.terrainScale;
        double vert = profile.verticality;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;

                double nx = (x + cx) * 0.09;
                double nz = (z + cz) * 0.09;

                double hNoise;

                // Archetype-specific noise "recipes" — this is what makes islands feel truly different
                switch (arch) {
                    case PLATEAUS -> {
                        // Flat tops with occasional sharp drops
                        hNoise = Math.sin(nx * 0.7) * Math.cos(nz * 0.65) * 2.8 * scale;
                        if (Math.abs(Math.sin(nx * 1.8)) < 0.35) hNoise *= 0.25; // flatten plateaus
                        hNoise += (rand.nextDouble() - 0.5) * 0.8;
                        if (rand.nextDouble() < 0.08 * vert) hNoise -= 2.5; // occasional sink
                    }
                    case VALLEYS_RIVERS -> {
                        hNoise = (Math.sin(nx * 1.1) + Math.cos(nz * 0.95)) * 1.6 * scale;
                        double river = Math.abs(Math.sin(nx * 0.45 + nz * 0.6)) - 0.2;
                        if (river < 0) hNoise -= (0.6 + rand.nextDouble() * 1.2) * (1.0 + profile.waterBias);
                        hNoise += (rand.nextDouble() - 0.5) * 0.9;
                    }
                    case CRAGGY_OUTCROPS -> {
                        hNoise = Math.sin(nx * 1.6) * Math.cos(nz * 1.4) * 3.2 * scale * vert;
                        hNoise += Math.abs(Math.sin(nx * 3.2)) * 1.8 * vert; // spiky ridges
                        hNoise += (rand.nextDouble() - 0.5) * 1.1;
                    }
                    case SCATTERED_ISLETS -> {
                        hNoise = Math.sin(nx * 0.6) * Math.cos(nz * 0.55) * 1.4 * scale;
                        if (dist > radius * 0.55 && rand.nextDouble() < 0.55) hNoise -= 1.8; // water gaps
                        hNoise += (rand.nextDouble() - 0.5) * 0.7;
                    }
                    case PILLARS_SPIRES -> {
                        hNoise = Math.sin(nx * 0.8) * Math.cos(nz * 0.75) * 2.1 * scale;
                        if (Math.sin(nx * 2.8) * Math.cos(nz * 2.4) > 0.65) hNoise += 3.5 * vert; // spire chance
                        hNoise += (rand.nextDouble() - 0.5) * 0.6;
                    }
                    default -> {
                        // ROLLING_HILLS + DENSE_CANOPY + BARREN_WASTES fall here with gentle multi-octave
                        hNoise = 0;
                        for (int o = 0; o < 4; o++) {
                            double f = Math.pow(1.8, o);
                            double a = 1.6 / f;
                            hNoise += Math.sin((x + cx) * 0.11 * f) * Math.cos((z + cz) * 0.105 * f) * a * scale;
                        }
                        hNoise += (rand.nextDouble() - 0.5) * 0.7;
                    }
                }

                // Biome + archetype final height shaping
                int heightVar = (int) Math.max(0, hNoise * 1.35 + (radius - dist) * 0.18);

                int baseY = cy;
                int topY = baseY + heightVar;

                // Core column
                for (int y = baseY - 3; y <= topY; y++) {
                    world.getBlockAt(cx + x, y, cz + z).setType(template.getBaseBlock());
                }

                // Surface
                int surfaceY = topY + 1;
                Block surf = world.getBlockAt(cx + x, surfaceY, cz + z);
                surf.setType(template.getSurfaceBlock());
                surf.setBiome(biome);

                // Occasional extra surface block for dunes / outcrops (stronger on craggy/plateau)
                double extraChance = (arch == IslandArchetype.CRAGGY_OUTCROPS || arch == IslandArchetype.PLATEAUS) ? 0.48 : 0.28;
                if (heightVar > 1 && rand.nextDouble() < extraChance) {
                    world.getBlockAt(cx + x, surfaceY + 1, cz + z).setType(template.getSurfaceBlock());
                }

                // Very rare "cliff face" exposure of base block (nice visual on craggy/plateau archetypes)
                if ((arch == IslandArchetype.CRAGGY_OUTCROPS || arch == IslandArchetype.PLATEAUS) && rand.nextDouble() < 0.06 * vert) {
                    if (rand.nextBoolean()) {
                        world.getBlockAt(cx + x, surfaceY, cz + z).setType(template.getBaseBlock());
                    }
                }
            }
        }
    }

    private void addRandomizedFeatures(World world, int cx, int cy, int cz, int radius,
                                       BiomeTemplate template, Biome biome, World.Environment dim, Random rand,
                                       GenerationProfile profile) {
        String biomeName = biome.getKey().getKey().toUpperCase();
        double vegDens = template.getVegetationDensity() * (0.75 + profile.featureBias * 0.5);
        int featureAttempts = (int) (radius * 3.8 * (0.65 + rand.nextDouble() * 0.7));

        IslandArchetype arch = profile.archetype;

        for (int i = 0; i < featureAttempts; i++) {
            int x = cx + rand.nextInt(radius * 2 + 1) - radius;
            int z = cz + rand.nextInt(radius * 2 + 1) - radius;
            int surfY = cy + getApproxSurfaceY(world, cx, cy, cz, x, z) + 1;
            Block surfaceBlock = world.getBlockAt(x, surfY, z);
            if (surfaceBlock.getType() != template.getSurfaceBlock()) continue;

            double r = rand.nextDouble();

            // Vegetation with profile bias
            if (r < 0.52 * vegDens) {
                Material veg = getRandomVegetation(biomeName, rand);
                if (veg != null) surfaceBlock.setType(veg);
            }
            // Rocks / small formations — much more common on craggy archetypes
            else if (r < 0.52 * vegDens + 0.22 * template.getRockChance() * (arch == IslandArchetype.CRAGGY_OUTCROPS ? 1.6 : 1.0)) {
                placeSmallRockFormation(world, x, surfY, z, template, rand);
            }
            // New: occasional small coherent "micro-landmark" pieces (still fully procedural)
            else if (profile.featureBias > 0.55 && rand.nextDouble() < 0.09 * profile.featureBias) {
                placeMicroLandmark(world, x, surfY, z, template, biomeName, arch, rand);
            }
        }

        // Ponds — strongly modulated by waterBias from profile
        double pondRoll = template.getPondChance() * (0.6 + profile.waterBias * 1.1);
        if (rand.nextDouble() < pondRoll) {
            int pr = radius / 2 + rand.nextInt(4);
            placeSimplePond(world, cx + rand.nextInt(radius / 2) - radius / 4,
                            cy, cz + rand.nextInt(radius / 2) - radius / 4, pr, template, rand);

            // Bonus connected smaller pond in valley/river archetypes
            if ((arch == IslandArchetype.VALLEYS_RIVERS || arch == IslandArchetype.SCATTERED_ISLETS) && rand.nextDouble() < 0.55) {
                placeSimplePond(world, cx + rand.nextInt(radius / 3) - radius / 6,
                                cy, cz + rand.nextInt(radius / 3) - radius / 6, pr / 2, template, rand);
            }
        }
    }

    /**
     * Small coherent procedural landmarks that make an island feel hand-crafted.
     * Examples: low ruined wall segments, raised platforms, small sinkhole lips, etc.
     */
    private void placeMicroLandmark(World world, int x, int y, int z, BiomeTemplate template,
                                    String biomeName, IslandArchetype arch, Random rand) {
        Material base = template.getBaseBlock();
        Material surf = template.getSurfaceBlock();

        if (arch == IslandArchetype.CRAGGY_OUTCROPS || arch == IslandArchetype.PLATEAUS) {
            // Low stone "wall" or outcrop
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (rand.nextDouble() < 0.75) {
                        world.getBlockAt(x + dx, y, z + dz).setType(base);
                        if (rand.nextBoolean()) world.getBlockAt(x + dx, y + 1, z + dz).setType(base);
                    }
                }
            }
        } else if (arch == IslandArchetype.VALLEYS_RIVERS) {
            // Slight raised "bank" around a low point
            world.getBlockAt(x, y, z).setType(surf);
            if (rand.nextBoolean()) world.getBlockAt(x + 1, y, z).setType(surf);
            if (rand.nextBoolean()) world.getBlockAt(x, y, z + 1).setType(surf);
        } else {
            // Generic small cluster
            world.getBlockAt(x, y, z).setType(base);
            if (rand.nextDouble() < 0.6) world.getBlockAt(x + rand.nextInt(3) - 1, y, z + rand.nextInt(3) - 1).setType(surf);
        }
    }

    private int getApproxSurfaceY(World world, int cx, int cy, int cz, int x, int z) {
        // Simple scan up from base (assumes terrain already placed)
        for (int y = cy + 8; y >= cy - 3; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) return y - cy;
        }
        return 0;
    }

    private Material getRandomVegetation(String biomeName, Random rand) {
        return switch (biomeName) {
            case "DESERT" -> rand.nextDouble() < 0.4 ? Material.DEAD_BUSH : null;
            case "JUNGLE" -> rand.nextDouble() < 0.5 ? Material.SHORT_GRASS : (rand.nextDouble() < 0.6 ? Material.FERN : Material.LARGE_FERN);
            case "FOREST", "PLAINS" -> rand.nextDouble() < 0.65 ? Material.SHORT_GRASS : (rand.nextDouble() < 0.5 ? Material.DANDELION : Material.POPPY);
            case "TAIGA" -> rand.nextDouble() < 0.7 ? Material.SHORT_GRASS : Material.FERN;
            default -> Material.SHORT_GRASS;
        };
    }

    private void placeSmallRockFormation(World world, int x, int y, int z, BiomeTemplate template, Random rand) {
        // Simple 2-4 block rock cluster
        world.getBlockAt(x, y, z).setType(template.getBaseBlock());
        if (rand.nextBoolean()) world.getBlockAt(x + 1, y, z).setType(template.getBaseBlock());
        if (rand.nextBoolean()) world.getBlockAt(x, y, z + 1).setType(template.getBaseBlock());
        if (rand.nextDouble() < 0.4) world.getBlockAt(x, y + 1, z).setType(template.getBaseBlock());
    }

    private void placeSimplePond(World world, int cx, int cy, int cz, int r, BiomeTemplate template, Random rand) {
        Material liquid = (template.getBiome().getKey().getKey().contains("nether") ? Material.LAVA : Material.WATER);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= r * r * 0.9) {
                    int depth = 1 + rand.nextInt(2);
                    for (int d = 0; d < depth; d++) {
                        Block b = world.getBlockAt(cx + x, cy - d, cz + z);
                        if (b.getType() == template.getBaseBlock() || b.getType() == template.getSurfaceBlock()) {
                            b.setType(liquid);
                        }
                    }
                    // Surround with surface block
                    if (rand.nextDouble() < 0.7) {
                        world.getBlockAt(cx + x, cy + 1, cz + z).setType(template.getSurfaceBlock());
                    }
                }
            }
        }
    }

    private void addBalancedOreVeins(World world, int cx, int cy, int cz, int radius,
                                     BiomeTemplate template, Random rand) {
        int numVeins = (int) (radius * template.getOreChance() * template.getOreDensityMultiplier() * (0.85 + rand.nextDouble() * 0.3));
        numVeins = Math.clamp(numVeins, 2, (int) (radius * 1.2)); // clamp for balance

        List<Material> ores = template.getAllowedOres();
        if (ores.isEmpty()) return;

        for (int i = 0; i < numVeins; i++) {
            int x = cx + rand.nextInt(radius * 2) - radius;
            int z = cz + rand.nextInt(radius * 2) - radius;
            int y = cy + rand.nextInt(4) - 1; // near surface or slightly below

            Material ore = ores.get(rand.nextInt(ores.size()));
            // Simple small "vein" (3-6 blocks clustered)
            for (int v = 0; v < 3 + rand.nextInt(4); v++) {
                int ox = x + rand.nextInt(3) - 1;
                int oz = z + rand.nextInt(3) - 1;
                Block b = world.getBlockAt(ox, y + rand.nextInt(2) - 1, oz);
                if (b.getType() == template.getBaseBlock()) {
                    b.setType(ore);
                }
            }
        }
    }

    private void addVariableTrees(World world, int cx, int cy, int cz, int radius,
                                  BiomeTemplate template, Random rand, GenerationProfile profile) {
        if (template.getTreeLog() == null) return;

        double treeMult = (profile.archetype == IslandArchetype.DENSE_CANOPY) ? 1.35 : 1.0;
        int numTrees = (int) (radius * template.getTreeChance() * (0.7 + rand.nextDouble() * 0.55) * treeMult);
        numTrees = Math.clamp(numTrees, 0, radius / 2 + 4);

        List<Location> placed = new ArrayList<>();
        for (int i = 0; i < numTrees; i++) {
            int attempts = 0;
            boolean placedTree = false;
            while (attempts < 9 && !placedTree) {
                int x = cx + rand.nextInt(radius * 2) - radius;
                int z = cz + rand.nextInt(radius * 2) - radius;
                int y = cy + getApproxSurfaceY(world, cx, cy, cz, x, z) + 1;

                boolean tooClose = false;
                for (Location p : placed) {
                    if (p.distance(new Location(world, x, y, z)) < 3.2) {
                        tooClose = true; break;
                    }
                }
                if (tooClose) { attempts++; continue; }

                Block ground = world.getBlockAt(x, y - 1, z);
                if (ground.getType() == template.getSurfaceBlock()) {
                    placeRichTree(world, x, y, z, template, rand, profile);
                    placed.add(new Location(world, x, y, z));
                    placedTree = true;
                }
                attempts++;
            }
        }
    }

    /**
     * Much richer tree shapes than the original simple blob. Still simple enough to be fast.
     */
    private void placeRichTree(World world, int x, int y, int z, BiomeTemplate template, Random rand, GenerationProfile profile) {
        Material log = template.getTreeLog();
        Material leaves = template.getTreeLeaves();
        if (log == null || leaves == null) return;

        IslandArchetype arch = profile.archetype;

        // Choose a tree "profile" based on archetype + random
        int style = rand.nextInt(100);
        int height;

        if (arch == IslandArchetype.DENSE_CANOPY && style < 55) {
            // Tall thin "emergent" tree
            height = 6 + rand.nextInt(5);
            for (int h = 0; h < height; h++) {
                world.getBlockAt(x, y + h, z).setType(log);
            }
            // Small high canopy
            for (int lx = -1; lx <= 1; lx++) for (int lz = -1; lz <= 1; lz++) {
                if (lx * lx + lz * lz <= 2) {
                    for (int ly = height - 3; ly <= height + 1; ly++) {
                        if (rand.nextDouble() < 0.9) {
                            Block b = world.getBlockAt(x + lx, y + ly, z + lz);
                            if (b.getType().isAir()) b.setType(leaves);
                        }
                    }
                }
            }
        } else if (arch == IslandArchetype.CRAGGY_OUTCROPS && style < 40) {
            // Short stout with wider base
            height = 3 + rand.nextInt(3);
            for (int h = 0; h < height; h++) world.getBlockAt(x, y + h, z).setType(log);
            // Wide low leaves
            for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) {
                if (lx * lx + lz * lz <= 5) {
                    for (int ly = 1; ly <= 3; ly++) {
                        if (rand.nextDouble() < 0.75) {
                            Block b = world.getBlockAt(x + lx, y + ly, z + lz);
                            if (b.getType().isAir()) b.setType(leaves);
                        }
                    }
                }
            }
        } else if (style < 18) {
            // Fallen log on ground (great for making the island feel lived-in)
            int len = 3 + rand.nextInt(3);
            int dir = rand.nextBoolean() ? 1 : 0;
            for (int i = 0; i < len; i++) {
                int fx = x + (dir == 0 ? i : 0);
                int fz = z + (dir == 1 ? i : 0);
                world.getBlockAt(fx, y, fz).setType(log);
                if (rand.nextDouble() < 0.6) world.getBlockAt(fx, y + 1, fz).setType(leaves); // mossy top
            }
        } else {
            // Classic varied blob (most common)
            height = 4 + rand.nextInt(4);
            for (int h = 0; h < height; h++) {
                world.getBlockAt(x, y + h, z).setType(log);
            }
            int canopySize = (style < 45) ? 2 : 3;
            for (int lx = -canopySize; lx <= canopySize; lx++) {
                for (int lz = -canopySize; lz <= canopySize; lz++) {
                    if (lx * lx + lz * lz <= canopySize * canopySize + 0.5) {
                        for (int ly = height - 2; ly <= height + 2; ly++) {
                            if (rand.nextDouble() < 0.82) {
                                Block b = world.getBlockAt(x + lx, y + ly, z + lz);
                                if (b.getType().isAir()) b.setType(leaves);
                            }
                        }
                    }
                }
            }
            world.getBlockAt(x, y + height, z).setType(leaves);
        }
    }

    private void addBiomeSpecialFeaturesEnhanced(World world, int cx, int cy, int cz, int radius,
                                                  BiomeTemplate template, Biome biome,
                                                  World.Environment dimension, Random rand,
                                                  GenerationProfile profile) {
        if (rand.nextDouble() > template.getSpecialFeatureChance() * (0.85 + profile.featureBias * 0.4)) return;

        String biomeName = biome.getKey().getKey().toUpperCase();
        IslandArchetype arch = profile.archetype;

        switch (biomeName) {
            case "JUNGLE" -> {
                // Vines + melon/pumpkin patches (more in dense canopy)
                int count = (arch == IslandArchetype.DENSE_CANOPY) ? 4 + rand.nextInt(4) : 3 + rand.nextInt(3);
                for (int i = 0; i < count; i++) {
                    int x = cx + rand.nextInt(radius) - radius/2;
                    int z = cz + rand.nextInt(radius) - radius/2;
                    int y = cy + getApproxSurfaceY(world, cx, cy, cz, x, z) + 2;
                    for (int v = 0; v < 3 + rand.nextInt(4); v++) {
                        Block b = world.getBlockAt(x, y - v, z);
                        if (b.getType().isAir() || b.getType() == Material.GRASS_BLOCK) {
                            b.setType(Material.VINE);
                        }
                    }
                    if (rand.nextDouble() < 0.55) {
                        world.getBlockAt(x + 1, y - 1, z).setType(Material.MELON);
                    }
                }
            }
            case "DESERT" -> {
                // Small well or ruined sandstone structure (more dramatic on plateau/cragged)
                if (rand.nextDouble() < (arch == IslandArchetype.PLATEAUS || arch == IslandArchetype.CRAGGY_OUTCROPS ? 0.85 : 0.6)) {
                    int x = cx + rand.nextInt(radius / 2);
                    int z = cz + rand.nextInt(radius / 2);
                    int y = cy + getApproxSurfaceY(world, cx, cy, cz, x, z);
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            world.getBlockAt(x + dx, y, z + dz).setType(Material.WATER);
                        } else {
                            world.getBlockAt(x + dx, y, z + dz).setType(Material.SANDSTONE);
                            if (rand.nextDouble() < 0.7) world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.SANDSTONE_WALL);
                        }
                    }
                }
            }
            case "NETHER_WASTES" -> {
                if (rand.nextDouble() < 0.5) {
                    placeSimplePond(world, cx + rand.nextInt(radius/2), cy, cz + rand.nextInt(radius/2), 3, template, rand);
                } else {
                    int x = cx + rand.nextInt(radius) - radius/2;
                    int z = cz + rand.nextInt(radius) - radius/2;
                    int y = cy + 3 + rand.nextInt(3);
                    world.getBlockAt(x, y, z).setType(Material.GLOWSTONE);
                    for (int i = 0; i < 5; i++) {
                        world.getBlockAt(x + rand.nextInt(3)-1, y + rand.nextInt(2), z + rand.nextInt(3)-1).setType(Material.GLOWSTONE);
                    }
                }
                // Extra basalt pillars on PILLARS_SPIRES archetype
                if (arch == IslandArchetype.PILLARS_SPIRES && rand.nextDouble() < 0.6) {
                    int px = cx + rand.nextInt(radius) - radius/2;
                    int pz = cz + rand.nextInt(radius) - radius/2;
                    int py = cy + getApproxSurfaceY(world, cx, cy, cz, px, pz) + 1;
                    for (int h = 0; h < 3 + rand.nextInt(4); h++) {
                        world.getBlockAt(px, py + h, pz).setType(Material.BASALT);
                    }
                }
            }
            case "THE_END" -> {
                int x = cx + rand.nextInt(radius) - radius/2;
                int z = cz + rand.nextInt(radius) - radius/2;
                int y = cy + getApproxSurfaceY(world, cx, cy, cz, x, z) + 1;
                world.getBlockAt(x, y, z).setType(Material.OBSIDIAN);
                if (rand.nextDouble() < 0.75) {
                    world.getBlockAt(x, y + 1, z).setType(Material.CHORUS_PLANT);
                    if (rand.nextBoolean()) world.getBlockAt(x, y + 2, z).setType(Material.CHORUS_PLANT);
                }
            }
            default -> {
                if (biomeName.equals("FOREST") || biomeName.equals("PLAINS")) {
                    // Flower meadow patches or berry bushes — nicer in some archetypes
                    int flowers = (arch == IslandArchetype.ROLLING_HILLS) ? 3 + rand.nextInt(3) : 2;
                    for (int i = 0; i < flowers; i++) {
                        int x = cx + rand.nextInt(radius) - radius/2;
                        int z = cz + rand.nextInt(radius) - radius/2;
                        world.getBlockAt(x, cy + getApproxSurfaceY(world, cx, cy, cz, x, z) + 1, z)
                             .setType(rand.nextDouble() < 0.5 ? Material.ROSE_BUSH : Material.LILAC);
                    }
                }
            }
        }
    }

    // ==================== STARTER CHEST (fixed location, balanced Play-to-Win loot) ====================

    private void placeStarterChest(Location center, Biome biome, Player player, long seed) {
        Random rand = new Random(seed ^ 0xDEADBEEFL); // stable but varied minor contents

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Fixed relative position (e.g. 3 blocks east of center on surface)
        int chestX = cx + 3;
        int chestZ = cz;
        int surfaceY = cy + 2; // approximate, adjust if needed in real

        // Find actual surface
        for (int y = cy + 6; y > cy - 2; y--) {
            if (center.getWorld().getBlockAt(chestX, y, chestZ).getType().isSolid()) {
                surfaceY = y + 1;
                break;
            }
        }

        Block chestBlock = center.getWorld().getBlockAt(chestX, surfaceY, chestZ);
        chestBlock.setType(Material.CHEST);

        if (chestBlock.getState() instanceof Chest chestState) {
            Inventory inv = chestState.getInventory();
            inv.clear();

            // Core balanced starter (same for everyone - Play to Win)
            inv.addItem(new ItemStack(Material.DIRT, 32 + rand.nextInt(16)));
            inv.addItem(new ItemStack(Material.COBBLESTONE, 24));
            inv.addItem(new ItemStack(Material.OAK_SAPLING, 3 + rand.nextInt(2)));
            inv.addItem(new ItemStack(Material.BREAD, 16));
            inv.addItem(new ItemStack(Material.STONE_PICKAXE, 1));
            inv.addItem(new ItemStack(Material.STONE_AXE, 1));
            inv.addItem(new ItemStack(Material.STONE_HOE, 1));
            inv.addItem(new ItemStack(Material.STONE_SWORD, 1));
            inv.addItem(new ItemStack(Material.WHEAT_SEEDS, 12 + rand.nextInt(8)));
            inv.addItem(new ItemStack(Material.POTATO, 6 + rand.nextInt(4)));
            inv.addItem(new ItemStack(Material.CARROT, 6 + rand.nextInt(4)));
            inv.addItem(new ItemStack(Material.SUGAR_CANE, 6));
            inv.addItem(new ItemStack(Material.BONE_MEAL, 12 + rand.nextInt(6)));

            // Early game guide book (onboarding help, no power)
            ItemStack guide = new ItemStack(Material.WRITTEN_BOOK);
            if (guide.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta bookMeta) {
                bookMeta.setTitle("FoliaSkyblock Beginner's Guide");
                bookMeta.setAuthor("Island Elder");
                bookMeta.addPage("Welcome to your new island!\n\n§6Key Commands:\n§f/is or /island - Main menu\n/quests or /daily - First tasks\n/skills - Personal MCMMO skills\n/collections - Discover & unlock\n/wardrobe - Earned cosmetics");
                bookMeta.addPage("§aEarly Game Tips:\n\nStart by breaking blocks and planting seeds.\nComplete 'First' quests for a free cosmetic trail!\nLevel skills for abilities.\nMinions automate farming/mining.\nEverything is Play-to-Win - no pay advantages.");
                bookMeta.addPage("§eProgression:\n\nIsland XP from actions/quests.\nUnlock dimensions at levels.\nPrestige & Slayers for top cosmetics.\nTrade at /bazaar /ah for missing items.\n\nHave fun and play fair!");
                bookMeta.setGeneration(org.bukkit.inventory.meta.BookMeta.Generation.ORIGINAL);
                guide.setItemMeta(bookMeta);
            }
            inv.addItem(guide);

            // Minor biome flavor / random (cosmetic, low value)
            String bname = biome.getKey().getKey().toUpperCase();
            if (bname.contains("DESERT")) {
                inv.addItem(new ItemStack(Material.SAND, 16 + rand.nextInt(8)));
                inv.addItem(new ItemStack(Material.CACTUS, 2 + rand.nextInt(2)));
            } else if (bname.contains("JUNGLE")) {
                inv.addItem(new ItemStack(Material.JUNGLE_SAPLING, 2));
                inv.addItem(new ItemStack(Material.COCOA_BEANS, 3));
            } else if (bname.contains("TAIGA")) {
                inv.addItem(new ItemStack(Material.SPRUCE_SAPLING, 2));
                inv.addItem(new ItemStack(Material.SWEET_BERRIES, 6));
            } else if (bname.contains("MUSHROOM")) {
                inv.addItem(new ItemStack(Material.RED_MUSHROOM, 3));
                inv.addItem(new ItemStack(Material.BROWN_MUSHROOM, 3));
            }

            // Small random bonus (still balanced, no rare items)
            if (rand.nextDouble() < 0.35) inv.addItem(new ItemStack(Material.COAL, 4 + rand.nextInt(4)));
            if (rand.nextDouble() < 0.3) inv.addItem(new ItemStack(Material.IRON_INGOT, 2));
        }
    }
    private void setBiomeInChunk(Location center, Biome biome) {
        if (biome == null) return;

        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Modern non-deprecated replacement (Block#setBiome).
        // We sample every 4 blocks vertically because biomes are stored in 4x4x4 sections.
        // This covers the island + generous padding so the visual (grass color, foliage, etc.) applies correctly.
        int radius = 24; // Safe for typical island sizes. Increase if you have very large templates.

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = cy - 4; y <= cy + 16; y += 4) {
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;

                    Block block = world.getBlockAt(x, y, z);
                    block.setBiome(biome);
                }
            }
        }
    }
}
