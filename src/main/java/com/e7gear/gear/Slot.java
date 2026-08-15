package com.e7gear.gear;

import java.util.Arrays;
import java.util.List;

public enum Slot {
    WEAPON("weapon"),
    HELMET("helmet", "helm"),
    ARMOR("armor", "chest"),
    NECKLACE("necklace", "neck"),
    RING("ring"),
    BOOTS("boots", "boot");

    private final List<String> aliases;

    Slot(String... aliases) {
        this.aliases = Arrays.asList(aliases);
    }

    public boolean isLeftSide() {
        return this == WEAPON || this == HELMET || this == ARMOR;
    }

    public boolean isRightSide() {
        return this == NECKLACE || this == RING || this == BOOTS;
    }

    public static Slot fromString(String rawSlot) {
        if (rawSlot == null) return null;
        String normalized = rawSlot.trim().toLowerCase();
        for (Slot slot : values()) {
            if (slot.aliases.contains(normalized)) {
                return slot;
            }
        }
        return null;
    }
}
