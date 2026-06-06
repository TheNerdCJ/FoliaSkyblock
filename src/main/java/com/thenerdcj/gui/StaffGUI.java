package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.command.StaffCommand;
import com.thenerdcj.util.MessageUtil;
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
import org.bukkit.persistence.PersistentDataType;

/**
 * Expanded Staff Panel GUI (EssentialsX-style quick actions for staff).
 * Toggles for vanish/fly/god/socialspy + quick gm + tools.
 * Self-actions; use commands for targeting others.
 * Refreshes on click for live state.
 */
public class StaffGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    public StaffGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "staff_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player staff) {
        if (!staff.hasPermission("foliasb.staff")) {
            MessageUtil.sendMessage(staff, "§cNo permission.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 36, "§c§lStaff Panel");

        // Header info
        gui.setItem(4, GUIUtils.createItem(Material.NETHER_STAR, "§c§lStaff Panel",
                "§7Quick actions for §e" + staff.getName(),
                "§7Use commands for <player> targets"));

        // Row 1: Toggles
        boolean vanished = false;
        StaffCommand sc = plugin.getStaffCommand();
        if (sc != null) {
            // Use reflection? or add isVanished to StaffCommand. For now check via chat? Simple: default off, toggle will work.
            // We'll track via direct state where possible.
        }
        // Vanish
        boolean isVanished = (sc != null) && sc.isVanished(staff.getUniqueId());
        ItemStack vanish = GUIUtils.createItem(isVanished ? Material.LIME_DYE : Material.GRAY_DYE,
                "§a§lVanish " + (isVanished ? "§2(ON)" : "§7(OFF)"),
                "§7Click to toggle vanish mode");
        attachAction(vanish, "TOGGLE_VANISH");
        gui.setItem(10, vanish);

        // Fly
        boolean flying = staff.getAllowFlight();
        ItemStack fly = GUIUtils.createItem(flying ? Material.ELYTRA : Material.FEATHER,
                "§b§lFly " + (flying ? "§2(ON)" : "§7(OFF)"),
                "§7Click to toggle flight");
        attachAction(fly, "TOGGLE_FLY");
        gui.setItem(11, fly);

        // God
        boolean god = staff.isInvulnerable();
        ItemStack godItem = GUIUtils.createItem(god ? Material.GOLDEN_APPLE : Material.APPLE,
                "§e§lGod " + (god ? "§2(ON)" : "§7(OFF)"),
                "§7Click to toggle god/invulnerable");
        attachAction(godItem, "TOGGLE_GOD");
        gui.setItem(12, godItem);

        // Social Spy
        boolean spying = (plugin.getChatManager() != null) && plugin.getChatManager().isStaffSpying(staff.getUniqueId());
        ItemStack spy = GUIUtils.createItem(spying ? Material.ENDER_EYE : Material.ENDER_PEARL,
                "§d§lSocial Spy " + (spying ? "§2(ON)" : "§7(OFF)"),
                "§7See all /msg and /r messages");
        attachAction(spy, "TOGGLE_SOCIALSPY");
        gui.setItem(13, spy);

        // Gamemode quick self
        gui.setItem(19, makeGmButton(Material.GRASS_BLOCK, "§a§lSurvival", "SET_GM_SURVIVAL"));
        gui.setItem(20, makeGmButton(Material.DIAMOND_BLOCK, "§b§lCreative", "SET_GM_CREATIVE"));
        gui.setItem(21, makeGmButton(Material.CHAINMAIL_CHESTPLATE, "§6§lAdventure", "SET_GM_ADVENTURE"));
        gui.setItem(22, makeGmButton(Material.ENDER_PEARL, "§7§lSpectator", "SET_GM_SPECTATOR"));

        // Tools row
        gui.setItem(28, GUIUtils.createItem(Material.GOLDEN_APPLE, "§c§lHeal Self", "HEAL_SELF"));
        gui.setItem(29, GUIUtils.createItem(Material.ANVIL, "§a§lRepair Held", "REPAIR_SELF"));
        gui.setItem(30, GUIUtils.createItem(Material.BARRIER, "§c§lClear Inv", "CLEAR_SELF"));

        // Admin tools
        gui.setItem(31, GUIUtils.createItem(Material.COMPASS, "§6§l/setspawn", "HINT_SETSPAWN",
                "§7Sets global hub spawn to your pos"));
        gui.setItem(32, GUIUtils.createItem(Material.BOOK, "§e§l/isadmin", "HINT_ISADMIN",
                "§7Island admin: reset, balance, debug..."));

        // Close
        gui.setItem(35, GUIUtils.createItem(Material.BARRIER, "§c§lClose"));

        staff.openInventory(gui);
    }

    private ItemStack makeGmButton(Material mat, String name, String action) {
        ItemStack item = GUIUtils.createItem(mat, name, "§7Click to set your gamemode");
        attachAction(item, action);
        return item;
    }

    private void attachAction(ItemStack item, String action) {
        if (item == null || item.getItemMeta() == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player staff)) return;
        if (!e.getView().getTitle().contains("Staff Panel")) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "TOGGLE_VANISH" -> {
                StaffCommand scmd = plugin.getStaffCommand();
                if (scmd != null) {
                    scmd.toggleVanish(staff);
                } else {
                    MessageUtil.sendMessage(staff, "§cStaff tools unavailable.");
                }
            }
            case "TOGGLE_FLY" -> {
                boolean newFly = !staff.getAllowFlight();
                staff.setAllowFlight(newFly);
                staff.setFlying(newFly);
                MessageUtil.sendMessage(staff, "§aFlight " + (newFly ? "§2enabled" : "§cdisabled"));
            }
            case "TOGGLE_GOD" -> {
                boolean newGod = !staff.isInvulnerable();
                staff.setInvulnerable(newGod);
                MessageUtil.sendMessage(staff, "§aGod mode " + (newGod ? "§2enabled" : "§cdisabled"));
            }
            case "TOGGLE_SOCIALSPY" -> {
                if (plugin.getChatManager() != null) {
                    plugin.getChatManager().toggleStaffSpy(staff);
                }
            }
            case "SET_GM_SURVIVAL" -> staff.setGameMode(org.bukkit.GameMode.SURVIVAL);
            case "SET_GM_CREATIVE" -> staff.setGameMode(org.bukkit.GameMode.CREATIVE);
            case "SET_GM_ADVENTURE" -> staff.setGameMode(org.bukkit.GameMode.ADVENTURE);
            case "SET_GM_SPECTATOR" -> staff.setGameMode(org.bukkit.GameMode.SPECTATOR);
            case "HEAL_SELF" -> {
                staff.setHealth(20);
                staff.setFoodLevel(20);
                staff.setSaturation(20);
                MessageUtil.sendMessage(staff, "§aHealed.");
            }
            case "REPAIR_SELF" -> {
                var item = staff.getInventory().getItemInMainHand();
                if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg) {
                    dmg.setDamage(0);
                    item.setItemMeta(dmg);
                    MessageUtil.sendMessage(staff, "§aItem repaired.");
                } else {
                    MessageUtil.sendMessage(staff, "§cCannot repair held item.");
                }
            }
            case "CLEAR_SELF" -> {
                staff.getInventory().clear();
                MessageUtil.sendMessage(staff, "§aInventory cleared.");
            }
            case "HINT_SETSPAWN" -> {
                staff.closeInventory();
                staff.performCommand("setspawn");
            }
            case "HINT_ISADMIN" -> {
                staff.closeInventory();
                staff.performCommand("isadmin");
            }
            default -> {}
        }

        // Refresh GUI for live states (except close/hints that closed)
        if (!action.startsWith("HINT_")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (staff.isOnline()) open(staff);
            });
        }
    }

}
