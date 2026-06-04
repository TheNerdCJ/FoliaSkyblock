package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.skills.PlayerSkillManager;
import com.thenerdcj.skills.SkillType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

/**
 * Simple GUI for viewing per-player skills (MCMMO style).
 * Shows level, XP progress, ability status.
 *
 * Fully migrated to extend BaseGUI (with GUIUtils items, PDC action routing via ACTION_KEY,
 * standard nav/close, title prefix guard, SoundUtil, pagination foundation even if 1 page).
 */
public class SkillGUI extends BaseGUI {

    public SkillGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public SkillGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
    }

    @Override
    public void open(Player player) {
        PlayerSkillManager sm = plugin.getPlayerSkillManager();
        if (sm == null) {
            player.sendMessage("§cSkill system not loaded.");
            return;
        }
        super.open(player);
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lPlayer Skills";
    }

    @Override
    protected String getActionKeyName() {
        return "skill_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 36; // single page dashboard; value not used for slicing
    }

    @Override
    protected int getTotalPages(Player player) {
        return 1;
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        PlayerSkillManager sm = plugin.getPlayerSkillManager();
        if (sm == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Full glass fill for clean dashboard look (nav will overwrite bottom center slots)
        ItemStack glass = GUIUtils.createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, glass);
        }

        // Header info (slot 4, preserved from original)
        ItemStack info = GUIUtils.createItem(Material.BOOK, "§6§lPlayer Skills",
            "§7Gain XP from mining, chopping, farming,",
            "§7fishing, fighting, etc. (MCMMO inspired)",
            "§7Levels unlock abilities and bonuses.",
            "§7Check anti-cheat safe - no macro XP.",
            "§7Abilities: sneak+action for mining/wood at Lv10+.",
            "",
            "§eUse /skills or this GUI"
        );
        gui.setItem(4, info);

        // Skills (start at 9 like original; 8 skills fit easily before nav at 45+)
        int slot = 9;
        Map<SkillType, double[]> skills = sm.getPlayerSkills(uuid);
        for (SkillType type : SkillType.values()) {
            double[] data = skills.getOrDefault(type, new double[]{0, 1});
            int level = (int) data[1];
            double xp = data[0];
            double nextXp = 100 * (level + 1) * 1.2; // approx
            double progress = Math.min(1.0, xp / nextXp);

            Material mat = getIcon(type);
            ItemStack item = GUIUtils.createItem(mat,
                "§e§l" + type.getDisplayName() + " §7Lv " + level,
                "§7" + type.getDescription(),
                "§7XP: §a" + String.format("%.0f", xp) + " / " + String.format("%.0f", nextXp),
                "§7Progress: §b" + (int)(progress * 100) + "%",
                "§7Ability: " + (sm.isAbilityActive(uuid, type) ? "§aActive" : "§7Ready at Lv10+"),
                "",
                "§eClick for details (future)"
            );
            attachSkillPDC(item, type);
            gui.setItem(slot++, item);
            if (slot > 43) break;
        }

        // Note: BaseGUI will add CLOSE nav at slot 49 (standard). No manual close at 53.
        // For p=0 + total=1, no PREV/NEXT shown; remaining slots stay glass.
    }

    private void attachSkillPDC(ItemStack item, SkillType type) {
        if (item == null || type == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "SKILL:" + type.name());
            item.setItemMeta(meta);
        }
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        if (action != null && action.startsWith("SKILL:")) {
            String skillName = action.substring(6);
            player.sendMessage("§7Skill details and activation coming soon for §e" + skillName + "§7.");
            player.sendMessage("§7(High level abilities activate via sneak+action in world; see anti-cheat safe design.)");
            // No refresh needed (no state change). Base already played click sound.
            return;
        }
        // CLOSE/PREV/NEXT handled by BaseGUI before calling here.
    }

    private Material getIcon(SkillType type) {
        switch (type) {
            case MINING: return Material.DIAMOND_PICKAXE;
            case WOODCUTTING: return Material.IRON_AXE;
            case FARMING: return Material.WHEAT;
            case FISHING: return Material.FISHING_ROD;
            case COMBAT: return Material.DIAMOND_SWORD;
            case EXCAVATION: return Material.IRON_SHOVEL;
            case ACROBATICS: return Material.LEATHER_BOOTS;
            case REPAIR: return Material.ANVIL;
            default: return Material.BOOK;
        }
    }
}