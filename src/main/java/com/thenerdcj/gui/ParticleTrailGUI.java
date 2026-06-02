package com.thenerdcj.gui;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.ParticleTrail;
import com.thenerdcj.island.Island;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Particle Trail selection GUI (extends BaseGUI).
 *
 * Final modernization pass:
 * - Last remaining manual `new ItemStack(icon) + getItemMeta() + PDC` block
 *   in populatePage converted to `GUIUtils.createItem` + attach helper.
 * - All other item creation already using GUIUtils (preserved).
 * - Full consistency with the GUI modernization standard.
 */
public class ParticleTrailGUI extends BaseGUI {

    private final NamespacedKey TRAIL_ID_KEY;

    public ParticleTrailGUI(FoliaSkyblock plugin) {
        this(plugin, true);
    }

    public ParticleTrailGUI(FoliaSkyblock plugin, boolean autoRegister) {
        super(plugin, autoRegister);
        this.TRAIL_ID_KEY = new NamespacedKey(plugin, "trail_id");
    }

    @Override
    protected String getTitlePrefix() {
        return "§6§lParticle Trails";
    }

    @Override
    protected String getActionKeyName() {
        return "trail_action";
    }

    @Override
    protected int getItemsPerPage() {
        return 28;
    }

    @Override
    protected int getTotalPages(Player player) {
        long count = Arrays.stream(ParticleTrail.values())
                .filter(t -> t != ParticleTrail.NONE).count();
        return (int) Math.max(1, Math.ceil(count / (double) getItemsPerPage()));
    }

    @Override
    protected void populatePage(Inventory gui, Player player, int page) {
        gui.setItem(4, GUIUtils.createItem(Material.FIREWORK_ROCKET, "§6§lPersonal Particle Trails",
                "§7Cosmetic effects that follow you",
                "§7Unlock via Prestige & Slayer Shop",
                "§d35+ unique visual styles available!"));

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        int prestige = (island != null && plugin.getPrestigeManager() != null)
                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;
        int tokensHeld = (plugin.getBossManager() != null) ? plugin.getBossManager().getTotalSlayerTokens(player) : 0;

        gui.setItem(0, GUIUtils.createItem(Material.NETHER_STAR, "§bYour Prestige", "§7Level: §e" + prestige));
        gui.setItem(1, GUIUtils.createItem(Material.GOLD_NUGGET, "§6Slayer Tokens", "§7Held: §e" + tokensHeld));

        ParticleTrail active = plugin.getParticleTrailManager().getActiveTrail(player.getUniqueId());
        gui.setItem(8, GUIUtils.createItem(Material.GLOWSTONE_DUST, "§e§lActive Trail", "§a" + active.getDisplayName()));

        ParticleTrail[] trails = Arrays.stream(ParticleTrail.values())
                .filter(t -> t != ParticleTrail.NONE).toArray(ParticleTrail[]::new);

        int start = page * getItemsPerPage();
        int end = Math.min(start + getItemsPerPage(), trails.length);
        int slot = 10;

        Set<ParticleTrail> unlockedSet = plugin.getParticleTrailManager().getUnlockedTrails(player.getUniqueId());

        for (int i = start; i < end; i++) {
            if (slot > 43) break;
            ParticleTrail trail = trails[i];

            boolean isActive = trail == active;
            boolean unlocked = unlockedSet.contains(trail);
            boolean prestigeOk = prestige >= trail.getMinPrestige();
            boolean canAfford = trail.getTokenCost() == 0 || tokensHeld >= trail.getTokenCost();

            Material icon = getTrailIcon(trail);
            String statusLine;
            String actionValue;

            if (isActive) {
                statusLine = "§a§l★ ACTIVE - Click to Disable";
                actionValue = "UNEQUIP";
            } else if (unlocked) {
                statusLine = "§e§lClick to Equip";
                actionValue = "EQUIP";
            } else if (!prestigeOk) {
                statusLine = "§c§lLocked - Prestige " + trail.getMinPrestige() + "+";
                actionValue = "LOCKED";
            } else if (canAfford) {
                String costStr = trail.getTokenCost() > 0 ? trail.getTokenCost() + " Tokens" : "Free (Prestige)";
                statusLine = "§6§lClick to Unlock (" + costStr + ")";
                actionValue = "UNLOCK";
            } else {
                statusLine = "§c§lInsufficient Tokens";
                actionValue = "LOCKED";
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7" + trail.getDisplayName());
            if (trail.getMinPrestige() > 0) lore.add("§7Prestige: §b" + trail.getMinPrestige());
            if (trail.getTokenCost() > 0) lore.add("§7Cost: §6" + trail.getTokenCost());
            lore.add("");
            lore.add(statusLine);

            ItemStack item = GUIUtils.createItem(icon, "§e" + trail.getDisplayName(), lore.toArray(new String[0]));
            attachTrailPDCs(item, actionValue, trail);
            gui.setItem(slot, item);
            slot++;
        }
    }

    @Override
    protected void handleAction(String action, PersistentDataContainer pdc, Player player, int currentPage, ItemStack clicked) {
        String trailId = pdc.get(TRAIL_ID_KEY, PersistentDataType.STRING);
        if (trailId == null) return;

        try {
            ParticleTrail t = ParticleTrail.valueOf(trailId);

            switch (action) {
                case "UNEQUIP" -> plugin.getParticleTrailManager().setActiveTrail(player, ParticleTrail.NONE);
                case "EQUIP" -> plugin.getParticleTrailManager().setActiveTrail(player, t);
                case "UNLOCK" -> {
                    Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
                    int prestige = (island != null && plugin.getPrestigeManager() != null)
                            ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

                    if (prestige < t.getMinPrestige()) {
                        player.sendMessage("§cRequires Prestige " + t.getMinPrestige());
                        return;
                    }
                    int cost = t.getTokenCost();
                    if (cost > 0) {
                        if (plugin.getBossManager() == null || !plugin.getBossManager().consumeSlayerTokens(player, cost)) {
                            player.sendMessage("§cNot enough tokens");
                            return;
                        }
                    }
                    if (plugin.getParticleTrailManager().unlockTrail(player, t)) {
                        plugin.getParticleTrailManager().setActiveTrail(player, t);
                        player.sendMessage("§aUnlocked: " + t.getDisplayName());
                    }
                }
                case "LOCKED" -> player.sendMessage("§cTrail locked");
            }
            refresh(player);
        } catch (Exception ignored) {}
    }

    private Material getTrailIcon(ParticleTrail trail) {
        return switch (trail) {
            case FLAME_TRAIL, LAVA_TRAIL -> Material.BLAZE_POWDER;
            case HEART_AURA -> Material.REDSTONE;
            case ENDER_AURA, VOID_AURA -> Material.ENDER_PEARL;
            case SMOKE_TRAIL -> Material.COAL;
            case MAGIC_AURA -> Material.PURPLE_DYE;
            case NOTE_TRAIL -> Material.NOTE_BLOCK;
            case HAPPY_VILLAGER -> Material.EMERALD;
            case DUST_TRAIL, RAINBOW_DUST -> Material.GLOWSTONE_DUST;
            case SOUL_TRAIL -> Material.SOUL_SAND;
            case CRIT_SPARK -> Material.IRON_SWORD;
            case PORTAL_AURA -> Material.OBSIDIAN;
            case DRAGON_BREATH -> Material.DRAGON_BREATH;
            default -> Material.FIREWORK_ROCKET;
        };
    }

    private void attachTrailPDCs(ItemStack item, String actionValue, ParticleTrail trail) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_KEY, PersistentDataType.STRING, actionValue);
            pdc.set(TRAIL_ID_KEY, PersistentDataType.STRING, trail.name());
            item.setItemMeta(meta);
        }
    }
}