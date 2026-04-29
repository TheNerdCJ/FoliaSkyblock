package com.thenerdcj.island;

import org.bukkit.inventory.ItemStack;

public record Trade(
        ItemStack input,
        ItemStack output,
        int levelRequired,
        String description
) {
    public boolean canAfford(ItemStack playerItem) {
        return playerItem != null &&
               playerItem.getType() == input.getType() &&
               playerItem.getAmount() >= input.getAmount();
    }
}