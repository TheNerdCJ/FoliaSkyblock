package com.thenerdcj;

import com.thenerdcj.chat.ChatManager;
import com.thenerdcj.combat.CombatManager;
import com.thenerdcj.command.*;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.economy.EconomyManager;
import com.thenerdcj.island.GridManager;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.listener.*;
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

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("§6╔══════════════════════════════════════╗");
        getLogger().info("§6║     FoliaSkyblock Starting...        ║");
        getLogger().info("§6╚══════════════════════════════════════╝");

        // 1. Initialize HikariCP Database
        try {
            databaseManager = new DatabaseManager(this);
            getLogger().info("§a[✓] HikariCP Database initialized");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cFailed to initialize Database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Initialize GridManager and load used positions from database
        gridManager = new GridManager(this);
        gridManager.loadUsedPositions(databaseManager);
        getLogger().info("§a[✓] GridManager initialized and loaded used positions");

        // 3. Initialize All Other Managers
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

        // 4. Create Default Spawn Island
        createDefaultSpawnIsland();

        // 5. Register Commands
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

        // 6. Register Listeners
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
        World overworld = Bukkit.getWorld("world");
        if (overworld == null) {
            getLogger().warning("§cDefault world 'world' not found!");
            return;
        }
        Location spawnLoc = new Location(overworld, 0, 100, 0);
        overworld.setSpawnLocation(spawnLoc);
        getLogger().info("§a[✓] Default spawn island created at (0, 0)");
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
    public boolean isFolia() { return true; } // Folia detection
}