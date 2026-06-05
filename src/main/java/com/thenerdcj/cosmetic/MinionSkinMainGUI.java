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
 * Minion skin picker. Opened via {@link MinionSkinGUI#open(Player)}.
 */
public class MinionSkinMainGUI extends CosmeticPickerGUI {

    public MinionSkinMainGUI(FoliaSkyblock plugin) {
        super(plugin);
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lMinion Skins";
    }

    @Override
    protected String getActionKeyName() {
        return "minion_skin_action";
    }

    @Override
    protected Material getHeaderIcon() {
        return Material.COBBLESTONE;
    }

    @Override
    protected String[] getHeaderLore(Player player) {
        MinionSkinManager manager = plugin.getMinionSkinManager();
        int owned = manager != null ? manager.getOwnedSkins(player.getUniqueId()).size() : 0;
        return new String[]{
                "§7Themed appearances for your island minions",
                "§7Collection: §a" + owned + " / " + (MinionSkin.values().length - 1) + " §7skins",
                "§7Active theme applies to all your minions (or assign per-minion in /minions GUI)"
        };
    }

    @Override
    protected String getRemoveButtonLabel() {
        return "§cRemove Skin";
    }

    @Override
    protected String getNoneActionId() {
        return "MINION_SKIN_NONE";
    }

    @Override
    protected boolean isSystemAvailable(Player player) {
        if (plugin.getMinionSkinManager() == null) {
            player.sendMessage("§cMinion Skins system is not available.");
            return false;
        }
        return true;
    }

    @Override
    protected void populateSkinGrid(Inventory gui, Player player) {
        MinionSkinManager manager = plugin.getMinionSkinManager();
        Set<MinionSkin> owned = manager.getOwnedSkins(player.getUniqueId());
        MinionSkin current = manager.getActiveSkin(player.getUniqueId());

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
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void handlePickerAction(String action, Player player) {
        MinionSkinManager manager = plugin.getMinionSkinManager();
        if (manager == null) return;

        if ("MINION_SKIN_NONE".equals(action)) {
            manager.setActiveSkin(player, MinionSkin.NONE);
            return;
        }
        if (action != null && action.startsWith("MINION_SKIN_")) {
            try {
                MinionSkin skin = MinionSkin.valueOf(action.substring(12));
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