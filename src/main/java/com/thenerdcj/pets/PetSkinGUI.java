package com.thenerdcj.pets;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.BaseGUI;
import com.thenerdcj.gui.GUIUtils;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-pet skin picker (BaseGUI).
 */
public class PetSkinGUI extends BaseGUI {

    private final PetGUI coordinator;
    private final Map<UUID, String> targetPetByPlayer = new HashMap<>();

    public PetSkinGUI(FoliaSkyblock plugin, PetGUI coordinator) {
        super(plugin, true);
        this.coordinator = coordinator;
    }

    public void open(Player player, CosmeticPet pet) {
        targetPetByPlayer.put(player.getUniqueId(), pet.getCustomName());
        playerPages.put(player.getUniqueId(), 0);

        String title = getTitlePrefix() + pet.getCustomName();
        Inventory gui = Bukkit.createInventory(null, INVENTORY_SIZE, MessageUtil.legacy(title));
        populatePage(gui, player, 0);
        addStandardNavigation(gui, 0, 1);
        player.openInventory(gui);
    }

    @Override
    protected String getTitlePrefix() {
        return "§d§lSkins - ";
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

    private CosmeticPet resolvePet(Player player) {
        String name = targetPetByPlayer.get(player.getUniqueId());
        if (name == null) return null;
        for (CosmeticPet p : plugin.getPetManager().getOwnedPets(player.getUniqueId())) {
            if (p.getCustomName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        CosmeticPet pet = resolvePet(player);
        if (pet == null) {
            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cPet not found"));
            return;
        }

        var petManager = plugin.getPetManager();
        Set<PetSkin> ownedSkins = petManager.getOwnedSkins(player.getUniqueId());

        gui.setItem(4, GUIUtils.createItem(Material.NAME_TAG, "§d§lChoose Skin for " + pet.getCustomName(),
                "§7Current: " + (pet.getSkin() != null && !pet.getSkin().isNone()
                ? pet.getSkin().getRarity().getColorCode() + pet.getSkin().getDisplayName() : "§7None")));

        ItemStack noneItem = GUIUtils.createNavButton(Material.BARRIER, "§cRemove Skin", ACTION_KEY,
                "SKIN_NONE_" + pet.getCustomName().replace(" ", "_"));
        gui.setItem(18, noneItem);

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

            var skinMeta = item.getItemMeta();
            if (skinMeta != null) {
                skinMeta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                        "SKIN_" + skin.name() + "_" + pet.getCustomName().replace(" ", "_"));
                item.setItemMeta(skinMeta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void addStandardNavigation(Inventory gui, int page, int totalPages) {
        gui.setItem(45, GUIUtils.createNavButton(Material.ARROW, "§e§lBack to Pets", ACTION_KEY, "BACK_TO_PETS"));
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        if ("BACK_TO_PETS".equals(action)) {
            SoundUtil.click(player);
            player.closeInventory();
            coordinator.open(player);
            return;
        }

        if (action == null || !action.startsWith("SKIN_")) return;

        String data = action.substring(5);
        String[] parts = data.split("_", 2);
        if (parts.length < 2) return;

        String skinName = parts[0];
        String petName = parts[1].replace("_", " ");

        try {
            PetSkin skin = "NONE".equals(skinName) ? PetSkin.NONE : PetSkin.valueOf(skinName);
            boolean success = plugin.getPetManager().applySkinToPet(player.getUniqueId(), petName, skin);
            if (success) {
                player.sendMessage("§aSkin applied: " + (skin.isNone() ? "§7None"
                        : skin.getRarity().getColorCode() + skin.getDisplayName()));
            }
            SoundUtil.click(player);

            for (CosmeticPet p : plugin.getPetManager().getOwnedPets(player.getUniqueId())) {
                if (p.getCustomName().equals(petName)) {
                    open(player, p);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }
}