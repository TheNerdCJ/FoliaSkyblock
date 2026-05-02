package com.thenerdcj;

import com.thenerdcj.command.*;
import com.thenerdcj.command.ChallengeCommand;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.listener.*;
import com.thenerdcj.listener.IslandXPListener;
import com.thenerdcj.listener.ChallengeProgressListener;
import com.thenerdcj.manager.*;
import com.thenerdcj.rank.RankManager;
import com.thenerdcj.gui.BiomeSelectionGUI;
import com.thenerdcj.trade.TradeGUI;
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
    private TradeGUI tradeGUI;
    private BiomeSelectionGUI biomeSelectionGUI;
    private ChallengeManager challengeManager;
    private IslandUpgradeManager islandUpgradeManager;
    private IslandSettingsManager islandSettingsManager;

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

        // 3. Initialize World Manager (Custom Void Worlds)
        try {
            worldManager = new WorldManager(this);
            getLogger().info("§a[✓] World Manager initialized (3 void worlds)");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cFailed to initialize world manager!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Initialize All Managers
        try {
            economyManager = new EconomyManager(this);
            getLogger().info("§a[✓] Economy Manager initialized");

            islandManager = new IslandManager(this);
            getLogger().info("§a[✓] Island Manager initialized");

            rankManager = new RankManager(this);
            getLogger().info("§a[✓] Rank Manager initialized");

            chatManager = new ChatManager(this);
            getLogger().info("§a[✓] Chat Manager initialized");

            combatManager = new CombatManager(this);
            getLogger().info("§a[✓] Combat Manager initialized");

            gridManager = new GridManager(this);
            getLogger().info("§a[✓] Grid Manager initialized");

            bossManager = new BossManager(this);
            getLogger().info("§a[✓] Boss Manager initialized");

            // Initialize Challenge System (AI-powered)
            challengeManager = new ChallengeManager(this);
            getLogger().info("§a[✓] Challenge System initialized (AI-powered generation)");

            // Initialize Island Upgrade System (separate from leveling)
            islandUpgradeManager = new IslandUpgradeManager(this);
            getLogger().info("§a[✓] Island Upgrade System initialized");

            // Initialize Island Settings System
            islandSettingsManager = new IslandSettingsManager(this);
            getLogger().info("§a[✓] Island Settings System initialized");

            getLogger().info("§a[✓] All managers initialized");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cFailed to initialize managers!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 5. Create Default Spawn Island
        createDefaultSpawnIsland();

        // 6. Register Commands
        getCommand("island").setExecutor(new IslandCommand(this));
        getLogger().info("§a[✓] Commands registered");

        // 7. Register Listeners
        registerListeners();
        getLogger().info("§a[✓] Event listeners registered");

        // 8. Initialize Trade GUI
        tradeGUI = new TradeGUI(this);
        getLogger().info("§a[✓] Trade GUI initialized");

        getLogger().info("§a╔══════════════════════════════════════╗");
        getLogger().info("§a║  FoliaSkyblock Enabled Successfully!  ║");
        getLogger().info("§a╚══════════════════════════════════════╝");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new AntiCheatListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionIslandListener(this), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new com.thenerdcj.gui.IslandSettingsGUI(this), this);
        getServer().getPluginManager().registerEvents(new IslandXPListener(this), this);
        getServer().getPluginManager().registerEvents(new ChallengeProgressListener(this), this);
    }

    private void createDefaultSpawnIsland() {
        getLogger().info("§a[✓] Default spawn island protected at 0,0");
    }

    @Override
    public void onDisable() {
        getLogger().info("§cFoliaSkyblock disabled.");
    }

    public static FoliaSkyblock getInstance() {
        return instance;
    }

    // Getters
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public IslandManager getIslandManager() { return islandManager; }
    public ChatManager getChatManager() { return chatManager; }
    public CombatManager getCombatManager() { return combatManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public GridManager getGridManager() { return gridManager; }
    public RankManager getRankManager() { return rankManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public BossManager getBossManager() { return bossManager; }
    public TradeGUI getTradeGUI() { return tradeGUI; }
    public boolean isFolia() { return true; }

    public ChallengeManager getChallengeManager() {
        return challengeManager;
    }

    public IslandUpgradeManager getIslandUpgradeManager() {
        return islandUpgradeManager;
    }

    public IslandSettingsManager getIslandSettingsManager() {
        return islandSettingsManager;
    }
}