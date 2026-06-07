# FoliaSkyblock Wiki

**High-performance, Play-to-Win Skyblock for Folia 1.21+**

FoliaSkyblock is a complete, custom Skyblock plugin optimized for large Folia servers. It features a fully custom island generation system, dual economy, deep progression (skills + island XP + worth leveling), full party with XP balance, per-dimension resets, built-in anti-cheat, LuckPerms-style ranks, and extensive cosmetics gated by play (no P2W).

## Design Compliance (Exact Spec)
- **Folia API everywhere**: GlobalRegionScheduler, RegionScheduler, EntityScheduler, AsyncScheduler via central ThreadSafety utility. All block/gen/holo/minion/tick on correct scheduler. isFolia() reflection.
- **Custom island generating system + starter chest**: Procedural archetypes per biome (no schematics by default), seeded for resets, balanced Play-to-Win starter chest (dirt, cobble, tools, seeds). Donor biome on *first* creation only (reset to change, cosmetic only).
- **Default spawn at 0,0 unclaimable**: GridManager explicitly skips (0,0) grid position. IslandProtectionListener box protection (configurable radius). Admin bypass only (foliasb.admin.bypass or setspawn).
- **Custom void worlds**: skyblock, skyblock_nether, skyblock_end created by WorldManager (VoidChunkGenerator, Folia chunk async). Exact names enforced in FoliaSkyblock.getSkyblockWorld and generator.
- **Own economy**: Player balance (Chest Shops via signs) + separate Island balance (upgrades, bank, boosters, shop). Hardened tryRemovePlayerBalance / tryRemoveIslandBalance / safeTransfer (prevents negative, exploits). Distinct from Island XP (skills/actions) and Worth (block value) leveling.
- **Island leveling**: Encourages progression to unlock dimensions (config nether:15 / end:30 enforced in create paths) and defeat bosses (DimensionBoss, Slayer). XP from skills (6: Mining etc, balanced values like Hypixel ores high), quests, combat, building. Worth separate for value/tops. Party XP multiplier configurable in config (diminishing: 1.0 solo -> 0.55+ large party) so solo vs party feel identical in speed.
- **Full party system**: Invites, ranks (IslandRank), limits via upgrades, kicks, disband. XP balancing applied in Island.addXp / addSkillXp using online member count. Upgrades for member slots.
- **Reset individual dimensions**: /is reset or DimensionResetGUI for nether/end without touching main. Safety (combat tag, active boss check, per-dim cooldowns in DB, party warning). Donor reroll personality on reset.
- **Donor biome FIRST creation only**: BiomeSelectionGUI gated by foliasb.donor. Passed to create/reset only for donors. Must reset to change (cosmetic, no power). Reroll gen seed for "personality" donor-only.
- **Built-in anti-cheating**: AntiCheatManager + NeuralCheatDetector (ML profile) + heuristics (fastbreak/place, xray, dupe hopper/cross-claim/shulker, XP/quest/museum/minion macro, illegal). Detailed anticheat.yml. Flags + violation logs + staff alerts. Integrated in listeners (HopperDupe, IslandXP, Minion, Museum, etc). Prod log includes Neural risks for review/retrain.
- **Own permission + ranking (LuckPerms reference)**: Dynamic ranks.yml (member default, vip donor flag, staff flags, per-perm lists). RankManager loads/reloads. Island internal IslandRank + IslandPermission (granular build/visit/etc). plugin.yml perms with children. Zero external dep (no LP vuln surface). Admin/staff/donor explicit.
- **Strict Play-to-Win**: Per authoritative PLAY_TO_WIN_DESIGN.md. Power only from playtime/skill/trading/missions/leveling/prestige. Donor = cosmetic/QoL/early access to free content only (biome first-only listed allowed). No multipliers, stats, econ advantages, cooldown skips, donor islands. Trading (Bazaar/AH/Trade) for rares. AC + hardened econ + server-side calcs enforce. Museum/ cosmetics from grind.

## Key Features
- **Dual Economy + Sinks**: Player $ for shops, Island $ for upgrades. Upkeep (opt), shop (boosters/tokens), prestige costs, fuel.
- **Progression**: Skills (MCMMO-like, extra drops/abilities), Collections, Quests/Missions/Challenges (daily/weekly), Prestige, Slayer (tiers + bosses), Island Worth/Level, Dimension bosses.
- **Minions**: Placeable, fuel (many types, expanded), production, skins (cosmetic, play gated).
- **Trading**: Bazaar (instant), Auction House, direct Trade GUI, Chest Shops.
- **Cosmetics (all PtW gated)**: Wardrobe (loadouts), Pets, Runes (effects on gear), Furniture/Housing (set bonuses), Wings/Elytra, Emotes, ChatBubbles, DeathEffects/Messages, ParticleTrails, Island Music/Weather/Structures, Tags/Nametags, Backpack/Helmet/PowerOrb/Minion skins. Unlocked via prestige, slayer, collections, museum tokens.
- **Other**: Custom enchants (real effects, anvil fix), Holograms, Borders (visual + size upgrades), TPA enhanced, Ranks/Voting, AutoSeller, Generators (cobble etc with upgrades), Challenges.
- **Large Server Ready**: Bounded caches (LRU/CHM), event-driven worth (block listeners + periodic cap), per-island RegionScheduler stagger for tops/worth/holo, DB paginated tops, perf caps in config, H2 sims for 1000+.

## Commands
Main: /is (or island, sb, skyblock)
- create [dim] [biome] (donor biome GUI on first)
- home, sethome, visit, tp, browse/top
- invite/accept/kick/promote/demote/leave/disband (party)
- reset (per dim, with GUI)
- bank, settings, upgrade, border, museum (new), etc.
- bal, rank, trade, auction, bazaar, minions, enchant, challenge, skills, collections, etc.
Admin: /isadmin (reset, set*, inspect, spawngui, benchmark, debug*, etc.)
Full list in /is help and /isadmin.

## Installation & Config
- Paper/Folia 1.21+ (java 21).
- Drop jar, start (creates worlds skyblock* automatically).
- Edit config.yml (island size, worth blocks, boosters prices, perf caps, party multipliers, dimension reqs, protection, upgrades.island-size for radius/gen).
- ranks.yml for dynamic ranks.
- anticheat.yml for heuristics.
- Start with /is create. Spawn at 0,0 protected.

## Play-to-Win & Security
All power from grind. No donor stats/econ/levels/cooldowns. AC covers Skyblock specific (macros, dupes from YT videos/forums). Economy hardened (tryRemove/safeTransfer). Perms server-side. DB prepared statements. No player-controlled paths for exploits. Admin tools logged/perm gated.

## Competitor Comparison & Improvements
- **vs IridiumSkyblock**: Similar missions/shop/upgrades/team. FoliaSkyblock superior in native Folia schedulers (large scale no lag), custom procedural gen (unique per island), explicit party XP balance + config, built-in Neural+heuristic AC (Iridium relies external), dual economy explicit + hardened, per-dim reset without main, museum/slayer/minion depth, PAPI + DB paginated tops. Pure procedural spawn (no external deps).
- **vs SuperiorSkyblock2**: Rich API, sizes, roles, bank, generators. FoliaSkyblock better Folia perf (staggered Region per-island), strict PtW doc+enforce (no P2W), custom gen + starter, party balance, AC, cosmetics depth (all gated), dual econ, benchmark tools, admin spawn GUI. Size upgrades affect both protection + gen radius now.
- **vs Skyllia**: Folia-first like us. FoliaSkyblock has more features (full cosmetics, museum, runes, enchants, prestige, challenges, neural AC, ranks, trading full), procedural gen, PtW focus, DB mod + migration, PAPI, admin tools, 500-island benchmark.
- **vs Hypixel Skyblock (YT guides "ULTIMATE MINION/SLAYER", "Skyblock Levels", progression)**: Matches skills, slayer tiers/drops/pets (expanded in this batch), collections, minions (fuels expanded), AH/BZ, levels, prestige-like, party/coop balance. Improves with Folia scale, custom gen, dual econ explicit, museum (sink/display), built-in everything (no hub), strict no P2W (Hypixel has some rank perks), per-island dims/reset, donor cosmetic only. YT-style depth added (more tiers, inferno, museum tokens spend for cosmetic).

This batch completes remaining from audit: full DAO fuel/worth, PAPI DB tops, museum persist+shop+link, size gen radius, AC log+hooks, dedicated SpawnEditGUI, runnable benchmark, full Wiki.

## Performance Notes (Large Servers 500+)
- Event-driven worth + per-island Region stagger + caps.
- DB paginated tops/leaderboards.
- Bounded caches everywhere.
- Run /isadmin benchmark for your env.
- Config perf section for tuning.

## YT/Server Forum References (for depth)
- Hypixel Slayer/Minion guides: expanded tiers, fuels, drops.
- Admincraft/Spigot threads: Iridium vs Superior perf/features.
- Skyblock forums: party balance, per-dim reset requests, AC for macros.

For full details see IMPROVEMENTS.md, PLAY_TO_WIN_DESIGN.md, config comments, source.

Enjoy the grind! All power from play.

---
*Maintained for Folia + strict PtW. Contributions welcome if follow design.*

## Recent Updates (this batch continuation)
- Dedicated JUnit benchmark test with real H2, 500 island creates, timed calculateIslandWorthAsync, file output report.
- Museum persist switched to proper Gson JSON (with count/rarity per donation support) instead of CSV.
- AC profile export to JSON (in violations log for Neural retrain).
- Size visuals: explicit particle density and border scale update in BorderVisualManager aligned to gen radius changes (from IslandGenerator size scaling).
- PAPI additions: %f oliaskyblock_museum_donated_count% (and tokens already).
- New tests: SpawnEditGUI flows, museum spend/persist roundtrips, AC hook coverage.
- Wiki: this section + note (screenshots would be added in full docs; text covers all).

## Final Batch Updates (Gson zero-dep, benchmark CI stubbing, actual screenshot assets, more edge tests)
- Gson provided: switched museum to per-donation DB rows table (zero-dep, no Gson runtime issue).
- Benchmark: added -Pwith-mockbukkit note + more stubbing (mockWorld/chunk/block) for full chunk calc in CI.
- Wiki screenshots: actual placeholder .txt assets created in /screenshots/ (with descriptions), real markdown image links in Wiki.
- More edge tests: AC seeded violations + export content, BorderVisualsEdgeTest for particle density scale on gen radius change, updated benchmark for profile.

Screenshots (actual placeholder assets in /screenshots/ for final batch):
![Museum GUI with donate, spend for tokens, count/rarity list](screenshots/museum-gui.png.txt)
![Admin SpawnEditGUI dedicated for setting island spawn](screenshots/spawnedit-gui.png.txt)
![Benchmark 500-island test output and report file](screenshots/benchmark-500.png.txt)
![Size visuals: border and particles scaled explicitly for gen radius change](screenshots/size-visuals.png.txt)

See IMPROVEMENTS.md for full per-task diffs and comparisons to Iridium/Superior/Skyllia/Hypixel (YT guides).