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
 * Backpack skin picker. Opened via {@link BackpackSkinGUI#open(Player)}.
 */
public class BackpackSkinMainGUI extends CosmeticPickerGUI {

    public BackpackSkinMainGUI(FoliaSkyblock plugin) {
        super(plugin);
    }

    @Override
    protected String getTitlePrefix() {
        return "§a§lBackpack Skins";
    }

    @Override
    protected String getActionKeyName() {
        return "backpack_skin_action";
    }

    @Override
    protected Material getHeaderIcon() {
        return Material.CHEST;
    }

    @Override
    protected String[] getHeaderLore(Player player) {
        BackpackSkinManager manager = plugin.getBackpackSkinManager();
        int owned = manager != null ? manager.getOwnedSkins(player.getUniqueId()).size() : 0;
        return new String[]{
                "§7Cosmetic overrides for backpacks and storage",
                "§7Collection: §a" + owned + " / " + (BackpackSkin.values().length - 1) + " §7skins"
        };
    }

    @Override
    protected String getRemoveButtonLabel() {
        return "§cRemove Skin";
    }

    @Override
    protected String getNoneActionId() {
        return "BACKPACK_SKIN_NONE";
    }

    @Override
    protected boolean isSystemAvailable(Player player) {
        if (plugin.getBackpackSkinManager() == null) {
            player.sendMessage("§cBackpack Skins system is not available.");
            return false;
        }
        return true;
    }

    @Override
    protected void populateSkinGrid(Inventory gui, Player player) {
        BackpackSkinManager manager = plugin.getBackpackSkinManager();
        Set<BackpackSkin> owned = manager.getOwnedSkins(player.getUniqueId());
        BackpackSkin current = manager.getActiveSkin(player.getUniqueId());

        int slot = 19;
        for (BackpackSkin skin : BackpackSkin.values()) {
            if (slot > 44) break;
            if (skin.isNone()) continue;

            boolean isOwned = owned.contains(skin);
            boolean isCurrent = skin == current;

            ItemStack item = GUIUtils.createItem(Material.CHEST,
                    (isCurrent ? "§a§l★ " : isOwned ? "§e" : "§7") + skin.getRarity().getColorCode() + skin.getDisplayName(),
                    "§7" + skin.getDescription(),
                    "§7Rarity: " + skin.getRarity().getColorCode() + skin.getRarity().getDisplayName(),
                    isOwned ? "" : "§cLocked - Unlock via prestige or Slayer Shop",
                    isCurrent ? "§aCurrently Active" : isOwned ? "§eClick to apply" : "§7Preview only");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "BACKPACK_SKIN_" + skin.name());
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void handlePickerAction(String action, Player player) {
        BackpackSkinManager manager = plugin.getBackpackSkinManager();
        if (manager == null) return;

        if ("BACKPACK_SKIN_NONE".equals(action)) {
            manager.setActiveSkin(player, BackpackSkin.NONE);
            return;
        }
        if (action != null && action.startsWith("BACKPACK_SKIN_")) {
            try {
                BackpackSkin skin = BackpackSkin.valueOf(action.substring(14));
                if (!manager.hasSkin(player.getUniqueId(), skin) && !skin.isNone()) {
                    player.sendMessage("§cYou have not unlocked this backpack skin.");
                    return;
                }
                manager.setActiveSkin(player, skin);
                open(player);
            } catch (Exception ignored) {
                player.sendMessage("§cInvalid backpack skin.");
            }
        }
    }
}