package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.skills.PlayerSkillManager;
import com.thenerdcj.skills.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;

/**
 * Simple GUI for viewing per-player skills (MCMMO style).
 * Shows level, XP progress, ability status.
 */
public class SkillGUI implements Listener {

    private final FoliaSkyblock plugin;

    public SkillGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        PlayerSkillManager sm = plugin.getPlayerSkillManager();
        if (sm == null) {
            player.sendMessage("§cSkill system not loaded.");
            return;
        }

        UUID uuid = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lPlayer Skills");

        // Fill glass
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName("§8 ");
        glass.setItemMeta(gm);
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        int slot = 9;
        Map<SkillType, double[]> skills = sm.getPlayerSkills(uuid);
        for (SkillType type : SkillType.values()) {
            double[] data = skills.getOrDefault(type, new double[]{0, 1});
            int level = (int) data[1];
            double xp = data[0];
            double nextXp = 100 * (level + 1) * 1.2; // approx
            double progress = Math.min(1.0, xp / nextXp);

            Material mat = getIcon(type);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e§l" + type.getDisplayName() + " §7Lv " + level);
            meta.setLore(java.util.Arrays.asList(
                "§7" + type.getDescription(),
                "§7XP: §a" + String.format("%.0f", xp) + " / " + String.format("%.0f", nextXp),
                "§7Progress: §b" + (int)(progress * 100) + "%",
                "§7Ability: " + (sm.isAbilityActive(uuid, type) ? "§aActive" : "§7Ready at Lv10+"),
                "",
                "§eClick for details (future)"
            ));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
            if (slot > 44) break;
        }

        // Fill remaining with glass
        ItemStack fillerGlass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gmeta = fillerGlass.getItemMeta();
        gmeta.setDisplayName("§8 ");
        fillerGlass.setItemMeta(gmeta);
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, fillerGlass);
        }

        // Info
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6§lPlayer Skills");
        im.setLore(java.util.Arrays.asList(
            "§7Gain XP from mining, chopping, farming,",
            "§7fishing, fighting, etc. (MCMMO inspired)",
            "§7Levels unlock abilities and bonuses.",
            "§7Check anti-cheat safe - no macro XP.",
            "§7Abilities: sneak+action for mining/wood at Lv10+.",
            "",
            "§eUse /skills or this GUI"
        ));
        info.setItemMeta(im);
        inv.setItem(4, info);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cClose");
        close.setItemMeta(cm);
        inv.setItem(53, close);

        player.openInventory(inv);
    }

    private Material getIcon(SkillType type) {
        switch (type) {
            case MINING: return Material.DIAMOND_PICKAXE;
            case WOODCUTTING: return Material.IRON_AXE;
            case FARMING: return Material.WHEAT;
            case FISHING: return Material.FISHING_ROD;
            case COMBAT: return Material.DIAMOND_SWORD;
            case EXCAVATION: return Material.IRON_SHOVEL;
            case ACROBATICS: return Material.LEATHER_BOOTS;
            case REPAIR: return Material.ANVIL;
            default: return Material.BOOK;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().startsWith("§6§lPlayer Skills")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() != null && (e.getSlot() == 53 || e.getCurrentItem().getType() == Material.BARRIER)) {
            p.closeInventory();
        }
        // Future: clicking skill could show detailed ability info or activate toggle
    }
}