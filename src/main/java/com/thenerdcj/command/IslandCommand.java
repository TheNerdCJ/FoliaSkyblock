package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandRank;
import com.thenerdcj.island.IslandUpgradeGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class IslandCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public IslandCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create": handleCreate(player, args); break;
            case "home": handleHome(player); break;
            case "tp": case "teleport": if (args.length > 1) handleTeleport(player, args[1]); else player.sendMessage("§cUsage: /island tp <player>"); break;
            case "invite": if (args.length > 1) handleInvite(player, args[1]); else player.sendMessage("§cUsage: /island invite <player>"); break;
            case "accept": handleAccept(player); break;
            case "kick": if (args.length > 1) handleKick(player, args[1]); else player.sendMessage("§cUsage: /island kick <player>"); break;
            case "leave": handleLeave(player); break;
            case "rank": if (args.length > 2) handleRank(player, args[1], args[2]); else player.sendMessage("§cUsage: /island rank <player> <rank>"); break;
            case "top": handleTop(player); break;
            case "setspawn": handleSetSpawn(player); break;
            case "upgrade": handleUpgrade(player, args); break;
            case "trade": handleTrade(player); break;
            case "settings": handleSettings(player); break;
            case "bank": handleBank(player); break;
            case "browse": handleBrowse(player); break;
            case "warp": if (args.length > 1) handleWarp(player, args[1]); else player.sendMessage("§cUsage: /island warp <player>"); break;
            case "setwarp": handleSetWarp(player); break;
            case "chat": handleChat(player); break;
            default: player.sendMessage("§cUnknown subcommand. Use §b/island§c for help."); break;
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6╔══════════════════════════════════════╗");
        player.sendMessage("§6║          §eFoliaSkyblock Help          §6║");
        player.sendMessage("§6╚══════════════════════════════════════╝");
        player.sendMessage("§e/island create [biome] §7- Create new island");
        player.sendMessage("§e/island home §7- Teleport to your island");
        player.sendMessage("§e/island tp <player> §7- Teleport to another player's island");
        player.sendMessage("§e/island invite <player> §7- Invite player to your island");
        player.sendMessage("§e/island accept §7- Accept island invite");
        player.sendMessage("§e/island kick <player> §7- Kick member from island");
        player.sendMessage("§e/island rank <player> <rank> §7- Set member rank");
        player.sendMessage("§e/island top §7- View top islands leaderboard");
        player.sendMessage("§e/island setspawn §7- Set your island spawn point");
        player.sendMessage("§e/island upgrade [UPGRADE] §7- View or purchase island upgrades");
        player.sendMessage("§e/island trade §7- Open the island trade shop");
        player.sendMessage("§e/island settings §7- Open island settings GUI");
        player.sendMessage("§e/island bank §7- Open island bank");
        player.sendMessage("§e/island browse §7- Browse public islands");
        player.sendMessage("§e/island warp <player> §7- Teleport to another player's island warp");
        player.sendMessage("§e/island setwarp §7- Set your island's warp location");
        player.sendMessage("§e/island chat §7- Toggle island-only chat");
    }

    private void handleCreate(Player player, String[] args) {
        boolean isDonor = player.hasPermission("foliasb.donor") || player.hasPermission("foliasb.create.biome");
        if (args.length >= 2 && isDonor) {
            String biomeName = args[1].toUpperCase();
            if (biomeName.equals("NETHER_WASTES") || biomeName.equals("THE_END")) {
                player.sendMessage("§cNether and End islands are progression-locked!");
                return;
            }
            plugin.getIslandManager().createIsland(player, biomeName);
        } else {
            plugin.getIslandManager().createIsland(player, "PLAINS");
        }
    }

    private void handleHome(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) { player.sendMessage("§cYou don't have an island! Use §b/island create§c first."); return; }
        player.teleport(island.getSpawnLocation() != null ? island.getSpawnLocation() : new Location(player.getWorld(), 0, 64, 0));
        player.sendMessage("§aTeleported to your island!");
    }

    private void handleTeleport(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { player.sendMessage("§cPlayer not found."); return; }
        Island targetIsland = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
        if (targetIsland == null) { player.sendMessage("§cThat player doesn't have an island."); return; }
        player.teleport(targetIsland.getSpawnLocation() != null ? targetIsland.getSpawnLocation() : new Location(target.getWorld(), 0, 64, 0));
        player.sendMessage("§aTeleported to §e" + target.getName() + "§a's island!");
    }

    private void handleInvite(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { player.sendMessage("§cPlayer not found."); return; }
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) { player.sendMessage("§cYou don't have an island!"); return; }
        if (!island.isOwner(player.getUniqueId())) { player.sendMessage("§cOnly the owner can invite!"); return; }
        plugin.getIslandManager().inviteToParty(player, target);
    }

    private void handleAccept(Player player) { plugin.getIslandManager().acceptPartyInvite(player); }

    private void handleKick(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { player.sendMessage("§cPlayer not found."); return; }
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) { player.sendMessage("§cOnly the owner can kick!"); return; }
        plugin.getIslandManager().removeMemberFromIsland(player.getUniqueId(), target.getUniqueId());
        player.sendMessage("§aKicked §e" + target.getName() + "§a from your island.");
    }

    private void handleLeave(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) { player.sendMessage("§cYou are not in an island!"); return; }
        if (island.isOwner(player.getUniqueId())) { player.sendMessage("§cYou cannot leave your own island!"); return; }
        plugin.getIslandManager().removeMemberFromIsland(island.getOwnerUuid(), player.getUniqueId());
        player.sendMessage("§aYou have left the island.");
    }

    private void handleRank(Player player, String targetName, String rankName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { player.sendMessage("§cPlayer not found."); return; }
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) { player.sendMessage("§cOnly the owner can change ranks!"); return; }
        try {
            IslandRank rank = IslandRank.valueOf(rankName.toUpperCase());
            plugin.getIslandManager().setMemberRank(player.getUniqueId(), target.getUniqueId(), rank);
            player.sendMessage("§aSet §e" + target.getName() + "§a's rank to §b" + rank.name() + "§a.");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid rank! Use: GUEST, HELPER, MODERATOR, OWNER");
        }
    }

    private void handleTop(Player player) {
        plugin.getIslandManager().getTopIslands(10).thenAccept(topIslands -> {
            player.sendMessage("§6╔══════════════════════════════════════╗");
            player.sendMessage("§6║          §eTop 10 Islands              §6║");
            player.sendMessage("§6╚══════════════════════════════════════╝");
            int rank = 1;
            for (Island island : topIslands) {
                String ownerName = Bukkit.getOfflinePlayer(island.getOwnerUuid()).getName();
                player.sendMessage("§e#" + rank + " §f" + ownerName + " §7- Level " + island.getLevel());
                rank++;
            }
        });
    }

    private void handleSetSpawn(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) { player.sendMessage("§cOnly the owner can set spawn!"); return; }
        island.setSpawnLocation(player.getLocation());
        player.sendMessage("§aIsland spawn point set!");
    }

    private void handleUpgrade(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) { player.sendMessage("§cYou don't have an island!"); return; }
        if (args.length > 1) {
            plugin.getIslandUpgradeManager().purchaseUpgrade(player, island, com.thenerdcj.island.IslandUpgrade.valueOf(args[1].toUpperCase()));
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> new IslandUpgradeGUI(plugin).open(player, island));
        }
    }

    private void handleTrade(Player player) { plugin.getTradeGUI().openTradeGUI(player); }

    private void handleSettings(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) { player.sendMessage("§cOnly the owner can access settings!"); return; }
        plugin.getServer().getScheduler().runTask(plugin, () -> new com.thenerdcj.gui.IslandSettingsGUI(plugin).open(player, island));
    }

    private void handleBank(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) { player.sendMessage("§cYou don't have an island!"); return; }
        plugin.getServer().getScheduler().runTask(plugin, () -> new com.thenerdcj.gui.IslandBankGUI(plugin).open(player, island));
    }

    private void handleBrowse(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> new com.thenerdcj.gui.IslandBrowseGUI(plugin).open(player, 0));
    }

    private void handleWarp(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { player.sendMessage("§cPlayer not found."); return; }
        Island targetIsland = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
        if (targetIsland == null) { player.sendMessage("§cThat player doesn't have an island."); return; }
        plugin.getIslandWarpManager().getWarp(targetIsland.getGridPosition()).thenAccept(warp -> {
            if (warp != null && warp.isEnabled() && warp.getWarpLocation() != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.teleport(warp.getWarpLocation());
                    player.sendMessage("§aTeleported to §e" + target.getName() + "§a's island warp!");
                });
            } else {
                player.sendMessage("§cThat island doesn't have a warp set.");
            }
        });
    }

    private void handleSetWarp(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) { player.sendMessage("§cOnly the owner can set warp!"); return; }
        plugin.getIslandWarpManager().setWarp(island.getGridPosition(), player.getLocation()).thenAccept(success -> {
            player.sendMessage(success ? "§aIsland warp set!" : "§cFailed to set warp.");
        });
    }

    private void handleChat(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) { player.sendMessage("§cYou don't have an island!"); return; }
        plugin.getIslandChatManager().toggleIslandChat(player);
    }
}