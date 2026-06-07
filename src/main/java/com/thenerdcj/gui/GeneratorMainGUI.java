package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-island generator status (BaseGUI). Opened via {@link GeneratorGUI#open(Player, Island)}.
 */
public class GeneratorMainGUI extends BaseGUI {

    private static final int GUI_SIZE = 27;
    private static final int CLOSE_SLOT = 22;

    private final Map<UUID, Island> sessionIslands = new ConcurrentHashMap<>();

    public GeneratorMainGUI(FoliaSkyblock plugin) {
        super(plugin, true);
    }

    public GeneratorMainGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lIsland Generators";
    }

    @Override
    protected String getActionKeyName() {
        return "generator_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 1;
    }

    public void open(Player player, Island island) {
        if (island == null) {
            player.sendMessage("§cYou must be on an island to view generators.");
            return;
        }
        sessionIslands.put(player.getUniqueId(), island);
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, MessageUtil.legacy(getTitlePrefix()));

        int level = plugin.getIslandUpgradeManager() != null
                ? plugin.getIslandUpgradeManager().getOreGeneratorLevel(island)
                : 0;
        int activeQuests = plugin.getQuestManager() != null
                ? plugin.getQuestManager().getActiveQuestsCount(island.getId())
                : 0;
        gui.setItem(13, GUIUtils.createItem(Material.DIAMOND_PICKAXE, "§e§lGenerator Status",
                "§7Ore Generator Level: §b" + level,
                "§7Cobble gens produce better ores",
                "§7Quests In Progress: §b" + activeQuests,
                "",
                "§7Full customization coming soon!",
                "§7(Per-island type toggles, speed, etc.)"));
        gui.setItem(CLOSE_SLOT, GUIUtils.createNavButton(Material.BARRIER, "§c§lClose", ACTION_KEY, "CLOSE"));

        player.openInventory(gui);
    }

    @Override
    public void open(Player player, int page) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        open(player, island);
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        if ("CLOSE".equals(action)) {
            player.closeInventory();
            sessionIslands.remove(player.getUniqueId());
        }
    }
}