package com.thenerdcj;

import com.thenerdcj.anticheat.AntiCheatManager;
import com.thenerdcj.command.IslandCommand;
import com.thenerdcj.command.MiscCommand;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.economy.EconomyManager;
import com.thenerdcj.island.GridManager;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.listener.AntiCheatListener;
import com.thenerdcj.listener.CombatListener;
import com.thenerdcj.listener.DimensionIslandListener;
import com.thenerdcj.listener.IslandProtectionListener;
import com.thenerdcj.rank.RankManager;
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

    @Override
    public void onEnable() {
        instance = this;

        // Save all custom configuration files
        saveDefaultConfig();                    // config.yml
        saveResource("ranks.yml", false);       // ranks.yml
        saveResource("anticheat.yml", false);   // anticheat.yml
        saveResource("trades.yml", false);      // trades.yml

        // Initialize managers
        this.databaseManager = new DatabaseManager(this);
        this.gridManager = new GridManager(this);
        this.islandManager = new IslandManager(this);
        this.economyManager = new EconomyManager(this);
        this.rankManager = new RankManager(this);
        this.antiCheatManager = new AntiCheatManager(this);

        // Register commands
        getCommand("island").setExecutor(new IslandCommand(this));
        getCommand("island").setTabCompleter((IslandCommand) getCommand("island").getExecutor());

        MiscCommand misc = new MiscCommand(this);
        getCommand("spawn").setExecutor(misc);
        getCommand("setspawn").setExecutor(misc);
        getCommand("tpa").setExecutor(misc);
        getCommand("tpaccept").setExecutor(misc);
        getCommand("tpdeny").setExecutor(misc);
        getCommand("rules").setExecutor(misc);
        getCommand("bal").setExecutor(misc);
        getCommand("rank").setExecutor(misc);

        // Register listeners
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionIslandListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        // === CONDITIONAL ANTI-CHEAT REGISTRATION ===
        if (antiCheatManager.getConfig().getBoolean("enabled", true)) {
            getServer().getPluginManager().registerEvents(new AntiCheatListener(this), this);
            getLogger().info("§aAnti-Cheat + Anti-Bot system enabled.");
        } else {
            getLogger().info("§eAnti-Cheat + Anti-Bot system is DISABLED (via anticheat.yml).");
        }

        // Setup custom void worlds
        setupSkyblockWorlds();

        getLogger().info("§aFoliaSkyblock has been enabled successfully!");
    }

    private void setupSkyblockWorlds() {
        createVoidWorld("skyblock", World.Environment.NORMAL);
        createVoidWorld("skyblock_nether", World.Environment.NETHER);
        createVoidWorld("skyblock_end", World.Environment.THE_END);

        getLogger().info("§aAll three Skyblock worlds (Overworld, Nether, End) have been created.");
    }

    private void createVoidWorld(String name, World.Environment env) {
        if (getServer().getWorld(name) != null) return;

        WorldCreator creator = new WorldCreator(name);
        creator.environment(env);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[],\"biome\":\"minecraft:the_void\"}");
        creator.generateStructures(false);

        World world = creator.createWorld();
        if (world != null) {
            world.setSpawnLocation(0, 100, 0);
            getLogger().info("§aCreated custom world: " + name);
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