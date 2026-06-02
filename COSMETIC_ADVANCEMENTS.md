# FoliaSkyblock – Cosmetic Advancements Roadmap

**Philosophy (Non-Negotiable)**
- Everything is **strict Play-to-Win**. Cosmetics are earned through prestige, slayer tokens, collection milestones, island progression, and events.
- The **Wardrobe** is the central "Appearance Manager" hub (already proven with Armor/Equipment + Cosmetics & Trails + Pets + Tags tabs).
- New systems must follow established high-quality patterns:
  - Enum-based type definitions (`ParticleTrail`, `PetType`, `PlayerTag`)
  - Rarity + Collection XP system (Island XP on first unlock)
  - Prestige level + Slayer token gating
  - Live preview where possible (Wardrobe integration)
  - Pagination + filtering in GUIs (like the Pets/Cosmetics tabs)
  - Full Folia safety (EntityScheduler / ThreadSafety)
  - Dedicated GUI + Wardrobe tab (or merge into existing)

---

## Current Cosmetic State (as of latest session)

**Deep, Mature Systems**
- **Wardrobe** — Armor + Equipment presets with collection XP
- **Particle Trails** — 50+ advanced effects with live Wardrobe preview, heavy filtering/pagination
- **Pets** — Full follower system with rarity, collection XP, variants, advanced visuals (size/held items/sounds), prestige/slayer unlocks, Wardrobe tab + dedicated GUI
- **Player Tags** — Text cosmetics in chat/tab + overhead nametags (scoreboard teams), variants, collection, full GUI + Wardrobe tab, visibility toggle

**Minor Cosmetics**
- Donor biome choice on first creation / dimension reset reroll (cosmetic only)
- Seeded island "personality" variety (procedural generation flavor)

The bar is now very high. New cosmetics should feel like natural, high-value extensions rather than one-off features.

---

## Prioritized Cosmetic Advancement Ideas

### Tier S (Highest Value / Natural Extensions)

**1. Pet Skins (Strongly Recommended Next)**
- Why it fits perfectly: We just built a deep pet system with variants. Pet Skins are one of Hypixel's most popular long-term cosmetics.
- Concept: Unlockable appearances for existing `PetType` (different textures + particle overrides).
- Storage: Similar to `TagInstance` or a `PetSkin` applied to a `CosmeticPet`.
- Gating: Prestige / Slayer / rare collection rewards.
- UI: Right-click pet in Pet GUI or new "Pet Appearance" sub-tab in Wardrobe Pets.
- Technical: Extend `PetManager.spawnPet` to support skin overrides (different skull owners or resource-pack heads).
- Synergy: Makes the existing pet system feel much deeper without new entity types.

**2. Elytra Wing Cosmetics**
- Very popular modern cosmetic (custom elytra textures + animated effects).
- Could live in Wardrobe as a new "Wings" category or under Cosmetics & Trails.
- Implementation options:
  - Resource pack driven (player chooses a cosmetic pack client-side).
  - Or server-side via armor stand + elytra item with custom model data.
- Gating: High prestige + slayer or special events.
- Bonus: Could tie into existing particle trails (wing trails when gliding).

**3. Cosmetic Runes**
- Apply visual effects to tools/weapons (sparkles, trails on swing, etc.) — Hypixel style.
- Stored in a new `Rune` enum (cosmetic only, no stats).
- Applied via a "Rune Table" or directly in inventory with right-click.
- Excellent for Slayer shop rewards and prestige.

### Tier A (High Value, Slightly More Work)

**4. Death / Kill Effects**
- Cosmetic particle + sound bursts on player death or when killing bosses/mobs.
- Easy to gate behind prestige/slayer/collection.
- Can reuse a lot of the existing `ParticleTrailManager` code.

**5. Island Music & Ambient Cosmetics**
- Players unlock cosmetic music discs or ambient sound packs that play while on their island.
- Island-wide (for visitors) or personal-only.
- Nice progression sink and very "Skyblock" feeling.

**6. Backpack / Bag Skins**
- Cosmetic overrides for backpacks (very popular on Hypixel).
- Visual only (different textures when opened or carried).

**7. Accessory System (Light)**
- Small floating cosmetic items around the player (like Hypixel accessories but purely visual).
- Could use invisible armor stands or modern TextDisplay entities.
- High visual impact, relatively contained.

### Tier B (Good but Lower Priority)

- Helmet / Armor Appearance overrides (separate from actual armor)
- Join/Death cosmetic messages (with rich formatting using tags)
- More advanced overhead cosmetics (floating titles using TextDisplay)
- Minion Skins
- Power Orb visual reskins
- Chat emotes / chat cosmetics beyond tags

---

## Recommended Implementation Order (Next 3–4 Cosmetics)

1. **Pet Skins** — Highest immediate value. Leverages everything we just built for pets.
2. **Elytra Wing Cosmetics** — Modern, flashy, and fits the "advanced visuals" direction we took with pets.
3. **Cosmetic Runes** — Great Slayer shop filler and prestige rewards.
4. **Death Effects** or **Island Music** — Quick wins with high perceived value.

---

## Architectural Notes for Future Cosmetics

- **Always** add a new tab or section in the Wardrobe when possible.
- New types should get their own `XXXManager`, enum, and `XXXGUI`.
- Collection + rarity XP should be the default unlock reward (consistency with Pets/Tags/Wardrobe equipment).
- Live preview in Wardrobe is the gold standard (even if simulated).
- All new cosmetics must respect the existing prestige/slayer gating patterns.

---

*Document created after deep review of current cosmetic systems and popular Skyblock cosmetic trends (Hypixel and similar servers).*

**Next step suggestion:** If you want to continue the momentum, say "Let's do Pet Skins next" or pick any from the list above and I'll start executing immediately in the same style as the pet and tag systems.