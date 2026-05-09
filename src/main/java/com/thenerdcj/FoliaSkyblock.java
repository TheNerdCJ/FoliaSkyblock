package com.thenerdcj;

import com.thenerdcj.Trade.TradeGUI;
import com.thenerdcj.auction.AuctionManager;
import com.thenerdcj.bazaar.BazaarManager;
import com.thenerdcj.boss.BossManager;
import com.thenerdcj.challenge.ChallengeManager;
import com.thenerdcj.command.*;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.gui.*;
import com.thenerdcj.island.generator.IslandGenerator;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandUpgradeGUI;
import com.thenerdcj.listener.*;
import com.thenerdcj.manager.*;
import com.thenerdcj.rank.RankManager;
import com.thenerdcj.shop.ChestShopManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaSkyblock extends JavaPlugin {

    // ==================== MANAGERS ====================
    private DatabaseManager databaseManager;
    private GridManager gridManager;
    private IslandManager islandManager;
    private EconomyManager economyManager;
    private RankManager rankManager;
    private ChallengeManager challengeManager;
    private BossManager bossManager;
    private AntiCheatManager antiCheatManager;
    private IslandUpgradeManager islandUpgradeManager;
    private IslandSettingsManager islandSettingsManager;
    private IslandBankManager islandBankManager;
    private IslandRatingManager islandRatingManager;
    private IslandWarpManager islandWarpManager;
    private ChestShopManager chestShopManager;
    private AuctionManager auctionManager;
    private BazaarManager bazaarManager;
    private ChatManager chatManager;
    private WorldManager worldManager;
    private IslandGenerator islandGenerator;

    // ==================== GUI INSTANCES ====================
    private TradeGUI tradeGUI;
    private SlayerGUI slayerGUI;
    private SlayerLeaderboardGUI slayerLeaderboardGUI;
    private SlayerAchievementGUI slayerAchievementGUI;
    private EnchantingTableGUI enchantingTableGUI;
    private IslandChatManager islandChatManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Core Managers
        this.databaseManager = new DatabaseManager(this);
        databaseManager.initDatabase();

        this.gridManager = new GridManager(this);
        this.islandGenerator = new IslandGenerator(this);
        this.islandManager = new IslandManager(this);
        this.economyManager = new EconomyManager(this);
        this.rankManager = new RankManager(this);
        this.challengeManager = new ChallengeManager(this);
        this.bossManager = new BossManager(this);
        this.antiCheatManager = new AntiCheatManager(this);
        this.islandUpgradeManager = new IslandUpgradeManager(this);
        this.islandSettingsManager = new IslandSettingsManager(this);
        this.islandBankManager = new IslandBankManager(this);
        this.islandRatingManager = new IslandRatingManager(this);
        this.islandWarpManager = new IslandWarpManager(this);
        this.chestShopManager = new ChestShopManager(this);
        this.auctionManager = new AuctionManager(this);
        this.bazaarManager = new BazaarManager(this);
        this.chatManager = new ChatManager(this);
        this.worldManager = new WorldManager(this);

        // GUI Instances (instantiate once)
        this.tradeGUI = new TradeGUI(this);
        this.slayerGUI = new SlayerGUI(this);
        this.slayerLeaderboardGUI = new SlayerLeaderboardGUI(this);
        this.slayerAchievementGUI = new SlayerAchievementGUI(this);
        this.enchantingTableGUI = new EnchantingTableGUI(this);
        this.islandChatManager = new IslandChatManager(this);

        registerCommands();
        registerListeners();

        Bukkit.getOnlinePlayers().forEach(player -> islandManager.loadPlayerIslands(player));

        getLogger().info("§a[FoliaSkyblock] Plugin enabled successfully!");
    }

    private void registerCommands() {
        getCommand("island").setExecutor(new IslandCommand(this));
        getCommand("is").setExecutor(new IslandCommand(this));
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("bal").setExecutor(new BalanceCommand(this));
        getCommand("rank").setExecutor(new RankCommand(this));
        getCommand("challenge").setExecutor(new ChallengeCommand(this));
        getCommand("challenges").setExecutor(new ChallengeCommand(this));
        getCommand("spawn").setExecutor(new PlayerCommand(this));
        getCommand("tpa").setExecutor(new PlayerCommand(this));
        getCommand("tpaccept").setExecutor(new PlayerCommand(this));
        getCommand("tpdeny").setExecutor(new PlayerCommand(this));
        getCommand("slayer").setExecutor(new SlayerCommand(this));
        getCommand("enchant").setExecutor(new EnchantCommand(this));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new IslandProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DimensionIslandListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AntiCheatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChallengeProgressListener(this), this);
        Bukkit.getPluginManager().registerEvents(new IslandXPListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChestShopListener(this), this);

        // GUIs that register themselves
        new ChallengeGUI(this);
        new BiomeSelectionGUI(this);
        new IslandSettingsGUI(this);
        new IslandUpgradeGUI(this);
        new IslandBankGUI(this);
        new IslandBrowseGUI(this);
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.shutdown();
        if (gridManager != null) gridManager.saveUsedPositions();
        getLogger().info("§c[FoliaSkyblock] Plugin disabled.");
    }

    // ==================== GETTERS ====================

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public GridManager getGridManager() { return gridManager; }
    public IslandManager getIslandManager() { return islandManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public RankManager getRankManager() { return rankManager; }
    public ChallengeManager getChallengeManager() { return challengeManager; }
    public BossManager getBossManager() { return bossManager; }
    public AntiCheatManager getAntiCheatManager() { return antiCheatManager; }
    public IslandUpgradeManager getIslandUpgradeManager() { return islandUpgradeManager; }
    public IslandSettingsManager getIslandSettingsManager() { return islandSettingsManager; }
    public IslandBankManager getIslandBankManager() { return islandBankManager; }
    public IslandRatingManager getIslandRatingManager() { return islandRatingManager; }
    public IslandWarpManager getIslandWarpManager() { return islandWarpManager; }
    public ChestShopManager getChestShopManager() { return chestShopManager; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public BazaarManager getBazaarManager() { return bazaarManager; }
    public ChatManager getChatManager() { return chatManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public IslandGenerator getIslandGenerator() { return islandGenerator; }

    // GUI Getters
    public TradeGUI getTradeGUI() { return tradeGUI; }
    public SlayerGUI getSlayerGUI() { return slayerGUI; }
    public SlayerLeaderboardGUI getSlayerLeaderboardGUI() { return slayerLeaderboardGUI; }
    public SlayerAchievementGUI getSlayerAchievementGUI() { return slayerAchievementGUI; }
    public EnchantingTableGUI getEnchantingTableGUI() { return enchantingTableGUI; }
    public IslandChatManager getIslandChatManager() { return islandChatManager; }

    // Placeholder for QuestManager (create the class later if needed)
    public Object getQuestManager() { return null; }

    // Folia detection
    public boolean isFolia() {
        return true; // This plugin is designed for Folia
    }
}