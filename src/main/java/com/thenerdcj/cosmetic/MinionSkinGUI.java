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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/**
 * Dedicated GUI for Minion Skins.
 * Full parity with PowerOrbSkinGUI / DeathEffectGUI / BackpackSkinGUI.
 * Uses correct ItemMeta handling (no getItemMeta() anti-pattern for PDC).
 */
public class MinionSkinGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public MinionSkinGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "minion_skin_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        MinionSkinManager manager = plugin.getMinionSkinManager();
        if (manager == null) {
            player.sendMessage("§cMinion Skins system is not available.");
            return;
        }

        String title = "§6§lMinion Skins";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        Set<MinionSkin> owned = manager.getOwnedSkins(player.getUniqueId());
        MinionSkin current = manager.getActiveSkin(player.getUniqueId());

        // Header
        inv.setItem(4, GUIUtils.createItem(Material.COBBLESTONE, "§6§lCosmetic Minion Skins",
                "§7Themed appearances for your island minions",
                "§7Collection: §a" + owned.size() + " / " + (MinionSkin.values().length - 1) + " §7skins",
                "§7Active theme applies to all your minions (or assign per-minion in /minions GUI)"));

        // NONE option (remove theme)
        ItemStack noneItem = GUIUtils.createItem(Material.BARRIER, "§cRemove Skin",
                "§7Return minions to default appearance");
        ItemMeta noneMeta = noneItem.getItemMeta();
        if (noneMeta != null) {
            noneMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "MINION_SKIN_NONE");
            noneItem.setItemMeta(noneMeta);
        }
        inv.setItem(18, noneItem);

        int slot = 19;
        for (MinionSkin skin : MinionSkin.values()) {
            if (slot > 44) break;
            if (skin.isNone()) continue;

            boolean isOwned = owned.contains(skin);
            boolean isCurrent = skin == current;

            ItemStack item = GUIUtils.createItem(Material.COBBLESTONE,
                    (isCurrent ? "§a§l★ " : isOwned ? "§e" : "§7") + skin.getRarity().getColorCode() + skin.getDisplayName(),
                    "§7" + skin.getDescription(),
                    "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                    isOwned ? "" : "§cLocked - Unlock via prestige or Slayer Shop",
                    isCurrent ? "§aCurrently Active Theme" : isOwned ? "§eClick to apply to your minions" : "§7Preview only");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "MINION_SKIN_" + skin.name());
                item.setItemMeta(meta);
            }

            inv.setItem(slot++, item);
        }

        // Back to Wardrobe
        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals("§6§lMinion Skins")) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        MinionSkinManager manager = plugin.getMinionSkinManager();
        if (manager == null) return;

        if (action.equals("BACK_TO_WARDROBE")) {
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player);
            return;
        }

        if (action.equals("MINION_SKIN_NONE")) {
            manager.setActiveSkin(player, MinionSkin.NONE);
            open(player);
            return;
        }

        if (action.startsWith("MINION_SKIN_")) {
            String skinName = action.substring(12);
            try {
                MinionSkin skin = MinionSkin.valueOf(skinName);
                if (!manager.hasSkin(player.getUniqueId(), skin) && !skin.isNone()) {
                    player.sendMessage("§cYou have not unlocked this minion skin.");
                    return;
                }
                manager.setActiveSkin(player, skin);
                open(player);
            } catch (Exception ignored) {
                player.sendMessage("§cInvalid minion skin.");
            }
        }
    }
}
