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
 * Join/Leave message picker (CosmeticPickerGUI variant).
 */
public class JoinLeaveMessageMainGUI extends CosmeticPickerGUI {

    public JoinLeaveMessageMainGUI(FoliaSkyblock plugin) {
        super(plugin);
    }

    @Override
    protected String getTitlePrefix() {
        return "§a§lJoin/Leave Messages";
    }

    @Override
    protected String getActionKeyName() {
        return "join_leave_message_action";
    }

    @Override
    protected Material getHeaderIcon() {
        return Material.NAME_TAG;
    }

    @Override
    protected String[] getHeaderLore(Player player) {
        JoinLeaveMessageManager manager = plugin.getJoinLeaveMessageManager();
        int count = manager != null ? manager.getMessageCollectionCount(player.getUniqueId()) : 0;
        int total = JoinLeaveMessageCosmetic.values().length - 1;
        return new String[]{
                "§7Custom join and leave announcements",
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
        JoinLeaveMessageManager manager = plugin.getJoinLeaveMessageManager();
        if (manager == null) {
            return;
        }
        JoinLeaveMessageCosmetic active = manager.getActiveJoinLeaveMessage(player.getUniqueId());
        gui.setItem(8, GUIUtils.createItem(Material.NAME_TAG, "§e§lActive: "
                + (active.isNone() ? "None" : active.getDisplayName())));
    }

    @Override
    protected boolean isSystemAvailable(Player player) {
        if (plugin.getJoinLeaveMessageManager() == null) {
            player.sendMessage("§cJoin/Leave Messages system unavailable.");
            return false;
        }
        return true;
    }

    @Override
    protected void populateSkinGrid(Inventory gui, Player player) {
        JoinLeaveMessageManager manager = plugin.getJoinLeaveMessageManager();
        JoinLeaveMessageCosmetic active = manager.getActiveJoinLeaveMessage(player.getUniqueId());
        Set<JoinLeaveMessageCosmetic> owned = manager.getOwnedMessages(player.getUniqueId());

        int slot = 19;
        for (JoinLeaveMessageCosmetic msg : JoinLeaveMessageCosmetic.values()) {
            if (msg.isNone()) continue;
            if (slot > 44) break;

            boolean has = owned.contains(msg);
            boolean isActive = msg == active;

            ItemStack item = GUIUtils.createItem(
                    has ? (isActive ? Material.LIME_DYE : Material.PAPER) : Material.GRAY_DYE,
                    (isActive ? "§a§l★ " : has ? "§f" : "§7") + msg.getDisplayName(),
                    "§7Join: " + (msg.getJoinFormat().isEmpty() ? "§7Default" : "§f" + msg.getJoinFormat()),
                    "§7Leave: " + (msg.getLeaveFormat().isEmpty() ? "§7Default" : "§f" + msg.getLeaveFormat()),
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
        JoinLeaveMessageManager manager = plugin.getJoinLeaveMessageManager();
        if (manager == null) return;

        if ("NONE".equals(action)) {
            manager.setActiveJoinLeaveMessage(player, JoinLeaveMessageCosmetic.NONE);
            open(player);
            return;
        }
        if (action != null && action.startsWith("MSG_")) {
            try {
                JoinLeaveMessageCosmetic msg = JoinLeaveMessageCosmetic.valueOf(action.substring(4));
                if (manager.hasMessage(player.getUniqueId(), msg)) {
                    manager.setActiveJoinLeaveMessage(player, msg);
                } else {
                    player.sendMessage("§cYou have not unlocked this message.");
                }
                open(player);
            } catch (Exception ignored) {
            }
        }
    }
}