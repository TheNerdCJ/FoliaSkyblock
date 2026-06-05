package com.thenerdcj.util;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Isolated class for WorldEdit-dependent spawn schematic pasting.
 * This class should ONLY be loaded (via reflection or direct) AFTER confirming
 * that WorldEdit plugin is present at runtime.
 * 
 * All direct references to com.sk89q.worldedit.* are here, so the main plugin
 * classes can load without WorldEdit on the classpath.
 */
public class SpawnSchematicPaster {

    public static boolean tryUseSpawnSchematic(org.bukkit.plugin.java.JavaPlugin plugin, World world, int cx, int cy, int cz) {
        Plugin wePlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (wePlugin == null || !wePlugin.isEnabled()) {
            MessageUtil.warning(plugin.getLogger(), "§e[WorldManager] spawn-schematics.enabled but WorldEdit not present. Using procedural spawn generator.");
            return false;
        }

        File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("spawn-schematics.folder", "spawn-schematics"));
        if (!folder.exists() || !folder.isDirectory()) {
            folder.mkdirs();
            MessageUtil.info(plugin.getLogger(), "§e[WorldManager] Created spawn-schematics folder at " + folder.getAbsolutePath() + ". Add .schem files for complex spawns. Falling back to procedural for now.");
            return false;
        }

        // Auto-download .schem content files if enabled in config (separate from downloading the WorldEdit plugin itself)
        autoDownloadSchemContent(plugin, folder);

        File[] schemFiles = folder.listFiles(f -> f.isFile() && (f.getName().toLowerCase().endsWith(".schem") || f.getName().toLowerCase().endsWith(".schematic")));
        if (schemFiles == null || schemFiles.length == 0) {
            MessageUtil.info(plugin.getLogger(), "§e[WorldManager] No schematics found in " + folder.getAbsolutePath() + ". Using built-in procedural spawn (roads, plaza, buildings, nature).");
            return false;
        }

        // Choose once logic (same as before)
        File choiceFile = new File(plugin.getDataFolder(), "spawn-schematic-choice.txt");
        String chosenName;
        if (choiceFile.exists()) {
            try {
                chosenName = new String(java.nio.file.Files.readAllBytes(choiceFile.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
            } catch (Exception e) {
                chosenName = schemFiles[new java.util.Random().nextInt(schemFiles.length)].getName();
            }
        } else {
            chosenName = schemFiles[new java.util.Random().nextInt(schemFiles.length)].getName();
            try {
                java.nio.file.Files.write(choiceFile.toPath(), chosenName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
            MessageUtil.info(plugin.getLogger(), "§a[WorldManager] Randomly selected spawn schematic for this world: " + chosenName + " (chosen once on first creation)");
        }

        File chosenFile = new File(folder, chosenName);
        if (!chosenFile.exists()) {
            chosenFile = schemFiles[0];
        }

        boolean success = pasteSchematic(chosenFile, world, cx, cy, cz);
        if (success) {
            // The caller (WorldManager) will handle marking built and completing hub.
            MessageUtil.info(plugin.getLogger(), "§a[WorldManager] Spawn schematic pasted: " + chosenFile.getName() + " (complex design from external source)");
        }
        return success;
    }

    private static void autoDownloadSchemContent(org.bukkit.plugin.java.JavaPlugin plugin, File folder) {
        if (!plugin.getConfig().getBoolean("spawn-schematics.auto-download.enabled", false)) {
            return;
        }
        java.util.List<String> urls = plugin.getConfig().getStringList("spawn-schematics.auto-download.urls");
        if (urls.isEmpty()) {
            return;
        }
        File[] existing = folder.listFiles(f -> f.isFile() && (f.getName().toLowerCase().endsWith(".schem") || f.getName().toLowerCase().endsWith(".schematic")));
        if (existing != null && existing.length > 0) {
            return;
        }
        MessageUtil.info(plugin.getLogger(), "§e[WorldManager] Auto-downloading spawn schematics content (first time setup)...");
        for (String urlStr : urls) {
            if (urlStr == null || urlStr.trim().isEmpty() || urlStr.contains("example.com")) {
                MessageUtil.info(plugin.getLogger(), "§e[WorldManager] Skipping placeholder URL in auto-download. See SPAWN_SCHEMATICS.md for real recommendations and how to get direct links.");
                continue;
            }
            try {
                java.net.URL url = new java.net.URL(urlStr.trim());
                String fileName = urlStr.substring(urlStr.lastIndexOf('/') + 1);
                if (!fileName.toLowerCase().endsWith(".schem") && !fileName.toLowerCase().endsWith(".schematic")) {
                    fileName += ".schem";
                }
                fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                File dest = new File(folder, fileName);
                if (dest.exists()) continue;
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "FoliaSkyblock-SchematicDownloader/1.0");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();
                if (conn.getResponseCode() != 200) {
                    MessageUtil.warning(plugin.getLogger(), "§c[WorldManager] Failed to download " + urlStr + " (HTTP " + conn.getResponseCode() + ")");
                    continue;
                }
                try (java.io.InputStream in = conn.getInputStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                MessageUtil.info(plugin.getLogger(), "§a[WorldManager] Auto-downloaded spawn schematic: " + fileName);
            } catch (Exception e) {
                MessageUtil.warning(plugin.getLogger(), "§c[WorldManager] Error auto-downloading " + urlStr + ": " + e.getMessage());
            }
        }
    }

    private static boolean pasteSchematic(File file, World world, int x, int y, int z) {
        try {
            com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat format = com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(file);
            if (format == null) {
                MessageUtil.warning(null, "§c[WorldManager] Unknown schematic format for " + file.getName());  // logger passed from caller if needed
                return false;
            }
            com.sk89q.worldedit.extent.clipboard.Clipboard clipboard;
            try (FileInputStream fis = new FileInputStream(file);
                 com.sk89q.worldedit.extent.clipboard.io.ClipboardReader reader = format.getReader(fis)) {
                clipboard = reader.read();
            }
            com.sk89q.worldedit.EditSession editSession = com.sk89q.worldedit.WorldEdit.getInstance()
                .newEditSession(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
            com.sk89q.worldedit.session.ClipboardHolder holder = new com.sk89q.worldedit.session.ClipboardHolder(clipboard);
            com.sk89q.worldedit.function.operation.Operation operation = holder.createPaste(editSession)
                .to(com.sk89q.worldedit.math.BlockVector3.at(x, y, z))
                .ignoreAirBlocks(false)
                .build();
            com.sk89q.worldedit.function.operation.Operations.complete(operation);
            editSession.close();
            return true;
        } catch (Exception e) {
            // Use plugin logger if possible, but for isolation pass null or handle
            if (e.getMessage() != null) {
                MessageUtil.log(null, Level.SEVERE, "§c[WorldManager] Failed to paste spawn schematic " + file.getName(), e);
            }
            return false;
        }
    }
}