package com.thenerdcj;

import com.thenerdcj.trade.TradeGUI;
import com.thenerdcj.auction.Auction;
import com.thenerdcj.auction.AuctionGUI;
import com.thenerdcj.auction.AuctionManager;
import com.thenerdcj.bazaar.BazaarManager;
import com.thenerdcj.bazaar.BazaarGUI;
import com.thenerdcj.boss.BossManager;
import com.thenerdcj.challenge.ChallengeManager;
import com.thenerdcj.command.*;
import com.thenerdcj.island.IslandUpgradeGUI;
import com.thenerdcj.quest.QuestManager;
import com.thenerdcj.database.DatabaseManager;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.gui.*;
import com.thenerdcj.hologram.HologramManager;
import com.thenerdcj.island.generator.IslandGenerator;
import com.thenerdcj.island.generator.IslandOreGenerator;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.listener.*;
import com.thenerdcj.manager.*;
import com.thenerdcj.rank.RankManager;
import com.thenerdcj.shop.ChestShopManager;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.ThreadSafety;
import com.thenerdcj.util.NameCache;

import com.thenerdcj.mission.MissionManager;
import com.thenerdcj.booster.BoosterManager;
import com.thenerdcj.crate.CrateManager;
import com.thenerdcj.cosmetic.*;
import com.thenerdcj.wardrobe.*;
import com.thenerdcj.pets.*;
import com.thenerdcj.tags.*;
import com.thenerdcj.wings.*;
import com.thenerdcj.runes.*;
import com.thenerdcj.season.SeasonManager;
import com.thenerdcj.enchant.EnchantmentManager;
import com.thenerdcj.skills.PlayerSkillManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.thenerdcj.island.generator.VoidChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.Nullable;

public class FoliaSkyblock extends JavaPlugin {

    @Override
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return new VoidChunkGenerator();
    }

    private DatabaseManager databaseManager;private GridManager gridManager;private IslandManager islandManager;private EconomyManager economyManager;private RankManager rankManager;private ChallengeManager challengeManager;private QuestManager questManager;private QuestLogGUI questLogGUI;private QuestDetailGUI questDetailGUI;private BossManager bossManager;private AntiCheatManager antiCheatManager;private IslandUpgradeManager islandUpgradeManager;private MinionManager minionManager;private IslandSettingsManager islandSettingsManager;private IslandBankManager islandBankManager;private IslandRatingManager islandRatingManager;private IslandWarpManager islandWarpManager;private ChestShopManager chestShopManager;private AuctionManager auctionManager;private BazaarManager bazaarManager;private ChatManager chatManager;private WorldManager worldManager;private IslandGenerator islandGenerator;private IslandOreGenerator islandOreGenerator;private HologramManager hologramManager;private HologramEditorGUI hologramEditorGUI;private SuggestionManager suggestionManager;private TeleportRequestManager teleportRequestManager;private PunishmentManager punishmentManager;private BugReportManager bugReportManager;private BugReportListGUI bugReportListGUI;private AutoSellerManager autoSellerManager;private ThreadSafety threadSafety;private NameCache nameCache;

    private IslandWorthManager islandWorthManager;private MissionManager missionManager;private BoosterManager boosterManager;private BoosterGUI boosterGUI;private IslandShopManager islandShopManager;private IslandShopGUI islandShopGUI;private IslandBankGUI islandBankGUI;private IslandSettingsGUI islandSettingsGUI;private PrestigeManager prestigeManager;private PrestigeGUI prestigeGUI;private BorderVisualManager borderVisualManager;private CrateManager crateManager;private CrateGUI crateGUI;private ParticleTrailManager particleTrailManager;private ParticleTrailGUI particleTrailGUI;private GeneratorGUI generatorGUI;private AdminIslandInspectGUI adminIslandInspectGUI;private SpawnEditGUI spawnEditGUI;private WardrobeManager wardrobeManager;private WardrobeGUI wardrobeGUI;private WardrobeSlotOptionsGUI wardrobeSlotOptionsGUI;private PetManager petManager;private PetGUI petGUI;private PlayerTagManager playerTagManager;private TagGUI tagGUI;private com.thenerdcj.cosmetic.JoinLeaveMessageManager joinLeaveMessageManager;private com.thenerdcj.cosmetic.NameColorManager nameColorManager;private com.thenerdcj.cosmetic.JoinLeaveMessageMainGUI joinLeaveMessageMainGUI;private PlayerNametagManager playerNametagManager;private ElytraWingManager elytraWingManager;private WingGUI wingGUI;private RuneManager runeManager;private RuneGUI runeGUI;private HelmetSkinManager helmetSkinManager;private HelmetSkinGUI helmetSkinGUI;private DeathEffectManager deathEffectManager;private DeathEffectGUI deathEffectGUI;private DeathMessageManager deathMessageManager;
    private DeathMessageGUI deathMessageGUI;private BackpackSkinManager backpackSkinManager;private BackpackSkinGUI backpackSkinGUI;private PowerOrbSkinManager powerOrbSkinManager;private PowerOrbSkinGUI powerOrbSkinGUI;private MinionSkinManager minionSkinManager;private MinionSkinGUI minionSkinGUI;private IslandFurnitureManager islandFurnitureManager;private IslandFurnitureGUI islandFurnitureGUI;private IslandMusicManager islandMusicManager;private IslandMusicGUI islandMusicGUI;private OverheadCosmeticManager overheadCosmeticManager;private EmoteCosmeticManager emoteCosmeticManager;private IslandStructureManager islandStructureManager;private IslandStructureGUI islandStructureGUI;
    private AFKManager afkManager;

    private ChatBubbleCosmeticManager chatBubbleCosmeticManager;private IslandWeatherCosmeticManager islandWeatherCosmeticManager;private IslandWeatherGUI islandWeatherGUI;private AccessoryCosmeticManager accessoryCosmeticManager;private CollectionManager collectionManager;private CollectionsGUI collectionsGUI;private SeasonManager seasonManager;private MuseumManager museumManager;private MuseumGUI museumGUI;private PlayerSkillManager playerSkillManager;private SkillGUI skillGUI;private IslandTopGUI islandTopGUI;private IslandBrowseGUI islandBrowseGUI;private TradeGUI tradeGUI;private SlayerGUI slayerGUI;private SlayerLeaderboardGUI slayerLeaderboardGUI;private SlayerAchievementGUI slayerAchievementGUI;private SlayerShopGUI slayerShopGUI;private SlayerTokenLeaderboardGUI slayerTokenLeaderboardGUI;private EnchantingTableGUI enchantingTableGUI;private EnchantmentManager enchantmentManager;private IslandChatManager islandChatManager;private IslandUpgradeGUI islandUpgradeGUI;private ResetConfirmationGUI resetConfirmationGUI;private BiomeSelectionGUI biomeSelectionGUI;private TPAListGUI tpaListGUI;private AuctionGUI auctionGUI;private BazaarGUI bazaarGUI;private DimensionResetGUI dimensionResetGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureBlockWorthDefaults();
        validateConfiguration();

        // Note: For 1.21+ pause menu "Server Links", configure via server.properties or use Paper's ServerLinks API manually if desired.
        // Our fancy in-game /discord (chat + book + clickable) is the primary player-friendly way (works on Folia/Paper).
        String discordLinkForInfo = getConfig().getString("social.discord.link", "https://discord.gg/UdpSpPhEp5");
        getLogger().info("[FoliaSkyblock] Discord invite ready at: " + discordLinkForInfo + " (use /discord in-game for fancy clickable version)");

        this.threadSafety = new ThreadSafety(this);this.nameCache = new NameCache(this);this.databaseManager = new DatabaseManager(this);databaseManager.initDatabase();this.gridManager = new GridManager(this);this.islandGenerator = new IslandGenerator(this);this.islandManager = new IslandManager(this);this.economyManager = new EconomyManager(this);this.rankManager = new RankManager(this);this.challengeManager = new ChallengeManager(this);this.questManager = new QuestManager(this);Bukkit.getPluginManager().registerEvents(questManager, this);this.questLogGUI = new QuestLogGUI(this);this.questDetailGUI = new QuestDetailGUI(this);this.bossManager = new BossManager(this);this.antiCheatManager = new AntiCheatManager(this);

        this.islandUpgradeManager = new IslandUpgradeManager(this);this.islandOreGenerator = new IslandOreGenerator(this, gridManager, islandUpgradeManager);this.islandSettingsManager = new IslandSettingsManager(this);
        this.islandBankManager = new IslandBankManager(this);this.islandRatingManager = new IslandRatingManager(this);this.islandWarpManager = new IslandWarpManager(this);this.minionManager = new MinionManager(this);this.chestShopManager = new ChestShopManager(this);this.auctionManager = new AuctionManager(this);this.bazaarManager = new BazaarManager(this);this.chatManager = new ChatManager(this);this.teleportRequestManager = new TeleportRequestManager(this);this.tpaListGUI = new TPAListGUI(this, teleportRequestManager);this.punishmentManager = new PunishmentManager(this);this.bugReportManager = new BugReportManager(this);this.bugReportListGUI = new BugReportListGUI(this);getServer().getPluginManager().registerEvents(this.bugReportListGUI, this);this.autoSellerManager = new AutoSellerManager(this);this.islandWorthManager = new IslandWorthManager(this);this.missionManager = new MissionManager(this);this.boosterManager = new BoosterManager(this);this.boosterGUI = new BoosterGUI(this);this.islandShopManager = new IslandShopManager(this);this.islandShopGUI = new IslandShopGUI(this);this.islandBankGUI = new IslandBankGUI(this);this.islandSettingsGUI = new IslandSettingsGUI(this);this.prestigeManager = new PrestigeManager(this);this.prestigeGUI = new PrestigeGUI(this);this.borderVisualManager = new BorderVisualManager(this);this.crateManager = new CrateManager(this);this.crateGUI = new CrateGUI(this);this.particleTrailManager = new ParticleTrailManager(this);this.particleTrailGUI = new ParticleTrailGUI(this);this.generatorGUI = new GeneratorGUI(this);this.adminIslandInspectGUI = new AdminIslandInspectGUI(this);this.spawnEditGUI = new SpawnEditGUI(this);
        this.afkManager = new AFKManager(this);

        long worthIntervalMin = (islandWorthManager != null && islandWorthManager.isPeriodicRecalcEnabled())
                ? islandWorthManager.getWorthRecalcIntervalMinutes() : 0;
        long worthDelay = (worthIntervalMin > 0) ? (20 * 60 * Math.max(1, worthIntervalMin)) : (20 * 60 * 60);
        if (worthIntervalMin > 0) {
            threadSafety.runRepeatingOnMainThread(() -> {
                if (islandWorthManager != null && islandManager != null) {
                    long start = System.nanoTime();
                    int cap = islandWorthManager.getMaxIslandsPerRecalcTick();
                    int count = 0;
                    for (Island island : islandManager.getAllLoadedIslands().values()) {
                        if (island != null) {
                            if (cap > 0 && count >= cap) break;
                            org.bukkit.Location center = island.getCenter(null);
                            GridPosition gpForLog = island.getGridPosition();
                            if (center != null && center.getWorld() != null) {
                                threadSafety.runAtLocation(center, () -> {
                                    islandWorthManager.invalidateCache(island);
                                    islandWorthManager.recalculateAndUpdate(island);
                                });
                            } else {
                                islandWorthManager.invalidateCache(island);
                                islandWorthManager.recalculateAndUpdate(island);
                            }
                            count++;
                        }
                    }
                    long ns = System.nanoTime() - start;
                    if (ns > 1_000_000L && islandWorthManager.isProfileHotPaths()) {
                        getLogger().info("[FoliaSkyblock] PROFILE: worth recalc loop took " + (ns / 1_000_000.0) + " ms (capped at " + cap + ")");
                    }
                }
            }, worthDelay, worthDelay);
        }

        threadSafety.runRepeatingOnMainThread(() -> {
            if (islandWorthManager != null) {
                long start = System.nanoTime();
                int playerCap = getConfig().getInt("island.perf.max-players-for-tab-update", 200);
                int c = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (playerCap > 0 && c >= playerCap) break;
                    islandWorthManager.updatePlayerTabList(p);
                    c++;
                }
                long ns = System.nanoTime() - start;
                if (ns > 1_000_000L && islandWorthManager.isProfileHotPaths()) {
                    getLogger().info("[FoliaSkyblock] PROFILE: tab list update took " + (ns / 1_000_000.0) + " ms (capped at " + playerCap + " players for large scale)");
                }
            }
        }, 20 * 30L, 20 * 30L);

        threadSafety.runRepeatingOnMainThread(() -> {
            if (economyManager != null && islandManager != null) {
                if (getConfig().getBoolean("island.upkeep.enabled", false)) {
                    long start = System.nanoTime();
                    int cap = (islandWorthManager != null) ? islandWorthManager.getMaxIslandsPerRecalcTick() : 50;
                    int count = 0;
                    for (Island island : islandManager.getAllLoadedIslands().values()) {
                        if (island != null) {
                            if (cap > 0 && count >= cap) break;
                            GridPosition gp = island.getGridPosition();
                            org.bukkit.Location center = island.getCenter(null);
                            if (center != null) {
                                threadSafety.runAtLocation(center, () -> economyManager.applyIslandUpkeepTax(gp));
                            } else {
                                economyManager.applyIslandUpkeepTax(gp);
                            }
                            count++;
                        }
                    }
                    long ns = System.nanoTime() - start;
                    if (ns > 1_000_000L && islandWorthManager != null && islandWorthManager.isProfileHotPaths()) {
                        getLogger().info("[FoliaSkyblock] PROFILE: tax upkeep loop took " + (ns / 1_000_000.0) + " ms (capped at " + cap + " for large scale)");
                    }
                }
            }
        }, 20L * 60 * 60, 20L * 60 * 60); // hourly check

        threadSafety.runRepeatingOnMainThread(() -> {
            if (bossManager != null) {
                long start = 0;
                if (islandWorthManager != null && islandWorthManager.isProfileHotPaths()) start = System.nanoTime();
                bossManager.checkAndResetTokenLeaderboard();
                if (start != 0) {
                    long ns = System.nanoTime() - start;
                    if (ns > 1_000_000L && islandWorthManager.isProfileHotPaths()) {
                        getLogger().info("[FoliaSkyblock] PROFILE: weekly token leaderboard reset took " + (ns / 1_000_000.0) + " ms");
                    }
                }
                if (islandManager != null) {
                    int sCount = 0; int sMax = 3;
                    for (Island is : islandManager.getAllLoadedIslands().values()) {
                        if (sCount++ >= sMax) break;
                        org.bukkit.Location c = is.getCenter(null);
                        if (c != null) {
                            threadSafety.runAtLocation(c, () -> {
                                if (islandWorthManager != null && islandWorthManager.isProfileHotPaths()) {
                                    getLogger().fine("[FoliaSkyblock] Staggered region work for weekly leaderboard/island (example)");
                                }
                            });
                        }
                    }
                }
            }
        }, 20 * 60 * 5L, 20 * 60 * 5L);

        threadSafety.runRepeatingOnMainThread(() -> {
            if (islandRatingManager != null) {
                long start = 0;
                if (islandWorthManager != null && islandWorthManager.isProfileHotPaths()) start = System.nanoTime();
                islandRatingManager.getTopRatedIslands(5).thenAccept(tops -> {
                    if (islandManager != null) {
                        int staggerCount = 0;
                        int maxStagger = islandWorthManager != null ? Math.min(5, islandWorthManager.getMaxIslandsPerRecalcTick()) : 3;
                        for (GridPosition pos : tops.keySet()) {
                            if (staggerCount++ >= maxStagger) break;
                            org.bukkit.Location exampleCenter = new org.bukkit.Location(
                                org.bukkit.Bukkit.getWorlds().isEmpty() ? null : org.bukkit.Bukkit.getWorlds().get(0), 
                                pos.x() * 512 + 256, 100, pos.z() * 512 + 256
                            );
                            if (exampleCenter.getWorld() != null) {
                                getThreadSafety().runAtLocation(exampleCenter, () -> {
                                    if (islandWorthManager != null && islandWorthManager.isProfileHotPaths()) {
                                        getLogger().fine("[FoliaSkyblock] Staggered per-island region work for tops (pos=" + pos + ")");
                                    }
                                });
                            }
                        }
                    }
                });
                islandRatingManager.getTopRatedIslands(5, 5).thenAccept(page2Tops -> {});
                if (islandWorthManager != null) {
                    islandWorthManager.getTopIslandsByWorth(20, 0);
                    islandWorthManager.getTopIslandsByLevel(10, 0);
                    islandWorthManager.getTopIslandsByMemberCount(10, 0);
                    islandWorthManager.refreshRankSnapshotsFromTops();
                }

                if (start != 0) {
                    long ns = System.nanoTime() - start;
                    if (ns > 1_000_000L && islandWorthManager.isProfileHotPaths()) {
                        getLogger().info("[FoliaSkyblock] PROFILE: global top rated islands query took " + (ns / 1_000_000.0) + " ms (use Region stagger for 1000+)");
                    }
                }
            }
        }, 20 * 60 * 10L, 20 * 60 * 10L);

        threadSafety.runRepeatingOnMainThread(() -> { if (islandWorthManager != null) { islandWorthManager.refreshRankSnapshotsFromTops(); islandWorthManager.backfillMissingRankSnapshots(100); } }, 20 * 60 * 2L, 20 * 60 * 30L);

        this.wardrobeManager = new WardrobeManager(this);this.wardrobeGUI = new WardrobeGUI(this);this.wardrobeSlotOptionsGUI = new WardrobeSlotOptionsGUI(this);this.petManager = new PetManager(this);this.petGUI = new PetGUI(this);this.playerTagManager = new PlayerTagManager(this);this.tagGUI = new TagGUI(this);this.joinLeaveMessageManager = new com.thenerdcj.cosmetic.JoinLeaveMessageManager(this);this.nameColorManager = new com.thenerdcj.cosmetic.NameColorManager(this);this.joinLeaveMessageMainGUI = new com.thenerdcj.cosmetic.JoinLeaveMessageMainGUI(this);this.playerNametagManager = new PlayerNametagManager(this);this.elytraWingManager = new ElytraWingManager(this);this.wingGUI = new WingGUI(this);this.runeManager = new RuneManager(this);this.runeGUI = new RuneGUI(this);this.helmetSkinManager = new HelmetSkinManager(this);this.helmetSkinGUI = new HelmetSkinGUI(this);this.deathEffectManager = new DeathEffectManager(this);this.deathEffectGUI = new DeathEffectGUI(this);this.deathMessageManager = new DeathMessageManager(this);this.deathMessageGUI = new DeathMessageGUI(this);this.backpackSkinManager = new BackpackSkinManager(this);this.backpackSkinGUI = new BackpackSkinGUI(this);this.powerOrbSkinManager = new PowerOrbSkinManager(this);this.powerOrbSkinGUI = new PowerOrbSkinGUI(this);this.minionSkinManager = new MinionSkinManager(this);
        this.minionSkinGUI = new MinionSkinGUI(this);this.islandFurnitureManager = new IslandFurnitureManager(this);this.islandFurnitureGUI = new IslandFurnitureGUI(this);this.islandMusicManager = new IslandMusicManager(this);this.islandMusicGUI = new IslandMusicGUI(this);this.overheadCosmeticManager = new OverheadCosmeticManager(this);this.emoteCosmeticManager = new EmoteCosmeticManager(this);this.islandStructureManager = new IslandStructureManager(this);this.islandStructureGUI = new IslandStructureGUI(this);this.chatBubbleCosmeticManager = new ChatBubbleCosmeticManager(this);this.islandWeatherCosmeticManager = new IslandWeatherCosmeticManager(this);this.islandWeatherGUI = new IslandWeatherGUI(this);this.accessoryCosmeticManager = new AccessoryCosmeticManager(this);this.collectionManager = new CollectionManager(this);this.collectionsGUI = new CollectionsGUI(this);this.seasonManager = new SeasonManager(this);this.museumManager = new MuseumManager(this);this.museumGUI = new MuseumGUI(this);this.playerSkillManager = new PlayerSkillManager(this);this.skillGUI = new SkillGUI(this);this.islandTopGUI = new IslandTopGUI(this);this.islandBrowseGUI = new IslandBrowseGUI(this);

        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.DeathEffectListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.ChatBubbleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.EmoteTriggerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.CollectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.SkillListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.EarlyGameListener(this), this);

        this.worldManager = new WorldManager(this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.SpawnJoinListener(this), this);
        this.worldManager.initializeWorlds();
        MessageUtil.info(getLogger(), "§e[WorldManager] Creating custom void worlds for Skyblock...");

        this.hologramManager = new HologramManager(this);this.hologramEditorGUI = new HologramEditorGUI(this);this.suggestionManager = new SuggestionManager(this);this.tradeGUI = new TradeGUI(this);this.slayerGUI = new SlayerGUI(this);this.slayerLeaderboardGUI = new SlayerLeaderboardGUI(this);this.slayerAchievementGUI = new SlayerAchievementGUI(this);this.slayerShopGUI = new SlayerShopGUI(this);this.slayerTokenLeaderboardGUI = new SlayerTokenLeaderboardGUI(this);this.enchantingTableGUI = new EnchantingTableGUI(this);this.enchantmentManager = new EnchantmentManager(this);this.islandChatManager = new IslandChatManager(this);this.resetConfirmationGUI = new ResetConfirmationGUI(this);this.biomeSelectionGUI = new BiomeSelectionGUI(this);this.islandUpgradeGUI = new IslandUpgradeGUI(this);this.auctionGUI = new AuctionGUI(this, auctionManager);this.bazaarGUI = new BazaarGUI(this, bazaarManager);this.dimensionResetGUI = new DimensionResetGUI(this);

        registerCommands();
        registerListeners();
        Bukkit.getOnlinePlayers().forEach(player -> islandManager.loadPlayerIslands(player));
        setServerTabHeaderFooter();

        MessageUtil.info(getLogger(), "§a[FoliaSkyblock] Plugin enabled successfully on Folia! (v1.0.2)");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) hologramManager.cleanup();
        if (chestShopManager != null) chestShopManager.flushCoalescedShopSaves();
        if (databaseManager != null) databaseManager.close();
        MessageUtil.info(getLogger(), "§e[FoliaSkyblock] Plugin disabled.");
    }

    private void registerCommands() {
        safeRegisterCommand("island", new IslandCommand(this));
        safeRegisterCommand("is", new IslandCommand(this));
        safeRegisterCommand("bal", new BalanceCommand(this));
        safeRegisterCommand("balance", new BalanceCommand(this));
        safeRegisterCommand("rank", new RankCommand(this));
        safeRegisterCommand("spawn", new PlayerCommand(this));
        safeRegisterCommand("home", new PlayerCommand(this));

        safeRegisterCommand("tpa", new PlayerCommand(this));safeRegisterCommand("tpaccept", new PlayerCommand(this));safeRegisterCommand("tpdeny", new PlayerCommand(this));safeRegisterCommand("tpignore", new PlayerCommand(this));safeRegisterCommand("tplist", new PlayerCommand(this));safeRegisterCommand("pending", new PlayerCommand(this));safeRegisterCommand("msg", new PlayerCommand(this));safeRegisterCommand("r", new PlayerCommand(this));safeRegisterCommand("list", new PlayerCommand(this));safeRegisterCommand("online", new PlayerCommand(this));
        safeRegisterCommand("ic", (sender, cmd, label, args) -> { if (sender instanceof Player p && getChatManager() != null) getChatManager().toggleIslandChat(p); return true; });
        safeRegisterCommand("discord", new DiscordCommand(this));safeRegisterCommand("dc", new DiscordCommand(this));safeRegisterCommand("disc", new DiscordCommand(this));safeRegisterCommand("help", new PlayerCommand(this));safeRegisterCommand("rules", new PlayerCommand(this));safeRegisterCommand("challenge", new ChallengeCommand(this));safeRegisterCommand("challenges", new ChallengeCommand(this));safeRegisterCommand("daily", new ChallengeCommand(this));safeRegisterCommand("slayer", new SlayerCommand(this));safeRegisterCommand("enchant", new EnchantCommand(this));safeRegisterCommand("trail", new ParticleCommand(this));safeRegisterCommand("particles", new ParticleCommand(this));safeRegisterCommand("auction", new AuctionCommand(this));safeRegisterCommand("ah", new AuctionCommand(this));safeRegisterCommand("bazaar", new BazaarCommand(this));safeRegisterCommand("minions", new MinionsCommand(this));safeRegisterCommand("wardrobe", new WardrobeCommand(this));safeRegisterCommand("wd", new WardrobeCommand(this));safeRegisterCommand("pets", new PetCommand(this));safeRegisterCommand("tags", new TagCommand(this));safeRegisterCommand("joinleave", new com.thenerdcj.command.JoinLeaveMessageCommand(this));safeRegisterCommand("wings", new WingsCommand(this));safeRegisterCommand("runes", new RunesCommand(this));safeRegisterCommand("deatheffects", new DeathEffectCommand(this));safeRegisterCommand("death", new DeathEffectCommand(this));safeRegisterCommand("deathmessages", new DeathMessageCommand(this));safeRegisterCommand("deathmessage", new DeathMessageCommand(this));safeRegisterCommand("killmessages", new DeathMessageCommand(this));safeRegisterCommand("backpackskins", new BackpackSkinCommand(this));safeRegisterCommand("backpacks", new BackpackSkinCommand(this));safeRegisterCommand("powerorbskins", new PowerOrbSkinCommand(this));safeRegisterCommand("orbskins", new PowerOrbSkinCommand(this));safeRegisterCommand("minionskins", new MinionSkinCommand(this));safeRegisterCommand("minionskin", new MinionSkinCommand(this));safeRegisterCommand("furniture", new IslandFurnitureCommand(this));safeRegisterCommand("housing", new IslandFurnitureCommand(this));safeRegisterCommand("decor", new IslandFurnitureCommand(this));safeRegisterCommand("music", new IslandMusicCommand(this));safeRegisterCommand("ambience", new IslandMusicCommand(this));safeRegisterCommand("sounds", new IslandMusicCommand(this));safeRegisterCommand("emotes", new EmoteCosmeticCommand(this));safeRegisterCommand("emote", new EmoteCosmeticCommand(this));safeRegisterCommand("structures", new IslandStructureCommand(this));safeRegisterCommand("structure", new IslandStructureCommand(this));safeRegisterCommand("chatbubbles", new ChatBubbleCommand(this));safeRegisterCommand("chatbubble", new ChatBubbleCommand(this));safeRegisterCommand("bubble", new ChatBubbleCommand(this));safeRegisterCommand("weather", new IslandWeatherCommand(this));safeRegisterCommand("islandweather", new IslandWeatherCommand(this));safeRegisterCommand("weathereffects", new IslandWeatherCommand(this));

        safeRegisterCommand("accessories", new AccessoryCommand(this));safeRegisterCommand("accessory", new AccessoryCommand(this));safeRegisterCommand("collections", new CollectionCommand(this));safeRegisterCommand("collection", new CollectionCommand(this));safeRegisterCommand("skills", new SkillCommand(this));safeRegisterCommand("skill", new SkillCommand(this));var questsExecutor = (org.bukkit.command.CommandExecutor) (sender, cmd, label, args) -> { if (sender instanceof Player player) { com.thenerdcj.island.Island island = getIslandManager().getIslandForPlayer(player); String islandId = (island != null) ? island.getId() : player.getUniqueId().toString(); if (questLogGUI != null) { questLogGUI.open(player, islandId); } if (questManager != null) { questManager.generateOnboardingQuests(islandId);
                    questManager.generateDailyQuests(islandId);
                    questManager.generateWeeklyQuests(islandId);
                }
                return true;
            }
            sender.sendMessage(MessageUtil.legacy("§cThis command can only be used by players."));
            return false;
        };
        safeRegisterCommand("quests", questsExecutor);
        safeRegisterCommand("quest", questsExecutor);
        safeRegisterCommand("daily", questsExecutor);
        safeRegisterCommand("dailies", questsExecutor);

        safeRegisterCommand("overhead", new OverheadCosmeticCommand(this));safeRegisterCommand("nametag", new NametagCommand(this));StaffCommand staffCmd = new StaffCommand(this);safeRegisterCommand("staff", staffCmd);safeRegisterCommand("vanish", staffCmd);safeRegisterCommand("fly", staffCmd);safeRegisterCommand("god", staffCmd);safeRegisterCommand("heal", staffCmd);safeRegisterCommand("speed", staffCmd);safeRegisterCommand("gm", staffCmd);safeRegisterCommand("gamemode", staffCmd);safeRegisterCommand("gmc", staffCmd);safeRegisterCommand("gms", staffCmd);safeRegisterCommand("gma", staffCmd);
        safeRegisterCommand("gmsp", staffCmd);
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
        safeRegisterCommand("day", staffCmd);
        safeRegisterCommand("night", staffCmd);
        safeRegisterCommand("setspawn", staffCmd);
        safeRegisterCommand("setrank", staffCmd);
        safeRegisterCommand("isadmin", new AdminCommand(this));

        safeRegisterCommand("bug", new BugReportCommand(this));safeRegisterCommand("bugreport", new BugReportCommand(this));safeRegisterCommand("reportbug", new BugReportCommand(this));safeRegisterCommand("reports", new BugReportCommand(this));safeRegisterCommand("afk", new com.thenerdcj.command.AFKCommand(this));safeRegisterCommand("trade", (sender, cmd, label, args) -> {
            if (sender instanceof Player player) {
                tradeGUI.openTradeGUI(player);
                return true;
            }
            sender.sendMessage(MessageUtil.legacy("§cThis command can only be used by players."));
            return false;
        });

        safeRegisterCommand("holo", new HologramCommand(this));
        safeRegisterCommand("hologram", new HologramCommand(this));
        safeRegisterCommand("suggest", new com.thenerdcj.command.SuggestCommand(this));
        safeRegisterCommand("suggestion", new com.thenerdcj.command.SuggestCommand(this));
    }

    private void safeRegisterCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                cmd.setTabCompleter(tabCompleter);
            }
        } else {
            MessageUtil.warning(getLogger(), "§e[FoliaSkyblock] Command '" + name + "' not found in plugin.yml.");
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();

        pm.registerEvents(new IslandXPListener(this), this);
        pm.registerEvents(new ChallengeProgressListener(this), this);
        pm.registerEvents(islandOreGenerator, this);
        pm.registerEvents(new com.thenerdcj.listener.CropGrowthListener(this, islandUpgradeManager), this);
        pm.registerEvents(new ChestShopListener(this), this);
        pm.registerEvents(new IslandProtectionListener(this), this);
        pm.registerEvents(new AntiCheatListener(this), this);
        pm.registerEvents(new HopperDupeListener(this, antiCheatManager), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new AnvilListener(this), this);
        pm.registerEvents(new com.thenerdcj.listener.EnchantEffectListener(this), this);
        pm.registerEvents(new DimensionIslandListener(this), this);
        pm.registerEvents(new TPAListener(this, tpaListGUI), this);
        pm.registerEvents(new com.thenerdcj.listener.AFKListener(this), this);
        pm.registerEvents(auctionGUI, this);
        if (boosterGUI != null) {
            pm.registerEvents(boosterGUI, this);
        }
        if (prestigeGUI != null) {
            pm.registerEvents(prestigeGUI, this);
        }
        if (joinLeaveMessageMainGUI != null) {
            pm.registerEvents(joinLeaveMessageMainGUI, this);
        }
        pm.registerEvents(new com.thenerdcj.listener.WardrobeListener(this), this);
        pm.registerEvents(new com.thenerdcj.listener.ShopTokenListener(this), this);
        pm.registerEvents(new com.thenerdcj.listener.SlayerGearListener(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);
        pm.registerEvents(new com.thenerdcj.listener.ElytraWingListener(this), this);
        pm.registerEvents(new com.thenerdcj.listener.RuneEffectListener(this), this);
        pm.registerEvents(new com.thenerdcj.listener.PowerOrbUseListener(this), this);
        pm.registerEvents(new com.thenerdcj.listener.IslandFurnitureListener(this), this);

        MessageUtil.info(getLogger(), "§a[FoliaSkyblock] All listeners registered.");
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public IslandManager getIslandManager() { return islandManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public RankManager getRankManager() { return rankManager; }
    public GridManager getGridManager() { return gridManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public HologramEditorGUI getHologramEditorGUI() { return hologramEditorGUI; }
    public IslandGenerator getIslandGenerator() { return islandGenerator; }
    public IslandBankManager getIslandBankManager() { return islandBankManager; }
    public BossManager getBossManager() { return bossManager; }
    public ChatManager getChatManager() { return chatManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public MinionManager getMinionManager() { return minionManager; }
    public IslandUpgradeManager getIslandUpgradeManager() { return islandUpgradeManager; }
    public IslandOreGenerator getIslandOreGenerator() { return islandOreGenerator; }

    public IslandWorthManager getIslandWorthManager() { return islandWorthManager; }
    public AFKManager getAfkManager() { return afkManager; }
    public com.thenerdcj.mission.MissionManager getMissionManager() { return missionManager; }
    public com.thenerdcj.booster.BoosterManager getBoosterManager() { return boosterManager; }
    public com.thenerdcj.gui.IslandShopGUI getIslandShopGUI() { return islandShopGUI; }
    public com.thenerdcj.gui.IslandBankGUI getIslandBankGUI() { return islandBankGUI; }
    public com.thenerdcj.gui.IslandSettingsGUI getIslandSettingsGUI() { return islandSettingsGUI; }
    public com.thenerdcj.manager.IslandShopManager getIslandShopManager() { return islandShopManager; }
    public com.thenerdcj.manager.PrestigeManager getPrestigeManager() { return prestigeManager; }
    public com.thenerdcj.gui.PrestigeGUI getPrestigeGUI() { return prestigeGUI; }
    public com.thenerdcj.manager.BorderVisualManager getBorderVisualManager() { return borderVisualManager; }
    public com.thenerdcj.crate.CrateManager getCrateManager() { return crateManager; }
    public com.thenerdcj.gui.CrateGUI getCrateGUI() { return crateGUI; }
    public com.thenerdcj.cosmetic.ParticleTrailManager getParticleTrailManager() { return particleTrailManager; }
    public com.thenerdcj.gui.ParticleTrailGUI getParticleTrailGUI() { return particleTrailGUI; }
    public com.thenerdcj.gui.GeneratorGUI getGeneratorGUI() { return generatorGUI; }
    public com.thenerdcj.gui.AdminIslandInspectGUI getAdminIslandInspectGUI() { return adminIslandInspectGUI; }
    public com.thenerdcj.gui.SpawnEditGUI getSpawnEditGUI() { return spawnEditGUI; }
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
    public com.thenerdcj.gui.QuestLogGUI getQuestLogGUI() { return questLogGUI; }
    public com.thenerdcj.gui.QuestDetailGUI getQuestDetailGUI() { return questDetailGUI; }
    public TeleportRequestManager getTeleportRequestManager() { return teleportRequestManager; }

    public SlayerGUI getSlayerGUI() { return slayerGUI; }
    public SlayerLeaderboardGUI getSlayerLeaderboardGUI() { return slayerLeaderboardGUI; }
    public SlayerAchievementGUI getSlayerAchievementGUI() { return slayerAchievementGUI; }
    public com.thenerdcj.gui.SlayerShopGUI getSlayerShopGUI() { return slayerShopGUI; }
    public com.thenerdcj.gui.SlayerTokenLeaderboardGUI getSlayerTokenLeaderboardGUI() { return slayerTokenLeaderboardGUI; }
    public EnchantingTableGUI getEnchantingTableGUI() { return enchantingTableGUI; }
    public com.thenerdcj.enchant.EnchantmentManager getEnchantmentManager() { return enchantmentManager; }
    public ResetConfirmationGUI getResetConfirmationGUI() { return resetConfirmationGUI; }
    public BiomeSelectionGUI getBiomeSelectionGUI() { return biomeSelectionGUI; }
    public IslandUpgradeGUI getIslandUpgradeGUI() { return islandUpgradeGUI; }
    public TradeGUI getTradeGUI() { return tradeGUI; }

    public DimensionResetGUI getDimensionResetGUI() { return dimensionResetGUI; }
    public BugReportManager getBugReportManager() { return bugReportManager; }
    public BugReportListGUI getBugReportListGUI() { return bugReportListGUI; }

    public SuggestionManager getSuggestionManager() { return suggestionManager; }

    public World getSkyblockWorld(World.Environment environment) {
        if (worldManager == null) return null;
        return worldManager.resolveSkyblockWorld(environment);
    }

    public boolean isFolia() { return true; } // Folia-only, legacy detection removed

    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public AutoSellerManager getAutoSellerManager() { return autoSellerManager; }
    public com.thenerdcj.util.ThreadSafety getThreadSafety() { return threadSafety; }
    public com.thenerdcj.util.NameCache getNameCache() { return nameCache; }
    public AuctionGUI getAuctionGUI() { return auctionGUI; }
    public com.thenerdcj.bazaar.BazaarGUI getBazaarGUI() { return bazaarGUI; }

    public com.thenerdcj.wardrobe.WardrobeManager getWardrobeManager() { return wardrobeManager; }
    public com.thenerdcj.wardrobe.WardrobeGUI getWardrobeGUI() { return wardrobeGUI; }
    public com.thenerdcj.wardrobe.WardrobeSlotOptionsGUI getWardrobeSlotOptionsGUI() { return wardrobeSlotOptionsGUI; }

    public com.thenerdcj.pets.PetManager getPetManager() { return petManager; }
    public com.thenerdcj.pets.PetGUI getPetGUI() { return petGUI; }

    public com.thenerdcj.tags.PlayerTagManager getPlayerTagManager() { return playerTagManager; }
    public com.thenerdcj.cosmetic.NameColorManager getNameColorManager() { return nameColorManager; }
    public com.thenerdcj.tags.TagGUI getTagGUI() { return tagGUI; }

    public com.thenerdcj.cosmetic.JoinLeaveMessageManager getJoinLeaveMessageManager() { return joinLeaveMessageManager; }
    public com.thenerdcj.cosmetic.JoinLeaveMessageMainGUI getJoinLeaveMessageMainGUI() { return joinLeaveMessageMainGUI; }
    public com.thenerdcj.cosmetic.JoinLeaveMessageMainGUI getJoinLeaveMessageGUI() { return joinLeaveMessageMainGUI; } // alias for command compatibility

    public com.thenerdcj.tags.PlayerNametagManager getPlayerNametagManager() { return playerNametagManager; }

    public com.thenerdcj.wings.ElytraWingManager getElytraWingManager() { return elytraWingManager; }

    public com.thenerdcj.wings.WingGUI getWingGUI() { return wingGUI; }

    public com.thenerdcj.runes.RuneManager getRuneManager() { return runeManager; }
    public com.thenerdcj.runes.RuneGUI getRuneGUI() { return runeGUI; }

    public com.thenerdcj.cosmetic.HelmetSkinManager getHelmetSkinManager() { return helmetSkinManager; }
    public com.thenerdcj.cosmetic.HelmetSkinGUI getHelmetSkinGUI() { return helmetSkinGUI; }

    public com.thenerdcj.cosmetic.DeathEffectManager getDeathEffectManager() { return deathEffectManager; }
    public com.thenerdcj.cosmetic.DeathEffectGUI getDeathEffectGUI() { return deathEffectGUI; }

    public com.thenerdcj.cosmetic.DeathMessageManager getDeathMessageManager() { return deathMessageManager; }
    public com.thenerdcj.cosmetic.DeathMessageGUI getDeathMessageGUI() { return deathMessageGUI; }

    public com.thenerdcj.cosmetic.BackpackSkinManager getBackpackSkinManager() { return backpackSkinManager; }
    public com.thenerdcj.cosmetic.BackpackSkinGUI getBackpackSkinGUI() { return backpackSkinGUI; }

    public com.thenerdcj.cosmetic.PowerOrbSkinManager getPowerOrbSkinManager() { return powerOrbSkinManager; }
    public com.thenerdcj.cosmetic.PowerOrbSkinGUI getPowerOrbSkinGUI() { return powerOrbSkinGUI; }

    public com.thenerdcj.cosmetic.MinionSkinManager getMinionSkinManager() { return minionSkinManager; }
    public com.thenerdcj.cosmetic.MinionSkinGUI getMinionSkinGUI() { return minionSkinGUI; }

    public com.thenerdcj.cosmetic.IslandFurnitureManager getIslandFurnitureManager() { return islandFurnitureManager; }
    public com.thenerdcj.cosmetic.IslandFurnitureGUI getIslandFurnitureGUI() { return islandFurnitureGUI; }

    public com.thenerdcj.cosmetic.IslandMusicManager getIslandMusicManager() { return islandMusicManager; }
    public com.thenerdcj.cosmetic.IslandMusicGUI getIslandMusicGUI() { return islandMusicGUI; }

    public com.thenerdcj.cosmetic.OverheadCosmeticManager getOverheadCosmeticManager() { return overheadCosmeticManager; }

    public com.thenerdcj.cosmetic.OverheadCosmeticGUI getOverheadCosmeticGUI() {
        return new com.thenerdcj.cosmetic.OverheadCosmeticGUI(this);
    }

    public com.thenerdcj.cosmetic.EmoteCosmeticManager getEmoteCosmeticManager() { return emoteCosmeticManager; }

    public com.thenerdcj.cosmetic.EmoteCosmeticGUI getEmoteCosmeticGUI() {
        return new com.thenerdcj.cosmetic.EmoteCosmeticGUI(this);
    }

    public com.thenerdcj.cosmetic.IslandStructureManager getIslandStructureManager() { return islandStructureManager; }

    public com.thenerdcj.cosmetic.IslandStructureGUI getIslandStructureGUI() { return islandStructureGUI; }

    public com.thenerdcj.cosmetic.ChatBubbleCosmeticManager getChatBubbleCosmeticManager() { return chatBubbleCosmeticManager; }

    public com.thenerdcj.cosmetic.ChatBubbleGUI getChatBubbleGUI() {
        return new com.thenerdcj.cosmetic.ChatBubbleGUI(this);
    }

    public com.thenerdcj.cosmetic.IslandWeatherCosmeticManager getIslandWeatherCosmeticManager() { return islandWeatherCosmeticManager; }
    public com.thenerdcj.cosmetic.IslandWeatherGUI getIslandWeatherGUI() { return islandWeatherGUI; }

    public com.thenerdcj.cosmetic.AccessoryCosmeticManager getAccessoryCosmeticManager() { return accessoryCosmeticManager; }
    public com.thenerdcj.cosmetic.AccessoryCosmeticGUI getAccessoryCosmeticGUI() {
        return new com.thenerdcj.cosmetic.AccessoryCosmeticGUI(this);
    }

    public com.thenerdcj.manager.CollectionManager getCollectionManager() { return collectionManager; }
    public com.thenerdcj.gui.CollectionsGUI getCollectionsGUI() { return collectionsGUI; }

    public com.thenerdcj.season.SeasonManager getSeasonManager() { return seasonManager; }

    public com.thenerdcj.manager.MuseumManager getMuseumManager() { return museumManager; }
    public com.thenerdcj.gui.MuseumGUI getMuseumGUI() { return museumGUI; }

    public com.thenerdcj.skills.PlayerSkillManager getPlayerSkillManager() { return playerSkillManager; }
    public SkillGUI getSkillGUI() { return skillGUI; }
    public com.thenerdcj.gui.IslandTopGUI getIslandTopGUI() { return islandTopGUI; }
    public com.thenerdcj.gui.IslandBrowseGUI getIslandBrowseGUI() { return islandBrowseGUI; }

    private void validateConfiguration() {
        boolean hasIssues = false;
        if (getConfig().contains("seasonal.party") || getConfig().contains("seasonal.worth")
                || getConfig().contains("seasonal.perf") || getConfig().contains("seasonal.upkeep")) {
            MessageUtil.severe(getLogger(),
                    "§c[Config] island.party/worth/perf/upkeep appear under 'seasonal:' — move them under 'island:' (see default config.yml).");
            hasIssues = true;
        }

        String[] importantSections = {
            "island", "island.reset", "island.worth", "island.party", "island.perf", "island.upkeep",
            "boosters", "reports", "seasonal", "suggestions"
        };
        for (String sec : importantSections) {
            if (!getConfig().contains(sec)) {
                MessageUtil.warning(getLogger(), "§e[Config] Missing section/key '" + sec + "' in config.yml. Defaults will be used.");
                if (sec.startsWith("island.")) {
                    hasIssues = true;
                }
            }
        }

        if (!getConfig().isConfigurationSection("island.worth.block-worth")
                || getConfig().getConfigurationSection("island.worth.block-worth").getKeys(false).isEmpty()) {
            MessageUtil.severe(getLogger(),
                    "§c[Config] island.worth.block-worth is empty or missing — island worth/levels will not progress from blocks.");
            hasIssues = true;
        }

        if (getConfig().getInt("reports.cooldown-minutes", 5) < 0) {
            MessageUtil.warning(getLogger(), "§e[Config] reports.cooldown-minutes should be >= 0.");
        }
        if (getConfig().getInt("reports.max-description-length", 500) < 20) {
            MessageUtil.warning(getLogger(), "§e[Config] reports.max-description-length is very low; consider >= 100.");
        }
        if (getConfig().getBoolean("island.reset.enabled", true)) {
            double cost = getConfig().getDouble("island.reset.cost", 5000);
            if (cost < 0) {
                MessageUtil.severe(getLogger(), "§c[Config] island.reset.cost cannot be negative!");
                hasIssues = true;
            }
        }

        if (getConfig().getDouble("island.worth.level-formula.base", 100) <= 0) {
            MessageUtil.warning(getLogger(), "§e[Config] island.worth.level-formula.base should be > 0.");
        }
        double upkeepPercent = getConfig().getDouble("island.upkeep.percent-per-hour", 0.5);
        if (upkeepPercent < 0 || upkeepPercent > 10) {
            MessageUtil.warning(getLogger(), "§e[Config] island.upkeep.percent-per-hour looks extreme (" + upkeepPercent + "%).");
        }
        int maxRecalc = getConfig().getInt("island.worth.max-islands-per-recalc-tick", 50);
        if (maxRecalc < 1 || maxRecalc > 500) {
            MessageUtil.warning(getLogger(), "§e[Config] island.worth.max-islands-per-recalc-tick should be reasonable (1-200 for large servers).");
        }
        if (!getConfig().getBoolean("reports.enabled", true)) {
            MessageUtil.info(getLogger(), "§e[Config] reports.enabled=false — in-game bug reporting disabled.");
        }

        // Suggestions poll system config
        if (getConfig().getInt("suggestions.cooldown-minutes", 10) < 0) {
            MessageUtil.warning(getLogger(), "§e[Config] suggestions.cooldown-minutes should be >= 0.");
        }
        if (getConfig().getInt("suggestions.min-length", 10) < 3) {
            MessageUtil.warning(getLogger(), "§e[Config] suggestions.min-length is very low.");
        }
        if (getConfig().getInt("suggestions.max-length", 250) < 20) {
            MessageUtil.warning(getLogger(), "§e[Config] suggestions.max-length is too low for meaningful feedback.");
        }

        if (hasIssues) {
            MessageUtil.severe(getLogger(), "§c[Config] Critical configuration issues detected. Review above warnings.");
        } else {
            MessageUtil.info(getLogger(), "§a[Config] Configuration validated (including new reports section).");
        }
    }

    /**
     * Ensures island.worth.block-worth is populated even for users with stale/outdated config.yml on disk.
     * saveDefaultConfig() only writes the file if it is completely absent.
     * This merges the default block values (without overwriting any user customizations) and saves if changes were made.
     * Prevents the "block-worth is empty or missing" critical error.
     */
    private void ensureBlockWorthDefaults() {
        FileConfiguration config = getConfig();
        if (config.isConfigurationSection("island.worth.block-worth")
                && !config.getConfigurationSection("island.worth.block-worth").getKeys(false).isEmpty()) {
            return; // already populated
        }

        // Load the pristine defaults directly from the plugin jar resource
        try (InputStreamReader reader = new InputStreamReader(
                getResource("config.yml"), StandardCharsets.UTF_8)) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            ConfigurationSection defSection = defaults.getConfigurationSection("island.worth.block-worth");
            if (defSection == null || defSection.getKeys(false).isEmpty()) return;

            ConfigurationSection target = config.getConfigurationSection("island.worth.block-worth");
            if (target == null) {
                target = config.createSection("island.worth.block-worth");
            }

            boolean addedAny = false;
            for (String key : defSection.getKeys(false)) {
                if (!target.contains(key)) {
                    target.set(key, defSection.get(key));
                    addedAny = true;
                }
            }

            if (addedAny) {
                saveConfig();
                MessageUtil.info(getLogger(), "§a[Config] Added missing default values to island.worth.block-worth (updated your config.yml).");
            }
        } catch (Exception e) {
            getLogger().warning("[FoliaSkyblock] [Config] Could not auto-merge block-worth defaults: " + e.getMessage());
        }
    }

    public void setServerTabHeaderFooter(){Component header=Component.text("FoliaSkyblock",NamedTextColor.GOLD).append(Component.text(" • ",NamedTextColor.DARK_GRAY)).append(Component.text("Skyblock",NamedTextColor.YELLOW));Component footer=Component.text("Play to Win",NamedTextColor.GRAY).append(Component.text(" • ",NamedTextColor.DARK_GRAY)).append(Component.text("No Pay-to-Win",NamedTextColor.GREEN));for(org.bukkit.entity.Player p:Bukkit.getOnlinePlayers()){p.sendPlayerListHeader(header);p.sendPlayerListFooter(footer);}}
    public void setServerTabHeaderFooter(Player player){if(player==null||!player.isOnline())return;Component header=Component.text("FoliaSkyblock",NamedTextColor.GOLD).append(Component.text(" • ",NamedTextColor.DARK_GRAY)).append(Component.text("Skyblock",NamedTextColor.YELLOW));Component footer=Component.text("Play to Win",NamedTextColor.GRAY).append(Component.text(" • ",NamedTextColor.DARK_GRAY)).append(Component.text("No Pay-to-Win",NamedTextColor.GREEN));player.sendPlayerListHeader(header);player.sendPlayerListFooter(footer);}
}