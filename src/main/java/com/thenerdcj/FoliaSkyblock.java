package com.thenerdcj;

import com.thenerdcj.command.*;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.manager.EconomyManager;
import com.thenerdcj.island.GridManager;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.listener.*;
import com.thenerdcj.manager.*;
import com.thenerdcj.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class FoliaSkyblock extends JavaPlugin {

    private static FoliaSkyblock instance;

    private DatabaseManager databaseManager;
    private IslandManager islandManager;
    private ChatManager chatManager;
    private CombatManager combatManager;
    private EconomyManager economyManager;
    private GridManager gridManager;
    private RankManager rankManager;
    private WorldManager worldManager;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("§6╔══════════════════════════════════════╗");
        getLogger().info("§6║     FoliaSkyblock Starting...        ║");
        getLogger().info("§6╚══════════════════════════════════════╝");

        // 1. Save default config if it doesn't exist
        saveDefaultConfig();

        // 2. Initialize HikariCP Database
        try {
            databaseManager = new DatabaseManager(this);
            getLogger().info("§a[✓] HikariCP Database initialized");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cFailed to initialize Database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Initialize World Manager and create void worlds
        try {
            worldManager = new WorldManager(this);
            worldManager.initializeWorlds();
            getLogger().info("§a[✓] World Manager initialized");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cFailed to initialize World Manager!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Initialize GridManager and load used positions from database
        gridManager = new GridManager(this);
        gridManager.loadUsedPositions(databaseManager);
        getLogger().info("§a[✓] GridManager initialized and loaded used positions");

        // 5. Initialize All Other Managers
        try {
            islandManager = new IslandManager(this);
            chatManager = new ChatManager(this);
            combatManager = new CombatManager(this);
            economyManager = new EconomyManager(this);
            rankManager = new RankManager(this);
            getLogger().info("§a[✓] All managers initialized");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cFailed to initialize managers!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 6. Create Default Spawn Island
        createDefaultSpawnIsland();

        // 7. Register Commands
        getCommand("island").setExecutor(new IslandCommand(this));
        getCommand("bal").setExecutor(new BalanceCommand(this));
        getCommand("rank").setExecutor(new RankCommand(this));

        PlayerCommand playerCmd = new PlayerCommand(this);
        getCommand("spawn").setExecutor(playerCmd);
        getCommand("home").setExecutor(playerCmd);
        getCommand("tpa").setExecutor(playerCmd);
        getCommand("tpaccept").setExecutor(playerCmd);
        getCommand("tpac").setExecutor(playerCmd);
        getCommand("tpdeny").setExecutor(playerCmd);
        getCommand("tpdecline").setExecutor(playerCmd);
        getCommand("tpignore").setExecutor(playerCmd);
        getCommand("pending").setExecutor(playerCmd);
        getCommand("rules").setExecutor(playerCmd);

        StaffCommand staffCmd = new StaffCommand(this);
        getCommand("mute").setExecutor(staffCmd);
        getCommand("unmute").setExecutor(staffCmd);
        getCommand("setspawn").setExecutor(staffCmd);

        getLogger().info("§a[✓] All commands registered");

        // 8. Register Listeners
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionIslandListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new AntiCheatListener(this), this);

        getLogger().info("§a[✓] All listeners registered");
        getLogger().info("§a╔══════════════════════════════════════╗");
        getLogger().info("§a║  FoliaSkyblock Enabled Successfully! ║");
        getLogger().info("§a╚══════════════════════════════════════╝");
    }

    private void createDefaultSpawnIsland() {
        String overworldName = getConfig().getString("worlds.overworld", "skyblock");
        World overworld = Bukkit.getWorld(overworldName);

        if (overworld == null) {
            getLogger().warning("§cOverworld '" + overworldName + "' not found!");
            return;
        }

        Location spawnLoc = new Location(overworld, 0, 100, 0);
        overworld.setSpawnLocation(spawnLoc);
        getLogger().info("§a[✓] Default spawn island created at (0, 0) in " + overworldName);
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
            getLogger().info("§aHikariCP connection pool closed.");
        }
        getLogger().info("§cFoliaSkyblock disabled.");
    }

    public static FoliaSkyblock getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public IslandManager getIslandManager() { return islandManager; }
    public ChatManager getChatManager() { return chatManager; }
    public CombatManager getCombatManager() { return combatManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public GridManager getGridManager() { return gridManager; }
    public RankManager getRankManager() { return rankManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public boolean isFolia() { return true; }
}