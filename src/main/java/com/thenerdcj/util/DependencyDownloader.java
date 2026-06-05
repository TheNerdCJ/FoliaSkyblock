package com.thenerdcj.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.thenerdcj.util.MessageUtil;

/**
 * Automatically downloads the latest versions of soft-dependency plugins
 * (PlaceholderAPI, WorldEdit) into the plugins folder if they are missing.
 * 
 * Downloads happen on first startup if the plugin JAR is not present.
 * A server restart is required for Bukkit to load the new plugins.
 * 
 * Uses Maven metadata to always fetch the latest release version.
 * Safe: only downloads if missing, graceful failure with warnings.
 */
public class DependencyDownloader {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Map of plugin display name -> [groupId, artifactId, mavenRepoBaseUrl, expectedPluginName]
    private static final Map<String, String[]> DEPENDENCIES = Map.of(
        "PlaceholderAPI", new String[] {
            "me.clip", 
            "placeholderapi", 
            "https://repo.extendedclip.com/content/repositories/placeholderapi/",
            "PlaceholderAPI"
        },
        "WorldEdit", new String[] {
            "com.sk89q.worldedit", 
            "worldedit-bukkit", 
            "https://maven.enginehub.org/repo/",
            "WorldEdit"
        }
    );

    private final JavaPlugin plugin;
    private final File pluginsFolder;

    public DependencyDownloader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pluginsFolder = plugin.getDataFolder().getParentFile();
    }

    /**
     * Checks and downloads any missing dependencies.
     * Call this early in onEnable() or onLoad().
     */
    public void downloadMissingDependencies() {
        MessageUtil.info(plugin.getLogger(), "§6[Dependencies] Checking for required soft dependencies...");

        for (Map.Entry<String, String[]> entry : DEPENDENCIES.entrySet()) {
            String displayName = entry.getKey();
            String[] coords = entry.getValue();
            String groupId = coords[0];
            String artifactId = coords[1];
            String repoBase = coords[2];
            String expectedPluginName = coords[3];

            if (isPluginPresent(expectedPluginName)) {
                MessageUtil.info(plugin.getLogger(), "§a[Dependencies] " + displayName + " is already installed.");
                continue;
            }

            // Check if a jar for this artifact already exists (even if not loaded yet)
            if (hasExistingJar(artifactId)) {
                MessageUtil.warning(plugin.getLogger(), 
                    "§e[Dependencies] " + displayName + " JAR detected but plugin not loaded yet. Restart may be needed.");
                continue;
            }

            try {
                String latestVersion = fetchLatestVersion(repoBase, groupId, artifactId);
                if (latestVersion == null) {
                    MessageUtil.warning(plugin.getLogger(), "§c[Dependencies] Could not determine latest version for " + displayName);
                    continue;
                }

                // Fallback for WorldEdit if snapshot or problematic version
                if (displayName.equals("WorldEdit") && (latestVersion.contains("SNAPSHOT") || latestVersion.startsWith("8."))) {
                    latestVersion = "7.2.20";  // Known stable for 1.21
                    MessageUtil.info(plugin.getLogger(), "§e[Dependencies] Using stable fallback version " + latestVersion + " for WorldEdit.");
                }

                File targetFile = new File(pluginsFolder, artifactId + "-" + latestVersion + ".jar");
                if (targetFile.exists()) {
                    MessageUtil.info(plugin.getLogger(), "§a[Dependencies] " + displayName + " " + latestVersion + " already downloaded.");
                    continue;
                }

                String downloadUrl = repoBase + groupId.replace('.', '/') + "/" + artifactId + "/" + latestVersion + "/" 
                                   + artifactId + "-" + latestVersion + ".jar";

                MessageUtil.info(plugin.getLogger(), "§e[Dependencies] Downloading latest " + displayName + " (" + latestVersion + ") from " + downloadUrl);

                downloadFile(downloadUrl, targetFile);

                MessageUtil.info(plugin.getLogger(), "§a[Dependencies] Successfully downloaded " + displayName + " " + latestVersion + ".");
                MessageUtil.warning(plugin.getLogger(), "§6[Dependencies] Please restart the server to load " + displayName + ".");

            } catch (Exception e) {
                MessageUtil.warning(plugin.getLogger(), "§c[Dependencies] Failed to auto-download " + displayName + ": " + e.getMessage());
                plugin.getLogger().warning("Full error for " + displayName + ": " + e);
            }
        }
    }

    private boolean isPluginPresent(String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }

    private boolean hasExistingJar(String artifactId) {
        if (!pluginsFolder.exists()) return false;
        File[] jars = pluginsFolder.listFiles(f -> f.getName().toLowerCase().endsWith(".jar") 
                && f.getName().toLowerCase().contains(artifactId.toLowerCase()));
        return jars != null && jars.length > 0;
    }

    private String fetchLatestVersion(String repoBase, String groupId, String artifactId) throws Exception {
        String metadataUrl = repoBase + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(metadataUrl))
                .header("User-Agent", "FoliaSkyblock-DependencyDownloader/1.0")
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching metadata");
        }

        String xml = response.body();
        
        // Prefer <release> tag (stable), fallback to <latest>, avoid pure snapshots for safety
        Pattern releasePattern = Pattern.compile("<release>([^<]+)</release>");
        Matcher matcher = releasePattern.matcher(xml);
        if (matcher.find()) {
            String version = matcher.group(1).trim();
            if (!version.contains("-SNAPSHOT")) {
                return version;
            }
        }
        
        // Fallback to <latest>
        Pattern latestPattern = Pattern.compile("<latest>([^<]+)</latest>");
        matcher = latestPattern.matcher(xml);
        if (matcher.find()) {
            String version = matcher.group(1).trim();
            return version;
        }
        
        // Last resort: find any version
        Pattern versionPattern = Pattern.compile("<version>([^<]+)</version>");
        matcher = versionPattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }

    private void downloadFile(String url, File destination) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "FoliaSkyblock-DependencyDownloader/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download: HTTP " + response.statusCode());
        }

        // Ensure parent dir
        destination.getParentFile().mkdirs();

        try (InputStream in = response.body();
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        // Basic validation: check file size > 100KB (crude check it's not error page)
        if (destination.length() < 100_000) {
            destination.delete();
            throw new IOException("Downloaded file seems too small (possible error page). Deleted.");
        }
    }
}