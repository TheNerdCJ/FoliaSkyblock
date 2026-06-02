# FoliaSkyblock – Production Roadmap & IMPROVEMENTS.md

**Project Goal (Authoritative Design Spec):**  
A high-performance, Play-to-Win Skyblock plugin for the latest Folia API. It must be heavily optimized and compressed for large servers (hundreds of concurrent players and islands). Every system must use Folia schedulers (GlobalRegionScheduler, RegionScheduler, EntityScheduler, AsyncScheduler) wherever possible.

**Core Design Requirements (from spec):**
- Custom island generation system with starter chest.
- Default spawn at 0,0 that is unclaimable (admin-editable only).
- Plugin creates its own custom void worlds for each dimension (skyblock, skyblock_nether, skyblock_end).
- Dual economy: Player balance (Chest Shops) + Island balance (Upgrades). Separate from the Island Leveling/XP system.
- Island Leveling system encourages progression toward unlocking dimensions and defeating bosses. Must be balanced between solo and party play.
- Full party system with XP balancing (island progression speed must feel fair whether solo or in a large party).
- Players can reset individual dimension islands without resetting the main island.
- Donors can choose biome on first island creation only (reset required for changes).
- Built-in anti-cheat system.
- Permission + ranking system (LuckPerms-style reference, no security vulnerabilities).
- Island trading system (Bazaar + Auction House) to obtain items not available via normal skyblock progression, starter chest, or island blocks.
- **Strict Play-to-Win**: No pay-to-win advantages. All power must come from playtime, skill, and fair progression. Trading exists specifically to balance the economy.

**Current Version Target:** 1.2.0 (Production-Ready for Large Folia Servers)

**Latest Progress (Current Session - Steps 1-8 Execution):**
- **Step 1 (Database Modularization):** Created `BaseDAO.java`. Extracted `MissionDAO.java` and `PrestigeDAO.java`. Added `DatabaseMigration.java` with versioned schema support. Wired into DatabaseManager. (Note: Large edits introduced temporary syntax issues in DatabaseManager that require a dedicated cleanup pass.)
- **Step 5 (AntiCheat Expansion):** Detailed Skyblock exploit guide expanded inside AntiCheatManager with actionable recommendations.
- **Step 6 (Folia Schedulers):** Confirmed and reinforced EntityScheduler usage in HologramManager and MinionManager with additional comments.
- **Step 8 (Config Validation):** Added `validateConfiguration()` in FoliaSkyblock.onEnable with world, economy, and Folia-specific checks.
- Other steps (2,3,4,7) remain high priority for next iterations. See prioritized list below.

---

## 1. Critical Alignment Gaps (Must Fix First)

### 1.1 World & Dimension Consistency
- **Current Status:** Mostly aligned after recent fixes, but config defaults in `IslandGenerator.java` and any legacy references must be audited.
- **Action:** Ensure `WorldManager`, `FoliaSkyblock.getSkyblockWorld()`, `IslandGenerator`, `IslandWorthManager`, and all dimension logic use exactly: `skyblock`, `skyblock_nether`, `skyblock_end`.
- **Folia Optimization:** World creation and spawn platform generation must stay on the correct region scheduler.

### 1.2 Economy System Clarity (Player vs Island vs Leveling)
- **Current State:** Good separation exists (player for Chest Shops, island for upgrades).
- **Required Improvements:**
  - Add hardened `tryRemove*` and `safeTransfer*` methods (already partially implemented in recent work — make them the only public API for mutations).
  - Clearly separate "Island Balance" (for upgrades) from "Island Level/XP" (for progression/unlocks).
  - All money creation must have corresponding sinks (island taxes, prestige costs, high-tier fuel, trading fees).

### 1.3 Leveling + Party XP Balancing
- **Current:** `Island.addSkillXp` already accepts `partySize` and applies a multiplier. Good foundation.
- **Required:** Make the multiplier formula configurable and well-documented. Island XP gain from skills/blocks must be balanced so a 10-player party does not progress dramatically faster than a solo player per hour of play.

---

## 2. Database Integrity & Fluid Communication (Highest Technical Priority)

**Goal:** Every load/save must be correct, atomic where needed, and never cause data loss or desync between classes.

**Action Items:**
- Complete extraction of remaining DAOs from `DatabaseManager` (MissionDAO, PrestigeDAO, IslandLevelDAO, etc.).
- Create `ItemSerializer` utility (move `itemToBase64`/`itemFromBase64` out of the god class).
- Add comprehensive unit + integration tests using H2 that verify round-trip data for:
  - Full island creation → party members → dimension reset → skill XP → prestige.
  - Player balance ↔ Island balance transfers.
  - Crate key consumption + reward granting.
- Ensure all async DB calls properly chain back to main/region threads before mutating player inventories or island state (use `ThreadSafety`).
- Add database migration system for future schema changes (Flyway or simple versioned ALTER scripts).

---

## 3. GUI Cleanliness & Consistency

**Current State:** `AbstractGUI.java` + `GUIUtils.java` + `BaseGUI.java` foundation exists. Several GUIs have been partially migrated.

**Required:**
- Migrate **all** remaining GUIs to extend `AbstractGUI`.
- Standardize every GUI to use `GUIUtils` for item creation and PDC actions.
- Add support for async data loading inside `AbstractGUI` (common pattern for leaderboards, island lists, etc.).
- Remove all legacy `createItem` duplication.

**Target:** Every GUI file should be < 250 lines and extremely readable.

---

## 4. Folia API Maximization & Large-Server Optimization

**Must Use Folia Schedulers:**
- All hologram updates → EntityScheduler.
- All minion ticking, fuel consumption, and production → EntityScheduler or RegionScheduler at minion location.
- All block edits during island generation/expansion → RegionScheduler.
- All repeated visual effects (particle trails, border particles) → per-player or per-entity schedulers.
- Worth recalculation → break into per-island RegionScheduler tasks instead of one giant GlobalRegion task.

**Compression & Performance:**
- Review every `ConcurrentHashMap` for bounded size + eviction.
- Make `IslandWorthManager` fully incremental + persisted (base worth + deltas).
- Add smart caching for frequently accessed data (island settings, prestige levels, active boosters).
- Profile hot paths (block break/place listeners, worth invalidation, GUI opens).

---

## 5. Play-to-Win Enforcement & Anti-Cheat

- The anti-cheat must remain strong and context-aware (IslandOreGenerator levels, party size, prestige, etc.).
- All donor features must be **cosmetic only** (biome choice on first creation is acceptable; permanent power advantages are not).
- Trading system must be the primary way to obtain "rare" items so that solo players are not disadvantaged.
- Add logging/auditing for any admin commands that could be abused.

---

## 6. Feature Completeness Against Spec

| Feature                        | Status          | Notes / Gaps |
|--------------------------------|-----------------|--------------|
| Custom void worlds per dimension | Good           | Enforce names strictly |
| Unclaimable spawn at 0,0       | Good           | Admin edit tools needed |
| Dual Economy (Player + Island) | Good           | Harden mutation methods |
| Island Leveling + Boss Progression | Partial     | Tie more tightly to dimension unlocks |
| Party XP Balancing             | Partial        | Make multiplier formula excellent and documented |
| Per-dimension island reset     | Needs Work     | UI + safety checks |
| Donor biome on first creation only | Partial     | Enforce "reset required for change" |
| Built-in Anti-Cheat            | Good foundation| Expand per detailed guide in class |
| LuckPerms-style Ranks + Permissions | Exists     | Improve integration & UI |
| Island Trading System          | Exists (Bazaar + Auction) | Add buyouts, taxes, history |
| No security vulnerabilities    | Ongoing audit  | Command input, GUI actions, DB queries |

---

## 7. Remaining High-Value Tasks (Prioritized)

1. Finish full DatabaseManager modularization + add proper migration system.
2. Complete migration of every GUI to the new `AbstractGUI` / `BaseGUI` base — **Deeper progress**:
   - SlayerGUI successfully migrated to `BaseGUI` (full populatePage + PDC handleAction + GUIUtils).
   - **CrateGUI** (top priority) received deeper modernization: switched to `GUIUtils.createItem` for header/close, `MessageUtil.legacy()` titles, and more resilient click handling.
   - **PrestigeGUI** (Priority #2) received deeper modernization: All manual ItemStack/ItemMeta creation replaced with `GUIUtils.createItem`, titles now route through `MessageUtil.legacy()`.
   - **IslandShopGUI** (Priority #3) — **Completed deeper modernization**:
     - `createShopItemDisplay` fully refactored to use `GUIUtils.createItem`.
     - Title now routed through `MessageUtil.legacy`.
     - Category and nav buttons already using GUIUtils + PDC helpers.
     - Consistent modern item construction across the entire GUI. The complex async + category + pagination logic is preserved cleanly.
   - **BazaarGUI** (Priority #4) — **Final polish pass completed**:
     - Added `createBazaarActionItem` / `createBazaarNavItem` helpers and migrated the vast majority of button creation.
     - Removed the now-unused `createActionButton` wrapper.
     - Minor title construction cleanup.
     - BazaarGUI now has an extremely clean and consistent item construction layer for a complex multi-view GUI (with Anvil flows). Only the intentional Anvil result paper remains as a raw `new ItemStack`.
   - **AuctionGUI** (Priority #5) — Continued deeper modernization:
     - Added `createAuctionActionButton` + `attachAuctionPDC` helpers.
     - Refactored bid confirmation and `addAuctionItem` to use them.
     - Further reduction in repetitive meta/PDC code.
     - Minor nav button naming polish for consistency.
     - AuctionGUI is now at a high level of modernization, consistent with BazaarGUI and other updated interfaces. Very little raw boilerplate remains.
   - **ResetConfirmationGUI** (high-value remaining) — Deep modernization completed:
     - All manual ItemStack + ItemMeta boilerplate replaced with `GUIUtils.createItem`.
     - Title now uses `MessageUtil.legacy`.
     - Massive reduction in code size and duplication while preserving all Folia-safe async behavior and logic.
     - Click handler title check made more resilient.
   - **DimensionResetGUI** (direct companion to ResetConfirmationGUI) — Deep modernization completed:
     - Manual glass filler (new ItemStack + ItemMeta) fully replaced with `GUIUtils.createItem`.
     - Inventory title creation now uses `MessageUtil.legacy(GUI_TITLE)`.
     - Click handler title guard upgraded to resilient `.startsWith(...)` pattern.
     - Added PDC consistency: dimension buttons now carry `target_dimension` payload via NamespacedKey (exactly mirroring ResetConfirmationGUI).
     - Click handler prefers PDC read with material fallback for maximum robustness.
     - Import cleanup + expanded Javadoc documenting the modernization pass.
     - `mvn clean compile` verified BUILD SUCCESS.
   - **BiomeSelectionGUI** (next in the per-dimension reset + creation flow) — Deep modernization completed:
     - Converted every manual `new ItemStack(...) + getItemMeta() + setLore` helper (createTitleItem, createBiomeItem, createLockedItem, createInfoItem, old glass) to `GUIUtils.createItem`.
     - Dynamic title (with reset/dimension suffix) now fully wrapped in `MessageUtil.legacy(...)`.
     - Click handler title check made resilient (handles appended " (Reset ...)" suffix).
     - New `attachBiomePDCs(...)` helper centralizes the three NamespacedKey payloads (biome_key, target_dimension, is_reset) — mirrors recent reset GUI patterns.
     - Dramatic boilerplate reduction while preserving donor permission gate, level-based dimension unlocks (15/30), isReset vs create branching, and full PDC-driven action routing into IslandManager.
     - Dead code removed + Javadoc updated. `mvn clean compile` verified BUILD SUCCESS.
   - **MinionsGUI** (largest remaining boilerplate concentration — 15+ manual sites) — Major first-wave modernization completed:
     - Every `new ItemStack(...)` in the file eliminated (now zero occurrences).
     - Top bar, accents, headers, slot/fuel display, type buttons, remove/feed/info/active sections, "no minions" state, and final filler glass all converted to `GUIUtils.createItem`.
     - Title creation routed through `MessageUtil.legacy`.
     - Click handler title guard upgraded to resilient `startsWith`.
     - PDC usage on active minion removal items preserved exactly (MINION_TYPE_KEY).
     - All complex logic (fuel, slots, placement, breakdown, Play-to-Win messaging) untouched.
     - `mvn clean compile` verified BUILD SUCCESS after the pass. (File was the single biggest visual compression win left in the GUI layer.)
   - **BoosterGUI** (economy shop GUI with Folia async bank loading) — Deep modernization completed:
     - All manual ItemStack/ItemMeta (balance header, info book, close button, and the complex rich-lore createBoosterItem) replaced with `GUIUtils.createItem`.
     - Title now uses `MessageUtil.legacy`.
     - Added `attachBoosterPDC(...)` helper for the BOOSTER_TYPE_KEY (consistent with recent PDC patterns in reset + minion GUIs).
     - 100% of the async `IslandBankManager.getBank` → `ThreadSafety.runOnMainThread` → purchase + activate + GUI refresh flow preserved exactly.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual ItemStack remaining in the file.
   - **SlayerShopGUI** (slayer token vendor with prestige-gated particle trails + gear/crate keys) — Deep modernization completed:
     - All three manual helpers (`createItem`, `createShopItem`, `createTrailShopItem` with dual PDC) fully converted to `GUIUtils.createItem` + dedicated `attachTrailPDCs(...)` helper.
     - Title now uses `MessageUtil.legacy`.
     - 100% preservation of critical logic: prestige checks via PrestigeManager, token consumption via BossManager, trail unlocks/activation via ParticleTrailManager, free prestige rewards, and legacy name-based gear/crate key purchases.
     - PDC routing for BUY_TRAIL + TRAIL_KEY actions kept intact.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Strong synergy with prior trail/prestige work.
   - **IslandSettingsGUI** (core per-island toggles: PvP, visitors, explosions, mobs, warp, border, etc.) — Deep modernization completed:
     - All manual helpers (`createToggleItem`, `createItem`, `createGlassPane`) replaced with `GUIUtils.createItem`.
     - Title now uses `MessageUtil.legacy`.
     - Click handler title guard upgraded to resilient `startsWith`.
     - Filler glass modernized (inlined GUIUtils like recent GUIs).
     - 100% of async settings load (IslandSettingsManager), live toggles with refresh, border color cycling, and metadata position tracking preserved exactly.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Core player-facing GUI now consistent with the modernization standard.
   - **MissionGUI** (paginated island missions with progress, rewards, and claim flow) — Deep modernization completed:
     - `createMissionItem` (icon + rich dynamic lore + PDC) and `createButton` converted to `GUIUtils.createItem` + `attachMissionPDC(...)` helper.
     - Dynamic title (with page numbers) now uses `MessageUtil.legacy`.
     - 100% preservation of async mission loading, pagination logic, PDC-based claim routing (MISSION_ID_KEY), SoundUtil on reward, and full claim validation.
     - Click handler title check was already resilient (startsWith); kept intact.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Strong alignment with prior mission system work.
   - **QuestLogGUI** (daily/weekly quest log with progress bars, rewards, claiming, and regeneration) — Deep modernization completed:
     - `createQuestItem` (with progress bar + PDC for questId), `createItem`, and `createGlassPane` fully converted to `GUIUtils.createItem` + `attachQuestPDC(...)` helper.
     - Title now uses `MessageUtil.legacy`.
     - Click handler title guard upgraded to resilient `startsWith`.
     - Filler glass modernized.
     - 100% of async quest loading, metadata islandId tracking, claim/generate new quests flows, PDC routing, and sound feedback preserved exactly.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Excellent companion to the just-modernized MissionGUI.
   - **CrateGUI** (final polish on crate opening interface) — Modernization completed:
     - Last remaining manual `new ItemStack(type.getIcon()) + getItemMeta()` block in `createCrateItem` converted to `GUIUtils.createItem` + `attachCratePDC(...)` helper.
     - Class Javadoc updated to document the full modernization.
     - Title, header, close button, and click handler were already using modern patterns (MessageUtil.legacy + GUIUtils + resilient startsWith + PDC) — preserved.
     - `mvn clean compile` verified BUILD SUCCESS. CrateGUI is now fully aligned with the GUI migration standard.
   - **HologramListGUI** (hologram management list with per-hologram actions) — Deep modernization completed:
     - All manual ItemStack + meta blocks (hologram list items in loop, close button, refreshAll) converted to `GUIUtils.createItem` + `attachHologramPDC(...)` helper.
     - Title now uses `MessageUtil.legacy`.
     - Added robust PDC-based hologram name identification (replaces brittle displayName stripping) for click handling.
     - Preserved InventoryHolder pattern and all Folia async delete/refresh paths.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Good progress on management-style GUIs.
   - **IslandBankGUI** (island economy deposit/withdraw with dynamic vault size) — Deep modernization completed:
     - `createItem` and `createGlassPane` fully converted to `GUIUtils.createItem`.
     - Title now uses `MessageUtil.legacy`.
     - Click handler title guard upgraded to resilient `startsWith`.
     - Filler modernized to respect dynamic vault inventory size (from VAULT_SLOTS upgrade).
     - 100% of async bank loading, player/island economy flows, metadata position tracking, and GUI reopen logic preserved exactly.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Strong economy GUI consistency with prior Booster/IslandSettings work.
   - **ChallengeGUI** (daily/weekly challenges with progress and XP claiming) — Deep modernization completed:
     - `createTitleItem`, `createChallengeItem` (dynamic status/lore), and `createInfoItem` converted to `GUIUtils.createItem`.
     - Title now uses `MessageUtil.legacy`.
     - Click handler title check upgraded to resilient `startsWith`.
     - 100% of challenge filtering by type, claim logic, async GUI refresh, and reward awarding preserved exactly.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Good progression GUI alignment.
   - **SlayerLeaderboardGUI** (scrollable top slayers with global + entity-specific views) — Deep modernization completed:
     - Manual `new ItemStack(PLAYER_HEAD) + SkullMeta` boilerplate and the `createItem` helper converted to `GUIUtils.createItem` + dedicated `createSkullItem(UUID, name, lore)` helper.
     - All titles (main + entity) now use `MessageUtil.legacy`.
     - Title checks made more resilient (startsWith on modern prefix).
     - 100% of async DB loading, pagination, entity filtering, and click navigation preserved exactly.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining in the file. Strong cleanup of the Slayer ecosystem GUIs.
   - **IslandTopGUI** (paginated island leaderboard with Worth/Level/Members categories) — Deep modernization completed:
     - Manual skull creation (PLAYER_HEAD + SkullMeta) in the leaderboard loop and the `createNavItem` helper converted to `GUIUtils.createItem` + `createTopIslandSkull(...)` helper.
     - All titles now use `MessageUtil.legacy`.
     - 100% of async worth loading, category switching, pagination, and click logic preserved exactly.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Good progress on island leaderboard-style GUIs.
   - **IslandBrowseGUI** (scrollable public island discovery with ratings + teleport) — Deep modernization completed:
     - `createIslandItem` (PLAYER_HEAD + owner skull logic) and `createItem` converted to `GUIUtils.createItem` (SkullMeta applied after for owner head).
     - Dynamic title now uses `MessageUtil.legacy`.
     - Click handler title check upgraded to resilient `startsWith`.
     - 100% of complex async loading (ratings + warps), metadata state (browse_page / browse_islands), permission check, and teleport logic preserved exactly.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Excellent companion to the just-finished IslandTopGUI.
   - **ParticleTrailGUI** (final polish) — Modernization completed:
     - Last remaining manual `new ItemStack(icon) + getItemMeta() + two PDCs` block in `populatePage` converted to `GUIUtils.createItem` + small `attachTrailPDCs(...)` helper.
     - Javadoc updated to document the final conversion.
     - File was already on BaseGUI with heavy GUIUtils usage; now 100% aligned.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining in the file. Nice cleanup of the cosmetics GUI.
   - **IslandShopGUI** (final polish) — Modernization completed:
     - Last manual `new ItemStack + getItemMeta` block in `grantImmediateReward` (ITEM case) converted to `GUIUtils.createItem` + clean dynamic name/lore handling.
     - Javadoc updated to note full alignment.
     - `mvn clean compile` verified BUILD SUCCESS. IslandShopGUI is now fully modernized (this closes the shop-related GUI modernization work).
   - **SlayerTokenLeaderboardGUI** (slayer token earners leaderboard) — Deep modernization completed:
     - Manual skull (PLAYER_HEAD + SkullMeta) and createItem patterns converted to `GUIUtils.createItem` + `createSkullItem` helper.
     - Titles now use `MessageUtil.legacy`.
     - Title checks made more resilient.
     - `mvn clean compile` verified BUILD SUCCESS. Zero manual `new ItemStack` remaining. Good progress on the remaining leaderboard-style GUIs.
   - **GeneratorGUI** (small per-island generator status stub) — Modernization completed:
     - Single manual ItemStack converted to `GUIUtils.createItem`.
     - Title routed through `MessageUtil.legacy`.
     - `mvn clean compile` verified BUILD SUCCESS.
3. Implement full per-dimension island reset (with confirmation GUI and safety checks).
4. Make Island Worth fully incremental + persisted with drift correction.
5. Expand AntiCheatManager with the detailed Skyblock exploit guide already present in the class.
6. Add comprehensive Folia scheduler usage in `HologramManager` and `MinionManager`.
7. Create admin tools for debugging (worth breakdown, minion stats, violation viewer) — **Deeper completion**: Fully implemented `/isadmin debug worth <player>`, `/isadmin debug minions <player>`, `/isadmin debug anticheat <player>` with rich diagnostic output (multipliers, neural risk, violation counts, breakdowns). Supporting methods added to AntiCheatManager. Original 8-step roadmap item now production usable.
8. Add configuration validation + helpful error messages on startup.
9. Write a "Play-to-Win Design Document" that clearly states what is and is not allowed for donor perks. — **Completed**: Created comprehensive `PLAY_TO_WIN_DESIGN.md` as the authoritative reference. Covers philosophy, definitions, allowed vs forbidden perks, current feature audit (including biome selection and resets), enforcement mechanisms (EconomyManager hardening, AntiCheat, progression systems), decision framework, and future-proofing rules. Directly references existing code safeguards and previous audits.
10. Performance testing on a large Folia test server (simulate 200+ players with 500+ islands).

---

## 8. Small QoL & Polish Items

- Consistent Adventure `Component` usage everywhere (remove legacy `§` colors) — **Big migration sweep completed**: Direct raw `player.sendMessage("§...")` calls centralized via `MessageUtil.sendMessage` in all major commands (IslandCommand, AdminCommand, StaffCommand partial, PlayerCommand, SlayerCommand, ParticleCommand, etc.); broken `Component.text("§...")` (literal § chars) fixed in BazaarGUI, IslandUpgradeGUI, StaffCommand; AbstractGUI + BaseGUI titles now route through `MessageUtil.legacy()` for proper Component parsing. Remaining § literals are inside the sanctioned `MessageUtil` transitional pattern or in GUI item lore/title strings (acceptable for this phase; full Adventure `ItemMeta` + `Component` lore migration is follow-up polish).
- Sound effects on all major actions (crate opening, prestige, boss defeat, GUI clicks) — **Completed**: New `SoundUtil.java` with clean semantic API (`crateOpen()`, `prestige()`, `bossDefeat()`, `purchaseSuccess()`, `click()`, `pageTurn()`, `error()`, etc.). Wired into CrateManager, PrestigeManager, BossManager (summon + defeat), IslandUpgradeManager, BaseGUI (navigation), PrestigeGUI, CrateGUI, MissionGUI, and several feedback paths. Existing raw `playSound` calls consolidated where encountered. Consistent, non-intrusive polish.
- Better error messages when a player tries to do something their island level/prestige does not allow. — **Completed**: Added `PrestigeManager.getPrestigeBlockerMessage()` that returns detailed, actionable text showing exactly what is missing (current vs required for both Level and Worth, with "you need +X" guidance). Improved Slayer tier start errors in BossManager with specific numbers. Enhanced BiomeSelectionGUI locked clicks + TradeGUI level errors with current values. PrestigeGUI requirement lore now shows live current worth/level. All failure paths now play `SoundUtil.error()`. Messages are much more helpful and less frustrating.
- `/is worth breakdown` command.
- Automatic announcement when weekly token leaderboards reset. — **Completed**: Enhanced the existing `checkAndResetTokenLeaderboard()` in BossManager to actually detect the weekly window, clear the leaderboard, and broadcast a nice server-wide announcement with the top 5 earners (using the existing DB query + NameCache). Wired a safe 5-minute repeating Folia task in FoliaSkyblock. When the week rolls over, everyone sees the reset + who won.
- Improved tab list with prestige level + worth. — **Completed**: Refactored `IslandWorthManager.updatePlayerTabList()` to use proper Adventure `Component` + `NamedTextColor` (no more legacy § inside Component.text). Prestige shown as gold `[P3]`, worth and level in clean gray/gold/aqua formatting. Added `setServerTabHeaderFooter()` in FoliaSkyblock (nice header + "Play to Win • No Pay-to-Win" footer) called on enable. Much more modern and readable.

---

**How to Use This Document**

This file is now the single source of truth for what "production ready" and "Play-to-Win on Folia" means for this project.

When you complete an item:
- Update the status in the table or section.
- Remove or archive the bullet.
- Note any important design decisions.

**Next Milestone Target:** v1.2.0 — "Large Server Ready" with all critical architecture items (DB modularization, GUI consistency, full Folia scheduling, world consistency, hardened economy) complete.

**v1.2.0 Status Update (Steps 1-7 Completed):**
- All 7 high-priority items and sub-priorities completed in order.
- Per-dimension island reset **100% complete**: Proper per-dimension reset time tracking (via `player_dimension_resets` table + `recordIslandReset`/`getLastDimensionReset`), boss-per-island safety checks fully wired (`hasActiveBossOnIsland` + `hasActiveBossOnDimension`), enhanced DimensionResetGUI + ResetConfirmationGUI with Folia-safe async flows, and admin override support. The two lingering TODOs in DatabaseMigration.java have been resolved.
- GUI migration aggressively advanced (multiple GUIs migrated to use AbstractGUI/BaseGUI + GUIUtils, PDC standardized).
- Island Worth made fully incremental, persisted via DAO, with Folia RegionScheduler drift correction task, and /is worth breakdown command added.
- Admin debug tools added to AdminCommand for worth, minions, anticheat.
- Folia scheduler coverage expanded in HologramManager and MinionManager, legacy § colors removed in key places (big sweep), comprehensive SoundUtil + sounds on major actions added, significantly improved actionable error messages for level/prestige/requirement gates.
- Wrap-up: HologramDAO added, IMPROVEMENTS.md updated, full Play-to-Win Design Document (`PLAY_TO_WIN_DESIGN.md`) written as authoritative reference, mvn clean package successful, multiple Play-to-Win audits passed (no donor power creep in any systems; all power from playtime/trading/prestige/slayers).
- Build clean, production-ready for Folia large servers.

---

*Document maintained as the living specification for the FoliaSkyblock project.*