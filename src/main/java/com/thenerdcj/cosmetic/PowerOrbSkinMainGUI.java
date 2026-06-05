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
 * Power orb skin picker. Opened via {@link PowerOrbSkinGUI#open(Player)}.
 */
public class PowerOrbSkinMainGUI extends CosmeticPickerGUI {

    public PowerOrbSkinMainGUI(FoliaSkyblock plugin) {
        super(plugin);
    }

    @Override
    protected String getTitlePrefix() {
        return "§b§lPower Orb Skins";
    }

    @Override
    protected String getActionKeyName() {
        return "power_orb_skin_action";
    }

    @Override
    protected Material getHeaderIcon() {
        return Material.BEACON;
    }

    @Override
    protected String[] getHeaderLore(Player player) {
        PowerOrbSkinManager manager = plugin.getPowerOrbSkinManager();
        int owned = manager != null ? manager.getOwnedSkins(player.getUniqueId()).size() : 0;
        return new String[]{
                "§7Cosmetic overrides for Power Orbs",
                "§7Collection: §a" + owned + " / " + (PowerOrbSkin.values().length - 1) + " §7skins"
        };
    }

    @Override
    protected String getRemoveButtonLabel() {
        return "§cRemove Skin";
    }

    @Override
    protected String getNoneActionId() {
        return "POWER_ORB_SKIN_NONE";
    }

    @Override
    protected boolean isSystemAvailable(Player player) {
        if (plugin.getPowerOrbSkinManager() == null) {
            player.sendMessage("§cPower Orb Skins system is not available.");
            return false;
        }
        return true;
    }

    @Override
    protected void populateSkinGrid(Inventory gui, Player player) {
        PowerOrbSkinManager manager = plugin.getPowerOrbSkinManager();
        Set<PowerOrbSkin> owned = manager.getOwnedSkins(player.getUniqueId());
        PowerOrbSkin current = manager.getActiveSkin(player.getUniqueId());

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

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "POWER_ORB_SKIN_" + skin.name());
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void handlePickerAction(String action, Player player) {
        PowerOrbSkinManager manager = plugin.getPowerOrbSkinManager();
        if (manager == null) return;

        if ("POWER_ORB_SKIN_NONE".equals(action)) {
            manager.setActiveSkin(player, PowerOrbSkin.NONE);
            return;
        }
        if (action != null && action.startsWith("POWER_ORB_SKIN_")) {
            try {
                PowerOrbSkin skin = PowerOrbSkin.valueOf(action.substring(15));
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