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
        Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.legacy("§6§lSlayer Shop - Spend Tokens"));

        // Header - modernized
        gui.setItem(4, createItem(Material.NETHER_STAR, "§6§lSlayer Shop",
                "§7Earn Slayer Tokens from quests & island bosses",
                "§7Spend them here for exclusive rewards!"));

        // === SLAYER GEAR & KEYS (existing) ===
        gui.setItem(19, createShopItem("§cRevenant Scythe", Material.DIAMOND_SWORD, 150,
                "§7+25% damage to Zombies", "§7Unlocks special zombie drops"));

        gui.setItem(21, createShopItem("§5Tarantula Helmet", Material.DIAMOND_HELMET, 200,
                "§7+15% spider damage resistance", "§7Chance to avoid poison"));

        gui.setItem(23, createShopItem("§6Voidgloom Cloak", Material.ELYTRA, 350,
                "§7Enderman teleport immunity", "§7+Ender pearl drop rate"));

        gui.setItem(25, createShopItem("§eEpic Slayer Crate Key", Material.TRIPWIRE_HOOK, 75,
                "§7Contains high-tier slayer loot"));

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