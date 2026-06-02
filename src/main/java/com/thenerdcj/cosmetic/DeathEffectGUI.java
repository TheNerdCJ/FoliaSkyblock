package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/**
 * Dedicated GUI for Death / Kill Effects.
 * Follows the exact same patterns as HelmetSkinGUI and PetGUI skin selection.
 */
public class DeathEffectGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public DeathEffectGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "death_effect_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        DeathEffectManager manager = plugin.getDeathEffectManager();
        if (manager == null) {
            player.sendMessage("§cDeath Effects system is not available.");
            return;
        }

        String title = "§4§lDeath Effects";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<DeathEffect> owned = manager.getOwnedEffects(player.getUniqueId());
        DeathEffect current = manager.getActiveDeathEffect(player.getUniqueId());

        // Header
        inv.setItem(4, GUIUtils.createItem(Material.SKELETON_SKULL, "§4§lCosmetic Death Effects",
                "§7Effects on your death and when you kill",
                "§7Collection: §a" + owned.size() + " / " + (DeathEffect.values().length - 1) + " §7effects"));

        // NONE option
        ItemStack noneItem = GUIUtils.createItem(Material.BARRIER, "§cRemove Effect",
                "§7No cosmetic on death/kill");
        PersistentDataContainer nonePdc = noneItem.getItemMeta().getPersistentDataContainer();
        nonePdc.set(ACTION_KEY, PersistentDataType.STRING, "DEATH_EFFECT_NONE");
        noneItem.setItemMeta(noneItem.getItemMeta());
        inv.setItem(18, noneItem);

        int slot = 19;
        for (DeathEffect effect : DeathEffect.values()) {
            if (slot > 44) break;
            if (effect.isNone()) continue;

            boolean isOwned = owned.contains(effect);
            boolean isCurrent = effect == current;

            ItemStack item = GUIUtils.createItem(Material.SKELETON_SKULL,
                    (isCurrent ? "§a§l★ " : isOwned ? "§e" : "§7") + effect.getRarity().getColorCode() + effect.getDisplayName(),
                    "§7" + effect.getDescription(),
                    "§7Rarity: " + effect.getRarity().getColorCode() + effect.getRarity().getDisplayName(),
                    isOwned ? "" : "§cLocked - Unlock via prestige or Slayer Shop",
                    isCurrent ? "§aCurrently Active" : isOwned ? "§eClick to equip" : "§7Preview only");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "DEATH_EFFECT_" + effect.name());
            item.setItemMeta(item.getItemMeta());

            inv.setItem(slot++, item);
        }

        // Back to Wardrobe
        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        PersistentDataContainer backPdc = back.getItemMeta().getPersistentDataContainer();
        backPdc.set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
        back.setItemMeta(back.getItemMeta());
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals("§4§lDeath Effects")) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        DeathEffectManager manager = plugin.getDeathEffectManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        if (action.equals("DEATH_EFFECT_NONE")) {
            manager.setActiveDeathEffect(player, DeathEffect.NONE);
            open(player);
            return;
        }

        if (action.startsWith("DEATH_EFFECT_")) {
            String effectName = action.substring(13);
            try {
                DeathEffect effect = DeathEffect.valueOf(effectName);
                if (!manager.hasEffect(player.getUniqueId(), effect) && !effect.isNone()) {
                    player.sendMessage("§cYou have not unlocked this death effect.");
                    return;
                }
                manager.setActiveDeathEffect(player, effect);
                open(player);
            } catch (Exception ignored) {
                player.sendMessage("§cInvalid death effect.");
            }
        }
    }
}