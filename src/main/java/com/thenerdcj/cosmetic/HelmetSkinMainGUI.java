package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.CosmeticPickerGUI;
import com.thenerdcj.gui.GUIUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/**
 * Helmet skin picker. Opened via {@link HelmetSkinGUI#open(Player)}.
 */
public class HelmetSkinMainGUI extends CosmeticPickerGUI {

    public HelmetSkinMainGUI(FoliaSkyblock plugin) {
        super(plugin);
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lHelmet Skins";
    }

    @Override
    protected String getActionKeyName() {
        return "helmet_skin_action";
    }

    @Override
    protected Material getHeaderIcon() {
        return Material.DIAMOND_HELMET;
    }

    @Override
    protected String[] getHeaderLore(Player player) {
        HelmetSkinManager manager = plugin.getHelmetSkinManager();
        int owned = manager != null ? manager.getOwnedSkins(player.getUniqueId()).size() : 0;
        return new String[]{
                "§7Apply visual overrides to your helmet",
                "§7Collection: §a" + owned + " / " + (HelmetSkin.values().length - 1) + " §7skins"
        };
    }

    @Override
    protected String getRemoveButtonLabel() {
        return "§cRemove Skin";
    }

    @Override
    protected String getNoneActionId() {
        return "HELMET_SKIN_NONE";
    }

    @Override
    protected boolean isSystemAvailable(Player player) {
        if (plugin.getHelmetSkinManager() == null) {
            player.sendMessage("§cHelmet Skins system is not available.");
            return false;
        }
        return true;
    }

    @Override
    protected void populateSkinGrid(Inventory gui, Player player) {
        HelmetSkinManager manager = plugin.getHelmetSkinManager();
        Set<HelmetSkin> owned = manager.getOwnedSkins(player.getUniqueId());
        HelmetSkin current = manager.getActiveHelmetSkin(player.getUniqueId());

        int slot = 19;
        for (HelmetSkin skin : HelmetSkin.values()) {
            if (slot > 44) break;
            if (skin.isNone()) continue;

            boolean isOwned = owned.contains(skin);
            boolean isCurrent = skin == current;

            ItemStack item = GUIUtils.createItem(Material.DIAMOND_HELMET,
                    (isCurrent ? "§a§l★ " : isOwned ? "§e" : "§7") + skin.getRarity().getColorCode() + skin.getDisplayName(),
                    "§7" + skin.getDescription(),
                    "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                    isOwned ? "" : "§cLocked - Unlock via prestige or Slayer Shop",
                    isCurrent ? "§aCurrently Applied" : isOwned ? "§eClick to apply" : "§7Preview only");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "HELMET_SKIN_" + skin.name());
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void handlePickerAction(String action, Player player) {
        HelmetSkinManager manager = plugin.getHelmetSkinManager();
        if (manager == null) return;

        if ("HELMET_SKIN_NONE".equals(action)) {
            manager.setActiveHelmetSkin(player, HelmetSkin.NONE);
            return;
        }
        if (action != null && action.startsWith("HELMET_SKIN_")) {
            try {
                HelmetSkin skin = HelmetSkin.valueOf(action.substring(12));
                if (!manager.hasSkin(player.getUniqueId(), skin) && !skin.isNone()) {
                    player.sendMessage("§cYou have not unlocked this helmet skin.");
                    return;
                }
                manager.setActiveHelmetSkin(player, skin);
                open(player);
            } catch (Exception ignored) {
                player.sendMessage("§cInvalid helmet skin.");
            }
        }
    }
}