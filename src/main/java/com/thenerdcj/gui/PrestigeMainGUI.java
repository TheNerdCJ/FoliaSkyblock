package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.manager.PrestigeManager;
import com.thenerdcj.util.MessageUtil;
import com.thenerdcj.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island prestige (BaseGUI + PDC). Opened via {@link PrestigeGUI#open(Player, Island)}.
 */
public class PrestigeMainGUI extends BaseGUI {

    private static final int PRESTIGE_SIZE = 27;
    private static final int CLOSE_SLOT = 26;

    private final Map<UUID, Island> sessionIslands = new ConcurrentHashMap<>();

    public PrestigeMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    public PrestigeMainGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
    }

    @Override
    protected int getInventorySize() {
        return PRESTIGE_SIZE;
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lIsland Prestige";
    }

    @Override
    protected String formatTitle(Player player, int page) {
        Island island = sessionIslands.get(player.getUniqueId());
        com.thenerdcj.manager.PrestigeManager pm = plugin.getPrestigeManager();
        int p = island != null && pm != null ? pm.getPrestigeLevel(island) : 0;
        int eff = island != null && pm != null ? pm.getEffectiveLevel(island) : (island != null ? island.getLevel() : 0);
        return "§6§lIsland Prestige §7(P" + p + " • Eff " + eff + ")";
    }

    @Override
    protected String getActionKeyName() {
        return "prestige_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 1;
    }

    @Override
    protected int getTotalPages(Player player) {
        return 1;
    }

    public void open(Player player, Island island) {
        if (island == null) {
            player.sendMessage("§cYou must be on an island to prestige.");
            return;
        }
        sessionIslands.put(player.getUniqueId(), island);
        playerPages.put(player.getUniqueId(), 0);

        PrestigeManager pm = plugin.getPrestigeManager();
        int current = pm.getPrestigeLevel(island);
        boolean canPrestige = pm.canPrestige(island);

        Inventory gui = Bukkit.createInventory(null, PRESTIGE_SIZE, MessageUtil.legacy(formatTitle(player, 0)));

        double xpMult = (pm.getPrestigeMultiplier(island, PrestigeManager.PrestigeMultiplierType.XP) - 1) * 100;
        double worthMult = (pm.getPrestigeMultiplier(island, PrestigeManager.PrestigeMultiplierType.WORTH) - 1) * 100;

        int effLevel = island != null && pm != null ? pm.getEffectiveLevel(island) : (island != null ? island.getLevel() : 0);
        gui.setItem(4, GUIUtils.createItem(Material.NETHER_STAR, "§e§lCurrent Prestige: §b" + current,
                "§7Permanent multipliers active:",
                "§a+" + String.format("%.1f", xpMult) + "% §7Island XP",
                "§a+" + String.format("%.1f", worthMult) + "% §7Worth",
                "",
                "§7Effective island height (prestige incorporated): §b" + effLevel,
                "§7Higher prestige = stronger bonuses + higher effective level"));

        int minLevel = plugin.getConfig().getInt("island.prestige.requirements.min_island_level", 50);
        double minWorth = plugin.getConfig().getDouble("island.prestige.requirements.min_worth", 250000);
        double currentWorth = plugin.getIslandWorthManager() != null
                ? plugin.getIslandWorthManager().getCachedWorth(island) : 0;

        Material reqMaterial = canPrestige ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String reqTitle = canPrestige ? "§a§lREADY TO PRESTIGE" : "§c§lRequirements Not Met";
        String reqFooter = canPrestige ? "§aYou meet the requirements!" : "§cClick for exact requirements";

        gui.setItem(12, GUIUtils.createItem(reqMaterial, reqTitle,
                "§7Min Island Level: §e" + minLevel + " §7(You: §b" + island.getLevel() + "§7)",
                "§7Min Worth: §e$" + String.format("%,.0f", minWorth)
                        + " §7(You: §6$" + String.format("%,.0f", currentWorth) + "§7)",
                "",
                reqFooter));

        if (canPrestige) {
            // Default path: prestige + reset using the island's current biome (no cascade to other dims)
            ItemStack defaultBtn = GUIUtils.createNavButton(Material.EMERALD_BLOCK, "§a§lPRESTIGE + RESET",
                    ACTION_KEY, "PRESTIGE_DEFAULT");
            ItemMeta dMeta = defaultBtn.getItemMeta();
            if (dMeta != null) {
                dMeta.setLore(java.util.List.of(
                        "§7Reset this island to a fresh starter",
                        "§7using its §ecurrent biome§7 (default).",
                        "§7Your prestige level increases.",
                        "",
                        "§7Other dimensions are §cnot§7 affected.",
                        "§c§lWARNING: Physical island will be reset!"));
                defaultBtn.setItemMeta(dMeta);
            }
            gui.setItem(11, defaultBtn);

            // Prestige-exclusive "choose any biome" path (no donor requirement).
            // This is only available when the player has met the prestige requirements.
            // Selecting a different biome will also reset their Nether & End islands.
            ItemStack chooseBtn = GUIUtils.createNavButton(Material.NETHER_STAR, "§d§lPRESTIGE + CHOOSE BIOME",
                    ACTION_KEY, "PRESTIGE_CHOOSE_BIOME");
            ItemMeta cMeta = chooseBtn.getItemMeta();
            if (cMeta != null) {
                cMeta.setLore(java.util.List.of(
                        "§7Prestige perk: freely pick §eany§7 biome for the rebirth.",
                        "§7If you pick a §edifferent§7 biome, your",
                        "§7Nether and End islands will §calso§7 be reset to fresh starters.",
                        "",
                        "§7This gives a true fresh multi-dimension start for your new prestige run.",
                        "§c§lWARNING: Physical islands will be reset!"));
                chooseBtn.setItemMeta(cMeta);
            }
            gui.setItem(15, chooseBtn);
        }

        gui.setItem(22, GUIUtils.createItem(Material.BOOK, "§ePrestige Info",
                "§7Prestige is the ultimate endgame",
                "§7progression for dedicated players.",
                "§7Each level gives stacking multipliers",
                "§7that make future progress much faster.",
                "§eClick buttons or use Close at bottom to navigate."));

        gui.setItem(CLOSE_SLOT, GUIUtils.createNavButton(Material.BARRIER, "§c§lClose", ACTION_KEY, "CLOSE"));
        player.openInventory(gui);
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        // Built in open(Player, Island)
    }

    @Override
    protected void addStandardNavigation(Inventory gui, int page, int totalPages) {
        // Close only @26 — set in open()
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        Island island = sessionIslands.get(player.getUniqueId());
        if (island == null) {
            return;
        }
        if ("PRESTIGE_DEFAULT".equals(action)) {
            SoundUtil.click(player);
            player.closeInventory();
            // Uses current biome as "default", no cascade
            boolean success = plugin.getPrestigeManager().performPrestige(island, player);
            if (!success) {
                SoundUtil.error(player);
            }
        } else if ("PRESTIGE_CHOOSE_BIOME".equals(action)) {
            SoundUtil.click(player);
            player.closeInventory();
            // Store context so the biome picker knows to do a prestige rebirth + possible cascade
            if (plugin.getPrestigeManager() != null) {
                plugin.getPrestigeManager().startPrestigeBiomeChoice(player, island, true /* cascade other dims when new biome chosen */);
            }
            // Open the biome selector in prestige-rebirth mode.
            // This bypasses the normal donor requirement so any player who has reached the prestige
            // requirements can freely choose any biome (prestige-exclusive perk).
            // The picker will detect the pending context and route through PrestigeManager.
            if (plugin.getBiomeSelectionGUI() != null) {
                plugin.getBiomeSelectionGUI().open(player, true, org.bukkit.World.Environment.NORMAL, true);
            } else {
                MessageUtil.sendMessage(player, "§cBiome selector unavailable right now.");
            }
        }
    }
}