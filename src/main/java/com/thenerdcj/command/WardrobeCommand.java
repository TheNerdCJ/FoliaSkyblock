package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;import com.thenerdcj.wardrobe.WardrobeGUI;import com.thenerdcj.wardrobe.WardrobeManager;import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;import org.bukkit.Bukkit;import org.bukkit.Material;import org.bukkit.command.Command;import org.bukkit.command.CommandExecutor;import org.bukkit.command.CommandSender;import org.bukkit.entity.Player;import org.bukkit.inventory.Inventory;import org.bukkit.inventory.ItemStack;import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WardrobeCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;
    private final WardrobeGUI wardrobeGUI;
    private final WardrobeManager wardrobeManager;

    public WardrobeCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.wardrobeGUI = plugin.getWardrobeGUI();
        this.wardrobeManager = plugin.getWardrobeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("folia.skyblock.wardrobe")) {
            player.sendMessage("§cYou do not have permission to use the wardrobe.");
            return true;
        }

        if (args.length > 0) {
            String sub = args[0].toLowerCase();

            if (sub.equals("collection") || sub.equals("col") || sub.equals("collect")) {
                openCollectionView(player);
                return true;
            }

            if (sub.equals("help")) {
                sendHelp(player);
                return true;
            }
        }


        wardrobeGUI.openWardrobe(player);
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§lWardrobe Help");
        player.sendMessage("§e/wardrobe §7- Open your wardrobe");
        player.sendMessage("§e/wardrobe collection §7- View your equipment collection progress");
        player.sendMessage("§e/wardrobe help §7- Show this help");
    }

    private void openCollectionView(Player player) {
        int count = wardrobeManager.getEquipmentCollectionCount(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, "§6§lWardrobe Collection §8(§a" + count + "§8)");

        Set<Material> collected = wardrobeManager.getEquipmentCollection(player.getUniqueId());

        int slot = 10;
        for (Material mat : collected) {
            if (slot >= 44) break;

            ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta(); if (meta != null) { var ser = LegacyComponentSerializer.legacyAmpersand(); meta.displayName(ser.deserialize("§e" + mat.name())); java.util.List<net.kyori.adventure.text.Component> lore = new ArrayList<>(); lore.add(ser.deserialize("§7Collected via Wardrobe")); lore.add(ser.deserialize("§7Contributes to Island XP")); meta.lore(lore); item.setItemMeta(meta); }
            inv.setItem(slot, item);
            slot++;
            if ((slot - 9) % 9 == 0) slot += 2;
        }

        ItemStack info = new ItemStack(org.bukkit.Material.BOOK); ItemMeta im = info.getItemMeta(); var ser = LegacyComponentSerializer.legacyAmpersand(); im.displayName(ser.deserialize("§e§lYour Collection")); im.lore(java.util.Arrays.asList(ser.deserialize("§7Total unique equipment pieces: §a" + count), ser.deserialize("§7Each new material grants Island XP"), ser.deserialize("§7when first saved to any wardrobe slot."), ser.deserialize(""), ser.deserialize("§8Collection grows as you save more gear!"))); info.setItemMeta(im);
        inv.setItem(49, info);
        player.openInventory(inv);
        player.sendMessage("§aOpened your Wardrobe Collection view (§e" + count + " §apieces).");
    }
}