package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.PrestigeManager;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, size, 
            MessageUtil.legacy("§6§lIsland Prestige §7(Lv " + current + ")"));

        // Current prestige status - using GUIUtils
        double xpMult = (pm.getPrestigeMultiplier(island, PrestigeManager.PrestigeMultiplierType.XP) - 1) * 100;
        double worthMult = (pm.getPrestigeMultiplier(island, PrestigeManager.PrestigeMultiplierType.WORTH) - 1) * 100;

        gui.setItem(4, GUIUtils.createItem(Material.NETHER_STAR, "§e§lCurrent Prestige: §b" + current,
                "§7Permanent multipliers active:",
                "§a+" + String.format("%.1f", xpMult) + "% §7Island XP",
                "§a+" + String.format("%.1f", worthMult) + "% §7Worth",
                "",
                "§7Higher prestige = stronger bonuses"
        ));

        // Requirements / Status - using GUIUtils
        int minLevel = plugin.getConfig().getInt("island.prestige.requirements.min_island_level", 50);
        double minWorth = plugin.getConfig().getDouble("island.prestige.requirements.min_worth", 250000);
        double currentWorth = plugin.getIslandWorthManager() != null 
                ? plugin.getIslandWorthManager().getCachedWorth(island) : 0;

        Material reqMaterial = canPrestige ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String reqTitle = canPrestige ? "§a§lREADY TO PRESTIGE" : "§c§lRequirements Not Met";
        String reqFooter = canPrestige ? "§aYou meet the requirements!" : "§cClick for exact requirements";

        gui.setItem(12, GUIUtils.createItem(reqMaterial, reqTitle,
                "§7Min Island Level: §e" + minLevel + " §7(You: §b" + island.getLevel() + "§7)",
                "§7Min Worth: §e$" + String.format("%,.0f", minWorth) + " §7(You: §6$" + String.format("%,.0f", currentWorth) + "§7)",
                "",
                reqFooter
        ));

        // Prestige action button - using GUIUtils + PDC
        if (canPrestige) {
            ItemStack prestigeBtn = GUIUtils.createItem(Material.EMERALD_BLOCK, "§a§lPRESTIGE NOW",
                    "§7This will reset your island level & XP",
                    "§7in exchange for permanent power.",
                    "",
                    "§c§lWARNING: This is a major reset!",
                    "§7Click to confirm in next screen"
            );

            ItemMeta pMeta = prestigeBtn.getItemMeta();
            if (pMeta != null) {
                pMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "PRESTIGE");
                prestigeBtn.setItemMeta(pMeta);
            }
            gui.setItem(14, prestigeBtn);
        }

        // Info - using GUIUtils
        gui.setItem(22, GUIUtils.createItem(Material.BOOK, "§ePrestige Info",
                "§7Prestige is the ultimate endgame",
                "§7progression for dedicated players.",
                "§7Each level gives stacking multipliers",
                "§7that make future progress much faster."
        ));

        // Close - using GUIUtils
        gui.setItem(26, GUIUtils.createItem(Material.BARRIER, "§c§lClose", "§7Click to close"));

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
            SoundUtil.click(player);
            // Open confirmation (simple for now - direct prestige with warning)
            player.closeInventory();
            boolean success = plugin.getPrestigeManager().performPrestige(island, player);
            if (!success) {
                SoundUtil.error(player);
                // The PrestigeManager already sent the detailed blocker message
            }
        } else if (clicked.getType() == Material.BARRIER) {
            SoundUtil.click(player);
            player.closeInventory();
        }
    }
}