package com.thenerdcj.cosmetic;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for Cosmetic Chat Bubbles.
 * When a player chats, if they have an active bubble cosmetic, a temporary floating
 * TextDisplay + themed particles appears above their head showing the message.
 * Fully Folia-safe via ThreadSafety. Purely cosmetic.
 */
public class ChatBubbleCosmeticManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    private final Map<UUID, Set<ChatBubbleCosmetic>> owned = new ConcurrentHashMap<>();
    private final Map<UUID, ChatBubbleCosmetic> active = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.entity.Entity> activeBubble = new ConcurrentHashMap<>();

    private static final int[] COLLECTION_MILESTONES = {3, 5, 8, 12};

    public ChatBubbleCosmeticManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public Set<ChatBubbleCosmetic> getOwned(UUID uuid) {
        return owned.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public int getCollectionCount(UUID uuid) {
        return getOwned(uuid).size();
    }

    public ChatBubbleCosmetic getActive(UUID uuid) {
        return active.getOrDefault(uuid, ChatBubbleCosmetic.NONE);
    }

    public void unlock(UUID uuid, ChatBubbleCosmetic bubble) {
        if (bubble == null || bubble.isNone()) return;
        Set<ChatBubbleCosmetic> set = getOwned(uuid);
        boolean wasNew = set.add(bubble);
        if (wasNew) {
            int xp = Math.max(10, bubble.getRarity().getXpReward() / 3);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && plugin.getConfig().getBoolean("chatbubbles.collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(p, xp);
                p.sendMessage("§a§lChat Bubble Collection §7» Unlocked " + bubble.getRarity().getColorCode() + bubble.getDisplayName() + " §7(§a+" + xp + " XP§7)");

                int count = set.size();
                for (int m : COLLECTION_MILESTONES) {
                    if (count == m) {
                        p.sendMessage("§6§l★ Chat Stylist Milestone! ★ §e" + count + " unique chat bubbles unlocked!");
                        break;
                    }
                }
            }
            plugin.getDatabaseManager().savePlayerChatBubbleCosmetics(uuid, getBubbleIdSet(uuid));
        }
    }

    public boolean has(UUID uuid, ChatBubbleCosmetic b) {
        if (b == null || b.isNone()) return true;
        return getOwned(uuid).contains(b);
    }

    public void setActive(Player player, ChatBubbleCosmetic bubble) {
        ChatBubbleCosmetic toSet = (bubble == null ? ChatBubbleCosmetic.NONE : bubble);
        if (!toSet.isNone() && !has(player.getUniqueId(), toSet)) {
            player.sendMessage("§cYou have not unlocked this chat bubble.");
            return;
        }

        removeActiveBubble(player);
        active.put(player.getUniqueId(), toSet);

        if (!toSet.isNone()) {
            // Demo bubble on activation (Folia-safe)
            final ChatBubbleCosmetic finalBubble = toSet;
            threadSafety.runOnMainThreadLater(() -> {
                if (player.isOnline()) {
                    triggerChatBubble(player, "Preview: " + finalBubble.getDisplayName());
                }
            }, 2L);
        }

        player.sendMessage("§aChat Bubble " + (toSet.isNone() ? "removed" : "activated: " + toSet.getRarity().getColorCode() + toSet.getDisplayName()));
    }

    public void triggerChatBubble(Player player, String message) {
        ChatBubbleCosmetic bubble = getActive(player.getUniqueId());
        if (bubble.isNone() || !has(player.getUniqueId(), bubble)) return;

        String style = bubble.getBubbleStyle();
        Location loc = player.getLocation().add(0, 2.15, 0);

        threadSafety.runAtLocation(loc, () -> {
            // Cleanup previous bubble for this player
            org.bukkit.entity.Entity old = activeBubble.remove(player.getUniqueId());
            if (old != null && old.isValid()) {
                old.remove();
            }

            if (!player.isOnline()) return;

            TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class);

            String shortMsg = message.length() > 26 ? message.substring(0, 23) + "..." : message;
            String text;

            // Themed text + particles
            switch (style) {
                case "heart":
                    text = "§c❤ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.HEART, loc, 6, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "star":
                    text = "§e✦ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.FIREWORK, loc, 4, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "note":
                    text = "§a♫ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.NOTE, loc, 5, 0.3, 0.3, 0.3, 0.01);
                    break;
                case "flame":
                    text = "§c炎 " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.FLAME, loc, 8, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "magic":
                    text = "§5✧ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 7, 0.4, 0.3, 0.4, 0.02);
                    break;
                case "ender":
                    text = "§5☽ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.PORTAL, loc, 10, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "snow":
                    text = "§b❄ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 8, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "slime":
                    text = "§a☣ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 6, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "rainbow":
                    text = "§c§l✨ " + "§6" + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.3, 0.2, 0.3, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 0, 0), 1f));
                    player.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.3, 0.2, 0.3, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 255, 0), 1f));
                    player.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.3, 0.2, 0.3, 0.01, new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 0, 255), 1f));
                    break;
                case "crown":
                    text = "§6♛ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.CRIT, loc, 6, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "void":
                    text = "§8§o◌ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 8, 0.4, 0.3, 0.4, 0.01);
                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 3, 0.3, 0.2, 0.3, 0.01);
                    break;
                case "firework":
                    text = "§6✨ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.FIREWORK, loc, 5, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "aqua":
                    text = "§3~ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.SPLASH, loc, 6, 0.4, 0.3, 0.4, 0.01);
                    player.getWorld().spawnParticle(Particle.SPLASH, loc, 4, 0.3, 0.2, 0.3, 0.01);
                    break;
                case "burst":
                    text = "§b○ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.SPLASH, loc, 8, 0.5, 0.3, 0.5, 0.01);
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 4, 0.4, 0.2, 0.4, 0.01);
                    break;
                case "glitter":
                    text = "§d✧ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.END_ROD, loc, 5, 0.4, 0.3, 0.4, 0.01);
                    player.getWorld().spawnParticle(Particle.FIREWORK, loc, 3, 0.3, 0.2, 0.3, 0.01);
                    break;
                case "spark":
                    text = "§e⚡ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 6, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "leaf":
                    text = "§2🍃 " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc, 8, 0.4, 0.3, 0.4, 0.01);
                    break;
                case "skull":
                    text = "§8☠ " + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 0.3, 0.3, 0.3, 0.01);
                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 3, 0.2, 0.2, 0.2, 0.01);
                    break;
                default:
                    text = "§7" + shortMsg;
                    display.setText(text);
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 4, 0.3, 0.2, 0.3, 0.01);
            }

            display.setBillboard(TextDisplay.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0.05f, 0),
                    new AxisAngle4f(),
                    new Vector3f(0.65f, 0.65f, 0.65f),
                    new AxisAngle4f()
            ));

            activeBubble.put(player.getUniqueId(), display);

            // Auto-remove after ~4 seconds (Folia-safe via entity scheduler)
            threadSafety.runForEntityLater(display, () -> {
                if (display.isValid()) {
                    display.remove();
                }
                activeBubble.remove(player.getUniqueId(), display);
            }, 80L);
        });
    }

    public void removeActiveBubble(Player player) {
        active.remove(player.getUniqueId());
        org.bukkit.entity.Entity e = activeBubble.remove(player.getUniqueId());
        if (e != null && e.isValid()) {
            e.remove();
        }
    }

    public void loadPlayer(UUID uuid) {
        Set<String> ids = plugin.getDatabaseManager().loadPlayerChatBubbleCosmetics(uuid);
        Set<ChatBubbleCosmetic> set = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            try {
                ChatBubbleCosmetic b = ChatBubbleCosmetic.valueOf(id);
                if (!b.isNone()) set.add(b);
            } catch (Exception ignored) {}
        }
        owned.put(uuid, set);
    }

    public void savePlayer(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (ChatBubbleCosmetic b : getOwned(uuid)) ids.add(b.name());
        plugin.getDatabaseManager().savePlayerChatBubbleCosmetics(uuid, ids);
    }

    private Set<String> getBubbleIdSet(UUID uuid) {
        Set<String> ids = new HashSet<>();
        for (ChatBubbleCosmetic b : getOwned(uuid)) ids.add(b.name());
        return ids;
    }

    public void onPlayerJoin(Player player) {
        loadPlayer(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        savePlayer(player.getUniqueId());
        removeActiveBubble(player);
        owned.remove(player.getUniqueId());
    }
}
