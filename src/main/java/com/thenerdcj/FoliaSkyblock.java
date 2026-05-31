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
import com.thenerdcj.island.Island;
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

    // Island Worth / Level System (new)
    private IslandWorthManager islandWorthManager;
    private com.thenerdcj.mission.MissionManager missionManager;
    private com.thenerdcj.booster.BoosterManager boosterManager;
    private com.thenerdcj.gui.BoosterGUI boosterGUI;

    // Island Shop (deepened economy sink)
    private com.thenerdcj.manager.IslandShopManager islandShopManager;
    private com.thenerdcj.gui.IslandShopGUI islandShopGUI;

    // Prestige System (high-endgame reset for power)
    private com.thenerdcj.manager.PrestigeManager prestigeManager;
    private com.thenerdcj.gui.PrestigeGUI prestigeGUI;

    // Border / Size Upgrades + Visuals
    private com.thenerdcj.manager.BorderVisualManager borderVisualManager;

    // New systems (Visitor foundation + Crates + Minion expansion already wired in previous work)
    private com.thenerdcj.crate.CrateManager crateManager;
    private com.thenerdcj.gui.CrateGUI crateGUI;

    // Personal Particle Trails / Auras (new cosmetic system)
    private com.thenerdcj.cosmetic.ParticleTrailManager particleTrailManager;
    private com.thenerdcj.gui.ParticleTrailGUI particleTrailGUI;
    private com.thenerdcj.gui.GeneratorGUI generatorGUI;

    // Wardrobe system (added based on community research)
    private com.thenerdcj.wardrobe.WardrobeManager wardrobeManager;
    private com.thenerdcj.wardrobe.WardrobeGUI wardrobeGUI;
    private com.thenerdcj.wardrobe.WardrobeSlotOptionsGUI wardrobeSlotOptionsGUI;

    // ==================== GUI INSTANCES ====================
    private TradeGUI tradeGUI;
    private SlayerGUI slayerGUI;
    private SlayerLeaderboardGUI slayerLeaderboardGUI;
    private SlayerAchievementGUI slayerAchievementGUI;
    private com.thenerdcj.gui.SlayerShopGUI slayerShopGUI;
    private com.thenerdcj.gui.SlayerTokenLeaderboardGUI slayerTokenLeaderboardGUI;
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

        // Island Worth / Level System (classic Skyblock worth + leaderboards)
        this.islandWorthManager = new IslandWorthManager(this);
        this.missionManager = new com.thenerdcj.mission.MissionManager(this);
        this.boosterManager = new com.thenerdcj.booster.BoosterManager(this);
        this.boosterGUI = new com.thenerdcj.gui.BoosterGUI(this);

        // Island Shop (deepened)
        this.islandShopManager = new com.thenerdcj.manager.IslandShopManager(this);
        this.islandShopGUI = new com.thenerdcj.gui.IslandShopGUI(this);

        // Prestige System
        this.prestigeManager = new com.thenerdcj.manager.PrestigeManager(this);
        this.prestigeGUI = new com.thenerdcj.gui.PrestigeGUI(this);

        // Border Size + Visual Expansion
        this.borderVisualManager = new com.thenerdcj.manager.BorderVisualManager(this);

        // Crate / Loot system
        this.crateManager = new com.thenerdcj.crate.CrateManager(this);
        this.crateGUI = new com.thenerdcj.gui.CrateGUI(this);

        // Personal Particle Trails (cosmetic auras)
        this.particleTrailManager = new com.thenerdcj.cosmetic.ParticleTrailManager(this);
        this.particleTrailGUI = new com.thenerdcj.gui.ParticleTrailGUI(this);
        this.generatorGUI = new com.thenerdcj.gui.GeneratorGUI(this);

        // === Scheduled background worth recalculation (every 20 minutes) ===
        threadSafety.runRepeatingOnMainThread(() -> {
            if (islandWorthManager != null && islandManager != null) {
                for (Island island : islandManager.getAllLoadedIslands().values()) {
                    if (island != null) {
                        islandWorthManager.invalidateCache(island);
                        islandWorthManager.recalculateAndUpdate(island);
                    }
                }
                getLogger().fine("[IslandWorth] Background recalculation triggered for loaded islands.");
            }
        }, 20 * 60 * 15L, 20 * 60 * 20L);

        // === Light tab list worth display updates (every 30 seconds) ===
        threadSafety.runRepeatingOnMainThread(() -> {
            if (islandWorthManager != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    islandWorthManager.updatePlayerTabList(p);
                }
            }
        }, 20 * 30L, 20 * 30L); // every 30 seconds

        // === Wardrobe System (Armor + Equipment presets) ===
        this.wardrobeManager = new com.thenerdcj.wardrobe.WardrobeManager(this);
        this.wardrobeGUI = new com.thenerdcj.wardrobe.WardrobeGUI(this);
        this.wardrobeSlotOptionsGUI = new com.thenerdcj.wardrobe.WardrobeSlotOptionsGUI(this);

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
        this.slayerShopGUI = new com.thenerdcj.gui.SlayerShopGUI(this);
        this.slayerTokenLeaderboardGUI = new com.thenerdcj.gui.SlayerTokenLeaderboardGUI(this);
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
        safeRegisterCommand("trail", new com.thenerdcj.command.ParticleCommand(this));
        safeRegisterCommand("particles", new com.thenerdcj.command.ParticleCommand(this));
        safeRegisterCommand("auction", new AuctionCommand(this));
        safeRegisterCommand("ah", new AuctionCommand(this));
        safeRegisterCommand("bazaar", new BazaarCommand(this));
        safeRegisterCommand("minions", new MinionsCommand(this));
        safeRegisterCommand("wardrobe", new com.thenerdcj.command.WardrobeCommand(this));
        safeRegisterCommand("wd", new com.thenerdcj.command.WardrobeCommand(this));

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

        // Wardrobe persistence
        pm.registerEvents(new com.thenerdcj.listener.WardrobeListener(this), this);

        // Island Shop token redemption
        pm.registerEvents(new com.thenerdcj.listener.ShopTokenListener(this), this);

        // Slayer gear abilities
        pm.registerEvents(new com.thenerdcj.listener.SlayerGearListener(this), this);

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

    public IslandWorthManager getIslandWorthManager() { return islandWorthManager; }

    public com.thenerdcj.mission.MissionManager getMissionManager() { return missionManager; }

    public com.thenerdcj.booster.BoosterManager getBoosterManager() { return boosterManager; }

    public com.thenerdcj.gui.IslandShopGUI getIslandShopGUI() { return islandShopGUI; }

    public com.thenerdcj.manager.IslandShopManager getIslandShopManager() { return islandShopManager; }

    public com.thenerdcj.manager.PrestigeManager getPrestigeManager() { return prestigeManager; }

    public com.thenerdcj.gui.PrestigeGUI getPrestigeGUI() { return prestigeGUI; }

    public com.thenerdcj.manager.BorderVisualManager getBorderVisualManager() { return borderVisualManager; }

    public com.thenerdcj.crate.CrateManager getCrateManager() { return crateManager; }

    public com.thenerdcj.gui.CrateGUI getCrateGUI() { return crateGUI; }

    public com.thenerdcj.cosmetic.ParticleTrailManager getParticleTrailManager() { return particleTrailManager; }

    public com.thenerdcj.gui.ParticleTrailGUI getParticleTrailGUI() { return particleTrailGUI; }

    public com.thenerdcj.gui.GeneratorGUI getGeneratorGUI() { return generatorGUI; }

    public com.thenerdcj.gui.BoosterGUI getBoosterGUI() { return boosterGUI; }
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

    public com.thenerdcj.gui.SlayerShopGUI getSlayerShopGUI() { return slayerShopGUI; }

    public com.thenerdcj.gui.SlayerTokenLeaderboardGUI getSlayerTokenLeaderboardGUI() { return slayerTokenLeaderboardGUI; }
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

    // Wardrobe getters
    public com.thenerdcj.wardrobe.WardrobeManager getWardrobeManager() { return wardrobeManager; }
    public com.thenerdcj.wardrobe.WardrobeGUI getWardrobeGUI() { return wardrobeGUI; }
    public com.thenerdcj.wardrobe.WardrobeSlotOptionsGUI getWardrobeSlotOptionsGUI() { return wardrobeSlotOptionsGUI; }
}