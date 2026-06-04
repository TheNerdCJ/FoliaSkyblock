package com.thenerdcj.pets;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated GUI for managing cosmetic pets.
 * Can be opened via /pets or integrated from Wardrobe.
 */
public class PetGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey ACTION_KEY;

    // Pending renames: player -> (old custom name of the pet)
    private final Map<UUID, String> pendingPetRenames = new ConcurrentHashMap<>();

    public PetGUI(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.ACTION_KEY = new NamespacedKey(plugin, "pet_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§d§lCosmetic Pets");

        var petManager = plugin.getPetManager();
        List<CosmeticPet> owned = petManager.getOwnedPets(player.getUniqueId());
        CosmeticPet active = petManager.getActivePet(player.getUniqueId());

        // Header
        int collCount = petManager.getPetCollectionCount(player.getUniqueId());
        inv.setItem(4, GUIUtils.createItem(Material.BONE, "§d§lYour Cosmetic Pets",
                "§7Purely visual followers",
                "§7Collection: §a" + collCount + " §7unique pets (rarity XP)"));

        if (owned.isEmpty()) {
            inv.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo pets owned yet",
                    "§7Earn pets through gameplay and events."));
            player.openInventory(inv);
            return;
        }

        int slot = 18;
        for (CosmeticPet pet : owned) {
            if (slot > 44) break;

            boolean isActive = pet == active;
            ItemStack item = pet.createDisplayItem();

            ItemMeta meta = item.getItemMeta();
            List<String> lore = new java.util.ArrayList<>(meta.getLore() != null ? meta.getLore() : new java.util.ArrayList<>());
            lore.add("");
            lore.add(isActive ? "§a§lCurrently Active" : "§eLeft-click §7to equip");
            lore.add("§7Right-click §7for Skins");
            if (pet.getSkin() != null && !pet.getSkin().isNone()) {
                lore.add("§7Skin: " + pet.getSkin().getRarity().getColorCode() + pet.getSkin().getDisplayName());
            }
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "SELECT_" + pet.getType().name() + "_" + pet.getCustomName().replace(" ", "_"));

            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        // Remove active button
        inv.setItem(49, GUIUtils.createItem(Material.BARRIER, "§c§lRemove Active Pet", "§7Click to send your pet away"));

        // Back to Wardrobe
        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Wardrobe");
        PersistentDataContainer backPdc = back.getItemMeta().getPersistentDataContainer();
        backPdc.set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_WARDROBE");
        back.setItemMeta(back.getItemMeta());
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    /**
     * Opens a skin selection GUI for a specific pet.
     */
    private void openSkinSelection(Player player, CosmeticPet pet) {
        var petManager = plugin.getPetManager();
        Set<PetSkin> ownedSkins = petManager.getOwnedSkins(player.getUniqueId());

        String title = "§d§lSkins - " + pet.getCustomName();
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Header
        inv.setItem(4, GUIUtils.createItem(Material.NAME_TAG, "§d§lChoose Skin for " + pet.getCustomName(),
                "§7Current: " + (pet.getSkin() != null && !pet.getSkin().isNone() ? pet.getSkin().getRarity().getColorCode() + pet.getSkin().getDisplayName() : "§7None")));

        // NONE option (remove skin)
        ItemStack noneItem = GUIUtils.createItem(Material.BARRIER, "§cRemove Skin",
                "§7Return to default appearance");
        PersistentDataContainer nonePdc = noneItem.getItemMeta().getPersistentDataContainer();
        nonePdc.set(ACTION_KEY, PersistentDataType.STRING, "SKIN_NONE_" + pet.getCustomName().replace(" ", "_"));
        noneItem.setItemMeta(noneItem.getItemMeta());
        inv.setItem(18, noneItem);

        int slot = 19;
        for (PetSkin skin : PetSkin.values()) {
            if (slot > 44) break;
            if (skin.isNone()) continue;

            boolean owned = ownedSkins.contains(skin);
            boolean current = pet.getSkin() == skin;

            ItemStack item = GUIUtils.createItem(Material.NAME_TAG,
                    (current ? "§a§l★ " : owned ? "§e" : "§7") + skin.getRarity().getColorCode() + skin.getDisplayName(),
                    "§7" + skin.getDescription(),
                    owned ? "" : "§cLocked - Unlock via prestige or slayer shop",
                    current ? "§aCurrently Applied" : owned ? "§eClick to apply" : "§7Preview only");

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, "SKIN_" + skin.name() + "_" + pet.getCustomName().replace(" ", "_"));
            item.setItemMeta(item.getItemMeta());

            inv.setItem(slot++, item);
        }

        // Back button
        ItemStack back = GUIUtils.createItem(Material.ARROW, "§e§lBack to Pets");
        PersistentDataContainer backPdc = back.getItemMeta().getPersistentDataContainer();
        backPdc.set(ACTION_KEY, PersistentDataType.STRING, "BACK_TO_PETS");
        back.setItemMeta(back.getItemMeta());
        inv.setItem(45, back);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean isPetGUI = title.equals("§d§lCosmetic Pets");
        boolean isSkinGUI = title.startsWith("§d§lSkins - ");

        if (!isPetGUI && !isSkinGUI) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        var petManager = plugin.getPetManager();

        // Handle main Pet GUI
        if (isPetGUI) {
            if (action.equals("REMOVE_ACTIVE")) {
                petManager.setActivePet(player, null);
                player.sendMessage("§7Pet sent away.");
                open(player);
                return;
            }
            if (action.equals("BACK_TO_WARDROBE")) {
                player.closeInventory();
                plugin.getWardrobeGUI().openWardrobe(player, com.thenerdcj.wardrobe.WardrobeGUI.View.PETS);
                return;
            }

            if (action.startsWith("SELECT_")) {
                String data = action.substring(7);
                String[] parts = data.split("_", 2);
                if (parts.length < 2) return;

                try {
                    PetType type = PetType.valueOf(parts[0]);
                    String customName = parts[1].replace("_", " ");

                    List<CosmeticPet> owned = petManager.getOwnedPets(player.getUniqueId());
                    for (CosmeticPet pet : owned) {
                        if (pet.getType() == type && pet.getCustomName().equals(customName)) {
                            if (event.isRightClick()) {
                                openSkinSelection(player, pet);
                            } else if (event.isShiftClick()) {
                                player.sendMessage("§aPreviewing §d" + pet.getCustomName() + " §afor 8 seconds...");
                                spawnTemporaryPreview(player, pet);
                            } else {
                                petManager.setActivePet(player, pet);
                                player.sendMessage("§aEquipped: §d" + pet.getCustomName());
                                open(player);
                            }
                            return;
                        }
                    }
                } catch (Exception ignored) {}
            }
            return;
        }

        // Handle Skin Selection GUI
        if (isSkinGUI) {
            if (action.equals("BACK_TO_PETS")) {
                player.closeInventory();
                open(player);
                return;
            }

            if (action.startsWith("SKIN_")) {
                String data = action.substring(5);
                String[] parts = data.split("_", 2);
                if (parts.length < 2) return;

                String skinName = parts[0];
                String petName = parts[1].replace("_", " ");

                try {
                    PetSkin skin = "NONE".equals(skinName) ? PetSkin.NONE : PetSkin.valueOf(skinName);

                    boolean success = petManager.applySkinToPet(player.getUniqueId(), petName, skin);
                    if (success) {
                        player.sendMessage("§aSkin applied: " + (skin.isNone() ? "§7None" : skin.getRarity().getColorCode() + skin.getDisplayName()));
                    }

                    // Refresh the skin GUI
                    List<CosmeticPet> owned = petManager.getOwnedPets(player.getUniqueId());
                    for (CosmeticPet p : owned) {
                        if (p.getCustomName().equals(petName)) {
                            openSkinSelection(player, p);
                            return;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void startRename(Player player, CosmeticPet pet) {
        player.closeInventory();
        player.sendMessage("§eEnter a new name for §d" + pet.getCustomName() + "§e in chat (or type 'cancel'):");
        pendingPetRenames.put(player.getUniqueId(), pet.getCustomName());
    }

    /**
     * Spawns a temporary preview pet near the player for a few seconds.
     * Does not affect the player's actual equipped pet.
     */
    private void spawnTemporaryPreview(Player player, CosmeticPet pet) {
        org.bukkit.Location loc = player.getLocation().add(0.5, 1.2, 0.5);
        org.bukkit.entity.ArmorStand preview = (org.bukkit.entity.ArmorStand) player.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.ARMOR_STAND);

        preview.setVisible(false);
        preview.setGravity(false);
        preview.setSmall(true);
        preview.setMarker(true);
        preview.setCustomNameVisible(true);
        preview.setCustomName("§d" + pet.getCustomName());

        // Head
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (pet.getType().getHeadTextureOwner() != null) {
            meta.setOwner(pet.getType().getHeadTextureOwner());
        }
        head.setItemMeta(meta);
        preview.getEquipment().setHelmet(head);

        // Remove after 8 seconds
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

        // Perform rename on main thread
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
