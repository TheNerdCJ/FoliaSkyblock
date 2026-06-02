package com.thenerdcj.pets;

import com.thenerdcj.FoliaSkyblock;
import com.thenerdcj.island.Island;
import com.thenerdcj.util.ThreadSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.EulerAngle;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cosmetic (vanity-only) pets for players.
 * Pets are small ArmorStand-based followers.
 * 
 * Design goals:
 * - Purely cosmetic (no combat, no stats)
 * - Folia-safe using EntityScheduler
 * - Integrated with the Wardrobe system
 * - Players can own multiple pets and switch between them
 * - Proper collection/rarity system awarding Island XP on first ownership of each type (wardrobe-style)
 */
public class PetManager {

    private final FoliaSkyblock plugin;
    private final ThreadSafety threadSafety;

    // Per-player data
    private final Map<UUID, List<CosmeticPet>> ownedPets = new ConcurrentHashMap<>();
    private final Map<UUID, CosmeticPet> activePet = new ConcurrentHashMap<>();

    // Currently spawned pet entities (for cleanup)
    private final Map<UUID, ArmorStand> activePetEntities = new ConcurrentHashMap<>();

    // Pet collection tracking for rarity-based XP (parallel to WardrobeManager.wardrobeEquipmentCollection)
    private final Map<UUID, Set<PetType>> petCollection = new ConcurrentHashMap<>();

    // Owned Pet Skins (cosmetic appearances unlocked via prestige/slayer/collection)
    private final Map<UUID, Set<PetSkin>> ownedSkins = new ConcurrentHashMap<>();

    private static final int[] PET_COLLECTION_MILESTONES = {3, 5, 10, 15, 25, 40};

    public PetManager(FoliaSkyblock plugin) {
        this.plugin = plugin;
        this.threadSafety = plugin.getThreadSafety();
    }

    public List<CosmeticPet> getOwnedPets(UUID uuid) {
        return ownedPets.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    public CosmeticPet getActivePet(UUID uuid) {
        return activePet.get(uuid);
    }

    public void setActivePet(Player player, CosmeticPet pet) {
        UUID uuid = player.getUniqueId();

        // Despawn old pet
        despawnPet(uuid);

        if (pet == null) {
            activePet.remove(uuid);
            return;
        }

        activePet.put(uuid, pet);
        spawnPet(player, pet);
    }

    public void addPet(UUID uuid, CosmeticPet pet) {
        if (pet == null) return;
        List<CosmeticPet> list = getOwnedPets(uuid);

        // Enforce one-per-type (DB PK guarantees this, but defensive here)
        boolean alreadyOwned = list.stream().anyMatch(p -> p.getType() == pet.getType());
        if (alreadyOwned) {
            // Update existing custom name if different (for rename sync)
            for (CosmeticPet existing : list) {
                if (existing.getType() == pet.getType()) {
                    if (!existing.getCustomName().equals(pet.getCustomName())) {
                        existing.setCustomName(pet.getCustomName());
                    }
                    return;
                }
            }
        }

        list.add(pet);

        // Collection / rarity XP (only on first acquisition of this pet type)
        awardCollectionXPIfNew(uuid, pet.getType());
    }

    // ==================== SPAWNING & FOLLOWING (Folia Safe) ====================

    private void spawnPet(Player player, CosmeticPet pet) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        despawnPet(uuid);

        PetType t = pet.getType();
        boolean small = isSmallPet(t);
        float yOffset = small ? 0.9f : 1.4f;

        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(
            player.getLocation().add(0, yOffset, 0), 
            EntityType.ARMOR_STAND
        );

        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(small);
        stand.setMarker(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName("§d" + pet.getCustomName());

        // Give it a cute head based on PetType + optional PetSkin
        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        String owner = t.getHeadTextureOwner();

        PetSkin currentSkin = pet.getSkin();
        if (currentSkin != null && !currentSkin.isNone()) {
            owner = currentSkin.getEffectiveHeadOwner(t);
        }

        if (owner != null) {
            meta.setOwner(owner);
        }
        head.setItemMeta(meta);
        stand.getEquipment().setHelmet(head);

        // Advanced visual: held item for some pets (tool, food, etc.)
        ItemStack hand = getPetHeldItem(t);
        if (hand != null) {
            stand.getEquipment().setItemInMainHand(hand);
        }

        // Variant visual flair (colored particles on spawn for non-standard variants)
        if (pet.getVariant() != PetVariant.NONE) {
            Particle varParticle = Particle.END_ROD;
            player.getWorld().spawnParticle(varParticle, stand.getLocation().add(0, 0.7, 0), 6, 0.25, 0.25, 0.25, 0.02);
        }

        // Pet Skin visual flair (unique particles based on skin)
        if (pet.getSkin() != null && !pet.getSkin().isNone()) {
            Particle skinParticle = Particle.END_ROD;
            String skinName = pet.getSkin().name();
            if (skinName.contains("FIRE") || skinName.contains("PHOENIX") || skinName.contains("DRAGON")) {
                skinParticle = Particle.FLAME;
            } else if (skinName.contains("CRYSTAL") || skinName.contains("GLOW")) {
                skinParticle = Particle.END_ROD;
            } else if (skinName.contains("RAINBOW")) {
                skinParticle = Particle.HAPPY_VILLAGER;
            }
            player.getWorld().spawnParticle(skinParticle, stand.getLocation().add(0, 0.9, 0), 8, 0.3, 0.3, 0.3, 0.01);
        }

        activePetEntities.put(uuid, stand);

        // Spawn sound effect (themed)
        playPetSpawnSound(player, t);

        // Start following task using Folia EntityScheduler
        startFollowingTask(player, stand);
    }

    private boolean isSmallPet(PetType t) {
        // Larger / more imposing pets get full-size ArmorStand
        return switch (t.name()) {
            case "BABY_DRAGON", "PHOENIX", "GHAST", "BLAZE", "ENDERMAN", "SNIFFER", "CAMEL" -> false;
            default -> true;
        };
    }

    private ItemStack getPetHeldItem(PetType t) {
        return switch (t.name()) {
            case "WOLF", "STRAY" -> new ItemStack(org.bukkit.Material.BONE);
            case "ALLAY" -> new ItemStack(org.bukkit.Material.ALLIUM);
            case "BEE" -> new ItemStack(org.bukkit.Material.HONEYCOMB);
            case "GOAT" -> new ItemStack(org.bukkit.Material.GOAT_HORN);
            case "VEX" -> new ItemStack(org.bukkit.Material.IRON_SWORD);
            case "BLAZE" -> new ItemStack(org.bukkit.Material.BLAZE_ROD);
            default -> null;
        };
    }

    private void playPetSpawnSound(Player player, PetType t) {
        Sound sound = switch (t.name()) {
            case "CAT" -> Sound.ENTITY_CAT_AMBIENT;
            case "WOLF", "STRAY" -> Sound.ENTITY_WOLF_AMBIENT;
            case "BEE" -> Sound.ENTITY_BEE_LOOP;
            case "FOX" -> Sound.ENTITY_FOX_AMBIENT;
            case "PARROT", "BABY_PARROT" -> Sound.ENTITY_PARROT_AMBIENT;
            case "SLIME" -> Sound.ENTITY_SLIME_SQUISH;
            case "PHOENIX", "BLAZE" -> Sound.ENTITY_BLAZE_AMBIENT;
            case "GHAST" -> Sound.ENTITY_GHAST_AMBIENT;
            default -> Sound.ENTITY_GENERIC_HURT; // subtle
        };
        player.getWorld().playSound(player.getLocation(), sound, 0.6f, 1.0f);
    }

    private void startFollowingTask(Player player, ArmorStand petStand) {
        if (player == null || petStand == null || !petStand.isValid()) return;

        UUID playerUuid = player.getUniqueId();

        // Use Folia EntityScheduler for the pet stand itself when possible
        petStand.getScheduler().runAtFixedRate(plugin, task -> {
            if (!player.isOnline() || !petStand.isValid()) {
                task.cancel();
                activePetEntities.remove(playerUuid);
                return;
            }

            Location target = player.getLocation().clone().add(0, 0.8 + Math.sin(System.currentTimeMillis() / 800.0) * 0.1, 0); // gentle bobbing
            Location current = petStand.getLocation();

            double distance = current.distance(target);

            if (distance > 12 || current.getWorld() != target.getWorld()) {
                // Teleport if too far or in different world
                petStand.teleport(target);
            } else if (distance > 0.4) {
                // Smooth interpolated movement
                double speed = Math.min(0.35, distance * 0.18);
                Location newLoc = current.clone().add(
                    (target.getX() - current.getX()) * speed,
                    (target.getY() - current.getY()) * speed,
                    (target.getZ() - current.getZ()) * speed
                );

                // Look at player slightly
                newLoc.setYaw((float) Math.toDegrees(Math.atan2(target.getZ() - newLoc.getZ(), target.getX() - newLoc.getX())) - 90);

                petStand.teleport(newLoc);

                // Light cosmetic particles around the pet (type-themed)
                CosmeticPet currentPet = getActivePet(playerUuid);
                if (currentPet != null && Math.random() < 0.12) {
                    org.bukkit.Particle particle = org.bukkit.Particle.END_ROD;
                    String typeName = currentPet.getType().name();
                    if (typeName.contains("PHOENIX") || typeName.contains("BLAZE") || typeName.contains("DRAGON")) {
                        particle = org.bukkit.Particle.FLAME;
                    } else if (typeName.contains("WISP") || typeName.contains("ALLAY")) {
                        particle = org.bukkit.Particle.END_ROD;
                    }
                    player.getWorld().spawnParticle(particle, petStand.getLocation().add(0, 0.4, 0), 1, 0.15, 0.15, 0.15, 0.01);
                }

                // Occasional themed idle sound (advanced audio polish)
                if (Math.random() < 0.008) {
                    playPetIdleSound(player, currentPet != null ? currentPet.getType() : null);
                }
            }
        }, null, 1L, 2L);
    }

    private void playPetIdleSound(Player player, PetType t) {
        if (player == null || t == null) return;
        Sound sound = switch (t.name()) {
            case "CAT" -> Sound.ENTITY_CAT_PURREOW;
            case "WOLF", "STRAY" -> Sound.ENTITY_WOLF_PANT;
            case "BEE" -> Sound.ENTITY_BEE_LOOP;
            case "FOX" -> Sound.ENTITY_FOX_SNIFF;
            case "PARROT", "BABY_PARROT" -> Sound.ENTITY_PARROT_FLY;
            case "SLIME" -> Sound.ENTITY_SLIME_JUMP;
            default -> null;
        };
        if (sound != null) {
            player.getWorld().playSound(player.getLocation().add(0, 1, 0), sound, 0.4f, 1.0f + (float)Math.random() * 0.2f);
        }
    }

    public void despawnPet(UUID uuid) {
        ArmorStand stand = activePetEntities.remove(uuid);
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
    }

    public void onPlayerQuit(Player player) {
        despawnPet(player.getUniqueId());
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();

        // Load from DB if not loaded
        if (!ownedPets.containsKey(uuid)) {
            plugin.getDatabaseManager().loadPlayerPets(uuid, this);
        }

        CosmeticPet pet = getActivePet(uuid);
        if (pet != null) {
            // Small delay to let the world load
            plugin.getThreadSafety().runOnMainThreadLater(() -> {
                if (player.isOnline()) {
                    spawnPet(player, pet);
                }
            }, 20L);
        }
    }

    public void loadPlayer(UUID uuid) {
        // Called from listener
        // First load persisted collection (so we don't re-award XP on every login)
        loadPetCollectionFromDB(uuid);

        if (!ownedPets.containsKey(uuid)) {
            plugin.getDatabaseManager().loadPlayerPets(uuid, this);
        }
    }

    private void loadPetCollectionFromDB(UUID uuid) {
        Set<String> coll = plugin.getDatabaseManager().loadPetCollection(uuid);
        Set<PetType> types = ConcurrentHashMap.newKeySet();
        for (String name : coll) {
            try {
                types.add(PetType.valueOf(name));
            } catch (Exception ignored) {}
        }
        if (!types.isEmpty()) {
            petCollection.put(uuid, types);
        }
    }

    public void savePlayer(UUID uuid) {
        CosmeticPet active = getActivePet(uuid);
        List<CosmeticPet> owned = getOwnedPets(uuid);
        plugin.getDatabaseManager().savePlayerPets(uuid, owned, active);
    }

    /**
     * Renames one of the player's pets.
     */
    public boolean renamePet(UUID uuid, String oldName, String newName) {
        List<CosmeticPet> pets = getOwnedPets(uuid);
        for (CosmeticPet pet : pets) {
            if (pet.getCustomName().equalsIgnoreCase(oldName)) {
                pet.setCustomName(newName);

                // If it was the active pet, update it
                if (getActivePet(uuid) == pet) {
                    // Refresh the entity name if spawned
                    ArmorStand stand = activePetEntities.get(uuid);
                    if (stand != null && stand.isValid()) {
                        stand.setCustomName("§d" + newName);
                    }
                }
                return true;
            }
        }
        return false;
    }

    // ==================== PET COLLECTION / RARITY XP (Wardrobe-style) ====================

    /**
     * Awards Island XP the first time a player collects (unlocks) a new pet type.
     * Mirrors the wardrobe equipment collection XP system exactly in spirit.
     */
    private void awardCollectionXPIfNew(UUID uuid, PetType type) {
        if (type == null) return;

        Set<PetType> collected = petCollection.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        int previousCount = collected.size();

        if (collected.add(type)) {
            PetRarity rarity = type.getRarity();
            int xp = rarity.getXpReward();

            if (!plugin.getConfig().getBoolean("pets.collection-xp.enabled", true)) {
                // Still persist the collection entry even if XP disabled
                plugin.getDatabaseManager().savePetCollectionEntry(uuid, type.name());
                return;
            }

            // Persist the new collection entry immediately
            plugin.getDatabaseManager().savePetCollectionEntry(uuid, type.name());

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Award via main island XP (light, play-to-win friendly)
                plugin.getIslandManager().addIslandXp(player, xp);

                String rarityStr = rarity.getColorCode() + rarity.getDisplayName();
                player.sendMessage("§a§lPet Collection §7» Discovered a new pet! "
                        + rarityStr + " §e" + type.getDisplayName() + " §7(§a+" + xp + " Island XP§7)");

                if (plugin.getConfig().getBoolean("xp.show-gain-messages", true)) {
                    player.sendActionBar("§a+" + xp + " XP §7(Pet Collection - " + rarity.getDisplayName() + ")");
                }

                checkAndAnnouncePetMilestones(player, previousCount, collected.size());
            }
        }
    }

    private void checkAndAnnouncePetMilestones(Player player, int previousCount, int newCount) {
        for (int milestone : PET_COLLECTION_MILESTONES) {
            if (previousCount < milestone && newCount >= milestone) {
                player.sendMessage("§6§l★ Pet Collector Milestone! ★ §eYou have collected §a" + milestone + " §eunique pets!");
                // Sound could be added via SoundUtil in future
                break;
            }
        }
    }

    /**
     * Returns how many unique pet types this player has collected.
     */
    public int getPetCollectionCount(UUID uuid) {
        Set<PetType> set = petCollection.get(uuid);
        return set == null ? 0 : set.size();
    }

    /**
     * Returns an unmodifiable view of the player's collected pet types.
     * Used for collection views / commands.
     */
    public Set<PetType> getPetCollection(UUID uuid) {
        Set<PetType> set = petCollection.get(uuid);
        if (set == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(set));
    }

    /**
     * Force-add to collection without XP (used for admin / migration).
     */
    public void markPetCollected(UUID uuid, PetType type) {
        if (type == null) return;
        petCollection.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(type);
        plugin.getDatabaseManager().savePetCollectionEntry(uuid, type.name());
    }

    /**
     * Grant any pets the player now qualifies for via prestige (tokenCost == 0 ones).
     * Called from PrestigeManager on prestige-up, mirroring ParticleTrailManager.grantPrestigeUnlocks.
     */
    public void grantPrestigePetUnlocks(Player player, int newPrestigeLevel) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        List<CosmeticPet> owned = getOwnedPets(uuid);
        Set<PetType> alreadyOwnedTypes = new HashSet<>();
        for (CosmeticPet p : owned) {
            alreadyOwnedTypes.add(p.getType());
        }

        boolean grantedAny = false;
        for (PetType pt : PetType.values()) {
            if (alreadyOwnedTypes.contains(pt)) continue;
            if (pt.getMinPrestige() <= newPrestigeLevel && pt.getTokenCost() == 0) {
                // Free prestige reward pet (give cool variant for top ones to showcase item 5)
                com.thenerdcj.pets.PetVariant v = (pt == PetType.PHOENIX) ? com.thenerdcj.pets.PetVariant.RAINBOW : com.thenerdcj.pets.PetVariant.NONE;
                if (pt == PetType.BABY_DRAGON) v = com.thenerdcj.pets.PetVariant.GOLD;

                // Pet Skin rewards on high prestige
                com.thenerdcj.pets.PetSkin s = com.thenerdcj.pets.PetSkin.NONE;
                if (pt == PetType.PHOENIX && newPrestigeLevel >= 6) s = com.thenerdcj.pets.PetSkin.PHOENIX_ASCENDED;
                if (pt == PetType.BABY_DRAGON) s = com.thenerdcj.pets.PetSkin.BABY_DRAGON_FIRE;

                CosmeticPet newPet = new CosmeticPet(pt, pt.getDisplayName(), v, s);
                addPet(uuid, newPet);
                grantedAny = true;
                String vStr = (v != com.thenerdcj.pets.PetVariant.NONE) ? " §7(" + v.getColoredName() + "§7)" : "";
                player.sendMessage("§d§lPrestige Pet Reward! §7Unlocked "
                        + pt.getRarity().getColorCode() + pt.getDisplayName() + vStr + "§7!");
            }
        }

        if (grantedAny) {
            savePlayer(uuid);
        }
    }

    /**
     * Attempts to unlock a specific pet type via tokens (Slayer Shop path).
     * Returns true if successfully purchased, unlocked, and granted.
     */
    public boolean unlockPetWithTokens(Player player, PetType petType, int tokensToConsume) {
        if (player == null || petType == null) return false;
        UUID uuid = player.getUniqueId();

        // Check already owned
        List<CosmeticPet> owned = getOwnedPets(uuid);
        for (CosmeticPet p : owned) {
            if (p.getType() == petType) {
                player.sendMessage("§eYou already own this pet.");
                return false;
            }
        }

        // Prestige check
        Island island = plugin.getIslandManager().getIsland(uuid, player.getWorld().getEnvironment());
        int prestige = (island != null && plugin.getPrestigeManager() != null)
                ? plugin.getPrestigeManager().getPrestigeLevel(island) : 0;

        if (prestige < petType.getMinPrestige()) {
            player.sendMessage("§cRequires Prestige " + petType.getMinPrestige() + ".");
            return false;
        }

        if (tokensToConsume > 0) {
            if (plugin.getBossManager() != null && plugin.getBossManager().consumeSlayerTokens(player, tokensToConsume)) {
                CosmeticPet newPet = new CosmeticPet(petType, petType.getDisplayName());
                addPet(uuid, newPet);
                savePlayer(uuid);
                player.sendMessage("§aPurchased pet: " + petType.getRarity().getColorCode() + petType.getDisplayName() + " §7for " + tokensToConsume + " tokens!");
                return true;
            } else {
                player.sendMessage("§cNot enough Slayer Tokens!");
                return false;
            }
        } else {
            // Free at prestige
            CosmeticPet newPet = new CosmeticPet(petType, petType.getDisplayName());
            addPet(uuid, newPet);
            savePlayer(uuid);
            player.sendMessage("§aUnlocked pet (Prestige reward): " + petType.getRarity().getColorCode() + petType.getDisplayName());
            return true;
        }
    }

    // ==================== PET SKIN OWNERSHIP ====================

    public Set<PetSkin> getOwnedSkins(UUID uuid) {
        return ownedSkins.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public int getSkinCollectionCount(UUID uuid) {
        return getOwnedSkins(uuid).size();
    }

    public boolean hasSkin(UUID uuid, PetSkin skin) {
        if (skin == null || skin.isNone()) return true;
        return getOwnedSkins(uuid).contains(skin);
    }

    public void unlockSkin(UUID uuid, PetSkin skin) {
        if (skin == null || skin.isNone()) return;
        Set<PetSkin> skins = getOwnedSkins(uuid);
        boolean wasNew = skins.add(skin);

        if (wasNew) {
            // Light collection XP for new skins (optional feature)
            int xp = skin.getRarity().getXpReward() / 2; // smaller than pet type unlocks
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && plugin.getConfig().getBoolean("pets.skin-collection-xp.enabled", true)) {
                plugin.getIslandManager().addIslandXp(player, xp);
                player.sendMessage("§a§lPet Skin Collection §7» Unlocked " + skin.getRarity().getColorCode() + skin.getDisplayName() + " §7(§a+" + xp + " XP§7)");
            }
        }
    }

    /**
     * Applies a cosmetic skin to one of the player's owned pets.
     * Respects ownership if the skin is not NONE.
     */
    public boolean applySkinToPet(UUID uuid, String petCustomName, PetSkin skin) {
        if (skin != null && !skin.isNone() && !hasSkin(uuid, skin)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage("§cYou haven't unlocked this pet skin yet.");
            }
            return false;
        }

        List<CosmeticPet> pets = getOwnedPets(uuid);
        for (CosmeticPet pet : pets) {
            if (pet.getCustomName().equalsIgnoreCase(petCustomName)) {
                pet.setSkin(skin);

                // If currently active, respawn with new skin
                if (getActivePet(uuid) == pet) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        despawnPet(uuid);
                        spawnPet(player, pet);
                    }
                }
                return true;
            }
        }
        return false;
    }
}
