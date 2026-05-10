package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.gui.IslandBankGUI;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandRank;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * IslandCommand - Main command handler for /island and /is
 * 
 * PATCHED VERSION:
 * - Integrated donor biome selection GUI for /is create (when no biome specified)
 * - Integrated ResetConfirmationGUI + BiomeSelectionGUI flow for donors on /is reset
 * - Non-donors still use the simple "confirm" arg + default PLAINS biome
 * - Dimension-aware throughout (create/reset per Environment)
 * - All other subcommands and logic unchanged from previous version
 */
public class IslandCommand implements CommandExecutor, TabCompleter {

    private final FoliaSkyblock plugin;
    private final List<String> subCommands = Arrays.asList(
        "help", "create", "home", "h", "sethome", "setspawn", "info", "i", "members", "list",
        "invite", "accept", "deny", "kick", "promote", "demote", "transfer", "leave", "disband",
        "reset", "bank", "settings", "upgrade", "browse", "top", "visit", "tp"
    );

    private final List<String> dimensions = Arrays.asList("normal", "nether", "end", "overworld");
    private final List<String> ranks = Arrays.asList("OWNER", "MODERATOR", "GUEST");
    private final List<String> biomes = Arrays.asList(
        "PLAINS", "FOREST", "DESERT", "TAIGA", "JUNGLE", 
        "NETHER_WASTES", "THE_END"
    );

    public IslandCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help":
            case "?":
                sendHelp(player);
                break;

            case "create":
                handleCreate(player, args);
                break;

            case "home":
            case "h":
                handleHome(player, args);
                break;

            case "sethome":
                handleSetHome(player);
                break;

            case "setspawn":
                handleSetSpawn(player);
                break;

            case "info":
            case "i":
                handleInfo(player, args);
                break;

            case "members":
            case "list":
                handleMembers(player);
                break;

            case "invite":
                handleInvite(player, args);
                break;

            case "accept":
                handleAccept(player);
                break;

            case "deny":
                handleDeny(player);
                break;

            case "kick":
                handleKick(player, args);
                break;

            case "promote":
                handlePromote(player, args);
                break;

            case "demote":
                handleDemote(player, args);
                break;

            case "transfer":
                handleTransfer(player, args);
                break;

            case "leave":
                handleLeave(player);
                break;

            case "disband":
                handleDisband(player);
                break;

            case "reset":
                handleReset(player, args);
                break;

            case "bank":
                handleBank(player);
                break;

            case "settings":
                handleSettings(player);
                break;

            case "upgrade":
                handleUpgrade(player);
                break;

            case "browse":
            case "top":
            case "visit":
                handleBrowse(player, args);
                break;

            case "tp":
                handleTp(player, args);
                break;

            default:
                player.sendMessage("§cUnknown subcommand §f" + sub + "§c. Use §b/" + label + " help");
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l=== FoliaSkyblock Island Commands ===");
        player.sendMessage("§e/is create [dimension] [biome] §7- Create island (normal/nether/end). Donors get interactive biome GUI.");
        player.sendMessage("§e/is home [player] §7- Teleport to your (or visit) island home in current dimension.");
        player.sendMessage("§e/is sethome §7- Set your island's home location to current position.");
        player.sendMessage("§e/is setspawn §7- Set island spawn point (owner only).");
        player.sendMessage("§e/is info [player] §7- View island info, level, members.");
        player.sendMessage("§e/is members §7- List all island members and their ranks.");
        player.sendMessage("§e/is invite <player> §7- Invite player to your island party.");
        player.sendMessage("§e/is accept §7- Accept pending island invite.");
        player.sendMessage("§e/is deny §7- Deny pending island invite.");
        player.sendMessage("§e/is kick <player> §7- Kick member from island (owner only).");
        player.sendMessage("§e/is promote <player> <rank> §7- Promote member (OWNER/MODERATOR/GUEST).");
        player.sendMessage("§e/is demote <player> <rank> §7- Demote member.");
        player.sendMessage("§e/is transfer <player> §7- Transfer island ownership (owner only).");
        player.sendMessage("§e/is leave §7- Leave the island party.");
        player.sendMessage("§e/is disband §7- Disband island party (removes all members, owner only).");
        player.sendMessage("§e/is reset §7- Reset current dimension island (donors get confirmation + biome choice GUI).");
        player.sendMessage("§e/is bank §7- Open Island Bank GUI.");
        player.sendMessage("§e/is settings §7- Open Island Settings GUI.");
        player.sendMessage("§e/is upgrade §7- Open Island Upgrades GUI.");
        player.sendMessage("§e/is browse | top | visit §7- Browse top islands or visit menu.");
        player.sendMessage("§e/is tp <player> §7- Teleport to another player's island.");
        player.sendMessage("§7Aliases: /is , /island");
        player.sendMessage("§7Tip: Most actions are dimension-specific (you have separate islands per world).");
    }

    private void handleCreate(Player player, String[] args) {
        World.Environment dimension = player.getWorld().getEnvironment();
        String biomeName = null;

        if (args.length >= 2) {
            String arg1 = args[1].toLowerCase(Locale.ROOT);
            if (dimensions.contains(arg1) || arg1.equals("the_end")) {
                if (arg1.equals("nether") || arg1.equals("n")) dimension = World.Environment.NETHER;
                else if (arg1.equals("end") || arg1.equals("e") || arg1.equals("the_end")) dimension = World.Environment.THE_END;
                else dimension = World.Environment.NORMAL;
            } else {
                biomeName = args[1].toUpperCase(Locale.ROOT);
            }
        }
        if (args.length >= 3) {
            biomeName = args[2].toUpperCase(Locale.ROOT);
        }

        if (plugin.getIslandManager().hasIsland(player.getUniqueId(), dimension)) {
            player.sendMessage("§cYou already have an island in §e" + dimension.name() + "§c! Use §b/is reset §cto start over.");
            return;
        }

        boolean isDonor = player.hasPermission("foliasb.donor");
        if (biomeName != null && !isDonor) {
            player.sendMessage("§eOnly donors can choose a custom biome. Using random biome instead.");
            biomeName = null;
        }

        if (biomeName == null) {
            if (isDonor) {
                // Donor: open interactive biome selection GUI for the target dimension (not reset)
                player.sendMessage("§aOpening donor biome selection GUI for §e" + dimension.name() + "§a island...");
                plugin.getBiomeSelectionGUI().open(player, false, dimension);
                return;
            } else {
                biomeName = "PLAINS"; // default for non-donors
            }
        }

        // Non-donor or donor with explicit biome arg -> direct create
        player.sendMessage("§aCreating your §e" + dimension.name() + " §aisland... This may take a moment.");

        plugin.getIslandManager().createIsland(player, biomeName, dimension)
            .thenAccept(success -> {
                if (success) {
                    // Success message already sent inside IslandManager
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> 
                        player.sendMessage("§cIsland creation failed. Please try again or contact staff."));
                }
            });
    }

    private void handleHome(Player player, String[] args) {
        UUID targetUuid = player.getUniqueId();
        if (args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage("§cPlayer §e" + args[1] + " §cis not online.");
                return;
            }
            targetUuid = target.getUniqueId();
            // Future: add visit permission check here if islands can be private
        }

        // Uses current world dimension to locate the target's island in same dim
        plugin.getIslandManager().teleportToIsland(player, targetUuid);
    }

    private void handleSetHome(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cYou don't have an island in this dimension yet. Use §b/is create");
            return;
        }
        if (!island.isMember(player.getUniqueId())) {
            player.sendMessage("§cYou are not a member of this island.");
            return;
        }
        island.setSpawnLocation(player.getLocation());
        player.sendMessage("§aIsland home location set to your current position!");
        // Optional: persist spawn location if Island/Manager supports it (currently in-memory)
    }

    private void handleSetSpawn(Player player) {
        // Alias or same as sethome for now. In many plugins setspawn is for island spawn point.
        handleSetHome(player);
    }

    private void handleInfo(Player player, String[] args) {
        UUID targetUuid = player.getUniqueId();
        String targetName = "your";
        if (args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage("§cPlayer not found.");
                return;
            }
            targetUuid = target.getUniqueId();
            targetName = target.getName() + "'s";
        }

        Island island = plugin.getIslandManager().getIsland(targetUuid, player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§c" + (args.length > 1 ? targetName : "You") + " don't have an island in this dimension.");
            return;
        }

        player.sendMessage("§6§l=== " + targetName + " Island Info ===");
        player.sendMessage("§7Owner: §e" + Bukkit.getOfflinePlayer(island.getOwnerUuid()).getName());
        player.sendMessage("§7Dimension: §e" + island.getDimension().name());
        player.sendMessage("§7Biome: §e" + island.getBiomeName());
        player.sendMessage("§7Level: §a" + island.getLevel() + " §7(§f" + String.format("%.1f", island.getProgressToNextLevel() * 100) + "%§7 to next)");
        player.sendMessage("§7Members: §a" + island.getMemberCount());
        player.sendMessage("§7Use §b/is members §7for full list.");
    }

    private void handleMembers(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            player.sendMessage("§cYou don't have an island here. Use §b/is create");
            return;
        }

        player.sendMessage("§6§l=== Island Members ===");
        island.getMembers().forEach((uuid, rank) -> {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            String rankColor = rank == IslandRank.OWNER ? "§6" : (rank == IslandRank.MODERATOR ? "§b" : "§7");
            player.sendMessage(" §f" + name + " " + rankColor + "[" + rank.name() + "]");
        });
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §b/is invite <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer §e" + args[1] + " §cis not online.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cYou can't invite yourself.");
            return;
        }
        plugin.getIslandManager().inviteToParty(player, target);
    }

    private void handleAccept(Player player) {
        plugin.getIslandManager().acceptPartyInvite(player);
    }

    private void handleDeny(Player player) {
        plugin.getIslandManager().denyPartyInvite(player);
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §b/is kick <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        UUID targetUuid = (target != null) ? target.getUniqueId() : null;
        if (targetUuid == null) {
            // Try offline
            targetUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        }
        plugin.getIslandManager().kickMemberFromParty(player, targetUuid);
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §b/is promote <player> <OWNER|MODERATOR|GUEST>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not online.");
            return;
        }
        IslandRank newRank;
        try {
            newRank = IslandRank.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            player.sendMessage("§cInvalid rank. Valid: OWNER, MODERATOR, GUEST");
            return;
        }

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) {
            player.sendMessage("§cOnly the island owner can promote members.");
            return;
        }
        if (!island.isMember(target.getUniqueId())) {
            player.sendMessage("§cThat player is not a member of your island.");
            return;
        }

        island.setMemberRank(target.getUniqueId(), newRank);
        // Persist to DB using upsert
        GridPosition pos = island.getGridPosition();
        plugin.getDatabaseManager().addIslandMember(
            pos.x(), pos.z(), island.getDimension().name(), 
            target.getUniqueId(), newRank.name()
        );

        player.sendMessage("§aPromoted §e" + target.getName() + " §ato §b" + newRank.name());
        if (target.isOnline()) {
            target.sendMessage("§aYou were promoted to §b" + newRank.name() + " §aon the island!");
        }
    }

    private void handleDemote(Player player, String[] args) {
        // Similar to promote, but perhaps force to GUEST or specific lower rank. For simplicity reuse promote logic with lower rank suggestion.
        if (args.length < 3) {
            player.sendMessage("§cUsage: §b/is demote <player> <MODERATOR|GUEST>");
            return;
        }
        // Reuse promote handler (it accepts any rank)
        handlePromote(player, args);
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §b/is transfer <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer must be online to transfer ownership.");
            return;
        }
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) {
            player.sendMessage("§cOnly the current owner can transfer ownership.");
            return;
        }
        if (!island.isMember(target.getUniqueId())) {
            player.sendMessage("§cYou can only transfer to an existing island member.");
            return;
        }

        island.transferOwnership(target.getUniqueId());
        // Note: For full persistence, IslandManager should also update the islands table owner_uuid.
        // Current implementation updates memory + members table. Add DB owner update here if needed.
        player.sendMessage("§aOwnership transferred to §e" + target.getName() + "§a!");
        if (target.isOnline()) {
            target.sendMessage("§aYou are now the owner of this island!");
        }
    }

    private void handleLeave(Player player) {
        plugin.getIslandManager().leaveParty(player);
    }

    private void handleDisband(Player player) {
        plugin.getIslandManager().disbandParty(player);
    }

    private void handleReset(Player player, String[] args) {
        World.Environment dim = player.getWorld().getEnvironment();

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), dim);
        if (island == null) {
            player.sendMessage("§cYou don't have an island in this dimension to reset.");
            return;
        }

        boolean isDonor = player.hasPermission("foliasb.donor");

        if (isDonor) {
            // Donors get the nice confirmation GUI which then leads to biome selection
            player.sendMessage("§aOpening reset confirmation for §e" + dim.name() + "§a island (donor perks enabled)...");
            plugin.getResetConfirmationGUI().open(player, dim);
            return;
        }

        // Non-donors: require explicit "confirm" arg and always reset to PLAINS
        boolean confirmed = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        if (!confirmed) {
            player.sendMessage("§c§lWARNING: §cResetting will delete all progress and members in this dimension!");
            player.sendMessage("§eType §b/is reset confirm §eto proceed.");
            return;
        }

        player.sendMessage("§aResetting your §e" + dim.name() + " §aisland to default biome...");
        plugin.getIslandManager().resetIslandWithBiome(player, "PLAINS", dim);
    }

    private void handleBank(Player player) {
        player.sendMessage("§aOpening Island Bank GUI...");
        new IslandBankGUI(plugin).open(player, new IslandManager(plugin).getIsland(player.getUniqueId(), player.getWorld().getEnvironment()));
        player.sendMessage("§7(If GUI does not appear, check IslandBankGUI implementation)");
    }

    private void handleSettings(Player player) {
        player.sendMessage("§aOpening Island Settings GUI...");
        // Similar to bank
    }

    private void handleUpgrade(Player player) {
        player.sendMessage("§aOpening Island Upgrades GUI...");
        // Similar
    }

    private void handleBrowse(Player player, String[] args) {
        player.sendMessage("§aOpening Island Browse / Top list GUI...");
        // Links to IslandBrowseGUI
    }

    private void handleTp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §b/is tp <player>");
            return;
        }
        handleHome(player, args); // reuse home logic which supports target
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        if (args.length == 1) {
            return subCommands.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (List.of("invite", "kick", "home", "info", "tp", "transfer", "promote", "demote").contains(sub)) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            if (sub.equals("create")) {
                List<String> suggestions = new ArrayList<>(dimensions);
                suggestions.addAll(biomes);
                return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            if (sub.equals("promote") || sub.equals("demote")) {
                return ranks.stream()
                    .filter(r -> r.startsWith(args[1].toUpperCase()))
                    .collect(Collectors.toList());
            }
            if (sub.equals("reset")) {
                return Collections.singletonList("confirm");
            }
        }

        return Collections.emptyList();
    }
}
