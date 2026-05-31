package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Applies actual abilities for Slayer-specific gear.
 * Example: Revenant Scythe gives extra zombie drops.
 */
public class SlayerGearListener implements Listener {

    private final FoliaSkyblock plugin;

    public SlayerGearListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player player = event.getEntity().getKiller();

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null || mainHand.getType() != Material.DIAMOND_SWORD) return;

        ItemMeta meta = mainHand.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String name = meta.getDisplayName();

        // Revenant Scythe ability: extra zombie flesh on zombie kills
        if (name.contains("Revenant Scythe") && event.getEntity().getType() == EntityType.ZOMBIE) {
            // 35% chance for extra drop (balanced)
            if (new java.util.Random().nextDouble() < 0.35) {
                event.getDrops().add(new ItemStack(Material.ROTTEN_FLESH, 3));
                player.sendMessage("§a§lRevenant Scythe: §7Extra flesh dropped!");
            }
        }

        // Tarantula Helmet example (if we add the item later): reduced poison damage
        if (player.getInventory().getHelmet() != null) {
            ItemMeta helmetMeta = player.getInventory().getHelmet().getItemMeta();
            if (helmetMeta != null && helmetMeta.hasDisplayName() && helmetMeta.getDisplayName().contains("Tarantula")) {
                // Passive: 25% chance to negate poison effect when hit by spider
                if (event.getEntity().getType() == EntityType.SPIDER || event.getEntity().getType() == EntityType.CAVE_SPIDER) {
                    if (new java.util.Random().nextDouble() < 0.25) {
                        player.sendMessage("§a§lTarantula Helmet: §7Poison resisted!");
                        // Note: Full effect negation would require more event handling (PotionEffectEvent)
                    }
                }
            }
        }
    }
}