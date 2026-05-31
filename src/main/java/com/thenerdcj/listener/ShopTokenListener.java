package com.thenerdcj.listener;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.booster.BoosterType;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.IslandShopManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles right-click redemption of physical shop tokens purchased from the Island Shop.
 * Supports BOOSTER, and can be extended for other redeemable effects.
 */
public class ShopTokenListener implements Listener {

    private final FoliaSkyblock plugin;
    private final NamespacedKey TOKEN_KEY;
    private final NamespacedKey TOKEN_TYPE_KEY;

    public ShopTokenListener(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.TOKEN_KEY = new NamespacedKey(plugin, "shop_token");
        this.TOKEN_TYPE_KEY = new NamespacedKey(plugin, "shop_token_type");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasItem()) return;
        if (event.getAction().isLeftClick()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String tokenId = pdc.get(TOKEN_KEY, PersistentDataType.STRING);
        String tokenType = pdc.get(TOKEN_TYPE_KEY, PersistentDataType.STRING);

        if (tokenId == null || !"REDEEM".equals(tokenType)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cYou need an island to redeem this token.");
            return;
        }

        IslandShopManager manager = plugin.getIslandShopManager();
        IslandShopManager.ShopItem shopItem = manager.getItem(tokenId);
        if (shopItem == null) {
            player.sendMessage("§cThis token is no longer valid.");
            return;
        }

        // Consume one token
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Redeem based on reward type
        String rewardType = shopItem.rewardType();
        if ("BOOSTER".equals(rewardType)) {
            redeemBooster(player, island, shopItem);
        } else {
            // Fallback to immediate grant logic
            // For simplicity we reuse the same idea as GUI
            player.sendMessage("§aToken redeemed: " + shopItem.name());
            // Could delegate to manager for more complex rewards in future
        }
    }

    private void redeemBooster(Player player, Island island, IslandShopManager.ShopItem item) {
        var data = item.rewardData();
        String typeName = (String) data.getOrDefault("type", "CROP_GROWTH");
        double mult = ((Number) data.getOrDefault("multiplier", 2.0)).doubleValue();
        int minutes = ((Number) data.getOrDefault("duration_minutes", 30)).intValue();

        try {
            BoosterType type = BoosterType.valueOf(typeName);
            long millis = (long) minutes * 60 * 1000;

            plugin.getBoosterManager().activateBooster(island, type, mult, millis);
            player.sendMessage("§aRedeemed §e" + item.name() + "§a! §7(" + minutes + "m " + type.getDisplayName() + " " + mult + "x)");
        } catch (Exception e) {
            player.sendMessage("§cFailed to redeem booster token: invalid type.");
            plugin.getLogger().warning("[Shop] Bad booster token data for " + item.id());
        }
    }
}