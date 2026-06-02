package com.thenerdcj.pets;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a single cosmetic pet instance owned by a player.
 * Supports variants (colors / special editions) for differentiation.
 */
public class CosmeticPet {

    private final PetType type;
    private String customName;
    private PetVariant variant;
    private PetSkin skin;

    public CosmeticPet(PetType type, String customName) {
        this(type, customName, PetVariant.NONE, PetSkin.NONE);
    }

    public CosmeticPet(PetType type, String customName, PetVariant variant) {
        this(type, customName, variant, PetSkin.NONE);
    }

    public CosmeticPet(PetType type, String customName, PetVariant variant, PetSkin skin) {
        this.type = type;
        this.customName = customName != null ? customName : type.getDisplayName();
        this.variant = variant != null ? variant : PetVariant.NONE;
        this.skin = skin != null ? skin : PetSkin.NONE;
    }

    public PetType getType() {
        return type;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public PetVariant getVariant() {
        return variant;
    }

    public void setVariant(PetVariant variant) {
        this.variant = variant != null ? variant : PetVariant.NONE;
    }

    public PetSkin getSkin() {
        return skin;
    }

    public void setSkin(PetSkin skin) {
        this.skin = skin != null ? skin : PetSkin.NONE;
    }

    public ItemStack createDisplayItem() {
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String namePrefix = (variant != PetVariant.NONE) ? variant.getColorCode() + "[" + variant.getDisplayName() + "] " : "";
            meta.setDisplayName("§d" + namePrefix + customName);
            PetRarity r = type.getRarity();
            List<String> lore = new ArrayList<>();
            lore.add("§7Rarity: " + r.getColorCode() + r.getDisplayName());
            if (variant != PetVariant.NONE) {
                lore.add("§7Variant: " + variant.getColoredName());
            }
            if (skin != PetSkin.NONE) {
                lore.add("§7Skin: " + skin.getRarity().getColorCode() + skin.getDisplayName());
            }
            lore.add("§7Type: §e" + type.getDisplayName());
            lore.add("§7" + type.getDescription());
            lore.add("");
            lore.add("§aLeft-click §7to equip this pet");
            lore.add("§7Right-click §7for options / skins");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
