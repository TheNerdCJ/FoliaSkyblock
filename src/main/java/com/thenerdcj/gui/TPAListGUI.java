package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.manager.TeleportRequestManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/**
 * Interactive TPA Request List GUI.
 * Shows player heads of requesters.
 * Left-click head = Deny, Right-click head = Accept.
 * Integrates with TeleportRequestManager.
 * Example of head-based GUI similar to other project GUIs.
 */
public class TPAListGUI {

    private final FoliaSkyblock plugin;
    private final TeleportRequestManager tpaManager;

    public TPAListGUI(FoliaSkyblock plugin, TeleportRequestManager tpaManager) {
        this.plugin = plugin;
        this.tpaManager = tpaManager;
    }

    public void open(Player viewer) {
        List<UUID> requesters = tpaManager.getPendingRequestersFor(viewer);
        int size = Math.max(9, ((requesters.size() / 9) + 1) * 9);
        if (size > 54) size = 54; // cap

        Inventory inv = Bukkit.createInventory(null, size, "§6§lTPA Requests §7(" + requesters.size() + ")");

        int slot = 0;
        for (UUID uuid : requesters) {
            if (slot >= size) break;
            Player requester = Bukkit.getPlayer(uuid);
            if (requester == null || !requester.isOnline()) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(requester);
            meta.setDisplayName("§e" + requester.getName());
            meta.setLore(java.util.Arrays.asList(
                    "§7Left-click to §cDENY",
                    "§7Right-click to §aACCEPT",
                    "§8Click to manage request"
            ));
            head.setItemMeta(meta);

            inv.setItem(slot++, head);
        }

        // Fill empty with glass or info
        if (requesters.isEmpty()) {
            ItemStack info = new ItemStack(Material.BARRIER);
            var meta = info.getItemMeta();
            meta.setDisplayName("§cNo pending TPA requests");
            meta.setLore(java.util.Arrays.asList("§7No one is requesting to teleport to you right now."));
            info.setItemMeta(meta);
            inv.setItem(4, info);
        }

        viewer.openInventory(inv);
    }

    /**
     * Call this from an InventoryClickEvent listener.
     * Example integration:
     * if (event.getView().getTitle().contains("TPA Requests")) {
     *     tpaListGUI.handleClick(event);
     * }
     */
    public void handleClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (!event.getView().getTitle().contains("TPA Requests")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) return;

        String requesterName = meta.getOwningPlayer().getName();
        Player requester = Bukkit.getPlayer(requesterName);
        if (requester == null || !requester.isOnline()) {
            viewer.sendMessage("§cRequester is no longer online.");
            viewer.closeInventory();
            return;
        }

        ClickType click = event.getClick();

        if (click == ClickType.LEFT) {
            // Deny
            tpaManager.denyRequest(viewer, requester);
            viewer.sendMessage("§cDenied TPA from §e" + requesterName);
            viewer.closeInventory();
            // Reopen to refresh list
            plugin.getThreadSafety().runOnMainThreadLater(() -> open(viewer), 1L);
        } else if (click == ClickType.RIGHT) {
            // Accept
            boolean success = tpaManager.acceptRequest(viewer, requester);
            if (success) {
                viewer.closeInventory();
            }
        }
    }
}
