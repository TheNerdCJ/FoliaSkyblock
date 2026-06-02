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
- **Wardrobe** — Armor + Equipment presets with collection XP + full tabs for Trails, Pets, Tags, Wings, Runes, Helmet Skins, Death Effects, Backpack Skins, Power Orb Skins, Minion Skins, Furniture, Music, Overhead, Emotes, Structures, Chat Bubbles, Weather, Accessories
- **Particle Trails** — 50+ advanced effects with live Wardrobe preview, heavy filtering/pagination
- **Pets + Pet Skins** — Full follower system with rarity, collection XP, variants, skins (head overrides + themed particles), prestige/slayer unlocks, Wardrobe tab + dedicated GUI with skin selection
- **Player Tags** — Text cosmetics in chat/tab + overhead nametags (scoreboard teams), variants, collection, full GUI + Wardrobe tab, visibility toggle
- **Elytra Wings** — Custom model elytra + gliding particle effects, Wardrobe tab + dedicated GUI
- **Cosmetic Runes** — Tiered (1-3) weapon/tool visual effects (melee/bow/armor), collection XP, Rune Table (enchanting table integration), full Wardrobe tab + dedicated GUI
- **Helmet Skins** — Full cosmetic helmet overrides (CMD + visuals), auto re-apply on equip, Wardrobe tab + dedicated GUI, Slayer shop, prestige grants, collection XP
- **Death / Kill Effects** — 10+ Folia-safe particle bursts on player deaths and mob kills, dedicated GUI, Wardrobe tab, listener, shop + prestige integration
- **Backpack Skins** — Cosmetic overrides ready for backpacks/storage (full enum/manager/GUI/Wardrobe/shop/prestige layer)
- **Power Orb Skins** — Full Play-to-Win system (enum + manager + DB + GUI + command + Wardrobe tab + Slayer shop examples + prestige) — visual layer complete, ready for Power Orb item integration

**Minor Cosmetics**
- Donor biome choice on first creation / dimension reset reroll (cosmetic only)
- Seeded island "personality" variety (procedural generation flavor)

The bar is now very high. New cosmetics should feel like natural, high-value extensions rather than one-off features. Minion Skins are the current active expansion (skins on the existing robust minion ArmorStand system).

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

**Completed in recent sessions:**
- Pet Skins (full unlock, apply, persistence, Slayer shop, Wardrobe display + skin selection via PetGUI)
- Elytra Wing Cosmetics (full system + Wardrobe tab)
- Cosmetic Runes (tiered effects, collection, Folia-safe, full Wardrobe tab + Rune Table GUI)
- Helmet Skins (full enum, manager, DB, dedicated GUI, Wardrobe tab, Slayer shop, prestige, auto re-apply)
- Death / Kill Effects (full: enum with 10+ effects, manager, DB persistence, Folia-safe listener on EntityDeathEvent (kills + player deaths), Wardrobe tab with active display, full dedicated GUI with selection/apply/remove, /deatheffects and /death commands, Slayer shop examples, prestige grants, collection XP)
- Backpack Skins (full layer: enum/manager/GUI/DB/Wardrobe tab/shop/prestige/command — ready for item integration)
- Power Orb Skins + Items (FULLY ACTIVATED): skins + manager + GUI + Wardrobe + shop + prestige + real usable orb items (PDC + CMD) + themed Folia-safe particle effects on right-click + obtain paths via prestige/shop/command. Purely cosmetic.

**Minion Skins (Completed)**
- Full implementation following the established high-quality template (Enum + Manager + DB + GUI + Command + Wardrobe tab + Slayer Shop + Prestige + Listener wiring).
- Hypixel-style: 11+ cosmetic skins with head overrides (MHF_* player heads for themed minion helmets) + CMD fallback + light particle accents on premium skins.
- Per-owner active theme applies to ALL minions on their islands via updated spawn/respawn in MinionManager (Folia-safe EntityScheduler + ThreadSafety).
- Dedicated /minionskins GUI with proper PDC handling, collection XP + milestones on unlock, Back-to-Wardrobe, full preview in Wardrobe tab.
- Play-to-Win: Prestige-gated + Slayer token purchases, collection rewards.
- BUILD SUCCESS verified after full addition. Ready for per-minion assignment expansion when individual persistence is added.

**Island Furniture / Housing Cosmetics (FULL PLACEMENT COMPLETE)**
- Full working system: real ItemDisplay (with ArmorStand fallback) spawning using CustomModelData, Folia-safe via ThreadSafety.
- Placement via GUI → gives Placement Tool item (right-click block to place).
- Load/respawn on island enter (hooked into IslandManager).
- Removal support in manager + basic command.
- All previous foundation pieces (enum, manager, DB, GUI, Wardrobe tab, shop, prestige).
- BUILD SUCCESS (225 sources). Purely cosmetic island decoration system.

**Island Music & Ambient Cosmetics (COMPLETE)**
- `IslandMusicType` enum (12+ ambients: ocean, forest, cave, celestial, slayer echoes, grand symphony, void whispers, etc.).
- Full manager: per-island active music, unlocks + XP, Folia-safe looping playback (player schedulers + stopSound), on enter/leave hooks.
- DB (unlocks + active per island).
- GUI catalog with live set-active for your island + Back to Wardrobe.
- Commands `/music`, `/ambience`.
- Wardrobe integration (full tab), shop examples, prestige grants.
- BUILD SUCCESS.

**Deeper Island Housing Progress**
- FurnitureType expanded with 3 new pieces + `furnitureSet` field (Celestial, Slayer, Mystic themes) for future set bonuses.
- Music tab fully polished in Wardrobe.
- Removal UX improved (sneak + right-click with placement tool now removes nearby furniture).
- Furniture Set collection mechanics added: completing a themed set (e.g. Celestial, Slayer) awards bonus Island XP; set completion now auto-unlocks a special bonus furniture piece (e.g. Celestial Throne).
- Per-island furniture ownership display added to GUI: "View Placed on Island" button lists placed furniture types, counts, and placer info (partial UUID) in chat.
- Set completion now unlocks bonus furniture pieces automatically (Celestial Throne, etc.).
- Load/enter hooks improved in IslandManager: furniture visuals respawned, music active loaded + playback triggered on player island load/enter.
- BUILD SUCCESS after expansions.

**Advanced Overhead Cosmetics (Mature / Full Wardrobe Integration + More Variety)**
- `OverheadCosmetic` enum (TextDisplay floating effects: halos, titles, auras, crowns, glows, rainbow halo, dragon wings).
- Full-featured manager with ownership + collection XP, Folia-safe TextDisplay + particle lifecycle (with scale/rotation for some, expanded particles), load/save, onJoin/Quit.
- Dedicated GUI + full Wardrobe tab (preview + routing to catalog).
- `/overhead` command.
- DB persistence + Slayer Shop + Prestige grants.
- BUILD SUCCESS. System is now at parity with Music / Furniture for daily use.

**Chat Emote Cosmetics (New System - Foundation Complete)**
- `EmoteCosmetic` enum (wave, cheer, dance, bow, laugh, etc.).
- Manager with unlocks + XP, perform with particles and broadcast messages, Folia-safe.
- Dedicated GUI + command (/emotes, /emote <name>).
- DB persistence.
- Slayer Shop + Prestige grants.
- Wardrobe tab.
- BUILD SUCCESS.

**Island Structure Decorations (New System - Foundation + Shop/Prestige + GUI + Wardrobe + Tool Support)**
- `IslandStructureCosmetic` enum (pillars, arches, trees, fountains, mystic circles, dragon nests, grand arch, etc.).
- Manager with unlocks + XP, place that spawns cluster of ItemDisplays (Folia safe), load/save for placed, remove support.
- Dedicated GUI + command /structures.
- DB tables.
- Integrated in IslandManager load.
- Slayer Shop examples + Prestige grants.
- Full Wardrobe tab.
- Placement tool support in listener (right-click to place, shift for remove nearby structures specifically).
- Remove for structures is now specific (not clearing all).
- BUILD SUCCESS.

**Chat Bubble Cosmetics (New System - Full Parity)**
- `ChatBubbleCosmetic` enum (11+ styles: heart, star, note, flame, magic, ender, snow, slime, rainbow, crown, void).
- Full manager: ownership + active style, collection XP + milestones (3/5/8/12), Folia-safe trigger on chat (temporary TextDisplay floating message + themed particles via ThreadSafety.runAtLocation + entity scheduler).
- Dedicated GUI (54-slot, active ★ indicator, set/remove, demo on equip, Back to Wardrobe).
- Commands: /chatbubbles, /bubble, /chatbubble.
- DB: player_chat_bubble_cosmetics table + save/load.
- Full Wardrobe tab (CHAT_BUBBLES view + render + routing).
- Slayer Shop examples + BUY handler.
- Prestige grants at levels 2/4.
- Chat listener (MONITOR, ignoreCancelled) wired; integrates with existing chat flow.
- BUILD SUCCESS.
- Autonomous continuation: matches exact template parity of Emotes/Overhead/Structures.
- Polish: added 2 extra styles (bubble burst, glitter shower) + handling.

**Island Weather Cosmetics (New System - Full Parity)**
- `IslandWeatherCosmetic` enum (14 effects: gentle rain, snow flurry, meadow pollen, ember shower, mystic fog, starfall, aurora, slayer tempest, celestial glow, void mist, rainbow mist, bloom shower, lava glow, crystal rain).
- Full manager: player ownership + per-island active weather, collection XP + milestones, Folia-safe repeating particle loops (player scheduler + ThreadSafety) on island enter, enter/leave/start/stop hooks, DB load/save for active + unlocks.
- Dedicated GUI (catalog with active indicator, set/clear while on island, Back to Wardrobe).
- Commands: /weather, /islandweather, /weathereffects (open GUI; set from island).
- DB: player_island_weather + island_active_weather tables + save/load methods (modeled on music).
- Full Wardrobe tab (WEATHER view + render + routing to catalog).
- Slayer Shop examples + BUY handler.
- Prestige grants at levels 3/5.
- Integrated in IslandManager load/enter (like music/structures/furniture).
- Polish: onPlayerLeaveIsland wired in DimensionIslandListener (world change detection for old/new islands), additional effects.
- BUILD SUCCESS (250 sources).
- Purely cosmetic particle overlays (no actual weather or power changes).

**Accessories (New System - Full Parity)**
- `AccessoryCosmetic` enum (8+ light floating accessories: floating star, orbiting orb, sword, balloon, mystic companion, crown, dragon companion, rainbow orbit).
- Full manager: ownership + active, collection XP + milestones, Folia-safe ItemDisplay floating visuals (player scheduler, remove/respawn, themed particles), load/save, join/quit.
- Dedicated GUI + command (/accessories, /accessory).
- DB: player_accessories table + save/load.
- Full Wardrobe tab + routing.
- Slayer Shop examples + BUY handler.
- Prestige grants.
- BUILD SUCCESS (254 sources).
- Purely cosmetic floating items (ItemDisplay based).

**Recent Polish (Overhead Titles expansion + Emote variety + Accessories)**
- Added 3 new to OverheadCosmetic (floating book, glowing eyes, ice aura) with custom TextDisplay + particles in manager for titles expansion beyond previous.
- Added 2 new to EmoteCosmetic (thumbs up, shrug) + effect handling and broadcasts in manager.
- Added 2 new to AccessoryCosmetic (floating lantern, orbiting shield) + handling in manager for more variety.
- Emote triggers full: API + /emote trigger <key> <emote> command support + EmoteTriggerListener for EntityDeathEvent (kill/death); join demo in manager.
- More variety as per roadmap for overhead/emotes/accessories.
- Minion per-assignment complete: foundation (manager/DB) + full GUI support in MinionsGUI (new skin assignment section with slots for minion #1+, click to assign current active skin to that specific minion using the per-minion system). Supports individual skins per minion beyond global.
- Furniture/Structures UX polish: enhanced /furniture and /structure commands with "list" (shows numbered placed items) and "remove <num>" for easy management, in addition to existing shift+rightclick tool removal and GUI view.
- Deeper Housing UX (preview + persistence): Full placed furniture/structures DB load (new loadPlaced* + deletePlaced* in DatabaseManager; managers now populate placedByIsland from DB on load/respawn so list/remove and visuals survive restarts/reloads). Fixed furniture remove (was nuking *all* island entities; now filters by custom name prefix like structures + calls DB delete). Added previewFurniture/previewStructure (Folia-safe ItemDisplay holograms spawned in front of player via ThreadSafety.runAtLocation + runForEntityLater for 15s auto-clean + particles/glow/scale/name). GUIs: Shift+Click on owned catalog item spawns instant preview hologram (no tool consume) + updated lures. IslandManager: structures now use respawnAll for load consistency. DimensionIslandListener: added furniture + structures respawnAll on island enter (dim change) for full lifecycle coverage. BUILD SUCCESS (255 sources).
- Emote triggers polish (per-player + persistence): Triggers (kill/death/join/...) moved from global in-mem map to per-UUID persisted maps. Added player_emote_triggers table + save/load in DatabaseManager. Manager: load/save on join/quit, setTrigger/getTrigger now take UUID + auto-persist. Command updated to pass player UUID + better feedback. Still Folia-safe via existing triggerEmote + listener. Matches "set via command" + groundwork for GUI config. BUILD SUCCESS.
- Emote triggers via GUI + more events: Full GUI support added to EmoteCosmeticGUI (new bottom "Auto-Triggers" section with join/kill/death slots showing live per-player current assignment via manager.getTrigger; click slot to enter assignment mode, then click any owned emote to assign via setTrigger + auto GUI refresh; Shift+Click trigger slot clears; pending state map for flow; help item). Centralized "join" event in EmoteTriggerListener (new PlayerJoinEvent handler) + removed duplicate demo from manager onPlayerJoin to prevent double execution. Wardrobe emotes preview + command help updated to document GUI config. BUILD SUCCESS (255 sources). Directly completes "emote triggers via GUI" from roadmap.
- Titles expansion UI polish: Added 2 new title-centric OverheadCosmetic entries (CHAMPION_TITLE, MYSTIC_TITLE) reusing the "title" effectType (instant collection/Wardrobe/GUI/Prestige/Shop parity). Polished title rendering path in manager (decorative ✦ framing, emphasis scale/seeThrough/shadow for readability, subtle particles; distinguishes titles from halos/auras). Updated OverheadCosmeticGUI header lore + Wardrobe OVERHEAD preview to highlight titles expansion. Continues the "Titles expansion UI polish" item. BUILD SUCCESS.
- More accessory/minion/housing variety (broader polish + integration): Added 3 new AccessoryCosmetic (FLOATING_COMPASS, ORBITING_CRYSTAL, FEATHERED_WING) + extended manager for CMDs/positions/particles (Folia-safe). Added 2 MinionSkin (PHOENIX reusing theme, FROST_GOLEM) and 2 IslandFurnitureType (SLAYER_BANNER, MYSTIC_ALTAR) for housing depth (no manager changes needed as enum-driven). Integration: added SlayerShopGUI example item, PrestigeManager grants at levels 3/5 for new accessories. Wardrobe previews dynamic (auto include new in collection counts). Broader testing via full mvn path coverage. BUILD SUCCESS (255 sources).
- Advanced event triggers polish (broader integration on emotes): Extended EmoteTriggerListener with PlayerRespawnEvent ('respawn') and PlayerLevelChangeEvent ('levelup' on gain) for more advanced triggers. Updated command help, GUI trigger help text/lore to document new keys. Manager comment updated. Fits "more advanced event triggers" in Next. BUILD SUCCESS for testing.
- Chat cosmetics depth (more variety/polish on bubbles): Added 3 new ChatBubbleCosmetic styles (SPARK_BUBBLE, LEAF_BUBBLE, SKULL_BUBBLE) with themed text/particles in manager switch (ENCHANTED_HIT, CHERRY_LEAVES, SMOKE/DRAGON_BREATH). Updated SlayerShopGUI with 2 new shop examples, PrestigeManager grants at levels 3/5. Fits "chat cosmetics depth". BUILD SUCCESS (255 sources).
- Island ambience extensions (music variety + broader integration): Added 5 new IslandMusicType entries (JUNGLE_RHYTHM, MOUNTAIN_WIND, TUNDRA_CHILL, ANCIENT_RUINS, ETHEREAL_MIST) for more ambient depth. Updated SlayerShopGUI with examples, PrestigeManager grants at 3/5. Enhanced IslandMusicManager.onPlayerQuit for stopLoop to ensure no lingering audio (broader integration polish). Verified calls in IslandManager/DimensionIslandListener/PlayerQuitListener. Fits "island ambience extensions". BUILD SUCCESS (255 sources). mvn confirmed after integration tweaks.
- Broader integration testing + overall refinements: Audited FoliaSkyblock registrations, PlayerQuitListener (all quit hooks), DimensionIslandListener (enter/leave), IslandManager (loads). Most complete for all systems (player-based + island-bound). Refined by adding re-apply of active visuals (setActive) for Accessories and OverheadCosmetic on dim/island enter (ensures floating displays re-spawn across world changes). BUILD SUCCESS. Fits "broader integration testing, overall refinements".
- Overall refinements - weather variety: Added 2 new IslandWeatherCosmetic (SANDSTORM, FIREFLY_GLOW) with particle support in manager, shop examples in SlayerShopGUI, prestige grants. Continues polish on recent (weather/music). BUILD SUCCESS (255).
- Overall refinements - minion variety: Added 2 new MinionSkin (PUMPKIN, ROBOT) with shop and prestige integration (using existing particle themes). BUILD SUCCESS.
- Overall refinements - accessory variety: Added 2 new AccessoryCosmetic (FLOATING_KEY, ORBITING_GEM) with manager handling (CMD, scale, particles), shop, prestige. BUILD SUCCESS.

**Deeper Island Housing - Active Set Bonuses (Next after dragon protection review)**
- Extended IslandFurnitureType with 2 new advanced pieces (ETHEREAL_LANTERN, ANCIENT_RUNE_PILLAR) for housing variety.
- Implemented *active placed set bonuses*: when a full themed set (Celestial, Slayer, Mystic) is physically placed on the island, the island now "activates" it.
  - Tracking: updateActiveSetBonuses recomputed after every place/remove/load/respawn (computes from placed list which sets have all required pieces).
  - Unlock-side set completion (throne etc.) was already present; this adds the "placed on island" pride layer.
  - On completion via placement: island XP award + nice FIREWORK/END_ROD burst at island center for the owner(s).
  - Visuals: in spawnFurnitureEntity, pieces belonging to an active set get glowing + extra themed particles on spawn/respawn.
  - Player experience: onPlayerEnterIsland (wired in DimensionIslandListener after furniture respawn) does a welcoming pride particle burst around the placed set pieces (themed per set: END_ROD for Celestial, FLAME for Slayer, PORTAL for Mystic). Visitors see the housing "come alive".
  - Folia-safe throughout (ThreadSafety.runAtLocation for effects).
- Wardrobe Housing preview lore updated to document active set bonuses.
- BUILD SUCCESS (still 255 sources). Directly fulfills "more accessory/minion/housing variety [advanced]" + turns the furnitureSet field into real gameplay-feeling (cosmetic) depth.
- Fits the "deeper housing UX + set bonuses" thread from previous autonomous polish.

**Housing Polish Continuation (Autonomous):**
- Added 2 new furniture pieces for variety: CELESTIAL_LAMP (extends Celestial set) + DECORATIVE_GLOBE (general decor).
- Enhanced Wardrobe "Housing Decor" preview: now shows live "Active on this island: Celestial..." when sets are placed and pride is active; richer lore + management tips.
- IslandFurnitureGUI VIEW_PLACED now reports active set pride summary in chat for player.
- Polished onPlayerEnterIsland: added central set-themed burst at island center + per-piece bursts for stronger "come alive" visitor experience.
- Updated set required counts/comments in manager for new pieces.
- PrestigeManager: grants for CELESTIAL_LAMP (lv4) + DECORATIVE_GLOBE (lv3).
- SlayerShopGUI: added 2 shop purchase examples for the new furniture (generic BUY handler covers).
- Full wiring confirmed (IslandManager load, DimensionIslandListener enter+respawn, place/remove paths all call updateActiveSetBonuses + onEnter pride).
- mvn clean compile at C:\Users\CJ\IdeaProjects\FoliaSkyblock → BUILD SUCCESS (268 sources).
- Purely cosmetic, Folia-safe, collection + Play-to-Win consistent.

**Next (Autonomous):**
1. Per-minion UX polish COMPLETE (shift-clear + particles + feedback + manager/DB delete support in MinionsGUI). More minion skins if desired.
2. Deeper titles / OverheadCosmetic expansion COMPLETE (5+ title styles with name-based frames/particles/scales in manager, Wardrobe/GUI polish, prestige/shop).
3. Emote polish COMPLETE (added NOD/HIGH_FIVE; /emote list subcommand + UX help; GUI header; prestige/shop). More triggers or advanced GUI if needed.
4. Accessory variety expansions COMPLETE (added ORBITING_SWORD + GLOWING_LANTERN reusing manager handling + prestige/shop).
Custom Enchants major upgrade: PDC storage for customs, full runtime effects (10+ implemented: Execute/LifeSteal/Thunderbolt/Replenish etc + 3 new), EnchantEffectListener Folia-safe, prestige book rewards. BUILD SUCCESS (269).
5. New major if momentum (Cosmetic Kill/Death Messages, Island Borders/Flags, schematics opt-in).
6. Core (dragon/End per-island improvements, collections synergy with cosmetics/prestige, **early game / onboarding quests - COMPLETE**).
7. Overall polish + tests. Core Collections now done (feeds the cosmetic unlocks/XP everywhere).

**Non-cosmetic balance (Early Game / Onboarding) - COMPLETE (per IMPROVEMENTS + user directive):**
- Expanded starter chest + guide book, dynamic FIRST quests (QuestManager + 5 onboarding quests), safe EarlyGameListener (anti-cheat guarded), MinionManager hook, /quests integration + free trail reward on first claim. Island create seeds + tips. mvn BUILD SUCCESS. Balances late-game depth (no "boring start"). See IMPROVEMENTS.md for full.

**DB Modularization continuation (affects all persisted cosmetics + holograms etc.):**
- HologramDAO extracted + ItemSerializer polished (modern + fallback) + Mission/Prestige DAO fixes + expanded real H2 tests (including hologram + mission + ser/de). DM thinned with delegations. BUILD SUCCESS.
- **Further passes:** Wardrobe fully moved to CosmeticDAO (save/delete/load/collection with bridge/ItemSerializer); bridge cleanup to IslandDAO/HologramDAO/more in CosmeticDAO; dirty flush to BalanceDAO; tests expanded. BUILD SUCCESS. See IMPROVEMENTS "1." ; next per doc follow-ups: AntiCheat, GUI legacy deprecate, Folia edge, more H2 + admin inspect GUI.

---

## Architectural Notes for Future Cosmetics

- **Always** add a new tab or section in the Wardrobe when possible (Runes tab added as example of full integration).
- New types should get their own `XXXManager`, enum, and `XXXGUI`.
- Collection + rarity XP should be the default unlock reward (consistency with Pets/Tags/Wardrobe equipment).
- Live preview in Wardrobe is the gold standard (even if simulated).
- All new cosmetics must respect the existing prestige/slayer gating patterns.

---

*Document created after deep review of current cosmetic systems and popular Skyblock cosmetic trends (Hypixel and similar servers).*

**Autonomous continuation note:** Emote expansion COMPLETE. Housing set bonuses + variety polish COMPLETE (2 new furniture, live active sets in Wardrobe/GUI, central+per-piece pride bursts on enter, prestige/shop integration, BUILD SUCCESS 268 sources). Minion variety started: added GHOST + ANCIENT_GOLEM skins + prestige/shop + MinionsGUI header UX polish for assignments. Accessory variety continued: added FLOATING_BOOK + ORBITING_RUNE (full manager CMD/scale/particle support + prestige/shop). Per-minion UX polish: Shift+Click clears per-minion assignment in MinionsGUI (with manager clear + DB deleteMinionSkinAssignment support); Folia-safe particle confirmation (HAPPY_VILLAGER on assign, SMOKE on clear via ThreadSafety); improved chat + actionbar feedback; assignment item lore updated. Deeper titles polish COMPLETE: added CELESTIAL_TITLE, SLAYER_SIGIL, ETHEREAL_CROWN (now 5+ titles); enhanced OverheadCosmeticManager title rendering with name-based frames (ᚱ for runic/sigil, ◌ for void, ✧ etc.), varied scales, and extra themed particles (CRIT/PORTAL/END_ROD/CHERRY_LEAVES); updated Wardrobe overhead preview + OverheadCosmeticGUI header to document expanded titles; prestige grants + shop examples. Emote polish: added NOD + HIGH_FIVE; /emote list subcommand in command for owned list + improved help mentioning list; GUI header updated with command hints; prestige + shop examples. Accessory expansions: added ORBITING_SWORD + GLOWING_LANTERN (reused existing manager keys for auto visuals + prestige/shop). Multiple mvn BUILD SUCCESS (268). Next: more emote triggers/GUI or major.