package com.thenerdcj.auction;
import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.gui.GUIUtils;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Enhanced AuctionGUI with pagination, custom InventoryHolder,
 * PersistentDataContainer for reliable clicks, and confirmation dialogs.
 *
 * Modernized with GUIUtils + MessageUtil.legacy for item creation and titles.
 */
public class
AuctionGUI implements Listener {

    private final FoliaSkyblock plugin;
    private final AuctionManager auctionManager;
    private final Map<UUID, AuctionGUIHolder> openGUIs = new HashMap<>();
    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey AUCTION_ID_KEY;
    private final NamespacedKey PAGE_KEY;

    public AuctionGUI(FoliaSkyblock plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
        this.ACTION_KEY = new NamespacedKey(plugin, "auction_action");
        this.AUCTION_ID_KEY = new NamespacedKey(plugin, "auction_id");
        this.PAGE_KEY = new NamespacedKey(plugin, "auction_page");
        // Self-registration removed to prevent double registration (now done centrally in FoliaSkyblock)
    }

    public static class AuctionGUIHolder implements InventoryHolder {
        private final int currentPage;
        private final String context; // "browse", "my_auctions", "confirm_bid", etc.
        private final String pendingAuctionId;

        public AuctionGUIHolder(int currentPage, String context, String pendingAuctionId) {
            this.currentPage = currentPage;
            this.context = context;
            this.pendingAuctionId = pendingAuctionId;
        }

        public int getCurrentPage() { return currentPage; }
        public String getContext() { return context; }
        public String getPendingAuctionId() { return pendingAuctionId; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public void openAuctionBrowser(Player player) {
        openAuctionBrowser(player, 0);
    }

    public void openAuctionBrowser(Player player, int page) {
        Map<String, Auction> auctions = auctionManager.getActiveAuctions();
        List<Auction> auctionList = new ArrayList<>(auctions.values());

        int itemsPerPage = 36; // leave space for header at top for better UX
        int totalPages = (int) Math.ceil((double) auctionList.size() / itemsPerPage);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        Inventory gui = Bukkit.createInventory(
                new AuctionGUIHolder(page, "browse", null),
                54,
                MessageUtil.legacy("§6§lAuction House §7- Page " + (page + 1) + "/" + Math.max(1, totalPages) + " | Use arrows to browse, click to bid")
        );

        // Header info always visible for user friendly design
        gui.setItem(4, GUIUtils.createItem(Material.BOOK, "§6§lHow to Use Auction House",
            "§7Browse player auctions",
            "§eLeft-click item = place bid",
            "§7Use arrows for pages",
            "§7Create your own with /auction create"));

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, auctionList.size());

        // Place items starting after header (slot 9+) for clean header + list UX
        int slot = 9;
        for (int i = start; i < end && slot < 45; i++) {
            addAuctionItem(gui, slot, auctionList.get(i));
            slot++;
            if (slot % 9 == 0) slot += 0; // continue row
        }

        // Fill remaining content slots (9-44) with glass
        for (int i = 9; i < 45; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, createGlassPane());
            }
        }

        if (page > 0) gui.setItem(45, createNavButton("§a§l« Previous Page", "prev", page));
        if (page < totalPages - 1) gui.setItem(53, createNavButton("§a§lNext Page »", "next", page));
        gui.setItem(49, createCloseButton());

        // Info/help for user friendly navigation (best practice from popular GUIs)
        gui.setItem(4, GUIUtils.createItem(Material.BOOK, "§6§lHow to Use Auction House",
            "§7Browse player auctions",
            "§eLeft-click item = place bid",
            "§7Use arrows for pages",
            "§7Create your own with /auction create"));

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), (AuctionGUIHolder) gui.getHolder());
    }

    private void addAuctionItem(Inventory gui, int slot, Auction auction) {
        ItemStack item = GUIUtils.createItem(
                Material.valueOf(auction.getItemMaterial()), 
                "§e" + auction.getItemAmount() + "x " + auction.getItemMaterial()
        );
        item.setAmount(auction.getItemAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Current Bid: §a$" + String.format("%,.0f", auction.getCurrentBid()));
            lore.add("§7Starting Price: §7$" + String.format("%,.0f", auction.getStartingPrice()));
            if (auction.getCurrentBidder() != null) {
                lore.add("§7Highest Bidder: §bSomeone");
            }
            lore.add("§7Time Left: §e" + formatTime(auction.getTimeRemaining()));
            lore.add("");
            lore.add("§aLeft-click to §bBID §aon this item");
            meta.setLore(lore);

            // Use helper for common PDC attachment
            attachAuctionPDC(meta, "bid", auction.getId());

            item.setItemMeta(meta);
        }
        gui.setItem(slot, item);
    }

    private void attachAuctionPDC(ItemMeta meta, String action, String auctionId) {
        if (meta != null) {
            GUIUtils.setPDCString(meta, ACTION_KEY, action);
            if (auctionId != null) {
                GUIUtils.setPDCString(meta, AUCTION_ID_KEY, auctionId);
            }
        }
    }

    private String formatTime(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis % (1000 * 60 * 60)) / (1000 * 60);
        return hours + "h " + minutes + "m";
    }

    private ItemStack createNavButton(String name, String action, int currentPage) {
        ItemStack item = GUIUtils.createItem(Material.ARROW, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GUIUtils.setPDCString(meta, ACTION_KEY, action);
            meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, currentPage);
            meta.setLore(Arrays.asList("§7Click to change page", "§8Current page in title"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlassPane() {
        return GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private ItemStack createCloseButton() {
        ItemStack item = GUIUtils.createItem(Material.BARRIER, "§c§lClose");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GUIUtils.setPDCString(meta, ACTION_KEY, "close");
            meta.setLore(Arrays.asList(
                "§7Close the auction browser",
                "§eCreate auctions with /auction create",
                "§7Bid by left-clicking items"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== Auction-specific helpers (for consistency with modernized GUIs) ====================

    private ItemStack createAuctionActionButton(Material material, String name, String action, String auctionId) {
        ItemStack item = GUIUtils.createItem(material, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GUIUtils.setPDCString(meta, ACTION_KEY, action);
            if (auctionId != null) {
                GUIUtils.setPDCString(meta, AUCTION_ID_KEY, auctionId);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // Simple bid confirmation (can be expanded with amount input via Anvil or chat)
    private void openBidConfirmation(Player player, String auctionId) {
        Auction auction = auctionManager.getAuction(auctionId);
        if (auction == null) return;

        Inventory confirm = Bukkit.createInventory(
                new AuctionGUIHolder(0, "confirm_bid", auctionId),
                27,
                MessageUtil.legacy("§6§lPlace Bid?")
        );

        ItemStack info = GUIUtils.createItem(Material.valueOf(auction.getItemMaterial()), "§eBid on " + auction.getItemMaterial());
        info.setAmount(auction.getItemAmount());
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setLore(java.util.Arrays.asList(
                "§7Current Bid: §a$" + String.format("%,.0f", auction.getCurrentBid()),
                "§7Your bid must be higher!"
            ));
            attachAuctionPDC(infoMeta, "info", auctionId);
            info.setItemMeta(infoMeta);
        }
        confirm.setItem(13, info);

        double suggestedBid = auction.getCurrentBid() + Math.max(10, auction.getCurrentBid() * 0.1);

        confirm.setItem(11, createAuctionActionButton(Material.GREEN_WOOL, "§a§lBID §e$" + String.format("%,.0f", suggestedBid), "confirm_bid", auctionId));
        confirm.setItem(15, createAuctionActionButton(Material.RED_WOOL, "§c§lCANCEL", "cancel", null));

        player.openInventory(confirm);
        openGUIs.put(player.getUniqueId(), (AuctionGUIHolder) confirm.getHolder());
    }

    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AuctionGUIHolder guiHolder)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        String auctionId = pdc.get(AUCTION_ID_KEY, PersistentDataType.STRING);
        Integer page = pdc.get(PAGE_KEY, PersistentDataType.INTEGER);

        switch (action) {
            case "close":
            case "cancel":
                player.closeInventory();
                openGUIs.remove(player.getUniqueId());
                break;
            case "prev":
                int p = (page != null ? page : guiHolder.getCurrentPage()) - 1;
                player.closeInventory();
                openAuctionBrowser(player, Math.max(0, p));
                break;
            case "next":
                int n = (page != null ? page : guiHolder.getCurrentPage()) + 1;
                player.closeInventory();
                openAuctionBrowser(player, n);
                break;
            case "bid":
                if (auctionId != null) {
                    player.closeInventory();
                    openBidConfirmation(player, auctionId);
                }
                break;
            case "confirm_bid":
                if (auctionId != null) {
                    player.closeInventory();
                    // In production, get actual bid amount from player input or config
                    double suggestedBid = auctionManager.getAuction(auctionId).getCurrentBid() + 10;
                    auctionManager.placeBid(player, auctionId, suggestedBid);
                    openGUIs.remove(player.getUniqueId());
                }
                break;
        }
    }

    // Call this from AuctionCommand or similar
    public void open(Player player) {
        openAuctionBrowser(player);
    }
}
