package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.PowerOrbSkin;
import com.thenerdcj.cosmetic.PowerOrbSkinManager;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listens for cosmetic Power Orb usage (right-click).
 * Triggers the visual effect from the player's active (or item-stored) skin.
 * Purely cosmetic — no gameplay power.
 */
public class PowerOrbUseListener implements Listener {

    private final FoliaSkyblock plugin;
    private final PowerOrbSkinManager orbManager;

    public PowerOrbUseListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.orbManager = plugin.getPowerOrbSkinManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onOrbUse(PlayerInteractEvent event) {
        if (orbManager == null) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Only right-click actions for orbs
        if (!event.getAction().name().contains("RIGHT")) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // Check for our orb marker (set by manager.createOrbItem)
        if (!pdc.has(new org.bukkit.NamespacedKey("foliaskyblock", "power_orb_skin"), PersistentDataType.STRING)) {
            return;
        }

        // Get the skin stored on the item (or fall back to player's active)
        PowerOrbSkin skin = orbManager.getSkinFromOrbItem(item);
        if (skin == null || skin.isNone()) {
            skin = orbManager.getActiveSkin(player.getUniqueId());
        }

        // Trigger the beautiful Folia-safe visual
        orbManager.triggerOrbEffect(player, skin);

        // Cosmetic feedback
        MessageUtil.sendActionBar(player, "§b✧ Power Orb released: " + (skin.isNone() ? "§7Default" : skin.getRarity().getColorCode() + skin.getDisplayName()));

        // Optional: consume one use (makes them feel like a limited cosmetic tool)
        // For now we just give a short cooldown feel via message if spammed
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            // Remove the orb after use (single-use cosmetic burst feel)
            player.getInventory().setItemInMainHand(null);
        }

        event.setCancelled(true);
    }
}
