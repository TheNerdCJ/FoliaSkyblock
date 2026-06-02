package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.runes.Rune;
import com.thenerdcj.runes.RuneManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public class RuneEffectListener implements Listener {

    private final FoliaSkyblock plugin;

    public RuneEffectListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    // Note: All particle spawning is delegated to RuneManager which uses ThreadSafety
    // (Global/Region/Entity schedulers) for full Folia compatibility.
    // Direct spawns are avoided; location effects use runAtLocation for region safety.

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        RuneManager manager = plugin.getRuneManager();
        if (manager == null) return;

        Rune rune = manager.getRuneFromItem(weapon);
        if (rune != null && !rune.isNone()) {
            int tier = manager.getRuneTierFromItem(weapon);
            manager.triggerHitEffect(player, event.getEntity(), rune, tier);
        }

        // Armor rune support: if the damaged entity has runed armor (victim-side cosmetic)
        if (event.getEntity() instanceof Player damaged) {
            for (ItemStack armor : damaged.getInventory().getArmorContents()) {
                if (armor == null) continue;
                Rune armorRune = manager.getRuneFromItem(armor);
                if (armorRune != null && !armorRune.isNone()) {
                    int tier = manager.getRuneTierFromItem(armor);
                    org.bukkit.Location loc = damaged.getLocation().add(0, 1, 0);
                    manager.triggerLocationEffect(loc, armorRune, tier);
                    break;
                }
            }
        }
    }

    /**
     * Right-clicking an Enchanting Table opens the Rune Table GUI.
     * This acts as the physical "Rune Table" block.
     */
    @EventHandler
    public void onRuneTableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) return;

        Player player = event.getPlayer();
        if (plugin.getRuneGUI() != null) {
            event.setCancelled(true);
            plugin.getRuneGUI().openRuneTable(player);
        }
    }

    /**
     * Support for bow/arrow runes.
     */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        // Check main hand or offhand for bow with rune
        ItemStack bow = player.getInventory().getItemInMainHand();
        if (bow.getType() != Material.BOW && bow.getType() != Material.CROSSBOW) {
            bow = player.getInventory().getItemInOffHand();
        }

        RuneManager manager = plugin.getRuneManager();
        if (manager == null) return;

        Rune rune = manager.getRuneFromItem(bow);
        if (rune != null && !rune.isNone()) {
            int tier = manager.getRuneTierFromItem(bow);
            if (event.getHitEntity() != null) {
                manager.triggerHitEffect(player, event.getHitEntity(), rune, tier);
            } else if (event.getHitBlock() != null) {
                org.bukkit.Location loc = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
                manager.triggerLocationEffect(loc, rune, tier);
            }
        }
    }
}
