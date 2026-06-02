package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.cosmetic.PowerOrbSkin;
import com.thenerdcj.cosmetic.PowerOrbSkinManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PowerOrbSkinCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public PowerOrbSkinCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        PowerOrbSkinManager manager = plugin.getPowerOrbSkinManager();
        if (manager == null || plugin.getPowerOrbSkinGUI() == null) {
            player.sendMessage("§cPower Orb Skins system not available.");
            return true;
        }

        // Staff/testing: /powerorbskins give [skin]
        if (args.length >= 1 && args[0].equalsIgnoreCase("give") && player.hasPermission("foliasb.admin")) {
            PowerOrbSkin skin = PowerOrbSkin.DISCO_BALL;
            if (args.length >= 2) {
                try {
                    skin = PowerOrbSkin.valueOf(args[1].toUpperCase());
                } catch (Exception ignored) {}
            }
            manager.giveOrb(player, skin);
            player.sendMessage("§aGave test Power Orb: " + skin.getDisplayName());
            return true;
        }

        // Normal use: open the skins GUI + give a starter orb if they have none
        if (manager.getOwnedSkins(player.getUniqueId()).isEmpty()) {
            manager.unlockSkin(player.getUniqueId(), PowerOrbSkin.DISCO_BALL);
            manager.giveOrb(player, PowerOrbSkin.DISCO_BALL);
            player.sendMessage("§aStarter Power Orb Skin unlocked for testing!");
        }

        plugin.getPowerOrbSkinGUI().open(player);
        return true;
    }
}