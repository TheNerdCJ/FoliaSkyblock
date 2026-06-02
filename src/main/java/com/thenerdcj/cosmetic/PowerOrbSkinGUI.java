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
 * Dedicated GUI for Power Orb Skins.
 * Follows the exact same patterns as BackpackSkinGUI, DeathEffectGUI, etc.
 */
public class PowerOrbSkinGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public PowerOrbSkinGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "power_orb_skin_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        PowerOrbSkinManager manager = plugin.getPowerOrbSkinManager();
        if (manager == null) {
            player.sendMessage("§cPower Orb Skins system is not available.");
            return;
        }

        String title = "§b§lPower Orb Skins";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<PowerOrbSkin> owned = manager.getOwnedSkins(player.getUniqueId());
        PowerOrbSkin current = manager.getActiveSkin(player.getUniqueId());

        // Header
        inv.setItem(4, GUIUtils.createItem(Material.BEACON, "§b§lCosmetic Power Orb Skins",
                "§7Cosmetic overrides for Power Orbs",
                "§7Collection: §a" + owned.size() + " / " + (PowerOrbSkin.values().length - 1) + " §7skins"));

        // NONE option
        ItemStack noneItem = GUIUtils.createItem(Material.BARRIER, "§cRemove Skin",
                "§7Return to default Power Orb appearance");
        PersistentDataContainer nonePdc = noneItem.getItemMeta().getPersistentDataContainer();
        nonePdc.set(ACTION_KEY, PersistentDataType.STRING, "POWER_ORB_SKIN_NONE");
        noneItem.setItemMeta(noneItem.getItemMeta());
        inv.setItem(18, noneItem);

        int slot = 19;
        for (PowerOrbSkin skin : PowerOrbSkin.values()) {
            if (slot > 44) break;
            if (skin.isNone()) continue;

            boolean isOwned = owned.contains(skin);
            boolean isCurrent = skin == current;

            ItemStack item = GUIUtils.createItem(Material.BEACON,
                    (isCurrent ? "§a§l★ " : isOwned ? "§e" : "§7") + skin.getRarity().getColorCode() + skin.getDisplayName(),
                    "§7" + skin.getDescription(),
                    "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                    isOwned ? "" : "§cLocked - Unlock via prestige or Slayer Shop",
                    isCurrent ? "§aCurrently Active" : isOwned ? "§eClick to apply" : "§7Preview only");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "POWER_ORB_SKIN_" + skin.name());
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
        if (!title.equals("§b§lPower Orb Skins")) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        PowerOrbSkinManager manager = plugin.getPowerOrbSkinManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        if (action.equals("POWER_ORB_SKIN_NONE")) {
            manager.setActiveSkin(player, PowerOrbSkin.NONE);
            open(player);
            return;
        }

        if (action.startsWith("POWER_ORB_SKIN_")) {
            String skinName = action.substring(15);
            try {
                PowerOrbSkin skin = PowerOrbSkin.valueOf(skinName);
                if (!manager.hasSkin(player.getUniqueId(), skin) && !skin.isNone()) {
                    player.sendMessage("§cYou have not unlocked this power orb skin.");
                    return;
                }
                manager.setActiveSkin(player, skin);
                open(player);
            } catch (Exception ignored) {
                player.sendMessage("§cInvalid power orb skin.");
            }
        }
    }
}