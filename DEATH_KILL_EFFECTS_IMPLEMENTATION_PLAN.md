# Death / Kill Effects Implementation Plan for FoliaSkyblock

**Status**: Research + Code Audit Complete
**Priority**: Excellent alternative or follow-up to Helmet Skins (already listed as Tier A in your own COSMETIC_ADVANCEMENTS.md)

## Research Summary

### Web Research
- "Final Kill Effects", "Kill Effects", and "Death Effects" are extremely popular across Hypixel minigames (SkyWars, BedWars, etc.) and frequently requested for Skyblock.
- Common implementations: Particle bursts, lightning, fireworks, skulls, explosions, custom sounds on player death or boss kills.
- Plugins exist (DeathEffects, KillEffects) showing demand for configurable particle death visuals.
- In Skyblock context: Highly satisfying when killing tough bosses (Tarantula, Sven, Voidgloom, etc.) or getting final blows.

### Project Code Audit
- Death events already listened to:
  - `SlayerGearListener.onEntityDeath` — checks killer + specific gear (good hook point).
  - `IslandXPListener`, `ChallengeProgressListener` — other death processing.
- **ParticleTrailManager** is mature and Folia-safe:
  - Uses `EntityScheduler` per player.
  - Dozens of spawn methods (`spawnGalaxySwirl`, `spawnAngelWings`, etc.).
  - Per-player tasks and tick counters.
  - Perfect reuse for death effects (trigger burst at death location using the victim's or killer's scheduler).
- No existing dedicated DeathEffect enum or manager.
- Folia safety precedent: Effects in runes/pets use `runAtLocation` or EntityScheduler.

**Advantages for this project**:
- Leverages existing heavy investment in particles.
- High engagement on progression (slayer bosses, dungeons-style content if added later).
- Easy to make collection + rarity system.
- Can be "on death" (your own death) or "on kill" (when you kill something) — or both.

## Exact Implementation Steps (In Order)

### Phase 1: Core Model
1. Create `src/main/java/com/thenerdcj/cosmetic/DeathEffect.java` (enum).
   - `NONE`
   - Effects like: LIGHTNING_STRIKE, FIREWORK_BURST, BLOOD_SPLATTER, SOUL_RELEASE, DRAGON_BREATH, GALAXY_DEATH, etc.
   - Fields: displayName, description, rarity, minPrestige, tokenCost, particleType, count, spread, extraSound.
   - Methods for triggering.

### Phase 2: Manager (Heavy reuse of ParticleTrailManager)
2. `DeathEffectManager.java`
   - owned + active maps.
   - `triggerDeathEffect(Player victim, Player killer, DeathEffect effect)` — spawns particles at victim's location using safe scheduler.
   - `triggerKillEffect(...)` if you want separate "on kill" effects.
   - Award XP on unlock.
   - load/save.

3. DB: `player_death_effects` table + index (same pattern as player_pet_skins or player_rune_collection).

4. Wire into FoliaSkyblock + listeners.

### Phase 3: Integration
5. Listen in a new or existing listener (e.g. enhance `CombatListener` or `SlayerGearListener`).
   - On `EntityDeathEvent`:
     - If killed by player → check killer's active kill effect.
     - Always check victim's active death effect.
   - Call manager to spawn (Folia safe).

6. Wardrobe: New "Death Effects" tab or section (similar to Runes we just added).
   - Preview (maybe spawn a small test effect? Careful with safety).

7. Dedicated `DeathEffectGUI.java`.

### Phase 4: Shop + Prestige
8. SlayerShopGUI additions (easy, same as runes/skins).
9. PrestigeManager grants.

### Phase 5: Polish
- Make some effects scale with rarity (more particles on higher tiers).
- Sound effects using `SoundUtil`.
- Collection milestones.
- Final compile + Folia verification (all spawns must go through ThreadSafety or schedulers — easy since we reuse the particle manager).

**Why this is lower effort than Helmet Skins**: Reuses ~80% of existing particle infrastructure. Mostly new enum + manager + one listener hook + GUIs.

**Synergy**: Can combine with existing trails (e.g. death effect + active trail).

---

Ready for execution in the same style as Helmet Skins plan. Let me know the order preference.