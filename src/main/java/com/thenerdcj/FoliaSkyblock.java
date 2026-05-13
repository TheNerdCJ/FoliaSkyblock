package com.thenerdcj;

import com.thenerdcj.Trade.TradeGUI;
import com.thenerdcj.auction.AuctionManager;
import com.thenerdcj.bazaar.BazaarManager;
import com.thenerdcj.boss.BossManager;
import com.thenerdcj.challenge.ChallengeManager;
import com.thenerdcj.command.*;
import com.thenerdcj.quest.QuestManager;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.gui.*;
import com.thenerdcj.hologram.HologramManager;
import com.thenerdcj.island.generator.IslandGenerator;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandUpgradeGUI;
import com.thenerdcj.listener.*;
import com.thenerdcj.manager.*;
import com.thenerdcj.rank.RankManager;
import com.thenerdcj.shop.ChestShopManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * FoliaSkyblock - High performance Skyblock for Folia API.
 */
public class FoliaSkyblock extends JavaPlugin {

    // ==================== MANAGERS ====================
    private DatabaseManager databaseManager;
    private GridManager gridManager;
    private IslandManager islandManager;
    private EconomyManager economyManager;
    private RankManager rankManager;
    private ChallengeManager challengeManager;
    private QuestManager questManager;
    private BossManager bossManager;
    private AntiCheatManager antiCheatManager;
    private IslandUpgradeManager islandUpgradeManager;
    private MinionManager minionManager;
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
    private ResetConfirmationGUI resetConfirmationGUI;
    private BiomeSelectionGUI biomeSelectionGUI;

    // NEW: Hologram Manager
    private HologramManager hologramManager;

    // ==================== GUI INSTANCES ====================
    private TradeGUI tradeGUI;
    private SlayerGUI slayerGUI;
    private SlayerLeaderboardGUI slayerLeaderboardGUI;
    private SlayerAchievementGUI slayerAchievementGUI;
    private EnchantingTableGUI enchantingTableGUI;
    private IslandChatManager islandChatManager;
    private IslandUpgradeGUI islandUpgradeGUI;


    @Override
    public void onEnable() {
        saveDefaultConfig();

        // === Initialize Managers ===
        this.databaseManager = new DatabaseManager(this);
        databaseManager.initDatabase();

        this.gridManager = new GridManager(this);
        this.islandGenerator = new IslandGenerator(this);
        this.islandManager = new IslandManager(this);
        this.economyManager = new EconomyManager(this);
        this.rankManager = new RankManager(this);
        this.challengeManager = new ChallengeManager(this);
        this.questManager = new QuestManager(this);
        this.bossManager = new BossManager(this);
        this.antiCheatManager = new AntiCheatManager(this);
        this.islandUpgradeManager = new IslandUpgradeManager(this);
        this.islandSettingsManager = new IslandSettingsManager(this);
        this.islandBankManager = new IslandBankManager(this);
        this.islandRatingManager = new IslandRatingManager(this);
        this.islandWarpManager = new IslandWarpManager(this);
        this.minionManager = new MinionManager(this);
        this.chestShopManager = new ChestShopManager(this);
        this.auctionManager = new AuctionManager(this);
        this.bazaarManager = new BazaarManager(this);
        this.chatManager = new ChatManager(this);
        this.worldManager = new WorldManager(this);

        this.worldManager.initializeWorlds();

        this.hologramManager = new HologramManager(this);
        hologramManager.loadAndSpawnAll();

        // === GUIs ===
        this.tradeGUI = new TradeGUI(this);
        this.slayerGUI = new SlayerGUI(this);
        this.slayerLeaderboardGUI = new SlayerLeaderboardGUI(this);
        this.slayerAchievementGUI = new SlayerAchievementGUI(this);
        this.enchantingTableGUI = new EnchantingTableGUI(this);
        this.islandChatManager = new IslandChatManager(this);
        this.resetConfirmationGUI = new ResetConfirmationGUI(this);
        this.biomeSelectionGUI = new BiomeSelectionGUI(this);
        this.islandUpgradeGUI = new IslandUpgradeGUI(this);

        // === Register Commands & Listeners ===
        registerCommands();
        registerListeners();                    // ← ADD THIS

        // Load islands for already online players
        Bukkit.getOnlinePlayers().forEach(player -> islandManager.loadPlayerIslands(player));

        getLogger().info("§a[FoliaSkyblock] Plugin enabled successfully on Folia!");
    }
    @Override
    public void onDisable() {
        if (hologramManager != null) hologramManager.cleanup();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("§e[FoliaSkyblock] Plugin disabled. Holograms cleaned up.");
    }

    private void registerCommands() {
        safeRegisterCommand("island", new IslandCommand(this));
        safeRegisterCommand("is", new IslandCommand(this));
        safeRegisterCommand("balance", new BalanceCommand(this));
        safeRegisterCommand("bal", new BalanceCommand(this));
        safeRegisterCommand("rank", new RankCommand(this));
        safeRegisterCommand("challenge", new ChallengeCommand(this));
        safeRegisterCommand("challenges", new ChallengeCommand(this));
        safeRegisterCommand("spawn", new PlayerCommand(this));
        safeRegisterCommand("tpa", new PlayerCommand(this));
        safeRegisterCommand("tpaccept", new PlayerCommand(this));
        safeRegisterCommand("tpdeny", new PlayerCommand(this));
        safeRegisterCommand("slayer", new SlayerCommand(this));
        safeRegisterCommand("enchant", new EnchantCommand(this));
        safeRegisterCommand("auction", new AuctionCommand(this));
        safeRegisterCommand("bazaar", new BazaarCommand(this));
        safeRegisterCommand("staff", new StaffCommand(this));
        safeRegisterCommand("minions", new MinionsCommand(this));
        safeRegisterCommand("setspawn", new StaffCommand(this));
        safeRegisterCommand("mute", new StaffCommand(this));
        safeRegisterCommand("unmute", new StaffCommand(this));
        safeRegisterCommand("home", new IslandCommand(this));
        safeRegisterCommand("pending", new PlayerCommand(this));
        safeRegisterCommand("daily", new ChallengeCommand(this));
        safeRegisterCommand("rules", new PlayerCommand(this));

        safeRegisterCommand("trade", (sender, cmd, label, args) -> {
            if (sender instanceof Player player) {
                tradeGUI.openTradeGUI(player);
                return true;
            }
            sender.sendMessage("§cThis command can only be used by players.");
            return false;
        });

        safeRegisterCommand("holo", new HologramCommand(this));
        safeRegisterCommand("hologram", new HologramCommand(this));
    }

    private void safeRegisterCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        } else {
            getLogger().warning("§e[FoliaSkyblock] Command '" + name + "' not found in plugin.yml (skipped).");
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();

        // === Progression & XP ===
        pm.registerEvents(new IslandXPListener(this), this);
        pm.registerEvents(new ChallengeProgressListener(this), this);
        pm.registerEvents(new CobbleGeneratorListener(this, gridManager, islandUpgradeManager), this);
        // === Economy Systems ===
        pm.registerEvents(new ChestShopListener(this), this);

        // === Protection & Security ===
        pm.registerEvents(new IslandProtectionListener(this), this);
        pm.registerEvents(new AntiCheatListener(this), this);
        pm.registerEvents(new HopperDupeListener(this, antiCheatManager), this);
        // === Combat & Utilities ===
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new AnvilListener(this), this);

        // === Dimension & Island Logic ===
        pm.registerEvents(new DimensionIslandListener(this), this);

        getLogger().info("§a[FoliaSkyblock] All listeners registered successfully.");
    }
    // ==================== GETTERS ====================
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public IslandManager getIslandManager() { return islandManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public RankManager getRankManager() { return rankManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public GridManager getGridManager() { return gridManager; }
    public IslandGenerator getIslandGenerator() { return islandGenerator; }
    public IslandBankManager getIslandBankManager() { return islandBankManager; }
    public BossManager getBossManager() { return bossManager; }
    public ChatManager getChatManager() { return chatManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public MinionManager getMinionManager() { return minionManager; }
    public IslandUpgradeManager getIslandUpgradeManager() { return islandUpgradeManager; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public BazaarManager getBazaarManager() { return bazaarManager; }

    // GUI Getters (fix for SlayerCommand errors)
    public SlayerGUI getSlayerGUI() { return slayerGUI; }
    public SlayerLeaderboardGUI getSlayerLeaderboardGUI() { return slayerLeaderboardGUI; }
    public SlayerAchievementGUI getSlayerAchievementGUI() { return slayerAchievementGUI; }
    public EnchantingTableGUI getEnchantingTableGUI() { return enchantingTableGUI; }
    public ResetConfirmationGUI getResetConfirmationGUI() { return resetConfirmationGUI; }
    public BiomeSelectionGUI getBiomeSelectionGUI() { return biomeSelectionGUI; }

    public ChallengeManager getChallengeManager() { return challengeManager; }
    public QuestManager getQuestManager() { return questManager; }
    public AntiCheatManager getAntiCheatManager() { return antiCheatManager; }
    public ChestShopManager getChestShopManager() { return chestShopManager; }
    public IslandSettingsManager getIslandSettingsManager() { return islandSettingsManager; }
    public IslandRatingManager getIslandRatingManager() { return islandRatingManager; }
    public IslandWarpManager getIslandWarpManager() { return islandWarpManager; }
    public IslandUpgradeGUI getIslandUpgradeGUI() {
        return islandUpgradeGUI;
    }
    // Folia detection
    public boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
