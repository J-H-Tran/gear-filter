package com.e7gear.role;

/** Role-specific suitability, including slot/main-stat context. */
public record RoleScore(
        Role role,
        double score,
        int usefulStatCount,
        int slotPreferredStatCount,
        boolean mainStatPreferred,
        boolean viable
) {
}
