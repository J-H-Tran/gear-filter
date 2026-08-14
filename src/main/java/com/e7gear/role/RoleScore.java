package com.e7gear.role;

/**
 * Role-specific suitability with explicit slot/main-stat context.
 *
 * usefulStatCount = broad role affinity.
 * coreStatCount = stats specifically preferred for this role/slot.
 * slotCompatible = the item has at least one slot-preferred substat or a
 *                  role-preferred main stat.
 */
public record RoleScore(
        Role role,
        double score,
        int usefulStatCount,
        int coreStatCount,
        int slotPreferredStatCount,
        boolean mainStatPreferred,
        boolean slotCompatible,
        boolean viable
) {
}
