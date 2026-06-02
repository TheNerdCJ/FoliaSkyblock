package com.thenerdcj.command;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.pets.CosmeticPet;
import com.thenerdcj.pets.PetType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Basic /pets command for managing cosmetic pets.
 * In the future this can be merged into the Wardrobe GUI as a "Pets" tab.
 */
public class PetCommand implements CommandExecutor {

    private final FoliaSkyblock plugin;

    public PetCommand(FoliaSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            plugin.getPetGUI().open(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list" -> {
                List<CosmeticPet> pets = plugin.getPetManager().getOwnedPets(player.getUniqueId());
                if (pets.isEmpty()) {
                    player.sendMessage("§cYou don't own any pets yet.");
                    return true;
                }
                player.sendMessage("§6§lYour Pets:");
                for (CosmeticPet pet : pets) {
                    String active = plugin.getPetManager().getActivePet(player.getUniqueId()) == pet ? " §a(Active)" : "";
                    String v = (pet.getVariant() != null && pet.getVariant() != com.thenerdcj.pets.PetVariant.NONE)
                            ? " §7[" + pet.getVariant().getDisplayName() + "]" : "";
                    player.sendMessage(" §e- " + pet.getCustomName() + v + " §7(" + pet.getType().getDisplayName() + ")" + active);
                }
            }
            case "equip" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /pets equip <pet name>");
                    return true;
                }
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                CosmeticPet target = null;
                for (CosmeticPet p : plugin.getPetManager().getOwnedPets(player.getUniqueId())) {
                    if (p.getCustomName().equalsIgnoreCase(name)) {
                        target = p;
                        break;
                    }
                }
                if (target == null) {
                    player.sendMessage("§cYou don't own a pet with that name.");
                    return true;
                }
                plugin.getPetManager().setActivePet(player, target);
                player.sendMessage("§aYou equipped §e" + target.getCustomName() + "§a!");
            }
            case "unequip" -> {
                plugin.getPetManager().setActivePet(player, null);
                player.sendMessage("§7Your pet has been sent away.");
            }
            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /pets rename <old name> <new name>");
                    return true;
                }
                String oldName = args[1];
                String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                boolean success = plugin.getPetManager().renamePet(player.getUniqueId(), oldName, newName);
                if (success) {
                    player.sendMessage("§aRenamed pet to §e" + newName);
                } else {
                    player.sendMessage("§cCould not find a pet with that name.");
                }
            }
            default -> player.sendMessage("§cUnknown subcommand. Use /pets");
        }
        return true;
    }
}
