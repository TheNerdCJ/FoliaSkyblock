package com.thenerdcj;

import com.thenerdcj.anticheat.AntiCheatManager;
import com.thenerdcj.command.IslandCommand;
import com.thenerdcj.command.MiscCommand;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.economy.EconomyManager;
import com.thenerdcj.island.GridManager;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.listener.*;
import com.thenerdcj.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaSkyblock extends JavaPlugin {

    private static FoliaSkyblock instance;

    private DatabaseManager databaseManager;
    private GridManager gridManager;
    private IslandManager islandManager;
    private EconomyManager economyManager;
    private RankManager rankManager;
    private AntiCheatManager antiCheatManager;

    // Folia detection
    private boolean isFolia = false;

    @Override
    public void onEnable() {
        instance = this;

        // Detect Folia at runtime
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("§aFolia detected! Running in regionized multithreaded mode.");
        } catch (ClassNotFoundException e) {
            getLogger().info("§ePaper/Spigot detected — Folia optimizations disabled.");
        }

        // Save all config files
        saveDefaultConfig();
        saveResource("ranks.yml", false);
        saveResource("anticheat.yml", false);
        saveResource("trades.yml", false);

        // Initialize all managers
        this.databaseManager = new DatabaseManager(this);
        this.gridManager = new GridManager(this);
        this.islandManager = new IslandManager(this);
        this.economyManager = new EconomyManager(this);
        this.rankManager = new RankManager(this);
        this.antiCheatManager = new AntiCheatManager(this);

        // Register commands (cleaner way)
        IslandCommand islandCmd = new IslandCommand(this);
        getCommand("island").setExecutor(islandCmd);
        getCommand("island").setTabCompleter(islandCmd);

        MiscCommand miscCommand = new MiscCommand(this);

// Register all commands that MiscCommand handles
        getCommand("spawn").setExecutor(miscCommand);
        getCommand("setspawn").setExecutor(miscCommand);
        getCommand("tpa").setExecutor(miscCommand);
        getCommand("tpaccept").setExecutor(miscCommand);
        getCommand("tpac").setExecutor(miscCommand);
        getCommand("tpdeny").setExecutor(miscCommand);
        getCommand("tpdecline").setExecutor(miscCommand);
        getCommand("tpignore").setExecutor(miscCommand);
        getCommand("pending").setExecutor(miscCommand);
        getCommand("rules").setExecutor(miscCommand);
        getCommand("bal").setExecutor(miscCommand);
        getCommand("mute").setExecutor(miscCommand);
        getCommand("unmute").setExecutor(miscCommand);

// Also set TabCompleter
        getCommand("spawn").setTabCompleter(miscCommand);

        // Register listeners
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionIslandListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        // Conditional anti-cheat
        if (antiCheatManager.getConfig().getBoolean("enabled", true)) {
            getServer().getPluginManager().registerEvents(new AntiCheatListener(this), this);
            getLogger().info("§aAnti-Cheat + Anti-Bot system enabled.");
        } else {
            getLogger().info("§eAnti-Cheat + Anti-Bot system is DISABLED (via anticheat.yml).");
        }

        // Setup custom void worlds (must be sync)
        setupSkyblockWorlds();

        getLogger().info("§aFoliaSkyblock enabled successfully! " + (isFolia ? "§b[Folia Mode]" : ""));
    }

    private void setupSkyblockWorlds() {
        createVoidWorld("skyblock", World.Environment.NORMAL);
        createVoidWorld("skyblock_nether", World.Environment.NETHER);
        createVoidWorld("skyblock_end", World.Environment.THE_END);
        getLogger().info("§aAll three Skyblock worlds ready.");
    }

    private void createVoidWorld(String name, World.Environment env) {
        if (Bukkit.getWorld(name) != null) return;

        WorldCreator creator = new WorldCreator(name);
        creator.environment(env);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[],\"biome\":\"minecraft:the_void\"}");
        creator.generateStructures(false);

        World world = creator.createWorld();
        if (world != null) {
            world.setSpawnLocation(0, 100, 0);
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        getLogger().info("§cFoliaSkyblock has been disabled.");
    }

    // ====================== GETTERS ======================
    public static FoliaSkyblock getInstance() {
        return instance;
    }

    public boolean isFolia() {
        return isFolia;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public GridManager getGridManager() {
        return gridManager;
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public AntiCheatManager getAntiCheatManager() {
        return antiCheatManager;
    }
}