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
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FoliaSkyblock extends JavaPlugin {

    @Override
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return new VoidChunkGenerator();
    }

    // ==================== MANAGERS ====================
    private DatabaseManager databaseManager;
    private GridManager gridManager;
    private IslandManager islandManager;
    private EconomyManager economyManager;
    private RankManager rankManager;
    private ChallengeManager challengeManager;
    private QuestManager questManager;
    private QuestLogGUI questLogGUI;
    private QuestDetailGUI questDetailGUI;
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
    private IslandOreGenerator islandOreGenerator;
    private HologramManager hologramManager;
    private TeleportRequestManager teleportRequestManager;
    private PunishmentManager punishmentManager;
    private BugReportManager bugReportManager;
    private BugReportListGUI bugReportListGUI;
    private AutoSellerManager autoSellerManager;
    private ThreadSafety threadSafety;
    private NameCache nameCache;

    // Island Worth / Level System
    private IslandWorthManager islandWorthManager;
    private MissionManager missionManager;
    private BoosterManager boosterManager;
    private BoosterGUI boosterGUI;

    // Island Shop
    private IslandShopManager islandShopManager;
    private IslandShopGUI islandShopGUI;
    private IslandBankGUI islandBankGUI;
    private IslandSettingsGUI islandSettingsGUI;

    // Prestige System
    private PrestigeManager prestigeManager;
    private PrestigeGUI prestigeGUI;

    // Border / Size Upgrades + Visuals
    private BorderVisualManager borderVisualManager;

    // Crates + Cosmetics + Wardrobe
    private CrateManager crateManager;
    private CrateGUI crateGUI;
    private ParticleTrailManager particleTrailManager;
    private ParticleTrailGUI particleTrailGUI;
    private GeneratorGUI generatorGUI;

    // Admin tools
    private AdminIslandInspectGUI adminIslandInspectGUI;
    // Task batch: dedicated SpawnEditGUI (polish for admin spawn fixes)
    private SpawnEditGUI spawnEditGUI;

    private WardrobeManager wardrobeManager;
    private WardrobeGUI wardrobeGUI;
    private WardrobeSlotOptionsGUI wardrobeSlotOptionsGUI;

    // Pet System (cosmetic followers, integrated with Wardrobe)
    private PetManager petManager;
    private PetGUI petGUI;

    // Player Tag System (cosmetic chat/tab tags - prestige/slayer/collection gated)
    private PlayerTagManager playerTagManager;
    private TagGUI tagGUI;

    // Join/Leave Message cosmetic system (personalized rich messages on join/quit)
    private com.thenerdcj.cosmetic.JoinLeaveMessageManager joinLeaveMessageManager;
    private com.thenerdcj.cosmetic.JoinLeaveMessageMainGUI joinLeaveMessageMainGUI;

    // Overhead Nametags (cosmetic tags above player heads via scoreboard teams)
    private PlayerNametagManager playerNametagManager;

    // Elytra Wing Cosmetics (advanced gliding visual effects)
    private ElytraWingManager elytraWingManager;
    private WingGUI wingGUI;

    // Cosmetic Runes
    private RuneManager runeManager;
    private RuneGUI runeGUI;

    // Helmet Skins (cosmetic helmet appearance overrides)
    private HelmetSkinManager helmetSkinManager;
    private HelmetSkinGUI helmetSkinGUI;

    // Death / Kill Effects (cosmetic on death and kill visuals)
    private DeathEffectManager deathEffectManager;
    private DeathEffectGUI deathEffectGUI;

    // Cosmetic Death Messages (new system - text on kill/death, parallels DeathEffect)
    private DeathMessageManager deathMessageManager;
    private DeathMessageGUI deathMessageGUI;

    // Backpack Skins (cosmetic overrides for backpacks - exploration/full impl started)
    private BackpackSkinManager backpackSkinManager;
    private BackpackSkinGUI backpackSkinGUI;

    // Power Orb Skins (new cosmetic system - started)
    private PowerOrbSkinManager powerOrbSkinManager;
    private PowerOrbSkinGUI powerOrbSkinGUI;

    // Minion Skins (cosmetic themes for island minions - active implementation)
    private MinionSkinManager minionSkinManager;
    private MinionSkinGUI minionSkinGUI;

    // Island Furniture / Housing Cosmetics (foundation)
    private IslandFurnitureManager islandFurnitureManager;
    private IslandFurnitureGUI islandFurnitureGUI;

    // Island Music & Ambient Cosmetics (new)
    private IslandMusicManager islandMusicManager;
    private IslandMusicGUI islandMusicGUI;

    // Advanced Overhead Cosmetics (TextDisplay floating effects - new foundation)
    private OverheadCosmeticManager overheadCosmeticManager;

    // Cosmetic Emotes (new)
    private EmoteCosmeticManager emoteCosmeticManager;

    // Island Structure Decorations (new)
    private IslandStructureManager islandStructureManager;
    private IslandStructureGUI islandStructureGUI;

    // Chat Bubble Cosmetics (floating chat visuals - new)
    private ChatBubbleCosmeticManager chatBubbleCosmeticManager;

    // Island Weather Cosmetics (new)
    private IslandWeatherCosmeticManager islandWeatherCosmeticManager;
    private IslandWeatherGUI islandWeatherGUI;

    // Light Accessories (new)
    private AccessoryCosmeticManager accessoryCosmeticManager;

    // Core Collections System (unique per-island item discovery for progression + cosmetic rewards)
    private CollectionManager collectionManager;
    private CollectionsGUI collectionsGUI;

    // Seasonal resets (full Option B impl: DB wipe + staggered Region clear + grants + safety)
    private SeasonManager seasonManager;

    // Task 4: Museum system (Hypixel depth)
    private MuseumManager museumManager;
    private MuseumGUI museumGUI;

    // Player Skill System (MCMMO-inspired per-player skills with abilities, anti-cheat safe)
    private PlayerSkillManager playerSkillManager;
    private SkillGUI skillGUI;
    private IslandTopGUI islandTopGUI;
    private IslandBrowseGUI islandBrowseGUI;

    // ==================== GUI INSTANCES ====================
    private TradeGUI tradeGUI;
    private SlayerGUI slayerGUI;
    private SlayerLeaderboardGUI slayerLeaderboardGUI;
    private SlayerAchievementGUI slayerAchievementGUI;
    private SlayerShopGUI slayerShopGUI;
    private SlayerTokenLeaderboardGUI slayerTokenLeaderboardGUI;
    private EnchantingTableGUI enchantingTableGUI;
    private EnchantmentManager enchantmentManager;
    private IslandChatManager islandChatManager;
    private IslandUpgradeGUI islandUpgradeGUI;
    private ResetConfirmationGUI resetConfirmationGUI;
    private BiomeSelectionGUI biomeSelectionGUI;
    private TPAListGUI tpaListGUI;
    private AuctionGUI auctionGUI;
    private BazaarGUI bazaarGUI;

    // NEW: Per-dimension island reset system
    private DimensionResetGUI dimensionResetGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        validateConfiguration();

        // No auto-download of any soft dependencies. The plugin is designed to be as independent
        // as possible (no ProtocolLib, no WorldEdit, no PlaceholderAPI, etc.).

        // Schedulers must exist before managers that register repeating tasks in constructors
        this.threadSafety = new ThreadSafety(this);
        this.nameCache = new NameCache(this);

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
        Bukkit.getPluginManager().registerEvents(questManager, this);
        this.questLogGUI = new QuestLogGUI(this);
        this.questDetailGUI = new QuestDetailGUI(this);
        this.bossManager = new BossManager(this);
        this.antiCheatManager = new AntiCheatManager(this);

        this.islandUpgradeManager = new IslandUpgradeManager(this);
        this.islandOreGenerator = new IslandOreGenerator(this, gridManager, islandUpgradeManager);
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
        this.bugReportManager = new BugReportManager(this);
        this.bugReportListGUI = new BugReportListGUI(this);
        this.autoSellerManager = new AutoSellerManager(this);

        // Island Worth / Level + Economy sinks
        this.islandWorthManager = new IslandWorthManager(this);
        this.missionManager = new MissionManager(this);
        this.boosterManager = new BoosterManager(this);
        this.boosterGUI = new BoosterGUI(this);
        this.islandShopManager = new IslandShopManager(this);
        this.islandShopGUI = new IslandShopGUI(this);
        this.islandBankGUI = new IslandBankGUI(this);
        this.islandSettingsGUI = new IslandSettingsGUI(this);

        // Prestige + Border + Crates + Cosmetics
        this.prestigeManager = new PrestigeManager(this);
        this.prestigeGUI = new PrestigeGUI(this);
        this.borderVisualManager = new BorderVisualManager(this);
        this.crateManager = new CrateManager(this);
        this.crateGUI = new CrateGUI(this);
        this.particleTrailManager = new ParticleTrailManager(this);
        this.particleTrailGUI = new ParticleTrailGUI(this);
        this.generatorGUI = new GeneratorGUI(this);
        this.adminIslandInspectGUI = new AdminIslandInspectGUI(this);
        this.spawnEditGUI = new SpawnEditGUI(this);

        // Scheduled tasks - worth recalc now respects config (economy/perf optimization)
        // For large servers, set recalc-interval-minutes high or 0 in config + rely on event-driven adjustBlockWorth in block listeners.
        long worthIntervalMin = (islandWorthManager != null && islandWorthManager.isPeriodicRecalcEnabled())
                ? islandWorthManager.getWorthRecalcIntervalMinutes() : 0;
        long worthDelay = (worthIntervalMin > 0) ? (20 * 60 * Math.max(1, worthIntervalMin)) : (20 * 60 * 60); // fallback 1h if disabled
        if (worthIntervalMin > 0) {
            threadSafety.runRepeatingOnMainThread(() -> {
                if (islandWorthManager != null && islandManager != null) {
                    long start = System.nanoTime();
                    int cap = islandWorthManager.getMaxIslandsPerRecalcTick();
                    int count = 0;
                    for (Island island : islandManager.getAllLoadedIslands().values()) {
                        if (island != null) {
                            if (cap > 0 && count >= cap) break;
                            // Actual per-island RegionScheduler stagger for large scale worth drift correction (more than previous; use center for locality, cap work).
                            // See IMPROVEMENTS "staggered per-island recalc using RegionScheduler", "Worth recalculation → break into per-island RegionScheduler", "more per-island RegionScheduler for globals".
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
        } // else: fully event-driven (block events + explicit calls on upgrades/prestige/shop) for max scale perf

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

        // Island upkeep tax sink (economy/perf optimization from IMPROVEMENTS.md)
        // Applies configurable % drain on island balances periodically (hourly) if enabled.
        // Uses hardened tryRemove paths. Fire-and-forget per island. Uses RegionScheduler at island center for Folia safety.
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
                            // Schedule apply at island region for Folia correctness (even though DB async)
                            org.bukkit.Location center = island.getCenter(null); // dim handled in get
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

        // Weekly Slayer Token leaderboard reset check (lightweight)
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
                // Additional actual staggered per-island RegionScheduler for large scale global tasks (e.g. weekly leaderboards + per-island holograms on 1000+).
                // Cap + runAtLocation at island centers for locality instead of pure global.
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

        // Global tops/leaderboards for large scale (1000+ islands): use DB paginated + event-driven (dirty flags like topsDirty in rating + worthTopsDirty/levelTopsDirty/membersTopsDirty in worth manager) + short TTL result caching in IslandWorthManager + pre-warm of first pages here + per-island RegionScheduler for any island-specific refresh (e.g., at island center via runAtLocation). 
        // Example: stagger global top refresh to avoid global hot path.
        threadSafety.runRepeatingOnMainThread(() -> {
            if (islandRatingManager != null) {
                long start = 0;
                if (islandWorthManager != null && islandWorthManager.isProfileHotPaths()) start = System.nanoTime();
                islandRatingManager.getTopRatedIslands(5).thenAccept(tops -> {
                    // For real large scale, for each top island, could do threadSafety.runAtLocation(islandCenter, () -> updateHologramOrCache(top));
                    // This staggers per-island RegionScheduler instead of one global.
                    // Actual staggered per-island RegionScheduler for large scale (1000+ islands) global tops compression.
                    // Cap and run lightweight work at per-island region centers (locality) instead of global hot path.
                    // See IMPROVEMENTS "staggered RegionScheduler for more (e.g. global tops, leaderboards)", "per-island RegionScheduler for globals".
                    if (islandManager != null) {
                        int staggerCount = 0;
                        int maxStagger = islandWorthManager != null ? Math.min(5, islandWorthManager.getMaxIslandsPerRecalcTick()) : 3;
                        for (GridPosition pos : tops.keySet()) {
                            if (staggerCount++ >= maxStagger) break;
                            // Placeholder center (in full impl: resolve real island center loc from pos via IslandManager/Grid);
                            // Demonstrates executable stagger using ThreadSafety.runAtLocation for region-aware work.
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
                // Exercise DB-paginated tops (offset) for 1000+ scale compression (fetch only needed page, complements LIMIT in DAO).
                islandRatingManager.getTopRatedIslands(5, 5).thenAccept(page2Tops -> {
                    // page 2 sample; in production can drive paged leaderboard GUIs or partial refreshes.
                });

                // Pre-warm worth / level / members tops caches (event-driven + TTL in IslandWorthManager) for /is top GUI, PAPI, etc.
                // Calling the getters triggers cache refresh if dirty/expired (short TTL + dirty sinks).
                // Ties into "Top result caching + event-driven", "pre-warm top pages in Folia global tops task (staggered)".
                if (islandWorthManager != null) {
                    islandWorthManager.getTopIslandsByWorth(20, 0); // pre-warm default worth tops (used by IslandTopGUI)
                    islandWorthManager.getTopIslandsByLevel(10, 0);
                    islandWorthManager.getTopIslandsByMemberCount(10, 0);
                    // Note: the getters handle the actual refresh + clearDirty internally when miss.
                    // For full stagger on the top islands themselves, the per-pos runAtLocation above can be extended in future.
                    // Also refresh rank snapshots from the (now populated) top windows.
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

        // Periodic rank snapshot backfill/refresh task (the "Periodic or event-driven full rank snapshot backfill/refresh task" next step).
        // Low-freq GlobalRegion task: calls refresh from current top caches (position-based stamps, no COUNT cost for hot islands)
        // + backfillMissing which finds islands with worth>0 but no last_*_rank and fires the getMy* (which do one-time COUNT + persist snapshot).
        // Complements per-island saves on calc/prestige, window stamps on cache populate, and TopGUITest coverage.
        // Frequency low (30min) to keep work compressed for 1000+ islands. Initial delay to let startup settle.
        // Event-driven aspect: cache gets in pre-warm/GUI/PAPI also trigger refreshRankSnapshotsFromTops.
        threadSafety.runRepeatingOnMainThread(() -> {
            if (islandWorthManager != null) {
                islandWorthManager.refreshRankSnapshotsFromTops();
                islandWorthManager.backfillMissingRankSnapshots(100);  // batch the long-tail
            }
        }, 20 * 60 * 2L, 20 * 60 * 30L);

        // Note: for global leaderboards/tops on large scale (1000+ islands), prefer per-island RegionScheduler staggering where possible (e.g. refresh per-island data at center) + DB paginated queries + event-driven invalidation via dirty flags (auctionsDirty, topsDirty, worthTopsDirty etc.) + short-TTL result caching (IslandWorthManager) + pre-warm here instead of pure periodic global. See IMPROVEMENTS suggestions for "per-island RegionScheduler for globals/leaderboards", "staggered RegionScheduler for more (e.g. global tops, leaderboards)", "For 1000+ islands: make leaderboard/top queries fully DB paginated", "Top result caching + event-driven". HologramManager and rating use some GlobalRegion for tops; can layer Region at island centers for locality.

        // Wardrobe
        this.wardrobeManager = new WardrobeManager(this);
        this.wardrobeGUI = new WardrobeGUI(this);
        this.wardrobeSlotOptionsGUI = new WardrobeSlotOptionsGUI(this);

        // Pet System (cosmetic)
        this.petManager = new PetManager(this);
        this.petGUI = new PetGUI(this);

        // Player Tag System
        this.playerTagManager = new PlayerTagManager(this);
        this.tagGUI = new TagGUI(this);

        // Join/Leave Message System (personalized cosmetics for join/quit)
        this.joinLeaveMessageManager = new com.thenerdcj.cosmetic.JoinLeaveMessageManager(this);
        this.joinLeaveMessageMainGUI = new com.thenerdcj.cosmetic.JoinLeaveMessageMainGUI(this);

        // Overhead Nametag System (scoreboard teams)
        this.playerNametagManager = new PlayerNametagManager(this);

        // Elytra Wing Cosmetics System
        this.elytraWingManager = new ElytraWingManager(this);
        this.wingGUI = new WingGUI(this);

        // Cosmetic Runes System
        this.runeManager = new RuneManager(this);
        this.runeGUI = new RuneGUI(this);

        // Helmet Skins System
        this.helmetSkinManager = new HelmetSkinManager(this);
        this.helmetSkinGUI = new HelmetSkinGUI(this);

        // Death / Kill Effects System
        this.deathEffectManager = new DeathEffectManager(this);
        this.deathEffectGUI = new DeathEffectGUI(this);

        // Death Messages cosmetic
        this.deathMessageManager = new DeathMessageManager(this);
        this.deathMessageGUI = new DeathMessageGUI(this);

        // Backpack Skins System (exploration)
        this.backpackSkinManager = new BackpackSkinManager(this);
        this.backpackSkinGUI = new BackpackSkinGUI(this);

        // Power Orb Skins System (new)
        this.powerOrbSkinManager = new PowerOrbSkinManager(this);
        this.powerOrbSkinGUI = new PowerOrbSkinGUI(this);

        // Minion Skins System (active)
        this.minionSkinManager = new MinionSkinManager(this);
        this.minionSkinGUI = new MinionSkinGUI(this);

        // Island Furniture / Housing (foundation)
        this.islandFurnitureManager = new IslandFurnitureManager(this);
        this.islandFurnitureGUI = new IslandFurnitureGUI(this);

        // Island Music & Ambient (new)
        this.islandMusicManager = new IslandMusicManager(this);
        this.islandMusicGUI = new IslandMusicGUI(this);

        // Advanced Overhead Cosmetics (foundation)
        this.overheadCosmeticManager = new OverheadCosmeticManager(this);
        // Overhead GUI
        // (instantiated on demand for now; can be eager if needed)

        // Cosmetic Emotes (new)
        this.emoteCosmeticManager = new EmoteCosmeticManager(this);

        // Island Structure Decorations (new)
        this.islandStructureManager = new IslandStructureManager(this);
        this.islandStructureGUI = new IslandStructureGUI(this);

        // Chat Bubble Cosmetics (new)
        this.chatBubbleCosmeticManager = new ChatBubbleCosmeticManager(this);

        // Island Weather Cosmetics (new)
        this.islandWeatherCosmeticManager = new IslandWeatherCosmeticManager(this);
        this.islandWeatherGUI = new IslandWeatherGUI(this);

        // Light Accessories (new)
        this.accessoryCosmeticManager = new AccessoryCosmeticManager(this);

        // Core Collections (island discovery)
        this.collectionManager = new CollectionManager(this);
        this.collectionsGUI = new CollectionsGUI(this);

        // Seasonal resets (full Option B: data wipe + RegionScheduler plot clears + grant support)
        this.seasonManager = new SeasonManager(this);

        // Task 4: Museum (Hypixel-aligned collection sink/display + tokens for cosmetics)
        this.museumManager = new MuseumManager(this);
        this.museumGUI = new MuseumGUI(this);

        // Player Skills (MCMMO reference, Folia + anti-cheat safe)
        this.playerSkillManager = new PlayerSkillManager(this);
        this.skillGUI = new SkillGUI(this);
        this.islandTopGUI = new IslandTopGUI(this);
        this.islandBrowseGUI = new IslandBrowseGUI(this);

        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.DeathEffectListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.ChatBubbleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.EmoteTriggerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.CollectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.SkillListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.EarlyGameListener(this), this);

        // World + Holograms
        this.worldManager = new WorldManager(this);
        Bukkit.getPluginManager().registerEvents(new com.thenerdcj.listener.SpawnJoinListener(this), this);
        this.worldManager.initializeWorlds();
        MessageUtil.info(getLogger(), "§e[WorldManager] Creating custom void worlds for Skyblock...");

        this.hologramManager = new HologramManager(this);
        hologramManager.loadAndSpawnAll();

        // === GUIs ===
        this.tradeGUI = new TradeGUI(this);
        this.slayerGUI = new SlayerGUI(this);
        this.slayerLeaderboardGUI = new SlayerLeaderboardGUI(this);
        this.slayerAchievementGUI = new SlayerAchievementGUI(this);
        this.slayerShopGUI = new SlayerShopGUI(this);
        this.slayerTokenLeaderboardGUI = new SlayerTokenLeaderboardGUI(this);
        this.enchantingTableGUI = new EnchantingTableGUI(this);
        this.enchantmentManager = new EnchantmentManager(this);
        this.islandChatManager = new IslandChatManager(this);
        this.resetConfirmationGUI = new ResetConfirmationGUI(this);
        this.biomeSelectionGUI = new BiomeSelectionGUI(this);
        this.islandUpgradeGUI = new IslandUpgradeGUI(this);
        this.auctionGUI = new AuctionGUI(this, auctionManager);
        this.bazaarGUI = new BazaarGUI(this, bazaarManager);

        // NEW: Per-dimension island reset GUI
        this.dimensionResetGUI = new DimensionResetGUI(this);

        // Register commands & listeners
        registerCommands();
        registerListeners();

        // Load islands for online players
        Bukkit.getOnlinePlayers().forEach(player -> islandManager.loadPlayerIslands(player));

        // Improved tab list header/footer (polish)
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

    // ==================== COMMAND REGISTRATION ====================
    private void registerCommands() {
        safeRegisterCommand("island", new IslandCommand(this));
        safeRegisterCommand("is", new IslandCommand(this));
        safeRegisterCommand("bal", new BalanceCommand(this));
        safeRegisterCommand("balance", new BalanceCommand(this));
        safeRegisterCommand("rank", new RankCommand(this));
        safeRegisterCommand("spawn", new PlayerCommand(this));
        safeRegisterCommand("home", new PlayerCommand(this));

        // TPA
        safeRegisterCommand("tpa", new PlayerCommand(this));
        safeRegisterCommand("tpaccept", new PlayerCommand(this));
        safeRegisterCommand("tpdeny", new PlayerCommand(this));
        safeRegisterCommand("tpignore", new PlayerCommand(this));
        safeRegisterCommand("tplist", new PlayerCommand(this));
        safeRegisterCommand("pending", new PlayerCommand(this));

        // Messaging & Utility
        safeRegisterCommand("msg", new PlayerCommand(this));
        safeRegisterCommand("r", new PlayerCommand(this));
        safeRegisterCommand("list", new PlayerCommand(this));
        safeRegisterCommand("online", new PlayerCommand(this));
        safeRegisterCommand("help", new PlayerCommand(this));
        safeRegisterCommand("rules", new PlayerCommand(this));

        // Gameplay
        safeRegisterCommand("challenge", new ChallengeCommand(this));
        safeRegisterCommand("challenges", new ChallengeCommand(this));
        safeRegisterCommand("daily", new ChallengeCommand(this));
        safeRegisterCommand("slayer", new SlayerCommand(this));
        safeRegisterCommand("enchant", new EnchantCommand(this));
        safeRegisterCommand("trail", new ParticleCommand(this));
        safeRegisterCommand("particles", new ParticleCommand(this));
        safeRegisterCommand("auction", new AuctionCommand(this));
        safeRegisterCommand("ah", new AuctionCommand(this));
        safeRegisterCommand("bazaar", new BazaarCommand(this));
        safeRegisterCommand("minions", new MinionsCommand(this));
        safeRegisterCommand("wardrobe", new WardrobeCommand(this));
        safeRegisterCommand("wd", new WardrobeCommand(this));

        // Pet command (temporary - will be merged into Wardrobe later)
        safeRegisterCommand("pets", new PetCommand(this));

        // Tags command (new cosmetic tag system)
        safeRegisterCommand("tags", new TagCommand(this));

        // Join/Leave Message command (personalized cosmetics)
        safeRegisterCommand("joinleave", new com.thenerdcj.command.JoinLeaveMessageCommand(this));

        // Elytra Wings command (new dedicated GUI)
        safeRegisterCommand("wings", new WingsCommand(this));

        // Runes command
        safeRegisterCommand("runes", new RunesCommand(this));

        // Death Effects command (new cosmetic system)
        safeRegisterCommand("deatheffects", new DeathEffectCommand(this));
        safeRegisterCommand("death", new DeathEffectCommand(this));

        // Death Messages cosmetic command
        safeRegisterCommand("deathmessages", new DeathMessageCommand(this));
        safeRegisterCommand("deathmessage", new DeathMessageCommand(this));
        safeRegisterCommand("killmessages", new DeathMessageCommand(this));

        // Backpack Skins command (exploration)
        safeRegisterCommand("backpackskins", new BackpackSkinCommand(this));
        safeRegisterCommand("backpacks", new BackpackSkinCommand(this));

        // Power Orb Skins command (new system)
        safeRegisterCommand("powerorbskins", new PowerOrbSkinCommand(this));
        safeRegisterCommand("orbskins", new PowerOrbSkinCommand(this));

        // Minion Skins command (new system)
        safeRegisterCommand("minionskins", new MinionSkinCommand(this));
        safeRegisterCommand("minionskin", new MinionSkinCommand(this));

        // Island Furniture / Housing command (foundation)
        safeRegisterCommand("furniture", new IslandFurnitureCommand(this));
        safeRegisterCommand("housing", new IslandFurnitureCommand(this));
        safeRegisterCommand("decor", new IslandFurnitureCommand(this));

        // Island Music / Ambience command (new)
        safeRegisterCommand("music", new IslandMusicCommand(this));
        safeRegisterCommand("ambience", new IslandMusicCommand(this));
        safeRegisterCommand("sounds", new IslandMusicCommand(this));

        // Cosmetic Emotes command (new)
        safeRegisterCommand("emotes", new EmoteCosmeticCommand(this));
        safeRegisterCommand("emote", new EmoteCosmeticCommand(this));

        // Island Structures command (new)
        safeRegisterCommand("structures", new IslandStructureCommand(this));
        safeRegisterCommand("structure", new IslandStructureCommand(this));

        // Chat Bubble Cosmetics command (new)
        safeRegisterCommand("chatbubbles", new ChatBubbleCommand(this));
        safeRegisterCommand("chatbubble", new ChatBubbleCommand(this));
        safeRegisterCommand("bubble", new ChatBubbleCommand(this));

        // Island Weather Cosmetics command (new)
        safeRegisterCommand("weather", new IslandWeatherCommand(this));
        safeRegisterCommand("islandweather", new IslandWeatherCommand(this));
        safeRegisterCommand("weathereffects", new IslandWeatherCommand(this));

        // Light Accessories command (new)
        safeRegisterCommand("accessories", new AccessoryCommand(this));
        safeRegisterCommand("accessory", new AccessoryCommand(this));

        // Core Collections command
        safeRegisterCommand("collections", new CollectionCommand(this));
        safeRegisterCommand("collection", new CollectionCommand(this));

        // Player Skills command (MCMMO-like)
        safeRegisterCommand("skills", new SkillCommand(this));
        safeRegisterCommand("skill", new SkillCommand(this));

        // Early game / onboarding quests (daily + FIRST island quests)
        var questsExecutor = (org.bukkit.command.CommandExecutor) (sender, cmd, label, args) -> {
            if (sender instanceof Player player) {
                // Use getIslandForPlayer for reliable resolution (current dim with NORMAL fallback)
                // This ensures /quest from spawn/other dims still opens the player's island quest log ("new gui")
                com.thenerdcj.island.Island island = getIslandManager().getIslandForPlayer(player);
                String islandId = (island != null) ? island.getId() : player.getUniqueId().toString();
                if (questLogGUI != null) {
                    questLogGUI.open(player, islandId);
                }
                if (questManager != null) {
                    questManager.generateOnboardingQuests(islandId);
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

        // Advanced Overhead Cosmetics command (foundation test)
        safeRegisterCommand("overhead", new OverheadCosmeticCommand(this));

        // Nametag visibility toggle
        safeRegisterCommand("nametag", new NametagCommand(this));

        // Staff
        StaffCommand staffCmd = new StaffCommand(this);
        safeRegisterCommand("staff", staffCmd);
        safeRegisterCommand("vanish", staffCmd);
        safeRegisterCommand("fly", staffCmd);
        safeRegisterCommand("god", staffCmd);
        safeRegisterCommand("heal", staffCmd);
        safeRegisterCommand("speed", staffCmd);
        safeRegisterCommand("gm", staffCmd);
        safeRegisterCommand("gamemode", staffCmd);
        // EssentialsX-style shortcuts for gamemode (staff only)
        safeRegisterCommand("gmc", staffCmd);
        safeRegisterCommand("gms", staffCmd);
        safeRegisterCommand("gma", staffCmd);
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
        safeRegisterCommand("setspawn", staffCmd);
        safeRegisterCommand("isadmin", new AdminCommand(this));

        // Bug reporting system (player submit + staff /bug reports)
        safeRegisterCommand("bug", new BugReportCommand(this));
        safeRegisterCommand("bugreport", new BugReportCommand(this));
        safeRegisterCommand("reportbug", new BugReportCommand(this));
        safeRegisterCommand("reports", new BugReportCommand(this));

        // Trade
        safeRegisterCommand("trade", (sender, cmd, label, args) -> {
            if (sender instanceof Player player) {
                tradeGUI.openTradeGUI(player);
                return true;
            }
            sender.sendMessage(MessageUtil.legacy("§cThis command can only be used by players."));
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
            MessageUtil.warning(getLogger(), "§e[FoliaSkyblock] Command '" + name + "' not found in plugin.yml.");
        }
    }

    // ==================== LISTENER REGISTRATION ====================
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
    public IslandOreGenerator getIslandOreGenerator() { return islandOreGenerator; }

    public IslandWorthManager getIslandWorthManager() { return islandWorthManager; }
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

    // GUI Getters
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

    // NEW: Per-dimension island reset
    public DimensionResetGUI getDimensionResetGUI() { return dimensionResetGUI; }

    // Bug reporting system
    public BugReportManager getBugReportManager() { return bugReportManager; }
    public BugReportListGUI getBugReportListGUI() { return bugReportListGUI; }

    // ==================== HELPERS ====================
    public World getSkyblockWorld(World.Environment environment) {
        if (worldManager == null) return null;
        return worldManager.resolveSkyblockWorld(environment);
    }

    public boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public AutoSellerManager getAutoSellerManager() { return autoSellerManager; }
    public com.thenerdcj.util.ThreadSafety getThreadSafety() { return threadSafety; }
    public com.thenerdcj.util.NameCache getNameCache() { return nameCache; }
    public AuctionGUI getAuctionGUI() { return auctionGUI; }
    public com.thenerdcj.bazaar.BazaarGUI getBazaarGUI() { return bazaarGUI; }

    // Wardrobe
    public com.thenerdcj.wardrobe.WardrobeManager getWardrobeManager() { return wardrobeManager; }
    public com.thenerdcj.wardrobe.WardrobeGUI getWardrobeGUI() { return wardrobeGUI; }
    public com.thenerdcj.wardrobe.WardrobeSlotOptionsGUI getWardrobeSlotOptionsGUI() { return wardrobeSlotOptionsGUI; }

    public com.thenerdcj.pets.PetManager getPetManager() { return petManager; }
    public com.thenerdcj.pets.PetGUI getPetGUI() { return petGUI; }

    public com.thenerdcj.tags.PlayerTagManager getPlayerTagManager() { return playerTagManager; }
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

    // Death Messages
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

    // Core Collections
    public com.thenerdcj.manager.CollectionManager getCollectionManager() { return collectionManager; }
    public com.thenerdcj.gui.CollectionsGUI getCollectionsGUI() { return collectionsGUI; }

    public com.thenerdcj.season.SeasonManager getSeasonManager() { return seasonManager; }

    // Task 4 getters
    public com.thenerdcj.manager.MuseumManager getMuseumManager() { return museumManager; }
    public com.thenerdcj.gui.MuseumGUI getMuseumGUI() { return museumGUI; }

    // Player Skills
    public com.thenerdcj.skills.PlayerSkillManager getPlayerSkillManager() { return playerSkillManager; }
    public SkillGUI getSkillGUI() { return skillGUI; }
    public com.thenerdcj.gui.IslandTopGUI getIslandTopGUI() { return islandTopGUI; }
    public com.thenerdcj.gui.IslandBrowseGUI getIslandBrowseGUI() { return islandBrowseGUI; }

    // ==================== CONFIG VALIDATION ====================
    private void validateConfiguration() {
        boolean hasIssues = false;

        // Detect legacy mis-nesting (party/worth were previously under seasonal:)
        if (getConfig().contains("seasonal.party") || getConfig().contains("seasonal.worth")
                || getConfig().contains("seasonal.perf") || getConfig().contains("seasonal.upkeep")) {
            MessageUtil.severe(getLogger(),
                    "§c[Config] island.party/worth/perf/upkeep appear under 'seasonal:' — move them under 'island:' (see default config.yml).");
            hasIssues = true;
        }

        String[] importantSections = {
            "island", "island.reset", "island.worth", "island.party", "island.perf", "island.upkeep",
            "boosters", "reports", "seasonal"
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

        // Reports (new bug reporting system)
        if (getConfig().getInt("reports.cooldown-minutes", 5) < 0) {
            MessageUtil.warning(getLogger(), "§e[Config] reports.cooldown-minutes should be >= 0.");
        }
        if (getConfig().getInt("reports.max-description-length", 500) < 20) {
            MessageUtil.warning(getLogger(), "§e[Config] reports.max-description-length is very low; consider >= 100.");
        }

        // Island reset safety
        if (getConfig().getBoolean("island.reset.enabled", true)) {
            double cost = getConfig().getDouble("island.reset.cost", 5000);
            if (cost < 0) {
                MessageUtil.severe(getLogger(), "§c[Config] island.reset.cost cannot be negative!");
                hasIssues = true;
            }
        }

        // Worth / economy sanity
        if (getConfig().getDouble("island.worth.level-formula.base", 100) <= 0) {
            MessageUtil.warning(getLogger(), "§e[Config] island.worth.level-formula.base should be > 0.");
        }
        double upkeepPercent = getConfig().getDouble("island.upkeep.percent-per-hour", 0.5);
        if (upkeepPercent < 0 || upkeepPercent > 10) {
            MessageUtil.warning(getLogger(), "§e[Config] island.upkeep.percent-per-hour looks extreme (" + upkeepPercent + "%).");
        }

        // Perf caps (large server)
        int maxRecalc = getConfig().getInt("island.worth.max-islands-per-recalc-tick", 50);
        if (maxRecalc < 1 || maxRecalc > 500) {
            MessageUtil.warning(getLogger(), "§e[Config] island.worth.max-islands-per-recalc-tick should be reasonable (1-200 for large servers).");
        }

        if (!isFolia()) {
            MessageUtil.warning(getLogger(), "§e[Config] Running on non-Folia server. Many Folia-specific optimizations (Region/Entity schedulers, etc.) are disabled or fallback.");
        }

        // New reports system note
        if (!getConfig().getBoolean("reports.enabled", true)) {
            MessageUtil.info(getLogger(), "§e[Config] reports.enabled=false — in-game bug reporting disabled.");
        }

        if (hasIssues) {
            MessageUtil.severe(getLogger(), "§c[Config] Critical configuration issues detected. Review above warnings.");
        } else {
            MessageUtil.info(getLogger(), "§a[Config] Configuration validated (including new reports section).");
        }
    }

    /**
     * Sets a clean, informative server header and footer on the tab list.
     * Called on enable and can be called again on reload if needed.
     */
    public void setServerTabHeaderFooter() {
        Component header = Component.text("FoliaSkyblock", NamedTextColor.GOLD)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Skyblock", NamedTextColor.YELLOW));

        Component footer = Component.text("Play to Win", NamedTextColor.GRAY)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text("No Pay-to-Win", NamedTextColor.GREEN));

        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            p.sendPlayerListHeader(header);
            p.sendPlayerListFooter(footer);
        }
    }

    /**
     * Sets header/footer for a single player (e.g. on join after plugin enable).
     */
    public void setServerTabHeaderFooter(Player player) {
        if (player == null || !player.isOnline()) return;
        Component header = Component.text("FoliaSkyblock", NamedTextColor.GOLD)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Skyblock", NamedTextColor.YELLOW));

        Component footer = Component.text("Play to Win", NamedTextColor.GRAY)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text("No Pay-to-Win", NamedTextColor.GREEN));

        player.sendPlayerListHeader(header);
        player.sendPlayerListFooter(footer);
    }
}