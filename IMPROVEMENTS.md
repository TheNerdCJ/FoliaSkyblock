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
2. Complete migration of every GUI to the new `AbstractGUI` base.
3. Implement full per-dimension island reset (with confirmation GUI and safety checks).
4. Make Island Worth fully incremental + persisted with drift correction.
5. Expand AntiCheatManager with the detailed Skyblock exploit guide already present in the class.
6. Add comprehensive Folia scheduler usage in `HologramManager` and `MinionManager`.
7. Create admin tools for debugging (worth breakdown, minion stats, violation viewer).
8. Add configuration validation + helpful error messages on startup.
9. Write a "Play-to-Win Design Document" that clearly states what is and is not allowed for donor perks.
10. Performance testing on a large Folia test server (simulate 200+ players with 500+ islands).

---

## 8. Small QoL & Polish Items

- Consistent Adventure `Component` usage everywhere (remove legacy `§` colors).
- Sound effects on all major actions (crate opening, prestige, boss defeat, GUI clicks).
- Better error messages when a player tries to do something their island level/prestige does not allow.
- `/is worth breakdown` command.
- Automatic announcement when weekly token leaderboards reset.
- Improved tab list with prestige level + worth.

---

**How to Use This Document**

This file is now the single source of truth for what "production ready" and "Play-to-Win on Folia" means for this project.

When you complete an item:
- Update the status in the table or section.
- Remove or archive the bullet.
- Note any important design decisions.

**Next Milestone Target:** v1.2.0 — "Large Server Ready" with all critical architecture items (DB modularization, GUI consistency, full Folia scheduling, world consistency, hardened economy) complete.

---

*Document maintained as the living specification for the FoliaSkyblock project.*