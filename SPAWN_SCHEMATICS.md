# Recommended Schematics for FoliaSkyblock Spawn/Hub

The plugin supports complex, detailed spawn designs via schematics (using WorldEdit/FAWE). 

**How to use with auto-download (recommended for ease):**
1. Enable in `config.yml`:
   ```yaml
   spawn-schematics:
     enabled: true
     auto-download:
       enabled: true
       urls:
         - "YOUR_DIRECT_RAW_URL_HERE.schem"
   ```
2. Find a schematic below, download the .schem file from the page.
3. Host it publicly for direct download (e.g. create a free GitHub repo, upload the .schem, use the "Raw" URL like `https://raw.githubusercontent.com/youruser/yourrepo/main/filename.schem`).
4. Add the raw URL(s) to the list in config.
5. On first spawn generation (new world or remove the marker block), it will auto-download if needed, randomly pick one (if multiple), and use it permanently for consistent spawn.

**Notes:**
- Schematics should be for 1.16+ preferably, .schem (Sponge/WorldEdit format).
- Size: Aim for 100-300 block radius for spawn hub.
- Test paste with WorldEdit `/schem load` and `/paste` in a test world first.
- The schematic's origin will be placed at your configured spawn center (default 0,100,0). Design or offset accordingly.
- Fallback to built-in procedural if no WE or downloads fail.
- Always respect the author's license (most PMC are free for servers with credit).

## Recommended Free Skyblock Spawn/Hub Schematics

### 1. Island SkyBlock Spawn (80x100) - Small but detailed village-style spawn
- **Link**: https://www.planetminecraft.com/project/island-skyblock-spawn-free-download-80x100-maps-schematic-1-16x/
- **Description**: Compact skyblock spawn with island theme, good for small servers. Includes schematic.
- **Size**: ~80x100
- **Version**: 1.16+
- **How to get direct**: Download from page (free), upload to GitHub for raw URL.
- **Why good**: Simple, fits skyblock, has NPC areas, paths.

### 2. Legendary Big SkyBlock Spawn (150x150) - Larger detailed spawn
- **Link**: https://www.planetminecraft.com/project/legendary-big-skyblock-spawn-free-download-150x150-maps-download-1-16x/
- **Description**: Big, epic skyblock spawn with lots of detail. Free download includes schematic.
- **Size**: 150x150
- **Version**: 1.16+
- **MediaFire or direct often linked in description/comments**.
- **Why good**: Complex with multiple areas, good for main hub.

### 3. Hub "Floating Worlds" - Floating islands hub for skyblock
- **Link**: https://www.planetminecraft.com/project/hub-floating-worlds/
- **Description**: Small hub/lobby/spawn with central island, village, stalls for NPCs, floating islands representing overworld/nether/end with portal spots. Has .schem download. Perfect for skyblock with nature and paths.
- **Size**: Small (128x128-ish)
- **Version**: 1.19+
- **Why good**: Matches "roads, paths, buildings and nature" - arches, pathways, village feel. Complex enough without being massive.

### 4. Epic Large Spawn (various sizes) - Highly detailed large hub
- **Link**: https://www.planetminecraft.com/project/epic-large-spawn-free-download-map-schematic-1-21-4/
- **Description**: Epic, large scale spawn with schematic. Modern/detailed design suitable for skyblock.
- **Size**: Large
- **Version**: 1.21+
- **Why good**: Complex, high quality for impressive spawn.

### 5. SKYBLOCK SPAWN - Duskhaven (500x500) - Very complex full featured
- **Link**: https://www.planetminecraft.com/project/skyblock-spawn-duskhaven-500x500/
- **Description**: Large 500x500 skyblock spawn with grand castle, vibrant village, detailed medieval houses with interiors, market stalls, scenic pathways, shop areas, crate zones, functional portal, open spaces. Extremely complex and cohesive.
- **Size**: 500x500
- **Why good**: Matches "complex spawns" perfectly - roads/paths, buildings, nature, everything integrated like top PMC builds. Use if your server can handle large spawn.

### Additional Tips
- Search PMC for "skyblock spawn schematic free" and filter by schematic download.
- For BuiltByBit/CurseForge: Many premium, but some free or leaked (use at own risk, respect creators).
- To make auto-download work seamlessly: After downloading .schem locally, push to a GitHub repo and use raw link.
- Example raw URL format: `https://raw.githubusercontent.com/YourUsername/Schematics/main/my-skyblock-spawn.schem`
- Test with WorldEdit: `/schem load myfile` then `/paste` at your spawn center.
- If using multiple, the plugin randomly picks one on first creation for variety across resets if you want (but "once" means consistent after pick).
- Update your config with real URLs for automatic on fresh installs.

If you have specific schematics or want me to add more (e.g. search for particular style like medieval, modern, floating), provide links or descriptions and I'll update the list and config!

This will give your spawn the complex, non-randomized look you want when using a good schematic.