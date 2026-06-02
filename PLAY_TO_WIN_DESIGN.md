# FoliaSkyblock — Play-to-Win Design Document

**Version:** 1.0  
**Status:** Authoritative reference for v1.2.0 "Large Server Ready"  
**Last Updated:** June 2026

## 1. Core Philosophy

FoliaSkyblock is a **strict Play-to-Win** (PtW) Skyblock experience.

- Power, progression, and competitive advantage must come **exclusively** from playtime, skill, dedication, trading, missions, leveling, prestige, and fair competition.
- Donations may only provide **cosmetic, convenience, or quality-of-life** benefits that do not create a meaningful mechanical advantage in power, economy, or progression speed.
- The design goal is to match or exceed the fairness standards of top Skyblock servers (SuperiorSkyblock2, IridiumSkyblock, etc.) while being optimized for large Folia deployments.

This document is the single source of truth for what is and is not allowed.

## 2. Definitions

| Term              | Definition |
|-------------------|----------|
| **Power**         | Any stat, multiplier, gear quality, resource generation rate, or economic advantage that improves progression speed or combat strength. |
| **Cosmetic**      | Purely visual or auditory (particle trails, biome appearance, titles, sounds, tab list formatting). |
| **Convenience/QoL** | Time-saving features that do not increase total power (e.g., /home, auto-sell toggles, larger but non-powerful storage). |
| **Pay-to-Win (P2W)** | Any donation that gives faster access to power that free players cannot eventually obtain at the same or better rate through play. |

## 3. Allowed Donor Features

Donor perks **must** fall into one or more of these categories:

### 3.1 Purely Cosmetic
- Particle trails (unlocked via Prestige or Slayer tokens for free players; donors may get early access or more options)
- Custom biome choice **on first island creation only** (cosmetic only — changing it later requires a full dimension reset)
- Special chat prefixes, suffixes, or tab list formatting
- Exclusive crate keys or rewards that are **cosmetic or convenience only** (no stat items, no powerful gear)

### 3.2 Convenience / Quality of Life (No Power Gain)
- Extra /home or warp slots
- Larger personal /is vault or ender chest (storage only)
- Auto-sell or auto-pickup toggles (same rates as free players)
- Priority queue or reserved slots during peak hours
- Colored name in tab list or chat (no mechanical effect)

### 3.3 Early Access to Free Content
- Early access to particle trails or cosmetic crates that will be made available to all players later via prestige/slayers/trading
- Early access to new dimensions or features that are eventually unlocked for everyone through progression

## 4. Strictly Forbidden (P2W)

The following are **never** allowed for donors:

- Any XP, island level, prestige, or worth multipliers
- Stat bonuses (damage, defense, speed, health, etc.)
- Unique powerful gear, enchantments, or items unavailable to free players
- Faster resource generation (generators, minions, crops, spawners) beyond what free players can achieve
- Economic advantages (higher sell prices, lower buy prices, interest on balances, tax reduction)
- Reduced cooldowns on prestige, resets, slayer quests, or bosses
- "Pay to skip" progression (buying levels, prestige, or mission completions)
- Any form of "donor island" with better rates or exclusive powerful content
- Permanent power that cannot be matched or exceeded by dedicated free players

## 5. Current Feature Audit (v1.2.0)

| Feature                        | Classification     | Rationale / Safeguards |
|--------------------------------|--------------------|------------------------|
| Donor biome selection (first creation) | Cosmetic | Requires full dimension reset to change. No power benefit. |
| Particle trails via Prestige/Slayer | Free + Early Cosmetic | Core trails unlocked via play. Donors may get visual variety earlier. |
| Extra crate keys for donors    | Mixed (mostly QoL) | Rewards must remain cosmetic or low-value convenience. No stat items. |
| /is reset with confirmation    | Core feature       | Safety checks (bosses, parties, cooldowns) apply equally. No donor bypass. |
| Tab list prestige/worth display| Cosmetic           | Purely informational. |
| Economy hardening (tryRemove, safeTransfer) | Infrastructure | Protects against exploits for everyone. |
| Anticheat (generator macros, boosting, etc.) | Infrastructure | Enforces fairness for all players. |

**Audit Result:** As of the v1.2.0 milestone, no donor features grant mechanical power advantages.

## 6. Enforcement Mechanisms

1. **Code-Level Guards**
   - `EconomyManager` uses hardened methods (`tryRemovePlayerBalance`, `safeTransferPlayerToIsland`) that prevent negative balances and exploits.
   - All progression (XP, levels, prestige, worth) is calculated server-side with no donation multipliers.
   - Prestige and Slayer systems are the primary "power" gates — both are grind-based.

2. **Configuration & Data**
   - `anticheat.yml` explicitly states the Play-to-Win requirement.
   - All reward tables (crates, missions, slayer, bosses) are reviewed to ensure no P2W items.

3. **Runtime Monitoring**
   - `AntiCheatManager` contains detailed Skyblock-specific heuristics (generator macros, multi-account boosting, crate key dupes, etc.).
   - IslandWorthManager drift correction + incremental tracking prevents economic exploits.
   - Regular audits of new features against this document.

4. **Community & Transparency**
   - This document is public.
   - Any proposed donor feature must be reviewed against this document before implementation.

## 7. Decision Framework for New Features

When considering a new donor perk, ask:

1. Can a dedicated free player eventually obtain equal or better power through playtime/trading?
2. Does this feature reduce the time or effort required to gain power compared to free players?
3. Is the benefit purely visual, auditory, or a minor time saver that does not scale into advantage?
4. Would removing this feature for donors meaningfully hurt the fairness of the server?

If the answer to #1 or #2 is "no", the feature is not allowed.

## 8. Future-Proofing

- Every new system (minions, custom enchants, events, etc.) must be designed with Play-to-Win as a first-class constraint.
- Any "donor crate" or "donor reward" must be reviewed by at least one maintainer against this document.
- Major updates should include a delta audit against this document.

## 9. Related Documents

- `IMPROVEMENTS.md` — Overall roadmap and completion status (includes Play-to-Win audit passes).
- `anticheat.yml` — Detailed exploit prevention rules.
- Economy and progression manager classes (hardened methods and fair calculation logic).

---

**This document takes precedence over any other design notes or feature requests.**

Maintaining strict Play-to-Win is a core non-negotiable value of the FoliaSkyblock project.