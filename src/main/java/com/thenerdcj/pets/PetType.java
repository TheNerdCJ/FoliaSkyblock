package com.thenerdcj.pets;

import org.bukkit.Material;

/**
 * Defines all available cosmetic (vanity-only) pets for FoliaSkyblock.
 * These are purely visual followers — no combat, no stats, no advantages.
 * This keeps the server strictly Play-to-Win.
 *
 * Now includes rarity for collection XP (wardrobe-style) + future prestige gating.
 */
public enum PetType {

    // Cute / Small pets (COMMON) - low or no gates
    BABY_PARROT("Baby Parrot", Material.FEATHER, "A small colorful parrot that follows you.", "MHF_Parrot", PetRarity.COMMON, 0, 0),
    CAT("Cat", Material.COD, "A loyal feline companion.", "MHF_Cat", PetRarity.COMMON, 0, 0),
    FOX("Fox", Material.SWEET_BERRIES, "A curious fox that trots beside you.", "MHF_Fox", PetRarity.COMMON, 0, 0),
    RABBIT("Rabbit", Material.CARROT, "A bouncy little rabbit.", "MHF_Rabbit", PetRarity.COMMON, 0, 0),

    // Fun / Silly (COMMON)
    SLIME("Friendly Slime", Material.SLIME_BALL, "A bouncy green slime that follows you.", "MHF_Slime", PetRarity.COMMON, 0, 0),
    BEE("Busy Bee", Material.HONEYCOMB, "A cute buzzing bee.", "MHF_Bee", PetRarity.COMMON, 0, 0),
    AXOLOTL("Axolotl", Material.AXOLOTL_BUCKET, "An adorable axolotl that swims in air.", "MHF_Axolotl", PetRarity.COMMON, 0, 0),
    WOLF("Wolf", Material.BONE, "A loyal wolf companion.", "MHF_Wolf", PetRarity.COMMON, 0, 0),
    TURTLE("Turtle", Material.TURTLE_EGG, "A slow but steady turtle.", "MHF_Turtle", PetRarity.COMMON, 0, 0),
    FROG("Frog", Material.FROG_SPAWN_EGG, "A cute hopping frog.", "MHF_Frog", PetRarity.COMMON, 0, 0),
    GOAT("Goat", Material.GOAT_HORN, "A playful mountain goat.", "MHF_Goat", PetRarity.COMMON, 0, 0),
    PARROT("Colorful Parrot", Material.FEATHER, "A vibrant parrot on your shoulder.", "MHF_Parrot", PetRarity.COMMON, 0, 0),

    // Mid-tier companions (UNCOMMON) - small token or low prestige
    PANDA("Panda", Material.BAMBOO, "A chill panda that follows you.", "MHF_Panda", PetRarity.UNCOMMON, 1, 30),
    ALLAY("Allay", Material.ALLAY_SPAWN_EGG, "A helpful allay that dances around you.", "MHF_Allay", PetRarity.UNCOMMON, 1, 40),
    STRAY("Stray", Material.BOW, "A cold stray archer companion.", "MHF_Stray", PetRarity.UNCOMMON, 2, 60),
    VEX("Mischievous Vex", Material.IRON_SWORD, "A small ghostly vex that follows you.", "MHF_Vex", PetRarity.UNCOMMON, 2, 55),

    // Rare / Cool (RARE) - moderate
    ENDERMAN("Mini Enderman", Material.ENDER_PEARL, "A teleporting little enderman.", "MHF_Enderman", PetRarity.RARE, 3, 90),
    BLAZE("Blaze", Material.BLAZE_ROD, "A fiery floating blaze.", "MHF_Blaze", PetRarity.RARE, 3, 100),
    GHAST("Baby Ghast", Material.GHAST_TEAR, "A tiny floating ghast.", "MHF_Ghast", PetRarity.RARE, 4, 120),
    SNIFFER("Sniffer", Material.SNIFFER_EGG, "An ancient gentle giant.", "MHF_Sniffer", PetRarity.RARE, 4, 130),
    CAMEL("Camel", Material.SADDLE, "A chill desert camel.", "MHF_Camel", PetRarity.RARE, 3, 85),

    // High fantasy / prestige rewards (EPIC+)
    BABY_DRAGON("Baby Dragon", Material.DRAGON_BREATH, "A tiny dragon that hovers near you.", "MHF_Dragon", PetRarity.EPIC, 5, 200),
    WISP("Mystic Wisp", Material.END_ROD, "A floating ethereal light.", "MHF_Enderman", PetRarity.EPIC, 5, 180),
    PHOENIX("Mini Phoenix", Material.BLAZE_POWDER, "A small fiery phoenix spirit.", "MHF_Blaze", PetRarity.LEGENDARY, 6, 0);  // prestige reward only (tokenCost=0)

    private final String displayName;
    private final Material icon;
    private final String description;
    private final String headTextureOwner; // Skull owner for custom head (MHF_ names or player names)
    private final PetRarity rarity;
    private final int minPrestige;  // Prestige level required to unlock (0 = always available via tokens or starter)
    private final int tokenCost;    // Slayer token cost (0 = prestige reward / free at minPrestige)

    PetType(String displayName, Material icon, String description, String headTextureOwner, PetRarity rarity, int minPrestige, int tokenCost) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.headTextureOwner = headTextureOwner;
        this.rarity = rarity != null ? rarity : PetRarity.COMMON;
        this.minPrestige = Math.max(0, minPrestige);
        this.tokenCost = Math.max(0, tokenCost);
    }

    // Backward compat constructor if needed (defaults to COMMON, 0/0)
    PetType(String displayName, Material icon, String description) {
        this(displayName, icon, description, "MHF_Chicken", PetRarity.COMMON, 0, 0);
    }

    // Legacy 4-arg (for any old references) - defaults rarity + 0/0
    PetType(String displayName, Material icon, String description, String headTextureOwner) {
        this(displayName, icon, description, headTextureOwner, PetRarity.COMMON, 0, 0);
    }

    // 5-arg legacy from previous step
    PetType(String displayName, Material icon, String description, String headTextureOwner, PetRarity rarity) {
        this(displayName, icon, description, headTextureOwner, rarity, 0, 0);
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public String getHeadTextureOwner() {
        return headTextureOwner;
    }

    public PetRarity getRarity() {
        return rarity;
    }

    public int getMinPrestige() {
        return minPrestige;
    }

    public int getTokenCost() {
        return tokenCost;
    }

    /**
     * Quick gating helper (prestige + tokens held). Actual unlock still consumes via shop.
     */
    public boolean canUnlock(int prestigeLevel, int tokensHeld) {
        if (prestigeLevel < minPrestige) return false;
        return tokenCost == 0 || tokensHeld >= tokenCost;
    }
}
