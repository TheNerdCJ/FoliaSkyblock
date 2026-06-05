package com.thenerdcj.tags;

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

import java.util.Set;

/**
 * Cosmetic tags list (BaseGUI). Opened via {@link TagGUI#open(Player)}.
 */
public class TagMainGUI extends BaseGUI {

    public TagMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    @Override
    protected String getTitlePrefix() {
        return "§5§lCosmetic Tags";
    }

    @Override
    protected String getActionKeyName() {
        return "tag_action";
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
        var tagManager = plugin.getPlayerTagManager();
        if (tagManager == null) {
            gui.setItem(4, GUIUtils.createItem(Material.BARRIER, "§cTags system unavailable"));
            return;
        }
        int collCount = tagManager.getTagCollectionCount(player.getUniqueId());
        gui.setItem(4, GUIUtils.createItem(Material.NAME_TAG, "§5§lYour Cosmetic Tags",
                "§7Appear in chat and tab list",
                "§7Collection: §a" + collCount + " §7unique tags"));
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        var tagManager = plugin.getPlayerTagManager();
        if (tagManager == null) {
            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cTags system unavailable"));
            return;
        }

        Set<TagInstance> owned = tagManager.getOwnedTags(player.getUniqueId());
        PlayerTag active = tagManager.getActiveTag(player.getUniqueId());

        if (owned.isEmpty() || (owned.size() == 1 && owned.contains(PlayerTag.NONE))) {
            gui.setItem(22, GUIUtils.createItem(Material.BARRIER, "§cNo tags owned yet",
                    "§7Earn tags through prestige, slayer tokens,",
                    "§7and collection milestones."));
            return;
        }

        int slot = 18;
        for (TagInstance inst : owned) {
            PlayerTag tag = inst.getType();
            if (slot > 44) break;
            if (tag.isNone()) continue;

            boolean isActive = tag == active;
            ItemStack item = GUIUtils.createItem(Material.NAME_TAG,
                    (isActive ? "§a§l★ " : "§e") + tag.getTagText() + " §f" + tag.getDisplayName(),
                    "§7Rarity: " + tag.getRarity().getColorCode() + tag.getRarity().getDisplayName(),
                    "§7" + tag.getDescription(),
                    "",
                    isActive ? "§a§lCurrently Active" : "§eLeft-click §7to equip",
                    "§7Right-click §7for info");

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "SELECT_" + tag.name());
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
    }

    @Override
    protected void addStandardNavigation(Inventory gui, int page, int totalPages) {
        gui.setItem(45, GUIUtils.createNavButton(Material.ARROW, "§e§lBack to Wardrobe", ACTION_KEY, "BACK_TO_WARDROBE"));
        gui.setItem(49, GUIUtils.createNavButton(Material.BARRIER, "§c§lRemove Active Tag", ACTION_KEY, "REMOVE_ACTIVE"));
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        var tagManager = plugin.getPlayerTagManager();
        if (tagManager == null) return;

        if ("REMOVE_ACTIVE".equals(action)) {
            tagManager.setActiveTag(player, PlayerTag.NONE);
            player.sendMessage("§7Cosmetic tag cleared.");
            SoundUtil.click(player);
            open(player);
            return;
        }
        if ("BACK_TO_WARDROBE".equals(action)) {
            SoundUtil.click(player);
            player.closeInventory();
            plugin.getWardrobeGUI().openWardrobe(player, com.thenerdcj.wardrobe.WardrobeGUI.View.TAGS);
        }
    }

    @EventHandler
    public void onTagMainClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(getTitlePrefix())) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        if ("REMOVE_ACTIVE".equals(action) || "BACK_TO_WARDROBE".equals(action)) {
            handleAction(action, meta.getPersistentDataContainer(), player, 0, clicked);
            return;
        }

        if (!action.startsWith("SELECT_")) return;

        var tagManager = plugin.getPlayerTagManager();
        if (tagManager == null) return;

        try {
            PlayerTag tag = PlayerTag.valueOf(action.substring(7));
            if (event.isRightClick()) {
                SoundUtil.click(player);
                player.sendMessage("§e" + tag.getTagText() + " §f" + tag.getDisplayName());
                player.sendMessage("§7" + tag.getDescription());
                player.sendMessage("§7Rarity: " + tag.getRarity().getColorCode() + tag.getRarity().getDisplayName());
            } else {
                tagManager.setActiveTag(player, tag);
                SoundUtil.click(player);
                open(player);
            }
        } catch (Exception ignored) {
        }
    }
}