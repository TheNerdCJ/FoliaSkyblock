package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.ParticleTrail;
import com.thenerdcj.util.MessageUtil;
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
            MessageUtil.sendMessage(sender, "§cOnly players can use this command.");
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
                    MessageUtil.sendMessage(player, "§eParticle Trail GUI not available.");
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
                    MessageUtil.sendMessage(player, "§cUnknown trail: " + sub);
                    showHelp(player);
                }
        }

        return true;
    }

    private void showHelp(Player player) {
        MessageUtil.sendMessage(player, "§6§l=== Particle Trails ===");
        MessageUtil.sendMessage(player, "§e/trail list §7- Open trail menu");
        MessageUtil.sendMessage(player, "§e/trail off §7- Disable your trail");
        MessageUtil.sendMessage(player, "§e/trail <name> §7- Activate a trail (e.g. flame, heart)");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "§7Available: §aFLAME, HEART, SOUL, RAINBOW, DRAGON, ELECTRIC, NOTE, DUST, VOID...");
        MessageUtil.sendMessage(player, "§8Unlock via Prestige levels (some free) or Slayer Shop + /trail menu");
    }
}