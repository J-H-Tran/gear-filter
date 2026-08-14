package com.e7gear.role;

import com.e7gear.gear.Gear;
import com.e7gear.gear.Substat;
import com.e7gear.scorer.GearScorer;

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

    private static final Map<Role, Set<String>> ROLE_STATS = Map.of(
            Role.DPS, Set.of("AttackPercent", "CriticalHitChancePercent", "CriticalHitDamagePercent", "Speed"),
            Role.BRUISER, Set.of("HealthPercent", "DefensePercent", "CriticalHitChancePercent", "CriticalHitDamagePercent", "Speed"),
            Role.SUPPORT, Set.of("HealthPercent", "DefensePercent", "EffectResistancePercent", "Speed"),
            Role.DEBUFFER, Set.of("HealthPercent", "DefensePercent", "EffectivenessPercent", "Speed")
    );

    private static final Map<Role, Map<String, Set<String>>> SLOT_STATS = Map.of(
            Role.DPS, Map.of(
                    "Necklace", Set.of("CriticalHitDamagePercent", "CriticalHitChancePercent", "AttackPercent"),
                    "Ring", Set.of("AttackPercent"),
                    "Boots", Set.of("Speed", "AttackPercent")
            ),
            Role.BRUISER, Map.of(
                    "Necklace", Set.of("HealthPercent", "CriticalHitChancePercent", "CriticalHitDamagePercent", "DefensePercent"),
                    "Ring", Set.of("HealthPercent", "DefensePercent", "AttackPercent"),
                    "Boots", Set.of("Speed", "HealthPercent", "DefensePercent")
            ),
            Role.SUPPORT, Map.of(
                    "Necklace", Set.of("HealthPercent", "DefensePercent"),
                    "Ring", Set.of("HealthPercent", "DefensePercent", "EffectResistancePercent"),
                    "Boots", Set.of("Speed", "HealthPercent")
            ),
            Role.DEBUFFER, Map.of(
                    "Necklace", Set.of("HealthPercent", "DefensePercent", "AttackPercent"),
                    "Ring", Set.of("EffectivenessPercent", "HealthPercent"),
                    "Boots", Set.of("Speed")
            )
    );

    private static final Map<Role, Map<String, Set<String>>> MAIN_STATS = Map.of(
            Role.DPS, Map.of(
                    "Necklace", Set.of("CriticalHitDamagePercent", "CriticalHitChancePercent", "AttackPercent"),
                    "Ring", Set.of("AttackPercent"),
                    "Boots", Set.of("Speed", "AttackPercent")
            ),
            Role.BRUISER, Map.of(
                    "Necklace", Set.of("HealthPercent", "CriticalHitChancePercent", "CriticalHitDamagePercent"),
                    "Ring", Set.of("HealthPercent", "DefensePercent", "AttackPercent"),
                    "Boots", Set.of("Speed", "HealthPercent", "DefensePercent")
            ),
            Role.SUPPORT, Map.of(
                    "Necklace", Set.of("HealthPercent", "DefensePercent"),
                    "Ring", Set.of("HealthPercent", "DefensePercent", "EffectResistancePercent"),
                    "Boots", Set.of("Speed", "HealthPercent")
            ),
            Role.DEBUFFER, Map.of(
                    "Necklace", Set.of("HealthPercent", "DefensePercent", "AttackPercent"),
                    "Ring", Set.of("EffectivenessPercent", "HealthPercent"),
                    "Boots", Set.of("Speed")
            )
    );

    private static final List<Role> ROLES = List.of(Role.DPS, Role.BRUISER, Role.SUPPORT, Role.DEBUFFER);

    private final GearScorer gearScorer;

    public RoleEvaluator() {
        this(new GearScorer());
    }

    public RoleEvaluator(GearScorer gearScorer) {
        this.gearScorer = gearScorer;
    }

    public RoleEvaluation evaluate(Gear gear) {
        List<RoleScore> scores = ROLES.stream()
                .map(role -> evaluateRole(gear, role))
                .toList();

        Role bestRole = scores.stream()
                .filter(RoleScore::viable)
                .max((a, b) -> {
                    int byUseful = Integer.compare(a.usefulStatCount(), b.usefulStatCount());
                    if (byUseful != 0) return byUseful;
                    int bySlot = Integer.compare(a.slotPreferredStatCount(), b.slotPreferredStatCount());
                    if (bySlot != 0) return bySlot;
                    int byMain = Boolean.compare(a.mainStatPreferred(), b.mainStatPreferred());
                    if (byMain != 0) return byMain;
                    return Double.compare(a.score(), b.score());
                })
                .map(RoleScore::role)
                .orElse(Role.NONE);

        return new RoleEvaluation(scores, bestRole);
    }

    private RoleScore evaluateRole(Gear gear, Role role) {
        if (gear == null) {
            return new RoleScore(role, 0.0, 0, 0, false, false);
        }

        Set<String> roleStats = ROLE_STATS.get(role);
        Set<String> slotPreferredStats = SLOT_STATS.getOrDefault(role, Map.of())
                .getOrDefault(normalizeSlot(gear.getGear()), Set.of());

        double score = 0.0;
        int useful = 0;
        int slotPreferred = 0;

        if (gear.getSubstats() != null) {
            for (Substat substat : gear.getSubstats()) {
                if (substat == null || !roleStats.contains(substat.getType())) continue;
                useful++;
                score += gearScorer.calculateStatScore(substat.getType(), substat.getValue());
                if (slotPreferredStats.contains(substat.getType())) slotPreferred++;
            }
        }

        boolean mainPreferred = false;
        if (gear.getMain() != null) {
            Set<String> mainStats = MAIN_STATS.getOrDefault(role, Map.of())
                    .getOrDefault(normalizeSlot(gear.getGear()), Set.of());
            mainPreferred = mainStats.contains(gear.getMain().getType());
        }

        // Any favorable substat establishes role affinity. Minimum-stat thresholds
        // belong to DecisionEngine because they are decision policy.
        boolean viable = useful >= 1;

        // Slot context strengthens, rather than creates, viability.
        // A two-stat role fit remains viable even if its main stat is niche.
        return new RoleScore(role, score, useful, slotPreferred, mainPreferred, viable);
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

    public static Set<String> statsFor(Role role) {
        return ROLE_STATS.getOrDefault(role, Set.of());
    }
}
