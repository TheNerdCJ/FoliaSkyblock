package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.ParticleTrail;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ParticleCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public ParticleCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list":
            case "menu":
                if (plugin.getParticleTrailGUI() != null) {
                    plugin.getParticleTrailGUI().open(player, 0);
                } else {
                    player.sendMessage("§eParticle Trail GUI not available.");
                }
                break;

            case "off":
            case "disable":
            case "none":
                plugin.getParticleTrailManager().setActiveTrail(player, ParticleTrail.NONE);
                break;

            default:
                // Try to parse as a trail name
                try {
                    ParticleTrail trail = ParticleTrail.valueOf(sub.toUpperCase());
                    plugin.getParticleTrailManager().setActiveTrail(player, trail);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cUnknown trail: " + sub);
                    showHelp(player);
                }
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l=== Particle Trails ===");
        player.sendMessage("§e/trail list §7- Open trail menu");
        player.sendMessage("§e/trail off §7- Disable your trail");
        player.sendMessage("§e/trail <name> §7- Activate a trail (e.g. flame, heart)");
        player.sendMessage("");
        player.sendMessage("§7Available: §aFLAME, HEART, SOUL, RAINBOW, DRAGON, ELECTRIC, NOTE, DUST, VOID...");
        player.sendMessage("§8Unlock via Prestige levels (some free) or Slayer Shop + /trail menu");
    }
}