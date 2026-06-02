package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Abstract base class for all FoliaSkyblock GUIs.
 * Provides consistent styling, filler glass, button helpers, and click handling.
 * All new GUIs should extend this class for maintainability and clean UX.
 *
 * @deprecated Legacy base. Modern GUIs use BaseGUI (with GUIUtils + MessageUtil.legacy + PDC ACTION_KEY + resilient startsWith guards).
 * AbstractGUI retained for any remaining references; its createItem/createButton are @Deprecated.
 *
 * Production notes:
 * - Uses Folia-safe practices (no blocking operations in click handlers).
 * - Click actions should be registered via a central listener or per-GUI map if complex.
 */
public abstract class AbstractGUI implements InventoryHolder {

    protected final FoliaSkyblock plugin;
    protected final Player player;
    protected Inventory inventory;

    public AbstractGUI(FoliaSkyblock plugin, Player player, String title, int size) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, size, MessageUtil.legacy(title));
    }

    /**
     * Opens the GUI for the player. Must be called on the main thread.
     */
    public abstract void open();

    /**
     * Fills empty slots with gray stained glass panes for a clean look.
     */
    protected void fillWithGlass() {
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
                inventory.setItem(i, glass);
            }
        }
    }

    /**
     * Creates a clean ItemStack with name and optional lore.
     * @deprecated Use GUIUtils.createItem (or GUIUtils.createNavButton for PDC actions) + MessageUtil.legacy for titles.
     * All modern GUIs should extend BaseGUI (or implement Listener + use BaseGUI patterns) for PDC, pagination,
     * resilient title guards (startsWith), SoundUtil, and consistent Adventure-ready item construction.
     * AbstractGUI is legacy and retained only for backward compatibility during transition.
     */
    @Deprecated
    protected ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore.length > 0) {
                List<String> coloredLore = Arrays.stream(lore)
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .toList();
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Helper to create a button (same as createItem for now, but can be extended
     * with action registration in a full implementation).
     * @deprecated Migrate to GUIUtils + BaseGUI patterns.
     */
    @Deprecated
    protected ItemStack createButton(Material material, String name, String... lore) {
        return createItem(material, name, lore);
    }

    protected void setItem(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Example click handler pattern. Override in subclasses and register listener.
     */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        // Subclasses implement specific logic here or via command map
    }
}
