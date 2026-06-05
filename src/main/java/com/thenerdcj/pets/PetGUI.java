package com.thenerdcj.pets;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinator for cosmetic pet GUIs (main list + skin picker on {@link com.thenerdcj.gui.BaseGUI}).
 * Chat rename flow stays here. Open via /pets or Wardrobe.
 */
public class PetGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final PetMainGUI mainGui;
    private final PetSkinGUI skinGui;

    private final Map<UUID, String> pendingPetRenames = new ConcurrentHashMap<>();

    public PetGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.mainGui = new PetMainGUI(plugin, this);
        this.skinGui = new PetSkinGUI(plugin, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        mainGui.open(player);
    }

    void openSkinSelection(Player player, CosmeticPet pet) {
        skinGui.open(player, pet);
    }

    void spawnTemporaryPreview(Player player, CosmeticPet pet) {
        org.bukkit.Location loc = player.getLocation().add(0.5, 1.2, 0.5);
        org.bukkit.entity.ArmorStand preview = (org.bukkit.entity.ArmorStand) player.getWorld()
                .spawnEntity(loc, org.bukkit.entity.EntityType.ARMOR_STAND);

        preview.setVisible(false);
        preview.setGravity(false);
        preview.setSmall(true);
        preview.setMarker(true);
        preview.setCustomNameVisible(true);
        preview.setCustomName("§d" + pet.getCustomName());

        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (pet.getType().getHeadTextureOwner() != null) {
            meta.setOwner(pet.getType().getHeadTextureOwner());
        }
        head.setItemMeta(meta);
        preview.getEquipment().setHelmet(head);

        plugin.getThreadSafety().runOnMainThreadLater(preview::remove, 20L * 8);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String oldName = pendingPetRenames.remove(uuid);
        if (oldName == null) return;

        event.setCancelled(true);
        String newName = event.getMessage().trim();

        if (newName.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7Pet rename cancelled.");
            plugin.getThreadSafety().runOnMainThread(() -> open(player));
            return;
        }

        plugin.getThreadSafety().runOnMainThread(() -> {
            boolean success = plugin.getPetManager().renamePet(uuid, oldName, newName);
            if (success) {
                player.sendMessage("§aPet renamed to: §d" + newName);
            } else {
                player.sendMessage("§cFailed to rename pet.");
            }
            open(player);
        });
    }
}