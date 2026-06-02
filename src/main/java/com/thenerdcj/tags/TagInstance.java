package com.thenerdcj.tags;

import java.util.Objects;

/**
 * Represents an owned instance of a PlayerTag with an optional variant.
 * Mirrors CosmeticPet for pets (type + variant).
 */
public class TagInstance {

    private final PlayerTag type;
    private PlayerTagVariant variant;

    public TagInstance(PlayerTag type) {
        this(type, PlayerTagVariant.NONE);
    }

    public TagInstance(PlayerTag type, PlayerTagVariant variant) {
        this.type = Objects.requireNonNull(type, "Tag type cannot be null");
        this.variant = variant != null ? variant : PlayerTagVariant.NONE;
    }

    public PlayerTag getType() {
        return type;
    }

    public PlayerTagVariant getVariant() {
        return variant;
    }

    public void setVariant(PlayerTagVariant variant) {
        this.variant = variant != null ? variant : PlayerTagVariant.NONE;
    }

    /**
     * Returns the visually enhanced tag text, applying variant tint if present.
     */
    public String getFormattedTag() {
        String base = type.getFormattedTag();
        if (variant == PlayerTagVariant.NONE || base.isEmpty()) {
            return base;
        }

        // Apply tint by replacing the first color code section with the variant's tint
        String tint = variant.getTint();
        if (tint.isEmpty()) {
            return base;
        }

        // Simple tint application: insert tint after the first § color if present
        return base.replaceFirst("§[0-9a-fk-or]", tint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TagInstance)) return false;
        TagInstance that = (TagInstance) o;
        return type == that.type; // identity based on base type for collection purposes
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return type.name() + (variant != PlayerTagVariant.NONE ? ":" + variant.name() : "");
    }
}
