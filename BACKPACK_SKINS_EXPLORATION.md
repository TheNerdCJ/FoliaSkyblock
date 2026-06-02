# Backpack Skins - Quick Exploration (Third Option)

**Chosen for Exploration**: Backpack Skins (frequently mentioned alongside Helmet Skins and Pet Skins in Hypixel Fire Sales).

## Quick Research (from previous + targeted knowledge)
- Hypixel sells many "Backpack Skins" in Fire Sales (e.g. "Coco Backpack Skin", various cute/seasonal designs).
- They change the visual of the player's backpack item when carried or opened.
- Purely cosmetic. Applied to specific backpack items.

## Project Fit Analysis (Code Audit)
- Does the project have a Backpack system?
  - Quick search shows references to "backpack" in some shop/upgrade contexts, but no deep dedicated BackpackManager like the Pet or Wardrobe systems yet.
  - There are chest shops and island storage, but no prominent "player backpack" cosmetic item that players constantly carry (unlike helmets or pets).
- If a backpack system exists or is planned, this would be a natural skin layer (similar to how we plan Helmet Skins on top of real helmets).
- Lower immediate priority than Helmet Skins because:
  - Less constant visual presence (backpacks are not always "on" the player model like helmets).
  - Would require either a existing backpack item or creating one first.

## Recommended Approach if Chosen Later
1. Confirm/expand any existing backpack item in the economy or custom items.
2. Create `BackpackSkin.java` enum (very similar to HelmetSkin/PetSkin).
3. Manager for ownership + application via PDC on the backpack ItemStack.
4. Integrate into Wardrobe (new tab or under Equipment).
5. Shop + Prestige support.

**Verdict**: Good future option once a core "Backpack" item is more prominent in the server. Lower urgency than Helmet Skins right now because the visual hook (constant player model change) is weaker.

If you have a strong backpack system already that I missed in quick audit, this could jump in priority.

---

All three items from your query completed in order via immediate tool execution (code reads, web searches, and creation of detailed plan documents in the main project). 

Next step is yours — pick one plan and say "start implementing [Helmet Skins / Death Effects]" in the usual ordered steps format.