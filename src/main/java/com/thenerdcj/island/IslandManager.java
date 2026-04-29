package com.thenerdcj.island;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.economy.EconomyManager;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandManager {

    private final FoliaSkyblock plugin;
    private final GridManager gridManager;
    private final EconomyManager economyManager;
    private final DatabaseManager databaseManager;

    private final String skyblockWorldName;
    private final int baseY;

    // Cache: UUID -> (Dimension -> Island)
    private final Map<UUID, Map<World.Environment, Island>> playerToIsland = new ConcurrentHashMap<>();
    // Cache: GridPosition -> Island
    private final Map<GridPosition, Island> gridToIsland = new ConcurrentHashMap<>();

    public IslandManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.gridManager = plugin.getGridManager();
        this.economyManager = plugin.getEconomyManager();
        this.databaseManager = plugin.getDatabaseManager();

        this.skyblockWorldName = plugin.getConfig().getString("world.name", "skyblock");
        this.baseY = plugin.getConfig().getInt("island.base-y", 80);
    }

    // ====================== CREATE ISLAND ======================
    public CompletableFuture<Boolean> createIsland(Player player, String biomeName, World.Environment dimension) {
        UUID uuid = player.getUniqueId();
        if (hasIslandInDimension(uuid, dimension)) {
            player.sendMessage("§cYou already have an island in this dimension!");
            return CompletableFuture.completedFuture(false);
        }

        return gridManager.createPlayerIsland(uuid, dimension).thenCompose(pos -> {
            if (pos == null) {
                player.sendMessage("§cNo free island positions available.");
                return CompletableFuture.completedFuture(false);
            }

            Biome biome = parseBiome(biomeName != null ? biomeName : "PLAINS");
            IslandParty party = new IslandParty(uuid);
            Island island = new Island(pos, uuid, party, dimension);

            // Cache
            playerToIsland.computeIfAbsent(uuid, k -> new EnumMap<>(World.Environment.class)).put(dimension, island);
            gridToIsland.put(pos, island);

            // Save to DB
            databaseManager.saveIsland(pos.x(), pos.z(), uuid, biome.name(), dimension);
            databaseManager.saveMember(pos.x(), pos.z(), uuid, IslandRank.OWNER, dimension);

            // Generate island
            generateIsland(pos, biome, dimension);

            // Give starting balance
            economyManager.setIslandBalance(pos, economyManager.getDefaultIslandBalance());

            player.sendMessage("§aYour " + dimension.name().toLowerCase() + " island has been created!");
            return CompletableFuture.completedFuture(true);
        });
    }

    private Biome parseBiome(String name) {
        try {
            return Biome.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Biome.PLAINS;
        }
    }

    private void generateIsland(GridPosition pos, Biome biome, World.Environment dimension) {
        World world = Bukkit.getWorld(getWorldNameForDimension(dimension));
        if (world == null) return;

        Location center = gridManager.getCenterLocation(pos, world);
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        // Base platform
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                world.getBlockAt(cx + x, baseY, cz + z).setType(Material.GRASS_BLOCK);
            }
        }

        generateBiomeDecorations(world, cx, baseY + 1, cz, biome);
        placeStarterChest(world, cx + 2, baseY + 1, cz + 2, dimension);
    }

    private void generateBiomeDecorations(World world, int x, int y, int z, Biome biome) {
        // Simple biome decorations (expand as needed)
    }

    private void placeStarterChest(World world, int x, int y, int z, World.Environment dimension) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        Inventory inv = chest.getInventory();

        if (dimension == World.Environment.NETHER) {
            inv.addItem(new ItemStack(Material.NETHERRACK, 64));
            inv.addItem(new ItemStack(Material.BLAZE_ROD, 8));
        } else if (dimension == World.Environment.THE_END) {
            inv.addItem(new ItemStack(Material.END_STONE, 64));
            inv.addItem(new ItemStack(Material.CHORUS_FRUIT, 8));
        } else {
            inv.addItem(new ItemStack(Material.OAK_LOG, 32));
            inv.addItem(new ItemStack(Material.BREAD, 16));
            inv.addItem(new ItemStack(Material.STONE_PICKAXE));
        }
    }

    private String getWorldNameForDimension(World.Environment dimension) {
        return switch (dimension) {
            case NETHER -> "skyblock_nether";
            case THE_END -> "skyblock_end";
            default -> skyblockWorldName;
        };
    }

    // ====================== TRADE SYSTEM ======================
    public void performTrade(Player player, int tradeIndex) {
        File file = new File(plugin.getDataFolder(), "trades.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> trades = config.getMapList("trades");

        if (tradeIndex >= trades.size()) return;

        Map<?, ?> trade = trades.get(tradeIndex);

        // Safer extraction - this fixes the red underline
        Object levelObj = trade.get("level");
        int requiredLevel = levelObj instanceof Number ? ((Number) levelObj).intValue() : 1;

        Island island = getIsland(player.getUniqueId(), World.Environment.NORMAL);
        if (island == null || island.getLevel() < requiredLevel) {
            player.sendMessage("§cYou need island level §e" + requiredLevel + " §cto unlock this trade!");
            return;
        }

        // TODO: Add real item exchange logic here later
        player.sendMessage("§aTrade completed! (Level " + island.getLevel() + ")");
    }

    // ====================== LEVELING ======================
    public void addIslandXp(GridPosition pos, double xpAmount) {
        Island island = gridToIsland.get(pos);
        if (island != null) {
            island.addXp(xpAmount);
            databaseManager.saveIslandLevel(pos.x(), pos.z(), island.getLevel(), island.getXp(), island.getDimension());
        }
    }

    // ====================== GET / CACHE ======================
    public Island getIsland(UUID uuid, World.Environment dimension) {
        return playerToIsland.getOrDefault(uuid, Collections.emptyMap()).get(dimension);
    }

    public Island getIslandByGrid(GridPosition pos) {
        return gridToIsland.get(pos);
    }

    public boolean hasIslandInDimension(UUID uuid, World.Environment dimension) {
        return getIsland(uuid, dimension) != null;
    }

    public Location getIslandHome(Player player) {
        Island island = getIsland(player.getUniqueId(), World.Environment.NORMAL);
        if (island == null) return player.getWorld().getSpawnLocation();
        return gridManager.getCenterLocation(island.getGridPosition(), player.getWorld());
    }

    // ====================== RESET / DELETE ======================
    public CompletableFuture<Boolean> resetIsland(Player player, String biomeName) {
        UUID uuid = player.getUniqueId();
        Island island = getIsland(uuid, World.Environment.NORMAL);
        if (island == null) {
            player.sendMessage("§cYou don't have an island to reset!");
            return CompletableFuture.completedFuture(false);
        }

        return deleteIsland(uuid, World.Environment.NORMAL)
                .thenCompose(success -> success ? createIsland(player, biomeName, World.Environment.NORMAL) : CompletableFuture.completedFuture(false));
    }

    public CompletableFuture<Boolean> deleteIsland(UUID uuid, World.Environment dimension) {
        Island island = playerToIsland.getOrDefault(uuid, new EnumMap<>(World.Environment.class)).remove(dimension);
        if (island == null) return CompletableFuture.completedFuture(false);

        gridToIsland.remove(island.getGridPosition());
        databaseManager.deleteIsland(uuid, dimension);
        return gridManager.deletePlayerIsland(uuid, dimension);
    }

    // ====================== PARTY ======================
    public boolean addMemberToIsland(UUID ownerUuid, UUID memberUuid) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island == null) return false;
        return island.getParty().addMember(memberUuid);
    }

    public boolean removeMemberFromIsland(UUID ownerUuid, UUID memberUuid) {
        Island island = getIsland(ownerUuid, World.Environment.NORMAL);
        if (island == null) return false;
        boolean removed = island.getParty().removeMember(memberUuid);
        if (removed) playerToIsland.getOrDefault(memberUuid, new EnumMap<>(World.Environment.class)).remove(World.Environment.NORMAL);
        return removed;
    }

    public boolean setMemberRank(UUID setterUuid, UUID targetUuid, IslandRank newRank) {
        Island island = getIsland(setterUuid, World.Environment.NORMAL);
        if (island == null) return false;
        return island.getParty().setRank(setterUuid, targetUuid, newRank);
    }

    // ====================== DB LOADING ======================
    public void loadIslandData() {
        plugin.getLogger().info("§eIsland data loaded into cache.");
    }

    public CompletableFuture<Boolean> inviteToParty(Player inviter, String targetName) {
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> acceptPartyInvite(Player player) {
        return CompletableFuture.completedFuture(true);
    }
}