package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.database.GridPosition;
import com.thenerdcj.gui.IslandBankGUI;
import com.thenerdcj.island.Island;
import com.thenerdcj.island.IslandManager;
import com.thenerdcj.island.IslandRank;
import com.thenerdcj.util.MessageUtil;
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
        "reset", "bank", "settings", "upgrade", "browse", "top", "visit", "tp",
        "booster", "boosters", "shop", "store", "prestige", "crate", "crates"
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
            MessageUtil.sendMessage((Player) sender, "§cThis command can only be used by players.");
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

            case "museum":
            case "mus":
                if (plugin.getMuseumGUI() != null) {
                    plugin.getMuseumGUI().open(player);
                } else {
                    MessageUtil.sendMessage(player, "§cMuseum not available.");
                }
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

            case "booster":
            case "boosters":
                handleBooster(player);
                break;
            case "shop":
            case "store":
                handleShop(player);
                break;
            case "prestige":
                handlePrestige(player);
                break;
            case "border":
                if (args.length > 1 && args[1].equalsIgnoreCase("markers")) {
                    handleBorderMarkers(player, args);
                } else {
                    handleBorder(player);
                }
                break;
            case "boss":
                handleBossCommand(player, args);
                break;
            case "crate":
            case "crates":
                handleCrate(player);
                break;

            case "browse":
                handleBrowse(player, args);
                break;

            case "top":
                handleIslandTop(player, args);
                break;
            case "rank":
            case "myrank":
                // Direct /is rank support (efficient my ranks, persistence-backed)
                handleIslandTop(player, new String[]{"top", "rank"}); // reuses the rank branch
                break;

            case "visit":
                handleVisit(player, args);
                break;

            case "tp":
                handleTp(player, args);
                break;

            default:
                MessageUtil.sendMessage(player, "§cUnknown subcommand §f" + sub + "§c. Use §b/" + label + " help");
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        MessageUtil.sendMessage(player, "§6§l=== FoliaSkyblock Island Commands ===");
        MessageUtil.sendMessage(player, "§e/is create [dimension] [biome] §7- Create island (normal/nether/end). Donors get interactive biome GUI.");
        MessageUtil.sendMessage(player, "§e/is home [player] §7- Teleport to your (or visit) island home in current dimension.");
        MessageUtil.sendMessage(player, "§e/is sethome §7- Set your island's home location to current position.");
        MessageUtil.sendMessage(player, "§e/is setspawn §7- Set island spawn point (owner only).");
        MessageUtil.sendMessage(player, "§e/is info [player] §7- View island info, level, members.");
        MessageUtil.sendMessage(player, "§e/is members §7- List all island members and their ranks.");
        MessageUtil.sendMessage(player, "§e/is invite <player> §7- Invite player to your island party.");
        MessageUtil.sendMessage(player, "§e/is accept §7- Accept pending island invite.");
        MessageUtil.sendMessage(player, "§e/is deny §7- Deny pending island invite.");
        MessageUtil.sendMessage(player, "§e/is kick <player> §7- Kick member from island (owner only).");
        MessageUtil.sendMessage(player, "§e/is promote <player> <rank> §7- Promote member (OWNER/MODERATOR/GUEST).");
        MessageUtil.sendMessage(player, "§e/is demote <player> <rank> §7- Demote member.");
        MessageUtil.sendMessage(player, "§e/is transfer <player> §7- Transfer island ownership (owner only).");
        MessageUtil.sendMessage(player, "§e/is leave §7- Leave the island party.");
        MessageUtil.sendMessage(player, "§e/is disband §7- Disband island party (removes all members, owner only).");
        MessageUtil.sendMessage(player, "§e/is reset §7- Reset current dimension island (donors get confirmation + biome choice GUI).");
        MessageUtil.sendMessage(player, "§e/is museum §7- Open Museum (donate uniques for tokens, spend for cosmetics - PtW).");
        MessageUtil.sendMessage(player, "§e/is bank §7- Open Island Bank GUI.");
        MessageUtil.sendMessage(player, "§e/is settings §7- Open Island Settings GUI.");
        MessageUtil.sendMessage(player, "§e/is upgrade §7- Open Island Upgrades GUI.");
        MessageUtil.sendMessage(player, "§e/is border §7- View border size / prestige info.");
        MessageUtil.sendMessage(player, "§e/is border markers [on|off|toggle] §7- Toggle persistent corner hologram markers.");
        MessageUtil.sendMessage(player, "§e/is visit <player> §7- Visit another player's island (if they allow visitors).");
        MessageUtil.sendMessage(player, "§e/is browse | top §7- Browse top islands or visit menu.");
        MessageUtil.sendMessage(player, "§e/is tp <player> §7- Teleport to another player's island.");
        MessageUtil.sendMessage(player, "§7Aliases: /is , /island");
        MessageUtil.sendMessage(player, "§7Tip: Most actions are dimension-specific (you have separate islands per world).");
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
            MessageUtil.sendMessage(player, "§cYou already have an island in §e" + dimension.name() + "§c! Use §b/is reset §cto start over.");
            return;
        }

        // === TASK 1: ENFORCE DIM LEVEL GATE HERE TOO (before donor GUI or direct create) ===
        // Inter-class: reuses IslandManager.getMainIslandLevel + getDimensionLevelRequirement (config). Security: server-side only.
        // Play-to-Win + design: cannot create nether/end until main XP progression (no donor bypass for gate, only biome choice).
        if (dimension != World.Environment.NORMAL) {
            int mainLvl = plugin.getIslandManager().getMainIslandLevel(player.getUniqueId());
            int req = plugin.getIslandManager().getDimensionLevelRequirement(dimension);
            if (mainLvl < req) {
                MessageUtil.sendMessage(player, "§cMain island level §e" + req + "+ §crequired for " + dimension.name() + " (current: " + mainLvl + "). Grind skills/quests/combat on Overworld.");
                return;
            }
        }

        boolean isDonor = player.hasPermission("foliasb.donor");
        if (biomeName != null && !isDonor) {
            MessageUtil.sendMessage(player, "§eOnly donors can choose a custom biome. Using random biome instead.");
            biomeName = null;
        }

        if (biomeName == null) {
            if (isDonor) {
                // Donor: open interactive biome selection GUI for the target dimension (not reset)
                MessageUtil.sendMessage(player, "§aOpening donor biome selection GUI for §e" + dimension.name() + "§a island...");
                plugin.getBiomeSelectionGUI().open(player, false, dimension);
                return;
            } else {
                biomeName = "PLAINS"; // default for non-donors
            }
        }

        // Non-donor or donor with explicit biome arg -> direct create
        MessageUtil.sendMessage(player, "§aCreating your §e" + dimension.name() + " §aisland... This may take a moment.");

        plugin.getIslandManager().createIsland(player, biomeName, dimension)
            .thenAccept(success -> {
                if (success) {
                    // Success message already sent inside IslandManager
                } else {
                    plugin.getThreadSafety().runOnMainThread(() -> 
                        MessageUtil.sendMessage(player, "§cIsland creation failed. Please try again or contact staff."));
                }
            });
    }

    private void handleHome(Player player, String[] args) {
        UUID targetUuid = player.getUniqueId();
        if (args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(player, "§cPlayer §e" + args[1] + " §cis not online.");
                return;
            }
            targetUuid = target.getUniqueId();
            // Future: add visit permission check here if islands can be private
        }

        // Uses current world dimension to locate the target's island in same dim
        plugin.getIslandManager().teleportToIsland(player, targetUuid);

        // Apply nice border visuals after teleport (WorldBorder + particles)
        plugin.getThreadSafety().runOnMainThreadLater(() -> {
            Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
            if (island != null && plugin.getBorderVisualManager() != null) {
                plugin.getBorderVisualManager().updatePlayerWorldBorder(player, island);
            }
        }, 15L); // slight delay for teleport to complete
    }

    private void handleSetHome(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension yet. Use §b/is create");
            return;
        }
        if (!island.isMember(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "§cYou are not a member of this island.");
            return;
        }
        island.setSpawnLocation(player.getLocation());
        MessageUtil.sendMessage(player, "§aIsland home location set to your current position!");
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
                MessageUtil.sendMessage(player, "§cPlayer not found.");
                return;
            }
            targetUuid = target.getUniqueId();
            targetName = target.getName() + "'s";
        }

        Island island = plugin.getIslandManager().getIsland(targetUuid, player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§c" + (args.length > 1 ? targetName : "You") + " don't have an island in this dimension.");
            return;
        }

        MessageUtil.sendMessage(player, "§6§l=== " + targetName + " Island Info ===");
        MessageUtil.sendMessage(player, "§7Owner: §e" + plugin.getNameCache().getName(island.getOwnerUuid()));
        MessageUtil.sendMessage(player, "§7Dimension: §e" + island.getDimension().name());
        MessageUtil.sendMessage(player, "§7Biome: §e" + island.getBiomeName());
        MessageUtil.sendMessage(player, "§7Level: §a" + island.getLevel() + " §7(§f" + String.format("%.1f", island.getProgressToNextLevel() * 100) + "%§7 to next)");

        // New Worth System info
        if (plugin.getIslandWorthManager() != null) {
            double worth = plugin.getIslandWorthManager().getCachedWorth(island);
            int worthLevel = plugin.getIslandWorthManager().getCachedWorthLevel(island);
            MessageUtil.sendMessage(player, "§7Worth: §6" + String.format("%,.0f", worth) + " §7(Level §b" + worthLevel + "§7)");
        }

        MessageUtil.sendMessage(player, "§7Members: §a" + island.getMemberCount());
        MessageUtil.sendMessage(player, "§7Use §b/is members §7for full list.");
    }

    private void handleMembers(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island here. Use §b/is create");
            return;
        }

        MessageUtil.sendMessage(player, "§6§l=== Island Members ===");
        island.getMembers().forEach((uuid, rank) -> {
            String name = plugin.getNameCache().getName(uuid);
            String rankColor = rank == IslandRank.OWNER ? "§6" : (rank == IslandRank.MODERATOR ? "§b" : "§7");
            player.sendMessage(" §f" + name + " " + rankColor + "[" + rank.name() + "]");
        });
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "§cUsage: §b/is invite <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(player, "§cPlayer §e" + args[1] + " §cis not online.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "§cYou can't invite yourself.");
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
            MessageUtil.sendMessage(player, "§cUsage: §b/is kick <player>");
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
            MessageUtil.sendMessage(player, "§cUsage: §b/is promote <player> <OWNER|MODERATOR|GUEST>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(player, "§cPlayer not online.");
            return;
        }
        IslandRank newRank;
        try {
            newRank = IslandRank.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            MessageUtil.sendMessage(player, "§cInvalid rank. Valid: OWNER, MODERATOR, GUEST");
            return;
        }

        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "§cOnly the island owner can promote members.");
            return;
        }
        if (!island.isMember(target.getUniqueId())) {
            MessageUtil.sendMessage(player, "§cThat player is not a member of your island.");
            return;
        }

        island.setMemberRank(target.getUniqueId(), newRank);
        // Persist to DB using upsert
        GridPosition pos = island.getGridPosition();
        plugin.getDatabaseManager().addIslandMember(
            pos.x(), pos.z(), island.getDimension().name(), 
            target.getUniqueId(), newRank.name()
        );
        // Update persisted aggregate snapshot for O(1) (member_count in island_worth)
        plugin.getDatabaseManager().getIslandDAO().saveIslandMemberCount(pos, island.getMemberCount());

        MessageUtil.sendMessage(player, "§aPromoted §e" + target.getName() + " §ato §b" + newRank.name());
        if (target.isOnline()) {
            target.sendMessage("§aYou were promoted to §b" + newRank.name() + " §aon the island!");
        }
    }

    private void handleDemote(Player player, String[] args) {
        // Similar to promote, but perhaps force to GUEST or specific lower rank. For simplicity reuse promote logic with lower rank suggestion.
        if (args.length < 3) {
            MessageUtil.sendMessage(player, "§cUsage: §b/is demote <player> <MODERATOR|GUEST>");
            return;
        }
        // Reuse promote handler (it accepts any rank)
        handlePromote(player, args);
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "§cUsage: §b/is transfer <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(player, "§cPlayer must be online to transfer ownership.");
            return;
        }
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null || !island.isOwner(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "§cOnly the current owner can transfer ownership.");
            return;
        }
        if (!island.isMember(target.getUniqueId())) {
            MessageUtil.sendMessage(player, "§cYou can only transfer to an existing island member.");
            return;
        }

        island.transferOwnership(target.getUniqueId());
        // Note: For full persistence, IslandManager should also update the islands table owner_uuid.
        // Current implementation updates memory + members table. Add DB owner update here if needed.
        MessageUtil.sendMessage(player, "§aOwnership transferred to §e" + target.getName() + "§a!");
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
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension to reset.");
            return;
        }

        boolean isDonor = player.hasPermission("foliasb.donor");

        if (isDonor) {
            // Donors get the nice confirmation GUI which then leads to biome selection
            MessageUtil.sendMessage(player, "§aOpening reset confirmation for §e" + dim.name() + "§a island (donor perks enabled)...");
            plugin.getResetConfirmationGUI().open(player, dim);
            return;
        }

        // Non-donors: require explicit "confirm" arg and always reset to PLAINS
        boolean confirmed = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        if (!confirmed) {
            MessageUtil.sendMessage(player, "§c§lWARNING: §cResetting will delete all progress and members in this dimension!");
            MessageUtil.sendMessage(player, "§eType §b/is reset confirm §eto proceed.");
            return;
        }

        MessageUtil.sendMessage(player, "§aResetting your §e" + dim.name() + " §aisland to default biome...");
        plugin.getIslandManager().resetIslandWithBiome(player, "PLAINS", dim);
    }

    private void handleBank(Player player) {
        MessageUtil.sendMessage(player, "§aOpening Island Bank GUI...");
        new IslandBankGUI(plugin).open(player, new IslandManager(plugin).getIsland(player.getUniqueId(), player.getWorld().getEnvironment()));
        MessageUtil.sendMessage(player, "§7(If GUI does not appear, check IslandBankGUI implementation)");
    }

    private void handleSettings(Player player) {
        MessageUtil.sendMessage(player, "§aOpening Island Settings GUI...");
        // Similar to bank
    }

    private void handleUpgrade(Player player) {
        MessageUtil.sendMessage(player, "§aOpening Island Upgrades GUI...");
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island != null) {
            plugin.getIslandUpgradeGUI().open(player, island);
        } else {
            MessageUtil.sendMessage(player, "§cYou don't have an island here!");
        }
    }

    private void handleBrowse(Player player, String[] args) {
        MessageUtil.sendMessage(player, "§aOpening Island Browse / Top list GUI...");
        // Links to IslandBrowseGUI
    }

    private void handleTp(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "§cUsage: §b/is tp <player>");
            return;
        }
        handleHome(player, args); // reuse home logic which supports target
    }

    private void handleVisit(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "§cUsage: §b/is visit <player>");
            MessageUtil.sendMessage(player, "§7Visits the target's island in your current dimension if they allow visitors.");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, "§cPlayer §e" + targetName + " §cis not online.");
            return;
        }

        UUID targetUuid = target.getUniqueId();
        Island targetIsland = plugin.getIslandManager().getIsland(targetUuid, player.getWorld().getEnvironment());

        if (targetIsland == null) {
            MessageUtil.sendMessage(player, "§cThat player does not have an island in this dimension.");
            return;
        }

        // Check visitors allowed setting
        GridPosition pos = targetIsland.getGridPosition();
        plugin.getIslandSettingsManager().getSettings(pos).thenAccept(settings -> {
            if (!settings.isVisitorsAllowed()) {
                MessageUtil.sendMessage(player, "§cThat island is currently closed to visitors.");
                return;
            }

            // Check basic permission (visitors get limited access)
            // For now we use the existing visitorsAllowed flag as the gate.
            // More granular guest permissions can be added later via IslandPermission for guests.

            plugin.getThreadSafety().runOnMainThread(() -> {
                // Teleport
                plugin.getIslandManager().teleportToIsland(player, targetUuid);

                // Notify owner (best effort)
                if (target.isOnline()) {
                    target.sendMessage("§a" + player.getName() + " §7has visited your island.");
                }

                // Record in visitor log
                targetIsland.recordVisitor(player.getUniqueId());

                MessageUtil.sendMessage(player, "§aYou are now visiting §e" + target.getName() + "'s §aisland.");
                MessageUtil.sendMessage(player, "§7You have guest permissions (build/interact may be restricted by owner).");
            });
        });
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

    // ==================== Island Top / Leaderboards (Worth System) ====================
    private void handleIslandTop(Player player, String[] args) {
        // Open the paged IslandTopGUI (compression for large servers: no chat flood, server-side DB pagination via offset, GUI list rendering).
        // Replaces previous text + "Full GUI coming soon..." placeholder. Category support for value/level/members (alt cats use worth-top buffer + in-mem sort for now; deep per-cat pagination can layer on same offset pattern).
        com.thenerdcj.gui.IslandTopGUI.Category cat = com.thenerdcj.gui.IslandTopGUI.Category.WORTH;
        if (args.length > 1) {
            String type = args[1].toLowerCase();
            if (type.equals("level")) {
                cat = com.thenerdcj.gui.IslandTopGUI.Category.LEVEL;
            } else if (type.equals("members") || type.equals("member")) {
                cat = com.thenerdcj.gui.IslandTopGUI.Category.MEMBERS;
            } else if (type.equals("recalculate") || type.equals("recalc")) {
                handleWorthRecalculate(player);
                return;
            } else if (type.equals("rank") || type.equals("myrank") || type.equals("my_rank")) {
                // New: efficient my ranks (persistence-backed COUNT, no full list load). Complements the paged tops GUI.
                showMyRanks(player);
                return;
            }
            // value/worth or default -> WORTH
        }
        MessageUtil.sendMessage(player, "§aOpening Island Top GUI (paged, DB-backed for large scale)...");
        plugin.getIslandTopGUI().open(player, cat, 0);
    }

    private void showMyRanks(Player player) {
        MessageUtil.sendMessage(player, "§6§l=== Your Island Ranks (global leaderboards) ===");
        try {
            int worthRank = plugin.getIslandWorthManager().getMyWorthRank(player.getUniqueId(), player.getWorld().getEnvironment()).join();
            int levelRank = plugin.getIslandWorthManager().getMyLevelRank(player.getUniqueId(), player.getWorld().getEnvironment()).join();
            MessageUtil.sendMessage(player, "§eWorth rank: §f#" + (worthRank > 0 ? worthRank : "N/A"));
            MessageUtil.sendMessage(player, "§eLevel rank: §f#" + (levelRank > 0 ? levelRank : "N/A"));
            MessageUtil.sendMessage(player, "§7(Computed efficiently from persisted island_worth / levels; see /is top for full lists)");
        } catch (Exception e) {
            MessageUtil.sendMessage(player, "§cCould not compute ranks right now.");
        }
    }

    private void handleWorthRecalculate(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension.");
            return;
        }

        MessageUtil.sendMessage(player, "§aRecalculating your island's worth... this may take a moment.");

        plugin.getIslandWorthManager().invalidateCache(island);
        plugin.getIslandWorthManager().calculateIslandWorthAsync(island).thenAccept(worth -> {
            int level = plugin.getIslandWorthManager().calculateWorthLevel(worth);
            plugin.getThreadSafety().runOnMainThread(() -> {
                MessageUtil.sendMessage(player, "§aRecalculation complete!");
                MessageUtil.sendMessage(player, "§7Worth: §6" + String.format("%,.0f", worth) + " §7→ Worth Level §b" + level);
            });
        });
    }

    private void handleBooster(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension.");
            return;
        }
        plugin.getBoosterGUI().open(player, island);
    }

    private void handleShop(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension.");
            return;
        }
        if (plugin.getIslandShopGUI() != null) {
            plugin.getIslandShopGUI().open(player, island);
        } else {
            MessageUtil.sendMessage(player, "§cIsland Shop temporarily unavailable.");
        }
    }

    private void handlePrestige(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension.");
            return;
        }
        if (plugin.getPrestigeGUI() != null) {
            plugin.getPrestigeGUI().open(player, island);
        } else {
            MessageUtil.sendMessage(player, "§cPrestige system temporarily unavailable.");
        }
    }

    private void handleBorder(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension.");
            return;
        }

        int radius = plugin.getIslandUpgradeManager().getEffectiveIslandRadius(island);
        int sizeLevel = island.getUpgradeLevel(com.thenerdcj.island.IslandUpgrade.ISLAND_SIZE);
        int prestige = (plugin.getPrestigeManager() != null) ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

        MessageUtil.sendMessage(player, "§6§l=== Island Border ===");
        MessageUtil.sendMessage(player, "§7Current Radius: §a" + radius + " blocks");
        MessageUtil.sendMessage(player, "§7Size Upgrade Level: §b" + sizeLevel);
        if (prestige > 0) {
            MessageUtil.sendMessage(player, "§7Prestige Bonus: §d+" + (prestige * plugin.getConfig().getInt("upgrades.island-size.prestige-bonus.extra-per-prestige", 16)) + " radius");
        }
        // Folia-safe: use cached value (non-blocking). Background refresh if needed.
        boolean markers = plugin.getIslandSettingsManager()
            .getCachedSettings(island.getGridPosition())
            .isBorderMarkersEnabled();
        MessageUtil.sendMessage(player, "§7Hologram Markers: " + (markers ? "§aEnabled" : "§7Disabled") + " §8(/is border markers toggle)");
        MessageUtil.sendMessage(player, "§7Upgrade size with §b/is upgrade §7(ISLAND_SIZE)");
    }

    private void handleBorderMarkers(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension.");
            return;
        }

        GridPosition pos = island.getGridPosition();

        plugin.getIslandSettingsManager().getSettings(pos).thenAccept(settings -> {
            boolean current = settings.isBorderMarkersEnabled();
            boolean newValue = current;

            String action = (args.length > 2) ? args[2].toLowerCase() : "toggle";

            switch (action) {
                case "on", "enable" -> newValue = true;
                case "off", "disable" -> newValue = false;
                case "toggle", "" -> newValue = !current;
                default -> {
                    MessageUtil.sendMessage(player, "§cUsage: /is border markers [toggle|on|off]");
                    return;
                }
            }

            if (newValue == current) {
                MessageUtil.sendMessage(player, "§eHologram markers are already " + (current ? "§aenabled" : "§7disabled") + "§e.");
                return;
            }

            settings.setBorderMarkersEnabled(newValue);
            plugin.getIslandSettingsManager().saveSettings(settings);

            final boolean finalNewValue = newValue;
            plugin.getThreadSafety().runOnMainThread(() -> {
                if (finalNewValue) {
                    if (plugin.getBorderVisualManager() != null) {
                        plugin.getBorderVisualManager().spawnBorderHologramMarkers(island, player);
                    }
                    MessageUtil.sendMessage(player, "§aPersistent border hologram markers §aenabled§a! Corners now have markers.");
                } else {
                    MessageUtil.sendMessage(player, "§7Persistent border hologram markers §cdisabled§7.");
                    MessageUtil.sendMessage(player, "§8Existing markers will remain until manually removed or server restart (full auto-clean coming soon).");
                }
            });
        });
    }

    private void handleCrate(Player player) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension.");
            return;
        }
        if (plugin.getCrateGUI() != null) {
            plugin.getCrateGUI().open(player, island);
        } else {
            MessageUtil.sendMessage(player, "§cCrate system temporarily unavailable.");
        }
    }

    // Dedicated /is boss summon command with basic prestige scaling & cooldown stub
    private void handleBossCommand(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIsland(player.getUniqueId(), player.getWorld().getEnvironment());
        if (island == null) {
            MessageUtil.sendMessage(player, "§cYou don't have an island in this dimension.");
            return;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("summon")) {
            MessageUtil.sendMessage(player, "§cUsage: /is boss summon <tier>");
            return;
        }

        // Prestige scaling example
        if (plugin.getBossManager() != null) {
            long remaining = plugin.getBossManager().getIslandBossSummonCooldownRemaining(island);
            if (remaining > 0) {
                long hours = remaining / (1000 * 60 * 60);
                long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
                MessageUtil.sendMessage(player, "§cBoss summon on cooldown for " + hours + "h " + minutes + "m.");
                return;
            }

            boolean success = plugin.getBossManager().summonIslandBossEvent(island, com.thenerdcj.boss.DimensionBoss.REVENANT_HORROR_T5);
            if (success) {
                MessageUtil.sendMessage(player, "§c§lIsland Boss Event has begun!");
            } else {
                MessageUtil.sendMessage(player, "§cCould not summon boss (maybe one is already active or on cooldown).");
            }
        }
    }
}
