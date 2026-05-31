package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.boss.BossManager;
import com.thenerdcj.cosmetic.ParticleTrail;
import com.thenerdcj.island.Island;
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

import java.util.Arrays;

/**
 * Dedicated Slayer Shop / Vendor.
 * Players spend Slayer Tokens (earned from Slayer quests and Island Boss Events)
 * on exclusive gear, crates, boosters, and cosmetics.
 */
public class SlayerShopGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final BossManager bossManager;
    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey TRAIL_KEY;

    public SlayerShopGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.bossManager = plugin.getBossManager();
        this.ACTION_KEY = new NamespacedKey(plugin, "slayer_shop_action");
        this.TRAIL_KEY = new NamespacedKey(plugin, "slayer_shop_trail");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, Island island) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6§lSlayer Shop - Spend Tokens");

        // Header
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

        // Prestige-gated or token affordable trails (real consume + unlock)
        gui.setItem(37, createTrailShopItem("Flame Trail", ParticleTrail.FLAME_TRAIL, Material.BLAZE_POWDER));
        gui.setItem(38, createTrailShopItem("Heart Aura", ParticleTrail.HEART_AURA, Material.REDSTONE));
        gui.setItem(39, createTrailShopItem("Soul Flame Trail", ParticleTrail.SOUL_TRAIL, Material.SOUL_SAND));
        gui.setItem(40, createTrailShopItem("Rainbow Dust", ParticleTrail.RAINBOW_DUST, Material.GLOWSTONE_DUST));
        gui.setItem(41, createTrailShopItem("Dragon Breath Aura", ParticleTrail.DRAGON_BREATH, Material.DRAGON_BREATH));
        gui.setItem(42, createTrailShopItem("Electric Spark", ParticleTrail.ELECTRIC_TRAIL, Material.LIGHTNING_ROD));

        // Token balance
        int tokens = (plugin.getBossManager() != null) ? plugin.getBossManager().getTotalSlayerTokens(player) : 0;
        gui.setItem(49, createItem(Material.GOLD_NUGGET, "§eYour Slayer Tokens",
                "§7Current: §6" + tokens,
                "§7Tokens are consumed from your inventory"));

        player.openInventory(gui);
    }

    private ItemStack createTrailShopItem(String label, ParticleTrail trail, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§dUnlock " + label);
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Personal cosmetic trail / aura");
            lore.add("");
            lore.add("§7Prestige Req: §b" + trail.getMinPrestige());
            lore.add("§6Cost: §e" + trail.getTokenCost() + " Slayer Tokens");
            if (trail.getTokenCost() == 0) {
                lore.add("§aFREE Prestige Reward");
            }
            lore.add("");
            lore.add("§aClick to purchase & unlock");
            lore.add("§8Opens in your trail collection");
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "BUY_TRAIL");
            pdc.set(TRAIL_KEY, PersistentDataType.STRING, trail.name());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShopItem(String name, Material mat, int tokenCost, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            java.util.List<String> fullLore = new java.util.ArrayList<>();
            fullLore.add("§6Cost: §e" + tokenCost + " Slayer Tokens");
            fullLore.add("");
            fullLore.addAll(Arrays.asList(lore));
            fullLore.add("");
            fullLore.add("§aClick to purchase");
            meta.setLore(fullLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
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