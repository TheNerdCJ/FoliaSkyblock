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
 * Death message picker (CosmeticPickerGUI variant: glass background, back @0, active @8).
 */
public class DeathMessageMainGUI extends CosmeticPickerGUI {

    public DeathMessageMainGUI(FoliaSkyblock plugin) {
        super(plugin);
    }

    @Override
    protected String getTitlePrefix() {
        return "§4§lDeath Messages";
    }

    @Override
    protected String getActionKeyName() {
        return "death_message_action";
    }

    @Override
    protected Material getHeaderIcon() {
        return Material.WRITABLE_BOOK;
    }

    @Override
    protected String[] getHeaderLore(Player player) {
        DeathMessageManager manager = plugin.getDeathMessageManager();
        int count = manager != null ? manager.getMessageCollectionCount(player.getUniqueId()) : 0;
        int total = DeathMessageCosmetic.values().length - 1;
        return new String[]{
                "§7Custom kill/death announcements",
                "§7Collection: §a" + count + " / " + total
        };
    }

    @Override
    protected String getRemoveButtonLabel() {
        return "§c§lDisable Messages";
    }

    @Override
    protected String getNoneActionId() {
        return "NONE";
    }

    @Override
    protected boolean useGlassBackground() {
        return true;
    }

    @Override
    protected int getBackButtonSlot() {
        return 0;
    }

    @Override
    protected void decorateHeaderExtras(Inventory gui, Player player) {
        DeathMessageManager manager = plugin.getDeathMessageManager();
        if (manager == null) {
            return;
        }
        DeathMessageCosmetic active = manager.getActiveDeathMessage(player.getUniqueId());
        gui.setItem(8, GUIUtils.createItem(Material.NAME_TAG, "§e§lActive: "
                + (active.isNone() ? "None" : active.getDisplayName())));
    }

    @Override
    protected boolean isSystemAvailable(Player player) {
        if (plugin.getDeathMessageManager() == null) {
            player.sendMessage("§cDeath Messages system unavailable.");
            return false;
        }
        return true;
    }

    @Override
    protected void populateSkinGrid(Inventory gui, Player player) {
        DeathMessageManager manager = plugin.getDeathMessageManager();
        DeathMessageCosmetic active = manager.getActiveDeathMessage(player.getUniqueId());
        Set<DeathMessageCosmetic> owned = manager.getOwnedMessages(player.getUniqueId());

        int slot = 19;
        for (DeathMessageCosmetic msg : DeathMessageCosmetic.values()) {
            if (msg.isNone()) continue;
            if (slot > 44) break;

            boolean has = owned.contains(msg);
            boolean isActive = msg == active;

            ItemStack item = GUIUtils.createItem(
                    has ? (isActive ? Material.LIME_DYE : Material.PAPER) : Material.GRAY_DYE,
                    (isActive ? "§a§l★ " : has ? "§f" : "§7") + msg.getDisplayName(),
                    "§7" + msg.getDescription(),
                    "§7Rarity: " + msg.getRarity().getColorCode() + msg.getRarity().getDisplayName(),
                    has ? (isActive ? "§aCurrently Active" : "§eClick to activate") : "§cLocked");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "MSG_" + msg.name());
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void handlePickerAction(String action, Player player) {
        DeathMessageManager manager = plugin.getDeathMessageManager();
        if (manager == null) return;

        if ("NONE".equals(action)) {
            manager.setActiveDeathMessage(player, DeathMessageCosmetic.NONE);
            return;
        }
        if (action != null && action.startsWith("MSG_")) {
            try {
                DeathMessageCosmetic msg = DeathMessageCosmetic.valueOf(action.substring(4));
                if (manager.hasMessage(player.getUniqueId(), msg)) {
                    manager.setActiveDeathMessage(player, msg);
                } else {
                    player.sendMessage("§cYou have not unlocked this message.");
                }
                open(player);
            } catch (Exception ignored) {
            }
        }
    }
}