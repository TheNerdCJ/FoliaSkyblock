package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.CosmeticPickerGUI;
import com.thenerdcj.gui.GUIUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/**
 * Death effect picker. Opened via {@link DeathEffectGUI#open(Player)}.
 */
public class DeathEffectMainGUI extends CosmeticPickerGUI {

    public DeathEffectMainGUI(FoliaSkyblock plugin) {
        super(plugin);
    }

    @Override
    protected String getTitlePrefix() {
        return "§4§lDeath Effects";
    }

    @Override
    protected String getActionKeyName() {
        return "death_effect_action";
    }

    @Override
    protected Material getHeaderIcon() {
        return Material.SKELETON_SKULL;
    }

    @Override
    protected String[] getHeaderLore(Player player) {
        DeathEffectManager manager = plugin.getDeathEffectManager();
        int owned = manager != null ? manager.getOwnedEffects(player.getUniqueId()).size() : 0;
        return new String[]{
                "§7Effects on your death and when you kill",
                "§7Collection: §a" + owned + " / " + (DeathEffect.values().length - 1) + " §7effects"
        };
    }

    @Override
    protected String getRemoveButtonLabel() {
        return "§cRemove Effect";
    }

    @Override
    protected String getNoneActionId() {
        return "DEATH_EFFECT_NONE";
    }

    @Override
    protected boolean isSystemAvailable(Player player) {
        if (plugin.getDeathEffectManager() == null) {
            player.sendMessage("§cDeath Effects system is not available.");
            return false;
        }
        return true;
    }

    @Override
    protected void populateSkinGrid(Inventory gui, Player player) {
        DeathEffectManager manager = plugin.getDeathEffectManager();
        Set<DeathEffect> owned = manager.getOwnedEffects(player.getUniqueId());
        DeathEffect current = manager.getActiveDeathEffect(player.getUniqueId());

        int slot = 19;
        for (DeathEffect effect : DeathEffect.values()) {
            if (slot > 44) break;
            if (effect.isNone()) continue;

            boolean isOwned = owned.contains(effect);
            boolean isCurrent = effect == current;

            ItemStack item = GUIUtils.createItem(Material.SKELETON_SKULL,
                    (isCurrent ? "§a§l★ " : isOwned ? "§e" : "§7") + effect.getRarity().getColorCode() + effect.getDisplayName(),
                    "§7" + effect.getDescription(),
                    "§7Rarity: " + effect.getRarity().getColorCode() + effect.getRarity().getDisplayName(),
                    isOwned ? "" : "§cLocked - Unlock via prestige or Slayer Shop",
                    isCurrent ? "§aCurrently Active" : isOwned ? "§eClick to equip" : "§7Preview only");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "DEATH_EFFECT_" + effect.name());
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void handlePickerAction(String action, Player player) {
        DeathEffectManager manager = plugin.getDeathEffectManager();
        if (manager == null) return;

        if ("DEATH_EFFECT_NONE".equals(action)) {
            manager.setActiveDeathEffect(player, DeathEffect.NONE);
            return;
        }
        if (action != null && action.startsWith("DEATH_EFFECT_")) {
            try {
                DeathEffect effect = DeathEffect.valueOf(action.substring(13));
                if (!manager.hasEffect(player.getUniqueId(), effect) && !effect.isNone()) {
                    player.sendMessage("§cYou have not unlocked this death effect.");
                    return;
                }
                manager.setActiveDeathEffect(player, effect);
                open(player);
            } catch (Exception ignored) {
                player.sendMessage("§cInvalid death effect.");
            }
        }
    }
}