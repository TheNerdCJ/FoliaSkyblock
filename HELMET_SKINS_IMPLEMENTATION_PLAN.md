# Helmet Skins Implementation Plan for FoliaSkyblock

**Status**: Research + Code Audit Complete (as of current session)
**Priority**: Top recommended next cosmetic (based on Hypixel Fire Sales trends + project gaps)
**Philosophy Alignment**: Strict Play-to-Win, cosmetic only, no power. Gated by Prestige/Slayer tokens + collection XP. Consistent with Pets Skins, Runes, etc.

## Research Summary (Web + Project Code)

### From Web Research (Hypixel Skyblock Focus)
- Helmet Skins are one of the most frequently released cosmetics in Fire Sales and seasonal shops (alongside Pet Skins, Backpack Skins, Power Orb Skins).
- Examples: "Chaos Terror Helmet Skin", "Blazing Crimson Helmet Skin" — applied to specific helmet lines (Crimson, Aurora, Terror, Fervor, Hollow, etc.).
- Mechanics in Hypixel:
  - Cosmetic override only (changes appearance of the helmet).
  - Often animated or multi-sprite.
  - Can be applied to multiple helmet variants in a set.
  - Purchased with Gems (our equivalent: Slayer tokens + Prestige gating).
  - Permanent once applied.
- Popularity: High visual impact because helmets are always visible on the player model. Frequently tied to events.

Other related popular items: Dyes for armor, general armor skins.

### Current Project State (Audit of Main Codebase)
- **Wardrobe**: Strong Armor (full sets with helmet) and Equipment tabs. `WardrobeManager` handles save/equip of `ItemStack[]` armor including `inv.getHelmet()`.
  - No cosmetic layer on top of real armor yet.
- **Skin Precedents** (excellent models):
  - `PetSkin.java`: Enum with rarity, minPrestige, tokenCost, override textures, `getEffectiveHeadOwner()`.
  - `PetManager`: `ownedSkins` map, `unlockSkin()`, `applySkinToPet()`, collection XP, persistence via DB.
  - `PetGUI`: Dedicated skin selection GUI with owned/locked states.
  - Wardrobe Pets tab already shows current skin + right-click flow.
- **Item Application**: Runes use PDC (`RUNE_KEY` as "NAME:TIER") + `CustomModelData` potential.
- **Wings**: Use custom elytra items with model data + PDC.
- **No existing HelmetSkin enum or manager** — clean slate but fits perfectly.
- **PrestigeManager / SlayerShopGUI**: Already extended for Runes, Pets, Wings, Tags. Easy to add `BUY_HELMET_SKIN`.
- **Folia Safety**: All GUI/click paths are main-thread. Armor equipping already uses `ThreadSafety.runOnMainThread()` in WardrobeManager.
- **Collection XP**: Pattern established (addIslandXp on first unlock, milestones).

**Gaps Identified**:
- No way to cosmetically override helmet appearance without changing the actual item.
- Wardrobe Armor tab saves real gear — we need a separate "cosmetic skins" layer.

## Exact Implementation Steps (In Strict Order)

Follow the same proven pattern as Pet Skins + Runes (enum → manager → persistence → GUI/Wardrobe → shop → prestige).

### Phase 1: Core Data Model (Low Risk)
1. Create `src/main/java/com/thenerdcj/cosmetic/HelmetSkin.java` (new package or under existing cosmetic if preferred).
   - Enum modeled exactly after `PetSkin`:
     - `NONE`
     - Universal skins + helmet-line specific (e.g. CRIMSON_BLAZING, TERROR_CHAOS).
     - Fields: displayName, description, rarity (reuse PetRarity or shared Rarity enum), minPrestige, tokenCost, overrideMaterial or CustomModelData value, compatibleHelmetTypes (or apply to any).
     - Methods: `isNone()`, `getEffectiveModelData(ItemStack baseHelmet)`, `getRarity()`, etc.
   - Use `CustomModelData` for client-side visuals (server-authoritative via PDC).
   - Add lore examples and prestige rewards (some skins locked to high prestige).

2. Update any shared Rarity if needed (or reuse `PetRarity` for now, as done with other systems).

### Phase 2: Manager + Persistence (Folia-Safe Core)
3. Create `HelmetSkinManager.java` (parallel to `PetManager`, `RuneManager`, `ElytraWingManager`).
   - `Map<UUID, Set<HelmetSkin>> ownedSkins`
   - `Map<UUID, HelmetSkin> activeHelmetSkin` (or per-helmet-slot if we want granularity later — start simple: one active skin that applies to equipped helmet).
   - Methods:
     - `unlockSkin(UUID, HelmetSkin)`
     - `applySkinToHelmet(Player, ItemStack helmet, HelmetSkin)` — modifies PDC on the item ("HELMET_SKIN:NAME")
     - `getSkinFromItem(ItemStack)`
     - `getOwnedSkins`, `hasSkin`, `getSkinCollectionCount`
     - Award collection XP on first unlock (call `islandManager.addIslandXp`, chat message, milestones at 5/10/etc.)
   - `loadPlayer(UUID)` / `savePlayer(UUID)` hooks.
   - Use `ThreadSafety` where appropriate (already done for armor equip).

4. Database extensions (in `DatabaseManager.java`):
   - Add table: `player_helmet_skins (uuid TEXT, skin_id TEXT, PRIMARY KEY (uuid, skin_id))`
   - Add index.
   - Methods: `savePlayerHelmetSkins(UUID, Set<String>)`, `loadPlayerHelmetSkins(UUID)`
   - Update `createTables()` and any migration logic.
   - Also store active skin per player if desired (new column or separate table like active_pet).

5. Wire into `FoliaSkyblock.java` (new field + getter + onEnable registration + PlayerJoin/Quit listeners via existing `PlayerQuitListener`).

6. Update `CosmeticPet` / no — this is separate. Update `WardrobeManager` or add helper to apply skin when equipping armor sets (optional polish).

### Phase 3: UI & Integration (Wardrobe + Dedicated)
7. Enhance `WardrobeGUI.java`:
   - Add new `View.HELMET_SKINS` (or merge into existing Armor tab as sub-section).
   - New `renderHelmetSkinsPreview(Player, Inventory)` — show owned skins, current active, collection count, "Open Full Helmet Skins Menu" button.
   - Tab button for it (add to the row with other cosmetic tabs).
   - Click handling: equip skin (apply to currently worn helmet or preview), right-click for info.

8. Create or extend `HelmetSkinGUI.java` (dedicated full GUI, modeled after `PetGUI` skin selection or `RuneGUI`).
   - List all skins (owned vs locked).
   - Click to apply to held helmet or current armor.
   - Back to Wardrobe button.

9. Update armor equipping flow (light touch in `WardrobeManager.equipSet` or a new listener) to re-apply any active HelmetSkin via PDC when helmet is equipped.

### Phase 4: Progression & Shop (Play-to-Win Completeness)
10. `SlayerShopGUI.java`:
    - Add `createHelmetSkinShopItem(...)` + attach PDCS with `BUY_HELMET_SKIN`.
    - Place 4–6 examples in the shop GUI (mix of cheap early + high-end prestige rewards).
    - Handle `BUY_HELMET_SKIN` action: check prestige, consume tokens, call `manager.unlockSkin()`.

11. `PrestigeManager.java`:
    - In `grantPrestigeUnlocks` (or specific level handlers), grant 1–2 Helmet Skins as rewards (e.g. at prestige 4, 6, etc.).
    - Mirror the pattern used for Pet Skins / Runes.

12. Collection XP + Milestones:
    - Already handled in manager from step 3.
    - Optional: Add to any existing collection GUI or new "Cosmetic Collection" view.

### Phase 5: Polish, Safety, Testing
13. Folia Safety:
    - All new GUI code is main-thread by nature.
    - Any helmet modification on equip must go through `threadSafety.runOnMainThread()`.
    - Particle previews (if adding skin-specific effects) must use EntityScheduler or runAtLocation.

14. Visuals:
    - Decide on implementation: Prefer `CustomModelData` on the helmet item (server sets it when skin applied). Resource pack friendly.
    - Fallback: Different player head textures for helmets (like PetSkin).

15. Testing Checklist:
    - Unlock via shop/prestige → persists across logout.
    - Apply to helmet → visual changes (requires test resource pack or heads).
    - Wardrobe tab shows correctly with pagination if many skins.
    - Collection XP awarded only on first unlock.
    - No power advantage (pure visual).
    - `mvn clean compile` clean.
    - Folia vs Spigot behavior.

16. Documentation:
    - Update `COSMETIC_ADVANCEMENTS.md` (mark as implemented).
    - Add example to `IMPROVEMENTS.md` or new section.
    - Optional: Add simple in-game help in the new GUI.

### Estimated Scope & Order Recommendation
- Do in strict order above (like previous cosmetics).
- Start with Phase 1–2 (enum + manager + DB) — core is isolated.
- Then Wardrobe integration (high visibility win).
- Shop + Prestige last (completes the loop).
- Total new files: ~4–6 (similar to Runes or Pet Skins initial pass).

This will feel like a natural, high-value addition that makes the Wardrobe even more central.

---

**Next Action Suggestion**: If you approve this plan, reply with "start with Phase 1" or "complete these steps in order" and I'll immediately begin writing the `HelmetSkin.java` enum and manager using the exact same style as `PetSkin.java` / `Rune.java`.

This outline is based on live code audit of your current main project + fresh web research on Hypixel trends. Ready to execute.