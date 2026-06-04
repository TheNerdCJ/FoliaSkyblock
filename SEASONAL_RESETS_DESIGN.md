# FoliaSkyblock — Seasonal Resets Design (Server-Wide 3-Month Cycles + Donor/Cosmetic Persistence)

**Status:** Design phase — no implementation started.  
**Date:** June 2026 (post aggregates/LRU/snapshot compression work)  
**Goal:** Enable quarterly server-wide "season" resets for fresh competitive events/leaderboards while preserving donor value (cosmetics, early unlocks, social ranks) and supporting seasonal-exclusive cosmetic releases to players and donors. Strict adherence to Play-to-Win (PLAY_TO_WIN_DESIGN.md) and existing Folia + DAO + async patterns.

## 1. Motivation & Requirements

- Server-wide reset every ~3 months (quarterly) to "wipe the slate" for new seasonal events, fresh leaderboards (/is top worth/level/members), prestige grinds, collections, museum, etc.
- **Non-negotiable: Persistence for "donor items"** — donors (VIP via ranks.yml `donor: true` + `foliasb.donor*` perms) and high-play players keep earned/purchased cosmetics across resets.
- Support **seasonal event cosmetic releases** (time-limited or event-gated visuals for all players + donor exclusives/variants/early access).
- Re-use as much existing infrastructure as possible:
  - CosmeticDAO + dozens of `player_*` / `player_*_collection` / `player_active_*` tables (already per-UUID, not per-island).
  - IslandDAO.deleteIsland + cleanupIslandData patterns (per-dim, but bulkable).
  - IslandWorthManager / tops / snapshots / LRU / dirty (post-reset they naturally start empty/fresh; pre-warm will populate from new activity).
  - Folia Region/GlobalRegionScheduler + ThreadSafety for any heavy clear/generation work.
  - CompletableFuture async DAO everywhere.
  - Existing per-player progression that feeds cosmetics (slayer tokens/kills, player_skills).
- Zero P2W creep: seasonal rewards = cosmetics, convenience, or limited non-power (e.g. small one-time tokens that everyone can grind equivalently later).

## 2. Current Persistence Boundaries (Audit Summary)

Full table list extracted from DatabaseManager.createTables() + migrations (v1–v12) + DAOs.

### 2.1 Island-Bound / Competitive Progress — **WIPE on Seasonal Reset**
These are tied to `islands` (owner_uuid + dimension) or `island_key` (grid-based) or grid+dim composites. Resetting these gives the "fresh season" feel.

- `islands`, `island_members` (party data per dim)
- `island_balances`, `island_banks`, `island_worth` (incl. the recent member_count / prestige_level / last_*_rank snapshots from aggregates work), `island_levels`, `island_skills`, `island_milestones`, `island_upgrades`, `island_fuel`, `island_boosters`
- `island_prestige`, `island_collections` (core play-to-cosmetic loop), `island_museum*`, `island_missions`, `island_shop_purchases`
- `island_settings`, `island_ratings`, `island_warps`
- `island_minions`, `minion_skin_assignments` (assignments only; ownership skins are player-owned — see below)
- `island_active_music/weather`, `island_placed_furniture`, `island_placed_structures`
- `player_dimension_resets` (cooldowns — can be cleared or carried; recommend clear for "new season, fresh cooldowns")
- Related: any in-memory caches in IslandManager (positionToIslandCache, playerIslands), IslandWorthManager (worthCache LRU, top* LRUs, dirty flags), PrestigeManager, CollectionManager, MuseumManager, MinionManager, IslandXPManager, etc.

**Post-wipe effect (good):** New /is create starts at level 1, worth 0, prestige 0, empty collections/museum, fresh tops. The recent shared `fetchRichPagedTopIslands` + persisted aggregates will just return empty pages until play happens. Snapshots/backfill will handle gracefully (0s).

### 2.2 Player-Owned / Social / Unlocks — **KEEP (this is the core of "donor item persistence")**
These are keyed only by `uuid` (or `player_uuid`). Survive any island/dim wipe today and will survive seasonal too with zero or minimal changes.

**Cosmetics (the big one — 20+ tables via CosmeticDAO + managers):**
- `player_wardrobe*` + collection
- `player_pets*`, `player_pet_collection`, `player_pet_skins`, `player_active_pet`
- `player_tags*`, `player_tag_collection`, `player_active_tag`
- `player_elytra_wings*` + collection + active
- `player_runes*` + collection
- `player_helmet_skins`, `player_death_effects`, `player_backpack_skins`, `player_power_orb_skins`, `player_minion_skins`
- `player_island_furniture` (ownership), `player_island_structures` (ownership), `player_island_music` (ownership), `player_island_weather` (ownership)
- `player_overhead_cosmetics`, `player_emote_cosmetics`, `player_emote_triggers`, `player_chat_bubble_cosmetics`, `player_accessories`
- `player_particle_trails`, `player_active_trail`
- `player_death_messages`

**Other player progress that feeds cosmetics / PtW:**
- `player_skills` (MCMMO-style — earned, feeds some rewards)
- `slayer_kills`, `slayer_tokens` (weekly_tokens etc. — prestige/slayer shop is a major cosmetic gate)
- `player_ranks` (VIP/donor flag lives here or in external perms; upvotes/votes table)
- `player_balances` (personal chest-shop money — policy decision below)
- `pending_items` (transient claims — keep or drain on reset)

**Donor-specific today (mostly non-DB):**
- Permission `foliasb.donor` / `foliasb.donor.biome` (plugin.yml + ranks.yml VIP has `donor: true`).
- In IslandManager: `isDonor` check for biome choice on create + `rerollPersonality` (generation_seed on `islands` table — this will be wiped on reset, which is fine; donor can reroll again on new-season create).
- Early access patterns in prestige/slayer grants (already in code for trails etc.).

**Result:** Donor "items" (cosmetic ownership, active looks, wardrobe sets, pet skins, furniture collections, etc.) are **already designed to be persistent across per-player island resets**. A server-wide seasonal data wipe that only touches island_* + islands tables will preserve them automatically. This is a huge advantage — the feature request aligns with existing architecture.

### 2.3 Global / Market / Admin — Special Handling
- `auctions`, `bazaar_orders`: Clear active/unsold or force-expire on reset (prevents old-season capital from dominating new economy). Or archive.
- `votes`: Keep (social).
- `punishments`, `bug_reports`: Keep (moderation history across seasons).
- `holograms` + lines: Keep (admin tools) or have a "seasonal hologram" subset.
- `pending_items`: Drain/claim-forced or keep.
- `schema_version`: Keep.

### 2.4 Physical World State (the hidden gotcha)
- Worlds are custom void (`skyblock`, `skyblock_nether`, `skyblock_end`) via WorldManager + VoidChunkGenerator. Persistent single set of worlds.
- Islands placed on 512-grid spiral (GridManager). `usedPositions` loaded from `SELECT ... FROM islands` on startup.
- Generation (IslandGenerator): fully RegionScheduler, seeded (supports donor reroll via generation_seed), places terrain + starter chest. No external schematics by default.
- On pure DB wipe of `islands`:
  - GridManager will see 0 used → re-allocates from layer 0 on next creates.
  - Physical blocks from prior season remain at the same centers.
  - New gen on overlapping grid will overwrite (good for owner), but "ruins" of unclaimed old plots + any lingering entities (old minions as armorstands, furniture as blocks? furniture is blocks + data) will be visible.
- Minions/furniture use placed entities/blocks tied to island_key.

**Conclusion from audit:** Pure data wipe is 80% of the value and reuses 100% of existing delete/cleanup. Physical freshness is the only major extra work.

## 3. Design Options

### Option A — Minimal Data-Only Wipe (Fastest to ship, lowest risk)
- Selective DB deletes only (new `wipeSeasonalProgress()` in IslandDAO or a dedicated SeasonalWipeDAO helper that calls the existing key/grid cleanups in batch).
- Clear all relevant in-memory caches + mark all dirties + force top LRU evict.
- Reset GridManager used set (reload after wipe).
- Announce + manual admin trigger.
- Old physical plots remain as ruins; new creates overlap/re-gen on demand. Players experience "fresh data" immediately on new island.
- Seasonal cosmetics via simple grant path (below) that writes to existing player_* tables.
- **Pros:** Small surface, reuses deleteIsland + cleanupIslandData almost verbatim, no world lag, easy rollback (just don't wipe the .db backup).
- **Cons:** Visual "old season ruins" until plots are re-claimed or manually cleaned.
- **When to choose:** MVP for first seasonal event. Can upgrade to B later.

### Option B — Data Wipe + Staggered Physical Plot Clear (Recommended)
- Same DB + cache work as A.
- Pre-wipe (or post): `List<GridPosition> oldGrids = islandDAO.listAllIslandGrids();`
- For each grid in batches (50–100 at a time):
  - Use `plugin.getServer().getRegionScheduler().execute(plugin, centerLoc, () -> { clearPlot(center, size); removeEntities(center); });`
  - `clearPlot`: compute bounding box from islandSize + upgrades (or fixed), set blocks in Y range to air (or a clean void floor layer matching gen base). Folia-safe per-region.
  - After clears complete (countdown latch or future chain), proceed to grid reload + "season started" broadcast.
- Add progress logging (`[Season] Cleared 342/1247 plots...`).
- Optional: run the clear pass in a low-priority background Global repeating task with sleep between batches.
- **Pros:** Fresh visual slate for the new season. Reuses exact RegionScheduler pattern from IslandGenerator + ThreadSafety.
- **Cons:** CPU/IO heavy for 1000+ islands (mitigated by stagger + maintenance window). Entities cleanup needs care (ArmorStand minions, TextDisplays for overhead, etc.).
- **When to choose:** Default for production seasonal resets. Matches "server wide reset" player expectation.

### Option C — Full Seasonal World Isolation (Maximum safety, high complexity)
- Maintain parallel world families: `skyblock_s1`, `skyblock_nether_s1`, ... or date-based.
- On reset: create new worlds (or pre-create), atomically switch "current" names in WorldManager/config + IslandGenerator.getWorldForDimension + FoliaSkyblock getters.
- Old worlds left mounted read-only for "museum" visits or archive (optional).
- Grid allocation per-season or global but worlds separate.
- **Pros:** Zero overlap risk, previous seasons fully preserved as snapshots, easy "visit season 3 museum island" later.
- **Cons:** Storage (void worlds aren't tiny with 1000+ plots + entities), WorldManager/IslandManager/Generator/BossManager all need current-season indirection, multi-world edge cases (portals? per-world boss events?), more config, backup strategy complexity. Holograms/ratings etc. would need season scoping.
- **When to choose:** Only if you plan "legacy season servers" or heavy event archiving. Probably overkill.

**Recommendation:** Start with **B** (or A as stepping stone). The existing per-dim reset + generator + aggregates work already gives you 70% of the machinery.

## 4. Seasonal Cosmetic Releases & Donor Persistence Mechanics

### 4.1 How Donor Items Already Persist
- All cosmetic ownership/active state lives in player-keyed tables (see 2.2).
- Unlock flows (ParticleTrailManager.unlockTrail, PrestigeManager grants, SlayerShop purchases, Collection milestone rewards, etc.) ultimately call CosmeticDAO.save* or direct equivalents → per-UUID rows.
- On player join post-reset: the cosmetic managers load exactly as today from the unchanged player_* tables.
- Wardrobe, PetGUI, all the *CosmeticGUI tabs continue to show owned items.
- Donor biome reroll / personality is re-available on the new season's first create (the islands.generation_seed is wiped, but permission check still works).
- Result: "donor items" (visuals, collections, actives) carry over with **no code change** for the persistence part.

### 4.2 Releasing Seasonal Event Cosmetics
Add lightweight grant + tracking without new per-cosmetic tables (reuse existing ownership tables to keep it simple and consistent with current design).

- **Table addition (migration v13):** 
  ```sql
  CREATE TABLE IF NOT EXISTS player_seasonal_grants (
    uuid TEXT,
    season_id TEXT,           -- e.g. "2026-Q3" or "s4"
    category TEXT,            -- "trail", "pet_skin", "furniture", "helmet_skin", ...
    cosmetic_id TEXT,
    granted_at INTEGER,
    source TEXT,              -- "event", "donor_reward", "prestige_season_bonus", ...
    PRIMARY KEY (uuid, season_id, category, cosmetic_id)
  );
  CREATE INDEX IF NOT EXISTS idx_seasonal_grants_season ON player_seasonal_grants(season_id);
  ```
  (Or make category+id a composite that maps to existing player_ tables.)

- **CosmeticDAO extensions (or a small SeasonalGrantDAO):**
  - `grantSeasonalCosmetic(UUID, String category, String id, String seasonId, String source)` — does the INSERT OR IGNORE into the right `player_<category>` table (or delegates to existing save methods) + logs the grant row.
  - `getSeasonalGrantsForPlayer(UUID, String seasonId)` for "what did I earn last season?"
  - `getPlayersWhoReceived(String seasonId, String category)` for admin audit.

- **Grant surface (admin + event):**
  - New (or under /isadmin) command: `/season grant <category> <id> [--to <player|all|donors|perm:foliasb.season.vip>] [--season 2026-Q3]`
    - Resolves targets (online + offline via UUID lookup if needed).
    - For "donors": filter `player.hasPermission("foliasb.donor")` or `plugin.getRankManager().getPlayerRankData(uuid).isDonor()`.
    - Async grant via DAO + ThreadSafety notify for onlines ("§a§lSeasonal Reward! §7You received <display>").
  - Programmatic: `SeasonManager.grantSeasonalReward(uuid, category, id, season, source)` callable from events, crates, quests, prestige on season-start bonus, etc.
  - Crate integration: new CrateType.SEASONAL or reward type that calls the grant.

- **Player-facing:**
  - Optional "Seasonal Rewards" claim GUI or just auto-grant + chat.
  - PAPI: `%foliasb_current_season%`, perhaps `%foliasb_seasonal_unlocks_count%`.
  - In Wardrobe or a new "Seasonal" tab/filter: highlight items granted this season.
  - Collection / prestige can have "seasonal milestone" variants that call the grant path.

- **Donor-specific releases examples (visual only):**
  - Exclusive trail/particle variants or colors.
  - Early access to next furniture set or a "donor signature" accessory.
  - Limited pet skin only available to donors this quarter.
  - Special death effect or chat bubble frame.
  - All gated behind the same "earned via play or donor early" philosophy as current prestige/slayer.

### 4.3 Season Identity
- Persist `current_season_id` (TEXT like "2026-Q3" or integer + lookup table).
- Simple meta table or just a single-row `server_meta (key TEXT PRIMARY KEY, value TEXT)`.
- On reset: bump season, record end date of prior + start of new in a `seasons` history table (optional but nice for "Season 4 Leaderboard" archives if you later snapshot tops).
- Config: `seasonal.length-months: 3`, `seasonal.announce-days-before: 14`, `seasonal.wipe-player-balances: false` (default keep personal money; island economy always wiped).

## 5. Risks & Mitigations

1. **Data loss / partial failure during wipe**
   - Mitigation: Admin-only with multi-step confirmation (`/season reset --confirm WIPE-2026-Q3`). Recommend host-level .db backup + `VACUUM` before. Implement as a sequence of DELETEs with rowcount logging + a "reset_id" audit row. Idempotent (DELETE WHERE ...; safe to re-run). No single giant transaction (SQLite limits); accept best-effort with post-reset validation queries ("SELECT COUNT(*) FROM islands" should be 0 for active dims).

2. **Folia / thread safety during global operation**
   - Mitigation: All DB work async via existing executor/CompletableFuture. Physical clears strictly via RegionScheduler per plot center (never synchronous world edits). Use a "seasonResetInProgress" volatile flag checked in hot paths (create, reset personal, join island, worth calc, etc.). Global announce via ThreadSafety. No player actions during window (or teleport to safe spawn).

3. **Play-to-Win violation via seasonal rewards**
   - Mitigation: Enforce via the existing PLAY_TO_WIN_DESIGN.md + review checklist. All seasonal grants go through the cosmetic path (no stat items, no multipliers, no exclusive powerful gear). Donor grants = visual early access or variants only. Add explicit note in the design doc and a code comment guard in the grant method.

4. **Grid / physical overlap + lingering state**
   - Mitigation: Option B clear pass (or accept ruins in A). Explicitly clear minion armorstands, any TextDisplay overheads, floating furniture entities if any, boss entities. After wipe + clear, force a GridManager.reloadUsedPositions() (now empty). Test with 2–3 islands locally first.

5. **Economy / market carry-over**
   - Mitigation: Config flag for player_balances. Always clear island_* economy (banks/balances). For auctions/bazaar: on reset, mark all active as expired/sold-false or DELETE unsold + refund via pending_items (best effort). Announce "old orders have been closed, balances returned where possible".

6. **Caches, snapshots, tops, PAPI, holograms stale**
   - Mitigation: Post-wipe explicit `islandWorthManager.clearAllForSeasonReset()` (evict LRUs, mark all dirty, reset last* stamps). Same for other managers. Pre-warm task will see empty and behave. Holograms showing tops will naturally show "no data yet" or a "Season X begins..." placeholder. Add a SeasonManager.isSeasonActive() or just rely on data presence.

7. **Player disruption**
   - Mitigation: Multi-day warning broadcasts (configurable task). Optional "soft lock" on island actions 1h before. On trigger: mass disband parties (reuse existing leave/kick paths + saveMemberCount=0), teleport all to spawn or safe area, optional kick with "Season reset in progress — rejoin in 10m". Log everything.

8. **Long-term data bloat (old grants, history)**
   - Mitigation: Optional cleanup job for grants older than N seasons, or just let it grow (rows are tiny: uuid + 4 strings + ts).

## 6. Minimal Implementation Plan (Reuse-First, No Gold-Plating)

**Phase 0 (this design) — done when user approves a path.**
- This doc + cross-refs in IMPROVEMENTS.md.
- Ask user: Option A/B/C? Scope for first cut (just data wipe + grant cmd, or include physical clear + PAPI + history table)? Any policy decisions (wipe player money?).

**Phase 1 — Core Wipe + Season Identity (small, high value)**
- New package or manager: `com.thenerdcj.season.SeasonManager.java` (light — holds currentSeasonId, resetInProgress flag, announce scheduler, grant facade).
- DB: bump CURRENT_SCHEMA_VERSION, add `seasons` history table + `player_seasonal_grants` (or reuse + log table) + optional `ALTER TABLE islands ADD COLUMN created_season TEXT`.
- IslandDAO (or new helper): `CompletableFuture<Integer> wipeAllIslandProgress()` — returns rows affected or count of islands processed. Internally re-uses the keyCleanups + gridCleanups lists from cleanupIslandData, plus explicit DELETEs for islands, island_members, museum, placed, prestige, collections, minions, etc. Batch or paginated if needed for 1000+ (reuse spirit of tops pagination).
- Also clear: auctions/bazaar (configurable), player_dimension_resets (for fresh cooldowns).
- IslandDAO: `List<GridPosition> getAllIslandGrids()` (simple SELECT, for clear pass and grid reset).
- Add `wipeForSeason()` entry point that does the DB work + fires a SeasonResetEvent (for listeners).
- FoliaSkyblock: wire SeasonManager.
- AdminCommand or new: basic `/isadmin seasonreset` stub with confirmation (calls the wipe, logs, broadcasts).
- Post-wipe: call clear methods on WorthManager (add `clearAllForNewSeason()` that does LRU.clear, mark*Dirty, refresh snapshots to 0s, etc.), similar for 5–6 other managers.
- GridManager: expose `resetForNewSeason()` (clear set + reload — will be empty).
- Verify: mvn clean compile/test-compile. Add a note in TopGUITest or a new small H2 seasonal test later.

**Phase 2 — Physical Freshness + Scheduling Polish (if B chosen)**
- Implement `clearIslandPlot(GridPosition, World)` using RegionScheduler + block iteration (respect island size from upgrades or config).
- Entity cleanup: world.getNearbyEntities(...) for ArmorStand (minion criteria), TextDisplay, etc. Remove if no longer valid.
- SeasonManager: `beginSeasonalReset()` that (1) collects grids, (2) async DB wipe, (3) schedules staggered clears via a repeating task or CompletableFuture chain with backpressure, (4) on complete: reset grids/caches, bump season, final broadcast.
- Config section `seasonal:` with the keys mentioned.
- Warning task: low-freq Global repeating that checks "days until reset" if configured.

**Phase 3 — Cosmetic Release Tooling + Exposure**
- CosmeticDAO: `grantSeasonal(...)` + helpers that know the mapping (category → table/column).
- Or a tiny `SeasonalCosmeticGrantService`.
- SeasonManager.grant API + logging to grants table.
- Command wiring + permission `foliasb.admin.season`.
- PAPI expansion additions for current season + (optional) "granted this season".
- Optional: simple "Season X" prefix in tab via existing RankManager or ChatManager hooks.
- Update COSMETIC_ADVANCEMENTS.md and PLAY_TO_WIN_DESIGN.md with seasonal notes.

**Phase 4 — Hardening & Docs**
- Safety: dry-run mode (`--dry-run` prints DELETE counts without executing).
- Audit log: write a `season_resets` row (started_at, ended_at, islands_wiped, triggered_by, season_before/after).
- Validation post-reset: assert islands==0, worth==0 for sample, cosmetics for a test player still load.
- Wiki / command help updates.
- H2 test exercising wipe + grant roundtrip (similar to TopGUITest pattern).
- Announce in release notes + the living docs.

**Cross-Refs to Existing Work (leverage heavily):**
- IslandDAO cleanupIslandData + deleteIsland (direct model for bulk).
- Recent v12 aggregates + member_count/prestige snapshots + shared fetch builder (post-reset tops are automatically clean + fast).
- LRU work in IslandWorthManager (evict on reset).
- CosmeticDAO generic saveOwned + specific (trails/pets/etc.) — grants just call them.
- ThreadSafety + RegionScheduler usage in gen, worth, borders, etc.
- Per-dim reset safety (combat/boss/party warnings) as inspiration for global safety.
- DatabaseMigration pattern (v13 for new tables/columns).
- PtW audit process from prior sessions.

**Estimated Scope for First Usable Cut (A or B-lite):** 4–8 focused files (DAO + Manager + 1–2 command bits + migration + 1 config + this doc updates). Reuses patterns so low bug surface.

## 7. Open Decisions for User

1. Preferred option (A data-only, B + clears, or C worlds)?
2. Policy on `player_balances` in seasonal wipe? (default: keep, as personal earned money; document it).
3. Do we want a `created_season` stamp on islands for "this season only" vs all-time tops later, or keep tops always "current season" (fresh data after wipe)?
4. Scope of first implementation pass (just the wipe + basic grant command, or full physical + PAPI + history)?
5. Any "donor items" that are **not** cosmetics/unlocks (e.g. special crate keys as ItemStacks with PDC that should explicitly survive, or actual furniture items in inventory)? If so, list them — most appear to be DB unlocks today.
6. Desired trigger (purely manual admin with confirmation, or also time-based with config + heavy warnings)?

Once a path is approved, we can enter implementation (following the same "create/edit as needed + mvn verify + update IMPROVEMENTS.md + new suggestions" loop used for the tops/aggregates work).

## 8. References in Codebase (as of this design)

- Persistence: DatabaseManager.java (createTables + many tables), DatabaseMigration.java (v12 aggregates), IslandDAO.java (deleteIsland, cleanupIslandData, recordIslandReset, rich tops), CosmeticDAO.java (all player_*), BalanceDAO/IslandLevelDAO/PrestigeDAO/SlayerDAO/etc.
- Reset logic: IslandManager.java:466 (resetIslandWithBiome + safety), 512 (delete + create), GridManager.java (loadUsed + spiral).
- Donor: IslandManager.java:125 (isDonor perm), 509 (reroll), plugin.yml + ranks.yml (VIP donor:true), IslandCommand.java biome flows.
- Cosmetics: COSMETIC_ADVANCEMENTS.md (full list), 40+ files under cosmetic/, wardrobe/, pets/, wings/, runes/, tags/ etc. + WardrobeManager.
- PtW: PLAY_TO_WIN_DESIGN.md (mandatory review for any seasonal reward).
- Large-scale patterns we must preserve: IslandWorthManager (LRU + dirty + snapshots), IslandTopGUI + pagination, Folia tasks in FoliaSkyblock, async DAO everywhere.

This design keeps the spirit of the project: heavy reuse, Folia-first, persistence-compressed where it matters (tops survive the pattern), strict PtW, and cosmetic depth as the donor value prop.

Next action: user feedback on options + scope → then implement (or spawn sub-plan).
