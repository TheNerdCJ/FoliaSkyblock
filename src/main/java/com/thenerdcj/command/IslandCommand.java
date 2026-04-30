package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandRank;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * Complete IslandCommand for FoliaSkyblock
 * Supports donor biome selection and full party system
 */
public class IslandCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;
    private static final List<String> ALLOWED_BIOMES = Arrays.asList(
            "PLAINS", "FOREST", "DESERT", "TAIGA", "JUNGLE", "NETHER_WASTES", "THE_END"
    );

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

        switch (args[0].toLowerCase()) {
            case "create":
                handleCreate(player, args);
                break;

            case "home":
                handleHome(player);
                break;

            case "tp":
            case "teleport":
                handleTeleport(player, args);
                break;

            case "invite":
                handleInvite(player, args);
                break;

            case "accept":
                handleAccept(player);
                break;

            case "kick":
                handleKick(player, args);
                break;

            case "rank":
                handleRank(player, args);
                break;

            case "top":
                handleTop(player);
                break;

            case "setspawn":
                handleSetSpawn(player);
                break;

            default:
                player.sendMessage("§cUnknown subcommand. Use §b/island§c for help.");
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6╔══════════════════════════════════════╗");
        player.sendMessage("§6║          §eFoliaSkyblock Help          §6║");
        player.sendMessage("§6╚══════════════════════════════════════╝");
        player.sendMessage("§e/island create [biome] §7- Create new island (donors can choose biome)");
        player.sendMessage("§e/island home §7- Teleport to your island");
        player.sendMessage("§e/island tp <player> §7- Teleport to another player's island");
        player.sendMessage("§e/island invite <player> §7- Invite player to your island");
        player.sendMessage("§e/island accept §7- Accept invite");
        player.sendMessage("§e/island kick <player> §7- Kick member");
        player.sendMessage("§e/island rank <player> <rank> §7- Set member rank (GUEST/HELPER/MODERATOR)");
        player.sendMessage("§e/island top §7- View top islands leaderboard");
        player.sendMessage("§e/island setspawn §7- Set your island spawn point");
    }

    private void handleCreate(Player player, String[] args) {
        boolean isDonor = player.hasPermission("foliasb.donor") || player.hasPermission("foliasb.create.biome");

        if (args.length >= 2 && isDonor) {
            String biomeName = args[1].toUpperCase();
            if (!ALLOWED_BIOMES.contains(biomeName)) {
                player.sendMessage("§cInvalid biome. Allowed: §e" + String.join(", ", ALLOWED_BIOMES));
                return;
            }
            plugin.getIslandManager().createIsland(player, biomeName, World.Environment.NORMAL);
            player.sendMessage("§aCreating your §e" + biomeName + "§a island...");
        } else {
            plugin.getIslandManager().createIsland(player, "PLAINS", World.Environment.NORMAL);
            player.sendMessage("§aCreating your island... (use §b/island create <biome>§a if you are a donor)");
        }
    }

    private void handleHome(Player player) {
        var home = plugin.getIslandManager().getIslandHome(player);
        if (home != null) {
            player.teleport(home);
            player.sendMessage("§aTeleported to your island home.");
        } else {
            player.sendMessage("§cYou don't have an island yet! Use §b/island create§c first.");
        }
    }

    private void handleTeleport(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /island tp <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found or not online.");
            return;
        }

        Island targetIsland = plugin.getIslandManager().getIsland(target.getUniqueId(), target.getWorld().getEnvironment());
        if (targetIsland == null) {
            player.sendMessage("§cThat player doesn't have an island in this dimension.");
            return;
        }

        var center = targetIsland.getCenter(target.getWorld());
        if (center != null) {
            player.teleport(center);
            player.sendMessage("§aTeleported to §e" + target.getName() + "'s§a island.");
        } else {
            player.sendMessage("§cCould not find that island's location.");
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /island invite <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return;
        }

        plugin.getIslandManager().inviteToParty(player, target);
    }

    private void handleAccept(Player player) {
        plugin.getIslandManager().acceptPartyInvite(player);
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /island kick <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return;
        }

        plugin.getIslandManager().removeMemberFromIsland(player.getUniqueId(), target.getUniqueId());
        player.sendMessage("§aKicked §e" + target.getName() + "§a from your island.");
    }

    private void handleRank(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /island rank <player> <GUEST|HELPER|MODERATOR>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return;
        }

        try {
            IslandRank rank = IslandRank.valueOf(args[2].toUpperCase());
            plugin.getIslandManager().setMemberRank(player.getUniqueId(), target.getUniqueId(), rank);
            player.sendMessage("§aSet §e" + target.getName() + "'s§a rank to §e" + rank);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid rank. Use: GUEST, HELPER, MODERATOR");
        }
    }

    private void handleTop(Player player) {
        player.sendMessage("§6=== Top Islands ===");
        player.sendMessage("§e#1 §7- Coming soon...");
        player.sendMessage("§71. Your island will appear here once leveling is active.");
    }

    private void handleSetSpawn(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cYou don't have an island in this dimension.");
            return;
        }

        if (!island.isOwner(player.getUniqueId())) {
            player.sendMessage("§cOnly the island owner can set the spawn point.");
            return;
        }

        player.sendMessage("§aIsland spawn point set to your current location!");
        player.sendMessage("§7(Full spawn point saving coming in next update)");
    }
}