package com.thenerdcj.hologram;

import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime wrapper for a spawned hologram.
 * Holds references to the TextDisplay entities for easy editing/removal.
 * All modifications must be scheduled on the correct Folia region thread.
 */
public class Hologram {

    private final HologramData data;
    private final List<TextDisplay> displays;

    public Hologram(HologramData data, List<TextDisplay> displays) {
        this.data = data;
        this.displays = new ArrayList<>(displays);
    }

    public HologramData getData() {
        return data;
    }

    public List<TextDisplay> getDisplays() {
        return displays;
    }

    /**
     * Removes all TextDisplay entities for this hologram.
     * Must be called from the correct region thread (use EntityScheduler or RegionScheduler).
     */
    public void removeAll() {
        for (TextDisplay display : displays) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
    }

    /**
     * Updates the text of a specific line (by index).
     * Caller must ensure this is executed on the region thread of the hologram location.
     */
    public void updateLineText(int index, net.kyori.adventure.text.Component newText) {
        if (index >= 0 && index < displays.size()) {
            TextDisplay display = displays.get(index);
            if (display != null && display.isValid()) {
                display.text(newText);
            }
        }
    }
}
