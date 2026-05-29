package com.thenerdcj;

import com.thenerdcj.Trade.TradeGUI;
import com.thenerdcj.auction.Auction;
import com.thenerdcj.auction.AuctionGUI;
import com.thenerdcj.auction.AuctionManager;
import com.thenerdcj.bazaar.BazaarManager;
import com.thenerdcj.boss.BossManager;
import com.thenerdcj.challenge.ChallengeManager;
import com.thenerdcj.command.*;
import com.thenerdcj.island.IslandUpgradeGUI;
import com.thenerdcj.quest.QuestManager;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.gui.*;
import com.thenerdcj.hologram.HologramManager;
import com.thenerdcj.island.generator.IslandGenerator;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.listener.*;
import com.thenerdcj.manager.*;
import com.thenerdcj.rank.RankManager;
import com.thenerdcj.shop.ChestShopManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
    private HologramManager hologramManager;
    private TeleportRequestManager teleportRequestManager;
    private PunishmentManager punishmentManager;
    private AutoSellerManager autoSellerManager;
    private com.thenerdcj.util.ThreadSafety threadSafety;
    private com.thenerdcj.util.NameCache nameCache;

    // ==================== GUI INSTANCES ====================
    private TradeGUI tradeGUI;
    private SlayerGUI slayerGUI;
    private SlayerLeaderboardGUI slayerLeaderboardGUI;
    private SlayerAchievementGUI slayerAchievementGUI;
    private EnchantingTableGUI enchantingTableGUI;
    private IslandChatManager islandChatManager;
    private IslandUpgradeGUI islandUpgradeGUI;
    private ResetConfirmationGUI resetConfirmationGUI;
    private BiomeSelectionGUI biomeSelectionGUI;
    private TPAListGUI tpaListGUI;
    private AuctionGUI auctionGUI;
    private com.thenerdcj.bazaar.BazaarGUI bazaarGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // === Core Managers ===
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

        // Create ThreadSafety and NameCache VERY early so other managers can safely use them
        this.threadSafety = new com.thenerdcj.util.ThreadSafety(this);
        this.nameCache = new com.thenerdcj.util.NameCache(this);

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
        this.teleportRequestManager = new TeleportRequestManager(this);
        this.tpaListGUI = new TPAListGUI(this, teleportRequestManager);
        this.punishmentManager = new PunishmentManager(this);
        this.autoSellerManager = new AutoSellerManager(this);


        // === WorldManager (Custom Void Worlds) ===
        this.worldManager = new WorldManager(this);
        this.worldManager.initializeWorlds();

        getLogger().info("§e[WorldManager] Creating custom void worlds for Skyblock...");

        // === Hologram Manager ===
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
        this.auctionGUI = new AuctionGUI(this, auctionManager);
        this.bazaarGUI = new com.thenerdcj.bazaar.BazaarGUI(this, bazaarManager);

        // === Register Commands & Listeners ===
        registerCommands();
        registerListeners();

        // Load islands for online players
        Bukkit.getOnlinePlayers().forEach(player -> islandManager.loadPlayerIslands(player));

        getLogger().info("§a[FoliaSkyblock] Plugin enabled successfully on Folia! (v1.0.2)");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) hologramManager.cleanup();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("§e[FoliaSkyblock] Plugin disabled.");
    }

    // ==================== COMMAND REGISTRATION ====================
    private void registerCommands() {
        safeRegisterCommand("island", new IslandCommand(this));
        safeRegisterCommand("is", new IslandCommand(this));

        safeRegisterCommand("bal", new BalanceCommand(this));
        safeRegisterCommand("balance", new BalanceCommand(this));

        safeRegisterCommand("rank", new RankCommand(this));

        safeRegisterCommand("spawn", new PlayerCommand(this));
        safeRegisterCommand("home", new PlayerCommand(this)); // or IslandCommand if preferred

        // Expanded TPA
        safeRegisterCommand("tpa", new PlayerCommand(this));
        safeRegisterCommand("tpaccept", new PlayerCommand(this));
        safeRegisterCommand("tpdeny", new PlayerCommand(this));
        safeRegisterCommand("tpignore", new PlayerCommand(this));
        safeRegisterCommand("tplist", new PlayerCommand(this));
        safeRegisterCommand("pending", new PlayerCommand(this));

        // Private Messaging
        safeRegisterCommand("msg", new PlayerCommand(this));
        safeRegisterCommand("r", new PlayerCommand(this));

        // List & Help
        safeRegisterCommand("list", new PlayerCommand(this));
        safeRegisterCommand("online", new PlayerCommand(this));
        safeRegisterCommand("help", new PlayerCommand(this));

        safeRegisterCommand("rules", new PlayerCommand(this));

        safeRegisterCommand("challenge", new ChallengeCommand(this));
        safeRegisterCommand("challenges", new ChallengeCommand(this));
        safeRegisterCommand("daily", new ChallengeCommand(this));

        safeRegisterCommand("slayer", new SlayerCommand(this));
        safeRegisterCommand("enchant", new EnchantCommand(this));
        safeRegisterCommand("auction", new AuctionCommand(this));
        safeRegisterCommand("ah", new AuctionCommand(this));
        safeRegisterCommand("bazaar", new BazaarCommand(this));
        safeRegisterCommand("minions", new MinionsCommand(this));

        // Staff Commands
        StaffCommand staffCmd = new StaffCommand(this);
        safeRegisterCommand("staff", staffCmd);
        safeRegisterCommand("vanish", staffCmd);
        safeRegisterCommand("fly", staffCmd);
        safeRegisterCommand("god", staffCmd);
        safeRegisterCommand("heal", staffCmd);
        safeRegisterCommand("speed", staffCmd);
        safeRegisterCommand("gm", staffCmd);
        safeRegisterCommand("gamemode", staffCmd);
        safeRegisterCommand("tp", staffCmd);
        safeRegisterCommand("tphere", staffCmd);
        safeRegisterCommand("tppos", staffCmd);
        safeRegisterCommand("ban", staffCmd);
        safeRegisterCommand("tempban", staffCmd);
        safeRegisterCommand("kick", staffCmd);
        safeRegisterCommand("mute", staffCmd);
        safeRegisterCommand("unmute", staffCmd);
        safeRegisterCommand("warn", staffCmd);
        safeRegisterCommand("invsee", staffCmd);
        safeRegisterCommand("endersee", staffCmd);
        safeRegisterCommand("freeze", staffCmd);
        safeRegisterCommand("sc", staffCmd);
        safeRegisterCommand("staffchat", staffCmd);
        safeRegisterCommand("broadcast", staffCmd);
        safeRegisterCommand("announce", staffCmd);
        safeRegisterCommand("clear", staffCmd);
        safeRegisterCommand("repair", staffCmd);
        safeRegisterCommand("setspawn", staffCmd);
        safeRegisterCommand("isadmin", new AdminCommand(this));

        // Trade GUI
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
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                cmd.setTabCompleter(tabCompleter);
            }
        } else {
            getLogger().warning("§e[FoliaSkyblock] Command '" + name + "' not found in plugin.yml.");
        }
    }

    // ==================== LISTENER REGISTRATION ====================
    private void registerListeners() {
        var pm = getServer().getPluginManager();

        pm.registerEvents(new IslandXPListener(this), this);
        pm.registerEvents(new ChallengeProgressListener(this), this);
        pm.registerEvents(new CobbleGeneratorListener(this, gridManager, islandUpgradeManager), this);
        pm.registerEvents(new com.thenerdcj.listener.CropGrowthListener(this, islandUpgradeManager), this);
        pm.registerEvents(new ChestShopListener(this), this);
        pm.registerEvents(new IslandProtectionListener(this), this);
        pm.registerEvents(new AntiCheatListener(this), this);
        pm.registerEvents(new HopperDupeListener(this, antiCheatManager), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new AnvilListener(this), this);
        pm.registerEvents(new DimensionIslandListener(this), this);
        pm.registerEvents(new TPAListener(this, tpaListGUI), this);
        // Register the single canonical AuctionGUI instance (prevents double-listener bug)
        pm.registerEvents(auctionGUI, this);

        // Player Quit Listener for ChatManager & TPA
        pm.registerEvents(new PlayerQuitListener(this), this); // Make sure this exists or add it

        getLogger().info("§a[FoliaSkyblock] All listeners registered.");
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
    public AntiCheatManager getAntiCheatManager() { return antiCheatManager; }
    public ChestShopManager getChestShopManager() { return chestShopManager; }
    public IslandSettingsManager getIslandSettingsManager() { return islandSettingsManager; }
    public IslandRatingManager getIslandRatingManager() { return islandRatingManager; }
    public IslandWarpManager getIslandWarpManager() { return islandWarpManager; }
    public ChallengeManager getChallengeManager() { return challengeManager; }
    public QuestManager getQuestManager() { return questManager; }
    public TeleportRequestManager getTeleportRequestManager() { return teleportRequestManager; }

    // GUI Getters
    public SlayerGUI getSlayerGUI() { return slayerGUI; }
    public SlayerLeaderboardGUI getSlayerLeaderboardGUI() { return slayerLeaderboardGUI; }
    public SlayerAchievementGUI getSlayerAchievementGUI() { return slayerAchievementGUI; }
    public EnchantingTableGUI getEnchantingTableGUI() { return enchantingTableGUI; }
    public ResetConfirmationGUI getResetConfirmationGUI() { return resetConfirmationGUI; }
    public BiomeSelectionGUI getBiomeSelectionGUI() { return biomeSelectionGUI; }
    public IslandUpgradeGUI getIslandUpgradeGUI() { return islandUpgradeGUI; }
    public TradeGUI getTradeGUI() { return tradeGUI; }

    // ==================== HELPERS ====================
    public World getSkyblockWorld(World.Environment environment) {
        if (worldManager == null) return null;
        return switch (environment) {
            case NORMAL -> Bukkit.getWorld("skyblock");
            case NETHER -> Bukkit.getWorld("skyblock_nether");
            case THE_END -> Bukkit.getWorld("skyblock_end");
            default -> null;
        };
    }

    public boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public AutoSellerManager getAutoSellerManager() {
        return autoSellerManager;
    }

    public com.thenerdcj.util.ThreadSafety getThreadSafety() {
        return threadSafety;
    }

    public com.thenerdcj.util.NameCache getNameCache() {
        return nameCache;
    }

    public AuctionGUI getAuctionGUI() { return auctionGUI; }

    public com.thenerdcj.bazaar.BazaarGUI getBazaarGUI() { return bazaarGUI; }
}