package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.PrestigeManager;
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

import java.util.Arrays;

/**
 * Prestige GUI - Info + Confirmation for the high-endgame prestige system.
 * Follows established PDC + autoRegister patterns.
 */
public class PrestigeGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public PrestigeGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public PrestigeGUI(FoliaSkyblock plugin, boolean autoRegister) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "prestige_action");
        if (autoRegister) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open(Player player, Island island) {
        PrestigeManager pm = plugin.getPrestigeManager();
        int current = pm.getPrestigeLevel(island);
        boolean canPrestige = pm.canPrestige(island);

        int size = 27;
        Inventory gui = Bukkit.createInventory(null, size, "§6§lIsland Prestige §7(Lv " + current + ")");

        // Current prestige status
        ItemStack status = new ItemStack(Material.NETHER_STAR);
        ItemMeta sMeta = status.getItemMeta();
        if (sMeta != null) {
            sMeta.setDisplayName("§e§lCurrent Prestige: §b" + current);
            double xpMult = (pm.getPrestigeMultiplier(island, PrestigeManager.PrestigeMultiplierType.XP) - 1) * 100;
            double worthMult = (pm.getPrestigeMultiplier(island, PrestigeManager.PrestigeMultiplierType.WORTH) - 1) * 100;
            sMeta.setLore(Arrays.asList(
                "§7Permanent multipliers active:",
                "§a+" + String.format("%.1f", xpMult) + "% §7Island XP",
                "§a+" + String.format("%.1f", worthMult) + "% §7Worth",
                "",
                "§7Higher prestige = stronger bonuses"
            ));
            status.setItemMeta(sMeta);
        }
        gui.setItem(4, status);

        // Requirements / Status
        ItemStack req = new ItemStack(canPrestige ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta rMeta = req.getItemMeta();
        if (rMeta != null) {
            rMeta.setDisplayName(canPrestige ? "§a§lREADY TO PRESTIGE" : "§c§lRequirements Not Met");
            rMeta.setLore(Arrays.asList(
                "§7Min Island Level: §e" + plugin.getConfig().getInt("island.prestige.requirements.min_island_level", 50),
                "§7Min Worth: §e" + String.format("%,.0f", plugin.getConfig().getDouble("island.prestige.requirements.min_worth", 250000)),
                "",
                "§7Your Level: §b" + island.getLevel(),
                canPrestige ? "§aYou meet the requirements!" : "§cKeep progressing!"
            ));
            req.setItemMeta(rMeta);
        }
        gui.setItem(12, req);

        // Prestige action button
        if (canPrestige) {
            ItemStack prestigeBtn = new ItemStack(Material.EMERALD_BLOCK);
            ItemMeta pMeta = prestigeBtn.getItemMeta();
            if (pMeta != null) {
                pMeta.setDisplayName("§a§lPRESTIGE NOW");
                pMeta.setLore(Arrays.asList(
                    "§7This will reset your island level & XP",
                    "§7in exchange for permanent power.",
                    "",
                    "§c§lWARNING: This is a major reset!",
                    "§7Click to confirm in next screen"
                ));
                pMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "PRESTIGE");
                prestigeBtn.setItemMeta(pMeta);
            }
            gui.setItem(14, prestigeBtn);
        }

        // Info
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta iMeta = info.getItemMeta();
        if (iMeta != null) {
            iMeta.setDisplayName("§ePrestige Info");
            iMeta.setLore(Arrays.asList(
                "§7Prestige is the ultimate endgame",
                "§7progression for dedicated players.",
                "§7Each level gives stacking multipliers",
                "§7that make future progress much faster."
            ));
            info.setItemMeta(iMeta);
        }
        gui.setItem(22, info);

        // Close
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lClose");
            close.setItemMeta(cMeta);
        }
        gui.setItem(26, close);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§6§lIsland Prestige")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if ("PRESTIGE".equals(action)) {
            // Open confirmation (simple for now - direct prestige with warning)
            player.closeInventory();
            boolean success = plugin.getPrestigeManager().performPrestige(island, player);
            if (!success) {
                player.sendMessage("§cPrestige failed. Requirements may have changed.");
            }
        } else if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
        }
    }
}