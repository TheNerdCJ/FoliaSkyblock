package com.thenerdcj.auction;
import com.thenerdcj.FoliaSkyblock;
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
        Bukkit.getPluginManager().registerEvents(this, plugin);
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

        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil((double) auctionList.size() / itemsPerPage);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        Inventory gui = Bukkit.createInventory(
                new AuctionGUIHolder(page, "browse", null),
                54,
                "§6§lAuction House §7(Page " + (page + 1) + "/" + Math.max(1, totalPages) + ")"
        );

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, auctionList.size());

        for (int i = start; i < end; i++) {
            addAuctionItem(gui, i - start, auctionList.get(i));
        }

        for (int i = end - start; i < 45; i++) {
            gui.setItem(i, createGlassPane());
        }

        if (page > 0) gui.setItem(45, createNavButton("§a§lPrevious", "prev", page));
        if (page < totalPages - 1) gui.setItem(53, createNavButton("§a§lNext", "next", page));
        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), (AuctionGUIHolder) gui.getHolder());
    }

    private void addAuctionItem(Inventory gui, int slot, Auction auction) {
        ItemStack item = new ItemStack(Material.valueOf(auction.getItemMaterial()), auction.getItemAmount());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + auction.getItemAmount() + "x " + auction.getItemMaterial());

        List<String> lore = new ArrayList<>();
        lore.add("§7Current Bid: §a$" + String.format("%,.0f", auction.getCurrentBid()));
        lore.add("§7Starting Price: §7$" + String.format("%,.0f", auction.getStartingPrice()));
        if (auction.getCurrentBidder() != null) {
            lore.add("§7Highest Bidder: §bSomeone");
        }
        lore.add("§7Time Left: §e" + formatTime(auction.getTimeRemaining()));
        lore.add("");
        lore.add("§aLeft-click to §bBID §aon this item");
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "bid");
        pdc.set(AUCTION_ID_KEY, PersistentDataType.STRING, auction.getId());

        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }

    private String formatTime(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis % (1000 * 60 * 60)) / (1000 * 60);
        return hours + "h " + minutes + "m";
    }

    private ItemStack createNavButton(String name, String action, int currentPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, action);
        pdc.set(PAGE_KEY, PersistentDataType.INTEGER, currentPage);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlassPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§lClose");
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "close");
        item.setItemMeta(meta);
        return item;
    }

    // Simple bid confirmation (can be expanded with amount input via Anvil or chat)
    private void openBidConfirmation(Player player, String auctionId) {
        Auction auction = auctionManager.getAuction(auctionId);
        if (auction == null) return;

        Inventory confirm = Bukkit.createInventory(
                new AuctionGUIHolder(0, "confirm_bid", auctionId),
                27,
                "§6§lPlace Bid?"
        );

        ItemStack info = new ItemStack(Material.valueOf(auction.getItemMaterial()), auction.getItemAmount());
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§eBid on " + auction.getItemMaterial());
        List<String> lore = new ArrayList<>();
        lore.add("§7Current Bid: §a$" + String.format("%,.0f", auction.getCurrentBid()));
        lore.add("§7Your bid must be higher!");
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        confirm.setItem(13, info);

        // For simplicity, assume next bid = current + 10% or fixed increment. In real use, prompt for amount.
        double suggestedBid = auction.getCurrentBid() + Math.max(10, auction.getCurrentBid() * 0.1);

        ItemStack bidBtn = new ItemStack(Material.GREEN_WOOL);
        ItemMeta bMeta = bidBtn.getItemMeta();
        bMeta.setDisplayName("§a§lBID §e$" + String.format("%,.0f", suggestedBid));
        PersistentDataContainer bPdc = bMeta.getPersistentDataContainer();
        bPdc.set(ACTION_KEY, PersistentDataType.STRING, "confirm_bid");
        bPdc.set(AUCTION_ID_KEY, PersistentDataType.STRING, auctionId);
        bidBtn.setItemMeta(bMeta);
        confirm.setItem(11, bidBtn);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cMeta = cancel.getItemMeta();
        cMeta.setDisplayName("§c§lCANCEL");
        PersistentDataContainer cPdc = cMeta.getPersistentDataContainer();
        cPdc.set(ACTION_KEY, PersistentDataType.STRING, "cancel");
        cancel.setItemMeta(cMeta);
        confirm.setItem(15, cancel);

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
