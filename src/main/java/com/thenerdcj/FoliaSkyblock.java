package com.thenerdcj;

import com.thenerdcj.command.*;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.listener.*;
import com.thenerdcj.manager.*;
import com.thenerdcj.rank.RankManager;
import com.thenerdcj.gui.*;
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
    private BossManager bossManager;
    private com.thenerdcj.trade.TradeGUI tradeGUI;
    private BiomeSelectionGUI biomeSelectionGUI;
    private ChallengeManager challengeManager;

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
            getLogger().info("§a[✓] Database initialized (HikariCP + SQLite)");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cFailed to initialize database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Initialize Core Managers
        try {
            gridManager = new GridManager(this);
            islandManager = new IslandManager(this);
            chatManager = new ChatManager(this);
            combatManager = new CombatManager(this);
            economyManager = new EconomyManager(this);
            rankManager = new RankManager(this);
            worldManager = new WorldManager(this);
            bossManager = new BossManager(this);
            tradeGUI = new com.thenerdcj.trade.TradeGUI(this);

            // Initialize Donor Biome Selection GUI
            biomeSelectionGUI = new BiomeSelectionGUI(this);
            getLogger().info("§a[✓] Donor Biome Selection GUI initialized");

            // Initialize Challenge System (AI-powered)
            challengeManager = new ChallengeManager(this);
            getLogger().info("§a[✓] Challenge System initialized (AI-powered generation)");

            getLogger().info("§a[✓] All managers initialized");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cFailed to initialize managers!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Create Default Spawn Island
        createDefaultSpawnIsland();

        // 5. Register Commands
        getCommand("island").setExecutor(new IslandCommand(this));
        getCommand("bal").setExecutor(new BalanceCommand(this));
        getCommand("rank").setExecutor(new RankCommand(this));
        getCommand("challenge").setExecutor(new ChallengeCommand(this));
        getCommand("daily").setExecutor(new ChallengeCommand(this));

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

        // 6. Register Listeners
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionIslandListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new AntiCheatListener(this), this);
        getServer().getPluginManager().registerEvents(new IslandXPListener(this), this);
        getServer().getPluginManager().registerEvents(new ChallengeProgressListener(this), this);

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

    // ==================== GETTERS ====================
    public static FoliaSkyblock getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public IslandManager getIslandManager() { return islandManager; }
    public ChatManager getChatManager() { return chatManager; }
    public CombatManager getCombatManager() { return combatManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public GridManager getGridManager() { return gridManager; }
    public RankManager getRankManager() { return rankManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public BossManager getBossManager() { return bossManager; }
    public com.thenerdcj.trade.TradeGUI getTradeGUI() { return tradeGUI; }
    public BiomeSelectionGUI getBiomeSelectionGUI() { return biomeSelectionGUI; }
    public ChallengeManager getChallengeManager() { return challengeManager; }
    public boolean isFolia() { return true; }
}