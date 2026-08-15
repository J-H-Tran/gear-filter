package com.e7gear.role;

import com.e7gear.config.FilterConfig;
import com.e7gear.gear.Gear;
import com.e7gear.gear.Substat;
import com.e7gear.scorer.GearScorer;
import com.e7gear.stats.StatType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates role suitability using both substats and the slot/main-stat
 * recommendations in the 2026 equipment guide.
 *
 * The evaluator reports suitability; it does not decide whether gear should
 * be deleted.
 */
public final class RoleEvaluator {

    private static final List<Role> ROLES = List.of(Role.DPS, Role.BRUISER, Role.SUPPORT, Role.DEBUFFER);

    // Role stat sets (using StatType)
    private static final Map<Role, Set<StatType>> ROLE_STATS = Map.of(
            Role.DPS, Set.of(
                    StatType.ATTACK_PERCENT,
                    StatType.CRIT_CHANCE,
                    StatType.CRIT_DAMAGE,
                    StatType.SPEED
            ),
            Role.BRUISER, Set.of(
                    StatType.HEALTH_PERCENT,
                    StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE,
                    StatType.CRIT_DAMAGE,
                    StatType.SPEED
            ),
            Role.SUPPORT, Set.of(
                    StatType.HEALTH_PERCENT,
                    StatType.DEFENSE_PERCENT,
                    StatType.EFFECT_RESISTANCE,
                    StatType.SPEED
            ),
            Role.DEBUFFER, Set.of(
                    StatType.HEALTH_PERCENT,
                    StatType.DEFENSE_PERCENT,
                    StatType.EFFECTIVENESS,
                    StatType.SPEED
            )
    );

    // Slot-specific preferred substats (using StatType)
    private static final Map<Role, Map<String, Set<StatType>>> SLOT_STATS = Map.of(
            Role.DPS, Map.of(
                    "Necklace", Set.of(StatType.CRIT_DAMAGE, StatType.CRIT_CHANCE, StatType.ATTACK_PERCENT),
                    "Ring", Set.of(StatType.ATTACK_PERCENT),
                    "Boots", Set.of(StatType.SPEED, StatType.ATTACK_PERCENT)
            ),
            Role.BRUISER, Map.of(
                    "Necklace", Set.of(StatType.HEALTH_PERCENT, StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.DEFENSE_PERCENT),
                    "Ring", Set.of(StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT, StatType.ATTACK_PERCENT),
                    "Boots", Set.of(StatType.SPEED, StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT)
            ),
            Role.SUPPORT, Map.of(
                    "Necklace", Set.of(StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT),
                    "Ring", Set.of(StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT, StatType.EFFECT_RESISTANCE),
                    "Boots", Set.of(StatType.SPEED, StatType.HEALTH_PERCENT)
            ),
            Role.DEBUFFER, Map.of(
                    "Necklace", Set.of(StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT, StatType.ATTACK_PERCENT),
                    "Ring", Set.of(StatType.EFFECTIVENESS, StatType.HEALTH_PERCENT),
                    "Boots", Set.of(StatType.SPEED)
            )
    );

    // Preferred main stats by role and slot (using StatType)
    private static final Map<Role, Map<String, Set<StatType>>> MAIN_STATS = Map.of(
            Role.DPS, Map.of(
                    "Necklace", Set.of(StatType.CRIT_DAMAGE, StatType.CRIT_CHANCE, StatType.ATTACK_PERCENT),
                    "Ring", Set.of(StatType.ATTACK_PERCENT),
                    "Boots", Set.of(StatType.SPEED, StatType.ATTACK_PERCENT)
            ),
            Role.BRUISER, Map.of(
                    "Necklace", Set.of(StatType.HEALTH_PERCENT, StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE),
                    "Ring", Set.of(StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT, StatType.ATTACK_PERCENT),
                    "Boots", Set.of(StatType.SPEED, StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT)
            ),
            Role.SUPPORT, Map.of(
                    "Necklace", Set.of(StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT),
                    "Ring", Set.of(StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT, StatType.EFFECT_RESISTANCE),
                    "Boots", Set.of(StatType.SPEED, StatType.HEALTH_PERCENT)
            ),
            Role.DEBUFFER, Map.of(
                    "Necklace", Set.of(StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT, StatType.ATTACK_PERCENT),
                    "Ring", Set.of(StatType.EFFECTIVENESS, StatType.HEALTH_PERCENT),
                    "Boots", Set.of(StatType.SPEED)
            )
    );

    private final GearScorer gearScorer;
    private final FilterConfig config;

    public RoleEvaluator(GearScorer gearScorer, FilterConfig config) {
        this.gearScorer = gearScorer;
        this.config = config;
    }

    public RoleEvaluation evaluate(Gear gear) {
        List<RoleScore> scores = ROLES.stream()
                .filter(r -> r != Role.NONE)
                .map(role -> evaluateRole(gear, role))
                .toList();

        Role bestRole = scores.stream()
                .filter(RoleScore::viable)
                .max((a, b) -> {
                    int byCore = Integer.compare(a.coreStatCount(), b.coreStatCount());
                    if (byCore != 0) return byCore;

                    int byMain = Boolean.compare(a.mainStatPreferred(), b.mainStatPreferred());
                    if (byMain != 0) return byMain;

                    int byUseful = Integer.compare(a.usefulStatCount(), b.usefulStatCount());
                    if (byUseful != 0) return byUseful;

                    int bySlot = Integer.compare(a.slotPreferredStatCount(), b.slotPreferredStatCount());
                    if (bySlot != 0) return bySlot;

                    return Double.compare(a.score(), b.score());
                })
                .map(RoleScore::role)
                .orElse(Role.NONE);

        return new RoleEvaluation(scores, bestRole);
    }

    private RoleScore evaluateRole(Gear gear, Role role) {
        if (gear == null) {
            return new RoleScore(role, 0.0, 0, 0, 0, false, false, false);
        }

        // 🟢 Skip NONE role to avoid null stats
        if (role == Role.NONE) {
            return new RoleScore(role, 0.0, 0, 0, 0, false, false, false);
        }

        String slot = normalizeSlot(gear.getGear());
        Set<StatType> roleStats = ROLE_STATS.get(role);
        if (roleStats == null) {
            // Should not happen for defined roles, but safe fallback
            return new RoleScore(role, 0.0, 0, 0, 0, false, false, false);
        }

        Set<StatType> slotPreferredStats = SLOT_STATS
                .getOrDefault(role, Map.of())
                .getOrDefault(slot, Set.of());

        double rawScore = 0.0;
        int useful = 0;
        int core = 0;

        if (gear.getSubstats() != null) {
            for (Substat substat : gear.getSubstats()) {
                if (substat == null) continue;
                StatType statType = StatType.fromString(substat.getType());
                if (statType == null || !roleStats.contains(statType)) {
                    continue;
                }

                useful++;
                rawScore += gearScorer.calculateStatScore(statType, substat.getValue());

                if (slotPreferredStats.contains(statType)) {
                    core++;
                }
            }
        }

        Set<StatType> preferredMainStats = MAIN_STATS
                .getOrDefault(role, Map.of())
                .getOrDefault(slot, Set.of());

        boolean mainPreferred = gear.getMain() != null
                && preferredMainStats.contains(StatType.fromString(gear.getMain().getType()));

        boolean slotCompatible = !slotPreferredStats.isEmpty()
                && (core > 0 || mainPreferred);

        boolean viable = useful >= 1;

        // 🟢 Safe set multiplier with null check
        String set = gear.getSet();
        double multiplier = config.setMultipliers().getOrDefault(set != null ? set : "", 1.0);
        double adjustedScore = rawScore * multiplier;

        return new RoleScore(
                role,
                adjustedScore,
                useful,
                core,
                core,
                mainPreferred,
                slotCompatible,
                viable
        );
    }

    private String normalizeSlot(String slot) {
        if (slot == null) return "";

        return switch (slot) {
            case "Neck", "Necklace" -> "Necklace";
            case "Ring" -> "Ring";
            case "Boot", "Boots" -> "Boots";
            default -> slot;
        };
    }

    public static Set<StatType> statsFor(Role role) {
        return ROLE_STATS.getOrDefault(role, Set.of());
    }

    public static Set<StatType> getRoleStats(Role role) {
        return ROLE_STATS.getOrDefault(role, Set.of());
    }
}
