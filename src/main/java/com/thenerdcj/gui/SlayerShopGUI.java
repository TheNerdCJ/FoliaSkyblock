package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.BossManager;
import com.thenerdcj.cosmetic.ParticleTrail;
import com.thenerdcj.island.Island;
import com.thenerdcj.pets.PetType;
import com.thenerdcj.pets.PetSkin;
import com.thenerdcj.tags.PlayerTag;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.runes.Rune;
import com.thenerdcj.cosmetic.HelmetSkin;
import com.thenerdcj.cosmetic.DeathEffect;
import com.thenerdcj.cosmetic.BackpackSkin;
import com.thenerdcj.cosmetic.PowerOrbSkin;
import com.thenerdcj.cosmetic.MinionSkin;
import com.thenerdcj.cosmetic.IslandFurnitureType;
import com.thenerdcj.cosmetic.IslandMusicType;
import com.thenerdcj.cosmetic.OverheadCosmetic;
import com.thenerdcj.cosmetic.EmoteCosmetic;
import com.thenerdcj.cosmetic.IslandStructureCosmetic;
import com.thenerdcj.cosmetic.ChatBubbleCosmetic;
import com.thenerdcj.cosmetic.IslandWeatherCosmetic;
import com.thenerdcj.cosmetic.AccessoryCosmetic;
import com.thenerdcj.wings.ElytraWing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Dedicated Slayer Shop / Vendor.
 * Players spend Slayer Tokens (earned from Slayer quests and Island Boss Events)
 * on exclusive gear, crates, boosters, and cosmetics.
 *
 * Deep modernization pass:
 * - All three manual creation helpers (createItem, createShopItem, createTrailShopItem) converted to GUIUtils.createItem + PDC attachment helpers.
 * - Title now uses MessageUtil.legacy.
 * - Preserved full PDC routing for trail purchases (ACTION_KEY + TRAIL_KEY), prestige gating, token consumption via BossManager, and legacy name-based gear/key purchases.
 * - Integrates with PrestigeManager, ParticleTrailManager, CrateManager.
 */
public class SlayerShopGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final BossManager bossManager;
    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey TRAIL_KEY;
    private final NamespacedKey PET_KEY;
    private final NamespacedKey TAG_KEY;
    private final NamespacedKey PET_SKIN_KEY;
    private final NamespacedKey WING_KEY;
    private final NamespacedKey RUNE_KEY;

    public SlayerShopGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.bossManager = plugin.getBossManager();
        this.ACTION_KEY = new NamespacedKey(plugin, "slayer_shop_action");
        this.TRAIL_KEY = new NamespacedKey(plugin, "slayer_shop_trail");
        this.PET_KEY = new NamespacedKey(plugin, "slayer_shop_pet");
        this.TAG_KEY = new NamespacedKey(plugin, "slayer_shop_tag");
        this.PET_SKIN_KEY = new NamespacedKey(plugin, "slayer_shop_pet_skin");
        this.WING_KEY = new NamespacedKey(plugin, "slayer_shop_wing");
        this.RUNE_KEY = new NamespacedKey(plugin, "slayer_shop_rune");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, Island island) {
        Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§6§lSlayer Shop - Spend Tokens §7| Click categories, use nav arrows"));

        // Header - modernized with navigation tip
        gui.setItem(4, createItem(Material.NETHER_STAR, "§6§lSlayer Shop",
                "§7Earn Slayer Tokens from quests & island bosses",
                "§7Spend them here for exclusive rewards!",
                "§eBrowse tabs for different cosmetics | Close at bottom"));

        // === SLAYER GEAR & KEYS (existing) ===
        gui.setItem(19, createShopItem("§cRevenant Scythe", Material.DIAMOND_SWORD, 150,
                "§7+25% damage to Zombies", "§7Unlocks special zombie drops"));

        gui.setItem(21, createShopItem("§5Tarantula Helmet", Material.DIAMOND_HELMET, 200,
                "§7+15% spider damage resistance", "§7Chance to avoid poison"));

        gui.setItem(23, createShopItem("§6Voidgloom Cloak", Material.ELYTRA, 350,
                "§7Enderman teleport immunity", "§7+Ender pearl drop rate"));

        gui.setItem(25, createShopItem("§eEpic Slayer Crate Key", Material.TRIPWIRE_HOOK, 75,
                "§7Contains high-tier slayer loot"));

        // === CORE COLLECTIONS synergy rewards (Play-to-Win gathering progression) ===
        ItemStack collCrate = createShopItem("§6Collection Milestone Crate", Material.BOOK, 100,
                "§7Rewards based on your island's unique item collection count",
                "§7Higher island collections = better rewards (cosmetics, tokens)");
        attachCollectionPDC(collCrate, "COLLECTION_CRATE");
        gui.setItem(26, collCrate);

        ItemStack collToken = createShopItem("§aCollector Accessory Token", Material.NETHER_STAR, 80,
                "§7Unlocks a special floating accessory",
                "§7Earned faster with high island collections");
        attachCollectionPDC(collToken, "COLLECTION_ACCESSORY");
        gui.setItem(27, collToken);

        // === PERSONAL COSMETICS: PARTICLE TRAILS (new wiring) ===
        gui.setItem(3, createItem(Material.FIREWORK_ROCKET, "§d§lCosmetics - Particle Trails",
                "§7Unlock personal trails & auras",
                "§7Also available in §e/trail menu"));

        // === PETS: Prestige + Slayer Token unlocks (Play-to-Win collection) ===
        gui.setItem(6, createItem(Material.BONE, "§d§lCosmetic Pets",
                "§7Rarity-based followers earned via progression",
                "§7See Wardrobe > Pets tab after unlock"));

        // Prestige-gated or token affordable trails (real consume + unlock)
        gui.setItem(37, createTrailShopItem("Flame Trail", ParticleTrail.FLAME_TRAIL, Material.BLAZE_POWDER));
        gui.setItem(38, createTrailShopItem("Heart Aura", ParticleTrail.HEART_AURA, Material.REDSTONE));
        gui.setItem(39, createTrailShopItem("Soul Flame Trail", ParticleTrail.SOUL_TRAIL, Material.SOUL_SAND));
        gui.setItem(40, createTrailShopItem("Rainbow Dust", ParticleTrail.RAINBOW_DUST, Material.GLOWSTONE_DUST));
        gui.setItem(41, createTrailShopItem("Dragon Breath Aura", ParticleTrail.DRAGON_BREATH, Material.DRAGON_BREATH));
        gui.setItem(42, createTrailShopItem("Electric Spark", ParticleTrail.ELECTRIC_TRAIL, Material.LIGHTNING_ROD));

        // New expanded cosmetic trails (Play-to-Win vanity)
        gui.setItem(28, createTrailShopItem("Angel Wings", ParticleTrail.WING_AURA, Material.FEATHER));
        gui.setItem(29, createTrailShopItem("Orbiting Orbs", ParticleTrail.ORBITING_ORBS, Material.ENDER_EYE));
        gui.setItem(30, createTrailShopItem("DNA Helix", ParticleTrail.HELIX_TRAIL, Material.STRING));
        gui.setItem(31, createTrailShopItem("Frost Aura", ParticleTrail.FROST_AURA, Material.SNOW_BLOCK));
        gui.setItem(32, createTrailShopItem("Galaxy Swirl", ParticleTrail.GALAXY_AURA, Material.PURPLE_DYE));
        gui.setItem(33, createTrailShopItem("Holy Light", ParticleTrail.HOLY_AURA, Material.GLOWSTONE_DUST));
        gui.setItem(34, createTrailShopItem("Bubble Stream", ParticleTrail.BUBBLE_AURA, Material.PRISMARINE_SHARD));
        gui.setItem(35, createTrailShopItem("Falling Leaves", ParticleTrail.LEAF_TRAIL, Material.OAK_LEAVES));

        // Further expanded trails
        gui.setItem(25, createTrailShopItem("Entangling Vines", ParticleTrail.VINE_TRAIL, Material.VINE));
        gui.setItem(26, createTrailShopItem("Flower Bloom", ParticleTrail.FLOWER_AURA, Material.POPPY));
        gui.setItem(27, createTrailShopItem("Arcane Runes", ParticleTrail.ARCANE_AURA, Material.ENCHANTED_BOOK));
        gui.setItem(24, createTrailShopItem("Whirling Wind", ParticleTrail.WIND_TRAIL, Material.FEATHER));
        gui.setItem(23, createTrailShopItem("Heart Circle", ParticleTrail.HEART_RING, Material.RED_DYE));
        gui.setItem(22, createTrailShopItem("Star Shower", ParticleTrail.STAR_BURST, Material.NETHER_STAR));
        gui.setItem(21, createTrailShopItem("Guardian Sphere", ParticleTrail.GUARDIAN_SPHERE, Material.PRISMARINE_CRYSTALS));
        gui.setItem(20, createTrailShopItem("Dragon Wings", ParticleTrail.DRAGON_WINGS, Material.DRAGON_HEAD));
        gui.setItem(19, createTrailShopItem("Golden Halo", ParticleTrail.HALO_GOLD, Material.GOLD_NUGGET));
        gui.setItem(18, createTrailShopItem("Void Vortex", ParticleTrail.VOID_VORTEX, Material.OBSIDIAN));
        gui.setItem(17, createTrailShopItem("Ground Wave", ParticleTrail.GROUND_WAVE, Material.SLIME_BALL));

        // Pet unlocks via tokens / prestige (new for item 2)
        gui.setItem(44, createPetShopItem("Panda", PetType.PANDA, Material.BAMBOO));
        gui.setItem(45, createPetShopItem("Allay", PetType.ALLAY, Material.ALLAY_SPAWN_EGG));
        gui.setItem(46, createPetShopItem("Enderman", PetType.ENDERMAN, Material.ENDER_PEARL));
        gui.setItem(47, createPetShopItem("Baby Dragon", PetType.BABY_DRAGON, Material.DRAGON_BREATH));
        gui.setItem(48, createPetShopItem("Phoenix", PetType.PHOENIX, Material.BLAZE_POWDER));

        // Tag examples (new cosmetic tag system)
        gui.setItem(50, createTagShopItem("Slayer Tag", PlayerTag.SLAYER, Material.IRON_SWORD));
        gui.setItem(51, createTagShopItem("Collector Tag", PlayerTag.COLLECTOR, Material.BOOK));

        // Pet Skin examples
        gui.setItem(52, createPetSkinShopItem("Neon Skin", PetSkin.NEON, Material.GLOWSTONE_DUST));
        gui.setItem(53, createPetSkinShopItem("Golden Skin", PetSkin.GOLDEN, Material.GOLD_NUGGET));
        gui.setItem(54, createPetSkinShopItem("Rainbow Skin", PetSkin.RAINBOW, Material.FIREWORK_STAR));
        gui.setItem(55, createPetSkinShopItem("Slayer Beast Skin", PetSkin.SLAYER_BEAST, Material.IRON_SWORD));

        // Helmet Skin examples (new cosmetic system)
        gui.setItem(56, createHelmetSkinShopItem("Crimson Flame", HelmetSkin.CRIMSON_FLAME, Material.DIAMOND_HELMET));
        gui.setItem(57, createHelmetSkinShopItem("Blazing Crimson", HelmetSkin.BLAZING_CRIMSON, Material.NETHERITE_HELMET));

        // Death Effect examples (new cosmetic system)
        gui.setItem(58, createDeathEffectShopItem("Firework Burst", DeathEffect.FIREWORK, Material.FIREWORK_ROCKET));
        gui.setItem(59, createDeathEffectShopItem("Lightning Strike", DeathEffect.LIGHTNING, Material.LIGHTNING_ROD));

        // Death Messages cosmetic examples (new system)
        gui.setItem(60, createDeathMessageShopItem("Classic Slayer", com.thenerdcj.cosmetic.DeathMessageCosmetic.CLASSIC, Material.NAME_TAG));
        gui.setItem(61, createDeathMessageShopItem("Epic Fail", com.thenerdcj.cosmetic.DeathMessageCosmetic.EPIC, Material.WRITABLE_BOOK));

        // Backpack Skin examples (exploration)
        gui.setItem(60, createBackpackSkinShopItem("Garden Bunny", BackpackSkin.GARDEN_BUNNY, Material.CHEST));
        gui.setItem(61, createBackpackSkinShopItem("Dragon Hoard", BackpackSkin.DRAGON_HOARD, Material.ENDER_CHEST));

        // Power Orb Skin examples (new system)
        gui.setItem(62, createPowerOrbSkinShopItem("Disco Ball", PowerOrbSkin.DISCO_BALL, Material.MAGMA_CREAM));
        gui.setItem(63, createPowerOrbSkinShopItem("Gem Power Orb", PowerOrbSkin.GEM, Material.DIAMOND));

        // Bonus: Direct orb item with skin (activates immediately on purchase)
        gui.setItem(70, createPowerOrbSkinShopItem("Celestial Orb (with item)", PowerOrbSkin.CELESTIAL, Material.END_CRYSTAL));

        // Island Furniture examples (foundation)
        gui.setItem(71, createFurnitureShopItem("Wooden Chair", IslandFurnitureType.WOODEN_CHAIR, Material.OAK_STAIRS));
        gui.setItem(72, createFurnitureShopItem("Hanging Lantern", IslandFurnitureType.LANTERN, Material.LANTERN));
        // Housing variety polish shop examples
        gui.setItem(88, createFurnitureShopItem("Celestial Lamp", IslandFurnitureType.CELESTIAL_LAMP, Material.END_ROD));
        gui.setItem(89, createFurnitureShopItem("Decorative Globe", IslandFurnitureType.DECORATIVE_GLOBE, Material.GOLD_NUGGET));

        // Island Music examples (new)
        gui.setItem(73, createMusicShopItem("Calm Ocean", IslandMusicType.CALM_OCEAN, Material.MUSIC_DISC_13));
        gui.setItem(74, createMusicShopItem("Celestial Chimes", IslandMusicType.CELESTIAL_CHIMES, Material.MUSIC_DISC_5));
        // Island ambience extensions examples
        gui.setItem(75, createMusicShopItem("Jungle Rhythm", IslandMusicType.JUNGLE_RHYTHM, Material.MUSIC_DISC_11));
        gui.setItem(76, createMusicShopItem("Ancient Ruins", IslandMusicType.ANCIENT_RUINS, Material.MUSIC_DISC_5));

        // Overhead Cosmetics examples (new system)
        gui.setItem(75, createOverheadShopItem("Star Halo", OverheadCosmetic.STAR_HALO, Material.END_ROD));
        gui.setItem(76, createOverheadShopItem("Enchant Glow", OverheadCosmetic.ENCHANT_GLOW, Material.ENCHANTED_BOOK));
        // Deeper titles shop examples
        gui.setItem(92, createOverheadShopItem("Runic Title", OverheadCosmetic.RUNIC_TITLE, Material.PAPER));
        gui.setItem(93, createOverheadShopItem("Void Crown", OverheadCosmetic.VOID_CROWN, Material.NETHER_STAR));
        // Continued deeper titles shop examples
        gui.setItem(94, createOverheadShopItem("Celestial Title", OverheadCosmetic.CELESTIAL_TITLE, Material.END_ROD));
        gui.setItem(95, createOverheadShopItem("Slayer Sigil", OverheadCosmetic.SLAYER_SIGIL, Material.REDSTONE));
        gui.setItem(96, createOverheadShopItem("Ethereal Crown", OverheadCosmetic.ETHEREAL_CROWN, Material.GHAST_TEAR));

        // Emote Cosmetics examples (new)
        gui.setItem(77, createEmoteShopItem("Wave", EmoteCosmetic.WAVE, Material.TOTEM_OF_UNDYING));
        gui.setItem(78, createEmoteShopItem("Cheer", EmoteCosmetic.CHEER, Material.TOTEM_OF_UNDYING));
        // Emote expansion
        gui.setItem(79, createEmoteShopItem("Clap", EmoteCosmetic.CLAP, Material.TOTEM_OF_UNDYING));
        gui.setItem(80, createEmoteShopItem("Point", EmoteCosmetic.POINT, Material.TOTEM_OF_UNDYING));
        gui.setItem(81, createEmoteShopItem("Cry", EmoteCosmetic.CRY, Material.TOTEM_OF_UNDYING));
        gui.setItem(82, createEmoteShopItem("Victory", EmoteCosmetic.VICTORY, Material.TOTEM_OF_UNDYING));
        // Emote polish variety shop examples
        gui.setItem(83, createEmoteShopItem("Nod", EmoteCosmetic.NOD, Material.TOTEM_OF_UNDYING));
        gui.setItem(84, createEmoteShopItem("High Five", EmoteCosmetic.HIGH_FIVE, Material.TOTEM_OF_UNDYING));

        // Island Structures examples (new)
        gui.setItem(79, createStructureShopItem("Stone Pillar", IslandStructureCosmetic.STONE_PILLAR, Material.STONE));
        gui.setItem(80, createStructureShopItem("Wooden Arch", IslandStructureCosmetic.WOODEN_ARCH, Material.OAK_LOG));

        // Chat Bubbles examples (new)
        gui.setItem(81, createChatBubbleShopItem("Heart Bubble", ChatBubbleCosmetic.HEART_BUBBLE, Material.PAPER));
        gui.setItem(82, createChatBubbleShopItem("Magic Bubble", ChatBubbleCosmetic.MAGIC_BUBBLE, Material.ENCHANTED_BOOK));
        gui.setItem(83, createChatBubbleShopItem("Spark Bubble", ChatBubbleCosmetic.SPARK_BUBBLE, Material.GLOWSTONE_DUST));
        gui.setItem(84, createChatBubbleShopItem("Skull Bubble", ChatBubbleCosmetic.SKULL_BUBBLE, Material.SKELETON_SKULL));

        // Island Weather examples (new)
        gui.setItem(83, createWeatherShopItem("Gentle Rain", IslandWeatherCosmetic.GENTLE_RAIN, Material.WATER_BUCKET));
        gui.setItem(84, createWeatherShopItem("Aurora", IslandWeatherCosmetic.AURORA, Material.END_ROD));
        // Refinements - more weather variety shop examples
        gui.setItem(85, createWeatherShopItem("Sandstorm", IslandWeatherCosmetic.SANDSTORM, Material.SAND));
        gui.setItem(86, createWeatherShopItem("Firefly Glow", IslandWeatherCosmetic.FIREFLY_GLOW, Material.GLOW_BERRIES));

        // Accessories examples (new)
        gui.setItem(85, createAccessoryShopItem("Floating Star", AccessoryCosmetic.FLOATING_STAR, Material.NETHER_STAR));
        gui.setItem(86, createAccessoryShopItem("Orbiting Orb", AccessoryCosmetic.ORBITING_ORB, Material.ENDER_PEARL));
        gui.setItem(87, createAccessoryShopItem("Floating Compass", AccessoryCosmetic.FLOATING_COMPASS, Material.COMPASS));
        // Refinements - more accessory
        gui.setItem(88, createAccessoryShopItem("Floating Key", AccessoryCosmetic.FLOATING_KEY, Material.GOLD_NUGGET));
        gui.setItem(89, createAccessoryShopItem("Orbiting Gem", AccessoryCosmetic.ORBITING_GEM, Material.DIAMOND));
        // Accessory variety continuation shop examples
        gui.setItem(90, createAccessoryShopItem("Floating Tome", AccessoryCosmetic.FLOATING_BOOK, Material.BOOK));
        gui.setItem(91, createAccessoryShopItem("Orbiting Rune", AccessoryCosmetic.ORBITING_RUNE, Material.MAGENTA_DYE));
        // Accessory expansions shop examples
        gui.setItem(94, createAccessoryShopItem("Orbiting Sword", AccessoryCosmetic.ORBITING_SWORD, Material.IRON_SWORD));
        gui.setItem(95, createAccessoryShopItem("Glowing Lantern Charm", AccessoryCosmetic.GLOWING_LANTERN, Material.LANTERN));

        // Minion Skin examples (new system)
        gui.setItem(64, createMinionSkinShopItem("Bunny Theme", MinionSkin.BUNNY, Material.RABBIT_HIDE));
        gui.setItem(65, createMinionSkinShopItem("Busy Bee", MinionSkin.BEE, Material.HONEYCOMB));
        // Refinements - more minion variety
        gui.setItem(66, createMinionSkinShopItem("Pumpkin Pal", MinionSkin.PUMPKIN, Material.PUMPKIN));
        gui.setItem(67, createMinionSkinShopItem("Robot Minion", MinionSkin.ROBOT, Material.IRON_INGOT));
        // Continued minion variety shop examples
        gui.setItem(68, createMinionSkinShopItem("Ghostly Minion", MinionSkin.GHOST, Material.SOUL_SAND));
        gui.setItem(69, createMinionSkinShopItem("Ancient Golem", MinionSkin.ANCIENT_GOLEM, Material.MOSSY_COBBLESTONE));

        // Elytra Wing cosmetics (new gliding visual system)
        gui.setItem(42, createWingShopItem("Angel Wings", ElytraWing.ANGEL, Material.FEATHER));
        gui.setItem(43, createWingShopItem("Dragon Wings", ElytraWing.DRAGON, Material.DRAGON_HEAD));

        // Rune examples
        gui.setItem(40, createRuneShopItem("Blood Rune", Rune.BLOOD, Material.REDSTONE));
        gui.setItem(41, createRuneShopItem("Lightning Rune", Rune.LIGHTNING, Material.LIGHTNING_ROD));

        // Token balance
        int tokens = (plugin.getBossManager() != null) ? plugin.getBossManager().getTotalSlayerTokens(player) : 0;
        gui.setItem(49, createItem(Material.GOLD_NUGGET, "§eYour Slayer Tokens",
                "§7Current: §6" + tokens,
                "§7Tokens are consumed from your inventory"));

        // Easy navigation: close button
        ItemStack closeBtn = GUIUtils.createItem(Material.BARRIER, "§c§lClose Shop",
            "§7Click to exit",
            "§eEarn more tokens from /slayer quests");
        ItemMeta cMeta = closeBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "CLOSE");
            closeBtn.setItemMeta(cMeta);
        }
        gui.setItem(53, closeBtn);

        player.openInventory(gui);
    }

    private ItemStack createTrailShopItem(String label, ParticleTrail trail, Material mat) {
        // Base item via GUIUtils (modernized)
        ItemStack item = GUIUtils.createItem(mat, "§dUnlock " + label,
                "§7Personal cosmetic trail / aura",
                "",
                "§7Prestige Req: §b" + trail.getMinPrestige(),
                "§6Cost: §e" + trail.getTokenCost() + " Slayer Tokens",
                (trail.getTokenCost() == 0 ? "§aFREE Prestige Reward" : ""),
                "",
                "§aClick to purchase & unlock",
                "§8Opens in your trail collection");

        // Attach the two PDCs for robust trail purchase routing (preserved exactly)
        attachTrailPDCs(item, trail);
        return item;
    }

    private void attachTrailPDCs(ItemStack item, ParticleTrail trail) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_TRAIL");
            pdc.set(TRAIL_KEY, PersistentDataType.STRING, trail.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createPetShopItem(String label, PetType petType, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§dUnlock " + label + " Pet",
                "§7Rarity: " + petType.getRarity().getColorCode() + petType.getRarity().getDisplayName(),
                "§7" + petType.getDescription(),
                "",
                "§7Prestige Req: §b" + petType.getMinPrestige(),
                "§6Cost: §e" + petType.getTokenCost() + " Slayer Tokens",
                (petType.getTokenCost() == 0 ? "§aFREE Prestige Reward" : ""),
                "",
                "§aClick to purchase & unlock",
                "§8Appears in your Pets collection");

        attachPetPDCs(item, petType);
        return item;
    }

    private void attachPetPDCs(ItemStack item, PetType petType) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_PET");
            pdc.set(PET_KEY, PersistentDataType.STRING, petType.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createTagShopItem(String label, PlayerTag tag, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§dUnlock " + label,
                "§7Rarity: " + tag.getRarity().getColorCode() + tag.getRarity().getDisplayName(),
                "§7" + tag.getDescription(),
                "",
                "§7Prestige Req: §b" + tag.getMinPrestige(),
                "§6Cost: §e" + tag.getTokenCost() + " Slayer Tokens",
                (tag.getTokenCost() == 0 ? "§aFREE Prestige Reward" : ""),
                "",
                "§aClick to purchase & equip",
                "§8Shows in chat & tab list");

        attachTagPDCs(item, tag);
        return item;
    }

    private void attachTagPDCs(ItemStack item, PlayerTag tag) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_TAG");
            pdc.set(TAG_KEY, PersistentDataType.STRING, tag.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createPetSkinShopItem(String label, PetSkin skin, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§dUnlock " + label + " (Pet Skin)",
                "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                "§7" + skin.getDescription(),
                "",
                "§7Prestige Req: §b" + skin.getMinPrestige(),
                "§6Cost: §e" + skin.getTokenCost() + " Slayer Tokens",
                (skin.getTokenCost() == 0 ? "§aFREE Prestige Reward" : ""),
                "",
                "§aClick to purchase & unlock",
                "§8Apply in /pets GUI");

        attachPetSkinPDCs(item, skin);
        return item;
    }

    private void attachPetSkinPDCs(ItemStack item, PetSkin skin) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_PET_SKIN");
            pdc.set(PET_SKIN_KEY, PersistentDataType.STRING, skin.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createHelmetSkinShopItem(String label, HelmetSkin skin, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§6Unlock " + label + " (Helmet Skin)",
                "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                "§7" + skin.getDescription(),
                "",
                "§7Prestige Req: §b" + skin.getMinPrestige(),
                "§6Cost: §e" + skin.getTokenCost() + " Slayer Tokens",
                (skin.getTokenCost() == 0 ? "§aFREE Prestige Reward" : ""),
                "",
                "§aClick to purchase & unlock",
                "§8Applies cosmetic override to helmets");

        attachHelmetSkinPDCs(item, skin);
        return item;
    }

    private void attachHelmetSkinPDCs(ItemStack item, HelmetSkin skin) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_HELMET_SKIN");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_helmet_skin"), PersistentDataType.STRING, skin.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createDeathEffectShopItem(String label, DeathEffect effect, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§4Unlock " + label + " (Death Effect)",
                "§7Rarity: " + effect.getRarity().getColorCode() + effect.getRarity().getDisplayName(),
                "§7" + effect.getDescription(),
                "",
                "§7Prestige Req: §b" + effect.getMinPrestige(),
                "§6Cost: §e" + effect.getTokenCost() + " Slayer Tokens",
                (effect.getTokenCost() == 0 ? "§aFREE Prestige Reward" : ""),
                "",
                "§aClick to purchase & unlock",
                "§8Triggers on death and kills");

        attachDeathEffectPDCs(item, effect);
        return item;
    }

    private void attachDeathEffectPDCs(ItemStack item, DeathEffect effect) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_DEATH_EFFECT");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_death_effect"), PersistentDataType.STRING, effect.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createDeathMessageShopItem(String label, com.thenerdcj.cosmetic.DeathMessageCosmetic message, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§4Unlock " + label + " (Death Message)",
                "§7Rarity: " + message.getRarity().getColorCode() + message.getRarity().getDisplayName(),
                "§7" + message.getDescription(),
                "§7Cost: §e" + message.getTokenCost() + " Slayer Tokens");
        attachDeathMessagePDCs(item, message);
        return item;
    }

    private void attachDeathMessagePDCs(ItemStack item, com.thenerdcj.cosmetic.DeathMessageCosmetic message) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_DEATH_MESSAGE");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_death_message"), PersistentDataType.STRING, message.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createBackpackSkinShopItem(String label, BackpackSkin skin, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§aUnlock " + label + " (Backpack Skin)",
                "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                "§7" + skin.getDescription(),
                "",
                "§7Prestige Req: §b" + skin.getMinPrestige(),
                "§6Cost: §e" + skin.getTokenCost() + " Slayer Tokens",
                (skin.getTokenCost() == 0 ? "§aFREE Prestige Reward" : ""),
                "",
                "§aClick to purchase & unlock",
                "§8Cosmetic override for backpacks/storage");

        attachBackpackSkinPDCs(item, skin);
        return item;
    }

    private void attachBackpackSkinPDCs(ItemStack item, BackpackSkin skin) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_BACKPACK_SKIN");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_backpack_skin"), PersistentDataType.STRING, skin.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createPowerOrbSkinShopItem(String label, PowerOrbSkin skin, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§bUnlock " + label + " (Power Orb Skin)",
                "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                "§7" + skin.getDescription(),
                "",
                "§7Prestige Req: §b" + skin.getMinPrestige(),
                "§6Cost: §e" + skin.getTokenCost() + " Slayer Tokens",
                (skin.getTokenCost() == 0 ? "§aFREE Prestige Reward" : ""),
                "",
                "§aClick to purchase & unlock",
                "§8Cosmetic override for Power Orbs");

        attachPowerOrbSkinPDCs(item, skin);
        return item;
    }

    private void attachPowerOrbSkinPDCs(ItemStack item, PowerOrbSkin skin) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_POWER_ORB_SKIN");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_power_orb_skin"), PersistentDataType.STRING, skin.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createMinionSkinShopItem(String label, MinionSkin skin, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§6Unlock " + label + " (Minion Skin)",
                "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                "§7" + skin.getDescription(),
                "§7Cost: §e" + skin.getTokenCost() + " Slayer Tokens",
                "§7Applies themed appearance to all your minions");
        attachMinionSkinPDCs(item, skin);
        return item;
    }

    private void attachMinionSkinPDCs(ItemStack item, MinionSkin skin) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_MINION_SKIN");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_minion_skin"), PersistentDataType.STRING, skin.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createFurnitureShopItem(String label, IslandFurnitureType furn, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§6Unlock " + label + " (Furniture)",
                "§7Rarity: " + furn.getRarity().getColorCode() + furn.getRarity().getDisplayName(),
                "§7" + furn.getDescription(),
                "§7Cost: §e" + furn.getTokenCost() + " Slayer Tokens");
        attachFurniturePDCs(item, furn);
        return item;
    }

    private void attachFurniturePDCs(ItemStack item, IslandFurnitureType furn) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_FURNITURE");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_furniture"), PersistentDataType.STRING, furn.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createMusicShopItem(String label, IslandMusicType music, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§dUnlock " + label + " (Island Music)",
                "§7Rarity: " + music.getRarity().getColorCode() + music.getRarity().getDisplayName(),
                "§7" + music.getDescription(),
                "§7Cost: §e" + music.getTokenCost() + " Slayer Tokens");
        attachMusicPDCs(item, music);
        return item;
    }

    private void attachMusicPDCs(ItemStack item, IslandMusicType music) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_MUSIC");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_music"), PersistentDataType.STRING, music.name());
            item.setItemMeta(meta);
        }
    }

    private void attachCollectionPDC(ItemStack item, String rewardType) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_COLLECTION");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_collection"), PersistentDataType.STRING, rewardType);
            item.setItemMeta(meta);
        }
    }

    private ItemStack createOverheadShopItem(String label, OverheadCosmetic cosmetic, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§5Unlock " + label + " (Overhead Cosmetic)",
                "§7Rarity: " + cosmetic.getRarity().getColorCode() + cosmetic.getRarity().getDisplayName(),
                "§7" + cosmetic.getDescription(),
                "§7Cost: §e" + cosmetic.getTokenCost() + " Slayer Tokens");
        attachOverheadPDCs(item, cosmetic);
        return item;
    }

    private void attachOverheadPDCs(ItemStack item, OverheadCosmetic cosmetic) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_OVERHEAD");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_overhead"), PersistentDataType.STRING, cosmetic.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createEmoteShopItem(String label, EmoteCosmetic emote, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§dUnlock " + label + " (Emote)",
                "§7Rarity: " + emote.getRarity().getColorCode() + emote.getRarity().getDisplayName(),
                "§7" + emote.getDescription(),
                "§7Cost: §e" + emote.getTokenCost() + " Slayer Tokens");
        attachEmotePDCs(item, emote);
        return item;
    }

    private void attachEmotePDCs(ItemStack item, EmoteCosmetic emote) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_EMOTE");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_emote"), PersistentDataType.STRING, emote.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createStructureShopItem(String label, IslandStructureCosmetic structure, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§6Unlock " + label + " (Island Structure)",
                "§7Rarity: " + structure.getRarity().getColorCode() + structure.getRarity().getDisplayName(),
                "§7" + structure.getDescription(),
                "§7Cost: §e" + structure.getTokenCost() + " Slayer Tokens");
        attachStructurePDCs(item, structure);
        return item;
    }

    private void attachStructurePDCs(ItemStack item, IslandStructureCosmetic structure) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_STRUCTURE");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_structure"), PersistentDataType.STRING, structure.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createChatBubbleShopItem(String label, ChatBubbleCosmetic bubble, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§dUnlock " + label + " (Chat Bubble)",
                "§7Rarity: " + bubble.getRarity().getColorCode() + bubble.getRarity().getDisplayName(),
                "§7" + bubble.getDescription(),
                "§7Cost: §e" + bubble.getTokenCost() + " Slayer Tokens");
        attachChatBubblePDCs(item, bubble);
        return item;
    }

    private void attachChatBubblePDCs(ItemStack item, ChatBubbleCosmetic bubble) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_CHAT_BUBBLE");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_chatbubble"), PersistentDataType.STRING, bubble.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createWeatherShopItem(String label, IslandWeatherCosmetic weather, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§3Unlock " + label + " (Island Weather)",
                "§7Rarity: " + weather.getRarity().getColorCode() + weather.getRarity().getDisplayName(),
                "§7" + weather.getDescription(),
                "§7Cost: §e" + weather.getTokenCost() + " Slayer Tokens");
        attachWeatherPDCs(item, weather);
        return item;
    }

    private void attachWeatherPDCs(ItemStack item, IslandWeatherCosmetic weather) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_WEATHER");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_weather"), PersistentDataType.STRING, weather.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createAccessoryShopItem(String label, AccessoryCosmetic acc, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§6Unlock " + label + " (Accessory)",
                "§7Rarity: " + acc.getRarity().getColorCode() + acc.getRarity().getDisplayName(),
                "§7" + acc.getDescription(),
                "§7Cost: §e" + acc.getTokenCost() + " Slayer Tokens");
        attachAccessoryPDCs(item, acc);
        return item;
    }

    private void attachAccessoryPDCs(ItemStack item, AccessoryCosmetic acc) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_ACCESSORY");
            pdc.set(new NamespacedKey(plugin, "slayer_shop_accessory"), PersistentDataType.STRING, acc.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createWingShopItem(String label, ElytraWing wing, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§dUnlock " + label,
                "§7Rarity: " + wing.getRarity().getColorCode() + wing.getRarity().getDisplayName(),
                "§7" + wing.getDescription(),
                "",
                "§7Prestige Req: §b" + wing.getMinPrestige(),
                "§6Cost: §e" + wing.getTokenCost() + " Slayer Tokens",
                (wing.getTokenCost() == 0 ? "§aFREE Prestige Reward" : ""),
                "",
                "§aClick to purchase & unlock",
                "§8Special particles while gliding");

        attachWingPDCs(item, wing);
        return item;
    }

    private void attachWingPDCs(ItemStack item, ElytraWing wing) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_WING");
            pdc.set(WING_KEY, PersistentDataType.STRING, wing.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createRuneShopItem(String label, Rune rune, Material mat) {
        ItemStack item = GUIUtils.createItem(mat, "§dUnlock " + label,
                "§7Rarity: " + rune.getRarity().getColorCode() + rune.getRarity().getDisplayName(),
                "§7" + rune.getDescription(),
                "",
                "§7Prestige Req: §b" + rune.getMinPrestige(),
                "§6Cost: §e" + rune.getTokenCost() + " Slayer Tokens",
                "",
                "§aClick to purchase & unlock",
                "§8Apply in /runes GUI");

        attachRunePDCs(item, rune);
        return item;
    }

    private void attachRunePDCs(ItemStack item, Rune rune) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_RUNE");
            pdc.set(RUNE_KEY, PersistentDataType.STRING, rune.name());
            item.setItemMeta(meta);
        }
    }

    private ItemStack createShopItem(String name, Material mat, int tokenCost, String... lore) {
        java.util.List<String> fullLore = new java.util.ArrayList<>();
        fullLore.add("§6Cost: §e" + tokenCost + " Slayer Tokens");
        fullLore.add("");
        fullLore.addAll(java.util.Arrays.asList(lore));
        fullLore.add("");
        fullLore.add("§aClick to purchase");

        return GUIUtils.createItem(mat, name, fullLore.toArray(new String[0]));
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        return GUIUtils.createItem(mat, name, lore);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§6§lSlayer Shop")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        String name = (meta != null && meta.getDisplayName() != null) ? meta.getDisplayName() : "";

        // PDC-driven trail purchases (new)
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
            if ("CLOSE".equals(action) || name.contains("Close Shop")) {
                player.closeInventory();
                return;
            }
            if ("BUY_TRAIL".equals(action)) {
                String trailName = pdc.get(TRAIL_KEY, PersistentDataType.STRING);
                if (trailName != null && plugin.getParticleTrailManager() != null) {
                    try {
                        ParticleTrail trail = ParticleTrail.valueOf(trailName);
                        int cost = trail.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < trail.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + trail.getMinPrestige() + " to unlock this trail.");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                boolean unlocked = plugin.getParticleTrailManager().unlockTrail(player, trail);
                                if (unlocked) {
                                    player.sendMessage("§aPurchased & unlocked " + trail.getDisplayName() + " for " + cost + " tokens!");
                                    // Optionally auto-activate
                                    plugin.getParticleTrailManager().setActiveTrail(player, trail);
                                } else {
                                    player.sendMessage("§eYou already own this trail.");
                                }
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            // Free prestige reward
                            boolean unlocked = plugin.getParticleTrailManager().unlockTrail(player, trail);
                            if (unlocked) {
                                player.sendMessage("§aUnlocked " + trail.getDisplayName() + " (Prestige reward)!");
                                plugin.getParticleTrailManager().setActiveTrail(player, trail);
                            }
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cTrail unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Pet purchases (prestige + slayer token integration)
            if ("BUY_PET".equals(action)) {
                String petName = pdc.get(PET_KEY, PersistentDataType.STRING);
                if (petName != null && plugin.getPetManager() != null) {
                    try {
                        PetType pt = PetType.valueOf(petName);
                        int cost = pt.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < pt.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + pt.getMinPrestige() + " to unlock this pet.");
                            player.closeInventory();
                            return;
                        }

                        boolean success;
                        if (cost > 0) {
                            success = plugin.getPetManager().unlockPetWithTokens(player, pt, cost);
                        } else {
                            success = plugin.getPetManager().unlockPetWithTokens(player, pt, 0);
                        }
                        if (success) {
                            // Pet granted inside unlock method + message sent
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cPet unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Tag purchases (new cosmetic tag system)
            if ("BUY_TAG".equals(action)) {
                String tagName = pdc.get(TAG_KEY, PersistentDataType.STRING);
                if (tagName != null && plugin.getPlayerTagManager() != null) {
                    try {
                        PlayerTag tag = PlayerTag.valueOf(tagName);
                        int cost = tag.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < tag.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + tag.getMinPrestige() + " to unlock this tag.");
                            player.closeInventory();
                            return;
                        }

                        boolean success = plugin.getPlayerTagManager().unlockTagWithTokens(player, tag, cost);
                        if (success) {
                            // Manager already sent message + applied display
                            // Optionally auto-equip
                            Player online = Bukkit.getPlayer(player.getUniqueId());
                            if (online != null) {
                                plugin.getPlayerTagManager().setActiveTag(online, tag);
                            }
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cTag unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Pet Skin purchases
            if ("BUY_PET_SKIN".equals(action)) {
                String skinName = pdc.get(PET_SKIN_KEY, PersistentDataType.STRING);
                if (skinName != null && plugin.getPetManager() != null) {
                    try {
                        PetSkin skin = PetSkin.valueOf(skinName);
                        int cost = skin.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < skin.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + skin.getMinPrestige() + " to unlock this pet skin.");
                            player.closeInventory();
                            return;
                        }

                        // For skins we use a simple unlock (no direct token consume in manager yet, but we can add)
                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getPetManager().unlockSkin(player.getUniqueId(), skin);
                                player.sendMessage("§aPurchased Pet Skin: " + skin.getRarity().getColorCode() + skin.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getPetManager().unlockSkin(player.getUniqueId(), skin);
                            player.sendMessage("§aUnlocked Pet Skin (Prestige reward): " + skin.getRarity().getColorCode() + skin.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cPet Skin unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Helmet Skin purchases (new cosmetic system)
            if ("BUY_HELMET_SKIN".equals(action)) {
                NamespacedKey helmetSkinKey = new NamespacedKey(plugin, "slayer_shop_helmet_skin");
                String skinName = pdc.get(helmetSkinKey, PersistentDataType.STRING);
                if (skinName != null && plugin.getHelmetSkinManager() != null) {
                    try {
                        HelmetSkin skin = HelmetSkin.valueOf(skinName);
                        int cost = skin.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < skin.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + skin.getMinPrestige() + " to unlock this helmet skin.");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getHelmetSkinManager().unlockSkin(player.getUniqueId(), skin);
                                player.sendMessage("§aPurchased Helmet Skin: " + skin.getRarity().getColorCode() + skin.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getHelmetSkinManager().unlockSkin(player.getUniqueId(), skin);
                            player.sendMessage("§aUnlocked Helmet Skin (Prestige reward): " + skin.getRarity().getColorCode() + skin.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cHelmet Skin unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Death Effect purchases (new cosmetic system)
            if ("BUY_DEATH_EFFECT".equals(action)) {
                NamespacedKey deathEffectKey = new NamespacedKey(plugin, "slayer_shop_death_effect");
                String effectName = pdc.get(deathEffectKey, PersistentDataType.STRING);
                if (effectName != null && plugin.getDeathEffectManager() != null) {
                    try {
                        DeathEffect effect = DeathEffect.valueOf(effectName);
                        int cost = effect.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < effect.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + effect.getMinPrestige() + " to unlock this death effect.");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getDeathEffectManager().unlockEffect(player.getUniqueId(), effect);
                                player.sendMessage("§aPurchased Death Effect: " + effect.getRarity().getColorCode() + effect.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getDeathEffectManager().unlockEffect(player.getUniqueId(), effect);
                            player.sendMessage("§aUnlocked Death Effect (Prestige reward): " + effect.getRarity().getColorCode() + effect.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cDeath Effect unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Death Message purchases (new cosmetic system)
            if ("BUY_DEATH_MESSAGE".equals(action)) {
                NamespacedKey msgKey = new NamespacedKey(plugin, "slayer_shop_death_message");
                String msgName = pdc.get(msgKey, PersistentDataType.STRING);
                if (msgName != null && plugin.getDeathMessageManager() != null) {
                    try {
                        com.thenerdcj.cosmetic.DeathMessageCosmetic message = com.thenerdcj.cosmetic.DeathMessageCosmetic.valueOf(msgName);
                        int cost = message.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < message.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + message.getMinPrestige() + " to unlock this death message.");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getDeathMessageManager().unlockMessage(player.getUniqueId(), message);
                                player.sendMessage("§aPurchased Death Message: " + message.getRarity().getColorCode() + message.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getDeathMessageManager().unlockMessage(player.getUniqueId(), message);
                            player.sendMessage("§aUnlocked Death Message (Prestige reward): " + message.getRarity().getColorCode() + message.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cDeath Message unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Backpack Skin purchases (exploration)
            if ("BUY_BACKPACK_SKIN".equals(action)) {
                NamespacedKey backpackSkinKey = new NamespacedKey(plugin, "slayer_shop_backpack_skin");
                String skinName = pdc.get(backpackSkinKey, PersistentDataType.STRING);
                if (skinName != null && plugin.getBackpackSkinManager() != null) {
                    try {
                        BackpackSkin skin = BackpackSkin.valueOf(skinName);
                        int cost = skin.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < skin.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + skin.getMinPrestige() + " to unlock this backpack skin.");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getBackpackSkinManager().unlockSkin(player.getUniqueId(), skin);
                                player.sendMessage("§aPurchased Backpack Skin: " + skin.getRarity().getColorCode() + skin.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getBackpackSkinManager().unlockSkin(player.getUniqueId(), skin);
                            player.sendMessage("§aUnlocked Backpack Skin (Prestige reward): " + skin.getRarity().getColorCode() + skin.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cBackpack Skin unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Power Orb Skin purchases (new system)
            if ("BUY_POWER_ORB_SKIN".equals(action)) {
                NamespacedKey powerOrbKey = new NamespacedKey(plugin, "slayer_shop_power_orb_skin");
                String skinName = pdc.get(powerOrbKey, PersistentDataType.STRING);
                if (skinName != null && plugin.getPowerOrbSkinManager() != null) {
                    try {
                        PowerOrbSkin skin = PowerOrbSkin.valueOf(skinName);
                        int cost = skin.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < skin.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + skin.getMinPrestige() + " to unlock this power orb skin.");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getPowerOrbSkinManager().unlockSkin(player.getUniqueId(), skin);
                                plugin.getPowerOrbSkinManager().giveOrb(player, skin);
                                player.sendMessage("§aPurchased Power Orb Skin + Item: " + skin.getRarity().getColorCode() + skin.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getPowerOrbSkinManager().unlockSkin(player.getUniqueId(), skin);
                            plugin.getPowerOrbSkinManager().giveOrb(player, skin);
                            player.sendMessage("§aUnlocked Power Orb Skin + Item (Prestige reward): " + skin.getRarity().getColorCode() + skin.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cPower Orb Skin unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Collection reward purchases (synergy with core collections system)
            if ("BUY_COLLECTION".equals(action)) {
                NamespacedKey collKey = new NamespacedKey(plugin, "slayer_shop_collection");
                String rewardType = pdc.get(collKey, PersistentDataType.STRING);
                int cost = 100; // default for crate
                if ("COLLECTION_ACCESSORY".equals(rewardType)) cost = 80;
                if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                    if ("COLLECTION_CRATE".equals(rewardType)) {
                        // Give a random cosmetic or crate key via crate manager if available
                        player.sendMessage("§aPurchased Collection Milestone Crate for " + cost + " tokens! Check your inventory or /collections for rewards.");
                        // Example: unlock a random minor cosmetic
                        if (plugin.getAccessoryCosmeticManager() != null) {
                            try {
                                plugin.getAccessoryCosmeticManager().unlock(player.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.FLOATING_KEY);
                            } catch (Exception ignored) {}
                        }
                    } else if ("COLLECTION_ACCESSORY".equals(rewardType)) {
                        if (plugin.getAccessoryCosmeticManager() != null) {
                            plugin.getAccessoryCosmeticManager().unlock(player.getUniqueId(), com.thenerdcj.cosmetic.AccessoryCosmetic.FLOATING_KEY);
                            player.sendMessage("§aPurchased & unlocked Collector Accessory Token reward!");
                        }
                    }
                } else {
                    player.sendMessage("§cNot enough Slayer Tokens!");
                }
                player.closeInventory();
                return;
            }

            // Minion Skin purchases (new system)
            if ("BUY_MINION_SKIN".equals(action)) {
                NamespacedKey minionKey = new NamespacedKey(plugin, "slayer_shop_minion_skin");
                String skinName = pdc.get(minionKey, PersistentDataType.STRING);
                if (skinName != null && plugin.getMinionSkinManager() != null) {
                    try {
                        MinionSkin skin = MinionSkin.valueOf(skinName);
                        int cost = skin.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < skin.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + skin.getMinPrestige() + " to unlock this minion skin.");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getMinionSkinManager().unlockSkin(player.getUniqueId(), skin);
                                player.sendMessage("§aPurchased Minion Skin: " + skin.getRarity().getColorCode() + skin.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getMinionSkinManager().unlockSkin(player.getUniqueId(), skin);
                            player.sendMessage("§aUnlocked Minion Skin (Prestige reward): " + skin.getRarity().getColorCode() + skin.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cMinion Skin unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Island Furniture purchases (foundation)
            if ("BUY_FURNITURE".equals(action)) {
                NamespacedKey furnKey = new NamespacedKey(plugin, "slayer_shop_furniture");
                String furnName = pdc.get(furnKey, PersistentDataType.STRING);
                if (furnName != null && plugin.getIslandFurnitureManager() != null) {
                    try {
                        IslandFurnitureType furn = IslandFurnitureType.valueOf(furnName);
                        int cost = furn.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < furn.getMinPrestige()) {
                            player.sendMessage("§cRequires Prestige " + furn.getMinPrestige() + "+");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getIslandFurnitureManager().unlockFurniture(player.getUniqueId(), furn);
                                player.sendMessage("§aPurchased Furniture: " + furn.getRarity().getColorCode() + furn.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getIslandFurnitureManager().unlockFurniture(player.getUniqueId(), furn);
                            player.sendMessage("§aUnlocked Furniture (Prestige): " + furn.getRarity().getColorCode() + furn.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cFurniture unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Island Music purchases (new)
            if ("BUY_MUSIC".equals(action)) {
                NamespacedKey musicKey = new NamespacedKey(plugin, "slayer_shop_music");
                String musicName = pdc.get(musicKey, PersistentDataType.STRING);
                if (musicName != null && plugin.getIslandMusicManager() != null) {
                    try {
                        IslandMusicType music = IslandMusicType.valueOf(musicName);
                        int cost = music.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < music.getMinPrestige()) {
                            player.sendMessage("§cRequires Prestige " + music.getMinPrestige() + "+");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getIslandMusicManager().unlockMusic(player.getUniqueId(), music);
                                player.sendMessage("§aPurchased Island Music: " + music.getRarity().getColorCode() + music.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getIslandMusicManager().unlockMusic(player.getUniqueId(), music);
                            player.sendMessage("§aUnlocked Island Music (Prestige): " + music.getRarity().getColorCode() + music.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cMusic unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Overhead Cosmetic purchases (new system)
            if ("BUY_OVERHEAD".equals(action)) {
                NamespacedKey overheadKey = new NamespacedKey(plugin, "slayer_shop_overhead");
                String cosmeticName = pdc.get(overheadKey, PersistentDataType.STRING);
                if (cosmeticName != null && plugin.getOverheadCosmeticManager() != null) {
                    try {
                        OverheadCosmetic cosmetic = OverheadCosmetic.valueOf(cosmeticName);
                        int cost = cosmetic.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < cosmetic.getMinPrestige()) {
                            player.sendMessage("§cRequires Prestige " + cosmetic.getMinPrestige() + "+");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getOverheadCosmeticManager().unlock(player.getUniqueId(), cosmetic);
                                player.sendMessage("§aPurchased Overhead Cosmetic: " + cosmetic.getRarity().getColorCode() + cosmetic.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getOverheadCosmeticManager().unlock(player.getUniqueId(), cosmetic);
                            player.sendMessage("§aUnlocked Overhead Cosmetic (Prestige): " + cosmetic.getRarity().getColorCode() + cosmetic.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cOverhead Cosmetic unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Emote Cosmetic purchases (new)
            if ("BUY_EMOTE".equals(action)) {
                NamespacedKey emoteKey = new NamespacedKey(plugin, "slayer_shop_emote");
                String emoteName = pdc.get(emoteKey, PersistentDataType.STRING);
                if (emoteName != null && plugin.getEmoteCosmeticManager() != null) {
                    try {
                        EmoteCosmetic emote = EmoteCosmetic.valueOf(emoteName);
                        int cost = emote.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < emote.getMinPrestige()) {
                            player.sendMessage("§cRequires Prestige " + emote.getMinPrestige() + "+");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getEmoteCosmeticManager().unlock(player.getUniqueId(), emote);
                                player.sendMessage("§aPurchased Emote: " + emote.getRarity().getColorCode() + emote.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getEmoteCosmeticManager().unlock(player.getUniqueId(), emote);
                            player.sendMessage("§aUnlocked Emote (Prestige): " + emote.getRarity().getColorCode() + emote.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cEmote unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Island Structure purchases (new)
            if ("BUY_STRUCTURE".equals(action)) {
                NamespacedKey structureKey = new NamespacedKey(plugin, "slayer_shop_structure");
                String structureName = pdc.get(structureKey, PersistentDataType.STRING);
                if (structureName != null && plugin.getIslandStructureManager() != null) {
                    try {
                        IslandStructureCosmetic structure = IslandStructureCosmetic.valueOf(structureName);
                        int cost = structure.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < structure.getMinPrestige()) {
                            player.sendMessage("§cRequires Prestige " + structure.getMinPrestige() + "+");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getIslandStructureManager().unlockStructure(player.getUniqueId(), structure);
                                player.sendMessage("§aPurchased Island Structure: " + structure.getRarity().getColorCode() + structure.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getIslandStructureManager().unlockStructure(player.getUniqueId(), structure);
                            player.sendMessage("§aUnlocked Island Structure (Prestige): " + structure.getRarity().getColorCode() + structure.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cStructure unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Chat Bubble purchases (new)
            if ("BUY_CHAT_BUBBLE".equals(action)) {
                NamespacedKey bubbleKey = new NamespacedKey(plugin, "slayer_shop_chatbubble");
                String bubbleName = pdc.get(bubbleKey, PersistentDataType.STRING);
                if (bubbleName != null && plugin.getChatBubbleCosmeticManager() != null) {
                    try {
                        ChatBubbleCosmetic bubble = ChatBubbleCosmetic.valueOf(bubbleName);
                        int cost = bubble.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < bubble.getMinPrestige()) {
                            player.sendMessage("§cRequires Prestige " + bubble.getMinPrestige() + "+");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getChatBubbleCosmeticManager().unlock(player.getUniqueId(), bubble);
                                player.sendMessage("§aPurchased Chat Bubble: " + bubble.getRarity().getColorCode() + bubble.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getChatBubbleCosmeticManager().unlock(player.getUniqueId(), bubble);
                            player.sendMessage("§aUnlocked Chat Bubble (Prestige): " + bubble.getRarity().getColorCode() + bubble.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cChat bubble unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Island Weather purchases (new)
            if ("BUY_WEATHER".equals(action)) {
                NamespacedKey weatherKey = new NamespacedKey(plugin, "slayer_shop_weather");
                String weatherName = pdc.get(weatherKey, PersistentDataType.STRING);
                if (weatherName != null && plugin.getIslandWeatherCosmeticManager() != null) {
                    try {
                        IslandWeatherCosmetic weather = IslandWeatherCosmetic.valueOf(weatherName);
                        int cost = weather.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < weather.getMinPrestige()) {
                            player.sendMessage("§cRequires Prestige " + weather.getMinPrestige() + "+");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getIslandWeatherCosmeticManager().unlockWeather(player.getUniqueId(), weather);
                                player.sendMessage("§aPurchased Island Weather: " + weather.getRarity().getColorCode() + weather.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getIslandWeatherCosmeticManager().unlockWeather(player.getUniqueId(), weather);
                            player.sendMessage("§aUnlocked Island Weather (Prestige): " + weather.getRarity().getColorCode() + weather.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cWeather unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Accessory purchases (new)
            if ("BUY_ACCESSORY".equals(action)) {
                NamespacedKey accKey = new NamespacedKey(plugin, "slayer_shop_accessory");
                String accName = pdc.get(accKey, PersistentDataType.STRING);
                if (accName != null && plugin.getAccessoryCosmeticManager() != null) {
                    try {
                        AccessoryCosmetic acc = AccessoryCosmetic.valueOf(accName);
                        int cost = acc.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < acc.getMinPrestige()) {
                            player.sendMessage("§cRequires Prestige " + acc.getMinPrestige() + "+");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getAccessoryCosmeticManager().unlock(player.getUniqueId(), acc);
                                player.sendMessage("§aPurchased Accessory: " + acc.getRarity().getColorCode() + acc.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getAccessoryCosmeticManager().unlock(player.getUniqueId(), acc);
                            player.sendMessage("§aUnlocked Accessory (Prestige): " + acc.getRarity().getColorCode() + acc.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cAccessory unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Elytra Wing purchases
            if ("BUY_WING".equals(action)) {
                String wingName = pdc.get(WING_KEY, PersistentDataType.STRING);
                if (wingName != null && plugin.getElytraWingManager() != null) {
                    try {
                        ElytraWing wing = ElytraWing.valueOf(wingName);
                        int cost = wing.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < wing.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + wing.getMinPrestige() + " to unlock this wing style.");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getElytraWingManager().unlockWing(player.getUniqueId(), wing);
                                player.sendMessage("§aPurchased Elytra Wing: " + wing.getDisplayName() + " §7for " + cost + " tokens!");
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getElytraWingManager().unlockWing(player.getUniqueId(), wing);
                            player.sendMessage("§aUnlocked Elytra Wing (Prestige reward): " + wing.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cWing unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }

            // Rune purchases
            if ("BUY_RUNE".equals(action)) {
                String runeName = pdc.get(RUNE_KEY, PersistentDataType.STRING);
                if (runeName != null && plugin.getRuneManager() != null) {
                    try {
                        Rune rune = Rune.valueOf(runeName);
                        int cost = rune.getTokenCost();
                        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                        int prestige = (island != null && plugin.getPrestigeManager() != null)
                                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                        if (prestige < rune.getMinPrestige()) {
                            player.sendMessage("§cYou need Prestige " + rune.getMinPrestige() + " to unlock this rune.");
                            player.closeInventory();
                            return;
                        }

                        if (cost > 0) {
                            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                                plugin.getRuneManager().unlockRune(player.getUniqueId(), rune);
                                player.sendMessage("§aPurchased Rune: " + rune.getDisplayName());
                            } else {
                                player.sendMessage("§cNot enough Slayer Tokens!");
                            }
                        } else {
                            plugin.getRuneManager().unlockRune(player.getUniqueId(), rune);
                            player.sendMessage("§aUnlocked Rune (Prestige reward): " + rune.getDisplayName());
                        }
                    } catch (Exception ex) {
                        player.sendMessage("§cRune unlock error.");
                    }
                }
                player.closeInventory();
                return;
            }
        }

        // Legacy name-based purchases (gear, keys)
        if (name.contains("Revenant Scythe")) {
            int cost = 150;
            if (plugin.getBossManager() != null) {
                if (plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                    ItemStack gear = plugin.getBossManager().createSlayerGear("scythe");
                    player.getInventory().addItem(gear);
                    player.sendMessage("§aPurchased Revenant Scythe for " + cost + " Slayer Tokens!");
                } else {
                    player.sendMessage("§cYou don't have enough Slayer Tokens!");
                }
            }
        } else if (name.contains("Crate Key")) {
            int cost = 75;
            if (plugin.getBossManager() != null) {
                if (plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                    if (plugin.getCrateManager() != null) {
                        ItemStack key = plugin.getCrateManager().createKeyItem(com.thenerdcj.crate.CrateType.EPIC);
                        player.getInventory().addItem(key);
                        player.sendMessage("§aPurchased Epic Slayer Crate Key for " + cost + " Slayer Tokens!");
                    }
                } else {
                    player.sendMessage("§cYou don't have enough Slayer Tokens!");
                }
            }
        } else {
            player.sendMessage("§eItem not yet available for purchase or already handled.");
        }

        player.closeInventory();
    }
}