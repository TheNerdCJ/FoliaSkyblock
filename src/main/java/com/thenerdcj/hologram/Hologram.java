package com.thenerdcj.hologram;

import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime wrapper for a spawned hologram.
 * Holds references to Display entities (TextDisplay for text, ItemDisplay for items).
 * All modifications must be scheduled on the correct Folia region/entity thread via ThreadSafety or direct schedulers.
 */
public class Hologram {

    private final HologramData data;
    private final List<Display> displays = new ArrayList<>();

    public Hologram(HologramData data, List<Display> displays) {
        this.data = data;
        if (displays != null) {
            this.displays.addAll(displays);
        }
    }

    public HologramData getData() {
        return data;
    }

    public List<Display> getDisplays() {
        return displays;
    }

    /**
     * Removes all Display entities for this hologram and clears the internal list.
     * MUST be called on the correct region/entity thread in Folia.
     */
    public void removeAll() {
        for (Display display : displays) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
    }

    /**
     * Adds a display entity (for internal use during spawn).
     */
    public void addDisplay(Display display) {
        if (display != null) {
            displays.add(display);
        }
    }
}