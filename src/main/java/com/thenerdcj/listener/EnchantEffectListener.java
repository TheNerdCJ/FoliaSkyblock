package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.enchant.CustomEnchantment;
import com.thenerdcj.enchant.EnchantmentManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Random;

/**
 * EnchantEffectListener - Implements runtime effects for custom (non-vanilla) Skyblock enchants.
 *
 * Upgraded system: effects are Folia-safe via ThreadSafety.
 * Custom enchants now have real power (damage, utility) in addition to storage/GUI.
 * Integrates with existing Combat/Block listeners patterns.
 * Effects are balanced, Play-to-Win gated via high-level books.
 */
public class EnchantEffectListener implements Listener {

    private final FoliaSkyblock plugin;
    private final EnchantmentManager enchantManager;
    private final Random random = new Random();

    public EnchantEffectListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.enchantManager = plugin.getEnchantmentManager();
    }

    /**
     * Combat enchants on player attack.
     * Modifies damage for Execute, First Strike, Giant Killer etc.
     * Side effects: Life Steal, Venomous, Thunderbolt.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;

        Map<CustomEnchantment, Integer> enchants = enchantManager.getAllEnchantments(weapon);
        if (enchants.isEmpty()) return;

        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();

        double damage = event.getDamage();
        boolean appliedBonus = false;

        for (Map.Entry<CustomEnchantment, Integer> entry : enchants.entrySet()) {
            CustomEnchantment enchant = entry.getKey();
            int level = entry.getValue();
            if (level <= 0 || enchant.isVanilla()) continue;

            switch (enchant) {
                case EXECUTE -> {
                    // Bonus damage vs low HP targets (up to 50% at lvl 5)
                    double healthPercent = target.getHealth() / target.getMaxHealth();
                    if (healthPercent < 0.5) {
                        double bonus = level * 0.1 * (1 - healthPercent); // scales with how low
                        damage += damage * bonus;
                        appliedBonus = true;
                    }
                }
                case FIRST_STRIKE -> {
                    // Simple first hit bonus (use metadata or assume on first in combat)
                    // For simplicity, 20% bonus on any hit (or enhance with tag)
                    if (random.nextDouble() < 0.3 + level * 0.05) {
                        damage += damage * (0.15 * level);
                        appliedBonus = true;
                    }
                }
                case GIANT_KILLER -> {
                    // Bonus vs "tough" targets (use max health as proxy for level)
                    if (target.getMaxHealth() > 30) {
                        double bonus = Math.min(0.5, (target.getMaxHealth() - 20) / 100.0) * level * 0.2;
                        damage += damage * bonus;
                        appliedBonus = true;
                    }
                }
                case LIFE_STEAL -> {
                    double heal = Math.min(4.0, damage * (0.08 * level));
                    double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + heal);
                    player.setHealth(newHealth);
                    // Folia safe particle
                    plugin.getThreadSafety().runAtLocation(player.getLocation(), () -> {
                        if (player.getWorld() != null) {
                            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 3, 0.3, 0.2, 0.3, 0.01);
                        }
                    });
                }
                case VENOMOUS -> {
                    if (random.nextDouble() < 0.4 + level * 0.08) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60 + level * 20, Math.min(2, level - 1)));
                    }
                }
                case THUNDERBOLT -> {
                    if (random.nextDouble() < 0.15 + level * 0.05) {
                        Location loc = target.getLocation();
                        plugin.getThreadSafety().runAtLocation(loc, () -> {
                            if (loc.getWorld() != null) {
                                loc.getWorld().strikeLightningEffect(loc);
                                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.add(0, 1, 0), 8, 0.4, 0.5, 0.4, 0.02);
                            }
                        });
                        // Small extra damage
                        damage += 1.5 * level;
                    }
                }
                case DRAGON_HUNTER -> {
                    String tname = target.getType().name();
                    if (tname.contains("DRAGON") || tname.contains("ENDER") || target.getMaxHealth() > 80) {
                        damage += damage * (0.15 * level);
                        appliedBonus = true;
                    }
                }
                case OVERLOAD -> {
                    if (random.nextDouble() < 0.18 * level) {
                        damage += damage * (0.4 + 0.1 * level);
                        appliedBonus = true;
                        plugin.getThreadSafety().runAtLocation(target.getLocation(), () -> {
                            if (target.getWorld() != null) {
                                target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 2, 0.5, 0.5, 0.5, 0.01);
                            }
                        });
                    }
                }
                case CUBISM -> {
                    String tname = target.getType().name();
                    if (tname.contains("SLIME") || tname.contains("MAGMA") || tname.contains("CUBE")) {
                        damage += damage * (0.2 * level);
                        appliedBonus = true;
                    }
                }
                default -> {}
            }
        }

        if (appliedBonus) {
            event.setDamage(damage);
            // Subtle particle for enchant proc
            plugin.getThreadSafety().runAtLocation(target.getLocation(), () -> {
                if (target.getWorld() != null) {
                    target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.01);
                }
            });
        }

        // Anti-cheat: report custom enchant procs for power farming detection (self-rate-limited inside)
        if (plugin.getAntiCheatManager() != null && !enchants.isEmpty()) {
            for (var entry : enchants.entrySet()) {
                if (!entry.getKey().isVanilla() && entry.getValue() > 0) {
                    plugin.getAntiCheatManager().reportEnchantProc(player, entry.getKey().name());
                    break;
                }
            }
        }

        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[EnchantEffectListener] PROFILE: onEntityDamage enchant procs took " + (ns / 1_000_000.0) + " ms");
        }
    }

    /**
     * Tool enchants on block break.
     * Replenish, Harvesting, Experience, etc.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) return;

        Map<CustomEnchantment, Integer> enchants = enchantManager.getAllEnchantments(tool);
        if (enchants.isEmpty()) return;

        long start = 0;
        if (plugin.getIslandWorthManager() != null && plugin.getIslandWorthManager().isProfileHotPaths()) start = System.nanoTime();

        for (Map.Entry<CustomEnchantment, Integer> entry : enchants.entrySet()) {
            CustomEnchantment enchant = entry.getKey();
            int level = entry.getValue();
            if (level <= 0 || enchant.isVanilla()) continue;

            switch (enchant) {
                case REPLENISH -> {
                    // Auto replant crops if possible
                    Material type = block.getType();
                    if (isCrop(type) && block.getBlockData() instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
                        // Replant after break (delay slightly for Folia)
                        Material seed = getSeedForCrop(type);
                        if (seed != null) {
                            Location loc = block.getLocation();
                            plugin.getThreadSafety().runAtLocation(loc, () -> {
                                if (loc.getBlock().getType() == Material.AIR) {
                                    loc.getBlock().setType(type);
                                    if (loc.getBlock().getBlockData() instanceof Ageable newAge) {
                                        newAge.setAge(0);
                                        loc.getBlock().setBlockData(newAge);
                                    }
                                    player.playSound(loc, Sound.ITEM_CROP_PLANT, 0.6f, 1.0f);
                                }
                            });
                        }
                    }
                }
                case HARVESTING -> {
                    // Extra crop drops (similar to fortune but custom)
                    if (isCrop(block.getType())) {
                        // Drop extra 1- level items (vanilla drops already handled)
                        // For demo, spawn extra seeds/drops
                        Material dropMat = getHarvestDrop(block.getType());
                        if (dropMat != null) {
                            int extra = random.nextInt(level) + 1;
                            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                            for (int i = 0; i < extra; i++) {
                                block.getWorld().dropItemNaturally(loc, new ItemStack(dropMat));
                            }
                            plugin.getThreadSafety().runAtLocation(loc, () -> {
                                if (loc.getWorld() != null) {
                                    loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 4, 0.3, 0.3, 0.3, 0.01);
                                }
                            });
                        }
                    }
                }
                case EXPERIENCE -> {
                    // Extra XP orbs on break (for mobs it's in damage, here for blocks)
                    if (random.nextDouble() < 0.3 * level) {
                        int xp = 1 + random.nextInt(level);
                        // Spawn orb via world (Folia safe enough on region)
                        block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), org.bukkit.entity.ExperienceOrb.class, orb -> {
                            orb.setExperience(xp);
                        });
                    }
                }
                default -> {}
            }
        }

        if (start != 0) {
            long ns = System.nanoTime() - start;
            if (ns > 500_000L) plugin.getLogger().info("[EnchantEffectListener] PROFILE: onBlockBreak enchant procs took " + (ns / 1_000_000.0) + " ms");
        }
    }

    private boolean isCrop(Material m) {
        return m == Material.WHEAT || m == Material.CARROTS || m == Material.POTATOES || m == Material.BEETROOTS || m == Material.NETHER_WART;
    }

    private Material getSeedForCrop(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }

    private Material getHarvestDrop(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }
}
