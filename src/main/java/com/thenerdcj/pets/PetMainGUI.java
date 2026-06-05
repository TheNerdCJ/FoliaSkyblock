package com.thenerdcj.pets;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.BaseGUI;
import com.thenerdcj.gui.GUIUtils;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Main cosmetic pets list (BaseGUI). Opened via {@link PetGUI#open(Player)}.
 */
public class PetMainGUI extends BaseGUI {

    private final PetGUI coordinator;

    public PetMainGUI(FoliaSkyblock plugin, PetGUI coordinator) {
        super(plugin, true);
        this.coordinator = coordinator;
    }

    @Override
    protected String getTitlePrefix() {
        return "§d§lCosmetic Pets";
    }

    @Override
    protected String getActionKeyName() {
        return "pet_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 27;
    }

    @Override
    protected int getTotalPages(Player player) {
        return 1;
    }

    @Override
    public void open(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);
        Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(getTitlePrefix()));
        addHeader(gui, player, page);
        populatePage(gui, player, page);
        addStandardNavigation(gui, page, getTotalPages(player));
        player.openInventory(gui);
    }

    @Override
    protected void addHeader(Inventory gui, Player player, int page) {
        var petManager = plugin.getPetManager();
        int collCount = petManager.getPetCollectionCount(player.getUniqueId());
        gui.setItem(4, GUIUtils.createItem(Material.BONE, "§d§lYour Cosmetic Pets",
                "§7Purely visual followers",
                "§7Collection: §a" + collCount + " §7unique pets (rarity XP)"));
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        var petManager = plugin.getPetManager();
        List<CosmeticPet> owned = petManager.getOwnedPets(player.getUniqueId());
        CosmeticPet active = petManager.getActivePet(player.getUniqueId());

        if (owned.isEmpty()) {
            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo pets owned yet",
                    "§7Earn pets through gameplay and events."));
            return;
        }

        int slot = 18;
        for (CosmeticPet pet : owned) {
            if (slot > 44) break;

            boolean isActive = pet == active;
            ItemStack item = pet.createDisplayItem();

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            List<String> lore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : new ArrayList<>());
            lore.add("");
            lore.add(isActive ? "§a§lCurrently Active" : "§eLeft-click §7to equip");
            lore.add("§7Right-click §7for Skins");
            lore.add("§7Shift-click §7to preview");
            if (pet.getSkin() != null && !pet.getSkin().isNone()) {
                lore.add("§7Skin: " + pet.getSkin().getRarity().getColorCode() + pet.getSkin().getDisplayName());
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                    "SELECT_" + pet.getType().name() + "_" + pet.getCustomName().replace(" ", "_"));
            item.setItemMeta(meta);
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void addStandardNavigation(Inventory gui, int page, int totalPages) {
        gui.setItem(45, GUIUtils.createNavButton(Material.ARROW, "§e§lBack to Wardrobe", ACTION_KEY, "BACK_TO_WARDROBE"));
        gui.setItem(49, GUIUtils.createNavButton(Material.BARRIER, "§c§lRemove Active Pet", ACTION_KEY, "REMOVE_ACTIVE"));
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        var petManager = plugin.getPetManager();
        if ("REMOVE_ACTIVE".equals(action)) {
            petManager.setActivePet(player, null);
            player.sendMessage("§7Pet sent away.");
            SoundUtil.click(player);
            open(player);
            return;
        }
        if ("BACK_TO_WARDROBE".equals(action)) {
            SoundUtil.click(player);
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player, com.thenerdcj.wardrobe.WardrobeGUI.View.PETS);
            return;
        }
        if (action != null && action.startsWith("SELECT_")) {
            player.sendMessage("§7Use left-click to equip, right-click for skins, shift-click to preview.");
        }
    }

    @EventHandler
    public void onPetMainClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(getTitlePrefix())) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        if ("PREV".equals(action) || "NEXT".equals(action) || "CLOSE".equals(action)) {
            onInventoryClick(event);
            return;
        }

        if ("REMOVE_ACTIVE".equals(action) || "BACK_TO_WARDROBE".equals(action)) {
            SoundUtil.click(player);
            handleAction(action, meta.getPersistentDataContainer(), player, 0, clicked);
            return;
        }

        if (!action.startsWith("SELECT_")) return;

        String data = action.substring(7);
        String[] parts = data.split("_", 2);
        if (parts.length < 2) return;

        try {
            PetType type = PetType.valueOf(parts[0]);
            String customName = parts[1].replace("_", " ");

            var petManager = plugin.getPetManager();
            for (CosmeticPet pet : petManager.getOwnedPets(player.getUniqueId())) {
                if (pet.getType() == type && pet.getCustomName().equals(customName)) {
                    if (event.isRightClick()) {
                        SoundUtil.click(player);
                        coordinator.openSkinSelection(player, pet);
                    } else if (event.isShiftClick()) {
                        SoundUtil.click(player);
                        player.sendMessage("§aPreviewing §d" + pet.getCustomName() + " §afor 8 seconds...");
                        coordinator.spawnTemporaryPreview(player, pet);
                    } else {
                        petManager.setActivePet(player, pet);
                        player.sendMessage("§aEquipped: §d" + pet.getCustomName());
                        SoundUtil.click(player);
                        open(player);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }
}