package com.thenerdcj.testutil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.mockito.Mockito.*;

/**
 * Aggressive MockBukkit GUI simulation helper.
 *
 * Uses reflection (like TestBase) to work across different MockBukkit versions.
 * Attempts real ServerMock + PlayerMock inventory/click simulation when possible.
 */
public class MockBukkitGuiSimulator {

    private Object server;
    private boolean mockBukkitActive;

    public MockBukkitGuiSimulator() {
        // ROBUSTNESS RULE: The simulator NEVER starts its own MockBukkit instance.
        // Only TestBase.addPlayer() / ensureMockBukkitActive() (called from dedicated integration tests)
        // are allowed to start a MockBukkit server. This prevents cross-test pollution and
        // Registry explosions in the hundreds of normal unit/GUI tests that only want stable mocks.
        //
        // The simulator only "attaches" to a server that is already running in the current JVM
        // (started by the owning TestBase instance for this test).
        refreshFromGlobalServer();
    }

    /**
     * Re-detects whether a MockBukkit server is currently active in this JVM.
     * Called on construction and can be called again after a test has done addPlayer().
     */
    public void refreshFromGlobalServer() {
        this.mockBukkitActive = false;
        this.server = null;
        try {
            Object current = Bukkit.getServer();
            if (current != null && current.getClass().getName().contains("mockbukkit")) {
                this.server = current;
                this.mockBukkitActive = true;
            }
        } catch (Throwable ignored) {}
    }

    public boolean isMockBukkitActive() {
        return mockBukkitActive && server != null;
    }

    public Object getServer() {
        return server;
    }

    public Player createPlayer(String name) {
        if (!isMockBukkitActive()) return null;
        try {
            try {
                Method addPlayer = server.getClass().getMethod("addPlayer", String.class);
                return (Player) addPlayer.invoke(server, name);
            } catch (NoSuchMethodException nsme) {
                Object p = server.getClass().getMethod("addPlayer").invoke(server);
                return (Player) p;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a realistic InventoryClickEvent for GUI testing.
     *
     * When MockBukkit is active, tries to use real inventory objects.
     * Always sets up the PersistentDataContainer on the clicked item.
     */
    public InventoryClickEvent createClickEvent(
            Player player,
            Inventory topInventory,
            int slot,
            ClickType clickType,
            NamespacedKey key,
            String actionValue
    ) {
        InventoryView view = createInventoryView(player, topInventory);
        ItemStack clickedItem = createItemWithPdc(key, actionValue);

        InventoryClickEvent event = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                slot,
                clickType,
                InventoryAction.PICKUP_ALL
        );

        try {
            Field currentItemField = InventoryClickEvent.class.getDeclaredField("currentItem");
            currentItemField.setAccessible(true);
            currentItemField.set(event, clickedItem);
        } catch (Exception ignored) {}

        return event;
    }

    private InventoryView createInventoryView(Player player, Inventory topInventory) {
        // ULTRA-DEFENSIVE version: avoid touching ANY Bukkit enum / type that has
        // a heavy static initializer (InventoryType, Registry, etc.) unless we have
        // a confirmed healthy MockBukkit server. This is the main source of
        // "NoClassDefFound Could not initialize class org.bukkit...." errors
        // that were making the suite fragile across different dev/CI machines.
        InventoryView view = mock(InventoryView.class, withSettings().lenient());

        org.bukkit.inventory.Inventory safeTop = (topInventory != null)
                ? topInventory
                : mock(Inventory.class, withSettings().lenient());
        doReturn(safeTop).when(view).getTopInventory();

        org.bukkit.inventory.PlayerInventory safeBottom = mock(org.bukkit.inventory.PlayerInventory.class, withSettings().lenient());
        doReturn(safeBottom).when(view).getBottomInventory();

        doReturn((HumanEntity) player).when(view).getPlayer();
        doReturn("§6§lSimulated GUI").when(view).getTitle();
        // Intentionally do NOT stub getType() here with a real InventoryType constant.

        // Only when we are inside a dedicated aggressive MB integration test do we
        // risk touching the real enums and real ServerMock inventory factories.
        if (isMockBukkitActive() && server != null && player != null &&
            player.getClass().getName().toLowerCase().contains("mockbukkit")) {
            try {
                doReturn(InventoryType.CHEST).when(view).getType();
                Method createInv = server.getClass().getMethod("createInventory", org.bukkit.inventory.InventoryHolder.class, int.class, String.class);
                Inventory realInv = (Inventory) createInv.invoke(server, null, 54, "Simulated");
                doReturn(realInv).when(view).getTopInventory();
            } catch (Throwable ignored) {}
        }

        return view;
    }

    private ItemStack createItemWithPdc(NamespacedKey key, String value) {
        try {
            ItemStack item = com.thenerdcj.TestBase.createSafeItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Some safe mocks return a meta whose PDC is also a mock; this is fine for tests
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
                item.setItemMeta(meta);
            }
            return item;
        } catch (Throwable t) {
            return mockItemWithPdc(key, value);
        }
    }

    private ItemStack mockItemWithPdc(NamespacedKey key, String value) {
        ItemStack mockItem = mock(ItemStack.class);
        ItemMeta mockMeta = mock(ItemMeta.class);
        PersistentDataContainer mockPdc = mock(PersistentDataContainer.class);

        when(mockMeta.getPersistentDataContainer()).thenReturn(mockPdc);
        when(mockPdc.get(eq(key), eq(PersistentDataType.STRING))).thenReturn(value);
        when(mockItem.getItemMeta()).thenReturn(mockMeta);
        when(mockItem.getType()).thenReturn(Material.PAPER);

        return mockItem;
    }

    /**
     * Convenience method for typical PDC-based GUI clicks (most common pattern in this plugin).
     */
    public InventoryClickEvent createPdcClick(
            Player player,
            Inventory inventory,
            int slot,
            ClickType clickType,
            String actionValue
    ) {
        NamespacedKey key = com.thenerdcj.TestBase.createSafeNamespacedKey("action");
        return createClickEvent(player, inventory, slot, clickType, key, actionValue);
    }

    /**
     * Creates a click event using a specific NamespacedKey (recommended).
     */
    public InventoryClickEvent createPdcClick(
            Player player,
            Inventory inventory,
            int slot,
            ClickType clickType,
            NamespacedKey key,
            String actionValue
    ) {
        return createClickEvent(player, inventory, slot, clickType, key, actionValue);
    }

    /**
     * Powerful helper for complex GUIs (Bazaar, Auction confirmations, etc.) that put
     * multiple values into the same item's PersistentDataContainer (e.g. action + material + orderId).
     *
     * Returns a real(ish) InventoryClickEvent whose currentItem has all the requested PDC entries.
     */
    public InventoryClickEvent createComplexPdcClick(
            Player player,
            Inventory inventory,
            int slot,
            ClickType clickType,
            java.util.Map<NamespacedKey, String> pdcValues
    ) {
        InventoryView view = createInventoryView(player, inventory);
        ItemStack clickedItem = createItemWithMultiplePdc(pdcValues);

        InventoryClickEvent event = new InventoryClickEvent(
                view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                slot,
                clickType,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );

        try {
            java.lang.reflect.Field currentItemField = InventoryClickEvent.class.getDeclaredField("currentItem");
            currentItemField.setAccessible(true);
            currentItemField.set(event, clickedItem);
        } catch (Exception ignored) {}

        return event;
    }

    private ItemStack createItemWithMultiplePdc(java.util.Map<NamespacedKey, String> values) {
        try {
            ItemStack item = com.thenerdcj.TestBase.createSafeItemStack(org.bukkit.Material.PAPER);
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                for (java.util.Map.Entry<NamespacedKey, String> e : values.entrySet()) {
                    meta.getPersistentDataContainer().set(e.getKey(), PersistentDataType.STRING, e.getValue());
                }
                item.setItemMeta(meta);
            }
            return item;
        } catch (Throwable t) {
            return mockComplexPdcItem(values);
        }
    }

    private ItemStack mockComplexPdcItem(java.util.Map<NamespacedKey, String> values) {
        ItemStack mockItem = mock(ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta mockMeta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        PersistentDataContainer mockPdc = mock(PersistentDataContainer.class);

        for (java.util.Map.Entry<NamespacedKey, String> e : values.entrySet()) {
            when(mockPdc.get(eq(e.getKey()), eq(PersistentDataType.STRING))).thenReturn(e.getValue());
        }
        when(mockMeta.getPersistentDataContainer()).thenReturn(mockPdc);
        when(mockItem.getItemMeta()).thenReturn(mockMeta);
        when(mockItem.getType()).thenReturn(org.bukkit.Material.PAPER);
        return mockItem;
    }

    /**
     * Opens a GUI on a PlayerMock and returns the view when possible.
     */
    public InventoryView openGuiOnPlayer(Player player, Inventory guiInventory) {
        if (!isMockBukkitActive() || player == null) {
            return createInventoryView(player, guiInventory);
        }
        try {
            // Some PlayerMock implementations support opening an inventory directly
            if (guiInventory != null) {
                try {
                    player.openInventory(guiInventory);
                } catch (Throwable ignored) {}
            }
            return player.getOpenInventory();
        } catch (Throwable t) {
            return createInventoryView(player, guiInventory);
        }
    }

    public void cleanup() {
        if (mockBukkitActive) {
            try {
                Class<?> mb = Class.forName("be.seeseemelk.mockbukkit.MockBukkit");
                mb.getMethod("unmock").invoke(null);
            } catch (Exception ignored) {}
        }
    }
}